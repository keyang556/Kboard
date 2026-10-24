// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.keyboard

import io.github.keyang556.kboard.engine.ZhuyinLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutsTest {

    private val labels = KeyLabels(
        symbols = "?123", moreSymbols = "=\\<", fullWidth = "全形", halfWidth = "半形",
        toEnglishLetters = "ABC", toZhuyin = "ㄅㄆㄇ", toNumbers = "123", toEnglish = "英",
        toChinese = "中", spaceChinese = "注音", spaceEnglish = "English",
    )

    private fun id(layer: Layer, language: Language = Language.CHINESE, layout: ZhuyinLayout = ZhuyinLayout.DACHEN) =
        KeyboardId(language, layer, Layer.LETTERS, layout, ShiftState.OFF, EnterKeyType.NEWLINE, enterLabel = null)

    private fun build(id: KeyboardId) = KeyboardLayouts.build(id, labels)

    // The 37 Zhuyin symbols and the four tone marks other than the first (which is Space).
    private val zhuyin = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦˊˇˋ˙".toSet()

    @Test
    fun dachenHasEverySymbolOnAKeyOfItsOwn() {
        val keyboard = build(id(Layer.LETTERS))
        val labelsOfZhuyinKeys = keyboard.keys.filter { it.action is KeyAction.Zhuyin }.map { it.label }
        assertEquals(zhuyin.size, labelsOfZhuyinKeys.size)
        assertEquals(zhuyin, labelsOfZhuyinKeys.map { it!!.single() }.toSet())
    }

    @Test
    fun the26KeyLayoutsCoverEverySymbol() {
        for (layout in listOf(ZhuyinLayout.ET26, ZhuyinLayout.HSU)) {
            val keyboard = build(id(Layer.LETTERS, layout = layout))
            val typed = keyboard.keys.filter { it.action is KeyAction.Zhuyin }
                .flatMap { (it.label.orEmpty() + it.hint.orEmpty()).toList() }
                .toSet()
            assertEquals("$layout", zhuyin, typed)
        }
    }

    @Test
    fun zhuyinKeysSendTheirQwertyKeysToTheEngine() {
        val keyboard = build(id(Layer.LETTERS))
        val bo = keyboard.keys.single { it.label == "ㄅ" }
        assertEquals(KeyAction.Zhuyin('1'), bo.action)
        assertEquals(KeyAction.Text("1"), bo.longPress)
        val er = keyboard.keys.single { it.label == "ㄦ" }
        assertEquals(KeyAction.Zhuyin('-'), er.action)
    }

    @Test
    fun englishHasTheAlphabetAndFollowsShift() {
        val lower = build(id(Layer.LETTERS, Language.ENGLISH))
        val letters = lower.keys.mapNotNull { (it.action as? KeyAction.Text)?.text }.filter { it.single().isLetter() }
        assertEquals(('a'..'z').map(Char::toString).toSet(), letters.toSet())

        val upper = build(id(Layer.LETTERS, Language.ENGLISH).copy(shift = ShiftState.ON))
        assertTrue(upper.keys.any { it.action == KeyAction.Text("Q") })
    }

    @Test
    fun theBottomRowSpansTheWholeKeyboard() {
        for (layer in Layer.entries) {
            for (language in Language.entries) {
                for (layout in ZhuyinLayout.entries) {
                    val keyboard = build(id(layer, language, layout))
                    val widest = keyboard.rows.maxOf { row -> row.sumOf { it.width.toDouble() } }
                    val bottom = keyboard.rows.last().sumOf { it.width.toDouble() }
                    assertEquals("$layer $language $layout", widest, bottom, 1e-6)
                }
            }
        }
    }

    @Test
    fun everyKeyboardCanDeleteAndBeLeft() {
        for (layer in Layer.entries) {
            val keyboard = build(id(layer))
            assertTrue("$layer has Delete", keyboard.keys.any { it.action == KeyAction.Delete })
            assertTrue("$layer has Enter", keyboard.keys.any { it.action == KeyAction.Enter })
            val leaves = keyboard.keys.any {
                it.action is KeyAction.ShowLayer || it.action == KeyAction.ShowBaseLayer
            }
            if (layer != Layer.PHONE) assertTrue("$layer can switch keyboards", leaves)
        }
    }

    @Test
    fun theSymbolPagesHaveFullWidthPunctuation() {
        val fullWidth = build(id(Layer.FULL_WIDTH))
        val texts = fullWidth.keys.mapNotNull { (it.action as? KeyAction.Text)?.text }
        assertTrue(texts.containsAll(listOf("，", "。", "、", "？", "！", "「", "」")))
    }
}
