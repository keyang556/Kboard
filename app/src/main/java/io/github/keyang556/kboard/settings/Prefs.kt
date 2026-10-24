// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.settings

import android.content.Context
import androidx.core.content.edit
import io.github.keyang556.kboard.engine.ZhuyinLayout
import io.github.keyang556.kboard.keyboard.Language

/** How tall the keys are, as a multiple of the standard height (which is at least 48 dp). */
enum class KeyHeight(val scale: Float) { NORMAL(1f), LARGE(1.15f), EXTRA_LARGE(1.3f) }

/** The user's settings. */
class Prefs(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var zhuyinLayout: ZhuyinLayout
        get() = enumValue(KEY_ZHUYIN_LAYOUT, ZhuyinLayout.DACHEN)
        set(value) = putString(KEY_ZHUYIN_LAYOUT, value.name)

    var keyHeight: KeyHeight
        get() = enumValue(KEY_KEY_HEIGHT, KeyHeight.NORMAL)
        set(value) = putString(KEY_KEY_HEIGHT, value.name)

    var vibrate: Boolean
        get() = preferences.getBoolean(KEY_VIBRATE, true)
        set(value) = putBoolean(KEY_VIBRATE, value)

    var sound: Boolean
        get() = preferences.getBoolean(KEY_SOUND, false)
        set(value) = putBoolean(KEY_SOUND, value)

    var autoCapitalize: Boolean
        get() = preferences.getBoolean(KEY_AUTO_CAPITALIZE, true)
        set(value) = putBoolean(KEY_AUTO_CAPITALIZE, value)

    var autoLearn: Boolean
        get() = preferences.getBoolean(KEY_AUTO_LEARN, true)
        set(value) = putBoolean(KEY_AUTO_LEARN, value)

    var speakPasswordKeys: Boolean
        get() = preferences.getBoolean(KEY_SPEAK_PASSWORD_KEYS, false)
        set(value) = putBoolean(KEY_SPEAK_PASSWORD_KEYS, value)

    /** The language last typed in, which the next text field starts with. */
    var lastLanguage: Language
        get() = enumValue(KEY_LAST_LANGUAGE, Language.CHINESE)
        set(value) = putString(KEY_LAST_LANGUAGE, value.name)

    private inline fun <reified T : Enum<T>> enumValue(key: String, default: T): T {
        val name = preferences.getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    private fun putString(key: String, value: String) {
        preferences.edit { putString(key, value) }
    }

    private fun putBoolean(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }

    companion object {
        private const val FILE = "settings"
        private const val KEY_ZHUYIN_LAYOUT = "zhuyin_layout"
        private const val KEY_KEY_HEIGHT = "key_height"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_SOUND = "sound"
        private const val KEY_AUTO_CAPITALIZE = "auto_capitalize"
        private const val KEY_AUTO_LEARN = "auto_learn"
        private const val KEY_SPEAK_PASSWORD_KEYS = "speak_password_keys"
        private const val KEY_LAST_LANGUAGE = "last_language"
    }
}
