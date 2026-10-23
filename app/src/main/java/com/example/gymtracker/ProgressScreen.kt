package com.example.gymtracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
    onDeleteWeight: (Long) -> Unit
) {
    val P = LocalPalette.current
    var showLogSheet by remember { mutableStateOf(false) }
    var showAllWeighIns by rememberSaveable { mutableStateOf(false) }
    val recovery = remember(history) { computeMuscleRecoveryStatus(history) }
    val bestLifts = remember(history) { computeBestLifts(history).take(6) }

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

        item { SectionHeader("Best lifts") }
        if (bestLifts.isEmpty()) {
            item { EmptyState(Icons.Rounded.EmojiEvents, "No lifts yet", "Finish a workout and your best sets show up here.") }
        } else {
            item {
                AppCard(Modifier.fillMaxWidth().enterAnimation(2), contentPadding = PaddingValues(vertical = 6.dp)) {
                    bestLifts.forEachIndexed { i, lift ->
                        if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lift.name, style = AppType.bodyStrong, color = P.textPrimary)
                                Text("Best set ${lift.bestSet}", style = AppType.caption, color = P.textSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${lift.estimated1Rm} kg", style = AppType.number, color = P.textPrimary)
                                Text("est. 1-rep max", style = AppType.caption, color = P.textTertiary)
                            }
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

private data class BestLift(val name: String, val estimated1Rm: Int, val bestSet: String)

private fun computeBestLifts(history: List<CompletedWorkout>): List<BestLift> =
    history.flatMap { w -> w.exercises.filter { !it.isSkipped }.map { it.name } }
        .distinctBy { it.lowercase() }
        .map { name -> computeExerciseRecords(name, history).let { BestLift(name, it.bestEstimated1Rm, it.bestSetText.replace("kg ×", " kg ×")) } }
        .filter { it.estimated1Rm > 0 }
        .sortedByDescending { it.estimated1Rm }

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
                    RollingText(formatKgClean(current), AppType.bigNumber, P.textPrimary)
                    Text(" kg", style = AppType.body, color = P.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
                }
                if (change != null) {
                    val rounded = (change * 10).roundToInt() / 10f
                    val (icon, text) = when {
                        rounded > 0f -> Icons.AutoMirrored.Rounded.TrendingUp to "+${formatKgClean(rounded)} kg since last time"
                        rounded < 0f -> Icons.AutoMirrored.Rounded.TrendingDown to "−${formatKgClean(abs(rounded))} kg since last time"
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
    val diff = prev?.let { ((entry.weightKg - it.weightKg) * 10).roundToInt() / 10f }
    Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${formatKgClean(entry.weightKg)} kg", style = AppType.number, color = P.textPrimary)
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
            "Based on when you last trained each area and how many sets you did. Most people do well with " +
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

@Composable
private fun LogWeightSheet(startKg: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    val P = LocalPalette.current
    var input by remember { mutableStateOf(formatKgClean(startKg)) }
    AppBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding()) {
            Text("Log today's weight", style = AppType.title, color = P.textPrimary)
            Text("Weigh yourself at the same time each day for the clearest trend.", style = AppType.label.copy(fontWeight = FontWeight.Normal), color = P.textSecondary)
            Spacer(Modifier.height(20.dp))
            NumberStepper(
                value = input,
                onValueChange = { v -> sanitizeDecimalInput(v)?.let { input = it } },
                onMinus = { input = formatKgClean(((input.toFloatOrNull() ?: startKg) - 0.1f).coerceAtLeast(0f)) },
                onPlus = { input = formatKgClean((input.toFloatOrNull() ?: startKg) + 0.1f) },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                "Save",
                onClick = { input.toFloatOrNull()?.takeIf { it > 0f }?.let(onSave) },
                enabled = (input.toFloatOrNull() ?: 0f) > 0f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
