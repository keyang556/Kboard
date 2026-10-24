// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.keyboard

import android.graphics.Rect
import androidx.annotation.DrawableRes

/** The keyboards there are; [LETTERS] is the Zhuyin or the English one, by language. */
enum class Layer { LETTERS, SYMBOLS, SYMBOLS_MORE, FULL_WIDTH, NUMBER, PHONE }

enum class Language { CHINESE, ENGLISH }

enum class ShiftState {
    OFF,

    /** Shifted by itself, at the beginning of a sentence. */
    AUTO,

    /** Shifted by the user, for one letter. */
    ON,

    /** Caps lock. */
    LOCKED;

    val isShifted: Boolean get() = this != OFF
}

/** What the Enter key does in the current editor. */
enum class EnterKeyType { NEWLINE, GO, SEARCH, SEND, NEXT, PREVIOUS, DONE }

/** What pressing a key does. */
sealed interface KeyAction {
    /** Types [text]: an English letter, a digit, a symbol. */
    data class Text(val text: String) : KeyAction

    /** Sends [key], a key of a QWERTY keyboard, to the Zhuyin engine. */
    data class Zhuyin(val key: Char) : KeyAction

    data object Delete : KeyAction
    data object Enter : KeyAction
    data object Space : KeyAction
    data object Shift : KeyAction

    /** Switches between Chinese and English. */
    data object ToggleLanguage : KeyAction

    /** Shows the keyboard [layer]. */
    data class ShowLayer(val layer: Layer) : KeyAction

    /** Back to the letters, or to the number pad in a number field. */
    data object ShowBaseLayer : KeyAction

    /** Shows the system's list of input methods. */
    data object PickInputMethod : KeyAction
}

/** How a key looks. */
enum class KeyStyle { CHARACTER, FUNCTION, ACTION }

/**
 * One key of a [Keyboard]. [width] is in units of the keyboard's narrowest keys; [hint] is
 * the small label in the corner, telling what a long press does or, on a 26-key Zhuyin
 * keyboard, the other symbols the key types.
 */
class Key(
    val action: KeyAction,
    val label: String? = null,
    @param:DrawableRes val icon: Int = 0,
    val hint: String? = null,
    val longPress: KeyAction? = null,
    val width: Float = 1f,
    val style: KeyStyle = KeyStyle.CHARACTER,
) {
    /** Where the key is drawn, in the keyboard view's coordinates. */
    val bounds = Rect()

    /** Where a touch counts as this key: its bounds plus its share of the gaps around it. */
    val hitBox = Rect()

    var isPressed = false

    /** Whether holding the key down repeats it. */
    val isRepeatable: Boolean get() = action == KeyAction.Delete

    override fun toString() = "Key($action, $label)"
}
