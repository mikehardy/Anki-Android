package com.ichi2.anki.dialogs

import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class ExportDialog : AnalyticsDialogFragment() {
    interface ExportDialogListener {
        fun exportApkg(path: String?, did: Long?, includeSched: Boolean, includeMedia: Boolean)
        fun dismissAllDialogFragments()
    }

    private val INCLUDE_SCHED = 0
    private val INCLUDE_MEDIA = 1
    private var mIncludeSched = false
    private var mIncludeMedia = false
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val res = resources
        val did = arguments!!.getLong("did", -1L)
        var initial = IntArray(0)
        if (did != -1L) {
            mIncludeSched = false
        } else {
            mIncludeSched = true
            initial = IntArray(1) { INCLUDE_SCHED }
        }
        val items = ArrayList<String>()
        items.add(res.getString(R.string.export_include_schedule))
        items.add(res.getString(R.string.export_include_media))
        return MaterialDialog(activity!!)
                .title(R.string.export, null)
                .message(null, arguments!!.getString("dialogMessage"), null)
                .cancelable(true)
                .listItemsMultiChoice(null, items, null, initial, true, false) { _, checked, _ ->
                                mIncludeMedia = false
                                mIncludeSched = false
                                for (integer in checked) {
                                    when (integer) {
                                        INCLUDE_SCHED -> mIncludeSched = true
                                        INCLUDE_MEDIA -> mIncludeMedia = true
                                    }
                                }
                            }
                .positiveButton(android.R.string.ok, null) {
                    (activity as ExportDialogListener)
                            .exportApkg(null, if (did != -1L) did else null, mIncludeSched, mIncludeMedia)
                    dismissAllDialogFragments()
                }
                .negativeButton(android.R.string.cancel, null) {
                    dismissAllDialogFragments()
                }
    }

    fun dismissAllDialogFragments() {
        (activity as ExportDialogListener?)!!.dismissAllDialogFragments()
    }

    companion object {
        /**
         * A set of dialogs which deal with importing a file
         *
         * @param did An integer which specifies which of the sub-dialogs to show
         * @param dialogMessage An optional string which can be used to show a custom message or specify import path
         */
        fun newInstance(dialogMessage: String, did: Long?): ExportDialog {
            val f = ExportDialog()
            val args = Bundle()
            args.putLong("did", did!!)
            args.putString("dialogMessage", dialogMessage)
            f.arguments = args
            return f
        }

        @JvmStatic
        fun newInstance(dialogMessage: String): ExportDialog {
            val f = ExportDialog()
            val args = Bundle()
            args.putString("dialogMessage", dialogMessage)
            f.arguments = args
            return f
        }
    }
}