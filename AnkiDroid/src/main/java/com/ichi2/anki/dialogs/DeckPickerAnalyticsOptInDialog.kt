package com.ichi2.anki.dialogs

import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.list.isItemChecked
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.analytics.UsageAnalytics

class DeckPickerAnalyticsOptInDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val d = MaterialDialog(activity!!)
                .title(R.string.analytics_dialog_title, null)
                .message(R.string.analytics_summ, null, null)
                .checkBoxPrompt(R.string.analytics_title, isCheckedDefault = true, onToggle = null)
                .positiveButton(R.string.dialog_continue, null) { dialog: MaterialDialog? ->
                    AnkiDroidApp.getSharedPrefs(context).edit()
                            .putBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, dialog!!.isItemChecked(0))
                            .apply()
                    (activity as DeckPicker?)!!.dismissAllDialogFragments()
                }
                .cancelable(true)
        d.setOnCancelListener { (activity as DeckPicker?)!!.dismissAllDialogFragments() }
        d.show()
        return d
    }

    companion object {
        @JvmStatic
        fun newInstance(): DeckPickerAnalyticsOptInDialog {
            return DeckPickerAnalyticsOptInDialog()
        }
    }
}