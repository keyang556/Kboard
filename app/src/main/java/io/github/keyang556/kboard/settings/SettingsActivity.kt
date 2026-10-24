// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.github.keyang556.kboard.BuildConfig
import io.github.keyang556.kboard.R
import io.github.keyang556.kboard.engine.ChewingEngineHolder
import io.github.keyang556.kboard.engine.ZhuyinLayout
import io.github.keyang556.kboard.ime.KboardService

/**
 * Setting up the keyboard, and its settings. Built of the platform's own widgets, each
 * labelled by its visible text, so it works with TalkBack and Switch Access as they are.
 */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)

        val root = findViewById<View>(R.id.settings_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        HEADINGS.forEach { ViewCompat.setAccessibilityHeading(findViewById(it), true) }

        status = findViewById(R.id.setup_status)
        findViewById<View>(R.id.enable_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<View>(R.id.select_button).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        bindChoice(
            R.id.zhuyin_layout_group,
            mapOf(
                R.id.layout_dachen to ZhuyinLayout.DACHEN,
                R.id.layout_et26 to ZhuyinLayout.ET26,
                R.id.layout_hsu to ZhuyinLayout.HSU,
            ),
            prefs.zhuyinLayout,
        ) { prefs.zhuyinLayout = it }
        bindChoice(
            R.id.key_height_group,
            mapOf(
                R.id.key_height_normal to KeyHeight.NORMAL,
                R.id.key_height_large to KeyHeight.LARGE,
                R.id.key_height_extra_large to KeyHeight.EXTRA_LARGE,
            ),
            prefs.keyHeight,
        ) { prefs.keyHeight = it }

        bindSwitch(R.id.vibrate_switch, prefs.vibrate) { prefs.vibrate = it }
        bindSwitch(R.id.sound_switch, prefs.sound) { prefs.sound = it }
        bindSwitch(R.id.auto_capitalize_switch, prefs.autoCapitalize) { prefs.autoCapitalize = it }
        bindSwitch(R.id.auto_learn_switch, prefs.autoLearn) { prefs.autoLearn = it }
        bindSwitch(R.id.speak_password_keys_switch, prefs.speakPasswordKeys) { prefs.speakPasswordKeys = it }

        findViewById<View>(R.id.reset_learning_button).setOnClickListener { confirmResetLearning() }

        val engineVersion = ChewingEngineHolder.version() ?: getString(R.string.about_engine_missing)
        findViewById<TextView>(R.id.about_version).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME, engineVersion)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // The input method picker is a dialog over this activity: check again once it is gone.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateStatus()
    }

    private fun updateStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val ours = ComponentName(this, KboardService::class.java)
        val enabled = imm.enabledInputMethodList.any { it.component == ours }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString) == ours
        status.setText(
            when {
                selected -> R.string.setup_status_selected
                enabled -> R.string.setup_status_enabled
                else -> R.string.setup_status_disabled
            },
        )
    }

    private fun <T> bindChoice(groupId: Int, choices: Map<Int, T>, current: T, onChange: (T) -> Unit) {
        val group = findViewById<RadioGroup>(groupId)
        choices.entries.firstOrNull { it.value == current }?.let { group.check(it.key) }
        group.setOnCheckedChangeListener { _, checkedId -> choices[checkedId]?.let(onChange) }
    }

    private fun bindSwitch(id: Int, current: Boolean, onChange: (Boolean) -> Unit) {
        val switch = findViewById<CompoundButton>(id)
        switch.isChecked = current
        switch.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun confirmResetLearning() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_learning_title)
            .setMessage(R.string.reset_learning_message)
            .setPositiveButton(R.string.reset_learning_confirm) { _, _ ->
                ChewingEngineHolder.resetUserData(this)
                Toast.makeText(this, R.string.reset_learning_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private val HEADINGS = listOf(
            R.id.title,
            R.id.heading_setup,
            R.id.heading_keyboard,
            R.id.heading_zhuyin,
            R.id.heading_accessibility,
            R.id.heading_about,
        )
    }
}
