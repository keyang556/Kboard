// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.keyboard

import io.github.keyang556.kboard.engine.ZhuyinLayout
import kotlin.math.roundToInt

/** Everything a keyboard is built from; two keyboards with equal ids are the same. */
data class KeyboardId(
    val language: Language,
    val layer: Layer,
    val baseLayer: Layer,
    val zhuyinLayout: ZhuyinLayout,
    val shift: ShiftState,
    val enterKeyType: EnterKeyType,
    val enterLabel: String?,
) {
    val isZhuyin: Boolean get() = layer == Layer.LETTERS && language == Language.CHINESE
    val isEnglishLetters: Boolean get() = layer == Layer.LETTERS && language == Language.ENGLISH
}

/**
 * Rows of [Key]s. [columns] is how many keys of width 1 fill a row; shorter rows are
 * centred. [layout] places the keys for a given size.
 */
class Keyboard(val id: KeyboardId, val rows: List<List<Key>>, private val columns: Float) {
    val keys: List<Key> = rows.flatten()

    var width = 0
        private set
    var height = 0
        private set

    /**
     * Places the keys in a [width] wide keyboard of [rowHeight] high rows. Keys are drawn
     * [gap] apart, but every point of the keyboard belongs to a key's hit box, so no touch
     * falls between keys.
     */
    fun layout(width: Int, rowHeight: Int, gap: Int, padding: Int) {
        this.width = width
        height = rows.size * rowHeight + 2 * padding
        val unit = (width - 2 * padding) / columns
        rows.forEachIndexed { rowIndex, row ->
            val top = padding + rowIndex * rowHeight
            val bottom = top + rowHeight
            val hitTop = if (rowIndex == 0) 0 else top
            val hitBottom = if (rowIndex == rows.lastIndex) height else bottom
            val rowWidth = row.sumOf { it.width.toDouble() }.toFloat() * unit
            var x = padding + (width - 2 * padding - rowWidth) / 2
            row.forEachIndexed { keyIndex, key ->
                val left = x.roundToInt()
                x += key.width * unit
                val right = x.roundToInt()
                key.bounds.set(left + gap / 2, top + gap / 2, right - gap / 2, bottom - gap / 2)
                key.hitBox.set(
                    if (keyIndex == 0) 0 else left,
                    hitTop,
                    if (keyIndex == row.lastIndex) width else right,
                    hitBottom,
                )
            }
        }
    }

    /** The key at ([x], [y]), or the nearest one. */
    fun keyAt(x: Int, y: Int): Key? {
        keys.firstOrNull { it.hitBox.contains(x, y) }?.let { return it }
        return keys.minByOrNull { key ->
            val dx = (key.hitBox.centerX() - x).toLong()
            val dy = (key.hitBox.centerY() - y).toLong()
            dx * dx + dy * dy
        }
    }
}
