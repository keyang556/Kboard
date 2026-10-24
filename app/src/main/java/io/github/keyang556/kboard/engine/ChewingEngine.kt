// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.engine

/** The Zhuyin keyboard arrangements on offer, with the number libchewing knows each by. */
enum class ZhuyinLayout(val chewingType: Int) {
    /** 大千, the standard arrangement printed on Taiwanese keyboards. */
    DACHEN(0),

    /** 倚天 26 鍵. */
    ET26(5),

    /** 許氏. */
    HSU(1),
}

/**
 * One candidate. [list] is the candidate list libchewing put it in, longest phrases first,
 * or [SELECTION] for a candidate of the selection libchewing has open (the symbol table,
 * or one opened from a hardware keyboard); [index] is its place in that list.
 */
data class Candidate(val text: String, val list: Int, val index: Int) {
    companion object {
        const val SELECTION = -1
    }
}

/** What is being composed, as libchewing has it. */
data class Composition(val preedit: String, val bopomofo: String, val cursor: Int) {
    val isEmpty: Boolean get() = preedit.isEmpty() && bopomofo.isEmpty()

    /** The preedit with the syllable being typed shown at the cursor, as the editor shows it. */
    val text: String
        get() {
            if (bopomofo.isEmpty()) return preedit
            val cursorCodePoints = cursor.coerceIn(0, preedit.codePointCount(0, preedit.length))
            val offset = preedit.offsetByCodePoints(0, cursorCodePoints)
            return preedit.substring(0, offset) + bopomofo + preedit.substring(offset)
        }

    companion object {
        val EMPTY = Composition("", "", 0)
    }
}

/** What a key press did: whether libchewing used the key, and what it has to commit. */
data class KeyResult(val handled: Boolean, val commit: String)

/**
 * The Zhuyin engine: a libchewing context, which turns the keys of a Zhuyin keyboard into
 * Chinese text. Must only be used from the main thread.
 */
class ChewingEngine internal constructor(private var context: Long) : AutoCloseable {

    fun configure(layout: ZhuyinLayout, autoLearn: Boolean) {
        ChewingNative.setKeyboardType(context, layout.chewingType)
        setOption("chewing.language_mode", CHINESE_MODE)
        setOption("chewing.character_form", HALF_WIDTH)
        setOption("chewing.candidates_per_page", MAX_SELECTION_KEYS)
        setOption("chewing.auto_commit_threshold", AUTO_COMMIT_THRESHOLD)
        // Candidates are for the phrase that ends at the cursor, which is where one types.
        setOption("chewing.phrase_choice_rearward", 1)
        setOption("chewing.auto_shift_cursor", 1)
        setOption("chewing.esc_clear_all_buffer", 1)
        setOption("chewing.space_is_select_key", 0)
        setOption("chewing.easy_symbol_input", 0)
        setOption("chewing.disable_auto_learn_phrase", if (autoLearn) 0 else 1)
    }

    /** Types [key], a key of a QWERTY keyboard, as the Zhuyin keyboard in use reads it. */
    fun typeKey(key: Char): KeyResult = result(ChewingNative.handleKey(context, key.code))

    /** Presses a key other than a character; one of the ChewingNative.KEY_ constants. */
    fun pressSpecial(key: Int): KeyResult = result(ChewingNative.handleSpecial(context, key))

    val composition: Composition
        get() = Composition(
            preedit = ChewingNative.preeditString(context),
            bopomofo = ChewingNative.bopomofoString(context),
            cursor = ChewingNative.cursor(context),
        )

    /** Whether libchewing has a candidate selection open, rather than just being typed into. */
    val isSelecting: Boolean get() = ChewingNative.isSelecting(context)

    /**
     * The candidates to offer: those of the open selection, if there is one, or else every
     * candidate list for the phrase ending at the cursor, longest phrases first.
     */
    fun candidates(): List<Candidate> {
        if (isSelecting) {
            return ChewingNative.candidates(context).mapIndexed { index, text ->
                Candidate(text, Candidate.SELECTION, index)
            }
        }
        if (ChewingNative.bopomofoString(context).isNotEmpty()) return emptyList()
        if (ChewingNative.preeditString(context).isEmpty()) return emptyList()
        val seen = HashSet<String>()
        return buildList {
            ChewingNative.peekCandidateLists(context).forEachIndexed { list, texts ->
                texts.forEachIndexed { index, text ->
                    if (text.isNotEmpty() && seen.add(text)) add(Candidate(text, list, index))
                }
            }
        }
    }

    /** Chooses [candidate], returning anything this made libchewing commit. */
    fun choose(candidate: Candidate): String {
        if (candidate.list == Candidate.SELECTION) {
            ChewingNative.chooseCandidate(context, candidate.index)
        } else {
            ChewingNative.chooseFromList(context, candidate.list, candidate.index)
        }
        return ChewingNative.takeCommitString(context)
    }

    /** Opens libchewing's symbol table, whose categories then are [candidates]. */
    fun openSymbolTable(): Boolean {
        ChewingNative.closeCandidates(context)
        ChewingNative.handleKey(context, SYMBOL_TABLE_KEY.code)
        return isSelecting
    }

    fun closeSelection() {
        ChewingNative.closeCandidates(context)
    }

    /**
     * Commits everything composed so far, dropping a syllable still being typed, and
     * returns the text to commit.
     */
    fun commitAll(): String {
        ChewingNative.closeCandidates(context)
        ChewingNative.commitPreedit(context)
        val commit = ChewingNative.takeCommitString(context)
        ChewingNative.clear(context)
        return commit
    }

    /** Forgets everything composed so far. */
    fun clear() {
        ChewingNative.clear(context)
        ChewingNative.takeCommitString(context)
    }

    /** Hands the context back to libchewing, which then saves what it has learned. */
    override fun close() {
        if (context == 0L) return
        ChewingNative.close(context)
        context = 0
    }

    private fun setOption(name: String, value: Int) {
        ChewingNative.configSetInt(context, name, value)
    }

    private fun result(status: Int) = KeyResult(
        handled = status and ChewingNative.STATUS_IGNORE == 0,
        commit = ChewingNative.takeCommitString(context),
    )

    companion object {
        private const val CHINESE_MODE = 1
        private const val HALF_WIDTH = 0
        private const val MAX_SELECTION_KEYS = 10

        // How long the preedit may grow before its beginning is committed.
        private const val AUTO_COMMIT_THRESHOLD = 16

        // The key that opens libchewing's symbol table.
        private const val SYMBOL_TABLE_KEY = '`'

        fun version(): String = ChewingNative.version()
    }
}
