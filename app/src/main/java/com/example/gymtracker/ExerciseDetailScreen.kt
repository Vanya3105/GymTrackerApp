package com.example.gymtracker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Full history of one exercise: chart, records and every logged session. */
@Composable
fun ExerciseDetailScreen(
    exerciseName: String,
    history: List<CompletedWorkout>,
    haptics: Haptics,
    canRename: Boolean,
    onRename: (String) -> Boolean,
    onBack: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val stats = remember(exerciseName, history) { buildExerciseHistory(exerciseName, history) }
    val info = remember(exerciseName) { ExerciseDatabase.find(exerciseName) }
    val muscle = info?.muscle ?: remember(exerciseName, history) {
        history.firstNotNullOfOrNull { w -> w.exercises.firstOrNull { exerciseKey(it.name) == exerciseKey(exerciseName) }?.targetMuscle }
    }
    val note = remember(exerciseName, history) { LocalStorage.getExerciseNote(context, exerciseName) }
    var showRename by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(P.bgPrimary),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.statusBarsPadding().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                if (canRename && stats.sessions.isNotEmpty()) {
                    CircleIconButton(Icons.Rounded.Edit, "Rename", { haptics.tick(); showRename = true })
                }
            }
        }

        item {
            Column {
                Text(exerciseName, style = AppType.display, color = P.textPrimary)
                val sub = listOfNotNull(muscle?.ifBlank { null }, info?.equipment).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = AppType.body, color = P.textSecondary)
            }
        }

        if (stats.sessions.isEmpty()) {
            item { EmptyState(Icons.Rounded.FitnessCenter, "Nothing logged yet", "Finish a workout with this exercise to see your progress.") }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(formatWeightKg(stats.best1RmKg), "Est. 1-rep max", Modifier.weight(1f))
                StatTile(formatWeightKg(stats.heaviestKg), "Heaviest", Modifier.weight(1f))
                StatTile("${stats.sessions.size}", if (stats.sessions.size == 1) "Session" else "Sessions", Modifier.weight(1f))
            }
        }

        item { ProgressChartCard(stats, haptics, Modifier.enterAnimation(0)) }

        if (note.isNotBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.bgControl).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PushPin, null, tint = P.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(note, style = AppType.body, color = P.textPrimary)
                }
            }
        }

        item { SectionHeader("Rep records") }
        item { RepRecordsCard(stats, Modifier.enterAnimation(1)) }

        item { SectionHeader("History") }
        items(stats.sessions, key = { it.workoutId }) { session -> SessionCard(session) }
    }

    if (showRename) {
        RenameDialog(
            current = exerciseName,
            onDismiss = { showRename = false },
            onRename = { newName ->
                if (onRename(newName)) {
                    haptics.confirm()
                    showRename = false
                    true
                } else false
            }
        )
    }
}

@Composable
private fun ProgressChartCard(stats: ExerciseHistory, haptics: Haptics, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    var metric by rememberSaveable { mutableStateOf(ChartMetric.EST_1RM) }
    var range by rememberSaveable { mutableStateOf(ChartRange.ALL) }
    val now = remember { System.currentTimeMillis() }
    val points = remember(stats, metric, range) { stats.chartPoints(metric, range, now) }
    var selected by remember(points) { mutableStateOf<Int?>(null) }

    fun format(v: Float) = if (metric == ChartMetric.VOLUME) formatVolume(v.roundToInt()) else formatWeightKg(v)

    AppCard(modifier.fillMaxWidth()) {
        SegmentedControl(
            options = ChartMetric.entries.map { it to it.label },
            selected = metric,
            onSelect = { haptics.tick(); metric = it }
        )
        Spacer(Modifier.height(16.dp))

        val shown = selected?.let { points.getOrNull(it) } ?: points.lastOrNull()
        if (shown != null) {
            Text(format(shown.second), style = AppType.bigNumber, color = P.textPrimary)
            Text(
                (if (selected == null) "Latest · " else "") + DateUtils.formatShortDayYear(Date(shown.first)),
                style = AppType.caption,
                color = P.textSecondary
            )
            Spacer(Modifier.height(12.dp))
        }

        if (points.size >= 2) {
            SessionChart(points, selected, onSelect = { idx ->
                if (idx != selected) haptics.tick()
                selected = idx
            })
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DateUtils.formatShortDayYear(Date(points.first().first)), style = AppType.caption, color = P.textTertiary)
                Text(DateUtils.formatShortDayYear(Date(points.last().first)), style = AppType.caption, color = P.textTertiary)
            }
        } else {
            Text(
                if (points.isEmpty()) "No sessions in this period." else "Log one more session to see a trend.",
                style = AppType.label.copy(fontWeight = FontWeight.Normal),
                color = P.textTertiary,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = ChartRange.entries.map { it to it.label },
            selected = range,
            onSelect = { haptics.tick(); range = it }
        )
    }
}

/**
 * Line chart on a real time axis. Tap or drag to inspect a session; the min/max of the visible
 * values are labelled on the right.
 */
@Composable
private fun SessionChart(points: List<Pair<Long, Float>>, selected: Int?, onSelect: (Int?) -> Unit) {
    val P = LocalPalette.current
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    val minV = points.minOf { it.second }
    val maxV = points.maxOf { it.second }
    val currentOnSelect by rememberUpdatedState(onSelect)

    Row(Modifier.fillMaxWidth().height(160.dp)) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(points) {
                    detectTapGestures { currentOnSelect(nearestPoint(points, it.x, size.width.toFloat(), 8.dp.toPx())) }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        currentOnSelect(nearestPoint(points, change.position.x, size.width.toFloat(), 8.dp.toPx()))
                    }
                }
        ) {
            val padX = 8.dp.toPx()
            val padY = 10.dp.toPx()
            val w = size.width - padX * 2
            val h = size.height - padY * 2
            val range = (maxV - minV).takeIf { it > 0f } ?: 1f
            val pts = points.mapIndexed { i, (_, v) ->
                val fx = xFraction(points, i)
                val fy = if (maxV == minV) 0.5f else (v - minV) / range
                Offset(padX + fx * w, padY + (1f - fy) * h)
            }

            // Hairlines at max, middle and min.
            for (f in listOf(0f, 0.5f, 1f)) {
                val y = padY + f * h
                drawLine(P.borderSubtle, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }

            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            clipRect(right = size.width * reveal.value + 6.dp.toPx()) {
                drawPath(fill, Brush.verticalGradient(listOf(P.accentPrimary.copy(alpha = 0.20f), Color.Transparent)))
                drawPath(line, P.accentPrimary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                if (pts.size <= 40) pts.forEach { pt ->
                    drawCircle(P.bgCard, 4.dp.toPx(), pt)
                    drawCircle(P.accentPrimary, 2.5.dp.toPx(), pt)
                }
            }

            selected?.let { pts.getOrNull(it) }?.let { pt ->
                drawLine(P.textTertiary, Offset(pt.x, 0f), Offset(pt.x, size.height), 1.dp.toPx())
                drawCircle(P.bgCard, 7.dp.toPx(), pt)
                drawCircle(P.accentPrimary, 5.dp.toPx(), pt)
            }
        }
        Column(
            Modifier.width(48.dp).fillMaxHeight().padding(vertical = 2.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(maxV, (maxV + minV) / 2f, minV).forEach {
                Text(compactNumber(it), style = AppType.caption.copy(fontFeatureSettings = "tnum"), color = P.textTertiary)
            }
        }
    }
}

@Composable
private fun RepRecordsCard(stats: ExerciseHistory, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    val byReps = stats.repRecords.associateBy { it.reps }
    AppCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        REP_RECORD_TARGETS.forEachIndexed { i, reps ->
            if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
            val rec = byReps[reps]
            Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (reps == 1) "1 rep" else "$reps reps", style = AppType.body, color = P.textSecondary, modifier = Modifier.weight(1f))
                if (rec == null) {
                    Text("—", style = AppType.number, color = P.textTertiary)
                } else {
                    Text(DateUtils.formatShortDayYear(Date(rec.workoutId)), style = AppType.caption, color = P.textTertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(formatWeightKg(rec.weightKg), style = AppType.number, color = P.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: ExerciseSession) {
    val P = LocalPalette.current
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(DateUtils.formatShortDayYear(Date(session.workoutId)), style = AppType.bodyStrong, color = P.textPrimary)
                Text(session.splitTitle, style = AppType.caption, color = P.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (session.isRecord) {
                Pill("Best yet", color = P.warningText, container = P.warningSurface, icon = Icons.Rounded.EmojiEvents)
            }
        }
        Spacer(Modifier.height(10.dp))
        var workNumber = 0
        session.sets.forEach { set ->
            val tag = when {
                set.isWarmupSet -> "W"
                else -> "${++workNumber}"
            }
            val kind = when (set.intensityType) {
                "DROP" -> "drop set"
                "REST_PAUSE" -> "rest-pause"
                else -> null
            }
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tag, style = AppType.caption.copy(fontWeight = FontWeight.SemiBold), color = if (set.isWarmupSet) P.textTertiary else P.textSecondary, modifier = Modifier.width(24.dp))
                Text(
                    "${formatWeightKg(set.weightKg)} × ${set.repCount}",
                    style = AppType.body.copy(fontFeatureSettings = "tnum"),
                    color = if (set.isWarmupSet) P.textSecondary else P.textPrimary
                )
                if (kind != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(kind, style = AppType.caption, color = P.textTertiary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Est. 1RM ${formatWeightKg(session.estimated1RmKg)} · Volume ${formatVolume(session.volumeKg.roundToInt())}",
            style = AppType.caption,
            color = P.textTertiary
        )
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Boolean) {
    val P = LocalPalette.current
    // Starts focused with the old name selected, so typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val text = field.text
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.bgElevated,
        title = { Text("Rename exercise", style = AppType.headline, color = P.textPrimary) },
        text = {
            Column {
                Text("Your history, notes and workouts keep it under the new name.", style = AppType.body, color = P.textSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = P.accentPrimary,
                        unfocusedBorderColor = P.borderStrong,
                        focusedTextColor = P.textPrimary,
                        unfocusedTextColor = P.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && text.trim() != current,
                onClick = { if (!onRename(text.trim())) error = "Another exercise already has that name." }
            ) { Text("Rename", color = P.accentPrimary, style = AppType.label) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.textSecondary, style = AppType.label) } }
    )
}

/** Horizontal position (0..1) of point [i] on the time axis; evenly spaced if all share one time. */
private fun xFraction(points: List<Pair<Long, Float>>, i: Int): Float {
    val minT = points.first().first
    val span = points.last().first - minT
    return if (span <= 0L) i / (points.size - 1f) else (points[i].first - minT) / span.toFloat()
}

private fun nearestPoint(points: List<Pair<Long, Float>>, x: Float, width: Float, padX: Float): Int =
    points.indices.minBy { i -> abs(padX + xFraction(points, i) * (width - padX * 2) - x) }

/** "1.2k" style labels for the chart's value axis. */
private fun compactNumber(kg: Float): String = Units.current.fromKg(kg).let { v ->
    when {
    v >= 10_000f -> "${(v / 1000f).roundToInt()}k"
    v >= 1_000f -> "${((v / 100f).roundToInt() / 10f)}k"
        else -> formatKgClean((v * 2f).roundToInt() / 2f)
    }
}
