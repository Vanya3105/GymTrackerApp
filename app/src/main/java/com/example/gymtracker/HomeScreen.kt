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
    onStartWorkout: (WorkoutSplit) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val program = programFor(userProfile.splitProgram)
    val week = remember(history, program) { computeWeekSummary(history, 0, userProfile.splitProgram) }
    val recovery = remember(history) { computeMuscleRecoveryStatus(history) }
    val upNext = splits.find { it.title == (activeSplitTitle ?: week.recommendedSplit) } ?: splits.first()

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
                    onStart = { onStartWorkout(upNext) },
                    modifier = Modifier
                        .enterAnimation(1)
                        .onGloballyPositioned { tutorialBounds["UP_NEXT"] = it.boundsInRoot() }
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
                        .onGloballyPositioned { tutorialBounds["WEEK"] = it.boundsInRoot() }
                )
            }

            item {
                RecoveryStrip(
                    items = recovery,
                    onClick = onOpenProgress,
                    modifier = Modifier
                        .enterAnimation(3)
                        .onGloballyPositioned { tutorialBounds["RECOVERY"] = it.boundsInRoot() }
                )
            }

            val others = splits.filter { it.title != upNext.title }
            if (others.isNotEmpty()) {
                item { SectionHeader("Other workouts", Modifier.enterAnimation(4)) }
                itemsIndexed(others, key = { _, s -> s.title }) { i, split ->
                    SplitRow(
                        split = split,
                        doneThisWeek = split.title in week.completedCoreSplits,
                        lastDone = history.firstOrNull { it.splitTitle == split.title },
                        onClick = { onStartWorkout(split) },
                        modifier = Modifier.enterAnimation(5 + i)
                    )
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
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val exerciseCount = remember(split.title) { LocalStorage.loadTemplateForSplit(context, split.title).size }
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
                Pill("$exerciseCount exercises", container = P.bgCard.copy(alpha = 0.7f))
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
            ProgressRing(progress = done / goal.toFloat(), modifier = Modifier.size(72.dp), strokeWidth = 8.dp) {
                Text("$done/$goal", style = AppType.number.copy(fontSize = AppType.headline.fontSize), color = P.textPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("This week", style = AppType.headline, color = P.textPrimary)
                Text(
                    when {
                        done >= goal -> "All $goal workouts done. Nice."
                        done == 0 -> "$goal workouts planned"
                        else -> "${goal - done} to go · $programName"
                    },
                    style = AppType.label.copy(fontWeight = FontWeight.Normal),
                    color = P.textSecondary,
                    maxLines = 2
                )
            }
            if (week.workouts.isNotEmpty()) {
                CircleIconButton(
                    icon = Icons.Rounded.IosShare,
                    contentDescription = "Share last workout",
                    onClick = {
                        val latest = week.workouts.first()
                        shareWorkoutCardImage(
                            context, latest.splitTitle, latest.durationSeconds, latest.exercises,
                            history.filter { it.id < latest.id }, bodyWeightKg, latest.photoPath
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, letter ->
                val day = i + 1 // Calendar.SUNDAY == 1
                val trained = day in trainedDays
                val isToday = day == today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(letter, style = AppType.caption, color = if (isToday) P.textPrimary else P.textTertiary)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (trained) P.accentPrimary else P.bgControl)
                            .then(if (isToday && !trained) Modifier.border(1.5.dp, P.accentPrimary, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (trained) Icon(Icons.Rounded.Check, null, tint = P.onAccentPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryStrip(items: List<MuscleRecoveryItem>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    val readyCount = items.count { it.status == RecoveryStatus.READY }
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recovery", style = AppType.headline, color = P.textPrimary, modifier = Modifier.weight(1f))
            Text(
                if (readyCount == items.size) "All rested" else "$readyCount of ${items.size} rested",
                style = AppType.label,
                color = P.textSecondary
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = P.textTertiary, modifier = Modifier.padding(start = 6.dp).size(16.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Eleven rings don't fit a phone's width: show the least rested; the rest are a tap away.
            items.sortedBy { it.recoveryPercent }.take(5).forEach { item ->
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
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (doneThisWeek) P.successSurface else P.bgControl),
                contentAlignment = Alignment.Center
            ) {
                if (doneThisWeek) Icon(Icons.Rounded.Check, "Done this week", tint = P.successText, modifier = Modifier.size(22.dp))
                else Text(split.title.take(1), style = AppType.headline, color = P.textSecondary)
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
            Icon(Icons.Rounded.PlayArrow, "Start ${split.title}", tint = P.accentPrimary, modifier = Modifier.size(22.dp))
        }
    }
}
