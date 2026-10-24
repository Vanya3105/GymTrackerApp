package com.example.gymtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.gymtracker.watchlink.WatchState
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ============================================================================
// SHARED HELPERS
// ============================================================================

private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
private val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
private val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

private fun textPaint(color: Int, size: Float, typeface: Typeface = MEDIUM, align: Paint.Align = Paint.Align.LEFT) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        this.typeface = typeface
        textAlign = align
    }

private fun fillPaint(color: Int, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    this.alpha = alpha
}

private fun strokePaint(color: Int, width: Float, alpha: Int = 255) = fillPaint(color, alpha).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
}

/** Shrinks the text size so [text] fits in [maxWidth]. */
private fun Paint.fitTo(text: String, maxWidth: Float): Paint = apply {
    val w = measureText(text)
    if (w > maxWidth) textSize *= maxWidth / w
}

/** Cuts [text] with an ellipsis so it fits in [maxWidth]. */
private fun Paint.ellipsize(text: String, maxWidth: Float): String {
    if (measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && measureText(text, 0, end) + measureText("…") > maxWidth) end--
    return text.substring(0, end).trimEnd() + "…"
}

/** Palette that follows the in-app theme setting (System/Light/Dark). */
private fun appPalette(context: Context): GymPalette = paletteFor(context, LocalStorage.loadSettings(context).themeChoice)

private fun widgetPalette(context: Context, settings: WidgetSettings): GymPalette = when (settings.theme) {
    THEME_LIGHT -> LightPalette
    THEME_DARK -> DarkPalette
    else -> appPalette(context)
}

private fun widgetBackground(p: GymPalette, settings: WidgetSettings): Int =
    (if (settings.style == WIDGET_STYLE_TINTED) p.accentSoft else p.bgCard).toArgb()

private fun alpha255(percent: Int) = (percent.coerceIn(0, 100) / 100f * 255f).roundToInt()

/** Kind of widget placed under [appWidgetId], from the provider class that owns it. */
fun widgetKindFor(context: Context, appWidgetId: Int): String =
    when (AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className) {
        MuscleFatigueWidgetProvider::class.java.name -> WIDGET_KIND_RECOVERY
        SquarePulseWidgetProvider::class.java.name -> WIDGET_KIND_WEEKLY
        else -> WIDGET_KIND_WORKOUT
    }

// ============================================================================
// 1. LIVE SESSION (shared by the workout widget and the lock-screen card)
// ============================================================================

/**
 * The set the widget is showing. The two +/- boxes show [primary] and [secondary], which depend
 * on the exercise type (weight and reps, reps only, time only, distance and time…).
 */
private class NextSetInfo(
    val exerciseName: String,
    val label: String,
    val summary: String,
    val primary: String,
    val secondary: String?,
    val setNumber: Int,
    val setCount: Int
)

private class LiveSession(
    val splitName: String,
    val currentExercise: ExerciseEntry?,
    val totalSetsLeft: Int,
    val restRemainingMillis: Long
) {
    val isResting: Boolean get() = restRemainingMillis > 1000L

    val nextSet: NextSetInfo? get() {
        val ex = currentExercise ?: return null
        val idx = ex.sets.indexOfFirst { !it.isCompleted }
        if (idx == -1) return null
        val set = ex.sets[idx]
        val workNum = ex.sets.take(idx + 1).count { !it.isWarmupSet }
        return NextSetInfo(
            exerciseName = ex.name,
            label = when {
                set.isWarmupSet -> "Warm-up"
                set.intensityType == "DROP" -> "Drop set"
                else -> "Set $workNum"
            },
            summary = formatSet(set, ex.type),
            primary = when (ex.type) {
                ExerciseType.WEIGHT_REPS -> formatWeightKg(parseWeightClean(set.weight, 20f))
                ExerciseType.WEIGHTED_BODYWEIGHT -> "+${formatWeightKg(set.weightKg)}"
                ExerciseType.ASSISTED -> "−${formatWeightKg(set.weightKg)}"
                ExerciseType.BODYWEIGHT -> "${parseRepsClean(set.reps, 8)} reps"
                ExerciseType.DURATION -> formatClock(set.secondCount.toLong())
                ExerciseType.DISTANCE_TIME -> formatDistance(set.distanceMeters)
            },
            secondary = when (ex.type) {
                ExerciseType.BODYWEIGHT, ExerciseType.DURATION -> null
                ExerciseType.DISTANCE_TIME -> formatClock(set.secondCount.toLong())
                else -> "${parseRepsClean(set.reps, 8)}"
            },
            setNumber = idx + 1,
            setCount = ex.sets.size
        )
    }
}

/** The live workout for the watch, formatted the same way as the widget. */
fun watchStateOf(context: Context): WatchState {
    val live = readLiveSession(context) ?: return WatchState(active = false)
    val next = live.nextSet
    val type = live.currentExercise?.type
    val restEnd = LocalStorage.getRestEndTimeMillis(context).takeIf { live.isResting } ?: 0L
    val restStart = LocalStorage.getRestStartedMillis(context)
    return WatchState(
        active = true,
        workoutName = live.splitName,
        exerciseName = next?.exerciseName,
        setLabel = next?.label.orEmpty(),
        primary = next?.primary.orEmpty(),
        primaryLabel = when (type) {
            ExerciseType.BODYWEIGHT -> "reps"
            ExerciseType.DURATION -> "time"
            ExerciseType.DISTANCE_TIME -> "distance"
            ExerciseType.WEIGHTED_BODYWEIGHT -> "added"
            ExerciseType.ASSISTED -> "assist"
            else -> "weight"
        },
        secondary = next?.secondary,
        secondaryLabel = next?.secondary?.let { if (type == ExerciseType.DISTANCE_TIME) "time" else "reps" },
        summary = next?.summary.orEmpty(),
        setsLeft = live.totalSetsLeft,
        restEndsAtMillis = restEnd,
        restTotalMillis = if (restEnd > 0L && restStart in 1 until restEnd) restEnd - restStart else 0L
    )
}

private fun readLiveSession(context: Context): LiveSession? {
    val (splitName, exercises) = LocalStorage.loadActiveSession(context) ?: return null
    val active = exercises.filter { !it.isSkipped }
    return LiveSession(
        splitName = splitName,
        currentExercise = active.firstOrNull { ex -> ex.sets.any { !it.isCompleted } },
        totalSetsLeft = active.sumOf { ex -> ex.sets.count { !it.isCompleted } },
        restRemainingMillis = LocalStorage.getRestEndTimeMillis(context) - System.currentTimeMillis()
    )
}

private fun widgetActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, GymWidgetProvider::class.java).apply {
        this.action = action
        setPackage(context.packageName)
    }
    return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/**
 * Builds the workout widget / lock-screen card. Used by the provider, the lock-screen
 * notification and the live preview in [WidgetConfigActivity].
 */
fun buildWorkoutWidgetViews(
    context: Context,
    settings: WidgetSettings,
    compact: Boolean,
    requestCodeBase: Int = 10,
    forLockScreen: Boolean = false
): RemoteViews {
    val p = widgetPalette(context, settings)
    val views = RemoteViews(context.packageName, R.layout.gym_widget)
    val primary = p.textPrimary.toArgb()
    val secondary = p.textSecondary.toArgb()
    val accent = p.accentPrimary.toArgb()
    val info = p.accentSecondary.toArgb()
    val control = (if (settings.style == WIDGET_STYLE_TINTED) p.bgCard else p.bgControl).toArgb()

    views.setInt(R.id.widget_bg_image, "setColorFilter", widgetBackground(p, settings))
    views.setInt(R.id.widget_bg_image, "setImageAlpha", if (forLockScreen) 255 else alpha255(settings.opacityPercent))
    listOf(R.id.widget_weight_pill_bg, R.id.widget_reps_pill_bg, R.id.widget_badge_bg).forEach {
        views.setInt(it, "setColorFilter", control)
    }
    views.setInt(R.id.widget_done_pill_bg, "setColorFilter", accent)
    views.setInt(R.id.widget_done_icon, "setColorFilter", p.onAccentPrimary.toArgb())
    views.setInt(R.id.widget_btn_weight_minus, "setColorFilter", secondary)
    views.setInt(R.id.widget_btn_reps_minus, "setColorFilter", secondary)
    views.setInt(R.id.widget_btn_weight_plus, "setColorFilter", accent)
    views.setInt(R.id.widget_btn_reps_plus, "setColorFilter", accent)
    views.setTextColor(R.id.widget_split_title, primary)
    views.setTextColor(R.id.widget_subtitle, secondary)
    views.setTextColor(R.id.widget_weight_value, primary)
    views.setTextColor(R.id.widget_reps_value, primary)
    views.setTextColor(R.id.widget_action_badge, accent)
    views.setTextColor(R.id.widget_rest_chronometer, info)

    val showControls = WIDGET_OPTION_CONTROLS in settings.options || forLockScreen
    val live = readLiveSession(context)

    fun status(text: String, color: Int) {
        views.setTextViewText(R.id.widget_status_label, text)
        views.setTextColor(R.id.widget_status_label, color)
        views.setInt(R.id.widget_status_dot, "setColorFilter", color)
    }

    if (live != null) {
        val next = live.nextSet
        if (next != null) {
            status(
                when {
                    live.isResting -> "Resting"
                    compact -> next.label
                    else -> "${live.splitName} · ${next.label}"
                },
                if (live.isResting) info else accent
            )
            views.setTextViewText(R.id.widget_split_title, next.exerciseName)
            views.setTextViewText(
                R.id.widget_subtitle,
                if (live.isResting) "Next: ${next.summary}"
                else "${live.totalSetsLeft} sets left today"
            )
            views.setViewVisibility(R.id.widget_subtitle, if (compact && !live.isResting && showControls) View.GONE else View.VISIBLE)

            val controlsVisible = showControls && !live.isResting
            views.setViewVisibility(R.id.widget_controls_row, if (controlsVisible) View.VISIBLE else View.GONE)
            if (controlsVisible) {
                views.setTextViewText(R.id.widget_weight_value, next.primary)
                views.setViewVisibility(R.id.widget_reps_group, if (next.secondary != null) View.VISIBLE else View.GONE)
                next.secondary?.let { views.setTextViewText(R.id.widget_reps_value, it) }
                listOf(
                    R.id.widget_btn_weight_minus to GymWidgetProvider.ACTION_WEIGHT_MINUS,
                    R.id.widget_btn_weight_plus to GymWidgetProvider.ACTION_WEIGHT_PLUS,
                    R.id.widget_btn_reps_minus to GymWidgetProvider.ACTION_REPS_MINUS,
                    R.id.widget_btn_reps_plus to GymWidgetProvider.ACTION_REPS_PLUS,
                    R.id.widget_btn_complete_set to GymWidgetProvider.ACTION_COMPLETE_SET
                ).forEachIndexed { i, (viewId, action) ->
                    views.setOnClickPendingIntent(viewId, widgetActionPendingIntent(context, action, requestCodeBase + i))
                }
            }
        } else {
            status("${live.splitName} · done", p.successText.toArgb())
            views.setTextViewText(R.id.widget_split_title, "All sets done")
            views.setTextViewText(R.id.widget_subtitle, "Open the app to save your workout")
            views.setViewVisibility(R.id.widget_controls_row, View.GONE)
        }

        if (live.isResting) {
            views.setViewVisibility(R.id.widget_rest_chronometer, View.VISIBLE)
            views.setViewVisibility(R.id.widget_badge_container, View.GONE)
            views.setChronometerCountDown(R.id.widget_rest_chronometer, true)
            views.setChronometer(R.id.widget_rest_chronometer, SystemClock.elapsedRealtime() + live.restRemainingMillis, "%s", true)
        } else {
            views.setViewVisibility(R.id.widget_rest_chronometer, View.GONE)
            views.setViewVisibility(R.id.widget_badge_container, View.VISIBLE)
            views.setTextViewText(R.id.widget_action_badge, "Open")
        }
    } else {
        val profile = LocalStorage.loadUserProfile(context)
        val week = computeWeekSummary(LocalStorage.loadHistory(context), 0, profile.splitProgram)
        val split = programFor(profile.splitProgram).splits.find { it.title == week.recommendedSplit }
        status("Up next", accent)
        views.setTextViewText(R.id.widget_split_title, week.recommendedSplit)
        views.setTextViewText(
            R.id.widget_subtitle,
            if (WIDGET_OPTION_WEEKLY in settings.options) "${week.completedCoreSplits.size} of ${week.weeklyGoal} done this week"
            else split?.subtitle.orEmpty()
        )
        views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
        views.setViewVisibility(R.id.widget_controls_row, View.GONE)
        views.setViewVisibility(R.id.widget_rest_chronometer, View.GONE)
        views.setViewVisibility(R.id.widget_badge_container, View.VISIBLE)
        views.setTextViewText(R.id.widget_action_badge, "Start")
    }

    val openApp = openAppPendingIntent(context, 0)
    views.setOnClickPendingIntent(R.id.widget_header_click_area, openApp)
    if (live == null) views.setOnClickPendingIntent(R.id.widget_root, openApp)
    return views
}

private const val WIDGET_OPTION_CONTROLS = "controls"
private const val WIDGET_OPTION_WEEKLY = "weekly"

// ============================================================================
// 2. LOCK-SCREEN LIVE WORKOUT NOTIFICATION
// ============================================================================

object LockScreenWorkoutController {
    private const val LIVE_WORKOUT_CHANNEL_ID = "gym_live_lockscreen_channel"
    private const val LIVE_NOTIFICATION_ID = 2002
    private var channelCreated = false

    private fun ensureChannel(manager: NotificationManager) {
        if (channelCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(LIVE_WORKOUT_CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shows your current set on the lock screen while you train"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        channelCreated = true
    }

    fun sync(context: Context) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            val live = readLiveSession(context)
            if (live == null) {
                manager.cancel(LIVE_NOTIFICATION_ID)
                return
            }
            ensureChannel(manager)

            val settings = defaultWidgetSettings(WIDGET_KIND_WORKOUT)
            val expanded = buildWorkoutWidgetViews(context, settings, compact = false, requestCodeBase = 20, forLockScreen = true)
            val builder = notificationBuilder(context, LIVE_WORKOUT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_workout)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_WORKOUT)
                .setContentIntent(openAppPendingIntent(context, 0))
                .setCustomContentView(buildWorkoutWidgetViews(context, settings, compact = true, requestCodeBase = 20, forLockScreen = true))
                .setCustomBigContentView(expanded)
                .setCustomHeadsUpContentView(expanded)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            manager.notify(LIVE_NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }
}

// ============================================================================
// 3. WIDGET #1: INTERACTIVE WORKOUT WIDGET
// ============================================================================

class GymWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action in SET_ACTIONS) {
            applySetAction(context.applicationContext, action)
            LocalStorage.flush(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidgetUi(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgetUi(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = LocalStorage.deleteWidgetSettings(context, appWidgetIds)

    companion object {
        const val ACTION_WEIGHT_MINUS = "com.example.gymtracker.ACTION_WEIGHT_MINUS"
        const val ACTION_WEIGHT_PLUS = "com.example.gymtracker.ACTION_WEIGHT_PLUS"
        const val ACTION_REPS_MINUS = "com.example.gymtracker.ACTION_REPS_MINUS"
        const val ACTION_REPS_PLUS = "com.example.gymtracker.ACTION_REPS_PLUS"
        const val ACTION_COMPLETE_SET = "com.example.gymtracker.ACTION_COMPLETE_SET"
        private val SET_ACTIONS = setOf(ACTION_WEIGHT_MINUS, ACTION_WEIGHT_PLUS, ACTION_REPS_MINUS, ACTION_REPS_PLUS, ACTION_COMPLETE_SET)

        /** One tap on the widget, lock-screen card or watch, applied to the next unfinished set. */
        fun applySetAction(context: Context, action: String) {
            // Set controls are hidden while resting; ignore stale taps (the caller's flush redraws).
            if (LocalStorage.getRestEndTimeMillis(context) - System.currentTimeMillis() > 1000L) return

            val (splitTitle, sessionExercises) = LocalStorage.loadActiveSession(context) ?: return
            val exercises = sessionExercises.toMutableList()
            val exIdx = exercises.indexOfFirst { ex -> !ex.isSkipped && ex.sets.any { !it.isCompleted } }
            if (exIdx == -1) return
            val currentEx = exercises[exIdx]
            val setIdx = currentEx.sets.indexOfFirst { !it.isCompleted }
            val target = currentEx.sets[setIdx]
            val settings = LocalStorage.loadSettings(context)
            val curReps = parseRepsClean(target.reps, 8)
            val type = currentEx.type

            fun reps(delta: Int) = target.copy(reps = "${(curReps + delta).coerceAtLeast(0)}")
            fun time(delta: Int) = target.copy(seconds = "${(target.secondCount + delta).coerceAtLeast(0)}")
            fun distance(deltaUnits: Float) = target.copy(distanceM = formatKgClean(
                (target.distanceMeters + deltaUnits * Units.current.metersPerDistanceUnit).coerceAtLeast(0f)
            ))

            // The first box is weight, reps (bodyweight), time (holds) or distance; the second is reps or time.
            val updated = when (action) {
                ACTION_WEIGHT_MINUS, ACTION_WEIGHT_PLUS -> {
                    val sign = if (action == ACTION_WEIGHT_PLUS) 1 else -1
                    when (type) {
                        ExerciseType.BODYWEIGHT -> reps(sign)
                        ExerciseType.DURATION -> time(15 * sign)
                        ExerciseType.DISTANCE_TIME -> distance(0.1f * sign)
                        else -> target.copy(weight = stepWeight(target.weight, settings.weightStep * sign))
                    }
                }
                ACTION_REPS_MINUS, ACTION_REPS_PLUS -> {
                    val sign = if (action == ACTION_REPS_PLUS) 1 else -1
                    if (type == ExerciseType.DISTANCE_TIME) time(60 * sign) else reps(sign)
                }
                ACTION_COMPLETE_SET -> target.copy(isCompleted = true)
                else -> return
            }
            exercises[exIdx] = currentEx.copy(sets = currentEx.sets.toMutableList().also { it[setIdx] = updated })
            LocalStorage.saveActiveSession(context, splitTitle, exercises, fromWidget = true)

            if (action == ACTION_COMPLETE_SET) {
                val restSec = target.restSeconds.takeIf { it >= 0 } ?: settings.restTimerSeconds
                sendSetDoneNotification(context, currentEx.name, formatSet(target, type), restSec)
                val restEnd = if (restSec > 0) System.currentTimeMillis() + restSec * 1000L else 0L
                LocalStorage.setRestEndTimeMillis(context, restEnd, fromWidget = true)
            }
        }

        fun refreshAllWidgets(context: Context) {
            try {
                val appCtx = context.applicationContext
                val manager = AppWidgetManager.getInstance(appCtx)
                for (id in manager.getAppWidgetIds(ComponentName(appCtx, GymWidgetProvider::class.java))) {
                    updateWidgetUi(appCtx, manager, id)
                }
            } catch (_: Exception) {}
        }

        fun updateWidgetUi(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val compact = manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) < 210
            val settings = LocalStorage.loadWidgetSettings(context, widgetId, WIDGET_KIND_WORKOUT)
            manager.updateAppWidget(widgetId, buildWorkoutWidgetViews(context, settings, compact))
        }
    }
}

// ============================================================================
// 4. CANVAS WIDGETS (#2 RECOVERY, #3 WEEKLY) — drawn at the widget's real size
// ============================================================================

/** Size of a placed widget in dp (portrait), falling back to [defaultW]×[defaultH]. */
private fun widgetSizeDp(manager: AppWidgetManager, widgetId: Int, defaultW: Int, defaultH: Int): Pair<Int, Int> {
    val o = manager.getAppWidgetOptions(widgetId)
    val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: defaultW
    val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: defaultH
    return w to h
}

/** Pixels per dp for a widget bitmap, capped so the bitmap stays within RemoteViews' memory budget. */
private fun bitmapScale(context: Context, wDp: Int, hDp: Int): Float {
    val density = context.resources.displayMetrics.density
    val maxScale = sqrt(2_500_000f / (wDp * hDp).coerceAtLeast(1))
    return min(density, maxScale)
}

private fun refreshCanvasWidgets(
    context: Context,
    provider: Class<*>,
    update: (Context, AppWidgetManager, Int) -> Unit
) {
    try {
        val appCtx = context.applicationContext
        val manager = AppWidgetManager.getInstance(appCtx)
        manager.getAppWidgetIds(ComponentName(appCtx, provider)).forEach { update(appCtx, manager, it) }
    } catch (_: Exception) {}
}

private fun pushCanvasWidget(context: Context, manager: AppWidgetManager, widgetId: Int, bitmap: Bitmap, requestCode: Int) {
    val views = RemoteViews(context.packageName, R.layout.gym_canvas_widget)
    views.setImageViewBitmap(R.id.canvas_widget_image, bitmap)
    views.setOnClickPendingIntent(R.id.canvas_widget_root, openAppPendingIntent(context, requestCode))
    manager.updateAppWidget(widgetId, views)
}

/** Draws the rounded card background shared by both canvas widgets. Returns the palette used. */
private fun drawWidgetCard(canvas: Canvas, context: Context, settings: WidgetSettings, w: Float, h: Float, s: Float): GymPalette {
    val p = widgetPalette(context, settings)
    val rect = RectF(0f, 0f, w, h)
    canvas.drawRoundRect(rect, 24 * s, 24 * s, fillPaint(widgetBackground(p, settings), alpha255(settings.opacityPercent)))
    if (settings.opacityPercent >= 30) {
        canvas.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), 24 * s, 24 * s, strokePaint(p.borderSubtle.toArgb(), 1 * s))
    }
    return p
}

class MuscleFatigueWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        update(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = LocalStorage.deleteWidgetSettings(context, appWidgetIds)

    companion object {
        fun refreshAll(context: Context) = refreshCanvasWidgets(context, MuscleFatigueWidgetProvider::class.java, ::update)

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val (wDp, hDp) = widgetSizeDp(manager, widgetId, 300, 140)
            val settings = LocalStorage.loadWidgetSettings(context, widgetId, WIDGET_KIND_RECOVERY)
            pushCanvasWidget(context, manager, widgetId, render(context, settings, wDp, hDp), 301)
        }

        fun render(context: Context, settings: WidgetSettings, wDp: Int, hDp: Int): Bitmap {
            val s = bitmapScale(context, wDp, hDp)
            val w = wDp * s
            val h = hDp * s
            val bmp = Bitmap.createBitmap(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val p = drawWidgetCard(canvas, context, settings, w, h, s)

            val items = computeMuscleRecoveryStatus(LocalStorage.loadHistory(context))
            val ready = items.count { it.status == RecoveryStatus.READY }
            val pad = 16 * s

            canvas.drawText("Recovery", pad, pad + 14 * s, textPaint(p.textPrimary.toArgb(), 15 * s))
            canvas.drawText(
                if (ready == items.size) "All rested" else "$ready of ${items.size} rested",
                w - pad, pad + 14 * s,
                textPaint(p.textSecondary.toArgb(), 12 * s, REGULAR, Paint.Align.RIGHT)
            )

            val showSets = "sets" in settings.options
            val showHours = "hours" in settings.options
            val colW = (w - pad * 2) / items.size
            val textRows = (if (showSets) 1 else 0) + (if (showHours) 1 else 0)
            val textBlock = (18 + textRows * 15) * s
            val areaTop = pad + 30 * s
            val ringD = min(colW - 10 * s, h - areaTop - pad - textBlock).coerceAtLeast(16 * s)
            // Center the rings + labels in the space under the header.
            val top = areaTop + ((h - areaTop - pad) - (ringD + textBlock)).coerceAtLeast(0f) / 2
            val stroke = (ringD * 0.11f).coerceIn(3 * s, 6 * s)

            items.forEachIndexed { i, item ->
                val cx = pad + colW * i + colW / 2
                val cy = top + ringD / 2
                val color = when (item.status) {
                    RecoveryStatus.TIRED -> p.dangerText
                    RecoveryStatus.RECOVERING -> p.warningText
                    RecoveryStatus.READY -> p.successText
                }.toArgb()
                val r = ringD / 2 - stroke / 2
                val arc = RectF(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(arc, 0f, 360f, false, strokePaint(p.bgControl.toArgb(), stroke))
                canvas.drawArc(arc, -90f, 360f * item.recoveryPercent / 100f, false, strokePaint(color, stroke))
                val pctPaint = textPaint(p.textPrimary.toArgb(), (ringD * 0.26f).coerceAtMost(15 * s), MEDIUM, Paint.Align.CENTER)
                canvas.drawText("${item.recoveryPercent}", cx, cy + pctPaint.textSize * 0.35f, pctPaint)

                var y = top + ringD + 16 * s
                canvas.drawText(item.muscleGroup, cx, y, textPaint(p.textPrimary.toArgb(), 11.5f * s, MEDIUM, Paint.Align.CENTER))
                if (showSets) {
                    y += 15 * s
                    val inTarget = item.weeklyWorkingSets in WEEKLY_SET_TARGET
                    canvas.drawText("${item.weeklyWorkingSets} sets", cx, y, textPaint((if (inTarget) p.successText else p.textSecondary).toArgb(), 10.5f * s, REGULAR, Paint.Align.CENTER))
                }
                if (showHours) {
                    y += 15 * s
                    val label = item.hoursSinceTrained?.let { if (it < 1) "Just now" else if (it < 48) "${it}h ago" else "${it / 24}d ago" } ?: "Not yet"
                    canvas.drawText(label, cx, y, textPaint(p.textTertiary.toArgb(), 10.5f * s, REGULAR, Paint.Align.CENTER))
                }
            }
            return bmp
        }
    }
}

class SquarePulseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        update(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = LocalStorage.deleteWidgetSettings(context, appWidgetIds)

    companion object {
        fun refreshAll(context: Context) = refreshCanvasWidgets(context, SquarePulseWidgetProvider::class.java, ::update)

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val (wDp, hDp) = widgetSizeDp(manager, widgetId, 160, 160)
            val settings = LocalStorage.loadWidgetSettings(context, widgetId, WIDGET_KIND_WEEKLY)
            pushCanvasWidget(context, manager, widgetId, render(context, settings, wDp, hDp), 302)
        }

        fun render(context: Context, settings: WidgetSettings, wDp: Int, hDp: Int): Bitmap {
            val s = bitmapScale(context, wDp, hDp)
            val w = wDp * s
            val h = hDp * s
            val bmp = Bitmap.createBitmap(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val p = drawWidgetCard(canvas, context, settings, w, h, s)

            val profile = LocalStorage.loadUserProfile(context)
            val week = computeWeekSummary(LocalStorage.loadHistory(context), 0, profile.splitProgram)
            val done = week.completedCoreSplits.size
            val goal = week.weeklyGoal.coerceAtLeast(1)
            val showVolume = "volume" in settings.options
            val showNext = "next" in settings.options
            val pad = 14 * s

            canvas.drawText("This week", pad, pad + 13 * s, textPaint(p.textPrimary.toArgb(), 14 * s))

            val footerLines = (if (showNext) 1 else 0) + (if (showVolume) 1 else 0)
            val footerH = footerLines * 16 * s + if (footerLines > 0) 6 * s else 0f
            val areaTop = pad + 24 * s
            val areaBottom = h - pad - footerH
            val d = min(w - pad * 2, areaBottom - areaTop).coerceAtLeast(24 * s)
            val cx = w / 2
            val cy = areaTop + (areaBottom - areaTop) / 2
            val stroke = (d * 0.1f).coerceIn(5 * s, 12 * s)
            val r = d / 2 - stroke / 2
            val arc = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(arc, 0f, 360f, false, strokePaint(p.bgControl.toArgb(), stroke))
            if (done > 0) canvas.drawArc(arc, -90f, 360f * (done.toFloat() / goal).coerceAtMost(1f), false, strokePaint(p.accentPrimary.toArgb(), stroke))
            val big = textPaint(p.textPrimary.toArgb(), d * 0.26f, BOLD, Paint.Align.CENTER)
            canvas.drawText("$done/$goal", cx, cy + big.textSize * 0.2f, big)
            canvas.drawText("workouts", cx, cy + big.textSize * 0.2f + 13 * s, textPaint(p.textSecondary.toArgb(), 10.5f * s, REGULAR, Paint.Align.CENTER))

            var y = h - pad - footerH + 16 * s
            if (showNext) {
                val nextText = "Next: ${week.recommendedSplit}"
                canvas.drawText(nextText, cx, y, textPaint(p.accentPrimary.toArgb(), 12 * s, MEDIUM, Paint.Align.CENTER).fitTo(nextText, w - pad * 2))
                y += 16 * s
            }
            if (showVolume) {
                val vol = week.workouts.sumOf { computeWorkoutStats(it.exercises, emptyList()).volumeKg }
                canvas.drawText(formatVolume(vol) + " lifted", cx, y, textPaint(p.textSecondary.toArgb(), 11 * s, REGULAR, Paint.Align.CENTER))
            }
            return bmp
        }
    }
}

// ============================================================================
// 5. SHARE CARD (1080×1920 image for stories and chats)
// ============================================================================

fun shareWorkoutCardImage(
    context: Context,
    splitTitle: String,
    durationSeconds: Long,
    exercises: List<ExerciseEntry>,
    history: List<CompletedWorkout>,
    bodyWeightKg: Float,
    photoPath: String? = null
) {
    try {
        val card = generateShareCard(context, splitTitle, durationSeconds, exercises, history, bodyWeightKg, photoPath)

        // Share cards are throwaway: keep them in cache and replace the previous one.
        val folder = File(context.cacheDir, "share_cards").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        // Older versions saved share cards next to workout photos and never removed them.
        File(context.filesDir, "workout_photos").listFiles { f -> f.name.startsWith("gym_story_share_") }?.forEach { it.delete() }
        val file = File(folder, "workout_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out -> card.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        card.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "$splitTitle done. Logged with GymTracker.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share workout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't create the image", Toast.LENGTH_SHORT).show()
    }
}

private fun generateShareCard(
    context: Context,
    splitTitle: String,
    durationSeconds: Long,
    exercises: List<ExerciseEntry>,
    history: List<CompletedWorkout>,
    bodyWeightKg: Float,
    photoPath: String?
): Bitmap {
    val W = 1080f
    val H = 1920f
    val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val photo = photoPath?.takeIf { it.isNotBlank() && File(it).exists() }?.let { BitmapFactory.decodeFile(it) }
    // Text over a photo always needs the dark palette to stay readable.
    val p = if (photo != null) DarkPalette else appPalette(context)
    val accent = p.accentPrimary.toArgb()

    if (photo != null) {
        val scale = maxOf(W / photo.width, H / photo.height)
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate((W - photo.width * scale) / 2f, (H - photo.height * scale) / 2f)
        }
        canvas.drawBitmap(photo, m, Paint(Paint.FILTER_BITMAP_FLAG))
        photo.recycle()
        canvas.drawRect(0f, 0f, W, H, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H,
                intArrayOf(Color.argb(150, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(200, 10, 10, 12), Color.argb(245, 10, 10, 12)),
                floatArrayOf(0f, 0.3f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        })
    } else {
        canvas.drawColor(p.bgPrimary.toArgb())
        canvas.drawRect(0f, 0f, W, H, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(W * 0.85f, H * 0.08f, 900f, Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
    }

    val left = 96f
    val right = W - 96f
    val text = p.textPrimary.toArgb()
    val muted = p.textSecondary.toArgb()

    // Header
    canvas.drawCircle(left + 14f, 158f, 14f, fillPaint(accent))
    canvas.drawText("GymTracker", left + 44f, 170f, textPaint(text, 40f))
    canvas.drawText(DateUtils.formatShortDayYear(Date()), right, 170f, textPaint(muted, 36f, REGULAR, Paint.Align.RIGHT))

    val stats = computeWorkoutStats(exercises, history)
    val lifts = exercises.filter { !it.isSkipped }.mapNotNull { ex ->
        bestSetOf(ex.type, ex.workSets)?.let { ex.name to formatSet(it, ex.type) }
    }

    // Title block sits lower when a photo fills the top.
    var y = if (photo != null) 1040f else 520f
    val bw = if (bodyWeightKg > 0f) "  ·  ${formatWeightKg(bodyWeightKg)} body weight" else ""
    canvas.drawText("Workout complete$bw", left, y, textPaint(accent, 40f).fitTo("Workout complete$bw", right - left))
    y += 120f
    canvas.drawText(splitTitle, left, y, textPaint(text, 120f, BOLD).fitTo(splitTitle, right - left))
    y += 90f

    // Stats grid (2 × 2)
    val gap = 24f
    val cellW = (right - left - gap) / 2
    val cellH = 170f
    listOf(
        formatDuration(durationSeconds) to "Time",
        formatVolume(stats.volumeKg) to "Volume",
        "${stats.workSets}" to "Sets",
        "${stats.prCount}" to "New bests"
    ).forEachIndexed { i, (value, label) ->
        val cx = left + (i % 2) * (cellW + gap)
        val cy = y + (i / 2) * (cellH + gap)
        canvas.drawRoundRect(RectF(cx, cy, cx + cellW, cy + cellH), 36f, 36f, fillPaint(p.bgCard.toArgb(), if (photo != null) 200 else 255))
        canvas.drawText(value, cx + 36f, cy + 92f, textPaint(text, 58f, BOLD))
        canvas.drawText(label, cx + 36f, cy + 136f, textPaint(muted, 32f, REGULAR))
    }
    y += cellH * 2 + gap + 90f

    // Top sets
    val maxRows = ((H - 200f - y) / 76f).toInt().coerceIn(0, 7)
    if (lifts.isNotEmpty() && maxRows > 0) {
        canvas.drawText("Top sets", left, y, textPaint(muted, 34f))
        y += 30f
        lifts.take(maxRows).forEach { (name, set) ->
            y += 76f
            val setPaint = textPaint(text, 40f, MEDIUM, Paint.Align.RIGHT)
            val namePaint = textPaint(text, 40f, REGULAR)
            canvas.drawText(namePaint.ellipsize(name, right - left - setPaint.measureText(set) - 32f), left, y, namePaint)
            canvas.drawText(set, right, y, setPaint)
        }
    }

    canvas.drawText("Logged with GymTracker", W / 2, H - 110f, textPaint(muted, 30f, REGULAR, Paint.Align.CENTER))
    return bmp
}
