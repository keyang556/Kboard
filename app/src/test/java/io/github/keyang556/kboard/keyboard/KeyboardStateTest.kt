// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardStateTest {

    private fun englishLetters() = KeyboardState().apply { startInput(Layer.LETTERS, Language.ENGLISH) }

    @Test
    fun shiftGoesOnThenLocksThenOff() {
        val state = englishLetters()
        state.pressShift()
        assertEquals(ShiftState.ON, state.shift)
        state.pressShift()
        assertEquals(ShiftState.LOCKED, state.shift)
        state.pressShift()
        assertEquals(ShiftState.OFF, state.shift)
    }

    @Test
    fun aLetterUsesUpShiftButNotCapsLock() {
        val state = englishLetters()
        state.pressShift()
        state.onLetterTyped()
        assertEquals(ShiftState.OFF, state.shift)

        state.pressShift()
        state.pressShift()
        state.onLetterTyped()
        assertEquals(ShiftState.LOCKED, state.shift)
    }

    @Test
    fun autoShiftFollowsTheEditorUnlessTheUserChose() {
        val state = englishLetters()
        state.updateAutoShift(capitalize = true)
        assertEquals(ShiftState.AUTO, state.shift)
        state.updateAutoShift(capitalize = false)
        assertEquals(ShiftState.OFF, state.shift)

        state.pressShift()
        state.updateAutoShift(capitalize = false)
        assertEquals(ShiftState.ON, state.shift)
    }

    @Test
    fun pressingShiftWhenAutoShiftedTurnsItOff() {
        val state = englishLetters()
        state.updateAutoShift(capitalize = true)
        state.pressShift()
        assertEquals(ShiftState.OFF, state.shift)
    }

    @Test
    fun shiftOnlyAppliesToEnglishLetters() {
        val state = englishLetters()
        state.pressShift()
        state.showLayer(Layer.SYMBOLS)
        assertEquals(ShiftState.OFF, state.effectiveShift)

        val chinese = KeyboardState().apply { startInput(Layer.LETTERS, Language.CHINESE) }
        chinese.pressShift()
        assertEquals(ShiftState.OFF, chinese.effectiveShift)
    }

    @Test
    fun toggleLanguageReturnsToLetters() {
        val state = KeyboardState().apply { startInput(Layer.LETTERS, Language.CHINESE) }
        state.toggleLanguage()
        assertEquals(Language.ENGLISH, state.language)
        assertEquals(Layer.LETTERS, state.layer)
        state.toggleLanguage()
        assertEquals(Language.CHINESE, state.language)
    }

    @Test
    fun theBaseLayerIsWhereSymbolsReturnTo() {
        val state = KeyboardState().apply { startInput(Layer.NUMBER, Language.CHINESE) }
        assertEquals(Layer.NUMBER, state.layer)
        state.showLayer(Layer.SYMBOLS)
        state.showBaseLayer()
        assertEquals(Layer.NUMBER, state.layer)
    }
}
