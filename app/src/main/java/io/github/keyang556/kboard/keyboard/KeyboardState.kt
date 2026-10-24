// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.keyboard

/**
 * Which keyboard is showing, and how pressing the keys that switch keyboards changes it.
 * [baseLayer] is the keyboard the editor asked for: letters, or a number or phone pad.
 */
class KeyboardState {
    var language = Language.CHINESE
        private set
    var layer = Layer.LETTERS
        private set
    var baseLayer = Layer.LETTERS
        private set
    var shift = ShiftState.OFF
        private set

    /** Shift only exists on the English letters. */
    val effectiveShift: ShiftState
        get() = if (layer == Layer.LETTERS && language == Language.ENGLISH) shift else ShiftState.OFF

    fun startInput(baseLayer: Layer, language: Language) {
        this.baseLayer = baseLayer
        this.language = language
        layer = baseLayer
        shift = ShiftState.OFF
    }

    fun toggleLanguage() {
        language = if (language == Language.CHINESE) Language.ENGLISH else Language.CHINESE
        layer = Layer.LETTERS
        shift = ShiftState.OFF
    }

    fun showLayer(target: Layer) {
        layer = target
        shift = ShiftState.OFF
    }

    fun showBaseLayer() {
        showLayer(baseLayer)
    }

    /**
     * The user pressed Shift: off turns it on for one letter, pressing again locks it, and
     * a third press turns it off. Shift that came on by itself is turned off.
     */
    fun pressShift() {
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.AUTO -> ShiftState.OFF
            ShiftState.ON -> ShiftState.LOCKED
            ShiftState.LOCKED -> ShiftState.OFF
        }
    }

    /** A letter was typed, which uses up a Shift that is not locked. */
    fun onLetterTyped() {
        if (shift == ShiftState.ON || shift == ShiftState.AUTO) shift = ShiftState.OFF
    }

    /** Follows the editor's wish for a capital letter at the cursor, unless the user chose. */
    fun updateAutoShift(capitalize: Boolean) {
        shift = when {
            shift == ShiftState.OFF && capitalize -> ShiftState.AUTO
            shift == ShiftState.AUTO && !capitalize -> ShiftState.OFF
            else -> shift
        }
    }
}
