package com.example.gymtracker

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// 1. WORKOUT HEADER CLOCK, PR BANNER & SUPERSET HELPERS
// ============================================================================

@Composable
fun ElapsedWorkoutClock(startTimeMillis: Long, userProfile: UserProfile) {
    val P = LocalPalette.current
    var elapsedSec by remember {
        mutableLongStateOf(((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L))
    }

    LaunchedEffect(startTimeMillis) {
        while (true) {
            delay(1000L)
            elapsedSec = ((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L)
        }
    }

    val mins = elapsedSec / 60
    val secs = elapsedSec % 60
    Text(
        text = String.format(
            Locale.US,
            "⏱ %d:%02d • BW: ${formatKgClean(userProfile.currentWeightKg)}kg • DRAG ⠿",
            mins,
            secs
        ),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = P.accentSecondary
    )
}

@Composable
fun PrCelebrationBanner(message: String) {
    val P = LocalPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = P.successSurface,
        border = BorderStroke(1.dp, P.successText),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🏆", fontSize = 22.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NEW ALL-TIME PERSONAL RECORD!",
                    color = P.successText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = message,
                    color = P.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Finds all active exercise indices that belong to the same connected Superset chain
internal fun getConnectedSupersetActiveIndices(
    activeExercisesList: List<Pair<ExerciseEntry, Int>>,
    currentActiveIndex: Int
): IntRange {
    if (currentActiveIndex !in activeExercisesList.indices) return currentActiveIndex..currentActiveIndex
    var start = currentActiveIndex
    while (start > 0 && activeExercisesList[start - 1].first.isSuperset) {
        start--
    }
    var end = currentActiveIndex
    while (end < activeExercisesList.lastIndex && activeExercisesList[end].first.isSuperset) {
        end++
    }
    return start..end
}

// ============================================================================
// 2. ACTIVE WORKOUT SCREEN
// ============================================================================

@Composable
fun ActiveWorkoutScreen(
    split: WorkoutSplit,
    exercises: List<ExerciseEntry>,
    history: List<CompletedWorkout>,
    settings: AppSettings,
    userProfile: UserProfile,
    isEditingPastWorkout: Boolean,
    initialDurationSeconds: Long = 0L,
    initialPhotoPath: String?,
    onHaptic: () -> Unit,
    onUpdateExercises: (List<ExerciseEntry>) -> Unit,
    onReorderExercises: (Int, Int) -> Unit,
    onAddCustomExercise: (String) -> Unit,
    onRemoveExercisePermanently: (String) -> Unit,
    onCancelWorkout: () -> Unit,
    onFinishWorkout: (Long, String?, List<String>) -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current

    val startTimeMillis = remember { LocalStorage.getActiveSessionStartTime(context) }
    var restRemainingSec by remember { mutableIntStateOf(0) }

    var showCompletionModal by remember { mutableStateOf(false) }
    var inspectedExerciseIndex by remember { mutableStateOf<Int?>(null) }
    var showAddExerciseModal by remember { mutableStateOf(false) }
    var finalDurationSeconds by remember { mutableLongStateOf(initialDurationSeconds) }

    // Interactive Spotlight Tutorial State for Active Workout Screen
    val workoutTutorialBounds = remember { mutableStateMapOf<String, Rect>() }
    var showWorkoutTutorial by remember(TutorialPrefs.replayGeneration) {
        mutableStateOf(!isEditingPastWorkout && !TutorialPrefs.hasSeenWorkoutTutorial(context))
    }
    var workoutTutorialStepIndex by remember(TutorialPrefs.replayGeneration) { mutableIntStateOf(0) }

    val workoutTutorialSteps = remember {
        listOf(
            TutorialStepInfo(
                key = "WORKOUT_CARD_HEADER",
                title = "Reorder (⠿) & Connected Supersets (🔗)",
                description = "Drag '⠿' to reorder exercises. Tap '🔗 Superset' to pair two exercises together—their rest timers connect so rest only starts after you finish a set of BOTH exercises!"
            ),
            TutorialStepInfo(
                key = "WORKOUT_AI_BAR",
                title = "Evidence-Based AI & Smart Deload",
                description = "Monitors Double Progression and intra-workout rep drop-offs. Tap '⚡ Apply AI Target' or '🛡️ Smart Deload -10%' to auto-adjust your working sets."
            ),
            TutorialStepInfo(
                key = "WORKOUT_SET_ACTIONS",
                title = "Warmups, Drop Sets & Rest-Pause",
                description = "Tap any set badge (W, S1, DROP, R-P) to cycle set types, or tap '🔥 + DROP SET' to add an instant -20% drop set!"
            )
        )
    }

    var showExitPrompt by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        onHaptic()
        if (showWorkoutTutorial) {
            TutorialPrefs.markWorkoutTutorialSeen(context)
            showWorkoutTutorial = false
        } else if (showCompletionModal) {
            showCompletionModal = false
        } else if (inspectedExerciseIndex != null) {
            inspectedExerciseIndex = null
        } else if (showAddExerciseModal) {
            showAddExerciseModal = false
        } else if (restRemainingSec == 0) {
            val dur = if (isEditingPastWorkout) {
                history.find { it.splitTitle == split.title }?.durationSeconds ?: 0L
            } else {
                ((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L)
            }
            finalDurationSeconds = dur
            showCompletionModal = true
        } else {
            showExitPrompt = true
        }
    }

    if (showExitPrompt) {
        AlertDialog(
            onDismissRequest = { showExitPrompt = false },
            containerColor = P.bgCard,
            titleContentColor = P.textPrimary,
            title = { Text("Abort Workout?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to exit? Your progress for this session will be lost.",
                    color = P.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitPrompt = false
                    LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)
                    onCancelWorkout()
                }) {
                    Text("Abort", color = P.dangerText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitPrompt = false }) {
                    Text("Resume", color = P.textSecondary)
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            val endMillis = LocalStorage.getRestEndTimeMillis(context)
            if (endMillis > 0) {
                val left = Math.ceil((endMillis - System.currentTimeMillis()) / 1000.0).toInt()
                if (left > 0) {
                    restRemainingSec = left
                } else {
                    restRemainingSec = 0
                    LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)
                }
            } else {
                restRemainingSec = 0
            }
            delay(500)
        }
    }

    var newExerciseName by remember { mutableStateOf("") }
    var confirmRemoveExerciseName by remember { mutableStateOf<String?>(null) }
    var noteDraftText by remember { mutableStateOf("") }
    var noteRefreshTrigger by remember { mutableIntStateOf(0) }
    var attachedPhotoPath by remember { mutableStateOf(initialPhotoPath) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let { PhotoStorage.saveBitmapToInternalStorage(context, it)?.let { path -> attachedPhotoPath = path } }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { PhotoStorage.copyUriToInternalStorage(context, it)?.let { path -> attachedPhotoPath = path } }
    }

    var prCelebrationMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(prCelebrationMessage) {
        if (prCelebrationMessage != null) {
            delay(2600)
            prCelebrationMessage = null
        }
    }

    val previousSplitWorkout = remember(split.title, history) {
        history.firstOrNull { it.splitTitle.equals(split.title, ignoreCase = true) }
    }

    val activeExercisesList = exercises.mapIndexed { idx, ex -> ex to idx }.filter { !it.first.isSkipped }
    val skippedExercisesList = exercises.mapIndexed { idx, ex -> ex to idx }.filter { it.first.isSkipped }

    val totalSetsCount = maxOf(1, activeExercisesList.sumOf { it.first.sets.size })
    val completedSetsCount = activeExercisesList.sumOf { it.first.sets.count { s -> s.isCompleted } }
    val progressPct = (completedSetsCount.toFloat() / totalSetsCount.toFloat()).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // TOP HEADER BAR (Share button removed; only timer/abort controls remain)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditingPastWorkout) "EDIT ${split.title.uppercase()}" else split.title.uppercase(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = P.textPrimary
                    )
                    if (!isEditingPastWorkout) {
                        ElapsedWorkoutClock(startTimeMillis, userProfile)
                    } else {
                        Text(
                            text = "EDITING ARCHIVED WORKOUT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = P.accentPrimary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (restRemainingSec > 0) {
                        SpringButton(
                            onClick = {
                                onHaptic()
                                val newSec = restRemainingSec + 15
                                val nextEnd = System.currentTimeMillis() + (newSec * 1000L)
                                LocalStorage.setRestEndTimeMillis(context, nextEnd, fromWidget = true)
                            }
                        ) {
                            Surface(
                                color = P.bgControl,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, P.borderStrong)
                            ) {
                                Text(
                                    text = "+15s",
                                    color = P.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                                )
                            }
                        }
                        SpringButton(
                            onClick = {
                                onHaptic()
                                LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)
                            }
                        ) {
                            Surface(color = P.accentSecondary, shape = RoundedCornerShape(8.dp)) {
                                val rMins = restRemainingSec / 60
                                val rSecs = restRemainingSec % 60
                                Text(
                                    text = String.format(Locale.US, "REST %d:%02d ✕", rMins, rSecs),
                                    color = Color(0xFF07080A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    SpringButton(onClick = {
                        onHaptic()
                        showExitPrompt = true
                    }) {
                        Text("ABORT", color = P.dangerText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Box(modifier = Modifier.padding(horizontal = 18.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$completedSetsCount / $totalSetsCount ACTIVE SETS COMPLETED${if (skippedExercisesList.isNotEmpty()) " • ${skippedExercisesList.size} SKIPPED" else ""}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = P.textSecondary
                        )
                        Text(
                            text = "${(progressPct * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = P.accentPrimary
                        )
                    }
                    AnimatedProgressBar(
                        progress = progressPct,
                        height = 5.dp,
                        bgColor = P.bgControl,
                        fillColor = P.accentPrimary,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }

            if (prCelebrationMessage != null) {
                PrCelebrationBanner(prCelebrationMessage!!)
            }

            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    AiMuscleFatigueCard(
                        history = history,
                        P = P,
                        compact = true
                    )
                }

                if (!isEditingPastWorkout && previousSplitWorkout != null) {
                    item {
                        SpringButton(
                            scaleTo = 0.97f,
                            onClick = {
                                onHaptic()
                                val copied = exercises.map { cur ->
                                    val prev = previousSplitWorkout.exercises.find { it.name.equals(cur.name, ignoreCase = true) }
                                    if (prev == null) cur else cur.copy(
                                        sets = prev.sets.mapIndexed { idx, s ->
                                            s.copy(
                                                setNumber = idx + 1,
                                                isWarmup = if (idx == 0) s.isWarmup else false,
                                                isCompleted = false,
                                                restSeconds = s.restSeconds.takeIf { it >= 0 } ?: cur.restSeconds.takeIf { it > 0 } ?: settings.restTimerSeconds
                                            )
                                        }
                                    )
                                }
                                onUpdateExercises(copied)
                            }
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = P.bgCard,
                                border = BorderStroke(1.dp, P.borderStrong),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("⚡", color = P.accentPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CLONE LAST ${split.title.uppercase()} NUMBERS",
                                            color = P.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "Load weights & reps from ${previousSplitWorkout.displayDate.substringBefore('•').trim()}",
                                            color = P.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text("→", color = P.accentPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = activeExercisesList,
                    key = { _, pair -> pair.first.name }
                ) { index, (exercise, exIdx) ->
                    val supersetRange = getConnectedSupersetActiveIndices(activeExercisesList, index)
                    val isPartOfSuperset = supersetRange.first < supersetRange.last
                    val isSupersetWithNext = exercise.isSuperset && index < activeExercisesList.lastIndex
                    val supersetOrderChar = ('A' + (index - supersetRange.first))
                    val supersetPartnerNames = if (isPartOfSuperset) {
                        supersetRange.filter { it != index }.joinToString(" + ") { activeExercisesList[it].first.name }
                    } else ""

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        val aiAdvice = evaluateSmartAiAndDeload(split.title, exercise, history, settings.weightStepKg)
                        val records = computeExerciseRecords(exercise.name, history)
                        val pinnedNote = remember(exercise.name, noteRefreshTrigger) {
                            LocalStorage.getExerciseNote(context, exercise.name)
                        }
                        val exDefaultRest = exercise.restSeconds.takeIf { it > 0 } ?: settings.restTimerSeconds
                        var workSetCounter = 0

                        val dragOffsetY = remember { Animatable(0f) }
                        var isDraggingThis by remember { mutableStateOf(false) }
                        val cardScale by animateFloatAsState(
                            targetValue = if (isDraggingThis) 1.03f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
                            label = "card_drag_scale"
                        )
                        val coroutineScope = rememberCoroutineScope()
                        val currentExIdx by rememberUpdatedState(exIdx)
                        val currentExercisesCount by rememberUpdatedState(exercises.size)

                        val borderColor = when {
                            isDraggingThis -> P.accentPrimary
                            isPartOfSuperset -> P.accentSecondary
                            else -> P.borderSubtle
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = dragOffsetY.value
                                    scaleX = cardScale
                                    scaleY = cardScale
                                }
                                .zIndex(if (isDraggingThis) 10f else 0f),
                            color = if (isDraggingThis) P.bgControl else P.bgCard,
                            border = BorderStroke(
                                if (isDraggingThis || isPartOfSuperset) 2.dp else 1.dp,
                                borderColor
                            ),
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = if (isDraggingThis) 20.dp else 0.dp
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                if (isPartOfSuperset) {
                                    Surface(
                                        color = P.accentSecondary.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = "🔗 SUPERSET $supersetOrderChar • Connected Rest (${formatRestShort(exDefaultRest)}) with $supersetPartnerNames",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = P.accentSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = if (index == 0) {
                                        Modifier.onGloballyPositioned { coords ->
                                            workoutTutorialBounds["WORKOUT_CARD_HEADER"] = coords.boundsInRoot()
                                        }
                                    } else Modifier,
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(34.dp, 38.dp)
                                            .pointerInput(restRemainingSec == 0) {
                                                if (restRemainingSec > 0) return@pointerInput
                                                val swapThresholdPx = 145.dp.toPx()
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        onHaptic()
                                                        isDraggingThis = true
                                                    },
                                                    onDragEnd = {
                                                        isDraggingThis = false
                                                        coroutineScope.launch {
                                                            dragOffsetY.animateTo(
                                                                0f,
                                                                spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                                                            )
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        isDraggingThis = false
                                                        coroutineScope.launch {
                                                            dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                                        }
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val nextY = dragOffsetY.value + dragAmount
                                                        coroutineScope.launch {
                                                            dragOffsetY.snapTo(nextY)
                                                            if (nextY < -swapThresholdPx && currentExIdx > 0) {
                                                                onHaptic()
                                                                onReorderExercises(currentExIdx, currentExIdx - 1)
                                                                dragOffsetY.snapTo(nextY + swapThresholdPx)
                                                            } else if (nextY > swapThresholdPx && currentExIdx < currentExercisesCount - 1) {
                                                                onHaptic()
                                                                onReorderExercises(currentExIdx, currentExIdx + 1)
                                                                dragOffsetY.snapTo(nextY - swapThresholdPx)
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        color = if (isDraggingThis) P.accentPrimary else P.bgControl,
                                        border = BorderStroke(1.dp, if (isDraggingThis) P.accentPrimary else P.borderSubtle),
                                        shape = RoundedCornerShape(9.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("⠿", color = if (isDraggingThis) P.onAccentPrimary else P.accentPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black)
                                        }
                                    }

                                    SpringButton(
                                        scaleTo = 0.97f,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onHaptic()
                                            noteDraftText = pinnedNote
                                            inspectedExerciseIndex = exIdx
                                        }
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(exercise.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = P.textPrimary)
                                            Text(
                                                text = "${exercise.targetMuscle.ifBlank { "Custom" }}${if (records.bestSetText != "—") " • PR: ${records.bestSetText}" else ""} • Stats ›",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = P.textSecondary
                                            )
                                        }
                                    }

                                    if (index < activeExercisesList.lastIndex || exercise.isSuperset) {
                                        SpringButton(
                                            disabled = restRemainingSec > 0,
                                            scaleTo = 0.9f,
                                            onClick = {
                                                onHaptic()
                                                val toggled = !exercise.isSuperset
                                                val nextEx = exercises.toMutableList()
                                                nextEx[exIdx] = exercise.copy(isSuperset = toggled)
                                                if (toggled && index + 1 in activeExercisesList.indices) {
                                                    val partnerOrigIdx = activeExercisesList[index + 1].second
                                                    val partnerEx = nextEx[partnerOrigIdx]
                                                    nextEx[partnerOrigIdx] = partnerEx.copy(
                                                        restSeconds = exDefaultRest,
                                                        sets = partnerEx.sets.map { it.copy(restSeconds = exDefaultRest) }
                                                    )
                                                }
                                                onUpdateExercises(nextEx)
                                            }
                                        ) {
                                            Surface(
                                                color = if (exercise.isSuperset) P.accentSecondary else P.bgControl,
                                                border = BorderStroke(1.dp, P.accentSecondary),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (exercise.isSuperset) "🔗 Linked" else "🔗 Superset",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (exercise.isSuperset) Color(0xFF07080A) else P.accentSecondary,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    SpringButton(
                                        disabled = restRemainingSec > 0,
                                        scaleTo = 0.9f,
                                        onClick = {
                                            onHaptic()
                                            val nextRest = cycleRestPreset(exDefaultRest)
                                            val nextEx = exercises.toMutableList()
                                            for (activeIdx in supersetRange) {
                                                val origIdx = activeExercisesList[activeIdx].second
                                                val target = nextEx[origIdx]
                                                nextEx[origIdx] = target.copy(
                                                    restSeconds = nextRest,
                                                    sets = target.sets.map { it.copy(restSeconds = nextRest) }
                                                )
                                                LocalStorage.saveExerciseDefaultRest(context, target.name, nextRest)
                                            }
                                            onUpdateExercises(nextEx)
                                        }
                                    ) {
                                        Surface(
                                            color = P.bgControl,
                                            border = BorderStroke(1.dp, P.borderStrong),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "⏱ ${formatRestShort(exDefaultRest)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = P.accentSecondary,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    SpringButton(
                                        disabled = restRemainingSec > 0,
                                        scaleTo = 0.9f,
                                        onClick = {
                                            onHaptic()
                                            val nextEx = exercises.toMutableList()
                                            nextEx[exIdx] = exercise.copy(isSkipped = true, isSuperset = false)
                                            onUpdateExercises(nextEx)
                                        }
                                    ) {
                                        Surface(
                                            color = P.warningSurface,
                                            border = BorderStroke(1.dp, P.warningText),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Skip",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = P.warningText,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                if (pinnedNote.isNotBlank()) {
                                    SpringButton(
                                        scaleTo = 0.98f,
                                        onClick = {
                                            noteDraftText = pinnedNote
                                            inspectedExerciseIndex = exIdx
                                        },
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Surface(color = P.bgControl, shape = RoundedCornerShape(8.dp)) {
                                            Text(
                                                text = "📌 $pinnedNote",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = P.textPrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = if (aiAdvice.isPositive) P.successSurface else if (aiAdvice.isWarning) P.warningSurface else P.bgControl,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .then(
                                            if (index == 0) {
                                                Modifier.onGloballyPositioned { coords ->
                                                    workoutTutorialBounds["WORKOUT_AI_BAR"] = coords.boundsInRoot()
                                                }
                                            } else Modifier
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            text = aiAdvice.message,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (aiAdvice.isPositive) P.successText else if (aiAdvice.isWarning) P.warningText else P.accentPrimary
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (aiAdvice.suggestedWeight.isNotBlank()) {
                                                SpringButton(
                                                    disabled = restRemainingSec > 0,
                                                    scaleTo = 0.94f,
                                                    onClick = {
                                                        onHaptic()
                                                        val updatedSets = exercise.sets.map { s ->
                                                            if (s.isCompleted || s.isWarmup || s.intensityType == "WARMUP") s
                                                            else s.copy(weight = aiAdvice.suggestedWeight)
                                                        }
                                                        val nextEx = exercises.toMutableList()
                                                        nextEx[exIdx] = exercise.copy(sets = updatedSets)
                                                        onUpdateExercises(nextEx)
                                                    }
                                                ) {
                                                    Surface(
                                                        color = P.bgCard,
                                                        border = BorderStroke(1.dp, P.accentPrimary.copy(alpha = 0.6f)),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "⚡ Apply AI Target (${aiAdvice.suggestedWeight}kg)",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = P.accentPrimary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            SpringButton(
                                                disabled = restRemainingSec > 0,
                                                scaleTo = 0.94f,
                                                onClick = {
                                                    onHaptic()
                                                    val updatedSets = exercise.sets.map { s ->
                                                        if (s.isCompleted || s.isWarmup || s.intensityType == "WARMUP") s
                                                        else {
                                                            val curW = parseWeightClean(s.weight, 20f)
                                                            val deloadW = formatKgClean((curW * 0.9f).coerceAtLeast(2.5f))
                                                            s.copy(weight = deloadW)
                                                        }
                                                    }
                                                    val nextEx = exercises.toMutableList()
                                                    nextEx[exIdx] = exercise.copy(sets = updatedSets)
                                                    onUpdateExercises(nextEx)
                                                    Toast.makeText(context, "Smart Deload (-10%) applied to ${exercise.name}", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Surface(
                                                    color = P.bgCard,
                                                    border = BorderStroke(1.dp, P.warningText.copy(alpha = 0.6f)),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "🛡️ Smart Deload -10%",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = P.warningText,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("TYPE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = P.textSecondary, modifier = Modifier.width(44.dp))
                                    Text("KG", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = P.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1.15f))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("REPS", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = P.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("REST", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = P.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
                                    Spacer(modifier = Modifier.width(38.dp))
                                }

                                exercise.sets.forEachIndexed { setIdx, setEntry ->
                                    if (setEntry.intensityType == "NORMAL" && !setEntry.isWarmup) workSetCounter++
                                    val isPr = isSetPersonalRecord(setEntry, records)

                                    val badgeLabel = when {
                                        setEntry.isWarmup || setEntry.intensityType == "WARMUP" -> "W"
                                        setEntry.intensityType == "DROP" -> "DROP"
                                        setEntry.intensityType == "REST_PAUSE" -> "R-P"
                                        isPr -> "🏆"
                                        else -> "S$workSetCounter"
                                    }
                                    val badgeBg = when {
                                        setEntry.isWarmup || setEntry.intensityType == "WARMUP" -> P.warningSurface
                                        setEntry.intensityType == "DROP" -> P.dangerSurface
                                        setEntry.intensityType == "REST_PAUSE" -> P.accentSecondary.copy(alpha = 0.2f)
                                        else -> P.bgControl
                                    }
                                    val badgeColor = when {
                                        setEntry.isWarmup || setEntry.intensityType == "WARMUP" -> P.warningText
                                        setEntry.intensityType == "DROP" -> P.dangerText
                                        setEntry.intensityType == "REST_PAUSE" -> P.accentSecondary
                                        else -> P.textPrimary
                                    }
                                    val setRestSec = setEntry.restSeconds.takeIf { it >= 0 } ?: exDefaultRest

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (setEntry.isCompleted) P.successSurface else Color.Transparent)
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.width(44.dp)) {
                                            SpringButton(
                                                scaleTo = 0.85f,
                                                disabled = restRemainingSec > 0,
                                                onClick = {
                                                    onHaptic()
                                                    val nextType = when (setEntry.intensityType) {
                                                        "NORMAL" -> "WARMUP"
                                                        "WARMUP" -> "DROP"
                                                        "DROP" -> "REST_PAUSE"
                                                        else -> "NORMAL"
                                                    }
                                                    val nextSets = exercise.sets.toMutableList()
                                                    nextSets[setIdx] = setEntry.copy(
                                                        intensityType = nextType,
                                                        isWarmup = (nextType == "WARMUP")
                                                    )
                                                    val nextEx = exercises.toMutableList()
                                                    nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                    onUpdateExercises(nextEx)
                                                }
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(40.dp, 34.dp),
                                                    color = badgeBg,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(badgeLabel, fontSize = 10.sp, fontWeight = FontWeight.Black, color = badgeColor)
                                                    }
                                                }
                                            }
                                        }

                                        Box(modifier = Modifier.weight(1.15f)) {
                                            if (restRemainingSec > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(P.bgControl)
                                                        .border(1.dp, P.borderSubtle, RoundedCornerShape(8.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("${setEntry.weight}kg", color = P.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                                }
                                            } else {
                                                StepperControl(
                                                    value = setEntry.weight,
                                                    allowKeyboardTyping = true,
                                                    onValueChange = { v ->
                                                        val nextSets = exercise.sets.toMutableList()
                                                        nextSets[setIdx] = setEntry.copy(weight = v)
                                                        val nextEx = exercises.toMutableList()
                                                        nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                        onUpdateExercises(nextEx)
                                                    },
                                                    onStepDown = {
                                                        onHaptic()
                                                        val cur = parseWeightClean(setEntry.weight, 20f)
                                                        val next = (cur - settings.weightStepKg).coerceAtLeast(0f)
                                                        val nextSets = exercise.sets.toMutableList()
                                                        nextSets[setIdx] = setEntry.copy(weight = formatKgClean(next))
                                                        val nextEx = exercises.toMutableList()
                                                        nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                        onUpdateExercises(nextEx)
                                                    },
                                                    onStepUp = {
                                                        onHaptic()
                                                        val cur = parseWeightClean(setEntry.weight, 20f)
                                                        val next = cur + settings.weightStepKg
                                                        val nextSets = exercise.sets.toMutableList()
                                                        nextSets[setIdx] = setEntry.copy(weight = formatKgClean(next))
                                                        val nextEx = exercises.toMutableList()
                                                        nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                        onUpdateExercises(nextEx)
                                                    },
                                                    P = P
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Box(modifier = Modifier.weight(1f)) {
                                            if (restRemainingSec > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(P.bgControl)
                                                        .border(1.dp, P.borderSubtle, RoundedCornerShape(8.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("${setEntry.reps} reps", color = P.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                                }
                                            } else {
                                                StepperControl(
                                                    value = setEntry.reps,
                                                    allowKeyboardTyping = true,
                                                    onValueChange = { v ->
                                                        val filtered = v.filter { it.isDigit() }
                                                        val nextSets = exercise.sets.toMutableList()
                                                        nextSets[setIdx] = setEntry.copy(reps = filtered)
                                                        val nextEx = exercises.toMutableList()
                                                        nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                        onUpdateExercises(nextEx)
                                                    },
                                                    onStepDown = {
                                                        onHaptic()
                                                        val cur = parseRepsClean(setEntry.reps, 8)
                                                        val nextSets = exercise.sets.toMutableList()
                                                        nextSets[setIdx] = setEntry.copy(reps = "${maxOf(0, cur - 1)}")
                                                        val nextEx = exercises.toMutableList()
                                                        nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                        onUpdateExercises(nextEx)
                                                    },
                                                    onStepUp = {
                                                        onHaptic()
                                                        val cur = parseRepsClean(setEntry.reps, 8)
                                                        val nextSets = exercise.sets.toMutableList()
                                                        nextSets[setIdx] = setEntry.copy(reps = "${cur + 1}")
                                                        val nextEx = exercises.toMutableList()
                                                        nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                        onUpdateExercises(nextEx)
                                                    },
                                                    P = P
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        SpringButton(
                                            disabled = restRemainingSec > 0,
                                            scaleTo = 0.86f,
                                            onClick = {
                                                onHaptic()
                                                val cycledRest = cycleRestPreset(setRestSec)
                                                val nextEx = exercises.toMutableList()
                                                if (isPartOfSuperset) {
                                                    for (activeIdx in supersetRange) {
                                                        val origIdx = activeExercisesList[activeIdx].second
                                                        val target = nextEx[origIdx]
                                                        nextEx[origIdx] = target.copy(
                                                            restSeconds = cycledRest,
                                                            sets = target.sets.mapIndexed { sIdx, s ->
                                                                if (sIdx == setIdx) s.copy(restSeconds = cycledRest) else s
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    val nextSets = exercise.sets.toMutableList()
                                                    nextSets[setIdx] = setEntry.copy(restSeconds = cycledRest)
                                                    nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                }
                                                onUpdateExercises(nextEx)
                                            }
                                        ) {
                                            val restDisplayLabel = when {
                                                isSupersetWithNext -> "🔗→${('A' + (index - supersetRange.first + 1))}"
                                                isPartOfSuperset -> "🔗${formatRestShort(setRestSec)}"
                                                else -> formatRestShort(setRestSec)
                                            }
                                            Surface(
                                                modifier = Modifier.size(48.dp, 34.dp),
                                                color = P.bgControl,
                                                border = BorderStroke(1.dp, if (isPartOfSuperset) P.accentSecondary.copy(alpha = 0.5f) else P.borderSubtle),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = restDisplayLabel,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (setRestSec > 0 || isPartOfSuperset) P.accentSecondary else P.textSecondary
                                                    )
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier.width(38.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            SpringButton(
                                                disabled = restRemainingSec > 0,
                                                scaleTo = 0.82f,
                                                onClick = {
                                                    onHaptic()
                                                    val nextCompleted = !setEntry.isCompleted
                                                    val updatedSet = setEntry.copy(isCompleted = nextCompleted)

                                                    val nextSets = exercise.sets.toMutableList()
                                                    nextSets[setIdx] = updatedSet
                                                    val nextEx = exercises.toMutableList()
                                                    nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                    onUpdateExercises(nextEx)

                                                    if (nextCompleted) {
                                                        if (isSetPersonalRecord(updatedSet, records)) {
                                                            prCelebrationMessage = "${exercise.name} • ${updatedSet.weight}kg × ${updatedSet.reps}"
                                                        }

                                                        if (isPartOfSuperset) {
                                                            val unfinishedPartnerName = supersetRange.firstNotNullOfOrNull { activeIdx ->
                                                                val origIdx = activeExercisesList[activeIdx].second
                                                                val partnerEx = nextEx[origIdx]
                                                                val partnerSet = partnerEx.sets.getOrNull(setIdx) ?: partnerEx.sets.lastOrNull()
                                                                if (partnerSet != null && !partnerSet.isCompleted) partnerEx.name else null
                                                            }

                                                            if (unfinishedPartnerName == null) {
                                                                val connectedRestSec = supersetRange.maxOf { activeIdx ->
                                                                    val origIdx = activeExercisesList[activeIdx].second
                                                                    val partnerEx = nextEx[origIdx]
                                                                    partnerEx.sets.getOrNull(setIdx)?.restSeconds?.takeIf { it >= 0 }
                                                                        ?: partnerEx.restSeconds.takeIf { it > 0 }
                                                                        ?: settings.restTimerSeconds
                                                                }
                                                                if (!isEditingPastWorkout) {
                                                                    sendSetDoneNotification(context, "Superset Round ${setIdx + 1}", updatedSet.weight, updatedSet.reps, connectedRestSec)
                                                                    if (connectedRestSec > 0) {
                                                                        val newRestEnd = System.currentTimeMillis() + (connectedRestSec * 1000L)
                                                                        LocalStorage.setRestEndTimeMillis(context, newRestEnd, fromWidget = true)
                                                                    }
                                                                }
                                                            } else {
                                                                if (!isEditingPastWorkout) {
                                                                    sendSetDoneNotification(context, exercise.name, updatedSet.weight, updatedSet.reps, 0)
                                                                    Toast.makeText(
                                                                        context,
                                                                        "🔗 Superset: No rest yet! Go straight to $unfinishedPartnerName (Set ${setIdx + 1})",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        } else {
                                                            if (!isEditingPastWorkout) {
                                                                sendSetDoneNotification(context, exercise.name, updatedSet.weight, updatedSet.reps, setRestSec)
                                                                if (setRestSec > 0) {
                                                                    val newRestEnd = System.currentTimeMillis() + (setRestSec * 1000L)
                                                                    LocalStorage.setRestEndTimeMillis(context, newRestEnd, fromWidget = true)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(32.dp, 34.dp),
                                                    color = if (setEntry.isCompleted) P.accentPrimary else P.bgControl,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "✓",
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = if (setEntry.isCompleted) P.onAccentPrimary else P.textSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .then(
                                            if (index == 0) {
                                                Modifier.onGloballyPositioned { coords ->
                                                    workoutTutorialBounds["WORKOUT_SET_ACTIONS"] = coords.boundsInRoot()
                                                }
                                            } else Modifier
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SpringButton(
                                        disabled = restRemainingSec > 0,
                                        scaleTo = 0.96f,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onHaptic()
                                            val lastSet = exercise.sets.last()
                                            val nextSets = exercise.sets + SetEntry(
                                                setNumber = exercise.sets.size + 1,
                                                weight = lastSet.weight,
                                                reps = lastSet.reps,
                                                isWarmup = false,
                                                intensityType = "NORMAL",
                                                isCompleted = false,
                                                restSeconds = lastSet.restSeconds.takeIf { it >= 0 } ?: exDefaultRest
                                            )
                                            val nextEx = exercises.toMutableList()
                                            nextEx[exIdx] = exercise.copy(sets = nextSets)
                                            onUpdateExercises(nextEx)
                                        }
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp),
                                            color = P.bgControl,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("+ ADD SET", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = P.textPrimary)
                                            }
                                        }
                                    }

                                    SpringButton(
                                        disabled = restRemainingSec > 0,
                                        scaleTo = 0.96f,
                                        modifier = Modifier.weight(1.15f),
                                        onClick = {
                                            onHaptic()
                                            val lastSet = exercise.sets.last()
                                            val lastW = parseWeightClean(lastSet.weight, 20f)
                                            val dropWeight = formatKgClean((lastW * 0.8f).coerceAtLeast(2.5f))
                                            val nextSets = exercise.sets + SetEntry(
                                                setNumber = exercise.sets.size + 1,
                                                weight = dropWeight,
                                                reps = lastSet.reps,
                                                isWarmup = false,
                                                intensityType = "DROP",
                                                isCompleted = false,
                                                restSeconds = 0
                                            )
                                            val nextEx = exercises.toMutableList()
                                            nextEx[exIdx] = exercise.copy(sets = nextSets)
                                            onUpdateExercises(nextEx)
                                        }
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp),
                                            color = P.dangerSurface,
                                            border = BorderStroke(1.dp, P.dangerText.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("🔥 + DROP SET", fontSize = 11.sp, fontWeight = FontWeight.Black, color = P.dangerText)
                                            }
                                        }
                                    }

                                    if (exercise.sets.size > 1) {
                                        SpringButton(
                                            disabled = restRemainingSec > 0,
                                            scaleTo = 0.94f,
                                            onClick = {
                                                onHaptic()
                                                val nextSets = exercise.sets.dropLast(1)
                                                val nextEx = exercises.toMutableList()
                                                nextEx[exIdx] = exercise.copy(sets = nextSets)
                                                onUpdateExercises(nextEx)
                                            }
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(64.dp, 32.dp),
                                                color = P.bgControl,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("− SET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = P.textSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    if (skippedExercisesList.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            color = P.bgCard,
                            border = BorderStroke(1.dp, P.warningText),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "SKIPPED FOR TODAY (${skippedExercisesList.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    color = P.warningText,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                skippedExercisesList.forEach { (ex, originalIndex) ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        color = P.bgControl,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(ex.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = P.textSecondary, textDecoration = TextDecoration.LineThrough)
                                                Text(ex.targetMuscle, fontSize = 11.sp, color = P.textSecondary)
                                            }
                                            SpringButton(
                                                disabled = restRemainingSec > 0,
                                                scaleTo = 0.92f,
                                                onClick = {
                                                    onHaptic()
                                                    val nextEx = exercises.toMutableList()
                                                    nextEx[originalIndex] = ex.copy(isSkipped = false)
                                                    onUpdateExercises(nextEx)
                                                }
                                            ) {
                                                Surface(color = P.accentPrimary, shape = RoundedCornerShape(8.dp)) {
                                                    Text(
                                                        text = "UNDO SKIP",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = P.onAccentPrimary,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SpringButton(
                        disabled = restRemainingSec > 0,
                        scaleTo = 0.97f,
                        onClick = {
                            onHaptic()
                            newExerciseName = ""
                            showAddExerciseModal = true
                        }
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            color = P.bgCard,
                            border = BorderStroke(1.dp, P.borderSubtle),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+ ADD EXERCISE TO ${split.title.uppercase()}",
                                    color = P.accentPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), color = P.bgPrimary) {
                SpringButton(
                    disabled = restRemainingSec > 0,
                    scaleTo = 0.96f,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    onClick = {
                        onHaptic()
                        val dur = if (isEditingPastWorkout) {
                            history.find { it.splitTitle == split.title }?.durationSeconds ?: 0L
                        } else {
                            ((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L)
                        }
                        finalDurationSeconds = dur
                        showCompletionModal = true
                    }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        color = if (restRemainingSec > 0) P.bgControl else P.accentPrimary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isEditingPastWorkout) "SAVE WORKOUT UPDATES" else "FINISH & REVIEW WORKOUT",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = if (restRemainingSec > 0) P.textSecondary else P.onAccentPrimary
                            )
                        }
                    }
                }
            }
        }

        // --- Extracted Modals ---

        if (inspectedExerciseIndex != null && inspectedExerciseIndex!! in exercises.indices) {
            val exIdx = inspectedExerciseIndex!!
            ExerciseInspectionModal(
                splitTitle = split.title,
                exIdx = exIdx,
                totalExercisesCount = exercises.size,
                targetEx = exercises[exIdx],
                history = history,
                restRemainingSec = restRemainingSec,
                noteDraftText = noteDraftText,
                onNoteDraftChange = { noteDraftText = it },
                P = P,
                onHaptic = onHaptic,
                onMoveUp = {
                    onReorderExercises(exIdx, exIdx - 1)
                    inspectedExerciseIndex = exIdx - 1
                },
                onMoveDown = {
                    onReorderExercises(exIdx, exIdx + 1)
                    inspectedExerciseIndex = exIdx + 1
                },
                onRequestRemove = {
                    confirmRemoveExerciseName = exercises[exIdx].name
                    inspectedExerciseIndex = null
                },
                onSaveAndClose = {
                    LocalStorage.saveExerciseNote(context, exercises[exIdx].name, noteDraftText)
                    noteRefreshTrigger += 1
                    inspectedExerciseIndex = null
                },
                onDismiss = { inspectedExerciseIndex = null }
            )
        }

        if (confirmRemoveExerciseName != null) {
            AlertDialog(
                onDismissRequest = { confirmRemoveExerciseName = null },
                containerColor = P.bgCard,
                titleContentColor = P.textPrimary,
                title = { Text("Remove Exercise?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Remove \"${confirmRemoveExerciseName}\" from ${split.title}? It won't appear on future ${split.title} sessions unless you add it back.",
                        color = P.textSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onHaptic()
                        onRemoveExercisePermanently(confirmRemoveExerciseName!!)
                        confirmRemoveExerciseName = null
                    }) {
                        Text("Remove", color = P.dangerText, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemoveExerciseName = null }) {
                        Text("Cancel", color = P.textSecondary)
                    }
                }
            )
        }

        if (showAddExerciseModal) {
            AlertDialog(
                onDismissRequest = { showAddExerciseModal = false },
                containerColor = P.bgCard,
                titleContentColor = P.textPrimary,
                title = { Text("Add Exercise", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newExerciseName,
                        onValueChange = { newExerciseName = it },
                        placeholder = { Text("Exercise name", color = P.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = P.accentPrimary,
                            unfocusedBorderColor = P.borderStrong,
                            focusedTextColor = P.textPrimary,
                            unfocusedTextColor = P.textPrimary
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newExerciseName.isNotBlank()) {
                            onHaptic()
                            val trimmed = newExerciseName.trim()
                            onAddCustomExercise(trimmed)
                            onUpdateExercises(
                                exercises + ExerciseEntry(
                                    name = trimmed,
                                    targetMuscle = "Custom",
                                    restSeconds = settings.restTimerSeconds,
                                    sets = listOf(
                                        SetEntry(1, weight = "20", reps = "8", isWarmup = true, intensityType = "WARMUP", restSeconds = 60),
                                        SetEntry(2, weight = "40", reps = "8", isWarmup = false, intensityType = "NORMAL", restSeconds = settings.restTimerSeconds),
                                        SetEntry(3, weight = "40", reps = "8", isWarmup = false, intensityType = "NORMAL", restSeconds = settings.restTimerSeconds)
                                    )
                                )
                            )
                            newExerciseName = ""
                            showAddExerciseModal = false
                        }
                    }) {
                        Text("Add", color = P.accentPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddExerciseModal = false }) {
                        Text("Cancel", color = P.textSecondary)
                    }
                }
            )
        }

        if (showCompletionModal) {
            WorkoutCompletionModal(
                split = split,
                exercises = exercises,
                skippedExercisesList = skippedExercisesList,
                history = history,
                userProfile = userProfile,
                isEditingPastWorkout = isEditingPastWorkout,
                finalDurationSeconds = finalDurationSeconds,
                attachedPhotoPath = attachedPhotoPath,
                P = P,
                onHaptic = onHaptic,
                onUnskipAll = {
                    onUpdateExercises(exercises.map { it.copy(isSkipped = false) })
                    showCompletionModal = false
                },
                onClearPhoto = { attachedPhotoPath = null },
                onLaunchCamera = { cameraLauncher.launch(null) },
                onLaunchGallery = { galleryLauncher.launch("image/*") },
                onConfirmFinish = {
                    showCompletionModal = false
                    onFinishWorkout(
                        finalDurationSeconds,
                        attachedPhotoPath,
                        skippedExercisesList.map { it.first.name }
                    )
                },
                onDismiss = { showCompletionModal = false }
            )
        }

        if (showWorkoutTutorial) {
            SpotlightTutorialOverlay(
                steps = workoutTutorialSteps,
                currentStepIndex = workoutTutorialStepIndex,
                targetBounds = workoutTutorialBounds,
                P = P,
                onNext = {
                    if (workoutTutorialStepIndex < workoutTutorialSteps.lastIndex) {
                        workoutTutorialStepIndex++
                    } else {
                        TutorialPrefs.markWorkoutTutorialSeen(context)
                        showWorkoutTutorial = false
                    }
                },
                onPrev = {
                    if (workoutTutorialStepIndex > 0) workoutTutorialStepIndex--
                },
                onSkip = {
                    TutorialPrefs.markWorkoutTutorialSeen(context)
                    showWorkoutTutorial = false
                }
            )
        }
    }
}

// ============================================================================
// 3. MODULARIZED WORKOUT DIALOGS (STATS MODAL & COMPLETION MODAL)
// ============================================================================

@Composable
private fun ExerciseInspectionModal(
    splitTitle: String,
    exIdx: Int,
    totalExercisesCount: Int,
    targetEx: ExerciseEntry,
    history: List<CompletedWorkout>,
    restRemainingSec: Int,
    noteDraftText: String,
    onNoteDraftChange: (String) -> Unit,
    P: GymPalette,
    onHaptic: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRequestRemove: () -> Unit,
    onSaveAndClose: () -> Unit,
    onDismiss: () -> Unit
) {
    val rec = remember(targetEx.name, history) { computeExerciseRecords(targetEx.name, history) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.bgCard,
        titleContentColor = P.textPrimary,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(targetEx.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                    if (targetEx.targetMuscle.isNotBlank()) {
                        Text(targetEx.targetMuscle, fontSize = 12.sp, color = P.textSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = exIdx > 0 && restRemainingSec == 0) {
                                onHaptic()
                                onMoveUp()
                            },
                        color = if (exIdx > 0 && restRemainingSec == 0) P.bgControl else P.bgControl.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("↑", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = exIdx < totalExercisesCount - 1 && restRemainingSec == 0) {
                                onHaptic()
                                onMoveDown()
                            },
                        color = if (exIdx < totalExercisesCount - 1 && restRemainingSec == 0) P.bgControl else P.bgControl.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("↓", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val maxWFormatted = if (rec.maxWeightKg % 1f == 0f) "${rec.maxWeightKg.toInt()}kg" else "${rec.maxWeightKg}kg"
                    listOf(
                        "${rec.bestEstimated1Rm}kg" to "Est. 1RM",
                        maxWFormatted to "Max Weight",
                        rec.bestSetText to "Best Set"
                    ).forEach { (statVal, statLabel) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = P.bgControl,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(statVal, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                                Text(statLabel, fontSize = 10.sp, color = P.textSecondary)
                            }
                        }
                    }
                }

                if (rec.chartPoints.size >= 2) {
                    TrendLineMiniChart(title = "Top Weight Progression", points = rec.chartPoints, P = P)
                }

                OutlinedTextField(
                    value = noteDraftText,
                    onValueChange = onNoteDraftChange,
                    label = { Text("Pinned Setup Note (seat, cable pin)", color = P.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = P.accentPrimary,
                        unfocusedBorderColor = P.borderStrong,
                        focusedTextColor = P.textPrimary,
                        unfocusedTextColor = P.textPrimary
                    )
                )

                Text("Recent History", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = P.textPrimary)

                if (rec.recentHistory.isEmpty()) {
                    Text("No previous sessions logged yet.", fontSize = 12.sp, color = P.textSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rec.recentHistory.take(4).forEach { (dateLabel, setsLine) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = P.bgControl,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(dateLabel, fontSize = 10.sp, color = P.textSecondary)
                                    Text(setsLine, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = P.textPrimary)
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        if (restRemainingSec == 0) {
                            onHaptic()
                            onRequestRemove()
                        }
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Remove from $splitTitle",
                        color = if (restRemainingSec == 0) P.dangerText else P.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onHaptic()
                onSaveAndClose()
            }) {
                Text("Save & Close", color = P.accentPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = P.textSecondary)
            }
        }
    )
}

@Composable
private fun WorkoutCompletionModal(
    split: WorkoutSplit,
    exercises: List<ExerciseEntry>,
    skippedExercisesList: List<Pair<ExerciseEntry, Int>>,
    history: List<CompletedWorkout>,
    userProfile: UserProfile,
    isEditingPastWorkout: Boolean,
    finalDurationSeconds: Long,
    attachedPhotoPath: String?,
    P: GymPalette,
    onHaptic: () -> Unit,
    onUnskipAll: () -> Unit,
    onClearPhoto: () -> Unit,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onConfirmFinish: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val performed = exercises.filter { !it.isSkipped }
    var workSetsDone = 0
    var totalVolFloat = 0f
    var prCount = 0
    for (ex in performed) {
        val rec = computeExerciseRecords(ex.name, history)
        for (s in ex.sets) {
            if (!s.isWarmup && s.intensityType != "WARMUP") {
                val r = parseRepsClean(s.reps, 0)
                val kg = parseWeightClean(s.weight, 0f)
                if (r > 0) workSetsDone++
                totalVolFloat += (kg * r)
            }
            if (isSetPersonalRecord(s, rec)) prCount++
        }
    }
    val totalVol = totalVolFloat.roundToInt()
    val mins = finalDurationSeconds / 60

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.bgCard,
        titleContentColor = P.textPrimary,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditingPastWorkout) "UPDATE WORKOUT" else "SESSION COMPLETE ⚡",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = P.textPrimary
                    )
                    Text(
                        text = split.title.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = P.accentPrimary
                    )
                }
                Surface(
                    color = P.bgControl,
                    border = BorderStroke(1.dp, P.accentSecondary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("⚖️ BW", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = P.textSecondary)
                        Text("${formatKgClean(userProfile.currentWeightKg)}kg", fontSize = 13.sp, fontWeight = FontWeight.Black, color = P.accentSecondary)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpringButton(
                    scaleTo = 0.96f,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onHaptic()
                        shareAiWorkoutReport(
                            context = context,
                            splitTitle = split.title,
                            durationSeconds = finalDurationSeconds,
                            exercises = exercises,
                            history = history,
                            bodyWeightKg = userProfile.currentWeightKg,
                            photoPath = attachedPhotoPath
                        )
                    }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        color = P.bgControl,
                        border = BorderStroke(1.5.dp, P.accentSecondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "📤 SHARE STRAVA STORY CARD",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = P.accentSecondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "${mins}m" to "TIME",
                        "$workSetsDone" to "SETS",
                        "${totalVol}kg" to "VOLUME",
                        "🏆 $prCount" to "PRs"
                    ).forEach {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = P.bgControl,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(it.first, fontSize = 13.sp, fontWeight = FontWeight.Black, color = P.textPrimary)
                                Text(it.second, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = P.textSecondary)
                            }
                        }
                    }
                }

                if (skippedExercisesList.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = P.warningSurface,
                        border = BorderStroke(1.dp, P.warningText),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "⏭ EXERCISES SKIPPED TODAY (${skippedExercisesList.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = P.warningText
                            )
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                skippedExercisesList.forEach { (ex, _) ->
                                    Text("• ${ex.name} (${ex.targetMuscle})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                                }
                            }
                            SpringButton(
                                scaleTo = 0.96f,
                                onClick = {
                                    onHaptic()
                                    onUnskipAll()
                                },
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                Surface(modifier = Modifier.fillMaxWidth(), color = P.bgCard, shape = RoundedCornerShape(8.dp)) {
                                    Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                        Text("↩ UNSKIP & DO THESE EXERCISES NOW", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = P.warningText)
                                    }
                                }
                            }
                        }
                    }
                }

                if (attachedPhotoPath != null) {
                    val previewBitmap = remember(attachedPhotoPath) { PhotoStorage.loadImageBitmap(attachedPhotoPath) }
                    if (previewBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            SpringButton(
                                onClick = onClearPhoto,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                Surface(color = Color(0xBF000000), shape = CircleShape) {
                                    Text(
                                        text = "✕",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpringButton(
                        scaleTo = 0.95f,
                        modifier = Modifier.weight(1f),
                        onClick = onLaunchCamera
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            color = P.bgControl,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📷 Take Pump Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                            }
                        }
                    }
                    SpringButton(
                        scaleTo = 0.95f,
                        modifier = Modifier.weight(1f),
                        onClick = onLaunchGallery
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            color = P.bgControl,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🖼 Gallery", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmFinish,
                colors = ButtonDefaults.buttonColors(containerColor = P.accentPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("SAVE TO LOGBOOK", color = P.onAccentPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Back", color = P.textSecondary)
            }
        }
    )
}