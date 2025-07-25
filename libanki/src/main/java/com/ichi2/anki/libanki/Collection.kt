/*
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General private License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General private License for more details.
 *
 * You should have received a copy of the GNU General private License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/33a923797afc9655c3b4f79847e1705a1f998d03/pylib/anki/browser.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

// "FunctionName": many libAnki functions used to have leading _s
@file:Suppress("FunctionName")

package com.ichi2.anki.libanki

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import anki.card_rendering.EmptyCardsReport
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.config.ConfigKey
import anki.config.Preferences
import anki.config.copy
import anki.import_export.CsvMetadata
import anki.import_export.ExportAnkiPackageOptions
import anki.import_export.ExportLimit
import anki.import_export.ImportAnkiPackageOptions
import anki.import_export.ImportCsvRequest
import anki.import_export.ImportResponse
import anki.import_export.csvMetadataRequest
import anki.search.BrowserColumns
import anki.search.BrowserRow
import anki.search.SearchNode
import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.Utils.ids2str
import com.ichi2.anki.libanki.backend.model.toBackendNote
import com.ichi2.anki.libanki.backend.model.toProtoBuf
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.exception.InvalidSearchException
import com.ichi2.anki.libanki.sched.DummyScheduler
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.NotInLibAnki
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import timber.log.Timber
import java.io.File

typealias ImportLogWithChanges = anki.import_export.ImportResponse

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.
@KotlinCleanup("inline function in init { } so we don't need to init `crt` etc... at the definition")
@RustCleanup("combine with BackendImportExport")
@WorkerThread
class Collection(
    /**
     *  The path to the folder containing collection.anki2 database. Must be unicode and openable with [File].
     */

    val collectionFiles: CollectionFiles,
    /**
     * Outside of libanki, you should not access the backend directly for collection operations.
     * Operations that work on a closed collection (eg importing), or do not require a collection
     * at all (eg translations) are the exception.
     */
    val backend: Backend,
    databaseBuilder: (Backend) -> DB,
) {
    val colDb = collectionFiles.colDb

    /** Access backend translations */
    val tr = backend.tr

    @get:JvmName("isDbClosed")
    val dbClosed: Boolean
        get() {
            return dbInternal == null
        }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun debugEnsureNoOpenPointers() {
        val result = backend.getActiveSequenceNumbers()
        if (result.isNotEmpty()) {
            val numbers = result.toString()
            throw IllegalStateException("Contained unclosed sequence numbers: $numbers")
        }
    }

    // a lot of legacy code does not check for nullability
    val db: DB
        get() = dbInternal!!

    var dbInternal: DB? = null

    /**
     * Getters/Setters ********************************************************** *************************************
     */

    val media: Media

    lateinit var decks: Decks
        protected set

    val tags: Tags

    lateinit var config: Config

    @KotlinCleanup("see if we can inline a function inside init {} and make this `val`")
    lateinit var sched: Scheduler
        protected set

    private var startTime: Long
    private var startReps: Int

    private val lastSync: Long
        get() = db.queryLongScalar("select ls from col")

    var ls: Long = 0
    // END: SQL table columns

    init {
        media = Media(this)
        tags = Tags(this)
        val created = reopen(databaseBuilder = databaseBuilder)
        startReps = 0
        startTime = 0
        _loadScheduler()
        if (created) {
            config.set("schedVer", 2)
            // we need to reload the scheduler: this was previously loaded as V1
            _loadScheduler()
        }
    }

    fun name() = collectionFiles.collectionName

    /*
     * Scheduler
     * ***********************************************************
     */

    /**
     * For backwards compatibility, the v3 scheduler currently returns 2.
     * Use the separate [v3Scheduler] method to check if it is active.
     */
    @LibAnkiAlias("sched_ver")
    fun schedVer(): Int {
        @RustCleanup("move outside this method")
        @LibAnkiAlias("_supported_scheduler_versions")
        val supportedSchedulerVersions = listOf(1, 2)

        // for backwards compatibility, v3 is represented as 2
        val ver = config.get("schedVer") ?: 1
        if (ver in supportedSchedulerVersions) {
            return ver
        } else {
            throw RuntimeException("Unsupported scheduler version")
        }
    }

    @RustCleanup("doesn't match upstream")
    fun _loadScheduler() {
        val ver = schedVer()
        if (ver == 1) {
            sched = DummyScheduler(this)
        } else if (ver == 2) {
            if (!backend.getConfigBool(ConfigKey.Bool.SCHED_2021)) {
                backend.setConfigBool(ConfigKey.Bool.SCHED_2021, true, undoable = false)
            }
            sched = Scheduler(this)
            if (config.get<Int>("creationOffset") == null) {
                val prefs =
                    getPreferences().copy {
                        scheduling = scheduling.copy { newTimezone = true }
                    }
                setPreferences(prefs)
            }
        }
    }

    @LibAnkiAlias("v3_scheduler")
    fun v3Scheduler(): Boolean = schedVer() == 2 && backend.getConfigBool(ConfigKey.Bool.SCHED_2021)

    /**
     * @throws RuntimeException [enabled] requested, but not using the [schedVer][v2 scheduler]
     */
    @LibAnkiAlias("set_v3_scheduler")
    fun setV3Scheduler(enabled: Boolean) {
        if (this.v3Scheduler() != enabled) {
            if (enabled && schedVer() != 2) {
                throw RuntimeException("must upgrade to v2 scheduler first")
            }
            config.setBool(ConfigKey.Bool.SCHED_2021, enabled)
            _loadScheduler()
        }
    }

    /*
     * DB-related
     * ***********************************************************
     */

    // legacy properties; these will likely go away in the future

    val mod: Long
        get() = db.queryLongScalar("select mod from col")

    @RustCleanup("remove")
    @NotInLibAnki
    val scm: Long
        get() = db.queryLongScalar("select scm from col")

    /**
     * Disconnect from DB.
     * Python implementation has a save argument for legacy reasons;
     * AnkiDroid always saves as changes are made.
     */
    @Synchronized
    @LibAnkiAlias("close")
    @RustCleanup("doesn't match upstream")
    fun close(
        downgrade: Boolean = false,
        forFullSync: Boolean = false,
    ) {
        if (!dbClosed) {
            if (!forFullSync) {
                backend.closeCollection(downgrade)
            }
            dbInternal = null
            Timber.i("Collection closed")
        }
    }

    @LibAnkiAlias("close_for_full_sync")
    fun closeForFullSync() {
        // save and cleanup, but backend will take care of collection close
        if (dbInternal != null) {
            clearCaches()
            dbInternal = null
        }
    }

    @LibAnkiAlias("_clear_caches")
    private fun clearCaches() {
        notetypes.clearCache()
    }

    /** True if DB was created */
    @RustCleanup("doesn't match upstream")
    @LibAnkiAlias("reopen")
    fun reopen(
        afterFullSync: Boolean = false,
        databaseBuilder: (Backend) -> DB,
    ): Boolean {
        Timber.i("(Re)opening Database: %s", colDb)
        return if (dbClosed) {
            val (database, created) =
                Storage.openDB(
                    path = colDb,
                    backend = backend,
                    afterFullSync = afterFullSync,
                    buildDatabase = databaseBuilder,
                )
            dbInternal = database
            load()
            if (afterFullSync) {
                _loadScheduler()
            }
            created
        } else {
            false
        }
    }

    @NotInLibAnki
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun load() {
        notetypes = Notetypes(this)
        decks = Decks(this)
        config = Config(backend)
    }

    /** Mark schema modified to force a full
     * sync, but with the confirmation checking function disabled This
     * is equivalent to `modSchema(False)` in Anki. A distinct method
     * is used so that the type does not states that an exception is
     * thrown when in fact it is never thrown.
     */
    @NotInLibAnki
    fun modSchemaNoCheck() {
        db.execute(
            "update col set scm=?, mod=?",
            TimeManager.time.intTimeMS(),
            TimeManager.time.intTimeMS(),
        )
    }

    /** Mark schema modified to cause a one-way sync.
     * ConfirmModSchemaException will be thrown if the user needs to be prompted to confirm the action.
     * If the user chooses to confirm then modSchemaNoCheck should be called, after which the exception can
     * be safely ignored, and the outer code called again.
     *
     * @throws ConfirmModSchemaException
     */
    @LibAnkiAlias("mod_schema")
    fun modSchema() {
        if (!schemaChanged()) {
            /* In Android we can't show a dialog which blocks the main UI thread
             Therefore we can't wait for the user to confirm if they want to do
             a one-way sync here, and we instead throw an exception asking the outer
             code to handle the user's choice */
            throw ConfirmModSchemaException()
        }
        modSchemaNoCheck()
    }

    /** `true` if schema changed since last sync. */
    @LibAnkiAlias("schema_changed")
    @RustCleanup("doesn't match upstream")
    fun schemaChanged(): Boolean = scm > lastSync

    @LibAnkiAlias("usn")
    fun usn(): Int = -1

    /*
     * Import/export
     * ***********************************************************
     */

    /**
     * (Maybe) create a colpkg backup, while keeping the collection open. If the
     * configured backup interval has not elapsed, and force=false, no backup will be created,
     * and this routine will return false.
     *
     * There must not be an active transaction.
     *
     * If `waitForCompletion` is true, block until the backup completes. Otherwise this routine
     * returns quickly, and the backup can be awaited on a background thread with awaitBackupCompletion()
     * to check for success.
     *
     * Backups are automatically expired according to the user's settings.
     */
    @LibAnkiAlias("create_backup")
    fun createBackup(
        backupFolder: String,
        force: Boolean,
        waitForCompletion: Boolean,
    ): Boolean {
        // ensure any pending transaction from legacy code/add-ons has been committed
        val created =
            backend.createBackup(
                backupFolder = backupFolder,
                force = force,
                waitForCompletion = waitForCompletion,
            )
        return created
    }

    /**
     * If a backup is running, block until it completes, throwing if it fails, or already
     * failed, and the status has not yet been checked. On failure, an error is only returned
     * once; subsequent calls are a no-op until another backup is run.
     *
     * @throws Exception if backup creation failed, no-op after first throw
     */
    @LibAnkiAlias("await_backup_completion")
    fun awaitBackupCompletion() {
        backend.awaitBackupCompletion()
    }

    // export_collection_package is in AnkiDroid: BackendExporting.kt

    @LibAnkiAlias("import_anki_package")
    @RustCleanup("different input parameters - OK?")
    fun importAnkiPackage(
        packagePath: String,
        options: ImportAnkiPackageOptions,
    ): ImportResponse = backend.importAnkiPackage(packagePath, options)

    @LibAnkiAlias("export_anki_package")
    fun exportAnkiPackage(
        outPath: String,
        options: ExportAnkiPackageOptions,
        limit: ExportLimit,
    ): Int =
        backend.exportAnkiPackage(
            outPath = outPath,
            options = options,
            limit = limit,
        )

    @LibAnkiAlias("get_csv_metadata")
    fun getCsvMetadata(
        path: String,
        delimiter: CsvMetadata.Delimiter?,
    ): CsvMetadata {
        val request =
            csvMetadataRequest {
                this.path = path
                delimiter?.let { this.delimiter = delimiter }
            }
        return backend.getCsvMetadata(request)
    }

    @LibAnkiAlias("import_csv")
    @RustCleanup("not quite the same")
    fun importCsv(request: ImportCsvRequest): ImportLogWithChanges =
        backend.importCsv(
            path = request.path,
            metadata = request.metadata,
        )

    @LibAnkiAlias("export_note_csv")
    fun exportNoteCsv(
        outPath: String,
        limit: ExportLimit,
        withHtml: Boolean,
        withTags: Boolean,
        withDeck: Boolean,
        withNotetype: Boolean,
        withGuid: Boolean,
    ): Int =
        backend.exportNoteCsv(
            outPath = outPath,
            withHtml = withHtml,
            withTags = withTags,
            withDeck = withDeck,
            withNotetype = withNotetype,
            withGuid = withGuid,
            limit = limit,
        )

    @LibAnkiAlias("export_card_csv")
    fun exportCardCsv(
        outPath: String,
        limit: ExportLimit,
        withHtml: Boolean,
    ): Int =
        backend.exportCardCsv(
            outPath = outPath,
            withHtml = withHtml,
            limit = limit,
        )

    @LibAnkiAlias("import_json_file")
    fun importJsonFile(path: String): ImportLogWithChanges = backend.importJsonFile(path)

    @LibAnkiAlias("import_json_string")
    fun importJsonString(json: String): ImportLogWithChanges = backend.importJsonString(json)

    @LibAnkiAlias("export_dataset_for_research")
    fun exportDatasetForResearch(
        targetPath: String,
        minEntries: Int = 0,
    ) {
        backend.exportDataset(minEntries = minEntries, targetPath = targetPath)
    }

    /**
     * Object creation helpers **************************************************
     * *********************************************
     */
    fun getCard(id: CardId): Card = Card(this, id)

    fun updateCards(
        cards: Iterable<Card>,
        skipUndoEntry: Boolean = false,
    ): OpChanges = backend.updateCards(cards.map { it.toBackendCard() }, skipUndoEntry)

    fun updateCard(
        card: Card,
        skipUndoEntry: Boolean = false,
    ): OpChanges = updateCards(listOf(card), skipUndoEntry)

    fun getNote(id: NoteId): Note = Note(this, id)

    /**
     * Notes ******************************************************************** ***************************
     */
    fun noteCount(): Int = db.queryScalar("SELECT count() FROM notes")

    /**
     * Return a new note with the model derived from the deck or the configuration
     * @param forDeck When true it uses the model specified in the deck (mid), otherwise it uses the model specified in
     * the configuration (curModel)
     * @return The new note
     */
    fun newNote(forDeck: Boolean = true): Note = newNote(notetypes.current(forDeck))

    /**
     * Return a new note with a specific model
     * @param notetype The model to use for the new note
     * @return The new note
     */
    fun newNote(notetype: NotetypeJson): Note = Note.fromNotetypeId(this, notetype.id)

    /**
     * Cards ******************************************************************** ***************************
     */

    /**
     * Returns whether the collection contains no cards.
     */
    @LibAnkiAlias("is_empty")
    val isEmpty: Boolean
        get() = db.queryScalar("SELECT 1 FROM cards LIMIT 1") == 0

    fun cardCount(): Int = db.queryScalar("SELECT count() FROM cards")

    /*
      Finding cards ************************************************************ ***********************************
     */

    /**
     * Construct a search string from the provided search nodes. For example:
     * ```kotlin
     *       import anki.search.searchNode
     *       import anki.search.SearchNode
     *       import anki.search.SearchNodeKt.group
     *
     *       val node = searchNode {
     *           group = SearchNodeKt.group {
     *               joiner = SearchNode.Group.Joiner.AND
     *               nodes += searchNode { deck = "a **test** deck" }
     *               nodes += searchNode {
     *                   negated = searchNode {
     *                       tag = "foo"
     *                   }
     *               }
     *               nodes += searchNode { flag = SearchNode.Flag.FLAG_GREEN }
     *           }
     *       }
     *       // yields "deck:a \*\*test\*\* deck" -tag:foo flag:3
     *       val text = col.buildSearchString(node)
     *   }
     * ```
     */
    @Suppress("unused")
    fun buildSearchString(node: SearchNode): String = backend.buildSearchString(node)

    /**
     * Return a list of card ids
     * @throws InvalidSearchException
     */
    fun findCards(
        search: String,
        order: SortOrder = SortOrder.NoOrdering(),
    ): List<CardId> {
        val adjustedOrder =
            if (order is SortOrder.UseCollectionOrdering) {
                SortOrder.BuiltinSortKind(
                    config.get("sortType") ?: "noteFld",
                    config.get("sortBackwards") ?: false,
                )
            } else {
                order
            }
        return try {
            backend.searchCards(search, adjustedOrder.toProtoBuf())
        } catch (e: BackendInvalidInputException) {
            throw InvalidSearchException(e)
        }
    }

    fun findNotes(
        query: String,
        order: SortOrder = SortOrder.NoOrdering(),
    ): List<Long> {
        val adjustedOrder =
            if (order is SortOrder.UseCollectionOrdering) {
                SortOrder.BuiltinSortKind(
                    config.get("noteSortType") ?: "noteFld",
                    config.get("browserNoteSortBackwards") ?: false,
                )
            } else {
                order
            }
        val noteIDsList =
            try {
                backend.searchNotes(query, adjustedOrder.toProtoBuf())
            } catch (e: BackendInvalidInputException) {
                throw InvalidSearchException(e)
            }
        return noteIDsList
    }

    /**
     * @return An [OpChangesWithCount] representing the number of affected notes
     */
    @LibAnkiAlias("find_and_replace")
    @CheckResult
    fun findReplace(
        nids: List<Long>,
        search: String,
        replacement: String,
        regex: Boolean = false,
        field: String? = null,
        matchCase: Boolean = false,
    ): OpChangesWithCount = backend.findAndReplace(nids, search, replacement, regex, matchCase, field ?: "")

    @LibAnkiAlias("field_names_for_note_ids")
    fun fieldNamesForNoteIds(nids: List<Long>): List<String> = backend.fieldNamesForNotes(nids)

    // Browser Table

    @LibAnkiAlias("all_browser_columns")
    fun allBrowserColumns(): List<BrowserColumns.Column> = backend.allBrowserColumns()

    @LibAnkiAlias("get_browser_column")
    fun getBrowserColumn(key: String): BrowserColumns.Column? {
        for (column in backend.allBrowserColumns()) {
            if (column.key == key) {
                return column
            }
        }
        return null
    }

    /**
     * Returns a [BrowserRow], cells dependent on [Backend.setActiveBrowserColumns]
     *
     * WARN: As this is a latency-sensitive call, most callers should use [Backend.browserRowForId]
     *
     * @param id Either a [CardId] or a [NoteId], depending on the value of
     * [ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE]
     *
     * @see [setBrowserCardColumns]
     * @see [setBrowserNoteColumns]
     */
    // For performance, this does not match upstream:
    // https://github.com/ankitects/anki/blob/1fb1cbbf85c48a54c05cb4442b1b424a529cac60/pylib/anki/collection.py#L869-L881
    @LibAnkiAlias("browser_row_for_id")
    fun browserRowForId(id: Long): BrowserRow = backend.browserRowForId(id)

    /** Return the stored card column names and ensure the backend columns are set and in sync. */
    @LibAnkiAlias("load_browser_card_columns")
    fun loadBrowserCardColumns(): List<String> {
        val columns = config.get<List<String>>(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, BrowserDefaults.CARD_COLUMNS)!!
        backend.setActiveBrowserColumns(columns)
        return columns
    }

    @LibAnkiAlias("set_browser_card_columns")
    fun setBrowserCardColumns(columns: List<String>) {
        config.set(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, columns)
        backend.setActiveBrowserColumns(columns)
    }

    /** Return the stored note column names and ensure the backend columns are set and in sync. */
    @LibAnkiAlias("load_browser_note_columns")
    fun loadBrowserNoteColumns(): List<String> {
        val columns =
            config.get<List<String>>(
                BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY,
                BrowserDefaults.NOTE_COLUMNS,
            )!!
        backend.setActiveBrowserColumns(columns)
        return columns
    }

    @LibAnkiAlias("set_browser_note_columns")
    fun setBrowserNoteColumns(columns: List<String>) {
        config.set(BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY, columns)
        backend.setActiveBrowserColumns(columns)
    }

    /*
      Stats ******************************************************************** ***************************
     */

    // card stats
    // stats

    /*
     * Timeboxing *************************************************************** ********************************
     */

    fun startTimebox() {
        startTime = TimeManager.time.intTime()
        startReps = sched.numberOfAnswersRecorded
    }

    data class TimeboxReached(
        val secs: Int,
        val reps: Int,
    )

    /* Return (elapsedTime, reps) if timebox reached, or null.
     * Automatically restarts timebox if expired. */
    fun timeboxReached(): TimeboxReached? {
        if (sched.timeboxSecs() == 0) {
            // timeboxing disabled
            return null
        }
        val elapsed = TimeManager.time.intTime() - startTime
        val limit = sched.timeboxSecs()
        return if (elapsed > limit) {
            TimeboxReached(
                limit,
                sched.numberOfAnswersRecorded - startReps,
            ).also {
                startTimebox()
            }
        } else {
            null
        }
    }

    /*
     * Undo ********************************************************************* **************************
     */

    /** eg "Undo suspend card" if undo available */
    fun undoLabel(): String? {
        val action = undoStatus().undo
        return action?.let { tr.undoUndoAction(it) }
    }

    fun undoAvailable(): Boolean {
        val status = undoStatus()
        return status.undo != null
    }

    fun redoLabel(): String? {
        val action = undoStatus().redo
        return action?.let { tr.undoRedoAction(it) }
    }

    fun redoAvailable(): Boolean = undoStatus().redo != null

    fun removeNotes(
        nids: Iterable<NoteId> = listOf(),
        cids: Iterable<CardId> = listOf(),
    ): OpChangesWithCount =
        backend.removeNotes(noteIds = nids, cardIds = cids).also {
            Timber.d("removeNotes: %d changes", it.count)
        }

    /**
     * @return the number of deleted cards. **Note:** if an invalid/duplicate [CardId] is provided,
     * the output count may be less than the input.
     */
    fun removeCardsAndOrphanedNotes(cardIds: Iterable<CardId>) = backend.removeCards(cardIds)

    fun addNote(
        note: Note,
        deckId: DeckId,
    ): OpChanges {
        val resp = backend.addNote(note.toBackendNote(), deckId)
        note.id = resp.noteId
        return resp.changes
    }

    lateinit var notetypes: Notetypes
        protected set

    //endregion

    @NotInLibAnki
    @CheckResult
    fun filterToValidCards(cards: LongArray?): List<Long> = db.queryLongList("select id from cards where id in " + ids2str(cards))

    fun setDeck(
        cids: Iterable<CardId>,
        did: DeckId,
    ): OpChangesWithCount = backend.setDeck(cardIds = cids, deckId = did)

    /** Save (flush) the note to the DB. Unlike note.flush(), this is undoable. This should
     * not be used for adding new notes. */
    @CheckResult
    fun updateNote(note: Note): OpChanges = backend.updateNotes(notes = listOf(note.toBackendNote()), skipUndoEntry = false)

    fun updateNotes(notes: Iterable<Note>): OpChanges = backend.updateNotes(notes = notes.map { it.toBackendNote() }, skipUndoEntry = false)

    /** Fixes and optimizes the database. If any errors are encountered, a list of
     * problems is returned. Throws if DB is unreadable. */
    fun fixIntegrity(): List<String> = backend.checkDatabase()

    /** Change the flag color of the specified cards. flag=0 removes flag. */
    @CheckResult
    fun setUserFlagForCards(
        cids: Iterable<Long>,
        flag: Int,
    ): OpChangesWithCount = backend.setFlag(cardIds = cids, flag = flag)

    fun getEmptyCards(): EmptyCardsReport = backend.getEmptyCards()

    @Suppress("unused")
    fun syncStatus(auth: SyncAuth): SyncStatusResponse = backend.syncStatus(input = auth)

    /** Takes raw input from TypeScript frontend and returns suitable translations. */
    fun i18nResourcesRaw(input: ByteArray): ByteArray = backend.i18nResourcesRaw(input = input)

    // Python code has a cardsOfNote, but not vice-versa yet
    fun notesOfCards(cids: Iterable<CardId>): List<NoteId> = db.queryLongList("select distinct nid from cards where id in ${ids2str(cids)}")

    fun cardIdsOfNote(nid: NoteId): List<CardId> = backend.cardsOfNote(nid = nid)

    /**
     * returns the list of cloze ordinals in a note
     *
     * `"{{c1::A}} {{c3::B}}" => [1, 3]`
     */
    fun clozeNumbersInNote(n: Note): List<Int> {
        // the call appears to be non-deterministic. Sort ascending
        return backend
            .clozeNumbersInNote(n.toBackendNote())
            .sorted()
    }

    fun getImageForOcclusionRaw(input: ByteArray): ByteArray = backend.getImageForOcclusionRaw(input = input)

    fun getImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.getImageOcclusionNoteRaw(input = input)

    fun getImageOcclusionFieldsRaw(input: ByteArray): ByteArray = backend.getImageOcclusionFieldsRaw(input = input)

    fun addImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.addImageOcclusionNoteRaw(input = input)

    fun updateImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.updateImageOcclusionNoteRaw(input = input)

    fun congratsInfoRaw(input: ByteArray): ByteArray = backend.congratsInfoRaw(input = input)

    fun setWantsAbortRaw(input: ByteArray): ByteArray = backend.setWantsAbortRaw(input = input)

    fun latestProgressRaw(input: ByteArray): ByteArray = backend.latestProgressRaw(input = input)

    fun getSchedulingStatesWithContextRaw(input: ByteArray): ByteArray = backend.getSchedulingStatesWithContextRaw(input = input)

    fun setSchedulingStatesRaw(input: ByteArray): ByteArray = backend.setSchedulingStatesRaw(input = input)

    fun getChangeNotetypeInfoRaw(input: ByteArray): ByteArray = backend.getChangeNotetypeInfoRaw(input = input)

    fun changeNotetypeRaw(input: ByteArray): ByteArray = backend.changeNotetypeRaw(input = input)

    fun importJsonStringRaw(input: ByteArray): ByteArray = backend.importJsonStringRaw(input = input)

    fun importJsonFileRaw(input: ByteArray): ByteArray = backend.importJsonFileRaw(input = input)

    fun getIgnoredBeforeCountRaw(input: ByteArray): ByteArray = backend.getIgnoredBeforeCountRaw(input = input)

    fun getRetentionWorkloadRaw(input: ByteArray): ByteArray = backend.getRetentionWorkloadRaw(input = input)

    fun evaluateParamsLegacyRaw(input: ByteArray): ByteArray = backend.evaluateParamsLegacyRaw(input = input)

    fun compareAnswer(
        expected: String,
        provided: String,
        combining: Boolean = true,
    ): String = backend.compareAnswer(expected = expected, provided = provided, combining = combining)

    fun extractClozeForTyping(
        text: String,
        ordinal: Int,
    ): String = backend.extractClozeForTyping(text = text, ordinal = ordinal)

    fun defaultsForAdding(currentReviewCard: Card? = null): anki.notes.DeckAndNotetype {
        val homeDeck = currentReviewCard?.currentDeckId() ?: 0L
        return backend.defaultsForAdding(homeDeckOfCurrentReviewCard = homeDeck)
    }

    fun getPreferences(): Preferences = backend.getPreferences()

    fun setPreferences(preferences: Preferences): OpChanges = backend.setPreferences(preferences)
}

@NotInLibAnki
fun EmptyCardsReport.emptyCids(): List<CardId> = notesList.flatMap { it.cardIdsList }
