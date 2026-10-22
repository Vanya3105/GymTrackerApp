package com.example.gymtracker

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// 1. INTERACTIVE SPOTLIGHT TUTORIAL MANAGER & OVERLAY
// ============================================================================

@Immutable
data class TutorialStepInfo(
    val key: String,
    val title: String,
    val description: String
)

object TutorialPrefs {
    private const val PREFS_NAME = "gym_tracker_tutorial_prefs"
    private const val KEY_HOME_DONE = "has_seen_home_tutorial_v1"
    private const val KEY_WORKOUT_DONE = "has_seen_workout_tutorial_v1"

    var replayGeneration by mutableIntStateOf(0)

    fun hasSeenHomeTutorial(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HOME_DONE, false)
    }

    fun markHomeTutorialSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HOME_DONE, true).apply()
    }

    fun hasSeenWorkoutTutorial(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WORKOUT_DONE, false)
    }

    fun markWorkoutTutorialSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WORKOUT_DONE, true).apply()
    }

    fun resetAllTutorials(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HOME_DONE, false)
            .putBoolean(KEY_WORKOUT_DONE, false)
            .apply()
        replayGeneration++
    }
}

@Composable
fun SpotlightTutorialOverlay(
    steps: List<TutorialStepInfo>,
    currentStepIndex: Int,
    targetBounds: Map<String, Rect>,
    P: GymPalette,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit
) {
    if (currentStepIndex !in steps.indices) return
    val step = steps[currentStepIndex]
    val rect = targetBounds[step.key]

    val infiniteTransition = rememberInfiniteTransition(label = "spotlight_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val showCardAtBottom = rect == null || rect.center.y < (screenHeightPx * 0.52f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Intercept background taps during tutorial */ }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.84f))

            if (rect != null && rect.width > 0f && rect.height > 0f) {
                val pad = 10.dp.toPx()
                val topLeft = Offset(
                    x = (rect.left - pad).coerceAtLeast(4f),
                    y = (rect.top - pad).coerceAtLeast(4f)
                )
                val holeSize = Size(
                    width = rect.width + pad * 2,
                    height = rect.height + pad * 2
                )
                val corner = CornerRadius(18.dp.toPx(), 18.dp.toPx())

                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = topLeft,
                    size = holeSize,
                    cornerRadius = corner,
                    blendMode = BlendMode.Clear
                )

                drawRoundRect(
                    color = P.accentPrimary.copy(alpha = pulseAlpha),
                    topLeft = topLeft,
                    size = holeSize,
                    cornerRadius = corner,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = if (showCardAtBottom) Arrangement.Bottom else Arrangement.Top
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = P.bgCard,
                border = BorderStroke(1.5.dp, P.accentPrimary),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 24.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = P.accentPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "INTERACTIVE GUIDE • ${currentStepIndex + 1} / ${steps.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = P.accentPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        SpringButton(scaleTo = 0.92f, onClick = onSkip) {
                            Surface(
                                color = P.bgControl,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Skip ✕",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = P.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = step.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = P.textPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = step.description,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = P.textSecondary,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStepIndex > 0) {
                            SpringButton(scaleTo = 0.94f, onClick = onPrev) {
                                Surface(
                                    color = P.bgControl,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "← Previous",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = P.textPrimary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        SpringButton(scaleTo = 0.95f, onClick = onNext) {
                            Surface(
                                color = P.accentPrimary,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (currentStepIndex == steps.lastIndex) "Finish Tutorial ⚡" else "Next Tip →",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = P.onAccentPrimary,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. EVIDENCE-BASED SPORTS SCIENCE AI (FATIGUE, MEV/MAV, DOUBLE PROGRESSION)
// ============================================================================

@Immutable
data class MuscleRecoveryItem(
    val muscleGroup: String,
    val recoveryPercent: Int,
    val statusLabel: String,
    val hoursSinceTrained: Int?,
    val weeklyWorkingSets: Int
)

private fun doesExerciseHitMuscleGroup(
    splitTitle: String,
    exName: String,
    targetMuscle: String,
    group: String
): Boolean {
    val combined = "${exName.lowercase(Locale.US)} ${targetMuscle.lowercase(Locale.US)}"
    val splitLower = splitTitle.lowercase(Locale.US)
    return when (group) {
        "Chest" -> combined.contains("chest") || combined.contains("bench") || combined.contains("pec") || combined.contains("fly") || combined.contains("dip") || (targetMuscle.isBlank() && splitLower.contains("push"))
        "Back" -> combined.contains("lat") || combined.contains("back") || combined.contains("row") || combined.contains("pull-up") || combined.contains("pullup") || combined.contains("chin") || (targetMuscle.isBlank() && splitLower.contains("pull"))
        "Shoulders" -> combined.contains("shoulder") || combined.contains("delt") || combined.contains("lateral") || combined.contains("overhead press") || combined.contains("face pull")
        "Arms" -> combined.contains("bicep") || combined.contains("tricep") || combined.contains("curl") || combined.contains("pushdown") || combined.contains("extension") || combined.contains("brachialis") || combined.contains("hammer")
        "Legs" -> combined.contains("quad") || combined.contains("hamstring") || combined.contains("glute") || combined.contains("calf") || combined.contains("calves") || combined.contains("squat") || combined.contains("deadlift") || combined.contains("rdl") || combined.contains("leg")
        else -> false
    }
}

fun computeMuscleRecoveryStatus(history: List<CompletedWorkout>): List<MuscleRecoveryItem> {
    val nowMillis = System.currentTimeMillis()
    val sevenDaysAgoMillis = nowMillis - (7L * 24L * 60L * 60L * 1000L)
    val groups = listOf("Chest", "Back", "Shoulders", "Arms", "Legs")

    return groups.map { group ->
        var weeklySets = 0
        var lastTrainedMillis: Long? = null
        var lastSessionSetsForGroup = 0

        for (workout in history) {
            var setsInThisWorkout = 0
            for (ex in workout.exercises) {
                if (ex.isSkipped) continue
                if (doesExerciseHitMuscleGroup(workout.splitTitle, ex.name, ex.targetMuscle, group)) {
                    val validWorkSets = ex.sets.count {
                        !it.isWarmup && it.intensityType != "WARMUP" && parseRepsClean(it.reps, 0) > 0
                    }
                    setsInThisWorkout += validWorkSets
                }
            }
            if (setsInThisWorkout > 0) {
                if (lastTrainedMillis == null || workout.id > lastTrainedMillis) {
                    lastTrainedMillis = workout.id
                    lastSessionSetsForGroup = setsInThisWorkout
                }
                if (workout.id >= sevenDaysAgoMillis) {
                    weeklySets += setsInThisWorkout
                }
            }
        }

        if (lastTrainedMillis == null) {
            MuscleRecoveryItem(
                muscleGroup = group,
                recoveryPercent = 100,
                statusLabel = "FRESH",
                hoursSinceTrained = null,
                weeklyWorkingSets = weeklySets
            )
        } else {
            val hoursAgo = ((nowMillis - lastTrainedMillis) / (1000L * 60L * 60L)).toInt().coerceAtLeast(0)
            val requiredRecoveryHours = (44f + (lastSessionSetsForGroup * 3.5f)).coerceIn(48f, 78f)
            val recoveryPct = ((hoursAgo / requiredRecoveryHours) * 100f).roundToInt().coerceIn(12, 100)
            val status = when {
                recoveryPct < 55 -> "FATIGUED"
                recoveryPct < 85 -> "RECOVERING"
                else -> "FRESH"
            }
            MuscleRecoveryItem(
                muscleGroup = group,
                recoveryPercent = recoveryPct,
                statusLabel = status,
                hoursSinceTrained = hoursAgo,
                weeklyWorkingSets = weeklySets
            )
        }
    }
}

private fun isCompoundLift(exerciseName: String): Boolean {
    val lower = exerciseName.lowercase(Locale.US)
    return listOf("squat", "bench", "press", "deadlift", "rdl", "row", "pull-up", "pullup", "pulldown", "dip", "lunge").any {
        lower.contains(it)
    } && !lower.contains("pushdown") && !lower.contains("extension") && !lower.contains("fly")
}

fun evaluateSmartAiAndDeload(
    splitTitle: String,
    exercise: ExerciseEntry,
    history: List<CompletedWorkout>,
    stepKg: Float
): AiAdvice {
    val isCompound = isCompoundLift(exercise.name)
    val repRangeText = if (isCompound) "6–8" else "8–12"
    val topRepTarget = if (isCompound) 8 else 10

    val currentWorkSets = exercise.sets.filter { !it.isWarmup && it.intensityType != "WARMUP" }
    val completedWorkSets = currentWorkSets.filter { it.isCompleted && parseRepsClean(it.reps, 0) > 0 }

    if (completedWorkSets.size >= 2) {
        val firstSetReps = parseRepsClean(completedWorkSets.first().reps, 8)
        val latestSetReps = parseRepsClean(completedWorkSets.last().reps, 8)
        val latestWeight = parseWeightClean(completedWorkSets.last().weight, 40f)

        if (firstSetReps >= 6 && latestSetReps <= (firstSetReps * 0.62f).toInt()) {
            val adjustedW = formatKgClean((latestWeight * 0.9f).coerceAtLeast(2.5f))
            return AiAdvice(
                message = "⚠️ Intra-Workout Fatigue: Reps dropped from $firstSetReps → $latestSetReps (>35% drop-off). Autoregulate load to ${adjustedW}kg or rest +60s to stay in the hypertrophy zone.",
                isPositive = false,
                isWarning = true,
                suggestedWeight = adjustedW
            )
        }
    }

    val baseAdvice = evaluateSplitIsolatedAi(splitTitle, exercise, history, stepKg)
    if (baseAdvice != null) {
        return baseAdvice
    }

    val topCurrentWeight = currentWorkSets.maxOfOrNull { parseWeightClean(it.weight, 40f) } ?: 40f
    if (completedWorkSets.isNotEmpty()) {
        val hadVeryLowReps = completedWorkSets.any { parseRepsClean(it.reps, 8) in 1..4 }
        val allHitTopTarget = completedWorkSets.all { parseRepsClean(it.reps, 8) >= topRepTarget }

        if (hadVeryLowReps) {
            val deloadW = formatKgClean((topCurrentWeight * 0.9f).coerceAtLeast(2.5f))
            return AiAdvice(
                message = "⚠️ Load Too Heavy (≤4 reps): Above ~88% 1RM generates high joint fatigue with fewer effective hypertrophy reps. Drop to ${deloadW}kg (-10%).",
                isPositive = false,
                isWarning = true,
                suggestedWeight = deloadW
            )
        }
        if (allHitTopTarget) {
            val nextW = formatKgClean(topCurrentWeight + stepKg)
            return AiAdvice(
                message = "⚡ Double Progression Unlocked: Hit $topRepTarget+ reps! Increase working weight to ${nextW}kg (+${formatKgClean(stepKg)}kg).",
                isPositive = true,
                isWarning = false,
                suggestedWeight = nextW
            )
        }
    }

    val targetW = formatKgClean(topCurrentWeight)
    val liftTypeLabel = if (isCompound) "Compound" else "Isolation"
    return AiAdvice(
        message = "🧠 AI Double Progression ($liftTypeLabel) • Target: ${targetW}kg for $repRangeText reps (1–2 RIR). Hit $topRepTarget reps across all sets to unlock +${formatKgClean(stepKg)}kg.",
        isPositive = false,
        isWarning = false,
        suggestedWeight = targetW
    )
}

fun shareAiWorkoutReport(
    context: Context,
    splitTitle: String,
    durationSeconds: Long,
    exercises: List<ExerciseEntry>,
    history: List<CompletedWorkout>,
    bodyWeightKg: Float,
    photoPath: String? = null
) {
    shareWorkoutCardImage(
        context = context,
        splitTitle = splitTitle,
        durationSeconds = durationSeconds,
        exercises = exercises,
        history = history,
        bodyWeightKg = bodyWeightKg,
        photoPath = photoPath
    )
}

@Composable
fun AiMuscleFatigueCard(
    history: List<CompletedWorkout>,
    P: GymPalette,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val recoveryList = remember(history) { computeMuscleRecoveryStatus(history) }
    val fatiguedCount = recoveryList.count { it.statusLabel == "FATIGUED" }
    val optimalMavGroups = recoveryList.filter { it.weeklyWorkingSets in 6..16 }.map { it.muscleGroup }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = P.bgCard,
        border = BorderStroke(1.dp, P.accentSecondary.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(if (compact) 10.dp else 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧠 AI MUSCLE RECOVERY & WEEKLY VOLUME (MAV)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = P.accentSecondary
                )
                Surface(
                    color = if (fatiguedCount == 0) P.successSurface else P.warningSurface,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (fatiguedCount == 0) "100% PRIMED" else "$fatiguedCount RECOVERING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = if (fatiguedCount == 0) P.successText else P.warningText,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                recoveryList.forEach { item ->
                    val statusColor = when (item.statusLabel) {
                        "FATIGUED" -> P.dangerText
                        "RECOVERING" -> P.warningText
                        else -> P.successText
                    }
                    val statusBg = when (item.statusLabel) {
                        "FATIGUED" -> P.dangerSurface
                        "RECOVERING" -> P.warningSurface
                        else -> P.bgControl
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = statusBg,
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = item.muscleGroup,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = P.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "${item.recoveryPercent}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = statusColor
                            )
                            Text(
                                text = "${item.weeklyWorkingSets} sets/wk",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.weeklyWorkingSets in 6..16) P.accentPrimary else P.textSecondary
                            )
                        }
                    }
                }
            }

            if (!compact) {
                val summaryLine = if (optimalMavGroups.isNotEmpty()) {
                    "Hypertrophy MAV Zone (6–16 sets/wk): ${optimalMavGroups.joinToString(", ")} on track."
                } else {
                    "Volume Landmark Target: Aim for 6–16 hard working sets per muscle group each week."
                }
                Text(
                    text = summaryLine,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = P.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

// ============================================================================
// 3. ANIMATED UI PRIMITIVES
// ============================================================================

@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scaleTo: Float = 0.94f,
    disabled: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .pointerInput(disabled) {
                if (disabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    coroutineScope.launch {
                        scale.animateTo(scaleTo, spring(stiffness = 400f, dampingRatio = 0.5f))
                    }
                    val up = waitForUpOrCancellation()
                    coroutineScope.launch {
                        scale.animateTo(1f, spring(stiffness = 300f, dampingRatio = 0.6f))
                    }
                    if (up != null) {
                        currentOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun AnimatedProgressBar(
    progress: Float,
    height: Dp,
    bgColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(bgColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fillColor)
        )
    }
}

@Composable
fun TrendLineMiniChart(
    title: String,
    points: List<Pair<String, Float>>,
    P: GymPalette
) {
    if (points.size < 2) return

    val maxW = points.maxOf { it.second }.coerceAtLeast(1f)
    val minW = points.minOf { it.second }
    val range = (maxW - minW).takeIf { it > 0f } ?: 5f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = P.bgControl,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = P.textSecondary
                )
                Text(
                    text = "${formatKgClean(points.first().second)}kg → ${formatKgClean(points.last().second)}kg",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = P.accentPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                val path = Path()
                val coords = points.mapIndexed { idx, pair ->
                    val x = idx * stepX
                    val normY = (pair.second - minW) / range
                    val y = size.height - (normY * (size.height - 14.dp.toPx())) - 7.dp.toPx()
                    Offset(x, y)
                }

                coords.forEachIndexed { idx, pt ->
                    if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }

                drawPath(
                    path = path,
                    color = P.accentPrimary,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                coords.forEach { pt ->
                    drawCircle(
                        color = P.accentSecondary,
                        radius = 4.5.dp.toPx(),
                        center = pt
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEach { (dateLbl, wVal) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${formatKgClean(wVal)}kg",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = P.textPrimary
                        )
                        Text(
                            text = dateLbl,
                            fontSize = 9.sp,
                            color = P.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepperControl(
    value: String,
    allowKeyboardTyping: Boolean,
    onValueChange: (String) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    P: GymPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(P.bgControl)
            .border(1.dp, P.borderSubtle, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpringButton(
            onClick = onStepDown,
            scaleTo = 0.82f,
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
        ) {
            Text("−", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = P.textSecondary)
        }

        if (allowKeyboardTyping) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    color = P.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(P.accentPrimary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (value.isEmpty()) {
                            Text("0", color = P.textSecondary.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(value.ifEmpty { "0" }, color = P.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        SpringButton(
            onClick = onStepUp,
            scaleTo = 0.82f,
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
        ) {
            Text("+", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = P.accentPrimary)
        }
    }
}