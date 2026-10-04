package com.example.gymtracker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

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
    val opacityPercent: Int = 92 // 0 to 100
)

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

fun ensureNotificationChannelCreated(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                REST_NOTIFICATION_CHANNEL_ID,
                "Rest Timer Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when your rest timer between sets finishes"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        } catch (_: Exception) {}
    }
}

fun openAppNotificationSettings(context: Context) {
    try {
        val intent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", context.packageName, null)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

fun playMusicSafeRestAlert(context: Context? = null, splitTitle: String = "Workout") {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 240)
        Handler(Looper.getMainLooper()).postDelayed({
            try { toneGen.release() } catch (_: Exception) {}
        }, 300L)
    } catch (_: Exception) {}

    if (context != null) {
        sendRestFinishedNotification(context, splitTitle)
    }
}

fun sendRestFinishedNotification(
    context: Context,
    splitTitle: String,
    detailText: String = "Rest is over! Time to start your next set."
) {
    try {
        ensureNotificationChannelCreated(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
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
        } catch (e: Exception) {
            null
        }
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
        } catch (e: Exception) {
            null
        }
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
        } catch (e: Exception) {
            null
        }
    }
}

object LocalStorage {
    private const val FILE_NAME = "gym_tracker_store_v2.json"
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingWriteRunnable: Runnable? = null

    @Volatile
    private var cachedRoot: JSONObject? = null

    private fun getRoot(context: Context): JSONObject {
        cachedRoot?.let { return it }
        val file = File(context.filesDir, FILE_NAME)
        val loaded = if (!file.exists()) {
            JSONObject()
        } else {
            try {
                JSONObject(file.readText())
            } catch (e: Exception) {
                JSONObject()
            }
        }
        cachedRoot = loaded
        return loaded
    }

    private fun saveRootDebounced(context: Context, root: JSONObject, immediate: Boolean = false) {
        cachedRoot = root
        val appCtx = context.applicationContext
        pendingWriteRunnable?.let { mainHandler.removeCallbacks(it) }

        // Keep both the Home Screen Widget and Lock Screen Widget synced in real time
        GymWidgetProvider.refreshAllWidgets(appCtx)
        LockScreenWorkoutController.sync(appCtx)

        val task = Runnable {
            ioExecutor.execute {
                try {
                    val snapshot = synchronized(this) { root.toString() }
                    File(appCtx.filesDir, FILE_NAME).writeText(snapshot)
                } catch (_: Exception) {}
            }
        }

        if (immediate) {
            task.run()
        } else {
            pendingWriteRunnable = task
            mainHandler.postDelayed(task, 350L)
        }
    }

    fun loadWidgetAppearance(context: Context): WidgetAppearance {
        val root = getRoot(context)
        val obj = root.optJSONObject("widgetAppearance") ?: return WidgetAppearance()
        return WidgetAppearance(
            isDark = obj.optBoolean("isDark", true),
            opacityPercent = obj.optInt("opacityPercent", 92).coerceIn(0, 100)
        )
    }

    fun saveWidgetAppearance(context: Context, appearance: WidgetAppearance) {
        val root = getRoot(context)
        val obj = JSONObject().apply {
            put("isDark", appearance.isDark)
            put("opacityPercent", appearance.opacityPercent.coerceIn(0, 100))
        }
        synchronized(this) { root.put("widgetAppearance", obj) }
        saveRootDebounced(context, root, immediate = true)
    }

    fun setRestEndTimeMillis(context: Context, endTimeMillis: Long) {
        val root = getRoot(context)
        synchronized(this) {
            val activeObj = root.optJSONObject("activeSession") ?: return
            activeObj.put("restEndTimeMillis", endTimeMillis)
        }
        saveRootDebounced(context, root, immediate = true)
        RestAlarmScheduler.scheduleOrCancel(context.applicationContext, endTimeMillis)
    }

    fun getRestEndTimeMillis(context: Context): Long {
        val root = getRoot(context)
        return root.optJSONObject("activeSession")?.optLong("restEndTimeMillis", 0L) ?: 0L
    }

    fun exportJsonBackupString(context: Context): String {
        val root = getRoot(context)
        return synchronized(this) { root.toString(2) }
    }

    fun importJsonBackupString(context: Context, jsonContent: String): Boolean {
        return try {
            val parsed = JSONObject(jsonContent)
            synchronized(this) {
                cachedRoot = parsed
                File(context.filesDir, FILE_NAME).writeText(parsed.toString())
            }
            val appCtx = context.applicationContext
            GymWidgetProvider.refreshAllWidgets(appCtx)
            LockScreenWorkoutController.sync(appCtx)
            true
        } catch (e: Exception) {
            false
        }
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
            restSoundEnabled = obj.optBoolean("restSoundEnabled", true)
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
        }
        synchronized(this) { root.put("settings", obj) }
        saveRootDebounced(context, root, immediate = true)
    }

    fun getExerciseNote(context: Context, exerciseName: String): String {
        val root = getRoot(context)
        val notesObj = root.optJSONObject("exerciseNotes") ?: return ""
        return notesObj.optString(exerciseName.lowercase(Locale.US), "")
    }

    fun saveExerciseNote(context: Context, exerciseName: String, note: String) {
        val root = getRoot(context)
        synchronized(this) {
            val notesObj = root.optJSONObject("exerciseNotes") ?: JSONObject()
            if (note.isBlank()) {
                notesObj.remove(exerciseName.lowercase(Locale.US))
            } else {
                notesObj.put(exerciseName.lowercase(Locale.US), note.trim())
            }
            root.put("exerciseNotes", notesObj)
        }
        saveRootDebounced(context, root, immediate = true)
    }

    private fun defaultTemplatesForSplit(splitTitle: String): List<Pair<String, String>> {
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
            "Arms & Shoulders" -> listOf(
                "Seated Dumbbell Shoulder Press" to "Shoulders",
                "Cable Lateral Raises" to "Side Delts",
                "Incline Dumbbell Curl" to "Biceps",
                "Overhead Cable Tricep Extension" to "Triceps",
                "Hammer Curls" to "Brachialis"
            )
            "Chest & Back" -> listOf(
                "Incline Dumbbell Press" to "Upper Chest",
                "Weighted Pull-Ups / Lat Pulldown" to "Lats",
                "Bench Press / Chest Press" to "Mid Chest",
                "Chest-Supported Row" to "Upper Back"
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
        synchronized(this) {
            templatesObj.put(splitTitle, arr)
            root.put("splitTemplates", templatesObj)
        }
        saveRootDebounced(context, root, immediate = true)
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

    fun saveActiveSession(context: Context, splitTitle: String?, exercises: List<ExerciseEntry>) {
        val root = getRoot(context)
        synchronized(this) {
            if (splitTitle == null) {
                root.remove("activeSession")
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
        }
        if (splitTitle == null) {
            RestAlarmScheduler.scheduleOrCancel(context.applicationContext, 0L)
        }
        saveRootDebounced(context, root, immediate = splitTitle == null)
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
        synchronized(this) {
            val historyArr = root.optJSONArray("history") ?: JSONArray()
            val obj = JSONObject().apply {
                put("id", workout.id)
                put("splitTitle", workout.splitTitle)
                put("dateKey", workout.dateKey)
                put("displayDate", workout.displayDate)
                put("durationSeconds", workout.durationSeconds)
                put("photoPath", workout.photoPath ?: "")
                put("exercises", serializeExercises(workout.exercises))
            }
            historyArr.put(obj)
            root.put("history", historyArr)
            root.remove("activeSession")
        }
        RestAlarmScheduler.scheduleOrCancel(context.applicationContext, 0L)
        saveRootDebounced(context, root, immediate = true)
    }

    fun updateCompletedWorkout(context: Context, updatedWorkout: CompletedWorkout) {
        val root = getRoot(context)
        synchronized(this) {
            val arr = root.optJSONArray("history") ?: return
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optLong("id") == updatedWorkout.id) {
                    val replaced = JSONObject().apply {
                        put("id", updatedWorkout.id)
                        put("splitTitle", updatedWorkout.splitTitle)
                        put("dateKey", updatedWorkout.dateKey)
                        put("displayDate", updatedWorkout.displayDate)
                        put("durationSeconds", updatedWorkout.durationSeconds)
                        put("photoPath", updatedWorkout.photoPath ?: "")
                        put("exercises", serializeExercises(updatedWorkout.exercises))
                    }
                    newArr.put(replaced)
                } else {
                    newArr.put(obj)
                }
            }
            root.put("history", newArr)
        }
        saveRootDebounced(context, root, immediate = true)
    }

    fun deleteWorkoutFromHistory(context: Context, workoutId: Long) {
        val root = getRoot(context)
        synchronized(this) {
            val arr = root.optJSONArray("history") ?: return
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optLong("id") != workoutId) {
                    newArr.put(obj)
                }
            }
            root.put("history", newArr)
        }
        saveRootDebounced(context, root, immediate = true)
    }

    fun loadHistory(context: Context): List<CompletedWorkout> {
        val root = getRoot(context)
        val arr = root.optJSONArray("history") ?: return emptyList()
        val list = ArrayList<CompletedWorkout>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val rawPhoto = obj.optString("photoPath", "")
            list.add(
                CompletedWorkout(
                    id = obj.optLong("id"),
                    splitTitle = obj.optString("splitTitle"),
                    dateKey = obj.optString("dateKey"),
                    displayDate = obj.optString("displayDate"),
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    photoPath = rawPhoto.ifBlank { null },
                    exercises = deserializeExercises(obj.optJSONArray("exercises"))
                )
            )
        }
        return list.sortedByDescending { it.id }
    }

    private fun serializeExercises(exercises: List<ExerciseEntry>): JSONArray {
        val exArray = JSONArray()
        for (i in exercises.indices) {
            val ex = exercises[i]
            val exObj = JSONObject()
            exObj.put("name", ex.name)
            exObj.put("targetMuscle", ex.targetMuscle)
            val setsArr = JSONArray()
            for (j in ex.sets.indices) {
                val s = ex.sets[j]
                val sObj = JSONObject()
                sObj.put("setNumber", s.setNumber)
                sObj.put("weight", s.weight)
                sObj.put("reps", s.reps)
                sObj.put("isWarmup", s.isWarmup)
                sObj.put("isCompleted", s.isCompleted)
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
            val setsArr = exObj.optJSONArray("sets") ?: JSONArray()
            val sets = ArrayList<SetEntry>(setsArr.length())
            for (j in 0 until setsArr.length()) {
                val sObj = setsArr.getJSONObject(j)
                sets.add(
                    SetEntry(
                        setNumber = sObj.optInt("setNumber", j + 1),
                        weight = sObj.optString("weight", "20"),
                        reps = sObj.optString("reps", "8"),
                        isWarmup = sObj.optBoolean("isWarmup", false),
                        isCompleted = sObj.optBoolean("isCompleted", false)
                    )
                )
            }
            list.add(
                ExerciseEntry(
                    name = exObj.optString("name", ""),
                    targetMuscle = exObj.optString("targetMuscle", ""),
                    sets = sets
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
        val matched = workout.exercises.find { it.name.equals(exerciseName, ignoreCase = true) } ?: continue
        val workSets = matched.sets.filter {
            !it.isWarmup && (it.weight.toFloatOrNull() ?: 0f) > 0f && (it.reps.toIntOrNull() ?: 0) > 0
        }
        if (workSets.isNotEmpty()) {
            val sessionTopWeight = workSets.maxOfOrNull { it.weight.toFloatOrNull() ?: 0f } ?: 0f
            val shortDate = workout.displayDate.substringBefore(",")
            if (recentLogs.size < 5) {
                val summary = workSets.joinToString(" • ") { "${it.weight}kg × ${it.reps}" }
                recentLogs.add("${workout.splitTitle} ($shortDate)" to summary)
                recentChartPoints.add(shortDate to sessionTopWeight)
            }
            for (s in workSets) {
                val w = s.weight.toFloatOrNull() ?: 0f
                val r = s.reps.toIntOrNull() ?: 0
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
    if (setEntry.isWarmup || !setEntry.isCompleted) return false
    val w = setEntry.weight.toFloatOrNull() ?: return false
    val r = setEntry.reps.toIntOrNull() ?: return false
    if (w <= 0f || r <= 0 || records.bestEstimated1Rm <= 0) return false
    val current1Rm = calculateEstimated1Rm(w, r).roundToInt()
    return current1Rm > records.bestEstimated1Rm || w > records.maxWeightKg
}

fun computeWeekSummary(history: List<CompletedWorkout>, weekOffset: Int = 0): WeekSummaryData {
    val coreRotation = listOf("Push", "Pull", "Arms & Shoulders")

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

    val titleLabel = when (weekOffset) {
        0 -> "THIS WEEK"
        -1 -> "LAST WEEK"
        else -> "${-weekOffset} WEEKS AGO"
    }

    val (recommendedSplit, reasonText) = if (remainingCore.isNotEmpty()) {
        remainingCore.first() to "${completedCore.size}/3 CORE SPLITS DONE"
    } else {
        val weekSeed = startCal.get(Calendar.YEAR) * 100 + startCal.get(Calendar.WEEK_OF_YEAR)
        val bonusSplit = coreRotation[Random(weekSeed).nextInt(coreRotation.size)]
        bonusSplit to "ALL 3 DONE • WEEKLY BONUS"
    }

    return WeekSummaryData(
        weekOffset = weekOffset,
        titleLabel = titleLabel,
        dateRangeLabel = "$startLabel – $endLabel",
        workouts = weekWorkouts,
        completedCoreSplits = completedCore,
        recommendedSplit = recommendedSplit,
        reasonText = reasonText
    )
}

fun evaluateSplitIsolatedAi(
    splitTitle: String,
    exercise: ExerciseEntry,
    history: List<CompletedWorkout>,
    stepKg: Float
): AiAdvice? {
    val sameSplitWorkouts = history.filter { it.splitTitle.equals(splitTitle, ignoreCase = true) }

    val pastExerciseSessions = sameSplitWorkouts.mapNotNull { workout ->
        val matched = workout.exercises.find { it.name.equals(exercise.name, ignoreCase = true) }
        val validWorkSets = matched?.sets?.filter {
            !it.isWarmup && (it.weight.toFloatOrNull() ?: 0f) > 0f && (it.reps.toIntOrNull() ?: 0) > 0
        }.orEmpty()
        if (validWorkSets.isNotEmpty()) validWorkSets else null
    }

    if (pastExerciseSessions.isEmpty()) return null

    val prevSessionSets = pastExerciseSessions[0]
    val prevTopSet = prevSessionSets.maxByOrNull { it.weight.toFloatOrNull() ?: 0f } ?: prevSessionSets.first()
    val prevWeight = prevTopSet.weight.toFloatOrNull() ?: 0f
    val prevWeightStr = prevTopSet.weight

    val prevHad8Plus = prevSessionSets.any { (it.reps.toIntOrNull() ?: 0) >= 8 }
    val prevHad6OrLess = prevSessionSets.any { (it.reps.toIntOrNull() ?: 0) in 1..6 }

    val completedTodayWorkSets = exercise.sets.filter {
        !it.isWarmup && it.isCompleted && (it.reps.toIntOrNull() ?: 0) > 0
    }
    val currentHas8Plus = completedTodayWorkSets.any { (it.reps.toIntOrNull() ?: 0) >= 8 }
    val currentHas6OrLess = completedTodayWorkSets.any { (it.reps.toIntOrNull() ?: 0) in 1..6 }

    fun formatKg(value: Float): String {
        val clamped = value.coerceAtLeast(0f)
        return if (clamped % 1f == 0f) "${clamped.toInt()}" else "$clamped"
    }

    if (completedTodayWorkSets.isNotEmpty()) {
        if (prevHad8Plus && currentHas8Plus) {
            val nextW = formatKg(prevWeight + stepKg)
            return AiAdvice(
                message = "2× 8+ rep streak on $splitTitle → Up weight to ${nextW}kg",
                isPositive = true,
                isWarning = false,
                suggestedWeight = nextW
            )
        }
        if (prevHad6OrLess && currentHas6OrLess) {
            val dropW = formatKg(prevWeight - stepKg)
            return AiAdvice(
                message = "2× ≤6 reps on $splitTitle → Drop weight to ${dropW}kg",
                isPositive = false,
                isWarning = true,
                suggestedWeight = dropW
            )
        }
    }

    if (pastExerciseSessions.size >= 2) {
        val twoAgoSets = pastExerciseSessions[1]
        val twoAgoHad8Plus = twoAgoSets.any { (it.reps.toIntOrNull() ?: 0) >= 8 }
        val twoAgoHad6OrLess = twoAgoSets.any { (it.reps.toIntOrNull() ?: 0) in 1..6 }

        if (prevHad8Plus && twoAgoHad8Plus) {
            val nextW = formatKg(prevWeight + stepKg)
            return AiAdvice(
                message = "8+ reps in last 2 $splitTitle sessions → Up to ${nextW}kg today",
                isPositive = true,
                isWarning = false,
                suggestedWeight = nextW
            )
        }
        if (prevHad6OrLess && twoAgoHad6OrLess) {
            val dropW = formatKg(prevWeight - stepKg)
            return AiAdvice(
                message = "≤6 reps in last 2 $splitTitle sessions → Drop to ${dropW}kg today",
                isPositive = false,
                isWarning = true,
                suggestedWeight = dropW
            )
        }
    }

    return when {
        prevHad8Plus -> AiAdvice(
            message = "Last $splitTitle: ${prevWeightStr}kg (8+ reps) • Hit 8+ again to move up",
            isPositive = false,
            isWarning = false,
            suggestedWeight = prevWeightStr
        )
        prevHad6OrLess -> AiAdvice(
            message = "Last $splitTitle: ${prevWeightStr}kg (≤6 reps) • Push for 7–8 today",
            isPositive = false,
            isWarning = true,
            suggestedWeight = prevWeightStr
        )
        else -> AiAdvice(
            message = "Last $splitTitle top set: ${prevWeightStr}kg (6–8 rep zone)",
            isPositive = false,
            isWarning = false,
            suggestedWeight = prevWeightStr
        )
    }
}

// --- System-Level Rest Timer Scheduler (Works With Screen Off, In Background & From Widget) ---
object RestAlarmScheduler {
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private var fallbackRunnable: Runnable? = null

    fun scheduleOrCancel(context: Context, endTimeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, RestAlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            777,
            intent,
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

        val task = Runnable {
            triggerRestFinishedAlert(context)
        }
        fallbackRunnable = task
        fallbackHandler.postDelayed(task, delayMillis)
    }

    @Synchronized
    fun triggerRestFinishedAlert(context: Context) {
        val currentEnd = LocalStorage.getRestEndTimeMillis(context)
        if (currentEnd <= 0L) return

        val settings = LocalStorage.loadSettings(context)
        val active = LocalStorage.loadActiveSession(context)

        // Clearing restEndTimeMillis automatically refreshes both Home Screen & Lock Screen widgets!
        LocalStorage.setRestEndTimeMillis(context, 0L)

        val splitTitle = active?.first ?: "Workout"
        val nextEx = active?.second?.firstOrNull { ex -> ex.sets.any { !it.isCompleted } }
        val nextSet = nextEx?.sets?.firstOrNull { !it.isCompleted }

        val detailLine = if (nextEx != null && nextSet != null) {
            "Start next set: ${nextEx.name} (${nextSet.weight}kg × ${nextSet.reps} reps)"
        } else {
            "Rest is over! Time to start your next working set."
        }

        if (settings.restSoundEnabled) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { toneGen.release() } catch (_: Exception) {}
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

// --- Long-Press Home Screen Widget Settings Activity (Dark/Light Mode & Opacity Slider) ---
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
                color = Color(0xFF0F1115)
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
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF3F4F6)
                        )

                        val previewBg = if (appearance.isDark) Color(0xFF181B22) else Color(0xFFFFFFFF)
                        val previewText = if (appearance.isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
                        val previewSub = if (appearance.isDark) Color(0xFF8E95A5) else Color(0xFF6B7280)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF283040))
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
                                        text = "RECOMMENDED NEXT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3B82F6)
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
                            color = Color(0xFF181B22),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Widget Theme",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF3F4F6)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf(true to "Dark Mode", false to "Light Mode").forEach { (isDarkOpt, label) ->
                                        val selected = appearance.isDark == isDarkOpt
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    appearance = appearance.copy(isDark = isDarkOpt)
                                                },
                                            color = if (selected) Color(0xFF3B82F6) else Color(0xFF222630),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = label,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF181B22),
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
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF3F4F6)
                                    )
                                    Text(
                                        text = "${appearance.opacityPercent}%",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3B82F6)
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
                                        thumbColor = Color(0xFF3B82F6),
                                        activeTrackColor = Color(0xFF3B82F6)
                                    )
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            LocalStorage.saveWidgetAppearance(this@WidgetConfigActivity, appearance)
                            GymWidgetProvider.refreshAllWidgets(this@WidgetConfigActivity)
                            LockScreenWorkoutController.sync(this@WidgetConfigActivity)
                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Text(
                            text = "Apply to Widget",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// --- Non-Dismissible S26 Ultra Lock Screen Live Workout Widget ---
object LockScreenWorkoutController {
    private const val LIVE_WORKOUT_CHANNEL_ID = "gym_live_lockscreen_channel"
    private const val LIVE_NOTIFICATION_ID = 2002

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                LIVE_WORKOUT_CHANNEL_ID,
                "Active Workout Lock Screen Widget",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows interactive workout controls on the lock screen during an active session"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun getBroadcastPending(context: Context, action: String, reqCode: Int): android.app.PendingIntent {
        val intent = Intent(context, GymWidgetProvider::class.java).apply {
            this.action = action
        }
        return android.app.PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
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
            val exercises = active.second
            val currentExercise = exercises.firstOrNull { ex -> ex.sets.any { !it.isCompleted } }
            val totalSetsLeft = exercises.sumOf { ex -> ex.sets.count { !it.isCompleted } }

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
                showControls = false
            )

            val expandedView = buildLockScreenRemoteViews(
                context = context,
                splitName = splitName,
                currentExercise = currentExercise,
                totalSetsLeft = totalSetsLeft,
                isResting = isResting,
                remainingMillis = remainingMillis,
                showControls = true
            )

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = android.app.PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
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
        showControls: Boolean
    ): android.widget.RemoteViews {
        val views = android.widget.RemoteViews(context.packageName, R.layout.gym_widget)
        val appearance = LocalStorage.loadWidgetAppearance(context)

        val bgColorInt = if (appearance.isDark) {
            android.graphics.Color.parseColor("#181B22")
        } else {
            android.graphics.Color.parseColor("#FFFFFF")
        }
        val pillBgColorInt = if (appearance.isDark) {
            android.graphics.Color.parseColor("#242936")
        } else {
            android.graphics.Color.parseColor("#EEF1F6")
        }
        val primaryTextColor = if (appearance.isDark) {
            android.graphics.Color.parseColor("#F3F4F6")
        } else {
            android.graphics.Color.parseColor("#111827")
        }
        val secondaryTextColor = if (appearance.isDark) {
            android.graphics.Color.parseColor("#8E95A5")
        } else {
            android.graphics.Color.parseColor("#6B7280")
        }
        val accentBlue = android.graphics.Color.parseColor(if (appearance.isDark) "#3B82F6" else "#2563EB")
        val accentGreen = android.graphics.Color.parseColor(if (appearance.isDark) "#10B981" else "#059669")

        views.setInt(R.id.widget_bg_image, "setColorFilter", bgColorInt)
        views.setInt(R.id.widget_bg_image, "setImageAlpha", 255)
        views.setInt(R.id.widget_weight_pill_bg, "setColorFilter", pillBgColorInt)
        views.setInt(R.id.widget_reps_pill_bg, "setColorFilter", pillBgColorInt)
        views.setInt(R.id.widget_done_pill_bg, "setColorFilter", accentGreen)

        views.setTextColor(R.id.widget_split_title, primaryTextColor)
        views.setTextColor(R.id.widget_subtitle, secondaryTextColor)
        views.setTextColor(R.id.widget_weight_value, primaryTextColor)
        views.setTextColor(R.id.widget_reps_value, primaryTextColor)
        views.setTextColor(R.id.widget_btn_weight_plus, accentBlue)
        views.setTextColor(R.id.widget_btn_reps_plus, accentBlue)
        views.setTextColor(R.id.widget_btn_weight_minus, secondaryTextColor)
        views.setTextColor(R.id.widget_btn_reps_minus, secondaryTextColor)
        views.setTextColor(R.id.widget_action_badge, accentBlue)
        views.setTextColor(R.id.widget_rest_chronometer, accentGreen)

        views.setTextViewText(
            R.id.widget_status_label,
            if (isResting) "${splitName.uppercase()} • RESTING" else "${splitName.uppercase()} • LIVE WORKOUT"
        )
        views.setTextColor(R.id.widget_status_label, accentGreen)

        if (currentExercise != null && totalSetsLeft > 0) {
            val nextSetIndex = currentExercise.sets.indexOfFirst { !it.isCompleted }
            val nextSet = currentExercise.sets[nextSetIndex]
            val currentExSetsLeft = currentExercise.sets.count { !it.isCompleted }

            var workNum = 0
            for (i in 0..nextSetIndex) {
                if (!currentExercise.sets[i].isWarmup) workNum++
            }
            val setLabel = if (nextSet.isWarmup) "Warmup Set" else "Work Set $workNum"

            views.setTextViewText(R.id.widget_split_title, currentExercise.name)

            if (isResting) {
                views.setViewVisibility(R.id.widget_controls_row, View.GONE)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "Resting • Next: $setLabel (${nextSet.weight}kg × ${nextSet.reps})"
                )
            } else {
                views.setViewVisibility(
                    R.id.widget_controls_row,
                    if (showControls) View.VISIBLE else View.GONE
                )
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "$setLabel (${nextSet.weight}kg × ${nextSet.reps}) • $currentExSetsLeft left ($totalSetsLeft total)"
                )
                views.setTextViewText(R.id.widget_weight_value, "${nextSet.weight}kg")
                views.setTextViewText(R.id.widget_reps_value, "${nextSet.reps} reps")

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
                "⏱ Rest %s",
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

// --- Responsive 2x2 Box / 4x2 Rectangle Home Screen Widget Provider ---
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
            handleWidgetSetAction(context, action)
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
            // Block logging or changing sets while the rest timer is still active
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

            val exIdx = exercises.indexOfFirst { ex -> ex.sets.any { !it.isCompleted } }
            if (exIdx == -1) return

            val currentEx = exercises[exIdx]
            val setIdx = currentEx.sets.indexOfFirst { !it.isCompleted }
            if (setIdx == -1) return

            val targetSet = currentEx.sets[setIdx]
            val updatedSets = currentEx.sets.toMutableList()

            when (action) {
                ACTION_WEIGHT_MINUS -> {
                    val cur = targetSet.weight.toFloatOrNull() ?: 20f
                    val next = (cur - settings.weightStepKg).coerceAtLeast(0f)
                    val formatted = if (next % 1f == 0f) "${next.toInt()}" else "$next"
                    updatedSets[setIdx] = targetSet.copy(weight = formatted)
                }
                ACTION_WEIGHT_PLUS -> {
                    val cur = targetSet.weight.toFloatOrNull() ?: 20f
                    val next = cur + settings.weightStepKg
                    val formatted = if (next % 1f == 0f) "${next.toInt()}" else "$next"
                    updatedSets[setIdx] = targetSet.copy(weight = formatted)
                }
                ACTION_REPS_MINUS -> {
                    val cur = targetSet.reps.toIntOrNull() ?: 8
                    val next = (cur - 1).coerceAtLeast(0)
                    updatedSets[setIdx] = targetSet.copy(reps = "$next")
                }
                ACTION_REPS_PLUS -> {
                    val cur = targetSet.reps.toIntOrNull() ?: 7
                    val next = cur + 1
                    updatedSets[setIdx] = targetSet.copy(reps = "$next")
                }
                ACTION_COMPLETE_SET -> {
                    updatedSets[setIdx] = targetSet.copy(isCompleted = true)
                    exercises[exIdx] = currentEx.copy(sets = updatedSets)
                    LocalStorage.saveActiveSession(context, splitTitle, exercises)

                    if (settings.restTimerSeconds > 0) {
                        LocalStorage.setRestEndTimeMillis(
                            context,
                            System.currentTimeMillis() + (settings.restTimerSeconds * 1000L)
                        )
                    } else {
                        refreshAllWidgets(context)
                        LockScreenWorkoutController.sync(context)
                    }
                    return
                }
            }

            exercises[exIdx] = currentEx.copy(sets = updatedSets)
            LocalStorage.saveActiveSession(context, splitTitle, exercises)
            refreshAllWidgets(context)
            LockScreenWorkoutController.sync(context)
        }

        private fun getActionPendingIntent(context: Context, action: String, reqCode: Int): android.app.PendingIntent {
            val intent = Intent(context, GymWidgetProvider::class.java).apply {
                this.action = action
            }
            return android.app.PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun requestPinWidgetToHomeScreen(context: Context) {
            requestPinWidget(context)
        }

        fun requestPinWidget(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = android.content.ComponentName(context, GymWidgetProvider::class.java)
                    if (manager.isRequestPinAppWidgetSupported) {
                        manager.requestPinAppWidget(provider, null, null)
                    }
                }
            } catch (_: Exception) {}
        }

        fun refreshAllWidgets(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context, GymWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateWidgetUi(context, manager, id)
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
            val isCompactSquare = minWidthDp < 185

            val appearance = LocalStorage.loadWidgetAppearance(context)
            val bgColorInt = if (appearance.isDark) {
                android.graphics.Color.parseColor("#181B22")
            } else {
                android.graphics.Color.parseColor("#FFFFFF")
            }
            val pillBgColorInt = if (appearance.isDark) {
                android.graphics.Color.parseColor("#242936")
            } else {
                android.graphics.Color.parseColor("#EEF1F6")
            }
            val primaryTextColor = if (appearance.isDark) {
                android.graphics.Color.parseColor("#F3F4F6")
            } else {
                android.graphics.Color.parseColor("#111827")
            }
            val secondaryTextColor = if (appearance.isDark) {
                android.graphics.Color.parseColor("#8E95A5")
            } else {
                android.graphics.Color.parseColor("#6B7280")
            }
            val accentBlue = android.graphics.Color.parseColor(if (appearance.isDark) "#3B82F6" else "#2563EB")
            val accentGreen = android.graphics.Color.parseColor(if (appearance.isDark) "#10B981" else "#059669")

            val alpha255 = ((appearance.opacityPercent.coerceIn(0, 100) / 100f) * 255f).roundToInt()
            views.setInt(R.id.widget_bg_image, "setColorFilter", bgColorInt)
            views.setInt(R.id.widget_bg_image, "setImageAlpha", alpha255)

            views.setInt(R.id.widget_weight_pill_bg, "setColorFilter", pillBgColorInt)
            views.setInt(R.id.widget_reps_pill_bg, "setColorFilter", pillBgColorInt)
            views.setInt(R.id.widget_done_pill_bg, "setColorFilter", accentGreen)

            views.setTextColor(R.id.widget_split_title, primaryTextColor)
            views.setTextColor(R.id.widget_subtitle, secondaryTextColor)
            views.setTextColor(R.id.widget_weight_value, primaryTextColor)
            views.setTextColor(R.id.widget_reps_value, primaryTextColor)
            views.setTextColor(R.id.widget_btn_weight_plus, accentBlue)
            views.setTextColor(R.id.widget_btn_reps_plus, accentBlue)
            views.setTextColor(R.id.widget_btn_weight_minus, secondaryTextColor)
            views.setTextColor(R.id.widget_btn_reps_minus, secondaryTextColor)
            views.setTextColor(R.id.widget_action_badge, accentBlue)
            views.setTextColor(R.id.widget_rest_chronometer, accentGreen)

            val active = LocalStorage.loadActiveSession(context)
            val history = LocalStorage.loadHistory(context)
            val weekSummary = computeWeekSummary(history, 0)

            if (active != null) {
                val splitName = active.first
                val exercises = active.second

                val currentExercise = exercises.firstOrNull { ex -> ex.sets.any { !it.isCompleted } }
                val totalSetsLeft = exercises.sumOf { ex -> ex.sets.count { !it.isCompleted } }

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
                views.setTextColor(R.id.widget_status_label, accentGreen)

                if (currentExercise != null && totalSetsLeft > 0) {
                    val nextSetIndex = currentExercise.sets.indexOfFirst { !it.isCompleted }
                    val nextSet = currentExercise.sets[nextSetIndex]
                    val currentExSetsLeft = currentExercise.sets.count { !it.isCompleted }

                    var workNum = 0
                    for (i in 0..nextSetIndex) {
                        if (!currentExercise.sets[i].isWarmup) workNum++
                    }
                    val setLabel = if (nextSet.isWarmup) "Warmup Set" else "Work Set $workNum"

                    views.setTextViewText(R.id.widget_split_title, currentExercise.name)

                    if (isResting) {
                        views.setViewVisibility(R.id.widget_controls_row, View.GONE)
                        views.setTextViewText(
                            R.id.widget_subtitle,
                            "Resting • Next: $setLabel (${nextSet.weight}kg × ${nextSet.reps})"
                        )
                    } else {
                        views.setViewVisibility(R.id.widget_controls_row, View.VISIBLE)
                        views.setTextViewText(
                            R.id.widget_subtitle,
                            "$setLabel • $currentExSetsLeft left on this ($totalSetsLeft total)"
                        )
                        views.setTextViewText(
                            R.id.widget_weight_value,
                            if (isCompactSquare) nextSet.weight else "${nextSet.weight}kg"
                        )
                        views.setTextViewText(
                            R.id.widget_reps_value,
                            if (isCompactSquare) nextSet.reps else "${nextSet.reps} reps"
                        )

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
                views.setTextColor(R.id.widget_status_label, accentBlue)
                views.setTextViewText(R.id.widget_split_title, weekSummary.recommendedSplit)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "${weekSummary.completedCoreSplits.size} of 3 weekly sessions completed"
                )
                views.setTextViewText(R.id.widget_action_badge, "Start ›")
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = android.app.PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header_click_area, openAppPending)
            if (active == null) {
                views.setOnClickPendingIntent(R.id.widget_root, openAppPending)
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}