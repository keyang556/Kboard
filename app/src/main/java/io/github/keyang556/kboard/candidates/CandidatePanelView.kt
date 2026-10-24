// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.candidates

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import io.github.keyang556.kboard.R
import io.github.keyang556.kboard.engine.Candidate
import kotlin.math.max
import kotlin.math.min

/**
 * Shows every candidate, page by page, in place of the keys. Its title says which page is
 * showing, and is read by TalkBack whenever the page changes.
 */
class CandidatePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onCandidateSelected(candidate: Candidate)
        fun onCloseCandidatePanel()
    }

    var listener: Listener? = null

    private val title = TextView(context).apply {
        setTextColor(ContextCompat.getColor(context, R.color.key_label))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.panel_title_text_size))
        gravity = Gravity.CENTER_VERTICAL
        val padding = resources.getDimensionPixelSize(R.dimen.candidate_padding)
        setPadding(padding, 0, padding, 0)
        ViewCompat.setAccessibilityHeading(this, true)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    private val previousButton = textButton(R.string.candidates_previous_page) { showPage(page - 1) }
    private val nextButton = textButton(R.string.candidates_next_page) { showPage(page + 1) }

    private var candidates: List<Candidate> = emptyList()
    private var page = 0
    private var columns = 1
    private var rows = 1

    // Rows of the page showing that hold candidates.
    private var filledRows = 0
    private val pageSize: Int get() = columns * rows
    private val pageCount: Int get() = max(1, (candidates.size + pageSize - 1) / pageSize)

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_background))
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(title, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(textButton(R.string.candidates_close) { listener?.onCloseCandidatePanel() })
        }
        val footer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(previousButton, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(nextButton, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        ViewCompat.setAccessibilityDelegate(grid, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionInfo(
                    CollectionInfoCompat.obtain(filledRows, columns, false, CollectionInfoCompat.SELECTION_MODE_NONE),
                )
            }
        })
    }

    /** Shows [candidates] from the first page; [paneTitle] is what TalkBack calls the panel. */
    fun showCandidates(candidates: List<Candidate>, paneTitle: CharSequence) {
        this.candidates = candidates
        ViewCompat.setAccessibilityPaneTitle(this, paneTitle)
        page = 0
        if (grid.height > 0) layoutGrid(grid.width, grid.height) else requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The grid has its size only once the panel is laid out.
        post { if (grid.height > 0) layoutGrid(grid.width, grid.height) }
    }

    /** Fits as many columns of the widest candidate, and rows, as the grid has room for. */
    private fun layoutGrid(width: Int, height: Int) {
        val paint = Paint().apply { textSize = resources.getDimension(R.dimen.candidate_text_size) }
        val widest = candidates.maxOfOrNull { paint.measureText(it.text) } ?: 0f
        val cellWidth = max(
            resources.getDimensionPixelSize(R.dimen.candidate_cell_min_width),
            (widest + 2 * resources.getDimensionPixelSize(R.dimen.candidate_padding)).toInt(),
        )
        columns = (width / cellWidth).coerceIn(1, MAX_COLUMNS)
        rows = max(1, height / resources.getDimensionPixelSize(R.dimen.candidate_cell_height))
        showPage(min(page, pageCount - 1))
    }

    private fun showPage(newPage: Int) {
        page = newPage.coerceIn(0, pageCount - 1)
        grid.removeAllViews()
        val pageRows = candidates.drop(page * pageSize).take(pageSize).chunked(columns)
        filledRows = pageRows.size
        pageRows.forEachIndexed { rowIndex, rowCandidates ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (columnIndex in 0 until columns) {
                val candidate = rowCandidates.getOrNull(columnIndex)
                val cell = if (candidate == null) {
                    View(context)
                } else {
                    CandidateViews.candidateView(context, candidate, null) { listener?.onCandidateSelected(it) }.also {
                        setCellInfo(it, rowIndex, columnIndex)
                    }
                }
                row.addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
            grid.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        // Keep short pages' rows as tall as full pages'.
        repeat(rows - grid.childCount) { grid.addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)) }
        previousButton.isEnabled = page > 0
        nextButton.isEnabled = page < pageCount - 1
        title.text = resources.getString(R.string.candidates_page, page + 1, pageCount)
    }

    private fun setCellInfo(view: View, row: Int, column: Int) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionItemInfo(CollectionItemInfoCompat.obtain(row, 1, column, 1, false, false))
            }
        })
    }

    private fun textButton(@StringRes text: Int, onClick: () -> Unit) = Button(context).apply {
        setText(text)
        isAllCaps = false
        setTextColor(ContextCompat.getColorStateList(context, R.color.button_text))
        background = ContextCompat.getDrawable(context, R.drawable.candidate_background)
        minHeight = resources.getDimensionPixelSize(R.dimen.min_touch_target)
        minimumHeight = minHeight
        minWidth = resources.getDimensionPixelSize(R.dimen.min_touch_target)
        minimumWidth = minWidth
        setOnClickListener { onClick() }
    }

    companion object {
        private const val MAX_COLUMNS = 8
    }
}
