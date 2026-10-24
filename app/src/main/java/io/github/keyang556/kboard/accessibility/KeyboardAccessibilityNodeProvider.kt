// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.accessibility

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import io.github.keyang556.kboard.keyboard.Key
import io.github.keyang556.kboard.keyboard.KeyboardView

/**
 * Presents every key of a [KeyboardView] to accessibility services as a node of its own,
 * a "virtual view", although the keyboard draws all its keys itself. A key's virtual view
 * id is its index in the keyboard's list of keys.
 */
class KeyboardAccessibilityNodeProvider(
    private val view: KeyboardView,
    private val delegate: KeyboardAccessibilityDelegate,
    private val describer: KeyDescriber,
) : AccessibilityNodeProviderCompat() {
    private var focusedId = UNDEFINED
    private var hoveringId = UNDEFINED
    private val viewLocation = IntArray(2)
    private val boundsInScreen = Rect()

    private val keys: List<Key> get() = view.keyboard?.keys.orEmpty()

    private fun idOf(key: Key): Int = keys.indexOfFirst { it === key }

    private fun keyOf(id: Int): Key? = keys.getOrNull(id)

    /** The keys changed: forget focus, and have accessibility services read them anew. */
    fun onKeyboardChanged() {
        focusedId = UNDEFINED
        hoveringId = UNDEFINED
        onDescriptionsChanged()
    }

    /** The keys stay, but some are described differently: have them read anew. */
    fun onDescriptionsChanged() {
        if (!AccessibilityUtils.isEnabled(view.context)) return
        val event = AccessibilityUtils.obtainEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        view.onInitializeAccessibilityEvent(event)
        event.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        view.parent?.requestSendAccessibilityEvent(view, event)
    }

    /**
     * The finger came onto [key]. Lifting it will type the key, so for as long as it rests
     * there the key offers no click actions, which would only add "double-tap to activate"
     * to what is read.
     */
    fun onHoverEnterTo(key: Key) {
        val id = idOf(key)
        if (id == View.NO_ID) return
        hoveringId = id
        sendEventForKey(key, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        sendEventForKey(key, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
    }

    fun onHoverExitFrom(key: Key) {
        hoveringId = UNDEFINED
        sendEventForKey(key, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        sendEventForKey(key, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
    }

    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfoCompat? {
        if (virtualViewId == UNDEFINED) return null
        val keyboard = view.keyboard ?: return null
        if (virtualViewId == View.NO_ID) {
            val root = AccessibilityNodeInfoCompat.obtain(view)
            view.onInitializeAccessibilityNodeInfo(root.unwrap())
            keyboard.keys.indices.forEach { root.addChild(view, it) }
            return root
        }
        val key = keyOf(virtualViewId) ?: return null
        view.getLocationOnScreen(viewLocation)
        boundsInScreen.set(key.hitBox)
        boundsInScreen.offset(viewLocation[0], viewLocation[1])

        val info = AccessibilityNodeInfoCompat.obtain()
        info.packageName = view.context.packageName
        info.className = Key::class.java.name
        info.contentDescription = describer.describe(key, keyboard.id)
        info.stateDescription = describer.stateDescription(key, keyboard.id)
        @Suppress("DEPRECATION")
        info.setBoundsInParent(key.hitBox)
        info.setBoundsInScreen(boundsInScreen)
        info.setParent(view)
        info.setSource(view, virtualViewId)
        info.isEnabled = true
        info.isVisibleToUser = true
        if (virtualViewId != hoveringId) {
            info.isClickable = true
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            describer.longPressLabel(key)?.let { label ->
                info.isLongClickable = true
                info.addAction(AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, label))
            }
        }
        if (virtualViewId == focusedId) {
            info.isAccessibilityFocused = true
            info.addAction(AccessibilityActionCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        } else {
            info.addAction(AccessibilityActionCompat.ACTION_ACCESSIBILITY_FOCUS)
        }
        return info
    }

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        val key = keyOf(virtualViewId) ?: return false
        return performActionForKey(key, action)
    }

    fun performActionForKey(key: Key, action: Int): Boolean = when (action) {
        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS -> {
            focusedId = idOf(key)
            sendEventForKey(key, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
            true
        }
        AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
            focusedId = UNDEFINED
            sendEventForKey(key, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
            true
        }
        AccessibilityNodeInfoCompat.ACTION_CLICK -> {
            sendEventForKey(key, AccessibilityEvent.TYPE_VIEW_CLICKED)
            delegate.performClickOn(key)
            true
        }
        AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
            sendEventForKey(key, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
            delegate.performLongClickOn(key, fingerOnKey = false)
            true
        }
        else -> false
    }

    private fun sendEventForKey(key: Key, eventType: Int) {
        if (!AccessibilityUtils.isEnabled(view.context)) return
        val keyboard = view.keyboard ?: return
        val id = idOf(key)
        if (id == View.NO_ID) return
        val event = AccessibilityUtils.obtainEvent(eventType)
        event.packageName = view.context.packageName
        event.className = Key::class.java.name
        event.contentDescription = describer.describe(key, keyboard.id)
        event.isEnabled = true
        event.setSource(view, id)
        view.parent?.requestSendAccessibilityEvent(view, event)
    }

    companion object {
        // AccessibilityNodeInfo.UNDEFINED_ITEM_ID: no virtual view.
        private const val UNDEFINED = Int.MAX_VALUE
    }
}
