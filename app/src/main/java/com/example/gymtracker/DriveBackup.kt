package com.example.gymtracker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * "Sign in with Google": backups go to a hidden app folder in the user's own Google Drive (the
 * drive.appdata scope; nobody else, the user included, sees or edits the files there), so a new
 * phone gets everything back after signing in. No server of ours is involved: the app talks to
 * Drive directly with the user's token. Backups are the same file and rotation as [AutoBackup].
 *
 * Needs an Android OAuth client in a Google Cloud project for this package name and signing key
 * (see ROADMAP "Google sign-in setup"); without it, signing in fails with a clear message.
 */
object DriveBackup {
    private const val TAG = "DriveBackup"
    internal const val PREFS = "gym_tracker_drive_backup"
    private const val KEY_EMAIL = "email"
    private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    private const val SCOPE_EMAIL = "https://www.googleapis.com/auth/userinfo.email"

    data class Status(val email: String?, val lastSuccessMillis: Long, val error: String?, val running: Boolean = false) {
        val signedIn: Boolean get() = email != null
    }

    var status by mutableStateOf(Status(null, 0L, null))
        private set

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "drive-backup").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun refreshStatus(context: Context) {
        val p = prefs(context)
        status = Status(p.getString(KEY_EMAIL, null), p.getLong(AutoBackup.KEY_LAST_SUCCESS, 0L), p.getString(AutoBackup.KEY_ERROR, null))
    }

    private val request: AuthorizationRequest
        get() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_APPDATA), Scope(SCOPE_EMAIL))).build()

    // --- Signing in (from the UI) ---

    sealed interface SignInStep {
        /** Google needs the user to pick an account or agree: launch this, then call [finishSignIn]. */
        data class NeedsUser(val intent: PendingIntent) : SignInStep
        data class Done(val email: String) : SignInStep
        data class Failed(val message: String) : SignInStep
    }

    /** Starts signing in; runs [onStep] on the main thread. */
    fun startSignIn(context: Context, onStep: (SignInStep) -> Unit) {
        val app = context.applicationContext
        Identity.getAuthorizationClient(app).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) onStep(SignInStep.NeedsUser(pending))
                else completeSignIn(app, result, onStep)
            }
            .addOnFailureListener { e -> onStep(SignInStep.Failed(signInError(e))) }
    }

    /** After the account picker or consent screen came back with [data]. */
    fun finishSignIn(context: Context, data: Intent?, onStep: (SignInStep) -> Unit) {
        val app = context.applicationContext
        try {
            completeSignIn(app, Identity.getAuthorizationClient(app).getAuthorizationResultFromIntent(data), onStep)
        } catch (e: Exception) {
            onStep(SignInStep.Failed(signInError(e)))
        }
    }

    private fun completeSignIn(context: Context, result: AuthorizationResult, onStep: (SignInStep) -> Unit) {
        val token = result.accessToken ?: return onStep(SignInStep.Failed("Google didn't allow access to Drive."))
        // The account comes with the sign-in; asking Google for it separately could fail on its own.
        val signedInAs = runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()
        executor.execute {
            val step = try {
                // What matters is that Drive answers: check now rather than at the first backup.
                DriveFolder(token).list()
                val email = signedInAs ?: runCatching { fetchEmail(token) }.getOrNull() ?: "Google account"
                prefs(context).edit().putString(KEY_EMAIL, email).remove(AutoBackup.KEY_LAST_HASH).remove(AutoBackup.KEY_ERROR).apply()
                SignInStep.Done(email)
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in finished but Drive didn't answer", e)
                SignInStep.Failed(driveError(e))
            }
            main.post { refreshStatus(context); onStep(step) }
        }
    }

    private fun signInError(e: Exception): String {
        Log.e(TAG, "Sign-in failed", e)
        val text = e.message.orEmpty()
        return when {
            // Status 16: the user backed out; nothing to say.
            "16:" in text -> ""
            // Status 10: no OAuth client for this app's package and signing key.
            "10:" in text || "DEVELOPER_ERROR" in text -> "Google sign-in isn't set up for this version of the app yet."
            "7:" in text || "NETWORK" in text.uppercase(Locale.US) -> "No connection. Try again when you're online."
            else -> "Couldn't sign in with Google."
        }
    }

    /** Why Drive refused, in words that point at the fix. */
    private fun driveError(e: Exception): String {
        val detail = (e as? DriveException)?.message.orEmpty()
        return when {
            e is DriveException && e.code == 403 && ("accessNotConfigured" in detail || "has not been used" in detail || "disabled" in detail) ->
                "Signed in, but the Google Drive API isn't turned on in the Google Cloud project."
            e is DriveException && e.code == 403 -> "Signed in, but Google didn't give access to Drive (403). Check the drive.appdata scope."
            e is DriveException && e.code == 401 -> "Signed in, but Google turned down the access (401). Try signing in again."
            e is DriveException -> "Signed in, but Google Drive answered with an error (${e.code})."
            else -> "Signed in, but Google Drive didn't answer. Check the connection and try again."
        }
    }

    /** Stops backing up to Drive on this phone. The backups in Drive stay. */
    fun signOut(context: Context) {
        prefs(context).edit().clear().apply()
        refreshStatus(context)
    }

    // --- Backing up (also in the background) ---

    /** A backup if signed in and one is due; same rules as [AutoBackup.request]. */
    fun request(context: Context) {
        val app = context.applicationContext
        if (prefs(app).getString(KEY_EMAIL, null) == null) return
        if (LocalStorage.loadActiveSession(app) != null) return
        executor.execute { runOnce(app, force = false) }
    }

    fun backUpNow(context: Context) {
        val app = context.applicationContext
        if (prefs(app).getString(KEY_EMAIL, null) == null) return
        status = status.copy(running = true)
        executor.execute { runOnce(app, force = true) }
    }

    private fun runOnce(context: Context, force: Boolean) {
        // A phone with nothing logged (a new phone before its restore) must not write a backup:
        // it would be the newest of the day and the rotation could drop that day's real one.
        if (LocalStorage.loadHistory(context).isEmpty() && LocalStorage.loadWeightHistory(context).isEmpty()) {
            main.post { refreshStatus(context) }
            return
        }
        val token = silentToken(context)
        if (token == null) {
            prefs(context).edit().putString(AutoBackup.KEY_ERROR, "Google needs you to sign in again.").apply()
        } else {
            AutoBackup.run(context, DriveFolder(token), force, System.currentTimeMillis(), PREFS) { e ->
                if (e is DriveException && e.code == 401) "Google needs you to sign in again."
                else "The last backup to Google Drive didn't work. It'll try again next time."
            }
        }
        main.post { refreshStatus(context) }
    }

    /** A token without showing anything; null if Google needs the user (signed out elsewhere, access removed). */
    private fun silentToken(context: Context): String? = try {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request), 30, TimeUnit.SECONDS)
        if (result.hasResolution()) null else result.accessToken
    } catch (e: Exception) {
        Log.w(TAG, "No token", e)
        null
    }

    // --- Restoring ---

    data class DriveBackupFile(val file: BackupFile, val modifiedMillis: Long, val sizeBytes: Long)

    /** The backups in Drive, newest first; null if Drive couldn't be reached. Call off the main thread. */
    fun listBackups(context: Context): List<DriveBackupFile>? {
        val token = silentToken(context) ?: return null
        return runCatching { DriveFolder(token).listDetailed() }.onFailure { Log.e(TAG, "List failed", it) }.getOrNull()
    }

    /** One backup's contents; null on failure. Call off the main thread. */
    fun download(context: Context, file: BackupFile): String? {
        val token = silentToken(context) ?: return null
        return runCatching { DriveFolder(token).read(file) }.onFailure { Log.e(TAG, "Download failed", it) }.getOrNull()
    }
}

internal class DriveException(val code: Int, message: String) : IOException(message)

/** Drive's appDataFolder through its REST API, with a short-lived access token. */
internal class DriveFolder(private val token: String) : BackupFolder {
    private val api = "https://www.googleapis.com/drive/v3/files"

    override fun list(): List<BackupFile> = listDetailed().map { it.file }

    fun listDetailed(): List<DriveBackup.DriveBackupFile> {
        val q = URLEncoder.encode("name contains '${AutoBackup.FILE_PREFIX}' and trashed = false", "UTF-8")
        val json = JSONObject(
            call("GET", "$api?spaces=appDataFolder&pageSize=200&orderBy=modifiedTime%20desc&q=$q&fields=files(id,name,modifiedTime,size)")
        )
        val files = json.optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { i ->
            val f = files.getJSONObject(i)
            DriveBackup.DriveBackupFile(
                BackupFile(f.getString("id"), f.optString("name")),
                parseRfc3339(f.optString("modifiedTime")),
                f.optString("size").toLongOrNull() ?: 0L
            )
        }
    }

    override fun write(name: String, bytes: ByteArray) {
        val boundary = "gymtracker${System.nanoTime()}"
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n")
            append("--$boundary\r\nContent-Type: application/json\r\n\r\n")
        }.toByteArray() + bytes + "\r\n--$boundary--\r\n".toByteArray()
        call("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", body, "multipart/related; boundary=$boundary")
    }

    override fun delete(file: BackupFile) {
        call("DELETE", "$api/${file.id}")
    }

    fun read(file: BackupFile): String = call("GET", "$api/${file.id}?alt=media")

    private fun call(method: String, url: String, body: ByteArray? = null, contentType: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw DriveException(code, "Drive $method failed ($code): ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }
}

/** The signed-in account's address, to show which Google account the backups go to. */
private fun fetchEmail(token: String): String {
    val conn = URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
    try {
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        if (conn.responseCode != 200) throw DriveException(conn.responseCode, "userinfo failed")
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optString("email").ifBlank { "Google account" }
    } finally {
        conn.disconnect()
    }
}

/** "2026-10-30T09:15:02.123Z" → millis; 0 if it can't be read. */
internal fun parseRfc3339(text: String): Long {
    val clean = text.replace(Regex("""\.\d+"""), "")
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(clean)!!.time
    }.getOrDefault(0L)
}
