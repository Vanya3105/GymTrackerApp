package com.example.gymtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** A backup file in the chosen folder; [id] is whatever the folder needs to delete it. */
data class BackupFile(val id: String, val name: String)

/** Where automatic backups go: a folder picked with the system file picker, or a fake in tests. */
interface BackupFolder {
    fun list(): List<BackupFile>
    fun write(name: String, bytes: ByteArray)
    fun delete(file: BackupFile)
}

/**
 * Copies the same file "Save backup" makes into a folder the owner chose, after a workout is
 * saved and when the app goes to the background, so a lost phone doesn't mean lost logs. Skips
 * the copy when nothing changed since the last one, and keeps one file per day for the last 7
 * days plus one per week for 4 weeks before that.
 *
 * The folder lives in its own preferences, not the JSON store: the store is what gets backed up,
 * and a folder permission restored onto another phone would be worthless there.
 */
object AutoBackup {
    private const val TAG = "AutoBackup"
    private const val PREFS = "gym_tracker_auto_backup"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_FOLDER_NAME = "folder_name"
    internal const val KEY_LAST_SUCCESS = "last_success_millis"
    internal const val KEY_LAST_HASH = "last_hash"
    internal const val KEY_ERROR = "error"
    internal const val FILE_PREFIX = "gymtracker-backup-"
    private const val KEEP_DAYS = 7
    private const val KEEP_WEEKS = 4

    data class Status(val folderName: String?, val lastSuccessMillis: Long, val error: String?, val running: Boolean = false) {
        val isOn: Boolean get() = folderName != null
    }

    /** For the Profile screen; updated on the main thread after every run. */
    var status by mutableStateOf(Status(null, 0L, null))
        private set

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "auto-backup").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun refreshStatus(context: Context) {
        val p = prefs(context)
        status = Status(
            folderName = p.getString(KEY_TREE, null)?.let { p.getString(KEY_FOLDER_NAME, null) ?: "Chosen folder" },
            lastSuccessMillis = p.getLong(KEY_LAST_SUCCESS, 0L),
            error = p.getString(KEY_ERROR, null)
        )
    }

    /** Remembers the folder (and keeps access to it across restarts), then backs up straight away. */
    fun setFolder(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        prefs(context).getString(KEY_TREE, null)?.let { old ->
            if (old != tree.toString()) runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        resolver.takePersistableUriPermission(tree, flags)
        prefs(context).edit()
            .putString(KEY_TREE, tree.toString())
            .putString(KEY_FOLDER_NAME, SafFolder(context, tree).displayName())
            .remove(KEY_LAST_HASH)
            .remove(KEY_ERROR)
            .apply()
        refreshStatus(context)
        backUpNow(context)
    }

    /** Stops backing up. Files already in the folder stay there. */
    fun turnOff(context: Context) {
        prefs(context).getString(KEY_TREE, null)?.let { tree ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs(context).edit().clear().apply()
        refreshStatus(context)
    }

    /**
     * A backup if one is due: on, and no workout running (the copy after it's saved covers it,
     * and switching to a music app between sets shouldn't write a file each time).
     */
    fun request(context: Context) {
        val app = context.applicationContext
        DriveBackup.request(app)
        if (prefs(app).getString(KEY_TREE, null) == null) return
        if (LocalStorage.loadActiveSession(app) != null) return
        executor.execute { runWithFolder(app, force = false) }
    }

    /** "Back up now": writes a copy even if nothing changed. */
    fun backUpNow(context: Context) {
        val app = context.applicationContext
        if (prefs(app).getString(KEY_TREE, null) == null) return
        status = status.copy(running = true)
        executor.execute { runWithFolder(app, force = true) }
    }

    private fun runWithFolder(context: Context, force: Boolean) {
        val tree = prefs(context).getString(KEY_TREE, null)?.let(Uri::parse) ?: return
        run(context, SafFolder(context, tree), force, System.currentTimeMillis())
        main.post { refreshStatus(context) }
    }

    /**
     * One backup into [folder]. Returns true if a file was written. [prefsName] keeps this
     * destination's last backup, change check and error apart from others (the Drive backup).
     */
    internal fun run(
        context: Context,
        folder: BackupFolder,
        force: Boolean,
        nowMillis: Long,
        prefsName: String = PREFS,
        errorFor: (Exception) -> String = ::folderError
    ): Boolean {
        val p = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return try {
            val json = LocalStorage.exportJsonBackupString(context)
            val hash = sha256(json)
            if (!force && hash == p.getString(KEY_LAST_HASH, null)) return false
            folder.write(fileName(nowMillis), json.toByteArray())
            // Rotation failing (a file someone else locked) shouldn't make the backup count as failed.
            runCatching {
                val files = folder.list()
                val doomed = backupsToDelete(files.map { it.name }).toSet()
                files.filter { it.name in doomed }.forEach(folder::delete)
            }.onFailure { Log.w(TAG, "Couldn't remove old backups", it) }
            p.edit().putLong(KEY_LAST_SUCCESS, nowMillis).putString(KEY_LAST_HASH, hash).remove(KEY_ERROR).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            p.edit().putString(KEY_ERROR, errorFor(e)).apply()
            false
        }
    }

    private fun folderError(e: Exception): String =
        if (e is SecurityException || e is IllegalArgumentException) "Can't reach the folder any more. Choose it again."
        else "The last backup didn't work. Check the folder still exists."

    internal fun fileName(nowMillis: Long): String =
        FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(nowMillis)) + ".json"

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Which of [names] to delete: keeps the newest backup of each of the last [KEEP_DAYS] days
     * that have one, then the newest of each of the [KEEP_WEEKS] weeks before those. Files that
     * aren't ours (other names) are never touched.
     */
    internal fun backupsToDelete(names: List<String>): List<String> {
        val pattern = Regex("^${Regex.escape(FILE_PREFIX)}(\\d{4}-\\d{2}-\\d{2})-(\\d{4})")
        val ours = names.mapNotNull { name ->
            pattern.find(name)?.let { m ->
                runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull()?.let { Triple(name, it, m.groupValues[2]) }
            }
        }.sortedWith(compareByDescending<Triple<String, LocalDate, String>> { it.second }.thenByDescending { it.third }.thenByDescending { it.first })

        val newestPerDay = ours.distinctBy { it.second }
        val keptDays = newestPerDay.take(KEEP_DAYS)
        val keptWeeks = newestPerDay.drop(KEEP_DAYS)
            .distinctBy { it.second.with(DayOfWeek.MONDAY) }
            .take(KEEP_WEEKS)
        val keep = (keptDays + keptWeeks).mapTo(HashSet()) { it.first }
        return ours.map { it.first }.filter { it !in keep }
    }
}

/** A folder picked with ACTION_OPEN_DOCUMENT_TREE (Google Drive, OneDrive, a local folder, ...). */
private class SafFolder(context: Context, private val tree: Uri) : BackupFolder {
    private val resolver = context.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(tree)
    private val dir = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)

    fun displayName(): String? = runCatching {
        resolver.query(dir, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    override fun list(): List<BackupFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(children, cols, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(BackupFile(c.getString(0), c.getString(1) ?: "")) }
        } ?: throw IOException("Couldn't list the backup folder")
    }

    override fun write(name: String, bytes: ByteArray) {
        val doc = DocumentsContract.createDocument(resolver, dir, "application/json", name)
            ?: throw IOException("Couldn't create $name")
        try {
            resolver.openOutputStream(doc, "w")?.use { it.write(bytes) } ?: throw IOException("Couldn't open $name")
        } catch (e: Exception) {
            // Don't leave an empty or half-written file that looks like a backup.
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
    }

    override fun delete(file: BackupFile) {
        DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, file.id))
    }
}
