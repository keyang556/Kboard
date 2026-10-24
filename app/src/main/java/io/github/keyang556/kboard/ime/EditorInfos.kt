// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.ime

import android.annotation.SuppressLint
import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.github.keyang556.kboard.keyboard.EnterKeyType
import io.github.keyang556.kboard.keyboard.Layer

/** What an editor's [EditorInfo] tells about the keyboard it wants. */
object EditorInfos {

    /** The keyboard the editor's input type calls for. */
    fun baseLayer(inputType: Int): Layer = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Layer.NUMBER
        InputType.TYPE_CLASS_PHONE -> Layer.PHONE
        else -> Layer.LETTERS
    }

    /** Whether only English makes sense: passwords, e-mail and web addresses. */
    fun requiresEnglish(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> true
            else -> false
        }
    }

    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /**
     * Whether phrases typed into the editor may be learned: not from passwords, nor from
     * editors that ask keyboards not to learn from them, such as incognito browser tabs.
     * (That flag is new in Android 8; older editors simply never set it.)
     */
    @SuppressLint("InlinedApi")
    fun allowsLearning(inputType: Int, imeOptions: Int): Boolean =
        !isPassword(inputType) && imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0

    /** What Enter does: a new line, unless the editor asks for an action. */
    fun enterKeyType(imeOptions: Int): EnterKeyType {
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterKeyType.NEWLINE
        return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterKeyType.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterKeyType.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterKeyType.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterKeyType.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> EnterKeyType.PREVIOUS
            EditorInfo.IME_ACTION_DONE -> EnterKeyType.DONE
            else -> EnterKeyType.NEWLINE
        }
    }

    /** The editor action Enter performs, or null when it types a new line. */
    fun editorAction(editorInfo: EditorInfo): Int? {
        if (!editorInfo.actionLabel.isNullOrBlank()) return editorInfo.actionId
        if (enterKeyType(editorInfo.imeOptions) == EnterKeyType.NEWLINE) return null
        return editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
    }
}
