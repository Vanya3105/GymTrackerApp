package com.example.gymtracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    history: List<CompletedWorkout>,
    userProfile: UserProfile,
    weightHistory: List<WeightLogEntry>,
    haptics: Haptics,
    onLogWeight: (Float) -> Unit,
    onDeleteWeight: (Long) -> Unit,
    onOpenExercise: (String) -> Unit
) {
    val P = LocalPalette.current
    var showLogSheet by remember { mutableStateOf(false) }
    var showAllWeighIns by rememberSaveable { mutableStateOf(false) }
    var showAllLifts by rememberSaveable { mutableStateOf(false) }
    val recovery = remember(history) { computeMuscleRecoveryStatus(history) }
    val bestLifts = remember(history) { computeBestLifts(history) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Progress",
                style = AppType.display,
                color = P.textPrimary,
                modifier = Modifier.statusBarsPadding().padding(top = 20.dp, bottom = 2.dp)
            )
        }

        item {
            BodyWeightCard(
                current = userProfile.currentWeightKg,
                weightHistory = weightHistory,
                onLog = { haptics.tick(); showLogSheet = true },
                modifier = Modifier.enterAnimation(0)
            )
        }

        if (weightHistory.isNotEmpty()) {
            item {
                SectionHeader(
                    "Weigh-ins",
                    action = if (weightHistory.size > 4) (if (showAllWeighIns) "Show less" else "See all") else null,
                    onAction = { showAllWeighIns = !showAllWeighIns }
                )
            }
            item {
                val shown = if (showAllWeighIns) weightHistory else weightHistory.take(4)
                AppCard(
                    Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    shown.forEachIndexed { idx, entry ->
                        if (idx > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
                        WeighInRow(entry, weightHistory.getOrNull(idx + 1), onDelete = { haptics.tick(); onDeleteWeight(entry.id) })
                    }
                }
            }
        }

        item { SectionHeader("Recovery") }
        item { RecoveryDetailCard(recovery, Modifier.enterAnimation(1)) }

        item {
            SectionHeader(
                "Best lifts",
                action = if (bestLifts.size > 6) (if (showAllLifts) "Show less" else "See all") else null,
                onAction = { showAllLifts = !showAllLifts }
            )
        }
        if (bestLifts.isEmpty()) {
            item { EmptyState(Icons.Rounded.EmojiEvents, "No lifts yet", "Finish a workout and your best sets show up here.") }
        } else {
            item {
                AppCard(
                    Modifier.fillMaxWidth().enterAnimation(2).animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    (if (showAllLifts) bestLifts else bestLifts.take(6)).forEachIndexed { i, lift ->
                        if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
                        Row(
                            Modifier.fillMaxWidth().pressable { onOpenExercise(lift.name) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(lift.name, style = AppType.bodyStrong, color = P.textPrimary)
                                Text("Best set ${lift.bestSet?.let { formatSet(it, lift.type) } ?: "—"}", style = AppType.caption, color = P.textSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(lift.headline, style = AppType.number, color = P.textPrimary)
                                Text(lift.headlineLabel, style = AppType.caption, color = P.textTertiary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = P.textTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showLogSheet) {
        LogWeightSheet(
            startKg = userProfile.currentWeightKg.takeIf { it > 0f } ?: 70f,
            onDismiss = { showLogSheet = false },
            onSave = { kg ->
                haptics.confirm()
                onLogWeight(kg)
                showLogSheet = false
            }
        )
    }
}

private data class BestLift(
    val name: String,
    val type: ExerciseType,
    val bestSet: SetEntry?,
    /** Weighted lifts: estimated 1RM (also the sort key). */
    val estimated1Rm: Float,
    val headline: String,
    val headlineLabel: String
)

/** Loaded lifts by estimated 1RM first, then everything else (most recently trained first). */
private fun computeBestLifts(history: List<CompletedWorkout>): List<BestLift> {
    val byExercise = historyByExercise(history)
    return loggedExerciseNames(history)
        .map { name ->
            val rec = computeExerciseRecords(name, byExercise[ignoreCaseKey(name)].orEmpty())
            val type = rec.type
            if (type.hasWeightSuggestions && rec.bestEstimated1Rm > 0f) {
                BestLift(name, type, rec.bestSet, rec.bestEstimated1Rm, formatWeightKg(rec.bestEstimated1Rm), "est. 1-rep max")
            } else if (type == ExerciseType.WEIGHTED_BODYWEIGHT) {
                // Nothing added yet: it's a bodyweight exercise for now.
                BestLift(name, type, rec.bestSet, 0f, ChartMetric.MOST_REPS.format(rec.maxReps.toFloat()), "most reps")
            } else {
                val best = rec.bestSet
                val label = when (type) {
                    ExerciseType.BODYWEIGHT -> "most reps"
                    ExerciseType.ASSISTED -> "least help"
                    else -> "longest"
                }
                val headline = best?.let { formatSessionValue(type, sessionValue(type, listOf(it))) } ?: "—"
                BestLift(name, type, best, 0f, headline, label)
            }
        }
        .filter { it.bestSet != null }
        .sortedWith(compareByDescending<BestLift> { it.estimated1Rm > 0f }.thenByDescending { it.estimated1Rm })
}

@Composable
private fun BodyWeightCard(current: Float, weightHistory: List<WeightLogEntry>, onLog: () -> Unit, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    val points = weightHistory.take(8).reversed().map { it.displayDate.substringBefore(",") to it.weightKg }
    val change = if (weightHistory.size >= 2) weightHistory[0].weightKg - weightHistory[1].weightKg else null

    AppCard(modifier.fillMaxWidth(), elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Body weight", style = AppType.label, color = P.textSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(formatWeightNumber(current), AppType.bigNumber, P.textPrimary)
                    Text(" ${Units.current.label}", style = AppType.body, color = P.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
                }
                if (change != null) {
                    val rounded = roundedChange(change)
                    val unit = Units.current.label
                    val (icon, text) = when {
                        rounded > 0f -> Icons.AutoMirrored.Rounded.TrendingUp to "+${formatKgClean(rounded)} $unit since last time"
                        rounded < 0f -> Icons.AutoMirrored.Rounded.TrendingDown to "−${formatKgClean(abs(rounded))} $unit since last time"
                        else -> Icons.AutoMirrored.Rounded.TrendingFlat to "Same as last time"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = P.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text, style = AppType.caption, color = P.textSecondary)
                    }
                }
            }
            PrimaryButton("Log", onLog, icon = Icons.Rounded.Add, height = 44.dp)
        }
        if (points.size >= 2) {
            Spacer(Modifier.height(16.dp))
            TrendChart(points)
        } else {
            Spacer(Modifier.height(12.dp))
            Text("Log your weight a few times to see a trend.", style = AppType.caption, color = P.textTertiary)
        }
    }
}

@Composable
private fun WeighInRow(entry: WeightLogEntry, prev: WeightLogEntry?, onDelete: () -> Unit) {
    val P = LocalPalette.current
    val diff = prev?.let { roundedChange(entry.weightKg - it.weightKg) }
    Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatWeightKg(entry.weightKg), style = AppType.number, color = P.textPrimary)
            Text(entry.displayDate.substringBefore(" •"), style = AppType.caption, color = P.textSecondary)
        }
        if (diff != null && diff != 0f) {
            Text(
                (if (diff > 0) "+" else "−") + formatKgClean(abs(diff)),
                style = AppType.label.copy(fontFeatureSettings = "tnum"),
                color = P.textSecondary
            )
        }
        CircleIconButton(Icons.Rounded.Close, "Delete weigh-in", onDelete, size = 34.dp, container = Color.Transparent, tint = P.textTertiary)
    }
}

@Composable
private fun RecoveryDetailCard(items: List<MuscleRecoveryItem>, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    AppCard(modifier.fillMaxWidth()) {
        Text(
            "Based on when you last trained each muscle, how many sets it got and how long it usually takes " +
                "to recover (legs and back longest, arms and calves quickest). Muscles that help in a lift count " +
                "half a set, like biceps on rows or triceps on bench. Most people do well with " +
                "${WEEKLY_SET_TARGET.first}–${WEEKLY_SET_TARGET.last} hard sets per muscle each week.",
            style = AppType.caption,
            color = P.textSecondary
        )
        items.forEach { item ->
            Spacer(Modifier.height(14.dp))
            val color = recoveryColor(item.status, P)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.muscleGroup, style = AppType.bodyStrong, color = P.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    "${item.weeklyWorkingSets} sets this week",
                    style = AppType.caption.copy(fontFeatureSettings = "tnum"),
                    color = if (item.weeklyWorkingSets in WEEKLY_SET_TARGET) P.successText else P.textSecondary
                )
                Spacer(Modifier.width(10.dp))
                Pill(item.status.label, color = color, container = color.copy(alpha = 0.14f))
            }
            Spacer(Modifier.height(6.dp))
            AnimatedProgressBar(item.recoveryPercent / 100f, color = color, height = 6.dp)
            item.hoursSinceTrained?.let { h ->
                Text(
                    when { h < 1 -> "Trained just now"; h < 48 -> "Trained ${h}h ago"; else -> "Trained ${h / 24} days ago" },
                    style = AppType.caption.copy(fontWeight = FontWeight.Normal),
                    color = P.textTertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** A body-weight change in the current unit, to 0.1. */
private fun roundedChange(kg: Float): Float {
    val unit = Units.current
    return ((kg / unit.toKg(1f)) * 10).roundToInt() / 10f
}

@Composable
private fun LogWeightSheet(startKg: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    val P = LocalPalette.current
    val unit = Units.current
    val start = unit.fromKg(startKg)
    // Typed in the current unit; converted to kg on save.
    var input by remember { mutableStateOf(formatKgClean(start)) }
    AppBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding()) {
            Text("Log today's weight", style = AppType.title, color = P.textPrimary)
            Text("Weigh yourself at the same time each day for the clearest trend.", style = AppType.label.copy(fontWeight = FontWeight.Normal), color = P.textSecondary)
            Spacer(Modifier.height(20.dp))
            NumberStepper(
                value = input,
                onValueChange = { v -> sanitizeDecimalInput(v)?.let { input = it } },
                onMinus = { input = formatKgClean(unit.round(((input.toFloatOrNull() ?: start) - 0.1f).coerceAtLeast(0f))) },
                onPlus = { input = formatKgClean(unit.round((input.toFloatOrNull() ?: start) + 0.1f)) },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                "Save",
                onClick = { input.toFloatOrNull()?.takeIf { it > 0f }?.let { onSave(unit.toKg(it)) } },
                enabled = (input.toFloatOrNull() ?: 0f) > 0f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
