// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.github.keyang556.kboard.R
import io.github.keyang556.kboard.accessibility.AccessibilityUtils
import io.github.keyang556.kboard.candidates.CandidateBarView
import io.github.keyang556.kboard.candidates.CandidatePanelView
import io.github.keyang556.kboard.engine.Candidate
import io.github.keyang556.kboard.engine.ChewingData
import io.github.keyang556.kboard.engine.ChewingEngine
import io.github.keyang556.kboard.engine.ChewingEngineHolder
import io.github.keyang556.kboard.engine.ChewingNative
import io.github.keyang556.kboard.engine.Composition
import io.github.keyang556.kboard.keyboard.Key
import io.github.keyang556.kboard.keyboard.KeyAction
import io.github.keyang556.kboard.keyboard.KeyLabels
import io.github.keyang556.kboard.keyboard.Keyboard
import io.github.keyang556.kboard.keyboard.KeyboardId
import io.github.keyang556.kboard.keyboard.KeyboardLayouts
import io.github.keyang556.kboard.keyboard.KeyboardState
import io.github.keyang556.kboard.keyboard.KeyboardView
import io.github.keyang556.kboard.keyboard.Language
import io.github.keyang556.kboard.keyboard.Layer
import io.github.keyang556.kboard.keyboard.ShiftState
import io.github.keyang556.kboard.settings.Prefs
import io.github.keyang556.kboard.settings.SettingsActivity
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * The input method: Zhuyin through libchewing, English, digits and symbols, on a keyboard
 * that works with TalkBack the way HeliBoard's does.
 *
 * Chinese is composed in the editor itself (as "composing text"), so TalkBack reads it as
 * it changes; the candidate bar offers other characters and phrases for what was typed.
 */
class KboardService : InputMethodService(),
    KeyboardView.Listener,
    CandidateBarView.Listener,
    CandidatePanelView.Listener {

    private lateinit var prefs: Prefs
    private val state = KeyboardState()
    private val keyboards = HashMap<KeyboardId, Keyboard>()
    private lateinit var keyLabels: KeyLabels

    private var keyboardView: KeyboardView? = null
    private var candidateBar: CandidateBarView? = null
    private var candidatePanel: CandidatePanelView? = null

    // The engine, once configured with the current settings.
    private var configuredEngine: ChewingEngine? = null
    private var engineUnavailableShown = false

    // What this input method last put in the editor as composing text.
    private var composition = Composition.EMPTY
    private var candidates: List<Candidate> = emptyList()
    private var panelOpen = false

    // A symbol chosen from the symbol table opened with nothing composed is committed at once.
    private var commitAfterSelection = false

    // Whether the last key came from a hardware keyboard, whose digits choose candidates.
    private var hardwareKeyboardUsed = false
    private val consumedKeyCodes = HashSet<Int>()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        keyLabels = KeyLabels.from(resources)
        // Copy the dictionaries out early, so the first Chinese key does not wait for it.
        val appContext = applicationContext
        thread(name = "ChewingData") { ChewingData.install(appContext) }
    }

    override fun onDestroy() {
        ChewingEngineHolder.release()
        configuredEngine = null
        super.onDestroy()
    }

    // An input view has no parent to inflate into.
    @SuppressLint("InflateParams")
    override fun onCreateInputView(): View {
        keyLabels = KeyLabels.from(resources)
        keyboards.clear()
        val view = layoutInflater.inflate(R.layout.input_view, null)
        keyboardView = view.findViewById<KeyboardView>(R.id.keyboard_view).also { it.listener = this }
        candidateBar = view.findViewById<CandidateBarView>(R.id.candidate_bar).also { it.listener = this }
        candidatePanel = view.findViewById<CandidatePanelView>(R.id.candidate_panel).also { it.listener = this }
        // Keep the keys clear of the navigation bar where the system draws it over us.
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // With a hardware keyboard the keys stay hidden, but candidates still need showing.
    override fun onEvaluateInputViewShown(): Boolean =
        super.onEvaluateInputViewShown() || hardwareKeyboardUsed && candidates.isNotEmpty()

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        configuredEngine?.clear()
        composition = Composition.EMPTY
        candidates = emptyList()
        commitAfterSelection = false
        if (!restarting) {
            val language = when {
                attribute.inputType == InputType.TYPE_NULL -> Language.ENGLISH
                EditorInfos.requiresEnglish(attribute.inputType) -> Language.ENGLISH
                else -> prefs.lastLanguage
            }
            state.startInput(EditorInfos.baseLayer(attribute.inputType), language)
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val view = keyboardView ?: return
        // Settings may have changed while the keyboard was hidden.
        configuredEngine = null
        keyboards.clear()
        view.rowHeight = (resources.getDimension(R.dimen.key_row_height) * prefs.keyHeight.scale).roundToInt()
        val obscure = AccessibilityUtils.shouldObscureInput(
            this, EditorInfos.isPassword(info.inputType), prefs.speakPasswordKeys,
        )
        view.describer.obscureInput = obscure
        view.describer.syllablePending = false
        closePanel()
        updateAutoShift(redraw = false)
        showKeyboard()
        updateCandidates()
        if (obscure && AccessibilityUtils.isTouchExplorationEnabled(this)) {
            view.announce(getString(R.string.spoken_use_headphones))
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        commitComposition()
        closePanel()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        commitComposition()
        hardwareKeyboardUsed = false
        super.onFinishInput()
    }

    override fun onWindowHidden() {
        keyboardView?.let { view ->
            if (AccessibilityUtils.isEnabled(this)) view.announce(getString(R.string.spoken_keyboard_hidden))
        }
        super.onWindowHidden()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (composition.isEmpty) {
            updateAutoShift(redraw = true)
            return
        }
        val composingGone = candidatesStart < 0 || candidatesEnd < 0
        val cursorMoved = !composingGone && (newSelStart != newSelEnd || newSelEnd != candidatesEnd)
        // Composing text can vanish because the app cleared the field (a chat app sending a
        // message), or this can be a late report from before the composition was set: ask.
        if (cursorMoved || composingGone && !editorShowsComposition()) {
            configuredEngine?.clear()
            composition = Composition.EMPTY
            keyboardView?.describer?.syllablePending = false
            currentInputConnection?.finishComposingText()
            closePanel()
            updateCandidates()
        }
    }

    private fun editorShowsComposition(): Boolean {
        val text = composition.text
        val before = currentInputConnection?.getTextBeforeCursor(text.length, 0) ?: return false
        return before.toString() == text
    }

    // ---- The keys ----

    override fun onKeyDown(key: Key) {
        feedback(key.action)
    }

    override fun onKey(key: Key, action: KeyAction) {
        hardwareKeyboardUsed = false
        perform(key, action)
    }

    override fun onLongPress(key: Key): Boolean {
        val action = key.longPress ?: return false
        hardwareKeyboardUsed = false
        perform(key, action)
        return true
    }

    private fun perform(key: Key, action: KeyAction) {
        when (action) {
            is KeyAction.Text -> typeText(action.text)
            is KeyAction.Zhuyin -> typeZhuyin(action.key, key.label.orEmpty())
            KeyAction.Delete -> delete()
            KeyAction.Enter -> enter()
            KeyAction.Space -> space()
            KeyAction.Shift -> pressShift()
            KeyAction.ToggleLanguage -> toggleLanguage()
            is KeyAction.ShowLayer -> {
                state.showLayer(action.layer)
                showKeyboard()
            }
            KeyAction.ShowBaseLayer -> {
                state.showBaseLayer()
                updateAutoShift(redraw = false)
                showKeyboard()
            }
            KeyAction.PickInputMethod -> pickInputMethod()
        }
    }

    private fun typeText(text: String) {
        commitComposition()
        commitTyped(text)
        if (state.effectiveShift.isShifted && text.any(Char::isLetter)) {
            val before = state.shift
            state.onLetterTyped()
            if (state.shift != before) showKeyboard()
        }
        updateAutoShift(redraw = true)
    }

    private fun typeZhuyin(chewingKey: Char, label: String) {
        val engine = engine()
        if (engine == null) {
            // Without the engine, the Zhuyin symbols themselves are all there is to type.
            commitTyped(label)
            return
        }
        syncComposition(engine, engine.typeKey(chewingKey).commit)
    }

    private fun delete() {
        val engine = configuredEngine
        if (engine != null && (!engine.composition.isEmpty || engine.isSelecting)) {
            syncComposition(engine, engine.pressSpecial(ChewingNative.KEY_BACKSPACE).commit)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            updateAutoShift(redraw = true)
        }
    }

    private fun enter() {
        val engine = configuredEngine
        if (engine != null && !engine.composition.isEmpty) {
            commitComposition()
            return
        }
        val info = currentInputEditorInfo
        val action = info?.let(EditorInfos::editorAction)
        if (action != null) {
            currentInputConnection?.performEditorAction(action)
        } else {
            commitTyped("\n")
        }
        updateAutoShift(redraw = true)
    }

    private fun space() {
        val engine = configuredEngine
        if (engine != null && state.language == Language.CHINESE && state.layer == Layer.LETTERS) {
            val current = engine.composition
            // Space types the first tone of a syllable being typed, and commits a phrase.
            if (current.bopomofo.isNotEmpty()) {
                syncComposition(engine, engine.pressSpecial(ChewingNative.KEY_SPACE).commit)
                return
            }
            if (current.preedit.isNotEmpty()) {
                commitComposition()
                return
            }
        }
        typeText(" ")
    }

    private fun pressShift() {
        state.pressShift()
        showKeyboard()
        val announcement = when (state.shift) {
            ShiftState.OFF, ShiftState.AUTO -> R.string.spoken_shift_off_announcement
            ShiftState.ON -> R.string.spoken_shift_on_announcement
            ShiftState.LOCKED -> R.string.spoken_caps_lock_announcement
        }
        keyboardView?.announce(getString(announcement))
    }

    private fun toggleLanguage() {
        commitComposition()
        state.toggleLanguage()
        val info = currentInputEditorInfo
        if (info == null || !EditorInfos.requiresEnglish(info.inputType)) prefs.lastLanguage = state.language
        updateAutoShift(redraw = false)
        showKeyboard()
        updateCandidates()
    }

    private fun pickInputMethod() {
        commitComposition()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    /** Types [text] into the editor; as key events into editors that only take those. */
    private fun commitTyped(text: String) {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (info != null && info.inputType == InputType.TYPE_NULL && text.length == 1) {
            sendKeyChar(text[0])
        } else {
            ic.commitText(text, 1)
        }
    }

    // ---- Composition ----

    /** The engine, configured with the current settings; null if it cannot be loaded. */
    private fun engine(): ChewingEngine? {
        val engine = ChewingEngineHolder.get(this)
        if (engine == null) {
            if (!engineUnavailableShown) {
                engineUnavailableShown = true
                Toast.makeText(this, R.string.engine_unavailable, Toast.LENGTH_LONG).show()
            }
            return null
        }
        if (engine !== configuredEngine) {
            engine.configure(prefs.zhuyinLayout, prefs.autoLearn)
            configuredEngine = engine
        }
        return engine
    }

    /**
     * Brings the editor in line with the engine: commits [commit], which the engine just
     * gave up, and shows what it is composing now as composing text.
     */
    private fun syncComposition(engine: ChewingEngine, commit: String) {
        val now = engine.composition
        currentInputConnection?.let { ic ->
            ic.beginBatchEdit()
            // Committing replaces the composing text.
            if (commit.isNotEmpty()) ic.commitText(commit, 1)
            if (!now.isEmpty) {
                ic.setComposingText(now.text, 1)
            } else if (commit.isEmpty() && !composition.isEmpty) {
                ic.commitText("", 1)
            }
            ic.endBatchEdit()
        }
        composition = now
        keyboardView?.describer?.syllablePending = now.bopomofo.isNotEmpty()
        updateCandidates()
    }

    /** Commits whatever is composed, as it shows, and closes any selection. */
    private fun commitComposition() {
        val engine = configuredEngine ?: return
        if (engine.composition.isEmpty && !engine.isSelecting) return
        val commit = engine.commitAll()
        currentInputConnection?.let { ic ->
            if (commit.isNotEmpty()) {
                ic.commitText(commit, 1)
            } else if (!composition.isEmpty) {
                ic.commitText("", 1)
            }
        }
        composition = Composition.EMPTY
        commitAfterSelection = false
        keyboardView?.describer?.syllablePending = false
        closePanel()
        updateCandidates()
    }

    // ---- Candidates ----

    private fun updateCandidates() {
        val engine = configuredEngine
        candidates = engine?.candidates().orEmpty()
        if (panelOpen) {
            if (candidates.isEmpty()) closePanel() else showPanel()
        }
        candidateBar?.showCandidates(
            if (panelOpen) emptyList() else candidates,
            showNumbers = hardwareKeyboardUsed && engine?.isSelecting == true,
        )
        if (hardwareKeyboardUsed) updateInputViewShown()
    }

    override fun onCandidateSelected(candidate: Candidate) {
        val engine = configuredEngine ?: return
        val commit = engine.choose(candidate)
        if (engine.isSelecting) {
            // A category of the symbol table: its symbols are the candidates now.
            syncComposition(engine, commit)
            return
        }
        closePanel()
        syncComposition(engine, commit)
        if (commitAfterSelection) commitComposition()
    }

    override fun onShowAllCandidates() {
        if (candidates.isEmpty()) return
        panelOpen = true
        showPanel()
        updateCandidates()
    }

    override fun onCloseCandidatePanel() {
        val engine = configuredEngine
        commitAfterSelection = false
        if (engine != null && engine.isSelecting) {
            engine.closeSelection()
            closePanel()
            syncComposition(engine, "")
        } else {
            closePanel()
            updateCandidates()
        }
    }

    override fun onToolSelected(tool: CandidateBarView.Tool) {
        when (tool) {
            CandidateBarView.Tool.SETTINGS -> {
                commitComposition()
                requestHideSelf(0)
                startActivity(
                    Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            CandidateBarView.Tool.SYMBOL_TABLE -> openSymbolTable()
            CandidateBarView.Tool.PICK_INPUT_METHOD -> pickInputMethod()
            CandidateBarView.Tool.HIDE_KEYBOARD -> {
                commitComposition()
                requestHideSelf(0)
            }
        }
    }

    private fun openSymbolTable() {
        val engine = engine() ?: return
        commitAfterSelection = engine.composition.isEmpty
        if (!engine.openSymbolTable()) return
        panelOpen = true
        syncComposition(engine, "")
    }

    private fun showPanel() {
        val panel = candidatePanel ?: return
        val title = if (configuredEngine?.isSelecting == true) {
            getString(R.string.candidates_symbol_table)
        } else {
            getString(R.string.candidates_title)
        }
        keyboardView?.visibility = View.INVISIBLE
        candidateBar?.visibility = View.INVISIBLE
        panel.visibility = View.VISIBLE
        panel.showCandidates(candidates, title)
    }

    private fun closePanel() {
        panelOpen = false
        candidatePanel?.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
        candidateBar?.visibility = View.VISIBLE
    }

    // ---- Keyboards ----

    private fun showKeyboard() {
        val view = keyboardView ?: return
        val info = currentInputEditorInfo ?: EditorInfo()
        val id = KeyboardId(
            language = state.language,
            layer = state.layer,
            baseLayer = state.baseLayer,
            zhuyinLayout = prefs.zhuyinLayout,
            shift = state.effectiveShift,
            enterKeyType = EditorInfos.enterKeyType(info.imeOptions),
            enterLabel = info.actionLabel?.toString(),
        )
        view.setKeyboard(keyboards.getOrPut(id) { KeyboardLayouts.build(id, keyLabels) })
    }

    /** Shifts the English letters at the beginning of a sentence, where the editor asks. */
    private fun updateAutoShift(redraw: Boolean) {
        if (state.language != Language.ENGLISH || state.layer != Layer.LETTERS) return
        val info = currentInputEditorInfo ?: return
        val capitalize = prefs.autoCapitalize && info.inputType != InputType.TYPE_NULL &&
            (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
        val before = state.shift
        state.updateAutoShift(capitalize)
        if (redraw && state.shift != before) showKeyboard()
    }

    private fun feedback(action: KeyAction) {
        if (prefs.vibrate) {
            keyboardView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (prefs.sound) {
            val effect = when (action) {
                KeyAction.Delete -> AudioManager.FX_KEYPRESS_DELETE
                KeyAction.Enter -> AudioManager.FX_KEYPRESS_RETURN
                KeyAction.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(effect, SOUND_VOLUME)
        }
    }

    // ---- Hardware keyboards ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleHardwareKey(keyCode, event)) {
            consumedKeyCodes.add(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (consumedKeyCodes.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Types a hardware keyboard's keys into the engine while typing Chinese. Keys the
     * engine has no use for go to the editor as usual.
     */
    private fun handleHardwareKey(keyCode: Int, event: KeyEvent): Boolean {
        if (currentInputConnection == null) return false
        val toggle = keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH ||
            keyCode == KeyEvent.KEYCODE_SPACE && event.isShiftPressed && !event.isCtrlPressed && !event.isAltPressed
        if (toggle) {
            toggleLanguage()
            return true
        }
        if (state.language != Language.CHINESE || state.layer != Layer.LETTERS) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            commitComposition()
            return false
        }
        val engine = engine() ?: return false
        val composing = !engine.composition.isEmpty || engine.isSelecting
        val special = HARDWARE_SPECIAL_KEYS[keyCode]
        val result = if (special != null) {
            if (!composing) return false
            val current = engine.composition
            if (special == ChewingNative.KEY_SPACE && !engine.isSelecting &&
                current.bopomofo.isEmpty() && current.preedit.isNotEmpty()
            ) {
                hardwareKeyboardUsed = true
                commitComposition()
                return true
            }
            engine.pressSpecial(special)
        } else {
            val char = event.unicodeChar
            if (char == 0 || Character.isISOControl(char) || char > Char.MAX_VALUE.code) return false
            engine.typeKey(char.toChar())
        }
        if (!result.handled && result.commit.isEmpty()) return false
        hardwareKeyboardUsed = true
        syncComposition(engine, result.commit)
        return true
    }

    companion object {
        private const val SOUND_VOLUME = -1f

        private val HARDWARE_SPECIAL_KEYS = mapOf(
            KeyEvent.KEYCODE_SPACE to ChewingNative.KEY_SPACE,
            KeyEvent.KEYCODE_ENTER to ChewingNative.KEY_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER to ChewingNative.KEY_ENTER,
            KeyEvent.KEYCODE_DEL to ChewingNative.KEY_BACKSPACE,
            KeyEvent.KEYCODE_FORWARD_DEL to ChewingNative.KEY_DELETE,
            KeyEvent.KEYCODE_ESCAPE to ChewingNative.KEY_ESC,
            KeyEvent.KEYCODE_DPAD_LEFT to ChewingNative.KEY_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to ChewingNative.KEY_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP to ChewingNative.KEY_UP,
            KeyEvent.KEYCODE_DPAD_DOWN to ChewingNative.KEY_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME to ChewingNative.KEY_HOME,
            KeyEvent.KEYCODE_MOVE_END to ChewingNative.KEY_END,
            KeyEvent.KEYCODE_PAGE_UP to ChewingNative.KEY_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN to ChewingNative.KEY_PAGE_DOWN,
            KeyEvent.KEYCODE_TAB to ChewingNative.KEY_TAB,
        )
    }
}
