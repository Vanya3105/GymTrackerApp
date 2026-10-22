package com.example.gymtracker

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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

@Immutable
data class WidgetAppearance(
    val isDark: Boolean = true,
    val opacityPercent: Int = 94
)

val SPLIT_PROGRAMS = mapOf(
    "PPL_ARNOLD" to SplitProgramInfo(
        id = "PPL_ARNOLD",
        name = "PPL + Arnold Hybrid",
        tagline = "Push • Pull • Arms & Shoulders • Chest & Back",
        weeklyGoal = 4,
        splits = listOf(
            WorkoutSplit("Push", "CHEST • SHOULDERS • TRICEPS", "01"),
            WorkoutSplit("Pull", "LATS • UPPER BACK • BICEPS", "02"),
            WorkoutSplit("Arms & Shoulders", "DELTS • BICEPS • TRICEPS", "03"),
            WorkoutSplit("Chest & Back", "ANTAGONIST HYPERTROPHY", "04")
        )
    ),
    "CLASSIC_PPL" to SplitProgramInfo(
        id = "CLASSIC_PPL",
        name = "Classic Push / Pull / Legs",
        tagline = "3-Day Hypertrophy Foundation",
        weeklyGoal = 3,
        splits = listOf(
            WorkoutSplit("Push", "CHEST • SHOULDERS • TRICEPS", "01"),
            WorkoutSplit("Pull", "LATS • MID BACK • BICEPS", "02"),
            WorkoutSplit("Legs", "QUADS • HAMSTRINGS • CALVES", "03")
        )
    ),
    "UPPER_LOWER" to SplitProgramInfo(
        id = "UPPER_LOWER",
        name = "Upper / Lower 4-Day Split",
        tagline = "High Frequency Strength & Hypertrophy",
        weeklyGoal = 4,
        splits = listOf(
            WorkoutSplit("Upper A", "CHEST & BACK FOCUS • ARMS", "01"),
            WorkoutSplit("Lower A", "QUAD FOCUS • HAMSTRINGS • CALVES", "02"),
            WorkoutSplit("Upper B", "SHOULDER & ARM FOCUS • TORSO", "03"),
            WorkoutSplit("Lower B", "POSTERIOR CHAIN • GLUTES • QUADS", "04")
        )
    ),
    "FULL_BODY" to SplitProgramInfo(
        id = "FULL_BODY",
        name = "Full Body 3-Day Split",
        tagline = "Maximum Efficiency • 3x Weekly Stimulus",
        weeklyGoal = 3,
        splits = listOf(
            WorkoutSplit("Full Body A", "INCLINE PRESS • PULLDOWN • SQUAT", "01"),
            WorkoutSplit("Full Body B", "SHOULDER PRESS • ROW • RDL", "02"),
            WorkoutSplit("Full Body C", "DIPS/CHEST • CHIN-UPS • LEG PRESS", "03")
        )
    )
)

val REST_PRESETS = listOf(0, 60, 90, 120, 180, 240)
val WEIGHT_STEP_PRESETS = listOf(0.5f, 1.0f, 1.25f, 2.0f, 2.5f, 5.0f)

fun formatRestShort(sec: Int): String {
    if (sec <= 0) return "Off"
    return if (sec % 60 == 0) "${sec / 60}m" else "${sec}s"
}

fun cycleRestPreset(current: Int): Int {
    val idx = REST_PRESETS.indexOf(current)
    return if (idx == -1 || idx == REST_PRESETS.lastIndex) REST_PRESETS.first() else REST_PRESETS[idx + 1]
}

fun formatKgClean(value: Float): String {
    val clamped = (value.coerceAtLeast(0f) * 100f).roundToInt() / 100f
    return if (clamped % 1f == 0f) "${clamped.toInt()}" else "$clamped"
}

fun parseWeightClean(raw: String, fallback: Float = 20f): Float {
    val cleaned = raw.replace("kg", "", ignoreCase = true).trim()
    return cleaned.toFloatOrNull() ?: fallback
}

fun parseRepsClean(raw: String, fallback: Int = 8): Int {
    val cleaned = raw.replace("r", "", ignoreCase = true).replace("reps", "", ignoreCase = true).trim()
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

        val modes = display.supportedModes
        val currentMode = display.mode
        val sameResModes = modes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
        }.ifEmpty { modes.toList() }

        val bestMode = sameResModes.minByOrNull { abs(it.refreshRate - targetHz.toFloat()) }
        if (bestMode != null) {
            val params = window.attributes
            if (params.preferredDisplayModeId != bestMode.modeId) {
                params.preferredDisplayModeId = bestMode.modeId
                window.attributes = params
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            window.attributes.preferredRefreshRate = targetHz.toFloat()
        }
    } catch (_: Exception) {}
}

const val REST_NOTIFICATION_CHANNEL_ID = "gym_rest_timer_channel"
const val SET_DONE_NOTIFICATION_CHANNEL_ID = "gym_set_done_channel"

fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun ensureNotificationChannelCreated(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val restChannel = android.app.NotificationChannel(
                REST_NOTIFICATION_CHANNEL_ID,
                "Rest Timer Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when your rest timer between sets finishes"
                enableVibration(true)
            }
            val setDoneChannel = android.app.NotificationChannel(
                SET_DONE_NOTIFICATION_CHANNEL_ID,
                "Set Completed Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when a set is marked completed"
                enableVibration(true)
            }
            manager.createNotificationChannel(restChannel)
            manager.createNotificationChannel(setDoneChannel)
        } catch (_: Exception) {}
    }
}

fun sendSetDoneNotification(
    context: Context,
    exerciseName: String,
    weight: String,
    reps: String,
    restSeconds: Int
) {
    if (!hasNotificationPermission(context)) return
    try {
        ensureNotificationChannelCreated(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 1, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, SET_DONE_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        val restMsg = if (restSeconds > 0) " • Resting ${formatRestShort(restSeconds)}" else " • Ready for next set!"
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Set Done ✓ • $exerciseName")
            .setContentText("${weight}kg × $reps reps logged$restMsg")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(1002, notification)
    } catch (_: Exception) {}
}

fun sendRestFinishedNotification(
    context: Context,
    splitTitle: String,
    detailText: String = "Rest is over! Time to start your next set."
) {
    if (!hasNotificationPermission(context)) return
    try {
        ensureNotificationChannelCreated(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, REST_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Rest Over • $splitTitle")
            .setContentText(detailText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    } catch (_: Exception) {}
}

object PhotoStorage {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()

    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? {
        return try {
            val folder = File(context.filesDir, "workout_photos").apply { mkdirs() }
            val file = File(folder, "photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            memoryCache[file.absolutePath] = bitmap.asImageBitmap()
            file.absolutePath
        } catch (e: Exception) { null }
    }

    fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input)
            input.close()
            if (original == null) return null
            val maxDim = 1400
            val scaled = if (original.width > maxDim || original.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
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
        memoryCache[path]?.let { return it }
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val bmp = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            if (bmp != null) memoryCache[path] = bmp
            bmp
        } catch (e: Exception) { null }
    }
}

object LocalStorage {
    private const val FILE_NAME = "gym_tracker_store_v2.json"

    @Synchronized
    private fun getRoot(context: Context): JSONObject {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    @Synchronized
    private fun saveRootSync(context: Context, root: JSONObject, notifyInApp: Boolean = false) {
        val appCtx = context.applicationContext
        try {
            File(appCtx.filesDir, FILE_NAME).writeText(root.toString())
        } catch (_: Exception) {}

        GymWidgetProvider.refreshAllWidgets(appCtx)
        MuscleFatigueWidgetProvider.refreshAll(appCtx)
        SquarePulseWidgetProvider.refreshAll(appCtx)
        LockScreenWorkoutController.sync(appCtx)

        if (notifyInApp) {
            try {
                appCtx.sendBroadcast(Intent(ACTION_SESSION_SYNC).setPackage(appCtx.packageName))
            } catch (_: Exception) {}
        }
    }

    fun loadUserProfile(context: Context): UserProfile {
        val root = getRoot(context)
        val obj = root.optJSONObject("userProfile") ?: return UserProfile()
        return UserProfile(
            hasCompletedOnboarding = obj.optBoolean("hasCompletedOnboarding", false),
            age = obj.optInt("age", 0),
            currentWeightKg = obj.optDouble("currentWeightKg", 0.0).toFloat(),
            splitProgram = obj.optString("splitProgram", "PPL_ARNOLD"),
            currentStreakWeeks = obj.optInt("currentStreakWeeks", 0)
        )
    }

    fun saveUserProfile(context: Context, profile: UserProfile) {
        val root = getRoot(context)
        val obj = JSONObject().apply {
            put("hasCompletedOnboarding", profile.hasCompletedOnboarding)
            put("age", profile.age)
            put("currentWeightKg", profile.currentWeightKg.toDouble())
            put("splitProgram", profile.splitProgram)
            put("currentStreakWeeks", profile.currentStreakWeeks)
        }
        root.put("userProfile", obj)
        saveRootSync(context, root)
    }

    fun loadWeightHistory(context: Context): List<WeightLogEntry> {
        val root = getRoot(context)
        val arr = root.optJSONArray("weightHistory") ?: return emptyList()
        val list = ArrayList<WeightLogEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                WeightLogEntry(
                    id = o.optLong("id"),
                    dateKey = o.optString("dateKey"),
                    displayDate = o.optString("displayDate"),
                    weightKg = o.optDouble("weightKg", 0.0).toFloat()
                )
            )
        }
        return list.sortedByDescending { it.id }
    }

    fun addWeightLog(context: Context, weightKg: Float): List<WeightLogEntry> {
        val root = getRoot(context)
        val now = Date()
        val rounded = (weightKg * 10f).roundToInt() / 10f
        val arr = root.optJSONArray("weightHistory") ?: JSONArray()
        val obj = JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("dateKey", DateUtils.formatDayKey(now))
            put("displayDate", DateUtils.formatDisplayDate(now))
            put("weightKg", rounded.toDouble())
        }
        arr.put(obj)
        root.put("weightHistory", arr)

        val profObj = root.optJSONObject("userProfile") ?: JSONObject()
        profObj.put("currentWeightKg", rounded.toDouble())
        root.put("userProfile", profObj)

        saveRootSync(context, root)
        return loadWeightHistory(context)
    }

    fun deleteWeightLog(context: Context, id: Long): List<WeightLogEntry> {
        val root = getRoot(context)
        val arr = root.optJSONArray("weightHistory") ?: return emptyList()
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("id") != id) newArr.put(o)
        }
        root.put("weightHistory", newArr)
        saveRootSync(context, root)
        return loadWeightHistory(context)
    }

    fun loadWidgetAppearance(context: Context): WidgetAppearance {
        val root = getRoot(context)
        val obj = root.optJSONObject("widgetAppearance") ?: return WidgetAppearance()
        return WidgetAppearance(
            isDark = obj.optBoolean("isDark", true),
            opacityPercent = obj.optInt("opacityPercent", 94).coerceIn(0, 100)
        )
    }

    fun saveWidgetAppearance(context: Context, appearance: WidgetAppearance) {
        val root = getRoot(context)
        val obj = JSONObject().apply {
            put("isDark", appearance.isDark)
            put("opacityPercent", appearance.opacityPercent.coerceIn(0, 100))
        }
        root.put("widgetAppearance", obj)
        saveRootSync(context, root)
    }

    fun setRestEndTimeMillis(context: Context, endTimeMillis: Long, fromWidget: Boolean = false) {
        val root = getRoot(context)
        val activeObj = root.optJSONObject("activeSession") ?: return
        activeObj.put("restEndTimeMillis", endTimeMillis)
        saveRootSync(context, root, notifyInApp = fromWidget)
        RestAlarmScheduler.scheduleOrCancel(context.applicationContext, endTimeMillis)
    }

    fun getRestEndTimeMillis(context: Context): Long {
        val root = getRoot(context)
        return root.optJSONObject("activeSession")?.optLong("restEndTimeMillis", 0L) ?: 0L
    }

    fun exportJsonBackupString(context: Context): String {
        return getRoot(context).toString(2)
    }

    fun importJsonBackupString(context: Context, jsonContent: String): Boolean {
        return try {
            val clean = jsonContent.removePrefix("\uFEFF").trim()
            val parsed = JSONObject(clean)
            saveRootSync(context, parsed)
            true
        } catch (e: Exception) { false }
    }

    fun loadSettings(context: Context): AppSettings {
        val root = getRoot(context)
        val obj = root.optJSONObject("settings") ?: return AppSettings()
        return AppSettings(
            isDarkMode = obj.optBoolean("isDarkMode", true),
            targetFps = obj.optInt("targetFps", 120),
            hapticsEnabled = obj.optBoolean("hapticsEnabled", true),
            weightStepKg = obj.optDouble("weightStepKg", 2.5).toFloat(),
            restTimerSeconds = obj.optInt("restTimerSeconds", 90),
            keepScreenAwake = obj.optBoolean("keepScreenAwake", true),
            restSoundEnabled = obj.optBoolean("restSoundEnabled", true),
            themeChoice = obj.optString("themeChoice", "OBSIDIAN")
        )
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        val root = getRoot(context)
        val obj = JSONObject().apply {
            put("isDarkMode", settings.isDarkMode)
            put("targetFps", settings.targetFps)
            put("hapticsEnabled", settings.hapticsEnabled)
            put("weightStepKg", settings.weightStepKg.toDouble())
            put("restTimerSeconds", settings.restTimerSeconds)
            put("keepScreenAwake", settings.keepScreenAwake)
            put("restSoundEnabled", settings.restSoundEnabled)
            put("themeChoice", settings.themeChoice)
        }
        root.put("settings", obj)
        saveRootSync(context, root)
    }

    fun getExerciseNote(context: Context, exerciseName: String): String {
        val root = getRoot(context)
        val notesObj = root.optJSONObject("exerciseNotes") ?: return ""
        return notesObj.optString(exerciseName.lowercase(Locale.US), "")
    }

    fun saveExerciseNote(context: Context, exerciseName: String, note: String) {
        val root = getRoot(context)
        val notesObj = root.optJSONObject("exerciseNotes") ?: JSONObject()
        if (note.isBlank()) {
            notesObj.remove(exerciseName.lowercase(Locale.US))
        } else {
            notesObj.put(exerciseName.lowercase(Locale.US), note.trim())
        }
        root.put("exerciseNotes", notesObj)
        saveRootSync(context, root)
    }

    fun getExerciseDefaultRest(context: Context, exerciseName: String, fallback: Int): Int {
        val root = getRoot(context)
        val restObj = root.optJSONObject("exerciseRestDefaults") ?: return fallback
        return restObj.optInt(exerciseName.lowercase(Locale.US), fallback)
    }

    fun saveExerciseDefaultRest(context: Context, exerciseName: String, restSec: Int) {
        val root = getRoot(context)
        val restObj = root.optJSONObject("exerciseRestDefaults") ?: JSONObject()
        restObj.put(exerciseName.lowercase(Locale.US), restSec)
        root.put("exerciseRestDefaults", restObj)
        saveRootSync(context, root)
    }

    fun defaultTemplatesForSplit(splitTitle: String): List<Pair<String, String>> {
        return when (splitTitle) {
            "Push" -> listOf(
                "Incline Bench Press" to "Upper Chest",
                "Pec Deck / Cable Fly" to "Mid Chest",
                "Lateral Raises" to "Side Delts",
                "Tricep Pushdowns" to "Triceps"
            )
            "Pull" -> listOf(
                "Lat Pulldown" to "Lats",
                "Barbell / T-Bar Row" to "Mid Back",
                "Face Pulls / Rear Delt Fly" to "Rear Delts",
                "Preacher Curls" to "Biceps"
            )
            "Legs", "Lower A" -> listOf(
                "Barbell Squat / Hack Squat" to "Quads",
                "Romanian Deadlift" to "Hamstrings",
                "Leg Extension" to "Quads",
                "Seated Leg Curl" to "Hamstrings",
                "Standing Calf Raises" to "Calves"
            )
            "Lower B" -> listOf(
                "Leg Press" to "Quads & Glutes",
                "Lying Leg Curl" to "Hamstrings",
                "Bulgarian Split Squat" to "Quads & Glutes",
                "Seated Calf Raises" to "Calves"
            )
            "Arms & Shoulders" -> listOf(
                "Seated Dumbbell Shoulder Press" to "Shoulders",
                "Cable Lateral Raises" to "Side Delts",
                "Incline Dumbbell Curl" to "Biceps",
                "Overhead Cable Tricep Extension" to "Triceps",
                "Hammer Curls" to "Brachialis"
            )
            "Chest & Back", "Upper A" -> listOf(
                "Incline Dumbbell Press" to "Upper Chest",
                "Weighted Pull-Ups / Lat Pulldown" to "Lats",
                "Bench Press / Chest Press" to "Mid Chest",
                "Chest-Supported Row" to "Upper Back"
            )
            "Upper B" -> listOf(
                "Seated Shoulder Press" to "Shoulders",
                "Cable Row" to "Mid Back",
                "Cable Lateral Raises" to "Side Delts",
                "Preacher Curls" to "Biceps",
                "Tricep Rope Pushdowns" to "Triceps"
            )
            "Full Body A" -> listOf(
                "Incline Dumbbell Press" to "Chest",
                "Lat Pulldown" to "Lats",
                "Leg Press / Squat" to "Legs",
                "Lateral Raises" to "Side Delts"
            )
            "Full Body B" -> listOf(
                "Seated Shoulder Press" to "Shoulders",
                "Chest-Supported Row" to "Back",
                "Romanian Deadlift" to "Hamstrings",
                "Incline Dumbbell Curl" to "Biceps"
            )
            "Full Body C" -> listOf(
                "Bench Press / Dips" to "Chest & Triceps",
                "Weighted Pull-Ups" to "Lats",
                "Leg Extension & Curl Superset" to "Legs",
                "Overhead Tricep Extension" to "Triceps"
            )
            else -> emptyList()
        }
    }

    fun loadTemplateForSplit(context: Context, splitTitle: String): List<Pair<String, String>> {
        val root = getRoot(context)
        val templatesObj = root.optJSONObject("splitTemplates") ?: JSONObject()
        val arr = templatesObj.optJSONArray(splitTitle)
        if (arr == null) {
            val defaults = defaultTemplatesForSplit(splitTitle)
            saveTemplateForSplit(context, splitTitle, defaults)
            return defaults
        }
        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            list.add(item.optString("name") to item.optString("muscle"))
        }
        return list
    }

    fun saveTemplateForSplit(context: Context, splitTitle: String, list: List<Pair<String, String>>) {
        val root = getRoot(context)
        val templatesObj = root.optJSONObject("splitTemplates") ?: JSONObject()
        val arr = JSONArray()
        list.forEach { (name, muscle) ->
            val obj = JSONObject()
            obj.put("name", name)
            obj.put("muscle", muscle)
            arr.put(obj)
        }
        templatesObj.put(splitTitle, arr)
        root.put("splitTemplates", templatesObj)
        saveRootSync(context, root)
    }

    fun addExerciseToSplitTemplate(context: Context, splitTitle: String, name: String, muscle: String = "Custom") {
        val current = loadTemplateForSplit(context, splitTitle).toMutableList()
        if (current.none { it.first.equals(name, ignoreCase = true) }) {
            current.add(name to muscle)
            saveTemplateForSplit(context, splitTitle, current)
        }
    }

    fun removeExerciseFromSplitTemplate(context: Context, splitTitle: String, name: String) {
        val current = loadTemplateForSplit(context, splitTitle)
            .filterNot { it.first.equals(name, ignoreCase = true) }
        saveTemplateForSplit(context, splitTitle, current)
    }

    fun saveActiveSession(
        context: Context,
        splitTitle: String?,
        exercises: List<ExerciseEntry>,
        fromWidget: Boolean = false
    ) {
        val root = getRoot(context)
        if (splitTitle == null) {
            root.remove("activeSession")
            RestAlarmScheduler.scheduleOrCancel(context.applicationContext, 0L)
        } else {
            val prevObj = root.optJSONObject("activeSession")
            val existingStart = prevObj?.optLong("startTimeMillis", 0L)
                ?.takeIf { it > 0L } ?: System.currentTimeMillis()
            val existingRestEnd = prevObj?.optLong("restEndTimeMillis", 0L) ?: 0L

            val activeObj = JSONObject()
            activeObj.put("splitTitle", splitTitle)
            activeObj.put("startTimeMillis", existingStart)
            activeObj.put("restEndTimeMillis", existingRestEnd)
            activeObj.put("exercises", serializeExercises(exercises))
            root.put("activeSession", activeObj)
        }
        saveRootSync(context, root, notifyInApp = fromWidget)
    }

    fun getActiveSessionStartTime(context: Context): Long {
        val root = getRoot(context)
        return root.optJSONObject("activeSession")
            ?.optLong("startTimeMillis", System.currentTimeMillis())
            ?: System.currentTimeMillis()
    }

    fun loadActiveSession(context: Context): Pair<String, List<ExerciseEntry>>? {
        val root = getRoot(context)
        val activeObj = root.optJSONObject("activeSession") ?: return null
        val splitTitle = activeObj.optString("splitTitle", "")
        if (splitTitle.isBlank()) return null
        val exercises = deserializeExercises(activeObj.optJSONArray("exercises"))
        return splitTitle to exercises
    }

    fun saveCompletedWorkout(context: Context, workout: CompletedWorkout) {
        val root = getRoot(context)
        val historyArr = root.optJSONArray("history") ?: JSONArray()
        val skippedArr = JSONArray().apply { workout.skippedExercises.forEach { put(it) } }
        val obj = JSONObject().apply {
            put("id", workout.id)
            put("splitTitle", workout.splitTitle)
            put("dateKey", workout.dateKey)
            put("displayDate", workout.displayDate)
            put("durationSeconds", workout.durationSeconds)
            put("bodyWeightKg", workout.bodyWeightKg.toDouble())
            put("photoPath", workout.photoPath ?: "")
            put("skippedExercises", skippedArr)
            put("exercises", serializeExercises(workout.exercises))
            put("totalVolumeKg", workout.totalVolumeKg)
            put("prsHit", workout.prsHit)
        }
        historyArr.put(obj)
        root.put("history", historyArr)
        root.remove("activeSession")
        RestAlarmScheduler.scheduleOrCancel(context.applicationContext, 0L)
        saveRootSync(context, root)
    }

    fun updateCompletedWorkout(context: Context, updatedWorkout: CompletedWorkout) {
        val root = getRoot(context)
        val arr = root.optJSONArray("history") ?: return
        val newArr = JSONArray()
        val skippedArr = JSONArray().apply { updatedWorkout.skippedExercises.forEach { put(it) } }
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optLong("id") == updatedWorkout.id) {
                val replaced = JSONObject().apply {
                    put("id", updatedWorkout.id)
                    put("splitTitle", updatedWorkout.splitTitle)
                    put("dateKey", updatedWorkout.dateKey)
                    put("displayDate", updatedWorkout.displayDate)
                    put("durationSeconds", updatedWorkout.durationSeconds)
                    put("bodyWeightKg", updatedWorkout.bodyWeightKg.toDouble())
                    put("photoPath", updatedWorkout.photoPath ?: "")
                    put("skippedExercises", skippedArr)
                    put("exercises", serializeExercises(updatedWorkout.exercises))
                    put("totalVolumeKg", updatedWorkout.totalVolumeKg)
                    put("prsHit", updatedWorkout.prsHit)
                }
                newArr.put(replaced)
            } else {
                newArr.put(obj)
            }
        }
        root.put("history", newArr)
        saveRootSync(context, root)
    }

    fun deleteWorkoutFromHistory(context: Context, workoutId: Long) {
        val root = getRoot(context)
        val arr = root.optJSONArray("history") ?: return
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optLong("id") != workoutId) newArr.put(obj)
        }
        root.put("history", newArr)
        saveRootSync(context, root)
    }

    fun loadHistory(context: Context): List<CompletedWorkout> {
        val root = getRoot(context)
        val arr = root.optJSONArray("history") ?: return emptyList()
        val list = ArrayList<CompletedWorkout>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val rawPhoto = obj.optString("photoPath", "")
            val skippedJson = obj.optJSONArray("skippedExercises")
            val skippedList = mutableListOf<String>()
            if (skippedJson != null) {
                for (k in 0 until skippedJson.length()) {
                    skippedList.add(skippedJson.optString(k))
                }
            }
            list.add(
                CompletedWorkout(
                    id = obj.optLong("id"),
                    splitTitle = obj.optString("splitTitle"),
                    dateKey = obj.optString("dateKey"),
                    displayDate = obj.optString("displayDate"),
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    bodyWeightKg = obj.optDouble("bodyWeightKg", 0.0).toFloat(),
                    photoPath = rawPhoto.ifBlank { null },
                    skippedExercises = skippedList,
                    exercises = deserializeExercises(obj.optJSONArray("exercises")),
                    totalVolumeKg = obj.optInt("totalVolumeKg", 0),
                    prsHit = obj.optInt("prsHit", 0)
                )
            )
        }
        return list.sortedByDescending { it.id }
    }

    private fun serializeExercises(exercises: List<ExerciseEntry>): JSONArray {
        val exArray = JSONArray()
        for (ex in exercises) {
            val exObj = JSONObject()
            exObj.put("name", ex.name)
            exObj.put("targetMuscle", ex.targetMuscle)
            exObj.put("restSeconds", ex.restSeconds)
            exObj.put("isSkipped", ex.isSkipped)
            exObj.put("isSuperset", ex.isSuperset)
            val setsArr = JSONArray()
            for (j in ex.sets.indices) {
                val s = ex.sets[j]
                val sObj = JSONObject()
                sObj.put("setNumber", s.setNumber)
                sObj.put("weight", s.weight.replace("kg", "", ignoreCase = true).trim())
                sObj.put("reps", s.reps.replace("r", "", ignoreCase = true).trim())
                sObj.put("isWarmup", s.isWarmup)
                sObj.put("intensityType", s.intensityType)
                sObj.put("isCompleted", s.isCompleted)
                sObj.put("restSeconds", s.restSeconds)
                setsArr.put(sObj)
            }
            exObj.put("sets", setsArr)
            exArray.put(exObj)
        }
        return exArray
    }

    private fun deserializeExercises(arr: JSONArray?): List<ExerciseEntry> {
        if (arr == null) return emptyList()
        val list = ArrayList<ExerciseEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val exObj = arr.getJSONObject(i)
            val exRest = exObj.optInt("restSeconds", 90)
            val setsArr = exObj.optJSONArray("sets") ?: JSONArray()
            val sets = ArrayList<SetEntry>(setsArr.length())
            for (j in 0 until setsArr.length()) {
                val sObj = setsArr.getJSONObject(j)
                val rawWeight = (sObj.opt("weight")?.toString() ?: "20").replace("kg", "", ignoreCase = true).trim()
                val rawReps = (sObj.opt("reps")?.toString() ?: "8").replace("r", "", ignoreCase = true).trim()

                val oldIsWarmup = sObj.optBoolean("isWarmup", false)
                val resolvedType = sObj.optString("intensityType", if (oldIsWarmup) "WARMUP" else "NORMAL")

                sets.add(
                    SetEntry(
                        setNumber = sObj.optInt("setNumber", j + 1),
                        weight = rawWeight,
                        reps = rawReps,
                        isWarmup = oldIsWarmup || resolvedType == "WARMUP",
                        intensityType = resolvedType,
                        isCompleted = sObj.optBoolean("isCompleted", false),
                        restSeconds = sObj.optInt("restSeconds", exRest)
                    )
                )
            }
            list.add(
                ExerciseEntry(
                    name = exObj.optString("name", ""),
                    targetMuscle = exObj.optString("targetMuscle", ""),
                    restSeconds = exRest,
                    isSkipped = exObj.optBoolean("isSkipped", false),
                    sets = sets,
                    isSuperset = exObj.optBoolean("isSuperset", false)
                )
            )
        }
        return list
    }
}

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
        val workSets = matched.sets.filter {
            !it.isWarmup && it.intensityType != "WARMUP" && parseWeightClean(it.weight, 0f) > 0f && parseRepsClean(it.reps, 0) > 0
        }
        if (workSets.isNotEmpty()) {
            val sessionTopWeight = workSets.maxOfOrNull { parseWeightClean(it.weight, 0f) } ?: 0f
            val shortDate = workout.displayDate.substringBefore(",")
            if (recentLogs.size < 5) {
                val summary = workSets.joinToString(" • ") { "${it.weight}kg × ${it.reps}" }
                recentLogs.add("${workout.splitTitle} ($shortDate)" to summary)
                recentChartPoints.add(shortDate to sessionTopWeight)
            }
            for (s in workSets) {
                val w = parseWeightClean(s.weight, 0f)
                val r = parseRepsClean(s.reps, 0)
                val e1rm = calculateEstimated1Rm(w, r)
                if (w > maxWeight) maxWeight = w
                if (e1rm > best1Rm) {
                    best1Rm = e1rm
                    bestSetStr = "${s.weight}kg × $r"
                }
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
    if (setEntry.isWarmup || setEntry.intensityType == "WARMUP" || !setEntry.isCompleted) return false
    val w = parseWeightClean(setEntry.weight, 0f)
    val r = parseRepsClean(setEntry.reps, 0)
    if (w <= 0f || r <= 0 || records.bestEstimated1Rm <= 0) return false
    val current1Rm = calculateEstimated1Rm(w, r).roundToInt()
    return current1Rm > records.bestEstimated1Rm || w > records.maxWeightKg
}

fun computeWeekSummary(
    history: List<CompletedWorkout>,
    weekOffset: Int = 0,
    splitProgramId: String = "PPL_ARNOLD"
): WeekSummaryData {
    val prog = SPLIT_PROGRAMS[splitProgramId] ?: SPLIT_PROGRAMS["PPL_ARNOLD"]!!
    val coreRotation = prog.splits.map { it.title }

    val startCal = Calendar.getInstance()
    startCal.firstDayOfWeek = Calendar.SUNDAY
    startCal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    startCal.set(Calendar.HOUR_OF_DAY, 0)
    startCal.set(Calendar.MINUTE, 0)
    startCal.set(Calendar.SECOND, 0)
    startCal.set(Calendar.MILLISECOND, 0)
    startCal.add(Calendar.WEEK_OF_YEAR, weekOffset)

    val startMillis = startCal.timeInMillis
    val endCal = (startCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, 6)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    val endMillis = endCal.timeInMillis

    val startLabel = DateUtils.formatShortDay(startCal.time)
    val endLabel = DateUtils.formatShortDayYear(endCal.time)

    val weekWorkouts = history.filter { it.id in startMillis..endMillis }
    val doneTitles = weekWorkouts.map { it.splitTitle }.toSet()
    val completedCore = doneTitles.intersect(coreRotation.toSet())
    val remainingCore = coreRotation.filter { it !in completedCore }

    val fatigued = mutableSetOf<String>()
    weekWorkouts.forEach { w ->
        val title = w.splitTitle.lowercase(Locale.US)
        if (title.contains("push") || title.contains("chest")) fatigued.addAll(listOf("Chest", "Front Delts", "Triceps"))
        if (title.contains("pull") || title.contains("back")) fatigued.addAll(listOf("Lats", "Upper Back", "Biceps"))
        if (title.contains("legs") || title.contains("lower")) fatigued.addAll(listOf("Quads", "Hamstrings", "Glutes", "Calves"))
        if (title.contains("arms")) fatigued.addAll(listOf("Biceps", "Triceps", "Side Delts"))
        if (title.contains("full")) fatigued.addAll(listOf("Chest", "Back", "Legs"))
    }

    val titleLabel = when (weekOffset) {
        0 -> "THIS WEEK"
        -1 -> "LAST WEEK"
        else -> "${-weekOffset} WEEKS AGO"
    }

    val (recommendedSplit, reasonText) = if (remainingCore.isNotEmpty()) {
        remainingCore.first() to "${completedCore.size}/${prog.weeklyGoal} CORE SPLITS DONE"
    } else {
        val weekSeed = startCal.get(Calendar.YEAR) * 100 + startCal.get(Calendar.WEEK_OF_YEAR)
        val bonusSplit = coreRotation[Random(weekSeed).nextInt(coreRotation.size)]
        bonusSplit to "ALL ${prog.weeklyGoal} DONE • WEEKLY BONUS"
    }

    return WeekSummaryData(
        weekOffset = weekOffset,
        titleLabel = titleLabel,
        dateRangeLabel = "$startLabel – $endLabel",
        workouts = weekWorkouts,
        completedCoreSplits = completedCore,
        recommendedSplit = recommendedSplit,
        reasonText = reasonText,
        weeklyGoal = prog.weeklyGoal,
        fatiguedMuscles = fatigued.toList()
    )
}

fun evaluateSplitIsolatedAi(
    splitTitle: String,
    exercise: ExerciseEntry,
    history: List<CompletedWorkout>,
    stepKg: Float
): AiAdvice? {
    val sameSplitWorkouts = history.filter { it.splitTitle.equals(splitTitle, ignoreCase = true) }

    val pastSessions = sameSplitWorkouts.mapNotNull { workout ->
        val matched = workout.exercises.find {
            !it.isSkipped && it.name.equals(exercise.name, ignoreCase = true)
        }
        val validWorkSets = matched?.sets?.filter {
            !it.isWarmup && it.intensityType != "WARMUP" && parseWeightClean(it.weight, 0f) > 0f && parseRepsClean(it.reps, 0) > 0
        }.orEmpty()
        if (validWorkSets.isNotEmpty()) validWorkSets else null
    }

    if (pastSessions.isEmpty()) return null

    val prevSessionSets = pastSessions[0]
    val prevTopSet = prevSessionSets.maxByOrNull { parseWeightClean(it.weight, 0f) } ?: prevSessionSets.first()
    val prevWeight = parseWeightClean(prevTopSet.weight, 0f)
    val prevWeightStr = prevTopSet.weight

    if (pastSessions.size >= 3) {
        val vol1 = prevSessionSets.sumOf { (parseWeightClean(it.weight, 0f) * parseRepsClean(it.reps, 0)).toDouble() }
        val vol2 = pastSessions[1].sumOf { (parseWeightClean(it.weight, 0f) * parseRepsClean(it.reps, 0)).toDouble() }
        val vol3 = pastSessions[2].sumOf { (parseWeightClean(it.weight, 0f) * parseRepsClean(it.reps, 0)).toDouble() }

        if (vol1 < vol2 && vol2 < vol3 && vol1 > 0) {
            val dropW = formatKgClean(prevWeight * 0.9f)
            return AiAdvice(
                message = "⚠️ Volume dropped 3 sessions in a row. SMART DELOAD active. Use ${dropW}kg to recover.",
                isPositive = false,
                isWarning = true,
                suggestedWeight = dropW
            )
        }
    }

    val prevHad8Plus = prevSessionSets.any { parseRepsClean(it.reps, 0) >= 8 }
    val prevHad6OrLess = prevSessionSets.any { parseRepsClean(it.reps, 0) in 1..6 }

    val completedTodayWorkSets = exercise.sets.filter {
        !it.isWarmup && it.intensityType != "WARMUP" && it.isCompleted && parseRepsClean(it.reps, 0) > 0
    }

    if (completedTodayWorkSets.isNotEmpty()) {
        val currentHas8Plus = completedTodayWorkSets.any { parseRepsClean(it.reps, 0) >= 8 }
        val currentHas6OrLess = completedTodayWorkSets.any { parseRepsClean(it.reps, 0) in 1..6 }

        if (prevHad8Plus && currentHas8Plus) {
            val nextW = formatKgClean(prevWeight + stepKg)
            return AiAdvice(
                message = "⚡ 2× 8+ rep streak on $splitTitle → Increase to ${nextW}kg",
                isPositive = true,
                isWarning = false,
                suggestedWeight = nextW
            )
        }
        if (prevHad6OrLess && currentHas6OrLess) {
            val dropW = formatKgClean(prevWeight - stepKg)
            return AiAdvice(
                message = "⚠️ 2× ≤6 reps on $splitTitle → Deload to ${dropW}kg",
                isPositive = false,
                isWarning = true,
                suggestedWeight = dropW
            )
        }
    }

    if (pastSessions.size >= 2) {
        val twoAgoSets = pastSessions[1]
        val twoAgoHad8Plus = twoAgoSets.any { parseRepsClean(it.reps, 0) >= 8 }
        val twoAgoHad6OrLess = twoAgoSets.any { parseRepsClean(it.reps, 0) in 1..6 }

        if (prevHad8Plus && twoAgoHad8Plus) {
            val nextW = formatKgClean(prevWeight + stepKg)
            return AiAdvice(
                message = "⚡ 8+ reps in last 2 $splitTitle sessions → Push ${nextW}kg today",
                isPositive = true,
                isWarning = false,
                suggestedWeight = nextW
            )
        }
        if (prevHad6OrLess && twoAgoHad6OrLess) {
            val dropW = formatKgClean(prevWeight - stepKg)
            return AiAdvice(
                message = "⚠️ ≤6 reps in last 2 $splitTitle sessions → Drop to ${dropW}kg",
                isPositive = false,
                isWarning = true,
                suggestedWeight = dropW
            )
        }
    }

    return when {
        prevHad8Plus -> AiAdvice(
            message = "TARGET • Last $splitTitle: ${prevWeightStr}kg (8+ reps) • Repeat 8+ to progress",
            isPositive = false,
            isWarning = false,
            suggestedWeight = prevWeightStr
        )
        prevHad6OrLess -> AiAdvice(
            message = "TARGET • Last $splitTitle: ${prevWeightStr}kg (≤6 reps) • Aim for 7–8 reps",
            isPositive = false,
            isWarning = true,
            suggestedWeight = prevWeightStr
        )
        else -> AiAdvice(
            message = "TARGET • Last $splitTitle top set: ${prevWeightStr}kg (6–8 rep zone)",
            isPositive = false,
            isWarning = false,
            suggestedWeight = prevWeightStr
        )
    }
}

object RestAlarmScheduler {
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private var fallbackRunnable: Runnable? = null

    fun scheduleOrCancel(context: Context, endTimeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, RestAlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 777, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        try { alarmManager.cancel(pendingIntent) } catch (_: Exception) {}
        fallbackRunnable?.let { fallbackHandler.removeCallbacks(it) }

        val delayMillis = endTimeMillis - System.currentTimeMillis()
        if (endTimeMillis <= 0L || delayMillis <= 500L) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setWindow(
                    android.app.AlarmManager.RTC_WAKEUP,
                    endTimeMillis,
                    2000L,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    endTimeMillis,
                    pendingIntent
                )
            }
        } catch (_: Exception) {
            try {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, endTimeMillis, pendingIntent)
            } catch (_: Exception) {}
        }

        val task = Runnable { triggerRestFinishedAlert(context) }
        fallbackRunnable = task
        fallbackHandler.postDelayed(task, delayMillis)
    }

    @Synchronized
    fun triggerRestFinishedAlert(context: Context) {
        val currentEnd = LocalStorage.getRestEndTimeMillis(context)
        if (currentEnd <= 0L) return

        val settings = LocalStorage.loadSettings(context)
        val active = LocalStorage.loadActiveSession(context)

        LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)

        val splitTitle = active?.first ?: "Workout"
        val nextEx = active?.second?.firstOrNull { ex -> !ex.isSkipped && ex.sets.any { !it.isCompleted } }
        val nextSet = nextEx?.sets?.firstOrNull { !it.isCompleted }

        val detailLine = if (nextEx != null && nextSet != null) {
            "Start next set: ${nextEx.name} (${nextSet.weight}kg × ${nextSet.reps} reps)"
        } else {
            "Rest is over! Time to start your next working set."
        }

        if (settings.restSoundEnabled) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .build()
                } else null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.requestAudioFocus(focusRequest!!)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(
                        null,
                        AudioManager.STREAM_NOTIFICATION,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                }

                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { toneGen.release() } catch (_: Exception) {}
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.abandonAudioFocusRequest(focusRequest!!)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.abandonAudioFocus(null)
                    }
                }, 320L)
            } catch (_: Exception) {}
        }

        sendRestFinishedNotification(context, splitTitle, detailLine)
    }
}

class RestAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        RestAlarmScheduler.triggerRestFinishedAlert(context.applicationContext)
    }
}

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, resultValue)

        setContent {
            var appearance by remember { mutableStateOf(LocalStorage.loadWidgetAppearance(this)) }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF07080A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            text = "Widget Appearance",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFAFAFC)
                        )

                        val previewBg = if (appearance.isDark) Color(0xFF111318) else Color(0xFFFFFFFF)
                        val previewText = if (appearance.isDark) Color(0xFFFAFAFC) else Color(0xFF0B0F19)
                        val previewSub = if (appearance.isDark) Color(0xFF8A93A6) else Color(0xFF5B6478)
                        val previewAccent = if (appearance.isDark) Color(0xFFCCFF00) else Color(0xFFFF4500)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF1E222B))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = previewBg.copy(alpha = appearance.opacityPercent / 100f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, previewSub.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "UP NEXT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = previewAccent
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Push",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = previewText
                                    )
                                    Text(
                                        text = "Live Widget Preview (${appearance.opacityPercent}% Opacity)",
                                        fontSize = 12.sp,
                                        color = previewSub
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF111318),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Widget Theme",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFAFAFC)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf(true to "Obsidian Volt", false to "Arctic Light").forEach { (isDarkOpt, label) ->
                                        val selected = appearance.isDark == isDarkOpt
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    appearance = appearance.copy(isDark = isDarkOpt)
                                                },
                                            color = if (selected) Color(0xFFCCFF00) else Color(0xFF1E222B),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = label,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selected) Color(0xFF07080A) else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF111318),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Background Opacity",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFAFAFC)
                                    )
                                    Text(
                                        text = "${appearance.opacityPercent}%",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCCFF00)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = appearance.opacityPercent.toFloat(),
                                    onValueChange = {
                                        appearance = appearance.copy(opacityPercent = it.roundToInt())
                                    },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFCCFF00),
                                        activeTrackColor = Color(0xFFCCFF00)
                                    )
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            LocalStorage.saveWidgetAppearance(this@WidgetConfigActivity, appearance)
                            // Refresh all 3 widgets instantly when settings are saved
                            GymWidgetProvider.refreshAllWidgets(this@WidgetConfigActivity)
                            MuscleFatigueWidgetProvider.refreshAll(this@WidgetConfigActivity)
                            SquarePulseWidgetProvider.refreshAll(this@WidgetConfigActivity)

                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCCFF00))
                    ) {
                        Text(
                            text = "Apply to Widgets",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF07080A)
                        )
                    }
                }
            }
        }
    }
}

object LockScreenWorkoutController {
    private const val LIVE_WORKOUT_CHANNEL_ID = "gym_live_lockscreen_channel"
    private const val LIVE_NOTIFICATION_ID = 2002

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                LIVE_WORKOUT_CHANNEL_ID,
                "Active Workout Lock Screen Widget",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows interactive workout controls on the lock screen during an active session"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun getBroadcastPending(context: Context, action: String, reqCode: Int): android.app.PendingIntent {
        val intent = Intent(context, GymWidgetProvider::class.java).apply {
            this.action = action
            setPackage(context.packageName)
        }
        return android.app.PendingIntent.getBroadcast(
            context, reqCode, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun sync(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val active = LocalStorage.loadActiveSession(context)

            if (active == null) {
                manager.cancel(LIVE_NOTIFICATION_ID)
                return
            }

            ensureChannel(context)

            val splitName = active.first
            val activeExercises = active.second.filter { !it.isSkipped }
            val currentExercise = activeExercises.firstOrNull { ex -> ex.sets.any { !it.isCompleted } }
            val totalSetsLeft = activeExercises.sumOf { ex -> ex.sets.count { !it.isCompleted } }

            val restEndMillis = LocalStorage.getRestEndTimeMillis(context)
            val remainingMillis = restEndMillis - System.currentTimeMillis()
            val isResting = remainingMillis > 1000L

            val collapsedView = buildLockScreenRemoteViews(
                context = context,
                splitName = splitName,
                currentExercise = currentExercise,
                totalSetsLeft = totalSetsLeft,
                isResting = isResting,
                remainingMillis = remainingMillis,
                isCompactCollapsed = true
            )

            val expandedView = buildLockScreenRemoteViews(
                context = context,
                splitName = splitName,
                currentExercise = currentExercise,
                totalSetsLeft = totalSetsLeft,
                isResting = isResting,
                remainingMillis = remainingMillis,
                isCompactCollapsed = false
            )

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = android.app.PendingIntent.getActivity(
                context, 0, openAppIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, LIVE_WORKOUT_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context)
            }

            builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
                .setCategory(android.app.Notification.CATEGORY_WORKOUT)
                .setContentIntent(openAppPending)
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setCustomHeadsUpContentView(expandedView)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(android.app.Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }

            manager.notify(LIVE_NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }

    private fun buildLockScreenRemoteViews(
        context: Context,
        splitName: String,
        currentExercise: ExerciseEntry?,
        totalSetsLeft: Int,
        isResting: Boolean,
        remainingMillis: Long,
        isCompactCollapsed: Boolean
    ): android.widget.RemoteViews {
        val views = android.widget.RemoteViews(context.packageName, R.layout.gym_widget)
        val appearance = LocalStorage.loadWidgetAppearance(context)

        val bgColorInt = android.graphics.Color.parseColor(if (appearance.isDark) "#111318" else "#FFFFFF")
        val pillBgColorInt = android.graphics.Color.parseColor(if (appearance.isDark) "#1E222B" else "#E6EAF2")
        val primaryTextColor = android.graphics.Color.parseColor(if (appearance.isDark) "#FAFAFC" else "#0B0F19")
        val secondaryTextColor = android.graphics.Color.parseColor(if (appearance.isDark) "#8A93A6" else "#5B6478")
        val accentVolt = android.graphics.Color.parseColor(if (appearance.isDark) "#CCFF00" else "#FF4500")
        val accentCyan = android.graphics.Color.parseColor(if (appearance.isDark) "#00F0FF" else "#0066FF")

        views.setInt(R.id.widget_bg_image, "setColorFilter", bgColorInt)
        views.setInt(R.id.widget_bg_image, "setImageAlpha", 255)
        views.setInt(R.id.widget_weight_pill_bg, "setColorFilter", pillBgColorInt)
        views.setInt(R.id.widget_reps_pill_bg, "setColorFilter", pillBgColorInt)
        views.setInt(R.id.widget_done_pill_bg, "setColorFilter", accentVolt)

        views.setTextColor(R.id.widget_split_title, primaryTextColor)
        views.setTextColor(R.id.widget_subtitle, secondaryTextColor)
        views.setTextColor(R.id.widget_weight_value, primaryTextColor)
        views.setTextColor(R.id.widget_reps_value, primaryTextColor)
        views.setTextColor(R.id.widget_btn_weight_plus, accentVolt)
        views.setTextColor(R.id.widget_btn_reps_plus, accentVolt)
        views.setTextColor(R.id.widget_btn_weight_minus, secondaryTextColor)
        views.setTextColor(R.id.widget_btn_reps_minus, secondaryTextColor)
        views.setTextColor(R.id.widget_action_badge, accentVolt)
        views.setTextColor(R.id.widget_rest_chronometer, accentCyan)

        if (currentExercise != null && totalSetsLeft > 0) {
            val nextSetIndex = currentExercise.sets.indexOfFirst { !it.isCompleted }
            val nextSet = currentExercise.sets[nextSetIndex]
            val currentExSetsLeft = currentExercise.sets.count { !it.isCompleted }

            var workNum = 0
            for (i in 0..nextSetIndex) {
                if (!currentExercise.sets[i].isWarmup && currentExercise.sets[i].intensityType != "WARMUP") workNum++
            }
            val setLabel = if (nextSet.isWarmup || nextSet.intensityType == "WARMUP") "Warmup" else "Set $workNum"
            val cleanW = formatKgClean(parseWeightClean(nextSet.weight, 20f))
            val cleanR = parseRepsClean(nextSet.reps, 8)

            views.setTextViewText(
                R.id.widget_status_label,
                if (isResting) "${splitName.uppercase()} • RESTING"
                else "${splitName.uppercase()} • ${setLabel.uppercase()} ($currentExSetsLeft LEFT)"
            )
            views.setTextColor(R.id.widget_status_label, if (isResting) accentCyan else accentVolt)
            views.setTextViewText(R.id.widget_split_title, currentExercise.name)

            if (isResting) {
                views.setViewVisibility(R.id.widget_controls_row, View.GONE)
                views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "Up Next: $setLabel • ${cleanW}kg × $cleanR reps"
                )
            } else {
                views.setViewVisibility(R.id.widget_controls_row, View.VISIBLE)
                if (isCompactCollapsed) {
                    views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_subtitle,
                        "$setLabel (${cleanW}kg × $cleanR) • $totalSetsLeft total sets left"
                    )
                }

                views.setTextViewText(R.id.widget_weight_value, "${cleanW}kg")
                views.setTextViewText(R.id.widget_reps_value, "${cleanR}r")

                views.setOnClickPendingIntent(
                    R.id.widget_btn_weight_minus,
                    getBroadcastPending(context, GymWidgetProvider.ACTION_WEIGHT_MINUS, 20)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_weight_plus,
                    getBroadcastPending(context, GymWidgetProvider.ACTION_WEIGHT_PLUS, 21)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_reps_minus,
                    getBroadcastPending(context, GymWidgetProvider.ACTION_REPS_MINUS, 22)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_reps_plus,
                    getBroadcastPending(context, GymWidgetProvider.ACTION_REPS_PLUS, 23)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_complete_set,
                    getBroadcastPending(context, GymWidgetProvider.ACTION_COMPLETE_SET, 24)
                )
            }
        } else {
            views.setViewVisibility(R.id.widget_controls_row, View.GONE)
            views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
            views.setTextViewText(R.id.widget_status_label, "${splitName.uppercase()} • COMPLETE")
            views.setTextColor(R.id.widget_status_label, accentVolt)
            views.setTextViewText(R.id.widget_split_title, "All Sets Completed 🎉")
            views.setTextViewText(R.id.widget_subtitle, "Tap to open app & save $splitName")
        }

        if (isResting) {
            val baseElapsed = SystemClock.elapsedRealtime() + remainingMillis
            views.setViewVisibility(R.id.widget_rest_chronometer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_action_badge, View.GONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                views.setChronometerCountDown(R.id.widget_rest_chronometer, true)
            }
            views.setChronometer(
                R.id.widget_rest_chronometer,
                baseElapsed,
                "⏱ %s",
                true
            )
        } else {
            views.setViewVisibility(R.id.widget_rest_chronometer, View.GONE)
            views.setViewVisibility(R.id.widget_action_badge, View.VISIBLE)
            views.setTextViewText(R.id.widget_action_badge, "Open ›")
        }

        return views
    }
}

class GymWidgetProvider : android.appwidget.AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action in listOf(
                ACTION_WEIGHT_MINUS,
                ACTION_WEIGHT_PLUS,
                ACTION_REPS_MINUS,
                ACTION_REPS_PLUS,
                ACTION_COMPLETE_SET
            )
        ) {
            handleWidgetSetAction(context.applicationContext, action)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidgetUi(context, appWidgetManager, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgetUi(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_WEIGHT_MINUS = "com.example.gymtracker.ACTION_WEIGHT_MINUS"
        const val ACTION_WEIGHT_PLUS = "com.example.gymtracker.ACTION_WEIGHT_PLUS"
        const val ACTION_REPS_MINUS = "com.example.gymtracker.ACTION_REPS_MINUS"
        const val ACTION_REPS_PLUS = "com.example.gymtracker.ACTION_REPS_PLUS"
        const val ACTION_COMPLETE_SET = "com.example.gymtracker.ACTION_COMPLETE_SET"

        private fun handleWidgetSetAction(context: Context, action: String) {
            val restEndMillis = LocalStorage.getRestEndTimeMillis(context)
            if (restEndMillis - System.currentTimeMillis() > 1000L) {
                refreshAllWidgets(context)
                LockScreenWorkoutController.sync(context)
                return
            }

            val active = LocalStorage.loadActiveSession(context) ?: return
            val splitTitle = active.first
            val exercises = active.second.toMutableList()
            val settings = LocalStorage.loadSettings(context)

            val exIdx = exercises.indexOfFirst { ex -> !ex.isSkipped && ex.sets.any { !it.isCompleted } }
            if (exIdx == -1) return

            val currentEx = exercises[exIdx]
            val setIdx = currentEx.sets.indexOfFirst { !it.isCompleted }
            if (setIdx == -1) return

            val targetSet = currentEx.sets[setIdx]
            val updatedSets = currentEx.sets.toMutableList()

            when (action) {
                ACTION_WEIGHT_MINUS -> {
                    val cur = parseWeightClean(targetSet.weight, 20f)
                    val next = (cur - settings.weightStepKg).coerceAtLeast(0f)
                    updatedSets[setIdx] = targetSet.copy(weight = formatKgClean(next))
                }
                ACTION_WEIGHT_PLUS -> {
                    val cur = parseWeightClean(targetSet.weight, 20f)
                    val next = cur + settings.weightStepKg
                    updatedSets[setIdx] = targetSet.copy(weight = formatKgClean(next))
                }
                ACTION_REPS_MINUS -> {
                    val cur = parseRepsClean(targetSet.reps, 8)
                    val next = (cur - 1).coerceAtLeast(0)
                    updatedSets[setIdx] = targetSet.copy(reps = "$next")
                }
                ACTION_REPS_PLUS -> {
                    val cur = parseRepsClean(targetSet.reps, 8)
                    val next = cur + 1
                    updatedSets[setIdx] = targetSet.copy(reps = "$next")
                }
                ACTION_COMPLETE_SET -> {
                    updatedSets[setIdx] = targetSet.copy(isCompleted = true)
                    exercises[exIdx] = currentEx.copy(sets = updatedSets)
                    LocalStorage.saveActiveSession(context, splitTitle, exercises, fromWidget = true)

                    val setRestSec = targetSet.restSeconds.takeIf { it >= 0 } ?: settings.restTimerSeconds

                    sendSetDoneNotification(context, currentEx.name, targetSet.weight, targetSet.reps, setRestSec)

                    if (setRestSec > 0) {
                        LocalStorage.setRestEndTimeMillis(
                            context,
                            System.currentTimeMillis() + (setRestSec * 1000L),
                            fromWidget = true
                        )
                    } else {
                        LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)
                    }
                    return
                }
            }

            exercises[exIdx] = currentEx.copy(sets = updatedSets)
            LocalStorage.saveActiveSession(context, splitTitle, exercises, fromWidget = true)
        }

        private fun getActionPendingIntent(context: Context, action: String, reqCode: Int): android.app.PendingIntent {
            val intent = Intent(context, GymWidgetProvider::class.java).apply {
                this.action = action
                setPackage(context.packageName)
            }
            return android.app.PendingIntent.getBroadcast(
                context, reqCode, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun refreshAllWidgets(context: Context) {
            try {
                val appCtx = context.applicationContext
                val manager = AppWidgetManager.getInstance(appCtx)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(appCtx, GymWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateWidgetUi(appCtx, manager, id)
                }
            } catch (_: Exception) {}
        }

        private fun updateWidgetUi(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = android.widget.RemoteViews(context.packageName, R.layout.gym_widget)

            val options = manager.getAppWidgetOptions(widgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val isCompactSquare = minWidthDp < 210

            val appearance = LocalStorage.loadWidgetAppearance(context)
            val bgColorInt = android.graphics.Color.parseColor(if (appearance.isDark) "#111318" else "#FFFFFF")
            val pillBgColorInt = android.graphics.Color.parseColor(if (appearance.isDark) "#1E222B" else "#E6EAF2")
            val primaryTextColor = android.graphics.Color.parseColor(if (appearance.isDark) "#FAFAFC" else "#0B0F19")
            val secondaryTextColor = android.graphics.Color.parseColor(if (appearance.isDark) "#8A93A6" else "#5B6478")
            val accentVolt = android.graphics.Color.parseColor(if (appearance.isDark) "#CCFF00" else "#FF4500")
            val accentCyan = android.graphics.Color.parseColor(if (appearance.isDark) "#00F0FF" else "#0066FF")

            val alpha255 = ((appearance.opacityPercent.coerceIn(0, 100) / 100f) * 255f).roundToInt()
            views.setInt(R.id.widget_bg_image, "setColorFilter", bgColorInt)
            views.setInt(R.id.widget_bg_image, "setImageAlpha", alpha255)

            views.setInt(R.id.widget_weight_pill_bg, "setColorFilter", pillBgColorInt)
            views.setInt(R.id.widget_reps_pill_bg, "setColorFilter", pillBgColorInt)
            views.setInt(R.id.widget_done_pill_bg, "setColorFilter", accentVolt)

            views.setTextColor(R.id.widget_split_title, primaryTextColor)
            views.setTextColor(R.id.widget_subtitle, secondaryTextColor)
            views.setTextColor(R.id.widget_weight_value, primaryTextColor)
            views.setTextColor(R.id.widget_reps_value, primaryTextColor)
            views.setTextColor(R.id.widget_btn_weight_plus, accentVolt)
            views.setTextColor(R.id.widget_btn_reps_plus, accentVolt)
            views.setTextColor(R.id.widget_btn_weight_minus, secondaryTextColor)
            views.setTextColor(R.id.widget_btn_reps_minus, secondaryTextColor)
            views.setTextColor(R.id.widget_action_badge, accentVolt)
            views.setTextColor(R.id.widget_rest_chronometer, accentCyan)

            val active = LocalStorage.loadActiveSession(context)
            val history = LocalStorage.loadHistory(context)
            val profile = LocalStorage.loadUserProfile(context)
            val weekSummary = computeWeekSummary(history, 0, profile.splitProgram)

            if (active != null) {
                val splitName = active.first
                val activeExercises = active.second.filter { !it.isSkipped }

                val currentExercise = activeExercises.firstOrNull { ex -> ex.sets.any { !it.isCompleted } }
                val totalSetsLeft = activeExercises.sumOf { ex -> ex.sets.count { !it.isCompleted } }

                val restEndMillis = LocalStorage.getRestEndTimeMillis(context)
                val remainingMillis = restEndMillis - System.currentTimeMillis()
                val isResting = remainingMillis > 1000L

                views.setTextViewText(
                    R.id.widget_status_label,
                    if (isResting) {
                        if (isCompactSquare) "RESTING" else "${splitName.uppercase()} • RESTING"
                    } else {
                        if (isCompactSquare) splitName.uppercase() else "${splitName.uppercase()} • ACTIVE"
                    }
                )
                views.setTextColor(R.id.widget_status_label, if (isResting) accentCyan else accentVolt)

                if (currentExercise != null && totalSetsLeft > 0) {
                    val nextSetIndex = currentExercise.sets.indexOfFirst { !it.isCompleted }
                    val nextSet = currentExercise.sets[nextSetIndex]
                    val currentExSetsLeft = currentExercise.sets.count { !it.isCompleted }

                    var workNum = 0
                    for (i in 0..nextSetIndex) {
                        if (!currentExercise.sets[i].isWarmup && currentExercise.sets[i].intensityType != "WARMUP") workNum++
                    }
                    val setLabel = if (nextSet.isWarmup || nextSet.intensityType == "WARMUP") "Warmup" else "Set $workNum"
                    val cleanW = formatKgClean(parseWeightClean(nextSet.weight, 20f))
                    val cleanR = parseRepsClean(nextSet.reps, 8)

                    views.setTextViewText(R.id.widget_split_title, currentExercise.name)

                    if (isResting) {
                        views.setViewVisibility(R.id.widget_controls_row, View.GONE)
                        views.setTextViewText(
                            R.id.widget_subtitle,
                            "Resting • Next: $setLabel (${cleanW}kg × $cleanR)"
                        )
                    } else {
                        views.setViewVisibility(R.id.widget_controls_row, View.VISIBLE)
                        if (isCompactSquare) {
                            views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                        } else {
                            views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                            views.setTextViewText(
                                R.id.widget_subtitle,
                                "$setLabel • $currentExSetsLeft left ($totalSetsLeft total)"
                            )
                        }

                        views.setTextViewText(R.id.widget_weight_value, "${cleanW}kg")
                        views.setTextViewText(R.id.widget_reps_value, "${cleanR}r")

                        views.setOnClickPendingIntent(
                            R.id.widget_btn_weight_minus,
                            getActionPendingIntent(context, ACTION_WEIGHT_MINUS, 10)
                        )
                        views.setOnClickPendingIntent(
                            R.id.widget_btn_weight_plus,
                            getActionPendingIntent(context, ACTION_WEIGHT_PLUS, 11)
                        )
                        views.setOnClickPendingIntent(
                            R.id.widget_btn_reps_minus,
                            getActionPendingIntent(context, ACTION_REPS_MINUS, 12)
                        )
                        views.setOnClickPendingIntent(
                            R.id.widget_btn_reps_plus,
                            getActionPendingIntent(context, ACTION_REPS_PLUS, 13)
                        )
                        views.setOnClickPendingIntent(
                            R.id.widget_btn_complete_set,
                            getActionPendingIntent(context, ACTION_COMPLETE_SET, 14)
                        )
                    }
                } else {
                    views.setViewVisibility(R.id.widget_controls_row, View.GONE)
                    views.setTextViewText(R.id.widget_split_title, "All Sets Completed 🎉")
                    views.setTextViewText(R.id.widget_subtitle, "Tap to finish & save $splitName")
                }

                if (isResting) {
                    val baseElapsed = SystemClock.elapsedRealtime() + remainingMillis
                    views.setViewVisibility(R.id.widget_rest_chronometer, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_action_badge, View.GONE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        views.setChronometerCountDown(R.id.widget_rest_chronometer, true)
                    }
                    views.setChronometer(
                        R.id.widget_rest_chronometer,
                        baseElapsed,
                        if (isCompactSquare) "⏱ %s" else "⏱ Rest %s",
                        true
                    )
                } else {
                    views.setViewVisibility(R.id.widget_rest_chronometer, View.GONE)
                    views.setViewVisibility(R.id.widget_action_badge, View.VISIBLE)
                    views.setTextViewText(R.id.widget_action_badge, "App ›")
                }
            } else {
                views.setViewVisibility(R.id.widget_controls_row, View.GONE)
                views.setViewVisibility(R.id.widget_rest_chronometer, View.GONE)
                views.setViewVisibility(R.id.widget_action_badge, View.VISIBLE)

                views.setTextViewText(
                    R.id.widget_status_label,
                    if (isCompactSquare) "NEXT UP" else "RECOMMENDED NEXT"
                )
                views.setTextColor(R.id.widget_status_label, accentVolt)
                views.setTextViewText(R.id.widget_split_title, weekSummary.recommendedSplit)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "${weekSummary.completedCoreSplits.size} of ${weekSummary.weeklyGoal} weekly sessions done"
                )
                views.setTextViewText(R.id.widget_action_badge, "Start ›")
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = android.app.PendingIntent.getActivity(
                context, 0, openAppIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header_click_area, openAppPending)
            if (active == null) {
                views.setOnClickPendingIntent(R.id.widget_root, openAppPending)
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}