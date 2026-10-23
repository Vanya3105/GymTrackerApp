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

/** A weight for display, with its unit. */
fun formatWeightKg(kg: Float): String = "${formatKgClean(kg)} kg"

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
        bestEstimated1Rm = roundToHalf(best1Rm),
        bestSetText = bestSetStr,
        recentHistory = recentLogs,
        chartPoints = recentChartPoints.reversed()
    )
}

fun isSetPersonalRecord(setEntry: SetEntry, records: ExerciseRecords): Boolean {
    if (setEntry.isWarmupSet || !setEntry.isCompleted) return false
    val w = setEntry.weightKg
    val r = setEntry.repCount
    if (w <= 0f || r <= 0 || records.bestEstimated1Rm <= 0f) return false
    return roundToHalf(calculateEstimated1Rm(w, r)) > records.bestEstimated1Rm || w > records.maxWeightKg
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
