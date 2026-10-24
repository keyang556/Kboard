// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.github.keyang556.kboard.keyboard.EnterKeyType
import io.github.keyang556.kboard.keyboard.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorInfosTest {

    @Test
    fun numberAndPhoneFieldsGetPads() {
        assertEquals(Layer.NUMBER, EditorInfos.baseLayer(InputType.TYPE_CLASS_NUMBER))
        assertEquals(Layer.NUMBER, EditorInfos.baseLayer(InputType.TYPE_CLASS_DATETIME))
        assertEquals(Layer.PHONE, EditorInfos.baseLayer(InputType.TYPE_CLASS_PHONE))
        assertEquals(Layer.LETTERS, EditorInfos.baseLayer(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun passwordsAndAddressesAreTypedInEnglish() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(EditorInfos.requiresEnglish(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(EditorInfos.requiresEnglish(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertTrue(EditorInfos.requiresEnglish(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(EditorInfos.requiresEnglish(text or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
        assertFalse(EditorInfos.requiresEnglish(text))
    }

    @Test
    fun passwordFieldsAreRecognised() {
        assertTrue(EditorInfos.isPassword(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(EditorInfos.isPassword(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(EditorInfos.isPassword(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun nothingIsLearnedFromPasswordsOrPrivateEditors() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(EditorInfos.allowsLearning(text, EditorInfo.IME_ACTION_SEND))
        assertFalse(EditorInfos.allowsLearning(text or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0))
        assertFalse(
            EditorInfos.allowsLearning(text, EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING),
        )
    }

    @Test
    fun enterFollowsTheEditorAction() {
        assertEquals(EnterKeyType.SEARCH, EditorInfos.enterKeyType(EditorInfo.IME_ACTION_SEARCH))
        assertEquals(EnterKeyType.SEND, EditorInfos.enterKeyType(EditorInfo.IME_ACTION_SEND))
        assertEquals(EnterKeyType.NEWLINE, EditorInfos.enterKeyType(EditorInfo.IME_ACTION_UNSPECIFIED))
        assertEquals(
            EnterKeyType.NEWLINE,
            EditorInfos.enterKeyType(EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION),
        )
    }

    @Test
    fun aNewLineIsNoEditorAction() {
        val newline = EditorInfo().apply { imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION }
        assertNull(EditorInfos.editorAction(newline))
        val go = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_GO }
        assertEquals(EditorInfo.IME_ACTION_GO, EditorInfos.editorAction(go))
    }
}
