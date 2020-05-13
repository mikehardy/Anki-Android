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
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class CardBrowserOrderDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val items = resources.getStringArray(R.array.card_browser_order_labels)
        val itemList: ArrayList<String> = ArrayList()
        // Set sort order arrow
        for (i in items.indices) {
            if (i != CardBrowser.CARD_ORDER_NONE && i == arguments!!.getInt("order")) {
                if (arguments!!.getBoolean("isOrderAsc")) {
                    items[i] = items[i].toString() + " (\u25b2)"
                } else {
                    items[i] = items[i].toString() + " (\u25bc)"
                }
            }
            itemList.add(items[i])
        }

        return MaterialDialog(AnkiDroidApp.getInstance().applicationContext)
                .title(R.string.card_browser_change_display_order_title)
                .message(R.string.card_browser_change_display_order_reverse)
                .listItems(null, itemList) { _, index, _ ->
                    mOrderDialog?.onSelect(index)
                }
    }

    companion object {
        private var mOrderDialog: MaterialDialogSingleItemCallback? = null
        @JvmStatic
        fun newInstance(order: Int, isOrderAsc: Boolean,
                        orderDialog: MaterialDialogSingleItemCallback): CardBrowserOrderDialog {
            mOrderDialog =  orderDialog
            val f = CardBrowserOrderDialog()
            val args = Bundle()
            args.putInt("order", order)
            args.putBoolean("isOrderAsc", isOrderAsc)
            f.arguments = args
            return f
        }
    }
}