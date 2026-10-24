// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.accessibility

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

object AccessibilityUtils {

    private fun manager(context: Context): AccessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    fun isEnabled(context: Context): Boolean = manager(context).isEnabled

    /** Whether a screen reader explores the screen by touch, as TalkBack does. */
    fun isTouchExplorationEnabled(context: Context): Boolean =
        manager(context).let { it.isEnabled && it.isTouchExplorationEnabled }

    /**
     * Whether keys should be read as "dot" because the editor holds a password, which a
     * bystander should not hear: unless the user listens through headphones, or asked to
     * hear the keys of passwords.
     */
    fun shouldObscureInput(context: Context, isPassword: Boolean, speakPasswordKeys: Boolean): Boolean {
        if (!isPassword || speakPasswordKeys) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Before Android 8 this setting was the system-wide choice; it is gone since.
            @Suppress("DEPRECATION")
            val speakPassword = Settings.Secure.getInt(
                context.contentResolver, Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD, 0,
            ) != 0
            if (speakPassword) return false
        }
        return !isListeningThroughHeadphones(context)
    }

    private fun isListeningThroughHeadphones(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_USB_HEADSET -> true
                else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    device.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
        }
    }

    fun obtainEvent(eventType: Int): AccessibilityEvent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityEvent(eventType)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityEvent.obtain(eventType)
        }

    /**
     * Has the screen reader say [text], as a window state change of [view]: how keyboards
     * have long told TalkBack that they changed (AOSP's LatinIME, HeliBoard).
     */
    fun announce(view: View, text: CharSequence) {
        if (!isEnabled(view.context)) return
        val parent = view.parent ?: return
        val event = obtainEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        view.onInitializeAccessibilityEvent(event)
        event.text.add(text)
        event.contentDescription = null
        parent.requestSendAccessibilityEvent(view, event)
    }
}
