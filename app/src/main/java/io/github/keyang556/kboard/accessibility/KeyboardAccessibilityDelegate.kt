// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.accessibility

import android.view.MotionEvent
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.github.keyang556.kboard.keyboard.Key
import io.github.keyang556.kboard.keyboard.Keyboard
import io.github.keyang556.kboard.keyboard.KeyboardView

/**
 * Makes a [KeyboardView] usable with TalkBack, the way AOSP's keyboard and HeliBoard do:
 *
 * - Each key is a node of its own ([KeyboardAccessibilityNodeProvider]), read as what it
 *   types or does.
 * - Explore by touch: sliding a finger over the keys reads them, and lifting the finger
 *   types the key under it ("lift to type"). A double tap anywhere types the key last read.
 * - Resting on a key that does something else on a long press does that after a while;
 *   TalkBack's double-tap-and-hold, or its actions menu, does it at once.
 * - Changing keyboards is announced.
 */
class KeyboardAccessibilityDelegate(
    private val view: KeyboardView,
    private val describer: KeyDescriber,
) : AccessibilityDelegateCompat() {
    private val nodeProvider = KeyboardAccessibilityNodeProvider(view, this, describer)
    private var lastHoverKey: Key? = null

    // A long press made while the finger rests on a key; lifting it then types nothing.
    private var longPressedKey: Key? = null
    private var pendingLongPressKey: Key? = null
    private val longPress = Runnable {
        pendingLongPressKey?.let { performLongClickOn(it, fingerOnKey = true) }
        pendingLongPressKey = null
    }

    init {
        ViewCompat.setAccessibilityDelegate(view, this)
    }

    override fun getAccessibilityNodeProvider(host: View): KeyboardAccessibilityNodeProvider = nodeProvider

    /** Called whenever the view shows another keyboard, even with accessibility off. */
    fun onKeyboardChanged(old: Keyboard?, new: Keyboard) {
        cancelLongPress()
        lastHoverKey = null
        longPressedKey = null
        nodeProvider.onKeyboardChanged()
        if (!AccessibilityUtils.isEnabled(view.context)) return
        val oldId = old?.id
        val newId = new.id
        // Shift changes are announced by whoever changed them, as only they know whether
        // the user did. Here, just a new language or a new kind of keyboard.
        if (oldId == null || oldId.layer != newId.layer || oldId.language != newId.language) {
            announce(describer.keyboardName(newId))
        }
    }

    fun announce(text: CharSequence) {
        AccessibilityUtils.announce(view, text)
    }

    /** Hover events are what touches become while TalkBack explores by touch. */
    fun onHoverEvent(event: MotionEvent): Boolean {
        val key = keyAt(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                key?.let(::onHoverEnterTo)
                lastHoverKey = key
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                if (key !== lastHoverKey) {
                    lastHoverKey?.let(::onHoverExitFrom)
                    key?.let(::onHoverEnterTo)
                }
                lastHoverKey = key
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                lastHoverKey?.let(::onHoverExitFrom)
                // The finger lifted off a key, rather than sliding off the keyboard: type it.
                if (key != null) {
                    if (key !== longPressedKey) performClickOn(key)
                    if (key !== lastHoverKey) onHoverExitFrom(key)
                }
                longPressedKey = null
                lastHoverKey = null
            }
        }
        return true
    }

    /** The key at the event's position, or null when it is off the keyboard. */
    private fun keyAt(event: MotionEvent): Key? {
        val index = event.actionIndex
        val x = event.getX(index).toInt()
        val y = event.getY(index).toInt()
        if (x !in 0 until view.width || y !in 0 until view.height) return null
        return view.keyboard?.keyAt(x, y)
    }

    private fun onHoverEnterTo(key: Key) {
        cancelLongPress()
        if (key !== longPressedKey) longPressedKey = null
        view.setKeyPressed(key, true)
        nodeProvider.onHoverEnterTo(key)
        nodeProvider.performActionForKey(key, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)
        if (key.longPress != null) {
            pendingLongPressKey = key
            view.postDelayed(longPress, LONG_PRESS_TIMEOUT_MS)
        }
    }

    private fun onHoverExitFrom(key: Key) {
        cancelLongPress()
        view.setKeyPressed(key, false)
        nodeProvider.onHoverExitFrom(key)
    }

    private fun cancelLongPress() {
        view.removeCallbacks(longPress)
        pendingLongPressKey = null
    }

    fun performClickOn(key: Key) {
        view.performKeyClick(key)
    }

    /**
     * Does what a long press on [key] does. If the finger still rests on the key
     * ([fingerOnKey]), lifting it must not type the key as well.
     */
    fun performLongClickOn(key: Key, fingerOnKey: Boolean) {
        val action = key.longPress ?: return
        if (!view.performKeyLongPress(key)) return
        if (fingerOnKey) longPressedKey = key
        describer.describeTyped(action)?.let(::announce)
    }

    companion object {
        // How long a finger rests on a key before it counts as a long press. Long, as
        // exploring the keyboard means resting on keys; the same as HeliBoard's.
        private const val LONG_PRESS_TIMEOUT_MS = 3000L
    }
}
