package com.example.gymtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    splits: List<WorkoutSplit>,
    history: List<CompletedWorkout>,
    userProfile: UserProfile,
    programName: String,
    activeSplitTitle: String?,
    /** Exercises in the running workout, which may differ from the day's list. */
    activeExerciseCount: Int,
    onStartWorkout: (WorkoutSplit) -> Unit,
    onStartQuickWorkout: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val program = programFor(userProfile.splitProgram)
    val week = remember(history, program) { computeWeekSummary(history, 0, userProfile.splitProgram) }
    val recovery = remember(history) { computeMuscleRecoveryStatus(history) }
    val upNext = when (activeSplitTitle) {
        QUICK_WORKOUT_TITLE -> WorkoutSplit(QUICK_WORKOUT_TITLE, "Exercises you pick as you go", "")
        else -> splits.find { it.title == (activeSplitTitle ?: week.recommendedSplit) } ?: splits.first()
    }

    // Filled only while the tips are showing: positions change on every scroll frame.
    val tutorialBounds = remember { mutableStateMapOf<String, Rect>() }
    var showTips by remember(TutorialPrefs.replayGeneration) { mutableStateOf(!TutorialPrefs.hasSeenHomeTutorial(context)) }
    var tipIndex by remember(TutorialPrefs.replayGeneration) { mutableIntStateOf(0) }
    val tips = remember {
        listOf(
            TutorialStepInfo("UP_NEXT", "Your next workout", "We pick the workout you haven't done yet this week. Tap Start when you're ready."),
            TutorialStepInfo("WEEK", "Your week at a glance", "Each dot is a day. Filled dots are days you trained."),
            TutorialStepInfo("RECOVERY", "Muscle recovery", "A rough guide to which muscles are rested. Tap it for more detail.")
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp).enterAnimation(0),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()),
                            style = AppType.label,
                            color = P.textSecondary
                        )
                        Text(greeting(), style = AppType.display, color = P.textPrimary)
                    }
                    Pill(
                        text = formatWeightKg(userProfile.currentWeightKg),
                        color = P.textPrimary,
                        container = P.bgCard,
                        modifier = Modifier
                            .border(1.dp, P.borderSubtle, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .pressable(onClick = onOpenProgress)
                    )
                }
            }

            item {
                UpNextCard(
                    split = upNext,
                    history = history,
                    isInProgress = activeSplitTitle != null,
                    activeExerciseCount = activeExerciseCount,
                    onStart = { if (upNext.title == QUICK_WORKOUT_TITLE) onStartQuickWorkout() else onStartWorkout(upNext) },
                    modifier = Modifier
                        .enterAnimation(1)
                        .then(if (showTips) Modifier.onGloballyPositioned { tutorialBounds["UP_NEXT"] = it.boundsInRoot() } else Modifier)
                )
            }

            item {
                WeekCard(
                    week = week,
                    programName = programName,
                    history = history,
                    bodyWeightKg = userProfile.currentWeightKg,
                    onOpenHistory = onOpenHistory,
                    modifier = Modifier
                        .enterAnimation(2)
                        .then(if (showTips) Modifier.onGloballyPositioned { tutorialBounds["WEEK"] = it.boundsInRoot() } else Modifier)
                )
            }

            item {
                RecoveryStrip(
                    items = recovery,
                    onClick = onOpenProgress,
                    modifier = Modifier
                        .enterAnimation(3)
                        .then(if (showTips) Modifier.onGloballyPositioned { tutorialBounds["RECOVERY"] = it.boundsInRoot() } else Modifier)
                )
            }

            val others = splits.filter { it.title != upNext.title }
            val showQuick = activeSplitTitle != QUICK_WORKOUT_TITLE
            if (others.isNotEmpty() || showQuick) {
                item { SectionHeader("Other workouts", Modifier.enterAnimation(4)) }
                // One grouped list rather than a card per day; an empty workout for off-plan days last.
                item {
                    AppCard(Modifier.fillMaxWidth().enterAnimation(5), contentPadding = PaddingValues(vertical = 4.dp)) {
                        others.forEachIndexed { i, split ->
                            if (i > 0) Box(Modifier.padding(start = 74.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
                            SplitRow(
                                split = split,
                                doneThisWeek = split.title in week.completedCoreSplits,
                                lastDone = history.firstOrNull { it.splitTitle == split.title },
                                onClick = { onStartWorkout(split) }
                            )
                        }
                        if (showQuick) {
                            if (others.isNotEmpty()) Box(Modifier.padding(start = 74.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
                            QuickWorkoutRow(onClick = onStartQuickWorkout)
                        }
                    }
                }
            }
        }

        if (showTips) {
            SpotlightTutorialOverlay(
                steps = tips,
                currentStepIndex = tipIndex,
                targetBounds = tutorialBounds,
                onNext = {
                    if (tipIndex < tips.lastIndex) tipIndex++
                    else {
                        TutorialPrefs.markHomeTutorialSeen(context)
                        showTips = false
                    }
                },
                onPrev = { if (tipIndex > 0) tipIndex-- },
                onSkip = {
                    TutorialPrefs.markHomeTutorialSeen(context)
                    showTips = false
                }
            )
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

fun relativeDay(timestamp: Long): String {
    val days = ((System.currentTimeMillis() - timestamp) / (24L * 60 * 60 * 1000)).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 14 -> "$days days ago"
        else -> "${days / 7} weeks ago"
    }
}

@Composable
private fun UpNextCard(
    split: WorkoutSplit,
    history: List<CompletedWorkout>,
    isInProgress: Boolean,
    activeExerciseCount: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val plannedCount = remember(split.title, isInProgress) {
        if (isInProgress) 0 else LocalStorage.loadTemplateForSplit(context, split.title).size
    }
    val exerciseCount = if (isInProgress) activeExerciseCount else plannedCount
    val last = history.firstOrNull { it.splitTitle == split.title }

    AppCard(modifier = modifier.fillMaxWidth(), elevated = true, contentPadding = PaddingValues(0.dp)) {
        // A warm accent wash across the top gives the card some depth without being loud.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(P.accentSoft, P.bgElevated)))
                .padding(20.dp)
        ) {
            Text(if (isInProgress) "In progress" else "Up next", style = AppType.overline, color = P.accentPrimary)
            Spacer(Modifier.height(6.dp))
            Text(split.title, style = AppType.display, color = P.textPrimary)
            Text(split.subtitle, style = AppType.body, color = P.textSecondary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (exerciseCount == 1) "1 exercise" else "$exerciseCount exercises", container = P.bgCard.copy(alpha = 0.7f))
                Pill(
                    last?.let { "Last done ${relativeDay(it.id)}" } ?: "Not done yet",
                    container = P.bgCard.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = if (isInProgress) "Resume workout" else "Start workout",
                icon = Icons.Rounded.PlayArrow,
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeekCard(
    week: WeekSummaryData,
    programName: String,
    history: List<CompletedWorkout>,
    bodyWeightKg: Float,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val done = week.completedCoreSplits.size
    val goal = week.weeklyGoal
    val trainedDays = remember(week) {
        week.workouts.map { Calendar.getInstance().apply { timeInMillis = it.id }.get(Calendar.DAY_OF_WEEK) }.toSet()
    }
    val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

    AppCard(modifier = modifier.fillMaxWidth(), onClick = onOpenHistory) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("This week", style = AppType.headline, color = P.textPrimary)
                Text(
                    when {
                        done >= goal -> "All $goal done. Nice work."
                        done == 0 -> "$goal workouts planned · $programName"
                        else -> "${goal - done} to go · $programName"
                    },
                    style = AppType.caption,
                    color = P.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // A count reads at a glance; an empty ring at the start of the week looked unfinished.
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done", style = AppType.bigNumber, color = if (done > 0) P.textPrimary else P.textTertiary)
                Text("/$goal", style = AppType.number, color = P.textTertiary, modifier = Modifier.padding(bottom = 3.dp))
            }
            if (week.workouts.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                CircleIconButton(
                    icon = Icons.Rounded.IosShare,
                    contentDescription = "Share last workout",
                    onClick = {
                        val latest = week.workouts.first()
                        shareWorkoutCardImage(
                            latest.splitTitle, latest.durationSeconds, latest.exercises,
                            history.filter { it.id < latest.id }, latest.photoPath, whenMillis = latest.id
                        )
                    },
                    size = 36.dp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, letter ->
                val day = i + 1 // Calendar.SUNDAY == 1
                val trained = day in trainedDays
                val isToday = day == today
                Column(Modifier.width(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        letter,
                        style = AppType.caption.copy(fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (isToday) P.textPrimary else P.textTertiary
                    )
                    Spacer(Modifier.height(6.dp))
                    // Trained days are filled; the rest are small marks, so a fresh week isn't a row of empty buttons.
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        when {
                            trained -> Box(Modifier.fillMaxSize().clip(CircleShape).background(P.accentPrimary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, tint = P.onAccentPrimary, modifier = Modifier.size(14.dp))
                            }
                            isToday -> Box(Modifier.size(8.dp).clip(CircleShape).background(P.accentPrimary))
                            else -> Box(Modifier.size(6.dp).clip(CircleShape).background(P.borderStrong))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryStrip(items: List<MuscleRecoveryItem>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    val notReady = items.filter { it.status != RecoveryStatus.READY }.sortedBy { it.recoveryPercent }
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recovery", style = AppType.headline, color = P.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = P.textTertiary, modifier = Modifier.size(16.dp))
        }
        if (notReady.isEmpty()) {
            // Five green 100s say less than one sentence.
            Text(
                "Everything's recovered. Train whatever you like today.",
                style = AppType.caption,
                color = P.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        } else {
            Text(
                "${notReady.size} of ${items.size} muscles still recovering",
                style = AppType.caption,
                color = P.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(14.dp))
            // Only the ones that aren't ready; eleven rings don't fit and rested ones say nothing.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                notReady.take(5).forEach { item ->
                    val color = recoveryColor(item.status, P)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(
                            progress = item.recoveryPercent / 100f,
                            modifier = Modifier.size(46.dp),
                            strokeWidth = 5.dp,
                            color = color
                        ) {
                            Text("${item.recoveryPercent}", style = AppType.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = P.textPrimary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(recoveryLabel(item.muscleGroup), style = AppType.caption, color = P.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

fun recoveryColor(status: RecoveryStatus, P: GymPalette) = when (status) {
    RecoveryStatus.TIRED -> P.dangerText
    RecoveryStatus.RECOVERING -> P.warningText
    RecoveryStatus.READY -> P.successText
}

@Composable
private fun SplitRow(
    split: WorkoutSplit,
    doneThisWeek: Boolean,
    lastDone: CompletedWorkout?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val P = LocalPalette.current
    Row(
        modifier.fillMaxWidth().pressable(pressedScale = 0.99f, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (doneThisWeek) P.successSurface else P.bgControl),
                contentAlignment = Alignment.Center
            ) {
                if (doneThisWeek) Icon(Icons.Rounded.Check, "Done this week", tint = P.successText, modifier = Modifier.size(22.dp))
                // The day's place in the program; letters repeat too often (Push, Pull).
                else Text(split.code.trimStart('0').ifBlank { split.title.take(1) }, style = AppType.headline.copy(fontFeatureSettings = "tnum"), color = P.textSecondary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(split.title, style = AppType.bodyStrong, color = P.textPrimary)
                Text(
                    if (doneThisWeek) "Done this week" else lastDone?.let { "Last done ${relativeDay(it.id)}" } ?: split.subtitle,
                    style = AppType.caption,
                    color = if (doneThisWeek) P.successText else P.textSecondary
                )
            }
            Icon(Icons.Rounded.PlayArrow, "Start ${split.title}", tint = P.textTertiary, modifier = Modifier.size(22.dp))
    }
}

/** Starts a workout with no exercises, for days that don't follow the program. */
@Composable
private fun QuickWorkoutRow(onClick: () -> Unit) {
    val P = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.99f, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(P.bgControl), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = P.textSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Empty workout", style = AppType.bodyStrong, color = P.textPrimary)
            Text("Pick exercises as you go", style = AppType.caption, color = P.textSecondary)
        }
        Icon(Icons.Rounded.PlayArrow, "Start an empty workout", tint = P.textTertiary, modifier = Modifier.size(22.dp))
    }
}
