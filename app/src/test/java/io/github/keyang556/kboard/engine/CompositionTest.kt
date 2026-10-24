// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionTest {

    @Test
    fun theSyllableBeingTypedShowsAtTheCursor() {
        assertEquals("你ㄏㄠ", Composition("你", "ㄏㄠ", cursor = 1).text)
        assertEquals("ㄏㄠ你", Composition("你", "ㄏㄠ", cursor = 0).text)
        assertEquals("你好", Composition("你好", "", cursor = 2).text)
    }

    @Test
    fun theCursorCountsCharactersNotUtf16Units() {
        // An emoji takes two UTF-16 units but is one character to libchewing.
        assertEquals("😀ㄅ好", Composition("😀好", "ㄅ", cursor = 1).text)
    }

    @Test
    fun anOutOfRangeCursorIsClamped() {
        assertEquals("你ㄅ", Composition("你", "ㄅ", cursor = 5).text)
        assertEquals(2, Composition("你", "ㄅ", cursor = 5).textCursor)
    }

    @Test
    fun theEditorCursorFollowsTheSyllableBeingTyped() {
        assertEquals(3, Composition("你", "ㄏㄠ", cursor = 1).textCursor)
        assertEquals(2, Composition("你", "ㄏㄠ", cursor = 0).textCursor)
        assertEquals(1, Composition("你好", "", cursor = 1).textCursor)
        assertEquals(2, Composition("你好", "", cursor = 2).textCursor)
        // Past the emoji's two UTF-16 units.
        assertEquals(3, Composition("😀好", "ㄅ", cursor = 1).textCursor)
    }

    @Test
    fun emptyMeansNothingAtAll() {
        assertTrue(Composition.EMPTY.isEmpty)
        assertEquals(false, Composition("", "ㄅ", 0).isEmpty)
    }
}
