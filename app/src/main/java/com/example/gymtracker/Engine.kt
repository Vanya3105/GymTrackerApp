package com.example.gymtracker

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

const val ACTION_SESSION_SYNC = "com.example.gymtracker.ACTION_SESSION_SYNC"

@Immutable
data class ExerciseRecords(
    val maxWeightKg: Float,
    val bestEstimated1Rm: Int,
    val bestSetText: String,
    val recentHistory: List<Pair<String, String>>,
    val chartPoints: List<Pair<String, Float>>
)

const val WIDGET_KIND_WORKOUT = "WORKOUT"
const val WIDGET_KIND_RECOVERY = "RECOVERY"
const val WIDGET_KIND_WEEKLY = "WEEKLY"

const val WIDGET_STYLE_STANDARD = "STANDARD"
const val WIDGET_STYLE_TINTED = "TINTED"

/** Per-widget-instance look & content. [options] holds the on/off toggles listed in [widgetOptionLabels]. */
@Immutable
data class WidgetSettings(
    val theme: String = THEME_SYSTEM,
    val opacityPercent: Int = 100,
    val style: String = WIDGET_STYLE_STANDARD,
    val options: Set<String>
)

/** Toggle key → label shown in the widget settings screen, per widget kind. All on by default. */
fun widgetOptionLabels(kind: String): List<Pair<String, String>> = when (kind) {
    WIDGET_KIND_WORKOUT -> listOf(
        "controls" to "Weight and rep buttons",
        "weekly" to "Weekly progress when not training"
    )
    WIDGET_KIND_RECOVERY -> listOf(
        "sets" to "Sets this week",
        "hours" to "Time since last trained"
    )
    else -> listOf(
        "volume" to "Weekly volume",
        "next" to "Next workout"
    )
}

fun defaultWidgetSettings(kind: String) = WidgetSettings(options = widgetOptionLabels(kind).map { it.first }.toSet())

val SPLIT_PROGRAMS = mapOf(
    "PPL_ARNOLD" to SplitProgramInfo(
        id = "PPL_ARNOLD",
        name = "Push, Pull + Arnold split",
        tagline = "Push, pull, arms & shoulders, chest & back",
        weeklyGoal = 4,
        splits = listOf(
            WorkoutSplit("Push", "Chest, shoulders, triceps", "01"),
            WorkoutSplit("Pull", "Lats, upper back, biceps", "02"),
            WorkoutSplit("Arms & Shoulders", "Delts, biceps, triceps", "03"),
            WorkoutSplit("Chest & Back", "Chest and back together", "04")
        )
    ),
    "CLASSIC_PPL" to SplitProgramInfo(
        id = "CLASSIC_PPL",
        name = "Push / Pull / Legs",
        tagline = "The classic three-day split",
        weeklyGoal = 3,
        splits = listOf(
            WorkoutSplit("Push", "Chest, shoulders, triceps", "01"),
            WorkoutSplit("Pull", "Lats, mid back, biceps", "02"),
            WorkoutSplit("Legs", "Quads, hamstrings, calves", "03")
        )
    ),
    "UPPER_LOWER" to SplitProgramInfo(
        id = "UPPER_LOWER",
        name = "Upper / Lower",
        tagline = "Four days, each muscle twice a week",
        weeklyGoal = 4,
        splits = listOf(
            WorkoutSplit("Upper A", "Chest and back, plus arms", "01"),
            WorkoutSplit("Lower A", "Quads, hamstrings, calves", "02"),
            WorkoutSplit("Upper B", "Shoulders and arms", "03"),
            WorkoutSplit("Lower B", "Glutes, hamstrings, quads", "04")
        )
    ),
    "FULL_BODY" to SplitProgramInfo(
        id = "FULL_BODY",
        name = "Full body",
        tagline = "Three days, whole body each time",
        weeklyGoal = 3,
        splits = listOf(
            WorkoutSplit("Full Body A", "Incline press, pulldown, squat", "01"),
            WorkoutSplit("Full Body B", "Shoulder press, row, RDL", "02"),
            WorkoutSplit("Full Body C", "Dips, chin-ups, leg press", "03")
        )
    )
)

fun programFor(programId: String): SplitProgramInfo =
    SPLIT_PROGRAMS[programId] ?: SPLIT_PROGRAMS.getValue("PPL_ARNOLD")

val REST_PRESETS = listOf(0, 60, 90, 120, 180, 240)
val WEIGHT_STEP_PRESETS = listOf(0.5f, 1.0f, 1.25f, 2.0f, 2.5f, 5.0f)

fun formatRestShort(sec: Int): String {
    if (sec <= 0) return "Off"
    return if (sec % 60 == 0) "${sec / 60}m" else "${sec}s"
}

fun formatClock(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatDuration(totalSec: Long): String {
    val mins = (totalSec / 60).toInt()
    return when {
        mins >= 60 -> "${mins / 60}h ${mins % 60}m"
        mins > 0 -> "$mins min"
        else -> "< 1 min"
    }
}

fun formatVolume(kg: Int): String =
    if (kg >= 10_000) String.format(Locale.US, "%.1fk kg", kg / 1000f) else String.format(Locale.US, "%,d kg", kg)

fun cycleRestPreset(current: Int): Int {
    val idx = REST_PRESETS.indexOf(current)
    return if (idx == -1 || idx == REST_PRESETS.lastIndex) REST_PRESETS.first() else REST_PRESETS[idx + 1]
}

fun formatKgClean(value: Float): String {
    val clamped = (value.coerceAtLeast(0f) * 100f).roundToInt() / 100f
    return if (clamped % 1f == 0f) "${clamped.toInt()}" else "$clamped"
}

/**
 * Filters a typed weight: "62,5" (comma keyboards) → "62.5" and stray characters are dropped.
 * Returns null when the edit would add a second decimal point, so the field keeps its previous value.
 */
fun sanitizeDecimalInput(raw: String): String? {
    val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    return cleaned.takeIf { it.count { c -> c == '.' } <= 1 }
}

fun parseWeightClean(raw: String, fallback: Float = 20f): Float {
    val cleaned = raw.replace("kg", "", ignoreCase = true).trim()
    return cleaned.toFloatOrNull() ?: fallback
}

fun parseRepsClean(raw: String, fallback: Int = 8): Int {
    val cleaned = raw.replace("r", "", ignoreCase = true).trim()
    return cleaned.toIntOrNull() ?: fallback
}

object DateUtils {
    private val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFmt = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US)
    private val shortDayFmt = SimpleDateFormat("MMM d", Locale.US)
    private val shortDayYearFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val monthTitleFmt = SimpleDateFormat("MMMM yyyy", Locale.US)

    @Synchronized fun formatDayKey(date: Date): String = dayKeyFmt.format(date)
    @Synchronized fun formatDisplayDate(date: Date): String = displayDateFmt.format(date)
    @Synchronized fun formatShortDay(date: Date): String = shortDayFmt.format(date)
    @Synchronized fun formatShortDayYear(date: Date): String = shortDayYearFmt.format(date)
    @Synchronized fun formatMonthTitle(date: Date): String = monthTitleFmt.format(date)
}

fun applyDisplayRefreshRate(activity: Activity, targetHz: Int) {
    try {
        val window = activity.window
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        } ?: return

        val currentMode = display.mode
        val sameResModes = display.supportedModes.filter {
            it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight
        }.ifEmpty { display.supportedModes.toList() }

        val bestMode = sameResModes.minByOrNull { abs(it.refreshRate - targetHz.toFloat()) } ?: return
        val params = window.attributes
        if (params.preferredDisplayModeId != bestMode.modeId) {
            params.preferredDisplayModeId = bestMode.modeId
            window.attributes = params
        }
    } catch (_: Exception) {}
}

// ============================================================================
// NOTIFICATIONS
// ============================================================================

const val REST_NOTIFICATION_CHANNEL_ID = "gym_rest_timer_channel"
const val SET_DONE_NOTIFICATION_CHANNEL_ID = "gym_set_done_channel"

fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

fun ensureNotificationChannelCreated(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    try {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(REST_NOTIFICATION_CHANNEL_ID, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lets you know when your rest between sets is over"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(SET_DONE_NOTIFICATION_CHANNEL_ID, "Logged sets", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Confirms each set you finish"
                enableVibration(true)
            }
        )
    } catch (_: Exception) {}
}

fun notificationBuilder(context: Context, channelId: String): Notification.Builder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, channelId)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(context)
    }

fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun postAlert(context: Context, id: Int, channelId: String, icon: Int, title: String, text: String, requestCode: Int) {
    if (!hasNotificationPermission(context)) return
    try {
        ensureNotificationChannelCreated(context)
        val notification = notificationBuilder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent(context, requestCode))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    } catch (_: Exception) {}
}

fun sendSetDoneNotification(context: Context, exerciseName: String, weight: String, reps: String, restSeconds: Int) {
    val restMsg = if (restSeconds > 0) " · Rest ${formatRestShort(restSeconds)}" else ""
    postAlert(
        context, id = 1002, channelId = SET_DONE_NOTIFICATION_CHANNEL_ID, icon = R.drawable.ic_stat_workout,
        title = "Set logged · $exerciseName", text = "$weight kg × $reps$restMsg", requestCode = 1
    )
}

fun sendRestFinishedNotification(
    context: Context,
    splitTitle: String,
    detailText: String = "Time for your next set."
) {
    postAlert(
        context, id = 1001, channelId = REST_NOTIFICATION_CHANNEL_ID, icon = R.drawable.ic_stat_timer,
        title = "Rest's over · $splitTitle", text = detailText, requestCode = 0
    )
}

// ============================================================================
// PHOTO STORAGE
// ============================================================================

object PhotoStorage {
    private const val MAX_PHOTO_DIM = 1400

    // Bounded by bytes (1/8 of the heap) so browsing many workout photos can't exhaust memory.
    private val memoryCache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
    }

    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? {
        return try {
            val folder = File(context.filesDir, "workout_photos").apply { mkdirs() }
            val file = File(folder, "photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            memoryCache.put(file.absolutePath, bitmap.asImageBitmap())
            file.absolutePath
        } catch (e: Exception) { null }
    }

    fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            // Read dimensions first and subsample, so a 50MP gallery photo is never decoded at full size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= MAX_PHOTO_DIM && bounds.outHeight / (sampleSize * 2) >= MAX_PHOTO_DIM) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val scaled = if (original.width > MAX_PHOTO_DIM || original.height > MAX_PHOTO_DIM) {
                val ratio = minOf(MAX_PHOTO_DIM.toFloat() / original.width, MAX_PHOTO_DIM.toFloat() / original.height)
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * ratio).roundToInt().coerceAtLeast(1),
                    (original.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
            } else original
            saveBitmapToInternalStorage(context, scaled)
        } catch (e: Exception) { null }
    }

    fun loadImageBitmap(path: String?): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        memoryCache.get(path)?.let { return it }
        return try {
            val file = File(path)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()?.also { memoryCache.put(path, it) }
        } catch (e: Exception) { null }
    }
}

/** Starting exercises (name to muscle) for each split, all from [ExerciseDatabase]. */
fun defaultTemplateForSplit(splitTitle: String): List<Pair<String, String>> {
    val names = when (splitTitle) {
        "Push" -> listOf("Incline Barbell Bench Press", "Pec Deck", "Lateral Raise", "Tricep Pushdown")
        "Pull" -> listOf("Lat Pulldown", "T-Bar Row", "Face Pull", "Preacher Curl")
        "Legs", "Lower A" -> listOf("Hack Squat", "Romanian Deadlift", "Leg Extension", "Seated Leg Curl", "Standing Calf Raise")
        "Lower B" -> listOf("Leg Press", "Lying Leg Curl", "Bulgarian Split Squat", "Seated Calf Raise")
        "Arms & Shoulders" -> listOf(
            "Seated Dumbbell Shoulder Press", "Cable Lateral Raise", "Incline Dumbbell Curl",
            "Overhead Cable Tricep Extension", "Hammer Curl"
        )
        "Chest & Back", "Upper A" -> listOf("Incline Dumbbell Press", "Weighted Pull-Up", "Machine Chest Press", "Chest-Supported Row")
        "Upper B" -> listOf("Seated Dumbbell Shoulder Press", "Seated Cable Row", "Cable Lateral Raise", "Preacher Curl", "Rope Pushdown")
        "Full Body A" -> listOf("Incline Dumbbell Press", "Lat Pulldown", "Leg Press", "Lateral Raise")
        "Full Body B" -> listOf("Seated Dumbbell Shoulder Press", "Chest-Supported Row", "Romanian Deadlift", "Incline Dumbbell Curl")
        "Full Body C" -> listOf("Dips", "Weighted Pull-Up", "Leg Extension", "Overhead Cable Tricep Extension")
        else -> emptyList()
    }
    return names.map { it to (ExerciseDatabase.find(it)?.muscle ?: "") }
}

// ============================================================================
// LOCAL JSON STORAGE
// ============================================================================

/**
 * Single-file JSON store. The parsed document lives in memory; every change is serialized
 * immediately but written to disk on a background thread (atomic temp-file swap, newest
 * snapshot wins). Widgets and the lock-screen notification are redrawn once per burst of
 * edits instead of once per keystroke. BroadcastReceivers must call [flush] before returning.
 */
object LocalStorage {
    private const val FILE_NAME = "gym_tracker_store_v2.json"
    private const val SURFACE_REFRESH_DEBOUNCE_MS = 250L

    private val lock = Any()
    private var cachedRoot: JSONObject? = null
    private var cachedHistory: List<CompletedWorkout>? = null

    private val writeExecutor = Executors.newSingleThreadExecutor()
    private val pendingSnapshot = AtomicReference<String?>(null)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshContext: Context? = null
    private val refreshRunnable = Runnable { refreshContext?.let(::refreshSurfacesNow) }

    private fun storeFile(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    private fun root(context: Context): JSONObject = synchronized(lock) {
        cachedRoot ?: run {
            val file = storeFile(context)
            val parsed = try {
                if (file.exists()) JSONObject(file.readText()) else JSONObject()
            } catch (e: Exception) {
                // Keep the unreadable file aside: the next save would otherwise overwrite all history.
                try {
                    file.copyTo(File(file.parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}"))
                } catch (_: Exception) {}
                JSONObject()
            }
            cachedRoot = parsed
            parsed
        }
    }

    private inline fun edit(context: Context, notifyInApp: Boolean = false, block: (JSONObject) -> Unit) {
        synchronized(lock) {
            val root = root(context)
            block(root)
            persist(context, root)
        }
        scheduleSurfaceRefresh(context, notifyInApp)
    }

    private fun persist(context: Context, root: JSONObject) {
        val file = storeFile(context)
        pendingSnapshot.set(root.toString())
        writeExecutor.execute {
            val snapshot = pendingSnapshot.getAndSet(null) ?: return@execute
            try {
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(snapshot)
                if (!tmp.renameTo(file)) {
                    file.writeText(snapshot)
                    tmp.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun scheduleSurfaceRefresh(context: Context, notifyInApp: Boolean) {
        val appCtx = context.applicationContext
        refreshContext = appCtx
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, SURFACE_REFRESH_DEBOUNCE_MS)

        if (notifyInApp) {
            try {
                appCtx.sendBroadcast(Intent(ACTION_SESSION_SYNC).setPackage(appCtx.packageName))
            } catch (_: Exception) {}
        }
    }

    private fun refreshSurfacesNow(appCtx: Context) {
        GymWidgetProvider.refreshAllWidgets(appCtx)
        MuscleFatigueWidgetProvider.refreshAll(appCtx)
        SquarePulseWidgetProvider.refreshAll(appCtx)
        LockScreenWorkoutController.sync(appCtx)
    }

    /** Redraws widgets now and blocks until pending writes reach disk. */
    fun flush(context: Context) {
        mainHandler.removeCallbacks(refreshRunnable)
        refreshSurfacesNow(context.applicationContext)
        try {
            writeExecutor.submit {}.get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }

    // --- Profile & body weight ---

    fun loadUserProfile(context: Context): UserProfile {
        val obj = root(context).optJSONObject("userProfile") ?: return UserProfile()
        return UserProfile(
            hasCompletedOnboarding = obj.optBoolean("hasCompletedOnboarding", false),
            age = obj.optInt("age", 0),
            currentWeightKg = obj.optDouble("currentWeightKg", 0.0).toFloat(),
            splitProgram = obj.optString("splitProgram", "PPL_ARNOLD")
        )
    }

    fun saveUserProfile(context: Context, profile: UserProfile) = edit(context) { root ->
        root.put("userProfile", JSONObject().apply {
            put("hasCompletedOnboarding", profile.hasCompletedOnboarding)
            put("age", profile.age)
            put("currentWeightKg", profile.currentWeightKg.toDouble())
            put("splitProgram", profile.splitProgram)
        })
    }

    fun loadWeightHistory(context: Context): List<WeightLogEntry> {
        val arr = root(context).optJSONArray("weightHistory") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WeightLogEntry(
                id = o.optLong("id"),
                dateKey = o.optString("dateKey"),
                displayDate = o.optString("displayDate"),
                weightKg = o.optDouble("weightKg", 0.0).toFloat()
            )
        }.sortedByDescending { it.id }
    }

    fun addWeightLog(context: Context, weightKg: Float): List<WeightLogEntry> {
        val now = Date()
        val rounded = (weightKg * 10f).roundToInt() / 10f
        edit(context) { root ->
            val arr = root.optJSONArray("weightHistory") ?: JSONArray().also { root.put("weightHistory", it) }
            arr.put(JSONObject().apply {
                put("id", System.currentTimeMillis())
                put("dateKey", DateUtils.formatDayKey(now))
                put("displayDate", DateUtils.formatDisplayDate(now))
                put("weightKg", rounded.toDouble())
            })
            val profObj = root.optJSONObject("userProfile") ?: JSONObject().also { root.put("userProfile", it) }
            profObj.put("currentWeightKg", rounded.toDouble())
        }
        return loadWeightHistory(context)
    }

    fun deleteWeightLog(context: Context, id: Long): List<WeightLogEntry> {
        edit(context) { root ->
            root.optJSONArray("weightHistory")?.let { arr ->
                root.put("weightHistory", arr.filterObjects { it.optLong("id") != id })
            }
        }
        return loadWeightHistory(context)
    }

    // --- Widget settings (one entry per placed widget) ---

    fun loadWidgetSettings(context: Context, appWidgetId: Int, kind: String): WidgetSettings {
        val obj = root(context).optJSONObject("widgetSettings")?.optJSONObject(appWidgetId.toString())
            ?: return defaultWidgetSettings(kind)
        val optionsArr = obj.optJSONArray("options")
        return WidgetSettings(
            theme = obj.optString("theme", THEME_SYSTEM),
            opacityPercent = obj.optInt("opacityPercent", 100).coerceIn(0, 100),
            style = obj.optString("style", WIDGET_STYLE_STANDARD),
            options = optionsArr?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
                ?: defaultWidgetSettings(kind).options
        )
    }

    fun saveWidgetSettings(context: Context, appWidgetId: Int, settings: WidgetSettings) = edit(context) { root ->
        val all = root.optJSONObject("widgetSettings") ?: JSONObject().also { root.put("widgetSettings", it) }
        all.put(appWidgetId.toString(), JSONObject().apply {
            put("theme", settings.theme)
            put("opacityPercent", settings.opacityPercent.coerceIn(0, 100))
            put("style", settings.style)
            put("options", JSONArray(settings.options.toList()))
        })
    }

    fun deleteWidgetSettings(context: Context, appWidgetIds: IntArray) = edit(context) { root ->
        val all = root.optJSONObject("widgetSettings") ?: return@edit
        appWidgetIds.forEach { all.remove(it.toString()) }
    }

    // --- App settings ---

    fun loadSettings(context: Context): AppSettings {
        val obj = root(context).optJSONObject("settings") ?: return AppSettings()
        // Older versions stored one of four named themes; map them onto light/dark.
        val theme = when (val stored = obj.optString("themeChoice", THEME_SYSTEM)) {
            THEME_SYSTEM, THEME_LIGHT, THEME_DARK -> stored
            "ARCTIC" -> THEME_LIGHT
            else -> THEME_DARK
        }
        return AppSettings(
            themeChoice = theme,
            targetFps = obj.optInt("targetFps", 120),
            hapticsEnabled = obj.optBoolean("hapticsEnabled", true),
            weightStepKg = obj.optDouble("weightStepKg", 2.5).toFloat(),
            restTimerSeconds = obj.optInt("restTimerSeconds", 90),
            keepScreenAwake = obj.optBoolean("keepScreenAwake", true),
            restSoundEnabled = obj.optBoolean("restSoundEnabled", true)
        )
    }

    fun saveSettings(context: Context, settings: AppSettings) = edit(context) { root ->
        root.put("settings", JSONObject().apply {
            put("targetFps", settings.targetFps)
            put("hapticsEnabled", settings.hapticsEnabled)
            put("weightStepKg", settings.weightStepKg.toDouble())
            put("restTimerSeconds", settings.restTimerSeconds)
            put("keepScreenAwake", settings.keepScreenAwake)
            put("restSoundEnabled", settings.restSoundEnabled)
            put("themeChoice", settings.themeChoice)
        })
    }

    // --- Rest timer ---

    fun setRestEndTimeMillis(context: Context, endTimeMillis: Long, fromWidget: Boolean = false) {
        edit(context, notifyInApp = fromWidget) { root ->
            val activeObj = root.optJSONObject("activeSession") ?: return
            activeObj.put("restEndTimeMillis", endTimeMillis)
        }
        RestAlarmScheduler.scheduleOrCancel(context.applicationContext, endTimeMillis)
    }

    fun getRestEndTimeMillis(context: Context): Long =
        root(context).optJSONObject("activeSession")?.optLong("restEndTimeMillis", 0L) ?: 0L

    // --- Backup ---

    fun exportJsonBackupString(context: Context): String = synchronized(lock) { root(context).toString(2) }

    fun importJsonBackupString(context: Context, jsonContent: String): Boolean {
        val parsed = try {
            JSONObject(jsonContent.removePrefix("﻿").trim())
        } catch (e: Exception) {
            return false
        }
        synchronized(lock) {
            cachedRoot = parsed
            cachedHistory = null
            persist(context, parsed)
        }
        scheduleSurfaceRefresh(context, notifyInApp = false)
        return true
    }

    // --- Per-exercise notes & rest defaults ---

    private fun exerciseKey(name: String) = name.lowercase(Locale.US)

    fun getExerciseNote(context: Context, exerciseName: String): String =
        root(context).optJSONObject("exerciseNotes")?.optString(exerciseKey(exerciseName), "").orEmpty()

    fun saveExerciseNote(context: Context, exerciseName: String, note: String) = edit(context) { root ->
        val notesObj = root.optJSONObject("exerciseNotes") ?: JSONObject().also { root.put("exerciseNotes", it) }
        if (note.isBlank()) notesObj.remove(exerciseKey(exerciseName))
        else notesObj.put(exerciseKey(exerciseName), note.trim())
    }

    fun getExerciseDefaultRest(context: Context, exerciseName: String, fallback: Int): Int =
        root(context).optJSONObject("exerciseRestDefaults")?.optInt(exerciseKey(exerciseName), fallback) ?: fallback

    fun saveExerciseDefaultRest(context: Context, exerciseName: String, restSec: Int) = edit(context) { root ->
        val restObj = root.optJSONObject("exerciseRestDefaults") ?: JSONObject().also { root.put("exerciseRestDefaults", it) }
        restObj.put(exerciseKey(exerciseName), restSec)
    }

    // --- Split templates ---

    fun loadTemplateForSplit(context: Context, splitTitle: String): List<Pair<String, String>> {
        val arr = root(context).optJSONObject("splitTemplates")?.optJSONArray(splitTitle)
            ?: return defaultTemplateForSplit(splitTitle).also { saveTemplateForSplit(context, splitTitle, it) }
        return (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            item.optString("name") to item.optString("muscle")
        }
    }

    fun saveTemplateForSplit(context: Context, splitTitle: String, list: List<Pair<String, String>>) = edit(context) { root ->
        val templatesObj = root.optJSONObject("splitTemplates") ?: JSONObject().also { root.put("splitTemplates", it) }
        templatesObj.put(splitTitle, JSONArray().apply {
            list.forEach { (name, muscle) -> put(JSONObject().put("name", name).put("muscle", muscle)) }
        })
    }

    fun addExerciseToSplitTemplate(context: Context, splitTitle: String, name: String, muscle: String = "Custom") {
        val current = loadTemplateForSplit(context, splitTitle)
        if (current.none { it.first.equals(name, ignoreCase = true) }) {
            saveTemplateForSplit(context, splitTitle, current + (name to muscle))
        }
    }

    fun removeExerciseFromSplitTemplate(context: Context, splitTitle: String, name: String) {
        val current = loadTemplateForSplit(context, splitTitle)
        saveTemplateForSplit(context, splitTitle, current.filterNot { it.first.equals(name, ignoreCase = true) })
    }

    // --- Active session ---

    fun saveActiveSession(context: Context, splitTitle: String?, exercises: List<ExerciseEntry>, fromWidget: Boolean = false) {
        edit(context, notifyInApp = fromWidget) { root ->
            if (splitTitle == null) {
                root.remove("activeSession")
            } else {
                val prevObj = root.optJSONObject("activeSession")
                root.put("activeSession", JSONObject().apply {
                    put("splitTitle", splitTitle)
                    put("startTimeMillis", prevObj?.optLong("startTimeMillis", 0L)?.takeIf { it > 0L } ?: System.currentTimeMillis())
                    put("restEndTimeMillis", prevObj?.optLong("restEndTimeMillis", 0L) ?: 0L)
                    put("exercises", StoreCodec.serializeExercises(exercises))
                })
            }
        }
        if (splitTitle == null) RestAlarmScheduler.scheduleOrCancel(context.applicationContext, 0L)
    }

    fun getActiveSessionStartTime(context: Context): Long =
        root(context).optJSONObject("activeSession")?.optLong("startTimeMillis", 0L)?.takeIf { it > 0L }
            ?: System.currentTimeMillis()

    fun loadActiveSession(context: Context): Pair<String, List<ExerciseEntry>>? {
        val activeObj = root(context).optJSONObject("activeSession") ?: return null
        val splitTitle = activeObj.optString("splitTitle", "")
        if (splitTitle.isBlank()) return null
        return splitTitle to StoreCodec.deserializeExercises(activeObj.optJSONArray("exercises"))
    }

    // --- Workout history ---

    fun saveCompletedWorkout(context: Context, workout: CompletedWorkout) {
        edit(context) { root ->
            val historyArr = root.optJSONArray("history") ?: JSONArray().also { root.put("history", it) }
            historyArr.put(StoreCodec.serializeWorkout(workout))
            root.remove("activeSession")
            cachedHistory = null
        }
        RestAlarmScheduler.scheduleOrCancel(context.applicationContext, 0L)
    }

    fun updateCompletedWorkout(context: Context, updatedWorkout: CompletedWorkout) {
        edit(context) { root ->
            val arr = root.optJSONArray("history") ?: return
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optLong("id") == updatedWorkout.id) {
                    arr.put(i, StoreCodec.serializeWorkout(updatedWorkout))
                    break
                }
            }
            cachedHistory = null
        }
    }

    fun deleteWorkoutFromHistory(context: Context, workoutId: Long) {
        edit(context) { root ->
            val arr = root.optJSONArray("history") ?: return
            root.put("history", arr.filterObjects { it.optLong("id") != workoutId })
            cachedHistory = null
        }
    }

    /** Newest first. The parsed list is cached until history changes, so callers can use it as a stable key. */
    fun loadHistory(context: Context): List<CompletedWorkout> = synchronized(lock) {
        cachedHistory ?: StoreCodec.parseHistory(root(context).optJSONArray("history")).also { cachedHistory = it }
    }

    private inline fun JSONArray.filterObjects(keep: (JSONObject) -> Boolean): JSONArray {
        val out = JSONArray()
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            if (keep(o)) out.put(o)
        }
        return out
    }
}

/**
 * Pure JSON <-> model mapping for the store file, kept free of Context so it can be unit-tested
 * and reused by data migrations. Reading is lenient: older saves and hand-edited backups must still load.
 */
internal object StoreCodec {
    fun parseHistory(arr: JSONArray?): List<CompletedWorkout> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val skippedJson = obj.optJSONArray("skippedExercises")
            CompletedWorkout(
                id = obj.optLong("id"),
                splitTitle = obj.optString("splitTitle"),
                dateKey = obj.optString("dateKey"),
                displayDate = obj.optString("displayDate"),
                durationSeconds = obj.optLong("durationSeconds", 0L),
                bodyWeightKg = obj.optDouble("bodyWeightKg", 0.0).toFloat(),
                photoPath = obj.optString("photoPath", "").ifBlank { null },
                skippedExercises = skippedJson?.let { s -> (0 until s.length()).map { s.optString(it) } }.orEmpty(),
                exercises = deserializeExercises(obj.optJSONArray("exercises")),
                totalVolumeKg = obj.optInt("totalVolumeKg", 0),
                prsHit = obj.optInt("prsHit", 0)
            )
        }.sortedByDescending { it.id }
    }

    fun serializeWorkout(workout: CompletedWorkout) = JSONObject().apply {
        put("id", workout.id)
        put("splitTitle", workout.splitTitle)
        put("dateKey", workout.dateKey)
        put("displayDate", workout.displayDate)
        put("durationSeconds", workout.durationSeconds)
        put("bodyWeightKg", workout.bodyWeightKg.toDouble())
        put("photoPath", workout.photoPath ?: "")
        put("skippedExercises", JSONArray(workout.skippedExercises))
        put("exercises", serializeExercises(workout.exercises))
        put("totalVolumeKg", workout.totalVolumeKg)
        put("prsHit", workout.prsHit)
    }

    fun serializeExercises(exercises: List<ExerciseEntry>): JSONArray = JSONArray().apply {
        for (ex in exercises) {
            put(JSONObject().apply {
                put("name", ex.name)
                put("targetMuscle", ex.targetMuscle)
                put("restSeconds", ex.restSeconds)
                put("isSkipped", ex.isSkipped)
                put("isSuperset", ex.isSuperset)
                put("sets", JSONArray().apply {
                    for (s in ex.sets) {
                        put(JSONObject().apply {
                            put("setNumber", s.setNumber)
                            put("weight", s.weight.replace("kg", "", ignoreCase = true).trim())
                            put("reps", s.reps.replace("r", "", ignoreCase = true).trim())
                            put("isWarmup", s.isWarmup)
                            put("intensityType", s.intensityType)
                            put("isCompleted", s.isCompleted)
                            put("restSeconds", s.restSeconds)
                        })
                    }
                })
            })
        }
    }

    fun deserializeExercises(arr: JSONArray?): List<ExerciseEntry> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val exObj = arr.getJSONObject(i)
            val exRest = exObj.optInt("restSeconds", 90)
            val setsArr = exObj.optJSONArray("sets") ?: JSONArray()
            val sets = (0 until setsArr.length()).map { j ->
                val sObj = setsArr.getJSONObject(j)
                // Older saves only had isWarmup; intensityType was added later.
                val legacyWarmup = sObj.optBoolean("isWarmup", false)
                val type = sObj.optString("intensityType", if (legacyWarmup) "WARMUP" else "NORMAL")
                SetEntry(
                    setNumber = sObj.optInt("setNumber", j + 1),
                    weight = (sObj.opt("weight")?.toString() ?: "20").replace("kg", "", ignoreCase = true).trim(),
                    reps = (sObj.opt("reps")?.toString() ?: "8").replace("r", "", ignoreCase = true).trim(),
                    isWarmup = legacyWarmup || type == "WARMUP",
                    intensityType = type,
                    isCompleted = sObj.optBoolean("isCompleted", false),
                    restSeconds = sObj.optInt("restSeconds", exRest)
                )
            }
            ExerciseEntry(
                name = exObj.optString("name", ""),
                targetMuscle = exObj.optString("targetMuscle", ""),
                restSeconds = exRest,
                isSkipped = exObj.optBoolean("isSkipped", false),
                sets = sets,
                isSuperset = exObj.optBoolean("isSuperset", false)
            )
        }
    }
}

// ============================================================================
// RECORDS, WEEKLY SUMMARY & SPLIT-ISOLATED PROGRESSION AI
// ============================================================================

fun calculateEstimated1Rm(weightKg: Float, reps: Int): Float {
    if (weightKg <= 0f || reps <= 0) return 0f
    if (reps == 1) return weightKg
    return weightKg * (1f + reps / 30f)
}

fun computeExerciseRecords(exerciseName: String, history: List<CompletedWorkout>): ExerciseRecords {
    var maxWeight = 0f
    var best1Rm = 0f
    var bestSetStr = "—"
    val recentLogs = ArrayList<Pair<String, String>>(5)
    val recentChartPoints = ArrayList<Pair<String, Float>>(5)

    for (workout in history) {
        val matched = workout.exercises.find {
            !it.isSkipped && it.name.equals(exerciseName, ignoreCase = true)
        } ?: continue
        val workSets = matched.sets.filter { it.isValidWorkSet }
        if (workSets.isEmpty()) continue

        if (recentLogs.size < 5) {
            val shortDate = workout.displayDate.substringBefore(",")
            recentLogs.add("${workout.splitTitle} ($shortDate)" to workSets.joinToString(" • ") { "${it.weight}kg × ${it.reps}" })
            recentChartPoints.add(shortDate to workSets.maxOf { it.weightKg })
        }
        for (s in workSets) {
            val w = s.weightKg
            val e1rm = calculateEstimated1Rm(w, s.repCount)
            if (w > maxWeight) maxWeight = w
            if (e1rm > best1Rm) {
                best1Rm = e1rm
                bestSetStr = "${s.weight}kg × ${s.repCount}"
            }
        }
    }

    return ExerciseRecords(
        maxWeightKg = maxWeight,
        bestEstimated1Rm = best1Rm.roundToInt(),
        bestSetText = bestSetStr,
        recentHistory = recentLogs,
        chartPoints = recentChartPoints.reversed()
    )
}

fun isSetPersonalRecord(setEntry: SetEntry, records: ExerciseRecords): Boolean {
    if (setEntry.isWarmupSet || !setEntry.isCompleted) return false
    val w = setEntry.weightKg
    val r = setEntry.repCount
    if (w <= 0f || r <= 0 || records.bestEstimated1Rm <= 0) return false
    return calculateEstimated1Rm(w, r).roundToInt() > records.bestEstimated1Rm || w > records.maxWeightKg
}

fun computeWeekSummary(
    history: List<CompletedWorkout>,
    weekOffset: Int = 0,
    splitProgramId: String = "PPL_ARNOLD"
): WeekSummaryData {
    val prog = programFor(splitProgramId)
    val coreRotation = prog.splits.map { it.title }

    val startCal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.SUNDAY
        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.WEEK_OF_YEAR, weekOffset)
    }
    val endCal = (startCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, 6)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }

    val weekWorkouts = history.filter { it.id in startCal.timeInMillis..endCal.timeInMillis }
    val completedCore = weekWorkouts.map { it.splitTitle }.toSet().intersect(coreRotation.toSet())
    val remainingCore = coreRotation.filter { it !in completedCore }

    val titleLabel = when (weekOffset) {
        0 -> "This week"
        -1 -> "Last week"
        else -> "${-weekOffset} weeks ago"
    }

    // Next up is the first split not done this week; once all are done, a stable pick for a bonus day.
    val recommendedSplit = remainingCore.firstOrNull() ?: run {
        val weekSeed = startCal.get(Calendar.YEAR) * 100 + startCal.get(Calendar.WEEK_OF_YEAR)
        coreRotation[Random(weekSeed).nextInt(coreRotation.size)]
    }

    return WeekSummaryData(
        weekOffset = weekOffset,
        titleLabel = titleLabel,
        dateRangeLabel = "${DateUtils.formatShortDay(startCal.time)} – ${DateUtils.formatShortDayYear(endCal.time)}",
        workouts = weekWorkouts,
        completedCoreSplits = completedCore,
        recommendedSplit = recommendedSplit,
        weeklyGoal = prog.weeklyGoal
    )
}

/**
 * Weight suggestion based only on past sessions of the same split, using a 6–8 rep target:
 * two sessions in a row at 8+ reps → go up; two at 6 or fewer → go down; volume falling three
 * sessions in a row → back off 10%. Returns null when this exercise has no history in the split.
 */
fun suggestFromSplitHistory(
    splitTitle: String,
    exercise: ExerciseEntry,
    history: List<CompletedWorkout>,
    stepKg: Float
): Suggestion? {
    // Work sets of this exercise from each past session of the same split, newest first.
    val pastSessions = history
        .filter { it.splitTitle.equals(splitTitle, ignoreCase = true) }
        .mapNotNull { workout ->
            workout.exercises
                .find { !it.isSkipped && it.name.equals(exercise.name, ignoreCase = true) }
                ?.sets?.filter { it.isValidWorkSet }
                ?.takeIf { it.isNotEmpty() }
        }
    if (pastSessions.isEmpty()) return null

    val prevSessionSets = pastSessions[0]
    val prevTopSet = prevSessionSets.maxBy { it.weightKg }
    val prevWeight = prevTopSet.weightKg
    val prevWeightStr = prevTopSet.weight

    fun volume(sets: List<SetEntry>) = sets.sumOf { (it.weightKg * it.repCount).toDouble() }
    fun hit8Plus(sets: List<SetEntry>) = sets.any { it.repCount >= 8 }
    fun hit6OrLess(sets: List<SetEntry>) = sets.any { it.repCount in 1..6 }
    fun increase(message: (String) -> String) = formatKgClean(prevWeight + stepKg).let {
        Suggestion(message(it), SuggestionTone.GOOD, suggestedWeight = it)
    }
    fun decrease(message: (String) -> String) = formatKgClean(prevWeight - stepKg).let {
        Suggestion(message(it), SuggestionTone.CAUTION, suggestedWeight = it)
    }

    if (pastSessions.size >= 3) {
        val vol1 = volume(pastSessions[0])
        val vol2 = volume(pastSessions[1])
        val vol3 = volume(pastSessions[2])
        if (vol1 > 0 && vol1 < vol2 && vol2 < vol3) {
            val dropW = formatKgClean(prevWeight * 0.9f)
            return Suggestion(
                message = "Your numbers have dipped three sessions in a row. Go lighter today: $dropW kg.",
                tone = SuggestionTone.CAUTION,
                suggestedWeight = dropW
            )
        }
    }

    val prevHad8Plus = hit8Plus(prevSessionSets)
    val prevHad6OrLess = hit6OrLess(prevSessionSets)

    val completedToday = exercise.sets.filter { !it.isWarmupSet && it.isCompleted && it.repCount > 0 }
    if (completedToday.isNotEmpty()) {
        if (prevHad8Plus && hit8Plus(completedToday)) {
            return increase { w -> "8+ reps two sessions running. Move up to $w kg." }
        }
        if (prevHad6OrLess && hit6OrLess(completedToday)) {
            return decrease { w -> "Under 7 reps two sessions running. Drop to $w kg." }
        }
    }

    if (pastSessions.size >= 2) {
        if (prevHad8Plus && hit8Plus(pastSessions[1])) {
            return increase { w -> "You hit 8+ reps the last two times. Try $w kg today." }
        }
        if (prevHad6OrLess && hit6OrLess(pastSessions[1])) {
            return decrease { w -> "Under 7 reps the last two times. Try $w kg today." }
        }
    }

    return when {
        prevHad8Plus -> Suggestion(
            "Last time: $prevWeightStr kg for 8+ reps. Do it again to move up.",
            SuggestionTone.NEUTRAL, prevWeightStr
        )
        prevHad6OrLess -> Suggestion(
            "Last time: $prevWeightStr kg for 6 or fewer. Aim for 7–8 reps.",
            SuggestionTone.CAUTION, prevWeightStr
        )
        else -> Suggestion(
            "Last time: $prevWeightStr kg. Aim for 6–8 reps.",
            SuggestionTone.NEUTRAL, prevWeightStr
        )
    }
}

// ============================================================================
// REST TIMER ALARM
// ============================================================================

object RestAlarmScheduler {
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private var fallbackRunnable: Runnable? = null

    fun scheduleOrCancel(context: Context, endTimeMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 777, Intent(context, RestAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try { alarmManager.cancel(pendingIntent) } catch (_: Exception) {}
        fallbackRunnable?.let { fallbackHandler.removeCallbacks(it) }

        val delayMillis = endTimeMillis - System.currentTimeMillis()
        if (endTimeMillis <= 0L || delayMillis <= 500L) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, endTimeMillis, 2000L, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimeMillis, pendingIntent)
            }
        } catch (_: Exception) {
            try { alarmManager.set(AlarmManager.RTC_WAKEUP, endTimeMillis, pendingIntent) } catch (_: Exception) {}
        }

        // In-process backup in case the alarm is delayed while the app is in the foreground.
        val task = Runnable { triggerRestFinishedAlert(context) }
        fallbackRunnable = task
        fallbackHandler.postDelayed(task, delayMillis)
    }

    @Synchronized
    fun triggerRestFinishedAlert(context: Context) {
        if (LocalStorage.getRestEndTimeMillis(context) <= 0L) return

        val settings = LocalStorage.loadSettings(context)
        val active = LocalStorage.loadActiveSession(context)
        LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)

        val nextEx = active?.second?.firstOrNull { ex -> !ex.isSkipped && ex.sets.any { !it.isCompleted } }
        val nextSet = nextEx?.sets?.firstOrNull { !it.isCompleted }
        val detailLine = if (nextEx != null && nextSet != null) {
            "Next: ${nextEx.name} · ${nextSet.weight} kg × ${nextSet.reps}"
        } else {
            "Time for your next set."
        }

        if (settings.restSoundEnabled) playRestBeep(context)
        sendRestFinishedNotification(context, active?.first ?: "Workout", detailLine)
    }

    /** Short alarm beep that ducks (rather than stops) any music that's playing. */
    private fun playRestBeep(context: Context) {
        try {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                    .also { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                null
            }

            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
            Handler(Looper.getMainLooper()).postDelayed({
                try { toneGen.release() } catch (_: Exception) {}
                if (focusRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(null)
                }
            }, 320L)
        } catch (_: Exception) {}
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        RestAlarmScheduler.triggerRestFinishedAlert(context.applicationContext)
        LocalStorage.flush(context)
    }
}
