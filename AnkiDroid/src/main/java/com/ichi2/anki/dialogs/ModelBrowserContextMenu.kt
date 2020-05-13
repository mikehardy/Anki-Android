package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class ModelBrowserContextMenu : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val entries = ArrayList<String>(3)
        entries.add(resources.getString(R.string.model_browser_template))
        entries.add(resources.getString(R.string.model_browser_rename))
        entries.add(resources.getString(R.string.model_browser_delete))
        return MaterialDialog(activity!!.applicationContext)
                .title(text = arguments!!.getString("label"))
                .listItems(items = entries) { _, which, _ -> mContextMenuListener?.onSelect(which) }

    }

    companion object {
        const val MODEL_TEMPLATE = 0
        const val MODEL_RENAME = 1
        const val MODEL_DELETE = 2
        private var mContextMenuListener: MaterialDialogSingleItemCallback? = null
        @JvmStatic
        fun newInstance(label: String?, contextMenuListener: MaterialDialogSingleItemCallback?): ModelBrowserContextMenu {
            mContextMenuListener = contextMenuListener
            val n = ModelBrowserContextMenu()
            val b = Bundle()
            b.putString("label", label)
            n.arguments = b
            return n
        }
    }
}