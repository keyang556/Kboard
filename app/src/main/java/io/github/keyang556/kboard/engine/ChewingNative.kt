// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.engine

/**
 * The libchewing functions the input method uses, implemented in `kboard_jni.cpp`.
 *
 * Every function but [open] and [version] takes the context pointer [open] returned; a 0
 * pointer makes them do nothing. They must all be called from the main thread.
 */
internal object ChewingNative {
    // Keys handed to [handleSpecial]; the same numbers as SpecialKey in kboard_jni.cpp.
    const val KEY_SPACE = 1
    const val KEY_ENTER = 2
    const val KEY_BACKSPACE = 3
    const val KEY_ESC = 4
    const val KEY_DELETE = 5
    const val KEY_LEFT = 6
    const val KEY_RIGHT = 7
    const val KEY_UP = 8
    const val KEY_DOWN = 9
    const val KEY_HOME = 10
    const val KEY_END = 11
    const val KEY_PAGE_UP = 12
    const val KEY_PAGE_DOWN = 13
    const val KEY_TAB = 14

    // Bits of what [handleKey] and [handleSpecial] return.
    const val STATUS_IGNORE = 1
    const val STATUS_COMMIT = 2
    const val STATUS_ABSORB = 8

    init {
        System.loadLibrary("kboard_jni")
    }

    @JvmStatic external fun open(systemDir: String, userDictionary: String): Long
    @JvmStatic external fun close(context: Long)
    @JvmStatic external fun setKeyboardType(context: Long, type: Int): Int
    @JvmStatic external fun configSetInt(context: Long, name: String, value: Int): Int
    @JvmStatic external fun handleKey(context: Long, key: Int): Int
    @JvmStatic external fun handleSpecial(context: Long, key: Int): Int
    @JvmStatic external fun takeCommitString(context: Long): String
    @JvmStatic external fun preeditString(context: Long): String
    @JvmStatic external fun bopomofoString(context: Long): String
    @JvmStatic external fun cursor(context: Long): Int
    @JvmStatic external fun commitPreedit(context: Long): Int
    @JvmStatic external fun clear(context: Long)
    @JvmStatic external fun isSelecting(context: Long): Boolean
    @JvmStatic external fun openCandidates(context: Long): Int
    @JvmStatic external fun closeCandidates(context: Long): Int
    @JvmStatic external fun candidates(context: Long): Array<String>
    @JvmStatic external fun chooseCandidate(context: Long, index: Int): Int
    @JvmStatic external fun peekCandidateLists(context: Long): Array<Array<String>>
    @JvmStatic external fun chooseFromList(context: Long, list: Int, index: Int): Int
    @JvmStatic external fun version(): String
}
