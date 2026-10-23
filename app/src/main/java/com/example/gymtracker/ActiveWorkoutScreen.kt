package com.example.gymtracker

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val EXERCISE_KEY_PREFIX = "ex:"

private data class Banner(val message: String, val icon: ImageVector, val isPr: Boolean)

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
    haptics: Haptics,
    hasUnsavedEdits: Boolean = false,
    onUpdateExercises: (List<ExerciseEntry>) -> Unit,
    onReorderExercises: (Int, Int) -> Unit,
    onAddExercise: (name: String, muscle: String) -> Unit,
    onRemoveExercisePermanently: (String) -> Unit,
    onMinimize: () -> Unit,
    onDiscardWorkout: () -> Unit,
    onFinishWorkout: (durationSec: Long, photoPath: String?, skipped: List<String>) -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val latestExercises by rememberUpdatedState(exercises)
    val startTimeMillis = remember { LocalStorage.getActiveSessionStartTime(context) }

    var showPicker by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var detailsFor by remember { mutableStateOf<String?>(null) }
    var removeCandidate by remember { mutableStateOf<String?>(null) }
    var noteVersion by remember { mutableIntStateOf(0) }
    var photoPath by remember { mutableStateOf(initialPhotoPath) }
    var finalDuration by remember { mutableLongStateOf(initialDurationSeconds) }
    var banner by remember { mutableStateOf<Banner?>(null) }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(2800)
            banner = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let { PhotoStorage.saveBitmapToInternalStorage(context, it)?.let { path -> photoPath = path } }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { PhotoStorage.copyUriToInternalStorage(context, it)?.let { path -> photoPath = path } }
    }

    // First-run tips
    val tutorialBounds = remember { mutableStateMapOf<String, Rect>() }
    var showTips by remember(TutorialPrefs.replayGeneration) {
        mutableStateOf(!isEditingPastWorkout && !TutorialPrefs.hasSeenWorkoutTutorial(context))
    }
    var tipIndex by remember(TutorialPrefs.replayGeneration) { mutableIntStateOf(0) }
    val tips = remember {
        listOf(
            TutorialStepInfo("HANDLE", "Change the order", "Hold and drag the handle to move an exercise up or down."),
            TutorialStepInfo("SUGGESTION", "Weight suggestions", "Based on your last sessions. Tap \"Use\" to fill in the suggested weight."),
            TutorialStepInfo("SET_BADGE", "Set options", "Tap a set number to mark it as a warm-up or drop set, change its rest, or delete it.")
        )
    }
    fun finishTips() {
        TutorialPrefs.markWorkoutTutorialSeen(context)
        showTips = false
    }

    fun openFinish() {
        finalDuration = if (isEditingPastWorkout) initialDurationSeconds
        else ((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L)
        showFinish = true
    }

    BackHandler {
        when {
            showTips -> finishTips()
            isEditingPastWorkout && hasUnsavedEdits -> confirmDiscard = true
            isEditingPastWorkout -> onDiscardWorkout()
            else -> onMinimize()
        }
    }

    val active = exercises.withIndex().filter { !it.value.isSkipped }
    val skipped = exercises.withIndex().filter { it.value.isSkipped }
    val totalSets = active.sumOf { it.value.sets.size }
    val doneSets = active.sumOf { ex -> ex.value.sets.count { it.isCompleted } }
    val previousSplitWorkout = remember(split.title, history) {
        history.firstOrNull { it.splitTitle.equals(split.title, ignoreCase = true) }
    }

    fun supersetRange(pos: Int): IntRange {
        if (pos !in active.indices) return pos..pos
        var start = pos
        while (start > 0 && active[start - 1].value.isSuperset) start--
        var end = pos
        while (end < active.lastIndex && active[end].value.isSuperset) end++
        return start..end
    }

    fun updateAt(exIdx: Int, transform: (ExerciseEntry) -> ExerciseEntry) {
        val list = latestExercises.toMutableList()
        if (exIdx !in list.indices) return
        list[exIdx] = transform(list[exIdx])
        onUpdateExercises(list)
    }

    fun toggleSet(pos: Int, setIdx: Int) {
        val (exIdx, exercise) = active[pos]
        val setEntry = exercise.sets.getOrNull(setIdx) ?: return
        val nowDone = !setEntry.isCompleted
        val updatedSet = setEntry.copy(isCompleted = nowDone)
        val next = latestExercises.toMutableList()
        next[exIdx] = exercise.copy(sets = exercise.sets.toMutableList().also { it[setIdx] = updatedSet })
        onUpdateExercises(next)
        if (!nowDone) {
            haptics.tick()
            return
        }
        haptics.confirm()

        if (isSetPersonalRecord(updatedSet, computeExerciseRecords(exercise.name, history))) {
            banner = Banner("New best: ${exercise.name}, ${updatedSet.weight} kg × ${updatedSet.reps}", Icons.Rounded.EmojiEvents, isPr = true)
        }
        if (isEditingPastWorkout) return

        val range = supersetRange(pos)
        if (range.first < range.last) {
            // Superset: rest only once every exercise in the group has done this round.
            val waitingOn = range.firstNotNullOfOrNull { p ->
                val ex = next[active[p].index]
                val s = ex.sets.getOrNull(setIdx) ?: ex.sets.lastOrNull()
                if (s != null && !s.isCompleted) ex.name else null
            }
            if (waitingOn == null) {
                val rest = range.maxOf { p ->
                    val ex = next[active[p].index]
                    ex.sets.getOrNull(setIdx)?.restSeconds?.takeIf { it >= 0 } ?: ex.restSeconds.takeIf { it > 0 } ?: settings.restTimerSeconds
                }
                if (rest > 0) LocalStorage.setRestEndTimeMillis(context, System.currentTimeMillis() + rest * 1000L, fromWidget = true)
            } else {
                if (banner?.isPr != true) banner = Banner("Superset: go straight to $waitingOn", Icons.Rounded.Link, isPr = false)
            }
        } else {
            val rest = setEntry.restSeconds.takeIf { it >= 0 } ?: exercise.restSeconds
            if (rest > 0) LocalStorage.setRestEndTimeMillis(context, System.currentTimeMillis() + rest * 1000L, fromWidget = true)
        }
    }

    fun addFromLibrary(name: String, muscle: String) {
        if (latestExercises.any { it.name.equals(name, ignoreCase = true) }) return
        haptics.tick()
        onAddExercise(name, muscle)
        onUpdateExercises(latestExercises + newExerciseEntry(context, split.title, name, muscle, history, settings))
    }

    val listState = rememberLazyListState()
    val dragState = rememberDragDropState(
        listState = listState,
        canReorder = { key -> key is String && key.startsWith(EXERCISE_KEY_PREFIX) },
        onMove = { fromKey, toKey ->
            val from = latestExercises.indexOfFirst { EXERCISE_KEY_PREFIX + it.name == fromKey }
            val to = latestExercises.indexOfFirst { EXERCISE_KEY_PREFIX + it.name == toKey }
            if (from >= 0 && to >= 0) onReorderExercises(from, to)
        },
        onSwap = { haptics.tick() }
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            WorkoutTopBar(
                title = split.title,
                isEditing = isEditingPastWorkout,
                startTimeMillis = startTimeMillis,
                doneSets = doneSets,
                totalSets = totalSets,
                canCopyLast = !isEditingPastWorkout && previousSplitWorkout != null,
                onClose = {
                    if (isEditingPastWorkout && hasUnsavedEdits) confirmDiscard = true
                    else if (isEditingPastWorkout) onDiscardWorkout()
                    else onMinimize()
                },
                onFinish = { openFinish() },
                onCopyLast = {
                    val prev = previousSplitWorkout ?: return@WorkoutTopBar
                    haptics.tick()
                    onUpdateExercises(latestExercises.map { cur -> copyFromPrevious(cur, prev, settings) })
                },
                onDiscard = { confirmDiscard = true }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isEditingPastWorkout && previousSplitWorkout != null && doneSets == 0) {
                    item(key = "copy_last") {
                        AppCard(
                            Modifier.fillMaxWidth().animateItem(),
                            onClick = {
                                haptics.tick()
                                onUpdateExercises(latestExercises.map { copyFromPrevious(it, previousSplitWorkout, settings) })
                            },
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).clip(CircleShape).background(P.accentSecondarySoft), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.ContentCopy, null, tint = P.accentSecondary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Use last time's numbers", style = AppType.bodyStrong, color = P.textPrimary)
                                    Text(
                                        "Fill in weights and reps from ${previousSplitWorkout.displayDate.substringBefore(",")}",
                                        style = AppType.caption,
                                        color = P.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(active, key = { _, iv -> EXERCISE_KEY_PREFIX + iv.value.name }) { pos, (exIdx, exercise) ->
                    val key = EXERCISE_KEY_PREFIX + exercise.name
                    val range = supersetRange(pos)
                    val partners = if (range.first < range.last) range.filter { it != pos }.map { active[it].value.name } else emptyList()
                    val records = remember(exercise.name, history) { computeExerciseRecords(exercise.name, history) }
                    val suggestion = remember(exercise, history, settings.weightStepKg) {
                        suggestNextWeight(split.title, exercise, history, settings.weightStepKg)
                    }
                    val lastTime = remember(exercise.name, previousSplitWorkout) {
                        previousSplitWorkout?.exercises
                            ?.find { it.name.equals(exercise.name, ignoreCase = true) && !it.isSkipped }
                            ?.sets?.filter { it.isValidWorkSet }
                            ?.joinToString("  ·  ") { "${it.weight}×${it.reps}" }
                            ?.takeIf { it.isNotEmpty() }
                    }
                    val note = remember(exercise.name, noteVersion) { LocalStorage.getExerciseNote(context, exercise.name) }

                    DraggableItem(dragState, key) { isDragging ->
                        ExerciseCard(
                            exercise = exercise,
                            partners = partners,
                            canLinkNext = pos < active.lastIndex || exercise.isSuperset,
                            records = records,
                            suggestion = suggestion,
                            lastTime = lastTime,
                            note = note,
                            isDragging = isDragging,
                            splitTitle = split.title,
                            settings = settings,
                            handleModifier = Modifier
                                .dragHandle(dragState, key, onStart = { haptics.confirm() })
                                .then(if (pos == 0) Modifier.onGloballyPositioned { tutorialBounds["HANDLE"] = it.boundsInRoot() } else Modifier),
                            suggestionModifier = if (pos == 0) Modifier.onGloballyPositioned { tutorialBounds["SUGGESTION"] = it.boundsInRoot() } else Modifier,
                            firstBadgeModifier = if (pos == 0) Modifier.onGloballyPositioned { tutorialBounds["SET_BADGE"] = it.boundsInRoot() } else Modifier,
                            haptics = haptics,
                            onUpdate = { transform -> updateAt(exIdx, transform) },
                            onToggleSet = { setIdx -> toggleSet(pos, setIdx) },
                            onSetRest = { setIdx, sec ->
                                haptics.tick()
                                if (range.first < range.last) {
                                    val list = latestExercises.toMutableList()
                                    range.forEach { p ->
                                        val i = active[p].index
                                        list[i] = list[i].copy(sets = list[i].sets.mapIndexed { si, s -> if (si == setIdx) s.copy(restSeconds = sec) else s })
                                    }
                                    onUpdateExercises(list)
                                } else {
                                    updateAt(exIdx) { ex -> ex.copy(sets = ex.sets.mapIndexed { si, s -> if (si == setIdx) s.copy(restSeconds = sec) else s }) }
                                }
                            },
                            onSetExerciseRest = { sec ->
                                haptics.tick()
                                val list = latestExercises.toMutableList()
                                range.forEach { p ->
                                    val i = active[p].index
                                    list[i] = list[i].copy(restSeconds = sec, sets = list[i].sets.map { s -> if (s.intensityType == "DROP") s else s.copy(restSeconds = sec) })
                                    LocalStorage.saveExerciseDefaultRest(context, list[i].name, sec)
                                }
                                onUpdateExercises(list)
                            },
                            onToggleSuperset = {
                                haptics.tick()
                                val list = latestExercises.toMutableList()
                                val linking = !exercise.isSuperset
                                list[exIdx] = exercise.copy(isSuperset = linking)
                                if (linking && pos + 1 in active.indices) {
                                    // Partners share one rest timer.
                                    val pi = active[pos + 1].index
                                    list[pi] = list[pi].copy(restSeconds = exercise.restSeconds, sets = list[pi].sets.map { it.copy(restSeconds = exercise.restSeconds) })
                                }
                                onUpdateExercises(list)
                            },
                            onSkip = {
                                haptics.tick()
                                updateAt(exIdx) { it.copy(isSkipped = true, isSuperset = false) }
                            },
                            onOpenDetails = { detailsFor = exercise.name },
                            onRemove = { removeCandidate = exercise.name }
                        )
                    }
                }

                if (skipped.isNotEmpty()) {
                    item(key = "skipped") {
                        AppCard(Modifier.fillMaxWidth().animateItem()) {
                            Text("Skipped today", style = AppType.headline, color = P.textPrimary)
                            skipped.forEach { (idx, ex) ->
                                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(ex.name, style = AppType.body, color = P.textSecondary, modifier = Modifier.weight(1f))
                                    SecondaryButton("Bring back", {
                                        haptics.tick()
                                        updateAt(idx) { it.copy(isSkipped = false) }
                                    }, icon = Icons.AutoMirrored.Rounded.Undo, height = 36.dp)
                                }
                            }
                        }
                    }
                }

                item(key = "add") {
                    SecondaryButton(
                        "Add exercise",
                        { haptics.tick(); showPicker = true },
                        Modifier.fillMaxWidth().animateItem(),
                        icon = Icons.Rounded.Add,
                        contentColor = P.accentPrimary,
                        height = 52.dp
                    )
                }
            }
        }

        // Transient banner (new best / superset hint)
        AnimatedVisibility(
            visible = banner != null,
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) { -it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
        ) {
            val b = banner
            if (b != null) {
                AppCard(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    elevated = true,
                    color = if (b.isPr) P.warningSurface else P.bgElevated,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(b.icon, null, tint = if (b.isPr) P.warningText else P.accentSecondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(b.message, style = AppType.bodyStrong, color = P.textPrimary)
                    }
                }
            }
        }

        if (!isEditingPastWorkout) {
            RestTimerBar(
                exercises = exercises,
                haptics = haptics,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showTips) {
            SpotlightTutorialOverlay(
                steps = tips,
                currentStepIndex = tipIndex,
                targetBounds = tutorialBounds,
                onNext = { if (tipIndex < tips.lastIndex) tipIndex++ else finishTips() },
                onPrev = { if (tipIndex > 0) tipIndex-- },
                onSkip = { finishTips() }
            )
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            existingNames = exercises.map { it.name }.toSet(),
            onPick = { info -> addFromLibrary(info.name, info.muscle) },
            onCreateCustom = { name -> addFromLibrary(name, "") },
            onDismiss = { showPicker = false }
        )
    }

    detailsFor?.let { name ->
        val idx = exercises.indexOfFirst { it.name == name }
        if (idx == -1) detailsFor = null
        else ExerciseDetailsSheet(
            exercise = exercises[idx],
            history = history,
            canMoveUp = idx > 0,
            canMoveDown = idx < exercises.lastIndex,
            onMoveUp = { haptics.tick(); onReorderExercises(idx, idx - 1) },
            onMoveDown = { haptics.tick(); onReorderExercises(idx, idx + 1) },
            onSaveNote = { text ->
                LocalStorage.saveExerciseNote(context, name, text)
                noteVersion++
            },
            onRemove = { detailsFor = null; removeCandidate = name },
            splitTitle = split.title,
            onDismiss = { detailsFor = null }
        )
    }

    removeCandidate?.let { name ->
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            containerColor = P.bgElevated,
            title = { Text("Remove $name?", style = AppType.headline, color = P.textPrimary) },
            text = {
                Text(
                    if (isEditingPastWorkout) "It will be removed from this logged workout."
                    else "It won't show up in future ${split.title} workouts. You can add it back any time.",
                    style = AppType.body,
                    color = P.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.tick()
                    onRemoveExercisePermanently(name)
                    removeCandidate = null
                }) { Text("Remove", color = P.dangerText, style = AppType.label) }
            },
            dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("Cancel", color = P.textSecondary, style = AppType.label) } }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = P.bgElevated,
            title = { Text(if (isEditingPastWorkout) "Discard changes?" else "Discard this workout?", style = AppType.headline, color = P.textPrimary) },
            text = {
                Text(
                    if (isEditingPastWorkout) "Your edits to this workout won't be saved." else "Nothing from today's session will be saved.",
                    style = AppType.body,
                    color = P.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDiscardWorkout() }) {
                    Text("Discard", color = P.dangerText, style = AppType.label)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep going", color = P.textSecondary, style = AppType.label) } }
        )
    }

    if (showFinish) {
        FinishSheet(
            split = split,
            exercises = exercises,
            skipped = skipped.map { it.value },
            history = history,
            userProfile = userProfile,
            isEditing = isEditingPastWorkout,
            durationSeconds = finalDuration,
            photoPath = photoPath,
            onUnskipAll = {
                onUpdateExercises(latestExercises.map { it.copy(isSkipped = false) })
                showFinish = false
            },
            onClearPhoto = { photoPath = null },
            onCamera = { cameraLauncher.launch(null) },
            onGallery = { galleryLauncher.launch("image/*") },
            onSave = {
                showFinish = false
                onFinishWorkout(finalDuration, photoPath, skipped.map { it.value.name })
            },
            onDismiss = { showFinish = false }
        )
    }
}

private fun copyFromPrevious(current: ExerciseEntry, previous: CompletedWorkout, settings: AppSettings): ExerciseEntry {
    val prev = previous.exercises.find { it.name.equals(current.name, ignoreCase = true) } ?: return current
    return current.copy(
        sets = prev.sets.mapIndexed { idx, s ->
            s.copy(
                setNumber = idx + 1,
                isCompleted = false,
                restSeconds = s.restSeconds.takeIf { it >= 0 } ?: current.restSeconds.takeIf { it > 0 } ?: settings.restTimerSeconds
            )
        }
    )
}

// ============================================================================
// TOP BAR
// ============================================================================

@Composable
private fun WorkoutTopBar(
    title: String,
    isEditing: Boolean,
    startTimeMillis: Long,
    doneSets: Int,
    totalSets: Int,
    canCopyLast: Boolean,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    onCopyLast: () -> Unit,
    onDiscard: () -> Unit
) {
    val P = LocalPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(P.bgPrimary).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                if (isEditing) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.KeyboardArrowDown,
                if (isEditing) "Back" else "Minimize workout",
                onClose,
                container = Color.Transparent
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = AppType.title, color = P.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isEditing) Text("Editing a logged workout", style = AppType.caption, color = P.textSecondary)
                else ElapsedClock(startTimeMillis)
            }
            PrimaryButton(if (isEditing) "Save" else "Finish", onFinish, height = 40.dp)
            Box {
                CircleIconButton(Icons.Rounded.MoreVert, "More options", { menuOpen = true }, container = Color.Transparent, tint = P.textSecondary)
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = P.bgElevated) {
                    if (canCopyLast) {
                        DropdownMenuItem(
                            text = { Text("Use last time's numbers", style = AppType.body) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                            onClick = { menuOpen = false; onCopyLast() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (isEditing) "Discard changes" else "Discard workout", style = AppType.body, color = P.dangerText) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = P.dangerText) },
                        onClick = { menuOpen = false; onDiscard() }
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedProgressBar(
                progress = if (totalSets == 0) 0f else doneSets / totalSets.toFloat(),
                modifier = Modifier.weight(1f),
                height = 5.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("$doneSets of $totalSets sets", style = AppType.caption.copy(fontFeatureSettings = "tnum"), color = P.textSecondary)
        }
    }
}

@Composable
private fun ElapsedClock(startTimeMillis: Long) {
    val P = LocalPalette.current
    var elapsed by remember { mutableLongStateOf(((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L)) }
    LaunchedEffect(startTimeMillis) {
        while (true) {
            delay(1000L)
            elapsed = ((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(0L)
        }
    }
    Text(formatClock(elapsed), style = AppType.caption.copy(fontFeatureSettings = "tnum"), color = P.textSecondary)
}

// ============================================================================
// EXERCISE CARD
// ============================================================================

@Composable
private fun ExerciseCard(
    exercise: ExerciseEntry,
    partners: List<String>,
    canLinkNext: Boolean,
    records: ExerciseRecords,
    suggestion: Suggestion,
    lastTime: String?,
    note: String,
    isDragging: Boolean,
    splitTitle: String,
    settings: AppSettings,
    handleModifier: Modifier,
    suggestionModifier: Modifier,
    firstBadgeModifier: Modifier,
    haptics: Haptics,
    onUpdate: ((ExerciseEntry) -> ExerciseEntry) -> Unit,
    onToggleSet: (Int) -> Unit,
    onSetRest: (setIdx: Int, sec: Int) -> Unit,
    onSetExerciseRest: (Int) -> Unit,
    onToggleSuperset: () -> Unit,
    onSkip: () -> Unit,
    onOpenDetails: () -> Unit,
    onRemove: () -> Unit
) {
    val P = LocalPalette.current
    val inSuperset = partners.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        when {
            isDragging -> P.accentPrimary
            inSuperset -> P.accentSecondary.copy(alpha = 0.6f)
            else -> Color.Transparent
        }, label = "card_border"
    )

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, CardShape)
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)),
        elevated = isDragging,
        contentPadding = PaddingValues(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)
    ) {
        if (inSuperset) {
            Row(Modifier.padding(start = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, null, tint = P.accentSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Superset with ${partners.joinToString(", ")}", style = AppType.caption, color = P.accentSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Header: handle · name · menu
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                handleModifier.size(36.dp, 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DragIndicator, "Drag to reorder", tint = if (isDragging) P.accentPrimary else P.textTertiary)
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .pressable(pressedScale = 0.99f, onClick = onOpenDetails)
                    .padding(vertical = 2.dp, horizontal = 2.dp)
            ) {
                Text(exercise.name, style = AppType.headline, color = P.textPrimary, maxLines = 2)
                val sub = buildList {
                    if (exercise.targetMuscle.isNotBlank()) add(exercise.targetMuscle)
                    if (records.bestSetText != "—") add("Best ${records.bestSetText.replace("kg ×", " kg ×")}")
                }.joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = AppType.caption, color = P.textSecondary)
            }
            Pill(formatRestShort(exercise.restSeconds), icon = Icons.Rounded.Timer, modifier = Modifier.clip(RoundedCornerShape(50)).pressable {
                onSetExerciseRest(cycleRestPreset(exercise.restSeconds))
            })
            Box {
                CircleIconButton(Icons.Rounded.MoreVert, "Exercise options", { menuOpen = true }, size = 36.dp, container = Color.Transparent, tint = P.textSecondary)
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = P.bgElevated) {
                    DropdownMenuItem(
                        text = { Text("Notes & history", style = AppType.body) },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        onClick = { menuOpen = false; onOpenDetails() }
                    )
                    if (canLinkNext) {
                        DropdownMenuItem(
                            text = { Text(if (exercise.isSuperset) "Unlink from next" else "Superset with next", style = AppType.body) },
                            leadingIcon = { Icon(if (exercise.isSuperset) Icons.Rounded.LinkOff else Icons.Rounded.Link, null) },
                            onClick = { menuOpen = false; onToggleSuperset() }
                        )
                    }
                    RestChooser("Rest between sets", exercise.restSeconds) { menuOpen = false; onSetExerciseRest(it) }
                    HorizontalDivider(color = P.borderSubtle)
                    DropdownMenuItem(
                        text = { Text("Skip for today", style = AppType.body) },
                        leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                        onClick = { menuOpen = false; onSkip() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from $splitTitle", style = AppType.body, color = P.dangerText) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = P.dangerText) },
                        onClick = { menuOpen = false; onRemove() }
                    )
                }
            }
        }

        Column(Modifier.padding(start = 10.dp)) {
            if (lastTime != null) {
                Text("Last time  $lastTime", style = AppType.caption.copy(fontFeatureSettings = "tnum"), color = P.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
            if (note.isNotBlank()) {
                Row(
                    Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(P.bgControl).pressable(onClick = onOpenDetails).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PushPin, null, tint = P.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(note, style = AppType.caption, color = P.textPrimary)
                }
            }

            SuggestionBox(
                suggestion = suggestion,
                modifier = suggestionModifier.padding(top = 10.dp),
                onUse = {
                    haptics.tick()
                    onUpdate { ex -> ex.copy(sets = ex.sets.map { s -> if (s.isCompleted || s.isWarmupSet) s else s.copy(weight = suggestion.suggestedWeight) }) }
                },
                onLighter = {
                    haptics.tick()
                    onUpdate { ex ->
                        ex.copy(sets = ex.sets.map { s ->
                            if (s.isCompleted || s.isWarmupSet) s
                            else s.copy(weight = formatKgClean((parseWeightClean(s.weight, 20f) * 0.9f).coerceAtLeast(2.5f)))
                        })
                    }
                }
            )

            Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Set", style = AppType.caption, color = P.textTertiary, modifier = Modifier.width(40.dp))
                Text("kg", style = AppType.caption, color = P.textTertiary, modifier = Modifier.weight(1.25f).padding(start = 12.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reps", style = AppType.caption, color = P.textTertiary, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Spacer(Modifier.width(52.dp))
            }

            var workNumber = 0
            exercise.sets.forEachIndexed { setIdx, set ->
                if (set.intensityType == "NORMAL" && !set.isWarmup) workNumber++
                SetRow(
                    set = set,
                    workNumber = workNumber,
                    isPr = isSetPersonalRecord(set, records),
                    canDelete = exercise.sets.size > 1,
                    badgeModifier = if (setIdx == 0) firstBadgeModifier else Modifier,
                    stepKg = settings.weightStepKg,
                    onChange = { transform ->
                        onUpdate { ex -> ex.copy(sets = ex.sets.toMutableList().also { it[setIdx] = transform(it[setIdx]) }) }
                    },
                    onSetType = { type ->
                        haptics.tick()
                        onUpdate { ex -> ex.copy(sets = ex.sets.toMutableList().also { it[setIdx] = it[setIdx].copy(intensityType = type, isWarmup = type == "WARMUP") }) }
                    },
                    onSetRest = { sec -> onSetRest(setIdx, sec) },
                    onDelete = {
                        haptics.tick()
                        onUpdate { ex -> ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != setIdx }.mapIndexed { i, s -> s.copy(setNumber = i + 1) }) }
                    },
                    onToggle = { onToggleSet(setIdx) },
                    haptics = haptics
                )
            }

            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Add set", {
                    haptics.tick()
                    onUpdate { ex ->
                        val last = ex.sets.lastOrNull()
                        ex.copy(sets = ex.sets + SetEntry(
                            setNumber = ex.sets.size + 1,
                            weight = last?.weight ?: "20",
                            reps = last?.reps ?: "8",
                            restSeconds = last?.restSeconds?.takeIf { it >= 0 && last.intensityType != "DROP" } ?: ex.restSeconds
                        ))
                    }
                }, Modifier.weight(1f), icon = Icons.Rounded.Add, height = 40.dp)
                SecondaryButton("Drop set", {
                    haptics.tick()
                    onUpdate { ex ->
                        val last = ex.sets.lastOrNull()
                        val dropWeight = formatKgClean((parseWeightClean(last?.weight ?: "20", 20f) * 0.8f).coerceAtLeast(2.5f))
                        ex.copy(sets = ex.sets + SetEntry(
                            setNumber = ex.sets.size + 1,
                            weight = dropWeight,
                            reps = last?.reps ?: "8",
                            intensityType = "DROP",
                            restSeconds = 0
                        ))
                    }
                }, Modifier.weight(1f), icon = Icons.Rounded.LocalFireDepartment, height = 40.dp)
            }
        }
    }
}

@Composable
private fun RestChooser(label: String, current: Int, onPick: (Int) -> Unit) {
    val P = LocalPalette.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = AppType.caption, color = P.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            REST_PRESETS.forEach { sec ->
                val sel = sec == current
                Text(
                    formatRestShort(sec),
                    style = AppType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (sel) P.onAccentPrimary else P.textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) P.accentPrimary else P.bgControl)
                        .pressable(pressedScale = 0.9f) { onPick(sec) }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SuggestionBox(suggestion: Suggestion, modifier: Modifier, onUse: () -> Unit, onLighter: () -> Unit) {
    val P = LocalPalette.current
    val (bg, fg) = toneColors(suggestion.tone, P)
    val icon = when (suggestion.tone) {
        SuggestionTone.GOOD -> Icons.AutoMirrored.Rounded.TrendingUp
        SuggestionTone.CAUTION -> Icons.AutoMirrored.Rounded.TrendingDown
        SuggestionTone.NEUTRAL -> Icons.Rounded.Lightbulb
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
            Text(suggestion.message, style = AppType.label.copy(fontWeight = FontWeight.Normal), color = P.textPrimary)
        }
        Row(Modifier.padding(top = 10.dp, start = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (suggestion.suggestedWeight.isNotBlank()) {
                Text(
                    "Use ${suggestion.suggestedWeight} kg",
                    style = AppType.label,
                    color = fg,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(P.bgCard.copy(alpha = if (P.isDark) 0.35f else 0.8f)).pressable(onClick = onUse).padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Text(
                "Go lighter",
                style = AppType.label,
                color = P.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(50)).pressable(onClick = onLighter).padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SetRow(
    set: SetEntry,
    workNumber: Int,
    isPr: Boolean,
    canDelete: Boolean,
    badgeModifier: Modifier,
    stepKg: Float,
    onChange: ((SetEntry) -> SetEntry) -> Unit,
    onSetType: (String) -> Unit,
    onSetRest: (Int) -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    haptics: Haptics
) {
    val P = LocalPalette.current
    val rowBg by animateColorAsState(if (set.isCompleted) P.successSurface else Color.Transparent, tween(250), label = "set_row")
    var menuOpen by remember { mutableStateOf(false) }
    val (badgeText, badgeFg, badgeBg) = when {
        set.isWarmupSet -> Triple("W", P.warningText, P.warningSurface)
        set.intensityType == "DROP" -> Triple("D", P.dangerText, P.dangerSurface)
        set.intensityType == "REST_PAUSE" -> Triple("RP", P.accentSecondary, P.accentSecondarySoft)
        else -> Triple("$workNumber", P.textPrimary, P.bgControl)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(badgeModifier.width(40.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg)
                    .pressable(pressedScale = 0.88f) { haptics.tick(); menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                if (isPr && set.intensityType == "NORMAL") Icon(Icons.Rounded.EmojiEvents, "Personal best", tint = P.warningText, modifier = Modifier.size(16.dp))
                else Text(badgeText, style = AppType.label.copy(fontWeight = FontWeight.SemiBold), color = badgeFg)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = P.bgElevated) {
                listOf("NORMAL" to "Working set", "WARMUP" to "Warm-up", "DROP" to "Drop set", "REST_PAUSE" to "Rest-pause").forEach { (type, label) ->
                    val selected = if (type == "WARMUP") set.isWarmupSet else set.intensityType == type && !set.isWarmupSet
                    DropdownMenuItem(
                        text = { Text(label, style = AppType.body) },
                        trailingIcon = { if (selected) Icon(Icons.Rounded.Check, null, tint = P.accentPrimary) },
                        onClick = { menuOpen = false; onSetType(type) }
                    )
                }
                HorizontalDivider(color = P.borderSubtle)
                RestChooser("Rest after this set", set.restSeconds) { menuOpen = false; onSetRest(it) }
                if (canDelete) {
                    HorizontalDivider(color = P.borderSubtle)
                    DropdownMenuItem(
                        text = { Text("Delete set", style = AppType.body, color = P.dangerText) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = P.dangerText) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }

        NumberStepper(
            value = set.weight,
            onValueChange = { v -> sanitizeDecimalInput(v)?.let { w -> onChange { it.copy(weight = w) } } },
            onMinus = {
                haptics.tick()
                val next = (parseWeightClean(set.weight, 20f) - stepKg).coerceAtLeast(0f)
                onChange { it.copy(weight = formatKgClean(next)) }
            },
            onPlus = {
                haptics.tick()
                val next = parseWeightClean(set.weight, 20f) + stepKg
                onChange { it.copy(weight = formatKgClean(next)) }
            },
            modifier = Modifier.weight(1.25f)
        )
        Spacer(Modifier.width(8.dp))
        NumberStepper(
            value = set.reps,
            onValueChange = { v -> onChange { it.copy(reps = v.filter(Char::isDigit).take(3)) } },
            onMinus = {
                haptics.tick()
                onChange { it.copy(reps = "${maxOf(0, parseRepsClean(set.reps, 8) - 1)}") }
            },
            onPlus = {
                haptics.tick()
                onChange { it.copy(reps = "${parseRepsClean(set.reps, 8) + 1}") }
            },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        CheckButton(done = set.isCompleted, onClick = onToggle)
    }
}

@Composable
private fun CheckButton(done: Boolean, onClick: () -> Unit) {
    val P = LocalPalette.current
    val bg by animateColorAsState(if (done) P.accentPrimary else P.bgControl, tween(200), label = "check_bg")
    val fg by animateColorAsState(if (done) P.onAccentPrimary else P.textTertiary, tween(200), label = "check_fg")
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !previous) {
            pop.animateTo(1.18f, spring(stiffness = Spring.StiffnessHigh))
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
        previous = done
    }
    Box(
        Modifier
            .size(44.dp, 40.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .pressable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Check, if (done) "Mark set not done" else "Mark set done", tint = fg, modifier = Modifier.size(22.dp))
    }
}

// ============================================================================
// REST TIMER
// ============================================================================

/** Floating rest countdown. Reads the shared rest end time, so widget and lock-screen changes show here too. */
@Composable
private fun RestTimerBar(exercises: List<ExerciseEntry>, haptics: Haptics, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    val context = LocalContext.current
    var endMillis by remember { mutableLongStateOf(0L) }
    var totalMillis by remember { mutableLongStateOf(1L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            val end = LocalStorage.getRestEndTimeMillis(context)
            val t = System.currentTimeMillis()
            if (end != endMillis) {
                // Extending a running rest grows the total, so the ring doesn't jump back to full.
                totalMillis = if (endMillis > t && end > t) totalMillis + (end - endMillis) else (end - t).coerceAtLeast(1L)
                endMillis = end
            }
            now = t
            delay(100)
        }
    }

    val remaining = (endMillis - now).coerceAtLeast(0L)
    val visible = remaining > 0L
    val next = remember(exercises) {
        exercises.firstOrNull { ex -> !ex.isSkipped && ex.sets.any { !it.isCompleted } }?.let { ex ->
            val s = ex.sets.first { !it.isCompleted }
            "${ex.name} · ${s.weight} kg × ${s.reps}"
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        AppCard(
            Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth(),
            elevated = true,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = remaining / totalMillis.toFloat(),
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 5.dp,
                    color = P.accentSecondary
                ) {
                    Text(formatClock((remaining + 999) / 1000), style = AppType.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = P.textPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Resting", style = AppType.bodyStrong, color = P.textPrimary)
                    if (next != null) Text("Next: $next", style = AppType.caption, color = P.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "−15",
                    style = AppType.label,
                    color = P.textPrimary,
                    modifier = Modifier.clip(CircleShape).background(P.bgControl).pressable {
                        haptics.tick()
                        val newEnd = endMillis - 15_000L
                        LocalStorage.setRestEndTimeMillis(context, if (newEnd - System.currentTimeMillis() < 1000) 0L else newEnd, fromWidget = true)
                    }.padding(horizontal = 10.dp, vertical = 8.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "+15",
                    style = AppType.label,
                    color = P.textPrimary,
                    modifier = Modifier.clip(CircleShape).background(P.bgControl).pressable {
                        haptics.tick()
                        LocalStorage.setRestEndTimeMillis(context, endMillis + 15_000L, fromWidget = true)
                    }.padding(horizontal = 10.dp, vertical = 8.dp)
                )
                Spacer(Modifier.width(6.dp))
                CircleIconButton(Icons.Rounded.SkipNext, "Skip rest", {
                    haptics.tick()
                    LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)
                }, size = 36.dp, container = P.accentSecondarySoft, tint = P.accentSecondary)
            }
        }
    }
}

// ============================================================================
// SHEETS
// ============================================================================

@Composable
private fun ExerciseDetailsSheet(
    exercise: ExerciseEntry,
    history: List<CompletedWorkout>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    splitTitle: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSaveNote: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val rec = remember(exercise.name, history) { computeExerciseRecords(exercise.name, history) }
    var note by remember(exercise.name) { mutableStateOf(LocalStorage.getExerciseNote(context, exercise.name)) }
    val info = remember(exercise.name) { ExerciseDatabase.find(exercise.name) }

    AppBottomSheet(onDismiss = { onSaveNote(note); onDismiss() }) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = AppType.title, color = P.textPrimary)
                    val sub = listOfNotNull(exercise.targetMuscle.ifBlank { info?.muscle }, info?.equipment).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = AppType.label.copy(fontWeight = FontWeight.Normal), color = P.textSecondary)
                }
                CircleIconButton(Icons.Rounded.ArrowUpward, "Move up", onMoveUp, enabled = canMoveUp, size = 36.dp)
                Spacer(Modifier.width(6.dp))
                CircleIconButton(Icons.Rounded.ArrowDownward, "Move down", onMoveDown, enabled = canMoveDown, size = 36.dp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(if (rec.bestEstimated1Rm > 0) "${rec.bestEstimated1Rm} kg" else "—", "Est. 1-rep max", Modifier.weight(1f))
                StatTile(if (rec.maxWeightKg > 0f) "${formatKgClean(rec.maxWeightKg)} kg" else "—", "Heaviest", Modifier.weight(1f))
                StatTile(rec.bestSetText.replace("kg ×", " ×"), "Best set", Modifier.weight(1f))
            }
            if (rec.chartPoints.size >= 2) {
                Spacer(Modifier.height(16.dp))
                Text("Top weight per session", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                TrendChart(rec.chartPoints)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Setup note (seat height, grip…)") },
                leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = P.accentPrimary,
                    unfocusedBorderColor = P.borderStrong,
                    focusedLabelColor = P.accentPrimary,
                    unfocusedLabelColor = P.textSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text("Recent sessions", style = AppType.label, color = P.textSecondary)
            if (rec.recentHistory.isEmpty()) {
                Text("Nothing logged yet.", style = AppType.body, color = P.textTertiary, modifier = Modifier.padding(top = 6.dp))
            } else {
                rec.recentHistory.forEach { (label, sets) ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(label, style = AppType.caption, color = P.textTertiary)
                        Text(sets.replace("kg ×", " ×").replace(" • ", "  ·  "), style = AppType.body.copy(fontFeatureSettings = "tnum"), color = P.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SecondaryButton("Remove from $splitTitle", onRemove, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteOutline, contentColor = P.dangerText)
        }
    }
}

@Composable
private fun FinishSheet(
    split: WorkoutSplit,
    exercises: List<ExerciseEntry>,
    skipped: List<ExerciseEntry>,
    history: List<CompletedWorkout>,
    userProfile: UserProfile,
    isEditing: Boolean,
    durationSeconds: Long,
    photoPath: String?,
    onUnskipAll: () -> Unit,
    onClearPhoto: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val stats = remember(exercises, history) { computeWorkoutStats(exercises, history) }

    AppBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(if (isEditing) "Save changes" else "Nice work", style = AppType.display, color = P.textPrimary)
            Text(
                if (isEditing) split.title else "That's ${split.title} done.",
                style = AppType.body,
                color = P.textSecondary
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(formatDuration(durationSeconds), "Time", Modifier.weight(1f))
                StatTile("${stats.workSets}", "Sets", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(formatVolume(stats.volumeKg), "Volume", Modifier.weight(1f))
                StatTile("${stats.prCount}", "New bests", Modifier.weight(1f), valueColor = if (stats.prCount > 0) P.warningText else null)
            }

            if (skipped.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.warningSurface).padding(14.dp)) {
                    Text("You skipped ${skipped.size}: ${skipped.joinToString(", ") { it.name }}", style = AppType.label.copy(fontWeight = FontWeight.Normal), color = P.textPrimary)
                    Text(
                        "Do them now",
                        style = AppType.label,
                        color = P.warningText,
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).pressable(onClick = onUnskipAll).padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            val preview = remember(photoPath) { PhotoStorage.loadImageBitmap(photoPath) }
            if (preview != null) {
                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))) {
                    Image(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    CircleIconButton(
                        Icons.Rounded.Close, "Remove photo", onClearPhoto,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        size = 32.dp, container = Color.Black.copy(alpha = 0.55f), tint = Color.White
                    )
                }
            } else {
                Text("Add a photo (optional)", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Camera", onCamera, Modifier.weight(1f), icon = Icons.Rounded.PhotoCamera)
                    SecondaryButton("Gallery", onGallery, Modifier.weight(1f), icon = Icons.Rounded.Image)
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(if (isEditing) "Save changes" else "Save workout", onSave, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                "Share",
                {
                    shareWorkoutCardImage(context, split.title, durationSeconds, exercises, history, userProfile.currentWeightKg, photoPath)
                },
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.IosShare
            )
        }
    }
}
