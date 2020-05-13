package com.ichi2.anki.dialogs

import android.os.Bundle
import android.os.Message
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.ichi2.anki.*
import java.io.File
import java.io.IOException
import java.util.*

class DatabaseErrorDialog : AsyncDialogFragment() {
    private var mBackups = ArrayList<File?>()
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val mType = arguments!!.getInt("dialogType")
        val res = resources
        val dialog = MaterialDialog(activity!!.applicationContext)
        dialog.cancelable(true)
                .title(text = title)
        var sqliteInstalled = false
        try {
            sqliteInstalled = Runtime.getRuntime().exec("sqlite3 --version").waitFor() == 0
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        when (mType) {
            DIALOG_LOAD_FAILED -> {

                // Collection failed to load; give user the option of either choosing from repair options, or closing
                // the activity
                dialog.cancelable(false)
                        .message(text = message)
                        .icon(R.attr.dialogErrorIcon)
                        .positiveButton(R.string.error_handling_options) {
                            (activity as DeckPicker?)
                                    ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                        }
                        .negativeButton(R.string.close) {  exit() }
                        .show()
            }
            DIALOG_DB_ERROR -> {

                // Database Check failed to execute successfully; give user the option of either choosing from repair
                // options, submitting an error report, or closing the activity
                @Suppress("DEPRECATION")
                dialog
                        .cancelable(false)
                        .message(text = message)
                        .icon(R.attr.dialogErrorIcon)
                        .positiveButton(R.string.error_handling_options) {
                            (activity as DeckPicker?)
                                    ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                        }
                        .negativeButton(R.string.answering_error_report) {
                            (activity as DeckPicker?)!!.sendErrorReport()
                            dismissAllDialogFragments()
                        }
                        .neutralButton(R.string.close) { exit() }
                dialog.getActionButton(WhichButton.NEGATIVE).isEnabled = (activity as DeckPicker?)!!.hasErrorFiles()
                dialog.show()
            }
            DIALOG_ERROR_HANDLING -> {

                // The user has asked to see repair options; allow them to choose one of the repair options or go back
                // to the previous dialog
                val options = ArrayList<String>()
                if (!(activity as AnkiActivity?)!!.colIsOpen()) {
                    // retry
                    options.add(res.getString(R.string.backup_retry_opening))
                } else {
                    // fix integrity
                    options.add(res.getString(R.string.check_db))
                }
                // repair db with sqlite
                if (sqliteInstalled) {
                    options.add(res.getString(R.string.backup_error_menu_repair))
                }
                // // restore from backup
                options.add(res.getString(R.string.backup_restore))
                // delete old collection and build new one
                options.add(res.getString(R.string.backup_full_sync_from_server))
                // delete old collection and build new one
                options.add(res.getString(R.string.backup_del_collection))
                dialog.icon(R.attr.dialogErrorIcon)
                        .negativeButton(R.string.dialog_cancel)
                        .listItems(items = options) { _, _, charSequence ->
                            when (charSequence) {
                                res.getString(R.string.backup_retry_opening) -> {
                                    (activity as DeckPicker?)!!.restartActivity()
                                }
                                res.getString(R.string.check_db) -> {
                                    (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_CONFIRM_DATABASE_CHECK)
                                }
                                res.getString(R.string.backup_error_menu_repair) -> {
                                    (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_REPAIR_COLLECTION)
                                }
                                res.getString(R.string.backup_restore) -> {
                                    (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                                }
                                res.getString(R.string.backup_full_sync_from_server) -> {
                                    (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_FULL_SYNC_FROM_SERVER)
                                }
                                res.getString(R.string.backup_del_collection) -> {
                                    (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_NEW_COLLECTION)
                                }
                                else -> throw RuntimeException("Unknown dialog selection: $charSequence")
                            }
                        }
                        .show()
            }
            DIALOG_REPAIR_COLLECTION -> {

                // Allow user to run BackupManager.repairCollection()
                dialog.message(text = message)
                        .icon(R.attr.dialogErrorIcon)
                        .negativeButton(R.string.dialog_cancel)
                        .positiveButton(R.string.dialog_positive_repair) {
                            (activity as DeckPicker?)!!.repairDeck()
                            dismissAllDialogFragments()
                        }
                        .show()
            }
            DIALOG_RESTORE_BACKUP -> {

                // Allow user to restore one of the backups
                val path = CollectionHelper.getCollectionPath(activity)
                val files = BackupManager.getBackups(File(path))
                mBackups = ArrayList<File?>(files.size)
                var i = 0
                while (i < files.size) {
                    mBackups[i] = files[files.size - 1 - i]
                    i++
                }
                if (mBackups.size == 0) {
                    dialog.title(R.string.backup_restore)
                            .message(text = message)
                            .positiveButton(R.string.dialog_ok) {
                                (activity as DeckPicker?)
                                        ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                            }
                } else {
                    val dates = ArrayList<String>(mBackups.size)
                    var j = 0
                    while (j < mBackups.size) {
                        dates[j] = mBackups[j]!!.name.replace(
                                ".*-(\\d{4}-\\d{2}-\\d{2})-(\\d{2})-(\\d{2}).apkg".toRegex(), "$1 ($2:$3 h)")
                        j++
                    }
                    dialog.title(R.string.backup_restore_select_title)
                            .negativeButton(R.string.dialog_cancel) { dismissAllDialogFragments() }
                            .listItemsSingleChoice(items = dates) {
                                     _, which, _ ->
                                        if (mBackups[which]!!.length() > 0) {
                                            // restore the backup if it's valid
                                            (activity as DeckPicker?)
                                                    ?.restoreFromBackup(mBackups[which]
                                                            ?.path)
                                            dismissAllDialogFragments()
                                        } else {
                                            // otherwise show an error dialog
                                            MaterialDialog(this.context!!)
                                                    .title(R.string.backup_error)
                                                    .message(R.string.backup_invalid_file_error)
                                                    .positiveButton(R.string.dialog_ok)
                                                    .show()
                                        }
                                    }
                }
                dialog.show()
            }
            DIALOG_NEW_COLLECTION -> {

                // Allow user to create a new empty collection
                dialog.message(text = message)
                        .negativeButton(R.string.dialog_cancel)
                        .positiveButton(R.string.dialog_positive_create) {
                            CollectionHelper.getInstance().closeCollection(false, "DatabaseErrorDialog: Before Create New Collection")
                            val path1 = CollectionHelper.getCollectionPath(activity)
                            if (BackupManager.moveDatabaseToBrokenFolder(path1, false)) {
                                (activity as DeckPicker?)!!.restartActivity()
                            } else {
                                (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_LOAD_FAILED)
                            }
                        }
                        .show()
            }
            DIALOG_CONFIRM_DATABASE_CHECK -> {

                // Confirmation dialog for database check
                dialog.message(text = message)
                        .negativeButton(R.string.dialog_cancel)
                        .positiveButton(R.string.dialog_ok) {
                            (activity as DeckPicker?)!!.integrityCheck()
                            dismissAllDialogFragments()
                        }
                        .show()
            }
            DIALOG_CONFIRM_RESTORE_BACKUP -> {

                // Confirmation dialog for backup restore
                dialog.message(text = message)
                        .negativeButton(R.string.dialog_cancel)
                        .positiveButton(R.string.dialog_continue) {
                            (activity as DeckPicker?)!!
                                    .showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                        }
                        .show()
            }
            DIALOG_FULL_SYNC_FROM_SERVER -> {

                // Allow user to do a full-sync from the server
                dialog.message(text = message)
                        .negativeButton(R.string.dialog_cancel)
                        .positiveButton(R.string.dialog_positive_overwrite) {
                            (activity as DeckPicker?)!!.sync("download")
                            dismissAllDialogFragments()
                        }
                        .show()
            }
            DIALOG_DB_LOCKED -> {

                //If the database is locked, all we can do is ask the user to exit.
                dialog.message(text = message)
                        .cancelable(false)
                        .negativeButton(R.string.close)
                        .positiveButton(R.string.dialog_ok) {  exit() }
                        .show()
            }
        }
        return dialog
    }

    private fun exit() {
        (activity as DeckPicker?)!!.exit()
    }// Generic message shown when a libanki task failed

    // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
    // Show a specific message appropriate for the situation
    private val message: String
         get() = when (arguments!!.getInt("dialogType")) {
            DIALOG_LOAD_FAILED -> if (databaseCorruptFlag) {
                // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
                // Show a specific message appropriate for the situation
                res().getString(R.string.corrupt_db_message, res().getString(R.string.repair_deck))
            } else {
                // Generic message shown when a libanki task failed
                res().getString(R.string.access_collection_failed_message, res().getString(R.string.link_help))
            }
            DIALOG_DB_ERROR -> res().getString(R.string.answering_error_message)
            DIALOG_REPAIR_COLLECTION -> res().getString(R.string.repair_deck_dialog, BackupManager.BROKEN_DECKS_SUFFIX)
            DIALOG_RESTORE_BACKUP -> res().getString(R.string.backup_restore_no_backups)
            DIALOG_NEW_COLLECTION -> res().getString(R.string.backup_del_collection_question)
            DIALOG_CONFIRM_DATABASE_CHECK -> res().getString(R.string.check_db_warning)
            DIALOG_CONFIRM_RESTORE_BACKUP -> res().getString(R.string.restore_backup)
            DIALOG_FULL_SYNC_FROM_SERVER -> res().getString(R.string.backup_full_sync_from_server_question)
            DIALOG_DB_LOCKED -> res().getString(R.string.database_locked_summary)
            else -> arguments!!.getString("dialogMessage")!!
        }

    private val title: String
        get() = when (arguments!!.getInt("dialogType")) {
            DIALOG_LOAD_FAILED -> res().getString(R.string.open_collection_failed_title)
            DIALOG_DB_ERROR -> res().getString(R.string.answering_error_title)
            DIALOG_ERROR_HANDLING -> res().getString(R.string.error_handling_title)
            DIALOG_REPAIR_COLLECTION -> res().getString(R.string.backup_repair_deck)
            DIALOG_RESTORE_BACKUP -> res().getString(R.string.backup_restore)
            DIALOG_NEW_COLLECTION -> res().getString(R.string.backup_new_collection)
            DIALOG_CONFIRM_DATABASE_CHECK -> res().getString(R.string.check_db_title)
            DIALOG_CONFIRM_RESTORE_BACKUP -> res().getString(R.string.restore_backup_title)
            DIALOG_FULL_SYNC_FROM_SERVER -> res().getString(R.string.backup_full_sync_from_server)
            DIALOG_DB_LOCKED -> res().getString(R.string.database_locked_title)
            else -> res().getString(R.string.answering_error_title)
        }

    override fun getNotificationMessage(): String {
        return when (arguments!!.getInt("dialogType")) {
            else -> message
        }
    }

    override fun getNotificationTitle(): String {
        return when (arguments!!.getInt("dialogType")) {
            else -> res().getString(R.string.answering_error_title)
        }
    }

    override fun getDialogHandlerMessage(): Message {
        val msg = Message.obtain()
        msg.what = DialogHandler.MSG_SHOW_DATABASE_ERROR_DIALOG
        val b = Bundle()
        b.putInt("dialogType", arguments!!.getInt("dialogType"))
        msg.data = b
        return msg
    }

    fun dismissAllDialogFragments() {
        (activity as DeckPicker?)!!.dismissAllDialogFragments()
    }

    companion object {
        const val DIALOG_LOAD_FAILED = 0
        const val DIALOG_DB_ERROR = 1
        const val DIALOG_ERROR_HANDLING = 2
        const val DIALOG_REPAIR_COLLECTION = 3
        const val DIALOG_RESTORE_BACKUP = 4
        const val DIALOG_NEW_COLLECTION = 5
        const val DIALOG_CONFIRM_DATABASE_CHECK = 6
        const val DIALOG_CONFIRM_RESTORE_BACKUP = 7
        const val DIALOG_FULL_SYNC_FROM_SERVER = 8

        /** If the database is locked, all we can do is reset the app  */
        const val DIALOG_DB_LOCKED = 9

        // public flag which lets us distinguish between inaccessible and corrupt database
        @JvmField
        var databaseCorruptFlag = false

        /**
         * A set of dialogs which deal with problems with the database when it can't load
         *
         * @param dialogType An integer which specifies which of the sub-dialogs to show
         */
        @JvmStatic
        fun newInstance(dialogType: Int): DatabaseErrorDialog {
            val f = DatabaseErrorDialog()
            val args = Bundle()
            args.putInt("dialogType", dialogType)
            f.arguments = args
            return f
        }
    }
}