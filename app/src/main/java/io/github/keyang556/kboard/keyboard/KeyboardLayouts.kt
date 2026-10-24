// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.keyboard

import android.content.res.Resources
import io.github.keyang556.kboard.R
import io.github.keyang556.kboard.engine.ZhuyinLayout

/** The labels of keys that switch keyboards, which are translated. */
data class KeyLabels(
    val symbols: String,
    val moreSymbols: String,
    val fullWidth: String,
    val halfWidth: String,
    val toEnglishLetters: String,
    val toZhuyin: String,
    val toNumbers: String,
    val toEnglish: String,
    val toChinese: String,
    val spaceChinese: String,
    val spaceEnglish: String,
) {
    companion object {
        fun from(resources: Resources) = KeyLabels(
            symbols = resources.getString(R.string.key_label_symbols),
            moreSymbols = resources.getString(R.string.key_label_more_symbols),
            fullWidth = resources.getString(R.string.key_label_full_width),
            halfWidth = resources.getString(R.string.key_label_half_width),
            toEnglishLetters = resources.getString(R.string.key_label_english_letters),
            toZhuyin = resources.getString(R.string.key_label_zhuyin),
            toNumbers = resources.getString(R.string.key_label_numbers),
            toEnglish = resources.getString(R.string.key_label_to_english),
            toChinese = resources.getString(R.string.key_label_to_chinese),
            spaceChinese = resources.getString(R.string.key_label_space_chinese),
            spaceEnglish = resources.getString(R.string.key_label_space_english),
        )
    }
}

/** Every keyboard, built from its [KeyboardId]. */
object KeyboardLayouts {

    fun build(id: KeyboardId, labels: KeyLabels): Keyboard = when (id.layer) {
        Layer.LETTERS -> if (id.language == Language.CHINESE) zhuyin(id, labels) else english(id, labels)
        Layer.SYMBOLS -> symbolPage(id, labels, SYMBOLS_ROWS, pageKey(Layer.SYMBOLS_MORE, labels.moreSymbols))
        Layer.SYMBOLS_MORE -> symbolPage(id, labels, MORE_SYMBOLS_ROWS, pageKey(Layer.SYMBOLS, labels.symbols))
        Layer.FULL_WIDTH -> symbolPage(id, labels, FULL_WIDTH_ROWS, pageKey = null)
        Layer.NUMBER -> numberPad(id, labels)
        Layer.PHONE -> phonePad(id)
    }

    // Each key is written as the QWERTY key libchewing is sent, followed by the Zhuyin
    // symbols it types: the first is its label, the others its hint.
    private val DACHEN_ROWS = listOf(
        "1ㄅ 2ㄉ 3ˇ 4ˋ 5ㄓ 6ˊ 7˙ 8ㄚ 9ㄞ 0ㄢ -ㄦ",
        "qㄆ wㄊ eㄍ rㄐ tㄔ yㄗ uㄧ iㄛ oㄟ pㄣ",
        "aㄇ sㄋ dㄎ fㄑ gㄕ hㄘ jㄨ kㄜ lㄠ ;ㄤ",
        "zㄈ xㄌ cㄏ vㄒ bㄖ nㄙ mㄩ ,ㄝ .ㄡ /ㄥ",
    )
    private val ET26_ROWS = listOf(
        "qㄗㄟ wㄘㄝ eㄧ rㄜ tㄊㄤ yㄔ uㄩ iㄞ oㄛ pㄆㄡ",
        "aㄚ sㄙ dㄉ˙ fㄈˊ gㄐㄓ hㄏㄦ jㄖˇ kㄎˋ lㄌㄥ",
        "zㄠ xㄨ cㄒㄕ vㄍㄑ bㄅ nㄋㄣ mㄇㄢ",
    )
    private val HSU_ROWS = listOf(
        "wㄠ eㄧㄝ rㄖ tㄊ yㄚ uㄩ iㄞ oㄡ pㄆ",
        "aㄘㄟ sㄙ˙ dㄉˊ fㄈˇ gㄍㄜ hㄏㄛ jㄓㄐˋ kㄎㄤ lㄌㄥㄦ",
        "zㄗ xㄨ cㄕㄒ vㄔㄑ bㄅ nㄋㄣ mㄇㄢ",
    )

    // Long pressing these keys of the top row types a digit.
    private val DIGIT_KEYS = mapOf(
        '1' to "1", '2' to "2", '3' to "3", '4' to "4", '5' to "5",
        '6' to "6", '7' to "7", '8' to "8", '9' to "9", '0' to "0",
    )
    private val QWERTY_DIGITS = mapOf(
        'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
        'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
    )

    private val SYMBOLS_ROWS = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?"),
    )
    private val MORE_SYMBOLS_ROWS = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"),
        listOf("_", "<", ">", "[", "]", "©", "®"),
    )
    private val FULL_WIDTH_ROWS = listOf(
        listOf("，", "。", "、", "；", "：", "？", "！", "…", "—", "～"),
        listOf("「", "」", "『", "』", "（", "）", "《", "》", "〈", "〉"),
        listOf("·", "“", "”", "‘", "’", "【", "】", "※"),
    )

    private fun zhuyin(id: KeyboardId, labels: KeyLabels): Keyboard {
        val rows = when (id.zhuyinLayout) {
            ZhuyinLayout.DACHEN -> DACHEN_ROWS
            ZhuyinLayout.ET26 -> ET26_ROWS
            ZhuyinLayout.HSU -> HSU_ROWS
        }
        // Only 大千 has a key per symbol, leaving the corner of its top row free for digits.
        val digits = if (id.zhuyinLayout == ZhuyinLayout.DACHEN) DIGIT_KEYS else emptyMap()
        val keyRows = rows.map { row -> row.split(' ').map { zhuyinKey(it, digits) } }.toMutableList()
        // Delete closes the last row of Zhuyin keys.
        keyRows[keyRows.lastIndex] = keyRows.last() + deleteKey(if (id.zhuyinLayout == ZhuyinLayout.DACHEN) 1f else 1.5f)
        val columns = if (id.zhuyinLayout == ZhuyinLayout.DACHEN) 11f else 10f
        val enterWidth = if (id.zhuyinLayout == ZhuyinLayout.DACHEN) 2.5f else 2f
        val bottom = listOf(
            functionKey(KeyAction.ShowLayer(Layer.SYMBOLS), labels.symbols, 1.5f),
            Key(
                KeyAction.ToggleLanguage, label = labels.toEnglish, width = 1.25f,
                style = KeyStyle.FUNCTION, longPress = KeyAction.PickInputMethod,
            ),
            textKey("，", longPress = "、"),
            // The space bar takes what the other keys of the row leave.
            spaceKey(labels.spaceChinese, columns - (1.5f + 1.25f + 1f + 1f + enterWidth)),
            textKey("。", longPress = "？"),
            enterKey(id, enterWidth),
        )
        return Keyboard(id, keyRows + listOf(bottom), columns)
    }

    private fun zhuyinKey(spec: String, digits: Map<Char, String>): Key {
        val chewingKey = spec[0]
        val symbols = spec.substring(1)
        val digit = digits[chewingKey]
        return Key(
            action = KeyAction.Zhuyin(chewingKey),
            label = symbols.substring(0, 1),
            hint = digit ?: symbols.substring(1).ifEmpty { null },
            longPress = digit?.let { KeyAction.Text(it) },
        )
    }

    private fun english(id: KeyboardId, labels: KeyLabels): Keyboard {
        val shifted = id.shift.isShifted
        fun letters(row: String) = row.map { letter ->
            val text = if (shifted) letter.uppercase() else letter.toString()
            val digit = QWERTY_DIGITS[letter]
            Key(KeyAction.Text(text), label = text, hint = digit, longPress = digit?.let { KeyAction.Text(it) })
        }
        val shiftIcon = when (id.shift) {
            ShiftState.OFF -> R.drawable.ic_key_shift
            ShiftState.AUTO, ShiftState.ON -> R.drawable.ic_key_shift_on
            ShiftState.LOCKED -> R.drawable.ic_key_shift_locked
        }
        val rows = listOf(
            letters("qwertyuiop"),
            letters("asdfghjkl"),
            listOf(Key(KeyAction.Shift, icon = shiftIcon, width = 1.5f, style = KeyStyle.FUNCTION)) +
                letters("zxcvbnm") + deleteKey(1.5f),
            listOf(
                functionKey(KeyAction.ShowLayer(Layer.SYMBOLS), labels.symbols, 1.5f),
                Key(
                    KeyAction.ToggleLanguage, label = labels.toChinese, width = 1.25f,
                    style = KeyStyle.FUNCTION, longPress = KeyAction.PickInputMethod,
                ),
                textKey(","),
                spaceKey(labels.spaceEnglish, 3.25f),
                textKey("."),
                enterKey(id, 2f),
            ),
        )
        return Keyboard(id, rows, 10f)
    }

    private fun pageKey(layer: Layer, label: String) =
        functionKey(KeyAction.ShowLayer(layer), label, 1.5f)

    /**
     * A page of symbols: two rows of ten, a third row between [pageKey] (or one more symbol)
     * and Delete, and a bottom row like the letters'.
     */
    private fun symbolPage(id: KeyboardId, labels: KeyLabels, symbols: List<List<String>>, pageKey: Key?): Keyboard {
        val isFullWidth = id.layer == Layer.FULL_WIDTH
        val thirdRow = buildList {
            pageKey?.let(::add)
            addAll(symbols[2].map { textKey(it) })
            add(deleteKey(if (pageKey == null) 2f else 1.5f))
        }
        val widthToggle = if (isFullWidth) {
            functionKey(KeyAction.ShowLayer(Layer.SYMBOLS), labels.halfWidth, 1.25f)
        } else {
            functionKey(KeyAction.ShowLayer(Layer.FULL_WIDTH), labels.fullWidth, 1.25f)
        }
        val bottom = listOf(
            backKey(id, labels, 1.5f),
            widthToggle,
            textKey(if (isFullWidth) "，" else ","),
            spaceKey(if (id.language == Language.CHINESE) labels.spaceChinese else labels.spaceEnglish, 3.25f),
            textKey(if (isFullWidth) "。" else "."),
            enterKey(id, 2f),
        )
        val rows = listOf(symbols[0].map { textKey(it) }, symbols[1].map { textKey(it) }, thirdRow, bottom)
        return Keyboard(id, rows, 10f)
    }

    private fun numberPad(id: KeyboardId, labels: KeyLabels): Keyboard {
        val rows = listOf(
            listOf(textKey("1"), textKey("2"), textKey("3"), textKey("-")),
            listOf(textKey("4"), textKey("5"), textKey("6"), textKey(",")),
            listOf(textKey("7"), textKey("8"), textKey("9"), deleteKey(1f)),
            listOf(functionKey(KeyAction.ShowLayer(Layer.SYMBOLS), labels.symbols, 1f), textKey("0"), textKey("."), enterKey(id, 1f)),
        )
        return Keyboard(id, rows, 4f)
    }

    private fun phonePad(id: KeyboardId): Keyboard {
        val rows = listOf(
            listOf(textKey("1"), textKey("2"), textKey("3"), textKey("-")),
            listOf(textKey("4"), textKey("5"), textKey("6"), textKey("+")),
            listOf(textKey("7"), textKey("8"), textKey("9"), deleteKey(1f)),
            listOf(textKey("*"), textKey("0"), textKey("#"), enterKey(id, 1f)),
        )
        return Keyboard(id, rows, 4f)
    }

    private fun textKey(text: String, longPress: String? = null) =
        Key(KeyAction.Text(text), label = text, hint = longPress, longPress = longPress?.let { KeyAction.Text(it) })

    private fun functionKey(action: KeyAction, label: String, width: Float) =
        Key(action, label = label, width = width, style = KeyStyle.FUNCTION)

    private fun deleteKey(width: Float) =
        Key(KeyAction.Delete, icon = R.drawable.ic_key_delete, width = width, style = KeyStyle.FUNCTION)

    private fun spaceKey(label: String, width: Float) =
        Key(KeyAction.Space, label = label, width = width, longPress = KeyAction.PickInputMethod)

    private fun backKey(id: KeyboardId, labels: KeyLabels, width: Float): Key {
        val label = when {
            id.baseLayer != Layer.LETTERS -> labels.toNumbers
            id.language == Language.CHINESE -> labels.toZhuyin
            else -> labels.toEnglishLetters
        }
        return functionKey(KeyAction.ShowBaseLayer, label, width)
    }

    private fun enterKey(id: KeyboardId, width: Float): Key {
        val icon = when (id.enterKeyType) {
            EnterKeyType.NEWLINE -> R.drawable.ic_key_enter
            EnterKeyType.SEARCH -> R.drawable.ic_key_search
            EnterKeyType.SEND -> R.drawable.ic_key_send
            EnterKeyType.GO, EnterKeyType.NEXT -> R.drawable.ic_key_next
            EnterKeyType.PREVIOUS -> R.drawable.ic_key_previous
            EnterKeyType.DONE -> R.drawable.ic_key_done
        }
        return if (id.enterLabel.isNullOrBlank()) {
            Key(KeyAction.Enter, icon = icon, width = width, style = KeyStyle.ACTION)
        } else {
            Key(KeyAction.Enter, label = id.enterLabel, width = width, style = KeyStyle.ACTION)
        }
    }
}
