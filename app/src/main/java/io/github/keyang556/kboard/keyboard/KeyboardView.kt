// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import io.github.keyang556.kboard.R
import io.github.keyang556.kboard.accessibility.AccessibilityUtils
import io.github.keyang556.kboard.accessibility.KeyDescriber
import io.github.keyang556.kboard.accessibility.KeyboardAccessibilityDelegate
import kotlin.math.min

/**
 * Draws a [Keyboard] and turns touches on it into key presses. With TalkBack exploring by
 * touch, touches arrive as hover events instead, which [KeyboardAccessibilityDelegate]
 * handles.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** A key went down: time for feedback. */
        fun onKeyDown(key: Key)

        /** A key was typed. */
        fun onKey(key: Key, action: KeyAction)

        /** A key was held down; returns whether that did something. */
        fun onLongPress(key: Key): Boolean
    }

    var listener: Listener? = null

    var keyboard: Keyboard? = null
        private set

    /** The height of a row of keys, in pixels. */
    var rowHeight: Int = resources.getDimensionPixelSize(R.dimen.key_row_height)
        set(value) {
            if (field == value) return
            field = value
            layoutKeys()
            requestLayout()
        }

    val describer = KeyDescriber(context)
    private val accessibility = KeyboardAccessibilityDelegate(this, describer)

    private val keyGap = resources.getDimensionPixelSize(R.dimen.key_gap)
    private val keyboardPadding = resources.getDimensionPixelSize(R.dimen.keyboard_padding)
    private val keyRadius = resources.getDimension(R.dimen.key_corner_radius)
    private val hintPadding = resources.getDimension(R.dimen.key_hint_padding)
    private val fontScale = resources.configuration.fontScale.coerceIn(1f, MAX_FONT_SCALE)

    private val backgroundColor = color(R.color.keyboard_background)
    private val keyColor = color(R.color.key_background)
    private val keyPressedColor = color(R.color.key_background_pressed)
    private val functionKeyColor = color(R.color.key_function_background)
    private val functionKeyPressedColor = color(R.color.key_function_background_pressed)
    private val actionKeyColor = color(R.color.key_action_background)
    private val actionKeyPressedColor = color(R.color.key_action_background_pressed)
    private val labelColor = color(R.color.key_label)
    private val actionLabelColor = color(R.color.key_action_label)
    private val hintColor = color(R.color.key_hint)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // An outline marks every key off from the keyboard behind it, whatever its colour.
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.key_border_width)
        color = color(R.color.key_border)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = hintColor
    }
    private val keyRect = RectF()
    private val icons = HashMap<Int, Drawable>()

    // Touch handling: one key at a time; a second finger types the first finger's key.
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var pressedKey: Key? = null
    private var longPressDone = false
    private var repeated = false
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPress = Runnable {
        val key = pressedKey ?: return@Runnable
        if (listener?.onLongPress(key) == true) {
            longPressDone = true
            setKeyPressed(key, false)
        }
    }
    private val repeat = object : Runnable {
        override fun run() {
            val key = pressedKey ?: return
            repeated = true
            listener?.onKey(key, key.action)
            postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    init {
        isHapticFeedbackEnabled = true
        isSoundEffectsEnabled = false
    }

    fun setKeyboard(newKeyboard: Keyboard) {
        cancelTouch()
        val old = keyboard
        // Keyboards are reused: none should come back with a key still held down.
        old?.keys?.forEach { it.isPressed = false }
        newKeyboard.keys.forEach { it.isPressed = false }
        keyboard = newKeyboard
        layoutKeys()
        requestLayout()
        invalidate()
        accessibility.onKeyboardChanged(old, newKeyboard)
    }

    /** Has TalkBack say [text]. */
    fun announce(text: CharSequence) {
        accessibility.announce(text)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = keyboard?.rows?.size ?: DEFAULT_ROWS
        setMeasuredDimension(width, rows * rowHeight + 2 * keyboardPadding)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width > 0) keyboard?.layout(width, rowHeight, keyGap, keyboardPadding)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        keyboard?.keys?.forEach { drawKey(canvas, it) }
    }

    private fun drawKey(canvas: Canvas, key: Key) {
        keyPaint.color = when (key.style) {
            KeyStyle.CHARACTER -> if (key.isPressed) keyPressedColor else keyColor
            KeyStyle.FUNCTION -> if (key.isPressed) functionKeyPressedColor else functionKeyColor
            KeyStyle.ACTION -> if (key.isPressed) actionKeyPressedColor else actionKeyColor
        }
        keyRect.set(key.bounds)
        canvas.drawRoundRect(keyRect, keyRadius, keyRadius, keyPaint)
        val inset = borderPaint.strokeWidth / 2
        keyRect.inset(inset, inset)
        canvas.drawRoundRect(keyRect, keyRadius, keyRadius, borderPaint)
        keyRect.set(key.bounds)

        val contentColor = if (key.style == KeyStyle.ACTION) actionLabelColor else labelColor
        val label = key.label
        if (key.icon != 0 && label == null) {
            drawIcon(canvas, key, contentColor)
        } else if (label != null) {
            labelPaint.color = contentColor
            labelPaint.textSize = labelTextSize(key, label)
            val y = keyRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2
            canvas.drawText(label, keyRect.centerX(), y, labelPaint)
        }

        key.hint?.let { hint ->
            hintPaint.textSize = rowHeight * HINT_SIZE * fontScale
            fitText(hintPaint, hint, keyRect.width() / 2)
            canvas.drawText(hint, keyRect.right - hintPadding, keyRect.top + hintPadding - hintPaint.ascent(), hintPaint)
        }
    }

    private fun labelTextSize(key: Key, label: String): Float {
        val singleSymbol = label.codePointCount(0, label.length) == 1
        val size = if (singleSymbol) {
            min(rowHeight * SYMBOL_SIZE, key.bounds.width() * SYMBOL_WIDTH_SIZE)
        } else {
            rowHeight * WORD_SIZE
        }
        labelPaint.textSize = size * fontScale
        fitText(labelPaint, label, key.bounds.width() * LABEL_MAX_WIDTH)
        return labelPaint.textSize
    }

    /** Shrinks [paint]'s text size until [text] is at most [maxWidth] wide. */
    private fun fitText(paint: Paint, text: String, maxWidth: Float) {
        val width = paint.measureText(text)
        if (width > maxWidth && width > 0) paint.textSize = paint.textSize * maxWidth / width
    }

    private fun drawIcon(canvas: Canvas, key: Key, tint: Int) {
        val icon = icons.getOrPut(key.icon) {
            checkNotNull(ContextCompat.getDrawable(context, key.icon)).mutate()
        }
        icon.setTint(tint)
        val size = (min(key.bounds.width(), key.bounds.height()) * ICON_SIZE).toInt()
        val left = key.bounds.centerX() - size / 2
        val top = key.bounds.centerY() - size / 2
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
    }

    fun setKeyPressed(key: Key, pressed: Boolean) {
        if (key.isPressed == pressed) return
        key.isPressed = pressed
        invalidate()
    }

    /** Types [key] as a tap would, for accessibility services. */
    fun performKeyClick(key: Key) {
        listener?.onKeyDown(key)
        listener?.onKey(key, key.action)
    }

    /** Long presses [key] as holding it would, for accessibility services. */
    fun performKeyLongPress(key: Key): Boolean = listener?.onLongPress(key) == true

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (AccessibilityUtils.isTouchExplorationEnabled(context)) return accessibility.onHoverEvent(event)
        return super.onHoverEvent(event)
    }

    // Clicks are announced and performed per key, through KeyboardAccessibilityNodeProvider.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val keyboard = keyboard ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startPress(keyboard, event, 0)
            MotionEvent.ACTION_POINTER_DOWN -> {
                releasePress(type = true)
                startPress(keyboard, event, event.actionIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0 && !longPressDone && !repeated) {
                    val key = keyboard.keyAt(event.getX(index).toInt(), event.getY(index).toInt())
                    if (key !== pressedKey) movePress(key)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) releasePress(type = true)
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    private fun startPress(keyboard: Keyboard, event: MotionEvent, index: Int) {
        activePointerId = event.getPointerId(index)
        val key = keyboard.keyAt(event.getX(index).toInt(), event.getY(index).toInt()) ?: return
        pressedKey = key
        longPressDone = false
        repeated = false
        setKeyPressed(key, true)
        listener?.onKeyDown(key)
        if (key.isRepeatable) {
            postDelayed(repeat, longPressTimeout)
        } else if (key.longPress != null) {
            postDelayed(longPress, longPressTimeout)
        }
    }

    /** The finger slid onto another key, which is now the one lifting it types. */
    private fun movePress(key: Key?) {
        removeCallbacks(longPress)
        removeCallbacks(repeat)
        pressedKey?.let { setKeyPressed(it, false) }
        pressedKey = key
        if (key == null) return
        setKeyPressed(key, true)
        if (key.longPress != null) postDelayed(longPress, longPressTimeout)
    }

    private fun releasePress(type: Boolean) {
        removeCallbacks(longPress)
        removeCallbacks(repeat)
        val key = pressedKey
        pressedKey = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        if (key == null) return
        setKeyPressed(key, false)
        if (type && !longPressDone && !repeated) listener?.onKey(key, key.action)
    }

    private fun cancelTouch() {
        releasePress(type = false)
    }

    override fun onDetachedFromWindow() {
        cancelTouch()
        super.onDetachedFromWindow()
    }

    private fun color(resId: Int) = ContextCompat.getColor(context, resId)

    companion object {
        private const val DEFAULT_ROWS = 5
        private const val REPEAT_INTERVAL_MS = 50L

        // Sizes, as fractions of a row's height or a key's width.
        private const val SYMBOL_SIZE = 0.42f
        private const val SYMBOL_WIDTH_SIZE = 0.62f
        private const val WORD_SIZE = 0.3f
        private const val HINT_SIZE = 0.22f
        private const val ICON_SIZE = 0.46f
        private const val LABEL_MAX_WIDTH = 0.86f

        // Labels follow the user's font size up to this much; beyond, they would not fit.
        private const val MAX_FONT_SCALE = 1.3f
    }
}
