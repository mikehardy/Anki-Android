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
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.list.listItems
import com.ichi2.anki.*
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.async.CollectionTask
import com.ichi2.async.CollectionTask.TaskData
import com.ichi2.libanki.Consts
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONObject
import timber.log.Timber
import java.util.*

class CustomStudyDialog : AnalyticsDialogFragment() {
    interface CustomStudyListener {
        fun onCreateCustomStudySession()
        fun onExtendStudyLimits()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val dialogId = arguments!!.getInt("id")
        return if (dialogId < 100) {
            // Select the specified deck
            CollectionHelper.getInstance().getCol(activity).decks.select(arguments!!.getLong("did"))
            buildContextMenu(dialogId)
        } else {
            buildInputDialog(dialogId)
        }
    }

    /**
     * Build a context menu for custom study
     * @param id
     * @return
     */
    private fun buildContextMenu(id: Int): MaterialDialog {
        val listIds = getListIds(id)
        val jumpToReviewer = arguments!!.getBoolean("jumpToReviewer")
        return MaterialDialog(AnkiDroidApp.getInstance().applicationContext)
                .title(R.string.custom_study)
                .cancelable(true)
//                .itemsIds(listIds)
//                .items(ContextMenuHelper.getValuesFromKeys(keyValueMap, listIds))
//                .itemsCallback(object : ListCallback() {
                .listItems(null, ContextMenuHelper.getValuesListFromKeys(keyValueMap, listIds)) { _, which, _ ->
                        val activity = activity as AnkiActivity?
                    val selectionKey = listIds!![which]
                    when (selectionKey) {
                            DECK_OPTIONS -> {

                                // User asked to permanently change the deck options
                                val i = Intent(activity, DeckOptions::class.java)
                                i.putExtra("did", arguments!!.getLong("did"))
                                getActivity()!!.startActivity(i)
                            }
                            MORE_OPTIONS -> {

                                // User asked to see all custom study options
                                val d = newInstance(CONTEXT_MENU_STANDARD,
                                        arguments!!.getLong("did"), jumpToReviewer)
                                activity!!.showDialogFragment(d)
                            }
                            CUSTOM_STUDY_TAGS -> {

                                /*
                                 * This is a special Dialog for CUSTOM STUDY, where instead of only collecting a
                                 * number, it is necessary to collect a list of tags. This case handles the creation
                                 * of that Dialog.
                                 */
                                val currentDeck = arguments!!.getLong("did")
                                val dialogFragment = TagsDialog.newInstance(
                                        TagsDialog.TYPE_CUSTOM_STUDY_TAGS, ArrayList(),
                                        ArrayList(activity!!.col.tags.byDeck(currentDeck, true)))
                                dialogFragment.setTagsDialogListener { selectedTags, option -> /*
                                         * Here's the method that gathers the final selection of tags, type of cards and
                                         * generates the search screen for the custom study deck.
                                         */
                                    val sb = StringBuilder()
                                    when (option) {
                                        1 -> sb.append("is:new ")
                                        2 -> sb.append("is:due ")
                                        else -> {
                                        }
                                    }
                                    val arr: MutableList<String?> = ArrayList()
                                    if (selectedTags.size > 0) {
                                        for (tag in selectedTags) {
                                            arr.add(String.format("tag:'%s'", tag))
                                        }
                                        sb.append("(").append(TextUtils.join(" or ", arr)).append(")")
                                    }
                                    createCustomStudySession(JSONArray(), arrayOf(sb.toString(),
                                            Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM), true)
                                }
                                activity.showDialogFragment(dialogFragment)
                            }
                            else -> {

                                // User asked for a standard custom study option
                                val d = newInstance(selectionKey,
                                        arguments!!.getLong("did"), jumpToReviewer)
                                (getActivity() as AnkiActivity?)!!.showDialogFragment(d)
                            }
                        }
                    }

    }

    /**
     * Build an input dialog that is used to get a parameter related to custom study from the user
     * @param dialogId
     * @return
     */
    private fun buildInputDialog(dialogId: Int): MaterialDialog {
        /*
            TODO: Try to change to a standard input dialog (currently the thing holding us back is having the extra
            TODO: hint line for the number of cards available, and having the pre-filled text selected by default)
        */
        // Input dialogs
        // Show input dialog for an individual custom study dialog
        val v = activity!!.layoutInflater.inflate(R.layout.styled_custom_study_details_dialog, null)
        val textView1 = v.findViewById<View>(R.id.custom_study_details_text1) as TextView
        val textView2 = v.findViewById<View>(R.id.custom_study_details_text2) as TextView
        val mEditText = v.findViewById<View>(R.id.custom_study_details_edittext2) as EditText
        // Set the text
        textView1.text = text1
        textView2.text = text2
        mEditText.setText(defaultValue)
        // Give EditText focus and show keyboard
        mEditText.setSelectAllOnFocus(true)
        mEditText.requestFocus()
        // deck id
        val did = arguments!!.getLong("did")
        // Whether or not to jump straight to the reviewer
        val jumpToReviewer = arguments!!.getBoolean("jumpToReviewer")
        // Set builder parameters
        val dialog = MaterialDialog(activity!!.applicationContext)
                .customView(view = v, scrollable = true)
                .positiveButton(R.string.dialog_ok) {
                    val col = CollectionHelper.getInstance().getCol(activity)
                    // Get the value selected by user
                    val n: Int = try {
                        mEditText.text.toString().toInt()
                    } catch (ignored: Exception) {
                        Int.MAX_VALUE
                    }
                    when (dialogId) {
                        CUSTOM_STUDY_NEW -> {
                            AnkiDroidApp.getSharedPrefs(activity).edit().putInt("extendNew", n).apply()
                            val deck = col.decks[did]
                            deck.put("extendNew", n)
                            col.decks.save(deck)
                            col.sched.extendLimits(n, 0)
                            onLimitsExtended(jumpToReviewer)
                        }
                        CUSTOM_STUDY_REV -> {
                            AnkiDroidApp.getSharedPrefs(activity).edit().putInt("extendRev", n).apply()
                            val deck = col.decks[did]
                            deck.put("extendRev", n)
                            col.decks.save(deck)
                            col.sched.extendLimits(0, n)
                            onLimitsExtended(jumpToReviewer)
                        }
                        CUSTOM_STUDY_FORGOT -> {
                            val ar = JSONArray()
                            ar.put(0, 1)
                            createCustomStudySession(ar, arrayOf(String.format(Locale.US,
                                    "rated:%d:1", n), Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM), false)
                        }
                        CUSTOM_STUDY_AHEAD -> {
                            createCustomStudySession(JSONArray(), arrayOf(String.format(Locale.US,
                                    "prop:due<=%d", n), Consts.DYN_MAX_SIZE, Consts.DYN_DUE), true)
                        }
                        CUSTOM_STUDY_RANDOM -> {
                            createCustomStudySession(JSONArray(), arrayOf("", n, Consts.DYN_RANDOM), true)
                        }
                        CUSTOM_STUDY_PREVIEW -> {
                            createCustomStudySession(JSONArray(), arrayOf("is:new added:" +
                                    n.toString(), Consts.DYN_MAX_SIZE, Consts.DYN_OLDEST), false)
                        }
                        else -> {
                        }
                    }
                }
                .negativeButton(R.string.dialog_cancel) { (activity as AnkiActivity?)!!.dismissAllDialogFragments() }
        mEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                dialog.getActionButton(WhichButton.POSITIVE).isEnabled = editable.isNotEmpty()
            }
        })

        // Show soft keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    private val keyValueMap: HashMap<Int, String>
        get() {
            val res = resources
            val keyValueMap = HashMap<Int, String>()
            keyValueMap[CONTEXT_MENU_STANDARD] = res.getString(R.string.custom_study)
            keyValueMap[CUSTOM_STUDY_NEW] = res.getString(R.string.custom_study_increase_new_limit)
            keyValueMap[CUSTOM_STUDY_REV] = res.getString(R.string.custom_study_increase_review_limit)
            keyValueMap[CUSTOM_STUDY_FORGOT] = res.getString(R.string.custom_study_review_forgotten)
            keyValueMap[CUSTOM_STUDY_AHEAD] = res.getString(R.string.custom_study_review_ahead)
            keyValueMap[CUSTOM_STUDY_RANDOM] = res.getString(R.string.custom_study_random_selection)
            keyValueMap[CUSTOM_STUDY_PREVIEW] = res.getString(R.string.custom_study_preview_new)
            keyValueMap[CUSTOM_STUDY_TAGS] = res.getString(R.string.custom_study_limit_tags)
            keyValueMap[DECK_OPTIONS] = res.getString(R.string.study_options)
            keyValueMap[MORE_OPTIONS] = res.getString(R.string.more_options)
            return keyValueMap
        }

    /**
     * Retrieve the list of ids to put in the context menu list
     * @param dialogId option to specify which tasks are shown in the list
     * @return the ids of which values to show
     */
    private fun getListIds(dialogId: Int): IntArray? {
        val col = (activity as AnkiActivity?)!!.col
        when (dialogId) {
            CONTEXT_MENU_STANDARD ->                 // Standard context menu
                return intArrayOf(CUSTOM_STUDY_NEW, CUSTOM_STUDY_REV, CUSTOM_STUDY_FORGOT, CUSTOM_STUDY_AHEAD,
                        CUSTOM_STUDY_RANDOM, CUSTOM_STUDY_PREVIEW, CUSTOM_STUDY_TAGS)
            CONTEXT_MENU_LIMITS ->                 // Special custom study options to show when the daily study limit has been reached
                return if (col.sched.newDue() && col.sched.revDue()) {
                    intArrayOf(CUSTOM_STUDY_NEW, CUSTOM_STUDY_REV, DECK_OPTIONS, MORE_OPTIONS)
                } else {
                    if (col.sched.newDue()) {
                        intArrayOf(CUSTOM_STUDY_NEW, DECK_OPTIONS, MORE_OPTIONS)
                    } else {
                        intArrayOf(CUSTOM_STUDY_REV, DECK_OPTIONS, MORE_OPTIONS)
                    }
                }
            CONTEXT_MENU_EMPTY_SCHEDULE ->                 // Special custom study options to show when extending the daily study limits is not applicable
                return intArrayOf(CUSTOM_STUDY_FORGOT, CUSTOM_STUDY_AHEAD, CUSTOM_STUDY_RANDOM,
                        CUSTOM_STUDY_PREVIEW, CUSTOM_STUDY_TAGS, DECK_OPTIONS)
            else -> {
            }
        }
        return null
    }

    private val text1: String
        get() {
            val res = AnkiDroidApp.getAppResources()
            val col = CollectionHelper.getInstance().getCol(activity)
            return when (arguments!!.getInt("id")) {
                CUSTOM_STUDY_NEW -> res.getString(R.string.custom_study_new_total_new, col.sched.totalNewForCurrentDeck())
                CUSTOM_STUDY_REV -> res.getString(R.string.custom_study_rev_total_rev, col.sched.totalRevForCurrentDeck())
                else -> ""
            }
        }

    private val text2: String
        get() {
            val res = AnkiDroidApp.getAppResources()
            return when (arguments!!.getInt("id")) {
                CUSTOM_STUDY_NEW -> res.getString(R.string.custom_study_new_extend)
                CUSTOM_STUDY_REV -> res.getString(R.string.custom_study_rev_extend)
                CUSTOM_STUDY_FORGOT -> res.getString(R.string.custom_study_forgotten)
                CUSTOM_STUDY_AHEAD -> res.getString(R.string.custom_study_ahead)
                CUSTOM_STUDY_RANDOM -> res.getString(R.string.custom_study_random)
                CUSTOM_STUDY_PREVIEW -> res.getString(R.string.custom_study_preview)
                else -> ""
            }
        }

    private val defaultValue: String
        get() {
            val prefs = AnkiDroidApp.getSharedPrefs(activity)
            return when (arguments!!.getInt("id")) {
                CUSTOM_STUDY_NEW -> Integer.toString(prefs.getInt("extendNew", 10))
                CUSTOM_STUDY_REV -> Integer.toString(prefs.getInt("extendRev", 50))
                CUSTOM_STUDY_FORGOT -> Integer.toString(prefs.getInt("forgottenDays", 1))
                CUSTOM_STUDY_AHEAD -> Integer.toString(prefs.getInt("aheadDays", 1))
                CUSTOM_STUDY_RANDOM -> Integer.toString(prefs.getInt("randomCards", 100))
                CUSTOM_STUDY_PREVIEW -> Integer.toString(prefs.getInt("previewDays", 1))
                else -> ""
            }
        }

    /**
     * Create a custom study session
     * @param delays delay options for scheduling algorithm
     * @param terms search terms
     * @param resched whether to reschedule the cards based on the answers given (or ignore them if false)
     */
    private fun createCustomStudySession(delays: JSONArray, terms: Array<Any>, resched: Boolean) {
        val dyn: JSONObject
        val activity = activity as AnkiActivity?
        val col = CollectionHelper.getInstance().getCol(activity)
        val did = arguments!!.getLong("did")
        val deckToStudyName = col.decks[did].getString("name")
        val customStudyDeck = resources.getString(R.string.custom_study_deck_name)
        val cur = col.decks.byName(customStudyDeck)
        if (cur != null) {
            Timber.i("Found deck: '%s'", customStudyDeck)
            if (cur.getInt("dyn") != 1) {
                Timber.w("Deck: '%s' was non-dynamic", customStudyDeck)
                MaterialDialog(getActivity()!!.applicationContext)
                        .message(R.string.custom_study_deck_exists)
                        .negativeButton(R.string.dialog_cancel)
                        .show()
                return
            } else {
                Timber.i("Emptying dynamic deck '%s' for custom study", customStudyDeck)
                // safe to empty
                col.sched.emptyDyn(cur.getLong("id"))
                // reuse; don't delete as it may have children
                dyn = cur
                col.decks.select(cur.getLong("id"))
            }
        } else {
            Timber.i("Creating Dynamic Deck '%s' for custom study", customStudyDeck)
            val customStudyDid = col.decks.newDyn(customStudyDeck)
            dyn = col.decks[customStudyDid]
        }
        if (!dyn.has("terms")) {
            //#5959 - temp code to diagnose why terms doesn't exist.
            // normally we wouldn't want to log this much, but we need to know how deep the corruption is to fix the
            // issue
            Timber.w("Invalid Dynamic Deck: %s", dyn)
            AnkiDroidApp.sendExceptionReport("Custom Study Deck had no terms", "CustomStudyDialog - createCustomStudySession")
            UIUtils.showThemedToast(this.context, getString(R.string.custom_study_rebuild_deck_corrupt), false)
            return
        }
        // and then set various options
        if (delays.length() > 0) {
            dyn.put("delays", delays)
        } else {
            dyn.put("delays", JSONObject.NULL)
        }
        val ar = dyn.getJSONArray("terms")
        ar.getJSONArray(0).put(0, "deck:\"" + deckToStudyName + "\" " + terms[0])
        ar.getJSONArray(0).put(1, terms[1])
        ar.getJSONArray(0).put(2, terms[2])
        dyn.put("resched", resched)
        // Rebuild the filtered deck
        Timber.i("Rebuilding Custom Study Deck")
        CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_REBUILD_CRAM, object : CollectionTask.TaskListener() {
            override fun onPreExecute() {
                activity!!.showProgressBar()
            }

            override fun onPostExecute(result: TaskData) {
                activity!!.hideProgressBar()
                (activity as CustomStudyListener?)!!.onCreateCustomStudySession()
            }
        })

        // Hide the dialogs
        activity!!.dismissAllDialogFragments()
    }

    private fun onLimitsExtended(jumpToReviewer: Boolean) {
        val activity = activity as AnkiActivity?
        if (jumpToReviewer) {
            activity!!.startActivityForResultWithoutAnimation(Intent(activity, Reviewer::class.java), AnkiActivity.REQUEST_REVIEW)
            CollectionHelper.getInstance().getCol(activity).startTimebox()
        } else {
            (activity as CustomStudyListener?)!!.onExtendStudyLimits()
        }
        activity!!.dismissAllDialogFragments()
    }

    companion object {
        // Different configurations for the context menu
        const val CONTEXT_MENU_STANDARD = 0
        const val CONTEXT_MENU_LIMITS = 1
        const val CONTEXT_MENU_EMPTY_SCHEDULE = 2

        // Standard custom study options to show in the context menu
        private const val CUSTOM_STUDY_NEW = 100
        private const val CUSTOM_STUDY_REV = 101
        private const val CUSTOM_STUDY_FORGOT = 102
        private const val CUSTOM_STUDY_AHEAD = 103
        private const val CUSTOM_STUDY_RANDOM = 104
        private const val CUSTOM_STUDY_PREVIEW = 105
        private const val CUSTOM_STUDY_TAGS = 106

        // Special items to put in the context menu
        private const val DECK_OPTIONS = 107
        private const val MORE_OPTIONS = 108

        /**
         * Instance factories
         */
        @JvmStatic
        @JvmOverloads
        fun newInstance(id: Int, did: Long, jumpToReviewer: Boolean = false): CustomStudyDialog {
            val f = CustomStudyDialog()
            val args = Bundle()
            args.putInt("id", id)
            args.putLong("did", did)
            args.putBoolean("jumpToReviewer", jumpToReviewer)
            f.arguments = args
            return f
        }
    }
}