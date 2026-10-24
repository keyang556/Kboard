// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.accessibility

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.LocaleSpan
import io.github.keyang556.kboard.R
import io.github.keyang556.kboard.keyboard.EnterKeyType
import io.github.keyang556.kboard.keyboard.Key
import io.github.keyang556.kboard.keyboard.KeyAction
import io.github.keyang556.kboard.keyboard.KeyboardId
import io.github.keyang556.kboard.keyboard.Language
import io.github.keyang556.kboard.keyboard.Layer
import io.github.keyang556.kboard.keyboard.ShiftState
import java.util.Locale

/**
 * What a screen reader says for each key: what the key types, or what it does. Zhuyin is
 * marked as Traditional Chinese, so TalkBack reads it with a Chinese voice whatever the
 * language of the system.
 */
class KeyDescriber(private val context: Context) {

    /** Whether the editor holds a password, whose keys are read as "dot". */
    var obscureInput = false

    /** Whether a Zhuyin syllable is being typed, so that Space types the first tone. */
    var syllablePending = false

    private val symbolNames: Map<String, String> by lazy {
        context.resources.getStringArray(R.array.symbol_names).associate { entry ->
            val symbolEnd = entry.offsetByCodePoints(0, 1)
            entry.substring(0, symbolEnd) to entry.substring(symbolEnd).trim()
        }
    }

    fun describe(key: Key, id: KeyboardId): CharSequence = when (val action = key.action) {
        is KeyAction.Text -> if (obscureInput) string(R.string.spoken_dot) else describeText(action.text)
        is KeyAction.Zhuyin -> if (obscureInput) string(R.string.spoken_dot) else describeZhuyin(key)
        KeyAction.Delete -> string(R.string.spoken_delete)
        KeyAction.Enter -> describeEnter(id)
        KeyAction.Space -> if (id.isZhuyin && syllablePending) {
            string(R.string.spoken_space_first_tone)
        } else {
            string(R.string.spoken_space)
        }
        KeyAction.Shift -> string(R.string.spoken_shift)
        KeyAction.ToggleLanguage -> if (id.language == Language.CHINESE) {
            string(R.string.spoken_to_english)
        } else {
            string(R.string.spoken_to_zhuyin)
        }
        is KeyAction.ShowLayer -> describeLayerKey(action.layer, id)
        KeyAction.ShowBaseLayer -> describeBaseLayerKey(id)
        KeyAction.PickInputMethod -> string(R.string.spoken_pick_input_method)
    }

    /** The state of a toggle key, which TalkBack reads after its description. */
    fun stateDescription(key: Key, id: KeyboardId): CharSequence? = when (key.action) {
        KeyAction.Shift -> when (id.shift) {
            ShiftState.OFF -> string(R.string.spoken_shift_off)
            ShiftState.AUTO, ShiftState.ON -> string(R.string.spoken_shift_on)
            ShiftState.LOCKED -> string(R.string.spoken_caps_lock_on)
        }
        else -> null
    }

    /** What a long press on [key] does, for TalkBack to offer as an action. */
    fun longPressLabel(key: Key): CharSequence? = when (val action = key.longPress) {
        is KeyAction.Text -> context.getString(R.string.spoken_long_press_type, describeText(action.text))
        KeyAction.PickInputMethod -> string(R.string.spoken_pick_input_method)
        else -> null
    }

    /** What a long press typed, to be announced. */
    fun describeTyped(action: KeyAction): CharSequence? = when (action) {
        is KeyAction.Text -> if (obscureInput) string(R.string.spoken_dot) else describeText(action.text)
        else -> null
    }

    /** The name of the keyboard [id], announced when it shows up. */
    fun keyboardName(id: KeyboardId): CharSequence = string(
        when (id.layer) {
            Layer.LETTERS -> if (id.language == Language.CHINESE) R.string.keyboard_zhuyin else R.string.keyboard_english
            Layer.SYMBOLS -> R.string.keyboard_symbols
            Layer.SYMBOLS_MORE -> R.string.keyboard_more_symbols
            Layer.FULL_WIDTH -> R.string.keyboard_full_width
            Layer.NUMBER -> R.string.keyboard_number
            Layer.PHONE -> R.string.keyboard_phone
        },
    )

    fun describeText(text: String): CharSequence {
        symbolNames[text]?.let { return it }
        return if (hasIdeographs(text)) {
            inChinese(text)
        } else {
            text
        }
    }

    private fun hasIdeographs(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isIdeographic(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun describeZhuyin(key: Key): CharSequence {
        // A key of a 26-key keyboard types several symbols: its label and those of its hint.
        val symbols = (key.label.orEmpty() + key.hint.orEmpty().takeIf { key.longPress == null }.orEmpty())
        val tones = symbols.filter { it.toString() in symbolNames }
        val letters = symbols.filterNot { it.toString() in symbolNames }
        val spokenLetters = inChinese(letters.toList().joinToString(" "))
        if (tones.isEmpty()) return spokenLetters
        val spokenTones = tones.map { symbolNames.getValue(it.toString()) }.joinToString(" ")
        return if (letters.isEmpty()) spokenTones else TextUtils.concat(spokenLetters, " ", spokenTones)
    }

    private fun describeEnter(id: KeyboardId): CharSequence {
        id.enterLabel?.takeIf { it.isNotBlank() }?.let { return it }
        return string(
            when (id.enterKeyType) {
                EnterKeyType.NEWLINE -> R.string.spoken_enter
                EnterKeyType.GO -> R.string.spoken_action_go
                EnterKeyType.SEARCH -> R.string.spoken_action_search
                EnterKeyType.SEND -> R.string.spoken_action_send
                EnterKeyType.NEXT -> R.string.spoken_action_next
                EnterKeyType.PREVIOUS -> R.string.spoken_action_previous
                EnterKeyType.DONE -> R.string.spoken_action_done
            },
        )
    }

    private fun describeLayerKey(target: Layer, id: KeyboardId): CharSequence = string(
        when (target) {
            Layer.SYMBOLS -> if (id.layer == Layer.FULL_WIDTH) R.string.spoken_to_half_width else R.string.spoken_to_symbols
            Layer.SYMBOLS_MORE -> R.string.spoken_to_more_symbols
            Layer.FULL_WIDTH -> R.string.spoken_to_full_width
            Layer.LETTERS, Layer.NUMBER, Layer.PHONE -> return describeBaseLayerKey(id)
        },
    )

    private fun describeBaseLayerKey(id: KeyboardId): CharSequence = string(
        when {
            id.baseLayer != Layer.LETTERS -> R.string.spoken_to_numbers
            id.language == Language.CHINESE -> R.string.spoken_to_zhuyin_keyboard
            else -> R.string.spoken_to_english_letters
        },
    )

    private fun string(resId: Int): String = context.getString(resId)

    companion object {
        /** [text], marked as Traditional Chinese for text-to-speech. */
        fun inChinese(text: CharSequence): CharSequence = SpannableString(text).apply {
            setSpan(LocaleSpan(Locale.TAIWAN), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
