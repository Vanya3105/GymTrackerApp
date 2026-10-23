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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

const val ACTION_SESSION_SYNC = "com.example.gymtracker.ACTION_SESSION_SYNC"

@Immutable
data class ExerciseRecords(
    val maxWeightKg: Float,
    /** Rounded to 0.5 kg, like every estimated 1RM the app shows. */
    val bestEstimated1Rm: Float,
    /** The set behind the best estimated 1RM, or null when nothing is logged. */
    val bestSet: SetEntry?,
    /** Up to five most recent sessions, newest first: label to work sets. */
    val recentHistory: List<Pair<String, List<SetEntry>>>,
    /** Oldest first: each session's [sessionValue]. */
    val chartPoints: List<Pair<String, Float>>,
    val type: ExerciseType = ExerciseType.WEIGHT_REPS,
    val maxReps: Int = 0,
    val maxSeconds: Int = 0,
    val maxDistanceM: Float = 0f,
    /** Least assistance ever used (assisted exercises); null until one is logged. */
    val minAssistKg: Float? = null
)

/**
 * The single number that sums up a session for [type]: top weight (or added weight), most reps,
 * least assistance, longest hold, or longest distance.
 */
fun sessionValue(type: ExerciseType, workSets: List<SetEntry>): Float = when (type) {
    ExerciseType.WEIGHT_REPS, ExerciseType.WEIGHTED_BODYWEIGHT -> workSets.maxOf { it.weightKg }
    ExerciseType.BODYWEIGHT -> workSets.maxOf { it.repCount }.toFloat()
    ExerciseType.ASSISTED -> workSets.minOf { it.weightKg }
    ExerciseType.DURATION -> workSets.maxOf { it.secondCount }.toFloat()
    ExerciseType.DISTANCE_TIME -> workSets.maxOf { it.distanceMeters }
}

/** The set that best represents [sets] for [type] (heaviest, most reps, least help, longest). */
fun bestSetOf(type: ExerciseType, sets: List<SetEntry>): SetEntry? = when (type) {
    ExerciseType.WEIGHT_REPS, ExerciseType.WEIGHTED_BODYWEIGHT -> sets.maxWithOrNull(compareBy({ it.weightKg }, { it.repCount }))
    ExerciseType.BODYWEIGHT -> sets.maxByOrNull { it.repCount }
    ExerciseType.ASSISTED -> sets.minWithOrNull(compareBy({ it.weightKg }, { -it.repCount }))
    ExerciseType.DURATION -> sets.maxByOrNull { it.secondCount }
    ExerciseType.DISTANCE_TIME -> sets.maxWithOrNull(compareBy({ it.distanceMeters }, { it.secondCount }))
}

/** A [sessionValue] in words: "80 kg", "15 reps", "−20 kg", "1:30", "5 km". */
fun formatSessionValue(type: ExerciseType, value: Float): String = when (type) {
    ExerciseType.WEIGHT_REPS -> formatWeightKg(value)
    ExerciseType.WEIGHTED_BODYWEIGHT -> "+${formatWeightKg(value)}"
    ExerciseType.BODYWEIGHT -> if (value.toInt() == 1) "1 rep" else "${value.toInt()} reps"
    ExerciseType.ASSISTED -> if (value > 0f) "−${formatWeightKg(value)}" else "No help"
    ExerciseType.DURATION -> formatClock(value.toLong())
    ExerciseType.DISTANCE_TIME -> formatDistance(value)
}

/** The same value without its unit, for chart labels. */
fun formatSessionValueShort(type: ExerciseType, value: Float): String = when (type) {
    ExerciseType.BODYWEIGHT -> "${value.toInt()}"
    ExerciseType.DURATION -> formatClock(value.toLong())
    ExerciseType.DISTANCE_TIME -> formatKgClean(value / Units.current.metersPerDistanceUnit)
    else -> formatWeightNumber(value)
}

/** What [sessionValue] means, as a chart title. */
fun sessionValueTitle(type: ExerciseType): String = when (type) {
    ExerciseType.WEIGHT_REPS -> "Top weight per session"
    ExerciseType.WEIGHTED_BODYWEIGHT -> "Most added weight per session"
    ExerciseType.BODYWEIGHT -> "Most reps per session"
    ExerciseType.ASSISTED -> "Least assistance per session"
    ExerciseType.DURATION -> "Longest hold per session"
    ExerciseType.DISTANCE_TIME -> "Longest distance per session"
}

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
    "WENDLER_531" to SplitProgramInfo(
        id = "WENDLER_531",
        name = "5/3/1",
        tagline = "Four big lifts in four-week waves of a training max",
        weeklyGoal = 4,
        splits = listOf(
            WorkoutSplit("5/3/1 Squat", "Back squat, then legs", "01"),
            WorkoutSplit("5/3/1 Bench", "Bench press, then back and arms", "02"),
            WorkoutSplit("5/3/1 Deadlift", "Deadlift, then legs and core", "03"),
            WorkoutSplit("5/3/1 Press", "Overhead press, then back and arms", "04")
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

const val DEFAULT_PROGRAM_ID = "PPL_ARNOLD"

/**
 * Programs the user built or customized. Kept current by LocalStorage whenever its data loads
 * or changes; snapshot state, so program lists redraw on edits.
 */
object UserPrograms {
    private val state = mutableStateOf<List<SplitProgramInfo>>(emptyList())
    val all: List<SplitProgramInfo> get() = state.value

    fun set(programs: List<SplitProgramInfo>) {
        if (state.value != programs) state.value = programs
    }
}

/** Built-in programs first, then the user's own. */
fun allPrograms(): List<SplitProgramInfo> = SPLIT_PROGRAMS.values.toList() + UserPrograms.all

fun programFor(programId: String): SplitProgramInfo =
    SPLIT_PROGRAMS[programId] ?: UserPrograms.all.find { it.id == programId } ?: SPLIT_PROGRAMS.getValue(DEFAULT_PROGRAM_ID)

val SplitProgramInfo.isBuiltIn: Boolean get() = id in SPLIT_PROGRAMS

val REST_PRESETS = listOf(0, 60, 90, 120, 180, 240)

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

/** [setText] is the set in words, e.g. from [formatSet]. */
fun sendSetDoneNotification(context: Context, exerciseName: String, setText: String, restSeconds: Int) {
    val restMsg = if (restSeconds > 0) " · Rest ${formatRestShort(restSeconds)}" else ""
    postAlert(
        context, id = 1002, channelId = SET_DONE_NOTIFICATION_CHANNEL_ID, icon = R.drawable.ic_stat_workout,
        title = "Set logged · $exerciseName", text = "$setText$restMsg", requestCode = 1
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
        "5/3/1 Squat" -> listOf("Back Squat", "Leg Press", "Lying Leg Curl", "Hanging Leg Raise")
        "5/3/1 Bench" -> listOf("Barbell Bench Press", "Dumbbell Row", "Tricep Pushdown", "Face Pull")
        "5/3/1 Deadlift" -> listOf("Deadlift", "Bulgarian Split Squat", "Ab Wheel Rollout")
        "5/3/1 Press" -> listOf("Overhead Press", "Chin-Up", "Dips", "Face Pull")
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
    var bestSet: SetEntry? = null
    val recentLogs = ArrayList<Pair<String, List<SetEntry>>>(5)
    val recentChartPoints = ArrayList<Pair<String, Float>>(5)

    var type = ExerciseType.WEIGHT_REPS
    var maxReps = 0
    var maxSeconds = 0
    var maxDistance = 0f
    var minAssist: Float? = null

    for (workout in history) {
        val matched = workout.exercises.find {
            !it.isSkipped && it.name.equals(exerciseName, ignoreCase = true)
        } ?: continue
        type = matched.type
        val workSets = matched.workSets
        if (workSets.isEmpty()) continue

        if (recentLogs.size < 5) {
            val shortDate = workout.displayDate.substringBefore(",")
            recentLogs.add("${workout.splitTitle} ($shortDate)" to workSets)
            recentChartPoints.add(shortDate to sessionValue(type, workSets))
        }
        for (s in workSets) {
            val w = s.weightKg
            val e1rm = calculateEstimated1Rm(w, s.repCount)
            if (w > maxWeight) maxWeight = w
            if (e1rm > best1Rm) best1Rm = e1rm
            val better = when (type) {
                ExerciseType.WEIGHT_REPS, ExerciseType.WEIGHTED_BODYWEIGHT -> bestSet == null || e1rm > calculateEstimated1Rm(bestSet.weightKg, bestSet.repCount)
                ExerciseType.BODYWEIGHT -> s.repCount > maxReps
                ExerciseType.ASSISTED -> minAssist == null || w < minAssist || (w == minAssist && s.repCount > (bestSet?.repCount ?: 0))
                ExerciseType.DURATION -> s.secondCount > maxSeconds
                ExerciseType.DISTANCE_TIME -> s.distanceMeters > maxDistance
            }
            if (better) bestSet = s
            maxReps = maxOf(maxReps, s.repCount)
            maxSeconds = maxOf(maxSeconds, s.secondCount)
            maxDistance = maxOf(maxDistance, s.distanceMeters)
            if (type == ExerciseType.ASSISTED) minAssist = minOf(minAssist ?: w, w)
        }
    }

    return ExerciseRecords(
        maxWeightKg = maxWeight,
        bestEstimated1Rm = roundToHalf(best1Rm),
        bestSet = bestSet,
        recentHistory = recentLogs,
        chartPoints = recentChartPoints.reversed(),
        type = type,
        maxReps = maxReps,
        maxSeconds = maxSeconds,
        maxDistanceM = maxDistance,
        minAssistKg = minAssist
    )
}

/** Whether a completed set beats this exercise's earlier best. Never true without earlier history. */
fun isSetPersonalRecord(setEntry: SetEntry, records: ExerciseRecords, type: ExerciseType = records.type): Boolean {
    if (!setEntry.isCompleted || !setEntry.isValidWorkSet(type)) return false
    val w = setEntry.weightKg
    val r = setEntry.repCount
    return when (type) {
        ExerciseType.WEIGHT_REPS, ExerciseType.WEIGHTED_BODYWEIGHT -> {
            if (w <= 0f || records.bestEstimated1Rm <= 0f) false
            else roundToHalf(calculateEstimated1Rm(w, r)) > records.bestEstimated1Rm || w > records.maxWeightKg
        }
        ExerciseType.BODYWEIGHT -> records.maxReps > 0 && r > records.maxReps
        ExerciseType.ASSISTED -> records.minAssistKg?.let { w < it } ?: false
        ExerciseType.DURATION -> records.maxSeconds > 0 && setEntry.secondCount > records.maxSeconds
        ExerciseType.DISTANCE_TIME -> records.maxDistanceM > 0f && setEntry.distanceMeters > records.maxDistanceM
    }
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
    step: Float
): Suggestion? {
    if (!exercise.type.hasWeightSuggestions) return null
    // Work sets of this exercise from each past session of the same split, newest first.
    val pastSessions = history
        .filter { it.splitTitle.equals(splitTitle, ignoreCase = true) }
        .mapNotNull { workout ->
            workout.exercises
                .find { !it.isSkipped && it.name.equals(exercise.name, ignoreCase = true) }
                ?.workSets
                ?.takeIf { it.isNotEmpty() }
        }
    if (pastSessions.isEmpty()) return null

    val prevSessionSets = pastSessions[0]
    val prevTopSet = prevSessionSets.maxBy { it.weightKg }
    val prevWeight = prevTopSet.weightKg
    val prevWeightStr = prevTopSet.weight
    // Steps and rounding happen in the display unit so pounds land on whole plates.
    val unit = Units.current
    val prev = unit.fromKg(prevWeight)
    val prevText = formatWeightKg(prevWeight)

    fun volume(sets: List<SetEntry>) = sets.sumOf { (it.weightKg * it.repCount).toDouble() }
    // A target's rep range sets the thresholds; without one: 8+ moves up, 6 or fewer moves down.
    val top = exercise.target?.repMax ?: 8
    val low = exercise.target?.let { it.repMin - 1 } ?: 6
    val rangeText = exercise.target?.repText ?: "6–8"
    fun hit8Plus(sets: List<SetEntry>) = sets.any { it.repCount >= top }
    fun hit6OrLess(sets: List<SetEntry>) = sets.any { it.repCount in 1..low }
    fun suggest(value: Float, tone: SuggestionTone, message: (String) -> String): Suggestion {
        val v = unit.round(value.coerceAtLeast(0f))
        return Suggestion(message("${formatKgClean(v)} ${unit.label}"), tone, suggestedWeight = unit.stored(v))
    }
    fun increase(message: (String) -> String) = suggest(prev + step, SuggestionTone.GOOD, message)
    fun decrease(message: (String) -> String) = suggest(prev - step, SuggestionTone.CAUTION, message)

    if (pastSessions.size >= 3) {
        val vol1 = volume(pastSessions[0])
        val vol2 = volume(pastSessions[1])
        val vol3 = volume(pastSessions[2])
        if (vol1 > 0 && vol1 < vol2 && vol2 < vol3) {
            return suggest(prev * 0.9f, SuggestionTone.CAUTION) { w ->
                "Your numbers have dipped three sessions in a row. Go lighter today: $w."
            }
        }
    }

    val prevHad8Plus = hit8Plus(prevSessionSets)
    val prevHad6OrLess = hit6OrLess(prevSessionSets)

    val completedToday = exercise.sets.filter { !it.isWarmupSet && it.isCompleted && it.repCount > 0 }
    if (completedToday.isNotEmpty()) {
        if (prevHad8Plus && hit8Plus(completedToday)) {
            return increase { w -> "$top+ reps two sessions running. Move up to $w." }
        }
        if (prevHad6OrLess && hit6OrLess(completedToday)) {
            return decrease { w -> "Under ${low + 1} reps two sessions running. Drop to $w." }
        }
    }

    if (pastSessions.size >= 2) {
        if (prevHad8Plus && hit8Plus(pastSessions[1])) {
            return increase { w -> "You hit $top+ reps the last two times. Try $w today." }
        }
        if (prevHad6OrLess && hit6OrLess(pastSessions[1])) {
            return decrease { w -> "Under ${low + 1} reps the last two times. Try $w today." }
        }
    }

    return when {
        prevHad8Plus -> Suggestion(
            "Last time: $prevText for $top+ reps. Do it again to move up.",
            SuggestionTone.NEUTRAL, prevWeightStr
        )
        prevHad6OrLess -> Suggestion(
            "Last time: $prevText for $low or fewer. Aim for ${low + 1}–$top reps.",
            SuggestionTone.CAUTION, prevWeightStr
        )
        else -> Suggestion(
            "Last time: $prevText. Aim for $rangeText reps.",
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
            "Next: ${nextEx.name} · ${formatSet(nextSet)}"
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
