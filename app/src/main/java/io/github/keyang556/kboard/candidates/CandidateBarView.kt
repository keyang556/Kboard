// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.candidates

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import io.github.keyang556.kboard.R
import io.github.keyang556.kboard.engine.Candidate

/**
 * The bar above the keys. While typing Chinese it lists the candidates for what was just
 * typed; otherwise it offers a few tools.
 */
class CandidateBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onCandidateSelected(candidate: Candidate)
        fun onShowAllCandidates()
        fun onToolSelected(tool: Tool)
    }

    enum class Tool { SETTINGS, SYMBOL_TABLE, PICK_INPUT_METHOD, HIDE_KEYBOARD }

    var listener: Listener? = null

    private val candidateRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(candidateRow, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val showAllButton = toolButton(R.drawable.ic_expand, R.string.candidates_show_all) {
        listener?.onShowAllCandidates()
    }
    private val tools = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addTool(R.drawable.ic_settings, R.string.tool_settings, Tool.SETTINGS)
        addTool(R.drawable.ic_emoji, R.string.tool_symbol_table, Tool.SYMBOL_TABLE)
        addTool(R.drawable.ic_language, R.string.tool_pick_input_method, Tool.PICK_INPUT_METHOD)
        addTool(R.drawable.ic_keyboard_hide, R.string.tool_hide_keyboard, Tool.HIDE_KEYBOARD)
    }
    private var candidateCount = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.min_touch_target)
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_background))
        addView(scroller, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(showAllButton)
        addView(tools, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // The candidates are one list to TalkBack, which then says how many there are.
        ViewCompat.setAccessibilityDelegate(candidateRow, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionInfo(
                    CollectionInfoCompat.obtain(1, candidateCount, false, CollectionInfoCompat.SELECTION_MODE_NONE),
                )
            }
        })
        showCandidates(emptyList(), showNumbers = false, obscure = false)
    }

    /**
     * Shows [candidates], or the tools when there are none. With [showNumbers], the first
     * ten are numbered like the keys of a hardware keyboard that choose them; with
     * [obscure], TalkBack reads them as "dot".
     */
    fun showCandidates(candidates: List<Candidate>, showNumbers: Boolean, obscure: Boolean) {
        val shown = candidates.take(MAX_CANDIDATES)
        candidateCount = shown.size
        val hasCandidates = shown.isNotEmpty()
        scroller.visibility = if (hasCandidates) VISIBLE else GONE
        showAllButton.visibility = if (hasCandidates) VISIBLE else GONE
        tools.visibility = if (hasCandidates) GONE else VISIBLE

        // Reuse the views already there; the bar changes with every key typed.
        while (candidateRow.childCount > shown.size) candidateRow.removeViewAt(candidateRow.childCount - 1)
        shown.forEachIndexed { position, candidate ->
            val number = if (showNumbers && position < SELECTION_KEYS) (position + 1) % SELECTION_KEYS else null
            val view = candidateRow.getChildAt(position) as TextView?
            if (view == null) {
                candidateRow.addView(
                    CandidateViews.candidateView(context, candidate, number, obscure, ::select),
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
                )
            } else {
                CandidateViews.bind(view, candidate, number, obscure, ::select)
            }
            setItemInfo(candidateRow.getChildAt(position), position)
        }
        scroller.scrollTo(0, 0)
    }

    private fun select(candidate: Candidate) {
        listener?.onCandidateSelected(candidate)
    }

    private fun setItemInfo(view: View, position: Int) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionItemInfo(CollectionItemInfoCompat.obtain(0, 1, position, 1, false, false))
            }
        })
    }

    private fun LinearLayout.addTool(@DrawableRes icon: Int, @StringRes description: Int, tool: Tool) {
        addView(
            toolButton(icon, description) { listener?.onToolSelected(tool) },
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
        )
    }

    private fun toolButton(@DrawableRes icon: Int, @StringRes description: Int, onClick: () -> Unit) =
        ImageButton(context).apply {
            setImageResource(icon)
            setColorFilter(ContextCompat.getColor(context, R.color.key_label))
            background = ContextCompat.getDrawable(context, R.drawable.candidate_background)
            val label = resources.getString(description)
            contentDescription = label
            ViewCompat.setTooltipText(this, label)
            minimumWidth = resources.getDimensionPixelSize(R.dimen.min_touch_target)
            minimumHeight = resources.getDimensionPixelSize(R.dimen.min_touch_target)
            setOnClickListener { onClick() }
        }

    companion object {
        // More candidates than this are in the panel that shows them all.
        private const val MAX_CANDIDATES = 60
        private const val SELECTION_KEYS = 10
    }
}
