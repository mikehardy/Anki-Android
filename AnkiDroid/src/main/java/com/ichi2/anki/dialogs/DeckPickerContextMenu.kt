/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2></perceptualchaos2>@gmail.com>                          *
 * *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 * *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 * *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                           *
 */
package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.CustomStudyDialog.Companion.newInstance
import timber.log.Timber
import java.util.*

class DeckPickerContextMenu : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val did = arguments!!.getLong("did")
        val title = CollectionHelper.getInstance().getCol(context).decks.name(did)
        val itemIds = listIds
        return MaterialDialog(activity!!.applicationContext)
                .title(text = title)
                .cancelable(true)
                .noAutoDismiss()
                // FIXME make sure the selection indices line up with the keys in the switch in callback
                .listItems(items = ContextMenuHelper.getValuesListFromKeys(keyValueMap, itemIds)) { _, which, _ ->
                    mContextMenuListener.onSelect(which)
                }
    }

    private val keyValueMap: HashMap<Int, String>
        get() {
            val res = resources
            val keyValueMap = HashMap<Int, String>()
            keyValueMap[CONTEXT_MENU_RENAME_DECK] = res.getString(R.string.rename_deck)
            keyValueMap[CONTEXT_MENU_DECK_OPTIONS] = res.getString(R.string.study_options)
            keyValueMap[CONTEXT_MENU_CUSTOM_STUDY] = res.getString(R.string.custom_study)
            keyValueMap[CONTEXT_MENU_DELETE_DECK] = res.getString(R.string.contextmenu_deckpicker_delete_deck)
            keyValueMap[CONTEXT_MENU_EXPORT_DECK] = res.getString(R.string.export_deck)
            keyValueMap[CONTEXT_MENU_UNBURY] = res.getString(R.string.unbury)
            keyValueMap[CONTEXT_MENU_CUSTOM_STUDY_REBUILD] = res.getString(R.string.rebuild_cram_label)
            keyValueMap[CONTEXT_MENU_CUSTOM_STUDY_EMPTY] = res.getString(R.string.empty_cram_label)
            return keyValueMap
        }

    /**
     * Retrieve the list of ids to put in the context menu list
     * @return the ids of which values to show
     */
    private val listIds: IntArray
        get() {
            val col = CollectionHelper.getInstance().getCol(context)
            val did = arguments!!.getLong("did")
            val itemIds = ArrayList<Int>()
            if (col.decks.isDyn(did)) {
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY_REBUILD)
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY_EMPTY)
            }
            itemIds.add(CONTEXT_MENU_RENAME_DECK)
            itemIds.add(CONTEXT_MENU_DECK_OPTIONS)
            if (!col.decks.isDyn(did)) {
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY)
            }
            itemIds.add(CONTEXT_MENU_DELETE_DECK)
            itemIds.add(CONTEXT_MENU_EXPORT_DECK)
            if (col.sched.haveBuried(did)) {
                itemIds.add(CONTEXT_MENU_UNBURY)
            }
            return ContextMenuHelper.integerListToArray(itemIds)
        }

    // Handle item selection on context menu which is shown when the user long-clicks on a deck
    private val mContextMenuListener: MaterialDialogSingleItemCallback = MaterialDialogSingleItemCallback { which ->
            when (which) {
                CONTEXT_MENU_DELETE_DECK -> {
                    Timber.i("Delete deck selected")
                    (activity as DeckPicker?)!!.confirmDeckDeletion()
                }
                CONTEXT_MENU_DECK_OPTIONS -> {
                    Timber.i("Open deck options selected")
                    (activity as DeckPicker?)!!.showContextMenuDeckOptions()
                    (activity as AnkiActivity?)!!.dismissAllDialogFragments()
                }
                CONTEXT_MENU_CUSTOM_STUDY -> {
                    Timber.i("Custom study option selected")
                    val did = arguments!!.getLong("did")
                    val d = newInstance(
                            CustomStudyDialog.CONTEXT_MENU_STANDARD, did)
                    (activity as AnkiActivity?)!!.showDialogFragment(d)
                }
                CONTEXT_MENU_RENAME_DECK -> {
                    Timber.i("Rename deck selected")
                    (activity as DeckPicker?)!!.renameDeckDialog()
                }
                CONTEXT_MENU_EXPORT_DECK -> {
                    Timber.i("Export deck selected")
                    (activity as DeckPicker?)!!.showContextMenuExportDialog()
                }
                CONTEXT_MENU_UNBURY -> {
                    Timber.i("Unbury deck selected")
                    val col = CollectionHelper.getInstance().getCol(context)
                    col.sched.unburyCardsForDeck(arguments!!.getLong("did"))
                    (activity as StudyOptionsListener?)!!.onRequireDeckListUpdate()
                    (activity as AnkiActivity?)!!.dismissAllDialogFragments()
                }
                CONTEXT_MENU_CUSTOM_STUDY_REBUILD -> {
                    Timber.i("Empty deck selected")
                    (activity as DeckPicker?)!!.rebuildFiltered()
                    (activity as AnkiActivity?)!!.dismissAllDialogFragments()
                }
                CONTEXT_MENU_CUSTOM_STUDY_EMPTY -> {
                    Timber.i("Empty deck selected")
                    (activity as DeckPicker?)!!.emptyFiltered()
                    (activity as AnkiActivity?)!!.dismissAllDialogFragments()
                }
            }
        }


    companion object {
        /**
         * Context Menus
         */
        private const val CONTEXT_MENU_RENAME_DECK = 0
        private const val CONTEXT_MENU_DECK_OPTIONS = 1
        private const val CONTEXT_MENU_CUSTOM_STUDY = 2
        private const val CONTEXT_MENU_DELETE_DECK = 3
        private const val CONTEXT_MENU_EXPORT_DECK = 4
        private const val CONTEXT_MENU_UNBURY = 5
        private const val CONTEXT_MENU_CUSTOM_STUDY_REBUILD = 6
        private const val CONTEXT_MENU_CUSTOM_STUDY_EMPTY = 7
        @JvmStatic
        fun newInstance(did: Long): DeckPickerContextMenu {
            val f = DeckPickerContextMenu()
            val args = Bundle()
            args.putLong("did", did)
            f.arguments = args
            return f
        }
    }
}