package com.example.gymtracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// 1. THEME COLOR RESOLVER FOR NATIVE CANVAS (WIDGETS & STORY CARDS)
// ============================================================================

private data class NativeCanvasTheme(
    val bgCard: Int,
    val bgControl: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val borderStrong: Int,
    val accentPrimary: Int,
    val accentSecondary: Int,
    val success: Int,
    val warning: Int,
    val danger: Int
)

private fun resolveNativeCanvasTheme(context: Context): NativeCanvasTheme {
    val settings = LocalStorage.loadSettings(context)
    return when (settings.themeChoice) {
        "ARCTIC" -> NativeCanvasTheme(
            bgCard = Color.parseColor("#FFFFFF"),
            bgControl = Color.parseColor("#E6EAF2"),
            textPrimary = Color.parseColor("#0B0F19"),
            textSecondary = Color.parseColor("#5B6478"),
            borderStrong = Color.parseColor("#B0B9CB"),
            accentPrimary = Color.parseColor("#FF4500"),
            accentSecondary = Color.parseColor("#0066FF"),
            success = Color.parseColor("#16A34A"),
            warning = Color.parseColor("#EA580C"),
            danger = Color.parseColor("#DC2626")
        )
        "CYBERPUNK" -> NativeCanvasTheme(
            bgCard = Color.parseColor("#131520"),
            bgControl = Color.parseColor("#1D2130"),
            textPrimary = Color.parseColor("#FFFFFF"),
            textSecondary = Color.parseColor("#94A3B8"),
            borderStrong = Color.parseColor("#333A56"),
            accentPrimary = Color.parseColor("#FF0055"),
            accentSecondary = Color.parseColor("#00F0FF"),
            success = Color.parseColor("#22C55E"),
            warning = Color.parseColor("#F97316"),
            danger = Color.parseColor("#EF4444")
        )
        "OLED" -> NativeCanvasTheme(
            bgCard = Color.parseColor("#080808"),
            bgControl = Color.parseColor("#171717"),
            textPrimary = Color.parseColor("#FAFAFA"),
            textSecondary = Color.parseColor("#8A8A8A"),
            borderStrong = Color.parseColor("#383838"),
            accentPrimary = Color.parseColor("#3B82F6"),
            accentSecondary = Color.parseColor("#A855F7"),
            success = Color.parseColor("#4ADE80"),
            warning = Color.parseColor("#FB923C"),
            danger = Color.parseColor("#F87171")
        )
        else -> NativeCanvasTheme(
            bgCard = Color.parseColor("#111318"),
            bgControl = Color.parseColor("#1E222B"),
            textPrimary = Color.parseColor("#FAFAFC"),
            textSecondary = Color.parseColor("#8A93A6"),
            borderStrong = Color.parseColor("#2C3240"),
            accentPrimary = Color.parseColor("#CCFF00"),
            accentSecondary = Color.parseColor("#00F0FF"),
            success = Color.parseColor("#4ADE80"),
            warning = Color.parseColor("#FB923C"),
            danger = Color.parseColor("#FF453A")
        )
    }
}

// ============================================================================
// 2. WIDGET #2: AI MUSCLE FATIGUE & RECOVERY MATRIX WIDGET (4x2)
// ============================================================================

class MuscleFatigueWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun refreshAll(context: Context) {
            try {
                val appCtx = context.applicationContext
                val manager = AppWidgetManager.getInstance(appCtx)
                val ids = manager.getAppWidgetIds(ComponentName(appCtx, MuscleFatigueWidgetProvider::class.java))
                for (id in ids) {
                    updateWidget(appCtx, manager, id)
                }
            } catch (_: Exception) {}
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.gym_canvas_widget)
            val bmp = renderFatigueMatrixBitmap(context)
            views.setImageViewBitmap(R.id.canvas_widget_image, bmp)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 301, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.canvas_widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }

        private fun renderFatigueMatrixBitmap(context: Context): Bitmap {
            val width = 840
            val height = 380
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val appCtx = context.applicationContext
            val T = resolveNativeCanvasTheme(appCtx)
            val appearance = LocalStorage.loadWidgetAppearance(appCtx)

            // Safe history retrieval with fallback dummy data if logbook is empty
            val history = LocalStorage.loadHistory(appCtx)
            val recoveryList = computeMuscleRecoveryStatus(history)
            val fatiguedCount = recoveryList.count { it.statusLabel == "FATIGUED" }

            val alphaInt = ((appearance.opacityPercent.coerceIn(10, 100) / 100f) * 255f).roundToInt()
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.bgCard
                alpha = alphaInt
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.borderStrong
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val cardRect = RectF(4f, 4f, width - 4f, height - 4f)
            canvas.drawRoundRect(cardRect, 48f, 48f, bgPaint)
            canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

            // Header Title
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.accentSecondary
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
            }
            canvas.drawText("🧠 AI MUSCLE RECOVERY & MAV", 38f, 52f, headerPaint)

            // Status Pill on Top Right
            val badgeText = if (fatiguedCount == 0) "100% PRIMED ⚡" else "$fatiguedCount RECOVERING"
            val badgeColor = if (fatiguedCount == 0) T.success else T.warning
            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeColor
                alpha = 38
            }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeColor
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val badgeWidth = badgeTextPaint.measureText(badgeText) + 36f
            val badgeRect = RectF(width - 38f - badgeWidth, 24f, width - 38f, 64f)
            canvas.drawRoundRect(badgeRect, 18f, 18f, badgeBgPaint)
            canvas.drawText(badgeText, width - 56f, 51f, badgeTextPaint)

            // 5 Muscle Columns
            val colGap = 16f
            val totalSidePad = 76f
            val colWidth = (width - totalSidePad - (colGap * 4)) / 5f
            val colTop = 84f
            val colBottom = height - 34f

            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.bgControl
                alpha = alphaInt
            }
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.textPrimary
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.textSecondary
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            recoveryList.forEachIndexed { idx, item ->
                val left = 38f + idx * (colWidth + colGap)
                val right = left + colWidth
                val centerX = (left + right) / 2f

                val statusColor = when (item.statusLabel) {
                    "FATIGUED" -> T.danger
                    "RECOVERING" -> T.warning
                    else -> T.success
                }

                val colRect = RectF(left, colTop, right, colBottom)
                canvas.drawRoundRect(colRect, 28f, 28f, boxPaint)

                val colStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = statusColor
                    alpha = 95
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }
                canvas.drawRoundRect(colRect, 28f, 28f, colStroke)

                // Muscle Name
                canvas.drawText(item.muscleGroup.uppercase(Locale.US), centerX, colTop + 44f, namePaint)

                // Recovery %
                pctPaint.color = statusColor
                canvas.drawText("${item.recoveryPercent}%", centerX, colTop + 102f, pctPaint)

                // Progress Bar Track & Fill
                val barLeft = left + 18f
                val barRight = right - 18f
                val barTop = colTop + 126f
                val barBottom = barTop + 14f
                val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    alpha = 90
                }
                canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBottom), 7f, 7f, trackPaint)

                val fillWidth = (barRight - barLeft) * (item.recoveryPercent / 100f).coerceIn(0.1f, 1f)
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor }
                canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + fillWidth, barBottom), 7f, 7f, fillPaint)

                // Weekly Sets (MAV)
                subPaint.color = if (item.weeklyWorkingSets in 6..16) T.accentPrimary else T.textSecondary
                canvas.drawText("${item.weeklyWorkingSets} sets/wk", centerX, colTop + 178f, subPaint)

                // Fresh / Hours Ago
                val timeLbl = if (item.hoursSinceTrained != null && item.hoursSinceTrained < 72) {
                    "${item.hoursSinceTrained}h ago"
                } else {
                    "FRESH"
                }
                subPaint.color = T.textSecondary
                subPaint.textSize = 18f
                canvas.drawText(timeLbl, centerX, colTop + 216f, subPaint)
            }

            return bmp
        }
    }
}

// ============================================================================
// 3. WIDGET #3: SQUARE 2x2 WEEKLY PULSE & VOLUME RING WIDGET
// ============================================================================

class SquarePulseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun refreshAll(context: Context) {
            try {
                val appCtx = context.applicationContext
                val manager = AppWidgetManager.getInstance(appCtx)
                val ids = manager.getAppWidgetIds(ComponentName(appCtx, SquarePulseWidgetProvider::class.java))
                for (id in ids) {
                    updateWidget(appCtx, manager, id)
                }
            } catch (_: Exception) {}
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.gym_canvas_widget)
            val bmp = renderSquarePulseBitmap(context)
            views.setImageViewBitmap(R.id.canvas_widget_image, bmp)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 302, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.canvas_widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }

        private fun renderSquarePulseBitmap(context: Context): Bitmap {
            val size = 420
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val T = resolveNativeCanvasTheme(context)

            val history = LocalStorage.loadHistory(context)
            val profile = LocalStorage.loadUserProfile(context)
            val weekSummary = computeWeekSummary(history, 0, profile.splitProgram)

            val doneCount = weekSummary.completedCoreSplits.size
            val goalCount = weekSummary.weeklyGoal.coerceAtLeast(1)
            val progress = (doneCount.toFloat() / goalCount.toFloat()).coerceIn(0f, 1f)

            var weekVol = 0f
            for (w in weekSummary.workouts) {
                for (ex in w.exercises) {
                    for (s in ex.sets) {
                        if (!s.isWarmup && s.intensityType != "WARMUP") {
                            weekVol += parseWeightClean(s.weight, 0f) * parseRepsClean(s.reps, 0)
                        }
                    }
                }
            }
            val volFormatted = if (weekVol >= 1000f) {
                String.format(Locale.US, "%.1fk kg", weekVol / 1000f)
            } else {
                "${weekVol.roundToInt()} kg"
            }

            // Card Background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = T.bgCard }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.borderStrong
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val rect = RectF(4f, 4f, size - 4f, size - 4f)
            canvas.drawRoundRect(rect, 56f, 56f, bgPaint)
            canvas.drawRoundRect(rect, 56f, 56f, borderPaint)

            // Top Label
            val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.accentPrimary
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
            }
            canvas.drawText("⚡ WEEKLY PULSE", 32f, 48f, topPaint)

            // Circular Progress Ring in Center
            val ringCenterX = size / 2f
            val ringCenterY = 185f
            val ringRadius = 92f
            val ringRect = RectF(
                ringCenterX - ringRadius,
                ringCenterY - ringRadius,
                ringCenterX + ringRadius,
                ringCenterY + ringRadius
            )

            val trackRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.bgControl
                style = Paint.Style.STROKE
                strokeWidth = 22f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(ringRect, 0f, 360f, false, trackRingPaint)

            val fillRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.accentPrimary
                style = Paint.Style.STROKE
                strokeWidth = 22f
                strokeCap = Paint.Cap.ROUND
            }
            val sweepAngle = (progress * 360f).coerceAtLeast(18f)
            canvas.drawArc(ringRect, -90f, sweepAngle, false, fillRingPaint)

            // Center Ring Text (e.g. "3/4")
            val centerBigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.textPrimary
                textSize = 54f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$doneCount/$goalCount", ringCenterX, ringCenterY + 12f, centerBigPaint)

            val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.textSecondary
                textSize = 19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("SPLITS", ringCenterX, ringCenterY + 42f, centerSubPaint)

            // Bottom Pill: Next Split & Weekly Volume
            val pillRect = RectF(28f, size - 108f, size - 28f, size - 26f)
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = T.bgControl }
            canvas.drawRoundRect(pillRect, 26f, 26f, pillPaint)

            val nextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.accentSecondary
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "NEXT: ${weekSummary.recommendedSplit.uppercase(Locale.US)}",
                ringCenterX,
                size - 72f,
                nextPaint
            )

            val volPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = T.textPrimary
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("📦 Vol: $volFormatted", ringCenterX, size - 42f, volPaint)

            return bmp
        }
    }
}

// ============================================================================
// 4. STRAVA-STYLE INSTAGRAM STORY IMAGE CARD GENERATOR (1080x1920)
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
        val cardBitmap = generateStravaWorkoutCardBitmap(
            context = context,
            splitTitle = splitTitle,
            durationSeconds = durationSeconds,
            exercises = exercises,
            history = history,
            bodyWeightKg = bodyWeightKg,
            photoPath = photoPath
        )

        val folder = File(context.filesDir, "workout_photos").apply { mkdirs() }
        val shareFile = File(folder, "gym_story_share_${System.currentTimeMillis()}.jpg")
        FileOutputStream(shareFile).use { out ->
            cardBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            shareFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "⚡ ${splitTitle.uppercase(Locale.US)} Session Complete! Crushed with Gym Tracker AI."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, "Share Workout Card to Instagram / WhatsApp").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to generate share card", Toast.LENGTH_SHORT).show()
    }
}

private fun generateStravaWorkoutCardBitmap(
    context: Context,
    splitTitle: String,
    durationSeconds: Long,
    exercises: List<ExerciseEntry>,
    history: List<CompletedWorkout>,
    bodyWeightKg: Float,
    photoPath: String?
): Bitmap {
    val W = 1080
    val H = 1920
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val T = resolveNativeCanvasTheme(context)

    // 1. Draw Background (Pump Photo if attached, or Luxury Neon Grid)
    val pumpBitmap = if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
        BitmapFactory.decodeFile(photoPath)
    } else null

    if (pumpBitmap != null) {
        // Center-crop pump photo across the 1080x1920 canvas
        val scale = maxOf(W.toFloat() / pumpBitmap.width, H.toFloat() / pumpBitmap.height)
        val scaledW = pumpBitmap.width * scale
        val scaledH = pumpBitmap.height * scale
        val dx = (W - scaledW) / 2f
        val dy = (H - scaledH) / 2f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(pumpBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

        // Strava-style cinematic top & bottom dark gradient vignette
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(
                    Color.argb(190, 7, 8, 10),
                    Color.argb(40, 7, 8, 10),
                    Color.argb(215, 7, 8, 10),
                    Color.argb(248, 7, 8, 10)
                ),
                floatArrayOf(0f, 0.38f, 0.66f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), vignettePaint)
    } else {
        // Luxury Dark Carbon Background + Subtle Tactical Grid + Accent Glow
        canvas.drawColor(Color.parseColor("#07080A"))
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                W / 2f, H * 0.28f, 680f,
                Color.argb(65, Color.red(T.accentPrimary), Color.green(T.accentPrimary), Color.blue(T.accentPrimary)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glowPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 12
            strokeWidth = 2f
        }
        for (x in 0..W step 90) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), H.toFloat(), gridPaint)
        for (y in 0..H step 90) canvas.drawLine(0f, y.toFloat(), W.toFloat(), y.toFloat(), gridPaint)
    }

    // 2. Top Branding Pill & Date Stamp
    val topPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 17, 19, 24)
    }
    val topPillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = T.accentPrimary
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val topPillRect = RectF(64f, 88f, 490f, 158f)
    canvas.drawRoundRect(topPillRect, 35f, 35f, topPillPaint)
    canvas.drawRoundRect(topPillRect, 35f, 35f, topPillStroke)

    val brandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = T.accentPrimary
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.1f
    }
    canvas.drawText("⚡ GYM TRACKER AI", 98f, 133f, brandTextPaint)

    val dateStr = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US).format(Date())
    val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 210
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(dateStr.uppercase(Locale.US), W - 64f, 133f, datePaint)

    // 3. Calculate Workout Stats
    val performed = exercises.filter { !it.isSkipped }
    var workSetsDone = 0
    var totalVolFloat = 0f
    var prCount = 0
    val liftSummaries = mutableListOf<Pair<String, String>>()

    for (ex in performed) {
        val rec = computeExerciseRecords(ex.name, history)
        val validSets = ex.sets.filter {
            !it.isWarmup && it.intensityType != "WARMUP" && parseRepsClean(it.reps, 0) > 0
        }
        if (validSets.isNotEmpty()) {
            val topSet = validSets.maxByOrNull { parseWeightClean(it.weight, 0f) } ?: validSets.first()
            val tags = buildString {
                if (ex.isSuperset) append(" 🔗")
                if (ex.sets.any { it.intensityType == "DROP" }) append(" 🔥")
            }
            liftSummaries.add("${ex.name}$tags" to "${topSet.weight}kg × ${topSet.reps} (${validSets.size} sets)")
        }
        for (s in ex.sets) {
            if (!s.isWarmup && s.intensityType != "WARMUP") {
                val r = parseRepsClean(s.reps, 0)
                val w = parseWeightClean(s.weight, 0f)
                if (r > 0) {
                    workSetsDone++
                    totalVolFloat += (w * r)
                }
            }
            if (isSetPersonalRecord(s, rec)) prCount++
        }
    }
    val totalVol = totalVolFloat.roundToInt()
    val mins = (durationSeconds / 60L).coerceAtLeast(1L)

    // 4. Main Strava Summary HUD Box
    val boxTop = if (pumpBitmap != null) 1020f else 480f
    val boxBottom = H - 96f
    val boxRect = RectF(56f, boxTop, W - 56f, boxBottom)

    val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(228, 17, 19, 24)
    }
    val hudBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = T.accentPrimary
        alpha = 180
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawRoundRect(boxRect, 54f, 54f, hudBgPaint)
    canvas.drawRoundRect(boxRect, 54f, 54f, hudBorderPaint)

    // Workout Split Title & Subtitle inside HUD Box
    val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = T.accentSecondary
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.12f
    }
    val bwSuffix = if (bodyWeightKg > 0f) " • BW: ${formatKgClean(bodyWeightKg)}KG" else ""
    canvas.drawText("SESSION COMPLETE$bwSuffix", 104f, boxTop + 72f, subHeaderPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 74f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(splitTitle.uppercase(Locale.US), 104f, boxTop + 154f, titlePaint)

    // 4 Stat Pills Row (TIME | VOLUME | SETS | PRs)
    val statRowTop = boxTop + 195f
    val statRowBottom = statRowTop + 160f
    val statGap = 20f
    val statWidth = ((W - 208f) - (statGap * 3)) / 4f

    val stats = listOf(
        "${mins}m" to "DURATION",
        "${totalVol}kg" to "VOLUME",
        "$workSetsDone" to "WORK SETS",
        "🏆 $prCount" to "NEW PRs"
    )

    val statPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 30, 34, 43)
    }
    val statValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = T.accentPrimary
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val statLblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A93A6")
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    stats.forEachIndexed { idx, (valStr, lblStr) ->
        val left = 104f + idx * (statWidth + statGap)
        val right = left + statWidth
        val centerX = (left + right) / 2f
        canvas.drawRoundRect(RectF(left, statRowTop, right, statRowBottom), 28f, 28f, statPillPaint)
        canvas.drawText(valStr, centerX, statRowTop + 74f, statValPaint)
        canvas.drawText(lblStr, centerX, statRowTop + 122f, statLblPaint)
    }

    // Top Lifts List inside HUD Box
    val listStartY = statRowBottom + 68f
    val secTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A93A6")
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.08f
    }
    canvas.drawText("KEY LIFTS & TOP SETS", 104f, listStartY, secTitlePaint)

    val exNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 31f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val exSetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = T.accentSecondary
        textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 25
        strokeWidth = 2f
    }

    val maxLiftsToShow = if (pumpBitmap != null) 5 else 8
    liftSummaries.take(maxLiftsToShow).forEachIndexed { i, (exName, setSummary) ->
        val rowY = listStartY + 62f + (i * 70f)
        if (rowY < boxBottom - 36f) {
            val truncatedName = if (exName.length > 24) exName.take(22) + "…" else exName
            canvas.drawText(truncatedName, 104f, rowY, exNamePaint)
            canvas.drawText(setSummary, W - 104f, rowY, exSetPaint)
            canvas.drawLine(104f, rowY + 22f, W - 104f, rowY + 22f, dividerPaint)
        }
    }

    return bmp
}