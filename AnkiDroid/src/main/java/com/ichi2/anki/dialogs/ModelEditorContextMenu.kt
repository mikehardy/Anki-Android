/****************************************************************************************
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class ModelEditorContextMenu : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val entries = ArrayList<String>()
        entries.add(resources.getString(R.string.model_field_editor_reposition_menu))
        entries.add(resources.getString(R.string.model_field_editor_sort_field))
        entries.add(resources.getString(R.string.model_field_editor_rename))
        entries.add(resources.getString(R.string.model_field_editor_delete))
        entries.add(resources.getString(R.string.model_field_editor_toggle_sticky))
        return MaterialDialog(AnkiDroidApp.getInstance().applicationContext)
                .title(null, arguments!!.getString("label"))
                .listItems(null, entries) { _, index, _ ->
                    mContextMenu?.onSelect(index)
                }
    }

    companion object {
        const val FIELD_REPOSITION = 0
        const val SORT_FIELD = 1
        const val FIELD_RENAME = 2
        const val FIELD_DELETE = 3
        const val FIELD_TOGGLE_STICKY = 4
        private var mContextMenu: MaterialDialogSingleItemCallback? = null
        @JvmStatic
        fun newInstance(label: String?, contextMenu: MaterialDialogSingleItemCallback?): ModelEditorContextMenu {
            val n = ModelEditorContextMenu()
            mContextMenu = contextMenu
            val b = Bundle()
            b.putString("label", label)
            mContextMenu = contextMenu
            n.arguments = b
            return n
        }
    }
}