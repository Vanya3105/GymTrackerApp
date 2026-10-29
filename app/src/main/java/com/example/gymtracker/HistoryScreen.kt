package com.example.gymtracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class HistoryMode { WEEK, MONTH }

/** Workouts per exercise ([historyByExercise]) for the cards' summaries; null outside History. */
private val LocalHistoryIndex = staticCompositionLocalOf<Map<String, List<CompletedWorkout>>?> { null }

@Composable
fun HistoryScreen(
    history: List<CompletedWorkout>,
    userProfile: UserProfile,
    onOpenWorkout: (CompletedWorkout) -> Unit,
    onDeleteWorkout: (Long) -> Unit
) {
    val P = LocalPalette.current
    var mode by rememberSaveable { mutableStateOf(HistoryMode.WEEK) }
    var pendingDelete by remember { mutableStateOf<CompletedWorkout?>(null) }
    var photoToView by remember { mutableStateOf<String?>(null) }

    val byExercise = remember(history) { historyByExercise(history) }
    CompositionLocalProvider(LocalHistoryIndex provides byExercise) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 12.dp)) {
            Text("History", style = AppType.display, color = P.textPrimary)
            Spacer(Modifier.height(14.dp))
            SegmentedControl(
                options = listOf(HistoryMode.WEEK to "Week", HistoryMode.MONTH to "Month"),
                selected = mode,
                onSelect = { mode = it }
            )
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(120))) },
            label = "history_mode"
        ) { m ->
            when (m) {
                HistoryMode.WEEK -> WeekView(history, userProfile, onOpenWorkout, { pendingDelete = it }, { photoToView = it })
                HistoryMode.MONTH -> MonthView(history, onOpenWorkout, { pendingDelete = it }, { photoToView = it })
            }
        }
    }
    }

    pendingDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = P.bgElevated,
            title = { Text("Delete this workout?", style = AppType.headline, color = P.textPrimary) },
            text = { Text("${workout.splitTitle} on ${workout.displayDate.substringBefore(" •")} will be removed for good.", style = AppType.body, color = P.textSecondary) },
            confirmButton = {
                TextButton(onClick = { onDeleteWorkout(workout.id); pendingDelete = null }) {
                    Text("Delete", color = P.dangerText, style = AppType.label)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep", color = P.textSecondary, style = AppType.label) }
            }
        )
    }

    photoToView?.let { path ->
        val photo = rememberPhoto(path)
        val bmp = photo.bitmap
        if (bmp == null) { if (!photo.loading) photoToView = null }
        else AppBottomSheet(onDismiss = { photoToView = null }) {
            Image(
                bitmap = bmp,
                contentDescription = "Workout photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp))
            )
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun WeekView(
    history: List<CompletedWorkout>,
    userProfile: UserProfile,
    onOpen: (CompletedWorkout) -> Unit,
    onDelete: (CompletedWorkout) -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val P = LocalPalette.current
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val program = programFor(userProfile.splitProgram)
    val week = remember(history, weekOffset, program) { computeWeekSummary(history, weekOffset, userProfile.splitProgram) }
    val stats = remember(week) { week.workouts.map { computeWorkoutStats(it.exercises, emptyList()) } }

    Column(Modifier.fillMaxSize().swipeToPage({ weekOffset-- }, { weekOffset++ }, canGoNext = weekOffset < 0)) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous week", { weekOffset-- })
            AnimatedContent(weekOffset, transitionSpec = { pageTransition() }, modifier = Modifier.weight(1f), label = "week_title") { _ ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(week.titleLabel, style = AppType.bodyStrong, color = P.textPrimary)
                    Text(week.dateRangeLabel, style = AppType.caption, color = P.textSecondary)
                }
            }
            CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next week", { weekOffset++ }, enabled = weekOffset < 0)
        }
        // A row of zeros says nothing: the totals only show for weeks with workouts.
        AnimatedVisibility(week.workouts.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${week.workouts.size}", if (week.workouts.size == 1) "Workout" else "Workouts", Modifier.weight(1f))
                StatTile("${stats.sumOf { it.workSets }}", "Sets", Modifier.weight(1f))
                StatTile(formatVolume(stats.sumOf { it.volumeKg }), "Volume", Modifier.weight(1f))
            }
        }
        AnimatedContent(weekOffset, transitionSpec = { pageTransition() }, modifier = Modifier.weight(1f), label = "week_list") { offset ->
            val workouts = remember(history, offset, program) { computeWeekSummary(history, offset, userProfile.splitProgram).workouts }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                workoutList(
                    workouts, history, onOpen, onDelete, onViewPhoto,
                    emptyTitle = if (offset == 0) "No workouts yet this week" else "A rest week",
                    emptyText = if (offset == 0) history.firstOrNull()?.let { "Your last one was ${it.splitTitle}, ${relativeDay(it.id)}." } ?: "Finished workouts show up here."
                    else "Nothing was logged that week."
                )
            }
        }
    }
}

@Composable
private fun MonthView(
    history: List<CompletedWorkout>,
    onOpen: (CompletedWorkout) -> Unit,
    onDelete: (CompletedWorkout) -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val P = LocalPalette.current
    val today = remember { Calendar.getInstance() }
    var year by rememberSaveable { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(today.get(Calendar.MONTH)) }
    val todayKey = remember { DateUtils.formatDayKey(Date()) }
    var selectedKey by rememberSaveable { mutableStateOf(todayKey) }
    val byDate = remember(history) { history.groupBy { it.dateKey } }

    val (firstDayOffset, daysInMonth, monthTitle) = remember(year, month) {
        val cal = Calendar.getInstance().apply { set(year, month, 1) }
        Triple(cal.get(Calendar.DAY_OF_WEEK) - 1, cal.getActualMaximum(Calendar.DAY_OF_MONTH), DateUtils.formatMonthTitle(cal.time))
    }
    // "yyyy-MM-" for this month; day keys are built from it.
    val monthPrefix = remember(year, month) { String.format(Locale.US, "%04d-%02d-", year, month + 1) }
    val workoutsThisMonth = byDate.keys.count { it.startsWith(monthPrefix) }
    fun previousMonth() { if (month == 0) { month = 11; year-- } else month-- }
    fun nextMonth() { if (month == 11) { month = 0; year++ } else month++ }

    AnimatedContent(
        year * 12 + month,
        transitionSpec = { pageTransition() },
        modifier = Modifier.swipeToPage(::previousMonth, ::nextMonth),
        label = "month_page"
    ) { _ ->
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", ::previousMonth, size = 36.dp)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthTitle, style = AppType.bodyStrong, color = P.textPrimary)
                        Text(if (workoutsThisMonth == 1) "1 training day" else "$workoutsThisMonth training days", style = AppType.caption, color = P.textSecondary)
                    }
                    CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", ::nextMonth, size = 36.dp)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                        Text(it, Modifier.weight(1f), style = AppType.caption, color = P.textTertiary, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val rows = (firstDayOffset + daysInMonth + 6) / 7
                for (week in 0 until rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        for (col in 0..6) {
                            val day = week * 7 + col - firstDayOffset + 1
                            if (day !in 1..daysInMonth) {
                                Spacer(Modifier.weight(1f).height(42.dp))
                                continue
                            }
                            val key = monthPrefix + if (day < 10) "0$day" else "$day"
                            val trained = byDate.containsKey(key)
                            val selected = key == selectedKey
                            val isToday = key == todayKey
                            val bg by animateColorAsState(
                                when {
                                    selected -> P.accentPrimary
                                    trained -> P.accentSoft
                                    else -> Color.Transparent
                                }, label = "day_bg"
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .pressable(pressedScale = 0.9f) { selectedKey = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day",
                                    style = if (trained || isToday) AppType.label else AppType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                                    color = when {
                                        selected -> P.onAccentPrimary
                                        trained -> P.accentPrimary
                                        isToday -> P.textPrimary
                                        else -> P.textSecondary
                                    }
                                )
                                if (isToday && !selected) {
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(CircleShape).background(P.textPrimary))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            val label = if (selectedKey == todayKey) "Today" else selectedKey.toDayLabel()
            SectionHeader(label)
        }
        workoutList(byDate[selectedKey].orEmpty(), history, onOpen, onDelete, onViewPhoto, emptyTitle = "Rest day", emptyText = "No workout on this day.", emptyIcon = Icons.Rounded.Event)
    }
    }
}

private fun String.toDayLabel(): String = try {
    val d = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this)
    java.text.SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(d!!)
} catch (e: Exception) { this }

private fun LazyListScope.workoutList(
    workouts: List<CompletedWorkout>,
    allHistory: List<CompletedWorkout>,
    onOpen: (CompletedWorkout) -> Unit,
    onDelete: (CompletedWorkout) -> Unit,
    onViewPhoto: (String) -> Unit,
    emptyTitle: String,
    emptyText: String,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = AppIcons.Today
) {
    if (workouts.isEmpty()) {
        item(key = "empty") { EmptyState(emptyIcon, emptyTitle, emptyText, Modifier.enterAnimation()) }
    } else {
        itemsIndexed(workouts, key = { _, w -> w.id }) { i, workout ->
            WorkoutHistoryCard(
                workout = workout,
                allHistory = allHistory,
                onOpen = { onOpen(workout) },
                onDelete = { onDelete(workout) },
                onViewPhoto = onViewPhoto,
                modifier = Modifier.animateItem().enterAnimation(i)
            )
        }
    }
}

@Composable
fun WorkoutHistoryCard(
    workout: CompletedWorkout,
    allHistory: List<CompletedWorkout>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onViewPhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val photo = rememberPhoto(workout.photoPath)
    val sets = remember(workout.exercises) { workout.exercises.sumOf { ex -> ex.sets.count { !it.isWarmupSet && (it.repCount > 0 || it.isValidWorkSet(ex.type)) } } }
    var menuOpen by remember { mutableStateOf(false) }

    AppCard(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(workout.splitTitle, style = AppType.headline, color = P.textPrimary)
                Text(workout.displayDate.replace(" • ", " · "), style = AppType.caption, color = P.textSecondary)
            }
            if (workout.prsHit > 0) {
                Pill("${workout.prsHit} PR${if (workout.prsHit > 1) "s" else ""}", icon = Icons.Rounded.EmojiEvents, color = P.warningText, container = P.warningSurface)
            }
            Box {
                CircleIconButton(Icons.Rounded.MoreVert, "More", { menuOpen = true }, size = 34.dp, container = Color.Transparent, tint = P.textSecondary)
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = P.bgElevated) {
                    DropdownMenuItem(
                        text = { Text("Share", style = AppType.body) },
                        leadingIcon = { Icon(Icons.Rounded.IosShare, null) },
                        onClick = {
                            menuOpen = false
                            shareWorkoutCardImage(
                                workout.splitTitle, workout.durationSeconds, workout.exercises,
                                allHistory.filter { it.id < workout.id }, workout.photoPath, whenMillis = workout.id
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", style = AppType.body, color = P.dangerText) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = P.dangerText) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(formatDuration(workout.durationSeconds), icon = Icons.Rounded.Schedule)
            Pill("$sets sets")
            if (workout.totalVolumeKg > 0) Pill(formatVolume(workout.totalVolumeKg))
        }
        ExerciseSummary(workout, allHistory)
        // From the watch via Health Connect; keyed on the permissions so it appears once allowed.
        val heart by produceState<HeartRateSummary?>(null, workout.id, HealthSync.granted) {
            value = HealthSync.heartRate(context, workout)
        }
        heart?.let {
            Spacer(Modifier.height(8.dp))
            Pill("${it.average} bpm avg · ${it.max} max", icon = Icons.Rounded.Favorite, color = P.dangerText, container = P.dangerSurface)
        }
        if (workout.skippedExercises.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Skipped: ${workout.skippedExercises.joinToString(", ")}", style = AppType.caption, color = P.warningText)
        }
        val bitmap = photo.bitmap
        if (bitmap != null && workout.photoPath != null) {
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = bitmap,
                contentDescription = "Workout photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .pressable(pressedScale = 0.98f) { onViewPhoto(workout.photoPath) }
            )
        } else if (photo.loading) {
            // Same size as the photo, so the card doesn't jump when it arrives.
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(LocalPalette.current.bgControl))
        }
    }
}

private const val SUMMARY_ROWS = 4

/** Each exercise with its number of work sets and best set; a trophy where it was a new best. */
@Composable
private fun ExerciseSummary(workout: CompletedWorkout, allHistory: List<CompletedWorkout>) {
    val P = LocalPalette.current
    val unit = Units.current
    val byExercise = LocalHistoryIndex.current
    val rows = remember(workout, allHistory, byExercise, unit) {
        workout.exercises.filter { !it.isSkipped }.map { ex ->
            val best = bestSetOf(ex.sets, ex.type)
            // Only earlier workouts that have this exercise: same records, without scanning everything.
            val earlier = (byExercise?.get(ignoreCaseKey(ex.name)) ?: allHistory).filter { it.id < workout.id }
            val records = computeExerciseRecords(ex.name, earlier)
            val isBest = ex.sets.any { isSetPersonalRecord(it, records, ex.type) }
            Triple(ex, best, isBest)
        }
    }
    if (rows.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.bgControl).padding(horizontal = 12.dp, vertical = 8.dp)) {
        rows.take(SUMMARY_ROWS).forEach { (ex, best, isBest) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${ex.workSets.size} ×", style = AppType.caption.copy(fontFeatureSettings = "tnum"), color = P.textTertiary, modifier = Modifier.width(28.dp))
                Text(ex.name, style = AppType.label, color = P.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (isBest) {
                    Icon(Icons.Rounded.EmojiEvents, "New best", tint = P.warningText, modifier = Modifier.padding(end = 4.dp).size(14.dp))
                }
                if (best != null) Text(formatSet(best, ex.type), style = AppType.label.copy(fontFeatureSettings = "tnum"), color = if (isBest) P.warningText else P.textSecondary)
            }
        }
        if (rows.size > SUMMARY_ROWS) {
            val more = rows.size - SUMMARY_ROWS
            Text(if (more == 1) "+1 more exercise" else "+$more more exercises", style = AppType.caption, color = P.textTertiary, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
        }
    }
}

/** A workout photo, or that it's still loading. */
private class PhotoState(val bitmap: ImageBitmap?, val loading: Boolean)

/**
 * A photo from [PhotoStorage]: at once when it's already decoded, otherwise decoded off the main
 * thread (a 1400 px JPEG takes tens of milliseconds, a dropped frame or two mid-scroll).
 */
@Composable
private fun rememberPhoto(path: String?): PhotoState {
    if (path.isNullOrBlank()) return PhotoState(null, loading = false)
    val cached = remember(path) { PhotoStorage.cached(path) }
    if (cached != null) return PhotoState(cached, loading = false)
    val state by produceState(PhotoState(null, loading = true), path) {
        value = PhotoState(withContext(Dispatchers.IO) { PhotoStorage.loadImageBitmap(path) }, loading = false)
    }
    return state
}
