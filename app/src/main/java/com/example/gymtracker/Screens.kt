package com.example.gymtracker

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- Clean, Simple Homepage ---
@Composable
fun HomeScreen(
    splits: List<WorkoutSplit>,
    currentWeek: WeekSummaryData,
    settings: AppSettings,
    onSelectWorkout: (WorkoutSplit) -> Unit,
    onOpenWeeklySummary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val P = LocalPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Workouts",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = P.textPrimary
                )
                Text(
                    text = "${currentWeek.completedCoreSplits.size} of 3 weekly sessions completed",
                    fontSize = 14.sp,
                    color = P.textSecondary
                )
            }

            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable { onOpenSettings() },
                color = P.bgCard,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⚙", fontSize = 18.sp, color = P.textSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = splits,
                key = { it.title },
                contentType = { "split_card" }
            ) { split ->
                val isRecommended = split.title == currentWeek.recommendedSplit
                val wasDoneThisWeek = currentWeek.workouts.any { it.splitTitle == split.title }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectWorkout(split) },
                    color = P.bgCard,
                    border = if (isRecommended) BorderStroke(1.5.dp, P.accentPrimary) else null,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = split.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = P.textPrimary
                                )
                                if (isRecommended) {
                                    Surface(
                                        color = P.accentPrimary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Recommended",
                                            color = P.accentPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (wasDoneThisWeek) {
                                    Surface(
                                        color = P.successSurface,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "✓ Done",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = P.successText,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = split.subtitle,
                                fontSize = 13.sp,
                                color = P.textSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isRecommended) P.accentPrimary else P.bgControl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "›",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRecommended) P.onAccentPrimary else P.textSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onOpenWeeklySummary() },
            color = P.bgCard,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Weekly Summary & History",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = P.textPrimary
                )
                Text(
                    text = "›",
                    fontSize = 20.sp,
                    color = P.textSecondary
                )
            }
        }
    }
}

// --- Isolated Elapsed Clock ---
@Composable
fun ElapsedWorkoutClock(startTimeMillis: Long) {
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
        text = String.format(Locale.US, "%d:%02d • 6–8 rep target", mins, secs),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = P.textSecondary
    )
}

@Composable
fun RestTimerChip(
    triggerToken: Long,
    durationSeconds: Int,
    soundEnabled: Boolean,
    onHaptic: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    var remaining by remember { mutableIntStateOf(0) }

    // Start new rest timer when user checks off a set in-app
    LaunchedEffect(triggerToken) {
        if (triggerToken > 0L && durationSeconds > 0) {
            val endTime = System.currentTimeMillis() + (durationSeconds * 1000L)
            LocalStorage.setRestEndTimeMillis(context, endTime)
            remaining = durationSeconds
        }
    }

    // Keep UI countdown synced with LocalStorage (even if started from the Home Screen Widget!)
    LaunchedEffect(Unit) {
        while (true) {
            val endMillis = LocalStorage.getRestEndTimeMillis(context)
            val leftSec = ((endMillis - System.currentTimeMillis()) / 1000L).toInt()
            if (leftSec > 0) {
                remaining = leftSec
            } else if (remaining > 0) {
                remaining = 0
                onHaptic()
                RestAlarmScheduler.triggerRestFinishedAlert(context)
            }
            delay(500L)
        }
    }

    AnimatedVisibility(visible = remaining > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable {
                        onHaptic()
                        val newRemaining = remaining + 15
                        remaining = newRemaining
                        LocalStorage.setRestEndTimeMillis(
                            context,
                            System.currentTimeMillis() + (newRemaining * 1000L)
                        )
                    },
                color = P.bgControl,
                shape = RoundedCornerShape(9.dp)
            ) {
                Text(
                    text = "+15s",
                    color = P.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable {
                        remaining = 0
                        LocalStorage.setRestEndTimeMillis(context, 0L)
                    },
                color = P.accentPrimary,
                shape = RoundedCornerShape(9.dp)
            ) {
                val mins = remaining / 60
                val secs = remaining % 60
                Text(
                    text = String.format(Locale.US, "Rest %d:%02d ✕", mins, secs),
                    color = P.onAccentPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                )
            }
        }
    }
}
//--- Visual Progression Mini-Chart (5-Session Trend Line) ---
@Composable
fun ExerciseProgressionMiniChart(points: List<Pair<String, Float>>) {
    val P = LocalPalette.current
    if (points.size < 2) return

    val maxW = points.maxOf { it.second }.coerceAtLeast(1f)
    val minW = points.minOf { it.second }
    val range = (maxW - minW).takeIf { it > 0f } ?: 10f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = P.bgControl,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Weight Progression",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = P.textSecondary
                )
                Text(
                    text = "${points.first().second}kg → ${points.last().second}kg",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = P.accentPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                val path = Path()
                val coords = points.mapIndexed { idx, pair ->
                    val x = idx * stepX
                    val normY = (pair.second - minW) / range
                    val y = size.height - (normY * size.height)
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
                        color = P.accentPrimary,
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
                    val wTxt = if (wVal % 1f == 0f) "${wVal.toInt()}" else "$wVal"
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${wTxt}kg",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
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

// --- Clean Active Workout Screen ---
@Composable
fun ActiveWorkoutScreen(
    split: WorkoutSplit,
    exercises: List<ExerciseEntry>,
    history: List<CompletedWorkout>,
    settings: AppSettings,
    isEditingPastWorkout: Boolean = false,
    initialPhotoPath: String? = null,
    onHaptic: () -> Unit,
    onUpdateExercises: (List<ExerciseEntry>) -> Unit,
    onReorderExercises: (Int, Int) -> Unit,
    onAddCustomExercise: (String) -> Unit,
    onRemoveExercisePermanently: (String) -> Unit,
    onCancelWorkout: () -> Unit,
    onFinishWorkout: (Long, String?) -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current

    val startTimeMillis = remember { LocalStorage.getActiveSessionStartTime(context) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }

    var inspectedExerciseIndex by remember { mutableStateOf<Int?>(null) }
    var confirmRemoveExerciseName by remember { mutableStateOf<String?>(null) }
    var noteDraftText by remember { mutableStateOf("") }
    var noteRefreshTrigger by remember { mutableIntStateOf(0) }

    // Post-Workout Completion Receipt Modal State
    var showCompletionSheet by remember { mutableStateOf(false) }
    var finalDurationSeconds by remember { mutableLongStateOf(0L) }
    var attachedPhotoPath by remember { mutableStateOf(initialPhotoPath) }

    // Camera & Gallery Launchers for Bonus Workout Photo
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val savedPath = PhotoStorage.saveBitmapToInternalStorage(context, bitmap)
            if (savedPath != null) attachedPhotoPath = savedPath
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val savedPath = PhotoStorage.copyUriToInternalStorage(context, uri)
            if (savedPath != null) attachedPhotoPath = savedPath
        }
    }

    var restTimerTriggerToken by remember { mutableLongStateOf(0L) }

    // Smooth Animated Set Completion Progress Bar
    val totalSetsCount = remember(exercises) { exercises.sumOf { it.sets.size }.coerceAtLeast(1) }
    val completedSetsCount = remember(exercises) { exercises.sumOf { ex -> ex.sets.count { it.isCompleted } } }
    val animatedProgress by animateFloatAsState(
        targetValue = completedSetsCount.toFloat() / totalSetsCount.toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "workout_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isEditingPastWorkout) "Edit ${split.title}" else split.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = P.textPrimary
                )
                if (!isEditingPastWorkout) {
                    ElapsedWorkoutClock(startTimeMillis = startTimeMillis)
                } else {
                    Text(
                        text = "Editing past workout log",
                        fontSize = 12.sp,
                        color = P.accentPrimary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RestTimerChip(
                    triggerToken = restTimerTriggerToken,
                    durationSeconds = settings.restTimerSeconds,
                    soundEnabled = settings.restSoundEnabled,
                    onHaptic = onHaptic
                )

                TextButton(onClick = onCancelWorkout) {
                    Text(
                        text = "Cancel",
                        color = P.dangerText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Smooth Animated Progress Bar Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$completedSetsCount of $totalSetsCount sets completed",
                    fontSize = 11.sp,
                    color = P.textSecondary
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = P.accentPrimary
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(P.bgControl)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(P.accentPrimary)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = exercises,
                key = { _, item -> item.name },
                contentType = { _, _ -> "exercise_card" }
            ) { exIndex, exercise ->
                val aiAdvice = remember(split.title, exercise, history, settings.weightStepKg) {
                    evaluateSplitIsolatedAi(split.title, exercise, history, settings.weightStepKg)
                }
                val records = remember(exercise.name, history) {
                    computeExerciseRecords(exercise.name, history)
                }
                val pinnedNote = remember(exercise.name, noteRefreshTrigger) {
                    LocalStorage.getExerciseNote(context, exercise.name)
                }

                ExerciseCard(
                    exercise = exercise,
                    pinnedNote = pinnedNote,
                    records = records,
                    weightStepKg = settings.weightStepKg,
                    aiAdvice = aiAdvice,
                    onHaptic = onHaptic,
                    onSetChecked = { checkedNow ->
                        if (checkedNow && settings.restTimerSeconds > 0) {
                            restTimerTriggerToken = System.currentTimeMillis()
                        }
                    },
                    onUpdateExercise = { updated ->
                        val list = exercises.toMutableList()
                        list[exIndex] = updated
                        onUpdateExercises(list)
                    },
                    onOpenDetailsAndHistory = {
                        onHaptic()
                        noteDraftText = pinnedNote
                        inspectedExerciseIndex = exIndex
                    }
                )
            }

            item(key = "add_exercise_btn", contentType = "footer_btn") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onHaptic()
                            showAddExerciseDialog = true
                        },
                    color = P.bgCard,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+ Add Exercise",
                            color = P.accentPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(P.bgPrimary)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    onHaptic()
                    finalDurationSeconds = ((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L)
                    showCompletionSheet = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = P.accentPrimary)
            ) {
                Text(
                    text = if (isEditingPastWorkout) "Save Changes" else "Finish Workout",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = P.onAccentPrimary
                )
            }
        }
    }

    // --- Post-Workout Completion Receipt & Optional Photo Dialog ---
    if (showCompletionSheet) {
        val workSetsDone = exercises.sumOf { ex ->
            ex.sets.count { !it.isWarmup && (it.reps.toIntOrNull() ?: 0) > 0 }
        }
        val totalVol = exercises.sumOf { ex ->
            ex.sets.filter { !it.isWarmup }.sumOf { s ->
                (s.weight.toDoubleOrNull() ?: 0.0) * (s.reps.toIntOrNull() ?: 0)
            }
        }.toInt()
        val prCount = exercises.sumOf { ex ->
            val rec = computeExerciseRecords(ex.name, history)
            ex.sets.count { isSetPersonalRecord(it, rec) }
        }
        val mins = finalDurationSeconds / 60

        AlertDialog(
            onDismissRequest = { showCompletionSheet = false },
            containerColor = P.bgCard,
            titleContentColor = P.textPrimary,
            title = {
                Column {
                    Text(
                        text = if (isEditingPastWorkout) "Update Workout" else "Workout Complete 🎉",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = P.textPrimary
                    )
                    Text(
                        text = split.title,
                        fontSize = 14.sp,
                        color = P.accentPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "${mins}m" to "Duration",
                            "$workSetsDone" to "Work Sets",
                            "${totalVol}kg" to "Volume",
                            "🏆 $prCount" to "New PRs"
                        ).forEach { (valTxt, lblTxt) ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = P.bgControl,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(valTxt, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                                    Text(lblTxt, fontSize = 10.sp, color = P.textSecondary)
                                }
                            }
                        }
                    }

                    // Photo Preview if Attached
                    val previewBitmap = remember(attachedPhotoPath) {
                        PhotoStorage.loadImageBitmap(attachedPhotoPath)
                    }
                    if (previewBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Workout Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .clickable { attachedPhotoPath = null },
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "✕",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Camera & Gallery Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { cameraLauncher.launch(null) },
                            color = P.bgControl,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📷 Take Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = P.textPrimary)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { galleryLauncher.launch("image/*") },
                            color = P.bgControl,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🖼 From Gallery", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = P.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletionSheet = false
                        onFinishWorkout(finalDurationSeconds, attachedPhotoPath)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = P.accentPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save to History", color = P.onAccentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompletionSheet = false }) {
                    Text("Back", color = P.textSecondary)
                }
            }
        )
    }

    // --- Full Exercise History, Mini-Chart, Reorder, Setup Note & Remove Dialog ---
    if (inspectedExerciseIndex != null && inspectedExerciseIndex!! in exercises.indices) {
        val exIdx = inspectedExerciseIndex!!
        val targetEx = exercises[exIdx]
        val rec = remember(targetEx.name, history) {
            computeExerciseRecords(targetEx.name, history)
        }
        AlertDialog(
            onDismissRequest = { inspectedExerciseIndex = null },
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
                            text = targetEx.name,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = P.textPrimary
                        )
                        if (targetEx.targetMuscle.isNotBlank()) {
                            Text(
                                text = targetEx.targetMuscle,
                                fontSize = 12.sp,
                                color = P.textSecondary
                            )
                        }
                    }

                    // Up / Down Exercise Reorder Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = exIdx > 0) {
                                    onHaptic()
                                    onReorderExercises(exIdx, exIdx - 1)
                                    inspectedExerciseIndex = exIdx - 1
                                },
                            color = if (exIdx > 0) P.bgControl else P.bgControl.copy(alpha = 0.3f),
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
                                .clickable(enabled = exIdx < exercises.lastIndex) {
                                    onHaptic()
                                    onReorderExercises(exIdx, exIdx + 1)
                                    inspectedExerciseIndex = exIdx + 1
                                },
                            color = if (exIdx < exercises.lastIndex) P.bgControl else P.bgControl.copy(alpha = 0.3f),
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
                        val maxWFormatted = if (rec.maxWeightKg % 1f == 0f) {
                            "${rec.maxWeightKg.toInt()}kg"
                        } else {
                            "${rec.maxWeightKg}kg"
                        }
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

                    // Visual Progression Mini-Chart
                    if (rec.chartPoints.size >= 2) {
                        ExerciseProgressionMiniChart(points = rec.chartPoints)
                    }

                    OutlinedTextField(
                        value = noteDraftText,
                        onValueChange = { noteDraftText = it },
                        label = { Text("Pinned Setup Note (seat, cable pin)", color = P.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = P.accentPrimary,
                            unfocusedBorderColor = P.borderStrong,
                            focusedTextColor = P.textPrimary,
                            unfocusedTextColor = P.textPrimary
                        )
                    )

                    Text(
                        text = "Recent History",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = P.textPrimary
                    )

                    if (rec.recentHistory.isEmpty()) {
                        Text(
                            text = "No previous sessions logged yet.",
                            fontSize = 12.sp,
                            color = P.textSecondary
                        )
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
                            onHaptic()
                            val nameToDelete = targetEx.name
                            inspectedExerciseIndex = null
                            confirmRemoveExerciseName = nameToDelete
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Remove from ${split.title}",
                            color = P.dangerText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onHaptic()
                        LocalStorage.saveExerciseNote(context, targetEx.name, noteDraftText)
                        noteRefreshTrigger += 1
                        inspectedExerciseIndex = null
                    }
                ) {
                    Text("Save & Close", color = P.accentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedExerciseIndex = null }) {
                    Text("Cancel", color = P.textSecondary)
                }
            }
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
                TextButton(
                    onClick = {
                        onHaptic()
                        onRemoveExercisePermanently(confirmRemoveExerciseName!!)
                        confirmRemoveExerciseName = null
                    }
                ) {
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

    if (showAddExerciseDialog) {
        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
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
                TextButton(
                    onClick = {
                        if (newExerciseName.isNotBlank()) {
                            onHaptic()
                            val trimmed = newExerciseName.trim()
                            onAddCustomExercise(trimmed)
                            onUpdateExercises(
                                exercises + ExerciseEntry(
                                    name = trimmed,
                                    targetMuscle = "Custom"
                                )
                            )
                            newExerciseName = ""
                            showAddExerciseDialog = false
                        }
                    }
                ) {
                    Text("Add", color = P.accentPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExerciseDialog = false }) {
                    Text("Cancel", color = P.textSecondary)
                }
            }
        )
    }
}

// --- Clean Exercise Card ---
@Composable
fun ExerciseCard(
    exercise: ExerciseEntry,
    pinnedNote: String,
    records: ExerciseRecords,
    weightStepKg: Float,
    aiAdvice: AiAdvice?,
    onHaptic: () -> Unit,
    onSetChecked: (Boolean) -> Unit,
    onUpdateExercise: (ExerciseEntry) -> Unit,
    onOpenDetailsAndHistory: () -> Unit
) {
    val P = LocalPalette.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = P.bgCard,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenDetailsAndHistory() }
                ) {
                    Text(
                        text = exercise.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = P.textPrimary
                    )
                    val bestText = if (records.bestSetText != "—") {
                        " • Best: ${records.bestSetText}"
                    } else ""
                    Text(
                        text = "${exercise.targetMuscle.ifBlank { "Exercise" }}$bestText • Tap for history",
                        fontSize = 12.sp,
                        color = P.textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onOpenDetailsAndHistory() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⋯",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = P.textSecondary
                    )
                }
            }

            if (pinnedNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenDetailsAndHistory() },
                    color = P.bgControl,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "📌 $pinnedNote",
                        fontSize = 12.sp,
                        color = P.textPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (aiAdvice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val (bgCol, txtCol) = when {
                    aiAdvice.isPositive -> P.successSurface to P.successText
                    aiAdvice.isWarning -> P.warningSurface to P.warningText
                    else -> P.bgControl to P.accentPrimary
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = bgCol,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = aiAdvice.message,
                        color = txtCol,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = P.textSecondary,
                    modifier = Modifier.width(38.dp)
                )
                Text(
                    text = "kg",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = P.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1.15f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reps",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = P.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(42.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            var workSetCounter = 0
            exercise.sets.forEachIndexed { index, setEntry ->
                if (!setEntry.isWarmup) workSetCounter++
                val isPrSet = remember(setEntry, records) {
                    isSetPersonalRecord(setEntry, records)
                }
                val displayLabel = when {
                    setEntry.isWarmup -> "W"
                    isPrSet -> "🏆"
                    else -> "$workSetCounter"
                }
                val rowBg = if (setEntry.isCompleted) P.successSurface else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowBg)
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(38.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (setEntry.isWarmup) P.warningSurface else P.bgControl
                                )
                                .clickable {
                                    onHaptic()
                                    val updated = exercise.sets.toMutableList()
                                    updated[index] = setEntry.copy(isWarmup = !setEntry.isWarmup)
                                    onUpdateExercise(exercise.copy(sets = updated))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (setEntry.isWarmup) P.warningText else P.textPrimary
                            )
                        }
                    }

                    StepperControl(
                        value = setEntry.weight,
                        allowKeyboardTyping = true,
                        onValueChange = { newW ->
                            val updated = exercise.sets.toMutableList()
                            updated[index] = setEntry.copy(weight = newW)
                            onUpdateExercise(exercise.copy(sets = updated))
                        },
                        onStepDown = {
                            onHaptic()
                            val cur = setEntry.weight.toFloatOrNull() ?: 20f
                            val next = (cur - weightStepKg).coerceAtLeast(0f)
                            val formatted = if (next % 1f == 0f) "${next.toInt()}" else "$next"
                            val updated = exercise.sets.toMutableList()
                            updated[index] = setEntry.copy(weight = formatted)
                            onUpdateExercise(exercise.copy(sets = updated))
                        },
                        onStepUp = {
                            onHaptic()
                            val cur = setEntry.weight.toFloatOrNull() ?: 20f
                            val next = cur + weightStepKg
                            val formatted = if (next % 1f == 0f) "${next.toInt()}" else "$next"
                            val updated = exercise.sets.toMutableList()
                            updated[index] = setEntry.copy(weight = formatted)
                            onUpdateExercise(exercise.copy(sets = updated))
                        },
                        modifier = Modifier.weight(1.15f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    StepperControl(
                        value = setEntry.reps,
                        allowKeyboardTyping = false,
                        onValueChange = {},
                        onStepDown = {
                            onHaptic()
                            val cur = setEntry.reps.toIntOrNull() ?: 8
                            val next = (cur - 1).coerceAtLeast(0)
                            val updated = exercise.sets.toMutableList()
                            updated[index] = setEntry.copy(reps = "$next")
                            onUpdateExercise(exercise.copy(sets = updated))
                        },
                        onStepUp = {
                            onHaptic()
                            val cur = setEntry.reps.toIntOrNull() ?: 7
                            val next = cur + 1
                            val updated = exercise.sets.toMutableList()
                            updated[index] = setEntry.copy(reps = "$next")
                            onUpdateExercise(exercise.copy(sets = updated))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier.width(38.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (setEntry.isCompleted) P.successText else P.bgControl
                                )
                                .clickable {
                                    onHaptic()
                                    val nextState = !setEntry.isCompleted
                                    val updated = exercise.sets.toMutableList()
                                    updated[index] = setEntry.copy(isCompleted = nextState)
                                    onUpdateExercise(exercise.copy(sets = updated))
                                    onSetChecked(nextState)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (setEntry.isCompleted) Color.White else P.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable {
                            onHaptic()
                            val nextNum = exercise.sets.size + 1
                            val lastSet = exercise.sets.lastOrNull()
                            onUpdateExercise(
                                exercise.copy(
                                    sets = exercise.sets + SetEntry(
                                        setNumber = nextNum,
                                        weight = lastSet?.weight ?: "40",
                                        reps = lastSet?.reps ?: "8",
                                        isWarmup = false,
                                        isCompleted = false
                                    )
                                )
                            )
                        },
                    color = P.bgControl,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+ Set",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = P.textPrimary
                        )
                    }
                }

                if (exercise.sets.size > 1) {
                    Surface(
                        modifier = Modifier
                            .width(72.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable {
                                onHaptic()
                                onUpdateExercise(
                                    exercise.copy(sets = exercise.sets.dropLast(1))
                                )
                            },
                        color = P.bgControl,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "− Set",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = P.textSecondary
                            )
                        }
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
    modifier: Modifier = Modifier
) {
    val P = LocalPalette.current
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(P.bgControl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .clickable { onStepDown() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = P.textSecondary
            )
        }

        if (allowKeyboardTyping) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    color = P.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(P.accentPrimary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "0",
                                color = P.textSecondary.copy(alpha = 0.4f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.ifEmpty { "0" },
                    color = P.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .clickable { onStepUp() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = P.textPrimary
            )
        }
    }
}

// --- Clean Settings Screen (With Keep Screen Awake, Rest Alert Sound & Backup/Restore) ---
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onDataRestored: () -> Unit,
    onBack: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = LocalStorage.exportJsonBackupString(context)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
                Toast.makeText(context, "Backup saved successfully", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Failed to save backup", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                if (LocalStorage.importJsonBackupString(context, content)) {
                    onDataRestored()
                    Toast.makeText(context, "Workout history restored!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Failed to restore backup", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = P.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = P.textPrimary
            )
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                SettingGroupCard(title = "Theme") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedOptionButton(
                            label = "Dark",
                            selected = settings.isDarkMode,
                            onClick = { onUpdateSettings(settings.copy(isDarkMode = true)) },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOptionButton(
                            label = "Light",
                            selected = !settings.isDarkMode,
                            onClick = { onUpdateSettings(settings.copy(isDarkMode = false)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                SettingGroupCard(title = "Home Screen Widget") {
                    SegmentedOptionButton(
                        label = "+ Add Widget to Home Screen",
                        selected = true,
                        onClick = { GymWidgetProvider.requestPinWidgetToHomeScreen(context) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SettingGroupCard(title = "Display Refresh Rate") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedOptionButton(
                            label = "120 Hz",
                            selected = settings.targetFps == 120,
                            onClick = { onUpdateSettings(settings.copy(targetFps = 120)) },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOptionButton(
                            label = "60 Hz",
                            selected = settings.targetFps == 60,
                            onClick = { onUpdateSettings(settings.copy(targetFps = 60)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                SettingGroupCard(title = "Weight Increment (+ / −)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1.25f, 2.5f, 5.0f).forEach { step ->
                            val label = if (step % 1f == 0f) "${step.toInt()} kg" else "$step kg"
                            SegmentedOptionButton(
                                label = label,
                                selected = settings.weightStepKg == step,
                                onClick = { onUpdateSettings(settings.copy(weightStepKg = step)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                SettingGroupCard(title = "Rest Timer & Alert Sound") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Off", 60 to "60s", 90 to "90s", 120 to "2m", 180 to "3m").forEach { (sec, label) ->
                                SegmentedOptionButton(
                                    label = label,
                                    selected = settings.restTimerSeconds == sec,
                                    onClick = { onUpdateSettings(settings.copy(restTimerSeconds = sec)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SegmentedOptionButton(
                                label = "Music-Safe Beep On",
                                selected = settings.restSoundEnabled,
                                onClick = { onUpdateSettings(settings.copy(restSoundEnabled = true)) },
                                modifier = Modifier.weight(1f)
                            )
                            SegmentedOptionButton(
                                label = "Silent",
                                selected = !settings.restSoundEnabled,
                                onClick = { onUpdateSettings(settings.copy(restSoundEnabled = false)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                SettingGroupCard(title = "Keep Screen On During Workout") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedOptionButton(
                            label = "Keep Awake",
                            selected = settings.keepScreenAwake,
                            onClick = { onUpdateSettings(settings.copy(keepScreenAwake = true)) },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOptionButton(
                            label = "Normal Timeout",
                            selected = !settings.keepScreenAwake,
                            onClick = { onUpdateSettings(settings.copy(keepScreenAwake = false)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                SettingGroupCard(title = "Vibration Feedback") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedOptionButton(
                            label = "On",
                            selected = settings.hapticsEnabled,
                            onClick = { onUpdateSettings(settings.copy(hapticsEnabled = true)) },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOptionButton(
                            label = "Off",
                            selected = !settings.hapticsEnabled,
                            onClick = { onUpdateSettings(settings.copy(hapticsEnabled = false)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                SettingGroupCard(title = "Backup & Restore Data") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedOptionButton(
                            label = "Export Backup",
                            selected = false,
                            onClick = { exportLauncher.launch("gym_tracker_backup.json") },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOptionButton(
                            label = "Restore Backup",
                            selected = false,
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    val P = LocalPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = P.bgCard,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = P.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun SegmentedOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val P = LocalPalette.current
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (selected) P.accentPrimary else P.bgControl,
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) P.onAccentPrimary else P.textPrimary
            )
        }
    }
}

// --- Clean Weekly Summary Screen (With Edit, Delete & Photo Viewer) ---
@Composable
fun WeeklySummaryScreen(
    history: List<CompletedWorkout>,
    onEditWorkout: (CompletedWorkout) -> Unit,
    onDeleteWorkout: (Long) -> Unit,
    onBackHome: () -> Unit,
    onOpenMonthlyCalendar: () -> Unit
) {
    val P = LocalPalette.current
    var weekOffset by remember { mutableIntStateOf(0) }
    val weekData = remember(history, weekOffset) { computeWeekSummary(history, weekOffset) }
    var workoutPendingDelete by remember { mutableStateOf<CompletedWorkout?>(null) }
    var fullScreenPhotoPath by remember { mutableStateOf<String?>(null) }

    val totalWorkSets = remember(weekData) {
        weekData.workouts.sumOf { w ->
            w.exercises.sumOf { ex ->
                ex.sets.count { !it.isWarmup && (it.reps.toIntOrNull() ?: 0) > 0 }
            }
        }
    }

    val totalVolumeKg = remember(weekData) {
        weekData.workouts.sumOf { w ->
            w.exercises.sumOf { ex ->
                ex.sets.filter { !it.isWarmup }.sumOf { s ->
                    val kg = s.weight.toDoubleOrNull() ?: 0.0
                    val reps = s.reps.toIntOrNull() ?: 0
                    kg * reps
                }
            }
        }.toInt()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackHome) {
                Text("← Home", color = P.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "Weekly Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = P.textPrimary
            )
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = P.bgCard,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(P.bgControl)
                            .clickable { weekOffset -= 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("‹", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = weekData.titleLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = P.accentPrimary
                        )
                        Text(
                            text = weekData.dateRangeLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = P.textPrimary
                        )
                    }

                    val canGoForward = weekOffset < 0
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (canGoForward) P.bgControl else P.bgControl.copy(alpha = 0.35f))
                            .clickable(enabled = canGoForward) { weekOffset += 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "›",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canGoForward) P.textPrimary else P.textSecondary.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "${weekData.workouts.size}" to "Workouts",
                        "$totalWorkSets" to "Work Sets",
                        "${totalVolumeKg} kg" to "Volume"
                    ).forEach { (valStr, labelStr) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = P.bgControl,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(valStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                                Text(labelStr, fontSize = 11.sp, color = P.textSecondary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Push", "Pull", "Arms & Shoulders").forEach { coreSplit ->
                        val isDone = coreSplit in weekData.completedCoreSplits
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = if (isDone) P.successSurface else P.bgControl,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isDone) "✓ " else "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = P.successText
                                )
                                Text(
                                    text = if (coreSplit == "Arms & Shoulders") "Arms" else coreSplit,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDone) P.successText else P.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (weekData.workouts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No workouts logged this week.",
                        color = P.textSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = weekData.workouts,
                        key = { it.id },
                        contentType = { "history_card" }
                    ) { workout ->
                        WorkoutHistoryCard(
                            workout = workout,
                            onEdit = { onEditWorkout(workout) },
                            onRequestDelete = { workoutPendingDelete = workout },
                            onViewPhoto = { path -> fullScreenPhotoPath = path }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenMonthlyCalendar() },
            color = P.bgCard,
            shape = RoundedCornerShape(14.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "View Monthly Calendar",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = P.accentPrimary
                )
            }
        }
    }

    if (fullScreenPhotoPath != null) {
        FullScreenPhotoDialog(
            photoPath = fullScreenPhotoPath!!,
            onDismiss = { fullScreenPhotoPath = null }
        )
    }

    if (workoutPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutPendingDelete = null },
            containerColor = P.bgCard,
            titleContentColor = P.textPrimary,
            title = { Text("Delete Workout?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Delete ${workoutPendingDelete!!.splitTitle} from ${workoutPendingDelete!!.displayDate}?",
                    color = P.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteWorkout(workoutPendingDelete!!.id)
                        workoutPendingDelete = null
                    }
                ) {
                    Text("Delete", color = P.dangerText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutPendingDelete = null }) {
                    Text("Cancel", color = P.textSecondary)
                }
            }
        )
    }
}

@Composable
fun WorkoutHistoryCard(
    workout: CompletedWorkout,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val P = LocalPalette.current
    val photoBitmap = remember(workout.photoPath) {
        PhotoStorage.loadImageBitmap(workout.photoPath)
    }

    // Count exercises that have at least 1 logged set, plus total sets & duration
    val exerciseCount = remember(workout.exercises) {
        workout.exercises.count { ex ->
            ex.sets.any { it.weight.isNotBlank() || it.reps.isNotBlank() }
        }.coerceAtLeast(workout.exercises.size)
    }
    val totalSetsCount = remember(workout.exercises) {
        workout.exercises.sumOf { ex ->
            ex.sets.count { !it.isWarmup && (it.reps.toIntOrNull() ?: 0) > 0 }
        }
    }
    val durationLabel = remember(workout.durationSeconds) {
        val mins = (workout.durationSeconds / 60L).toInt()
        when {
            mins >= 60 -> "${mins / 60}h ${mins % 60}m"
            mins > 0 -> "$mins min"
            else -> "< 1 min"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onEdit() },
        color = P.bgCard,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workout.splitTitle,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = P.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = workout.displayDate,
                        fontSize = 12.sp,
                        color = P.textSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onEdit() },
                        color = P.accentPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            text = "Edit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = P.accentPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onRequestDelete() },
                        color = P.dangerSurface,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            text = "Delete",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = P.dangerText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean 3-Pill Summary: Exercises • Work Sets • Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "$exerciseCount Exercises",
                    "$totalSetsCount Work Sets",
                    "⏱ $durationLabel"
                ).forEach { pillText ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = P.bgControl,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pillText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = P.textPrimary
                            )
                        }
                    }
                }
            }

            if (photoBitmap != null && workout.photoPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = photoBitmap,
                    contentDescription = "Workout Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onViewPhoto(workout.photoPath) }
                )
            }
        }
    }
}

@Composable
fun FullScreenPhotoDialog(
    photoPath: String,
    onDismiss: () -> Unit
) {
    val P = LocalPalette.current
    val bmp = remember(photoPath) { PhotoStorage.loadImageBitmap(photoPath) }
    if (bmp == null) {
        onDismiss()
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.bgCard,
        text = {
            Image(
                bitmap = bmp,
                contentDescription = "Full Workout Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 460.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = P.accentPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// --- Clean Monthly Calendar View ---
@Composable
fun HistoryCalendarScreen(
    history: List<CompletedWorkout>,
    onEditWorkout: (CompletedWorkout) -> Unit,
    onDeleteWorkout: (Long) -> Unit,
    onBackToWeekly: () -> Unit
) {
    val P = LocalPalette.current
    val todayCal = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) }

    val todayKey = remember { DateUtils.formatDayKey(Date()) }
    var selectedDateKey by remember { mutableStateOf(todayKey) }
    var workoutPendingDelete by remember { mutableStateOf<CompletedWorkout?>(null) }
    var fullScreenPhotoPath by remember { mutableStateOf<String?>(null) }

    val workoutsByDate = remember(history) {
        history.groupBy { it.dateKey }
    }

    val monthTitle = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        DateUtils.formatMonthTitle(cal.time)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackToWeekly) {
                Text("← Back", color = P.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "Calendar",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = P.textPrimary
            )
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = P.bgCard,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(P.bgControl)
                            .clickable {
                                if (displayedMonth == 0) {
                                    displayedMonth = 11
                                    displayedYear -= 1
                                } else {
                                    displayedMonth -= 1
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                    }

                    Text(
                        text = monthTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = P.textPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(P.bgControl)
                            .clickable {
                                if (displayedMonth == 11) {
                                    displayedMonth = 0
                                    displayedYear += 1
                                } else {
                                    displayedMonth += 1
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = P.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayName ->
                        Text(
                            text = dayName,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = P.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val (firstDayOffset, daysInMonth, totalCells) = remember(displayedYear, displayedMonth) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, displayedYear)
                        set(Calendar.MONTH, displayedMonth)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    val offset = cal.get(Calendar.DAY_OF_WEEK) - 1
                    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val cells = ((offset + maxDays + 6) / 7) * 7
                    Triple(offset, maxDays, cells)
                }

                for (week in 0 until (totalCells / 7)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        for (dayCol in 0..6) {
                            val cellIndex = week * 7 + dayCol
                            val dayNumber = cellIndex - firstDayOffset + 1

                            if (dayNumber in 1..daysInMonth) {
                                val dateKey = String.format(
                                    Locale.US,
                                    "%04d-%02d-%02d",
                                    displayedYear,
                                    displayedMonth + 1,
                                    dayNumber
                                )
                                val dayWorkouts = workoutsByDate[dateKey].orEmpty()
                                val isSelected = dateKey == selectedDateKey

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isSelected -> P.accentPrimary
                                                dayWorkouts.isNotEmpty() -> P.bgControl
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { selectedDateKey = dateKey },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$dayNumber",
                                            fontSize = 13.sp,
                                            fontWeight = if (dayWorkouts.isNotEmpty() || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> P.onAccentPrimary
                                                dayWorkouts.isNotEmpty() -> P.textPrimary
                                                else -> P.textSecondary
                                            }
                                        )
                                        if (dayWorkouts.isNotEmpty() && !isSelected) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(P.accentPrimary)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).height(40.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val selectedDayWorkouts = workoutsByDate[selectedDateKey].orEmpty()

        if (selectedDayWorkouts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No workouts on $selectedDateKey",
                    color = P.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = selectedDayWorkouts,
                    key = { it.id },
                    contentType = { "history_card" }
                ) { workout ->
                    WorkoutHistoryCard(
                        workout = workout,
                        onEdit = { onEditWorkout(workout) },
                        onRequestDelete = { workoutPendingDelete = workout },
                        onViewPhoto = { path -> fullScreenPhotoPath = path }
                    )
                }
            }
        }
    }

    if (fullScreenPhotoPath != null) {
        FullScreenPhotoDialog(
            photoPath = fullScreenPhotoPath!!,
            onDismiss = { fullScreenPhotoPath = null }
        )
    }

    if (workoutPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutPendingDelete = null },
            containerColor = P.bgCard,
            titleContentColor = P.textPrimary,
            title = { Text("Delete Workout?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Delete ${workoutPendingDelete!!.splitTitle} from ${workoutPendingDelete!!.displayDate}?",
                    color = P.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteWorkout(workoutPendingDelete!!.id)
                        workoutPendingDelete = null
                    }
                ) {
                    Text("Delete", color = P.dangerText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutPendingDelete = null }) {
                    Text("Cancel", color = P.textSecondary)
                }
            }
        )
    }
}