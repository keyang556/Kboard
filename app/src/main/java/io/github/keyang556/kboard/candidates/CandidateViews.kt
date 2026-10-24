// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.candidates

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.github.keyang556.kboard.R
import io.github.keyang556.kboard.accessibility.KeyDescriber
import io.github.keyang556.kboard.engine.Candidate

/** Builds the views the candidate bar and panel show candidates in. */
internal object CandidateViews {

    /**
     * A view showing one candidate. It is an ordinary clickable view, so TalkBack reads it
     * when explored, and a double tap chooses it. [number], if any, is the key of a
     * hardware keyboard that chooses it. With [obscure], it is read as "dot", like the
     * keys of a password.
     */
    fun candidateView(
        context: Context,
        candidate: Candidate,
        number: Int?,
        obscure: Boolean,
        onClick: (Candidate) -> Unit,
    ): TextView = TextView(context).apply { bind(this, candidate, number, obscure, onClick) }

    fun bind(view: TextView, candidate: Candidate, number: Int?, obscure: Boolean, onClick: (Candidate) -> Unit) {
        val resources = view.resources
        view.text = if (number != null) "$number ${candidate.text}" else candidate.text
        view.tag = candidate
        describe(view, obscure)
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.candidate_text_size))
        view.setTextColor(ContextCompat.getColor(view.context, R.color.key_label))
        view.gravity = Gravity.CENTER
        view.minHeight = resources.getDimensionPixelSize(R.dimen.min_touch_target)
        view.minWidth = resources.getDimensionPixelSize(R.dimen.min_touch_target)
        val padding = resources.getDimensionPixelSize(R.dimen.candidate_padding)
        view.setPadding(padding, 0, padding, 0)
        view.background = ContextCompat.getDrawable(view.context, R.drawable.candidate_background)
        view.isClickable = true
        view.isFocusable = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.setOnClickListener { onClick(candidate) }
    }

    /**
     * Sets what TalkBack says for [view], if it shows a candidate: the candidate, or "dot"
     * with [obscure]. The view stays as it is, so TalkBack's focus stays on it.
     */
    fun describe(view: View, obscure: Boolean) {
        val candidate = view.tag as? Candidate ?: return
        view.contentDescription = if (obscure) {
            view.resources.getString(R.string.spoken_dot)
        } else {
            KeyDescriber.inChinese(candidate.text)
        }
    }
}
