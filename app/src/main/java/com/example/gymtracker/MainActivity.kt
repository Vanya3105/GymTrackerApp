package com.example.gymtracker

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.animation.PathInterpolator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // The app is ready almost at once, which would cut the logo animation off after a few
        // frames. Hold it until the arc has drawn (Android 12+; older versions show a still icon).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val shownAt = SystemClock.uptimeMillis()
            splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - shownAt < SPLASH_HOLD_MS }
        }
        // The logo grows and fades as the app appears behind it.
        splash.setOnExitAnimationListener { provider ->
            val easing = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            // Launches from a notification, widget or the watch get a splash without the logo, and
            // iconView then throws instead of returning null.
            runCatching { provider.iconView }.getOrNull()
                ?.animate()?.scaleX(1.35f)?.scaleY(1.35f)?.setDuration(SPLASH_EXIT_MS)?.setInterpolator(easing)?.start()
            provider.view.animate().alpha(0f).setDuration(SPLASH_EXIT_MS).setInterpolator(easing)
                .withEndAction { provider.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GymTrackerRoot(activity = this) }
    }
}

private const val SPLASH_HOLD_MS = 550L
private const val SPLASH_EXIT_MS = 320L

@Composable
fun GymTrackerRoot(activity: Activity) {
    val context = LocalContext.current
    val view = LocalView.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        ensureNotificationChannelCreated(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var settings by remember { mutableStateOf(LocalStorage.loadSettings(context)) }
    val palette = paletteFor(settings.themeChoice, isSystemInDarkTheme())

    LaunchedEffect(settings.targetFps) { applyDisplayRefreshRate(activity, settings.targetFps) }
    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    val haptics = remember(settings.hapticsEnabled, view) {
        Haptics(enabled = settings.hapticsEnabled, view = view)
    }

    GymTheme(palette) {
        Box(Modifier.fillMaxSize().background(palette.bgPrimary)) {
            GymTrackerApp(
                activity = activity,
                settings = settings,
                haptics = haptics,
                onUpdateSettings = { newSettings ->
                    settings = newSettings
                    LocalStorage.saveSettings(context, newSettings)
                    haptics.tick()
                }
            )
        }
    }
}

/** Small wrapper so screens can ask for "tick" vs "confirm" feedback without checking the setting. */
class Haptics(private val enabled: Boolean, private val view: android.view.View) {
    fun tick() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        if (!enabled) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        view.performHapticFeedback(type)
    }
}

enum class AppTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Home),
    HISTORY("History", Icons.Rounded.CalendarMonth),
    PROGRESS("Progress", Icons.Rounded.Insights),
    PROFILE("Profile", Icons.Rounded.Person)
}

private fun <T> List<T>.moved(fromIdx: Int, toIdx: Int): List<T> =
    toMutableList().apply { add(toIdx, removeAt(fromIdx)) }

/** Builds the logbook entry; PRs are judged only against workouts logged before this one. */
private fun buildCompletedWorkout(base: CompletedWorkout, exercises: List<ExerciseEntry>, history: List<CompletedWorkout>): CompletedWorkout {
    val performed = exercises.filter { !it.isSkipped }
    val stats = computeWorkoutStats(performed, history.filter { it.id < base.id })
    return base.copy(exercises = performed, totalVolumeKg = stats.volumeKg, prsHit = stats.prCount)
}

@Composable
fun GymTrackerApp(
    activity: Activity,
    settings: AppSettings,
    haptics: Haptics,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val P = LocalPalette.current

    var userProfile by remember { mutableStateOf(LocalStorage.loadUserProfile(context)) }
    var weightHistory by remember { mutableStateOf(LocalStorage.loadWeightHistory(context)) }
    var history by remember { mutableStateOf(LocalStorage.loadHistory(context)) }
    // Not remembered: programFor reads the user's programs, so edits to the active one show at once.
    val activeProgram = programFor(userProfile.splitProgram)
    val splits = activeProgram.splits

    var showOnboarding by remember { mutableStateOf(!userProfile.hasCompletedOnboarding || userProfile.currentWeightKg <= 0f) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var editingPastWorkout by remember { mutableStateOf<CompletedWorkout?>(null) }
    // Exercise detail screen, shown over everything else (including a running workout).
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    var editingProgramId by rememberSaveable { mutableStateOf<String?>(null) }

    val initialActive = remember { LocalStorage.loadActiveSession(context) }
    var activeSplit by remember {
        mutableStateOf(initialActive?.let { (title, _) -> splits.find { it.title == title } ?: WorkoutSplit(title, "", "01") })
    }
    var currentExercises by remember { mutableStateOf(initialActive?.second ?: emptyList()) }
    // The live workout is either open full-screen or minimized to the "Resume" bar.
    var workoutExpanded by remember { mutableStateOf(initialActive != null) }
    var lastBackPress by remember { mutableLongStateOf(0L) }

    val reloadFromDisk = rememberUpdatedState {
        val latest = LocalStorage.loadActiveSession(context)
        if (latest != null && editingPastWorkout == null) {
            activeSplit = splits.find { it.title == latest.first } ?: WorkoutSplit(latest.first, "", "01")
            currentExercises = latest.second
        }
    }

    // Weigh-ins from a scale or Samsung Health arrive through Health Connect while the app is away.
    val healthScope = rememberCoroutineScope()
    val pullFromHealth = rememberUpdatedState {
        healthScope.launch {
            HealthSync.refresh(context)
            if (HealthSync.importWeights(context)) {
                weightHistory = LocalStorage.loadWeightHistory(context)
                userProfile = LocalStorage.loadUserProfile(context)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadFromDisk.value()
                pullFromHealth.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) = reloadFromDisk.value()
        }
        val filter = IntentFilter(ACTION_SESSION_SYNC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    val workoutVisible = (activeSplit != null && workoutExpanded) || editingPastWorkout != null
    LaunchedEffect(workoutVisible, settings.keepScreenAwake) {
        if (workoutVisible && settings.keepScreenAwake) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    BackHandler(enabled = !workoutVisible && !showOnboarding) {
        if (selectedTab != AppTab.TODAY) {
            selectedTab = AppTab.TODAY
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPress < 2000L) activity.finish()
            else {
                lastBackPress = now
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startWorkout(split: WorkoutSplit) {
        haptics.confirm()
        if (activeSplit != null) {
            // A workout is already running — just bring it back.
            workoutExpanded = true
            return
        }
        val entries = LocalStorage.loadTemplateForSplit(context, split.title).map { (exName, muscle) ->
            newExerciseEntry(context, split.title, exName, muscle, history, settings)
        }
        activeSplit = split
        currentExercises = entries
        workoutExpanded = true
        LocalStorage.saveActiveSession(context, split.title, entries)
    }

    fun openPastWorkout(workout: CompletedWorkout) {
        haptics.tick()
        currentExercises = workout.exercises
        editingPastWorkout = workout
    }

    fun changeExerciseType(name: String, type: ExerciseType) {
        haptics.tick()
        LocalStorage.setExerciseType(context, name, type)
        history = LocalStorage.loadHistory(context)
        currentExercises = currentExercises.map { if (exerciseKey(it.name) == exerciseKey(name)) it.copy(type = type) else it }
    }

    fun deletePastWorkout(id: Long) {
        haptics.tick()
        LocalStorage.deleteWorkoutFromHistory(context, id)
        history = LocalStorage.loadHistory(context)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontallyBy(if (forward) 0.06f else -0.06f))
                        .togetherWith(fadeOut(tween(120)))
                },
                modifier = Modifier.weight(1f),
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    AppTab.TODAY -> HomeScreen(
                        splits = splits,
                        history = history,
                        userProfile = userProfile,
                        programName = activeProgram.name,
                        activeSplitTitle = activeSplit?.title,
                        onStartWorkout = { startWorkout(it) },
                        onOpenProgress = { selectedTab = AppTab.PROGRESS },
                        onOpenHistory = { selectedTab = AppTab.HISTORY }
                    )
                    AppTab.HISTORY -> HistoryScreen(
                        history = history,
                        userProfile = userProfile,
                        onOpenWorkout = { openPastWorkout(it) },
                        onDeleteWorkout = { deletePastWorkout(it) }
                    )
                    AppTab.PROGRESS -> ProgressScreen(
                        history = history,
                        userProfile = userProfile,
                        weightHistory = weightHistory,
                        haptics = haptics,
                        onLogWeight = { kg ->
                            weightHistory = LocalStorage.addWeightLog(context, kg)
                            userProfile = LocalStorage.loadUserProfile(context)
                        },
                        onDeleteWeight = { id -> weightHistory = LocalStorage.deleteWeightLog(context, id) },
                        onOpenExercise = { haptics.tick(); openExercise = it }
                    )
                    AppTab.PROFILE -> SettingsScreen(
                        settings = settings,
                        userProfile = userProfile,
                        onUpdateSettings = onUpdateSettings,
                        onUpdateProfile = { updated ->
                            userProfile = updated
                            LocalStorage.saveUserProfile(context, updated)
                        },
                        onOpenOnboarding = { showOnboarding = true },
                        onReplayTips = { selectedTab = AppTab.TODAY },
                        onDataRestored = {
                            history = LocalStorage.loadHistory(context)
                            userProfile = LocalStorage.loadUserProfile(context)
                            weightHistory = LocalStorage.loadWeightHistory(context)
                            onUpdateSettings(LocalStorage.loadSettings(context))
                        },
                        onEditProgram = { haptics.tick(); editingProgramId = it }
                    )
                }
            }

            AnimatedVisibility(
                visible = activeSplit != null && !workoutExpanded && editingPastWorkout == null,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                ResumeWorkoutBar(
                    splitTitle = activeSplit?.title.orEmpty(),
                    exercises = currentExercises,
                    onResume = {
                        haptics.tick()
                        workoutExpanded = true
                    }
                )
            }

            BottomNavBar(selected = selectedTab, onSelect = {
                if (it != selectedTab) haptics.tick()
                selectedTab = it
            })
        }

        // Full-screen workout (live or editing a past one) slides up over the tabs.
        AnimatedVisibility(
            visible = workoutVisible,
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200, delayMillis = 60))
        ) {
            val editing = editingPastWorkout
            val split = editing?.let { e -> splits.find { it.title == e.splitTitle } ?: WorkoutSplit(e.splitTitle, "", "01") }
                ?: activeSplit
            if (split != null) {
                Box(Modifier.fillMaxSize().background(P.bgPrimary)) {
                    if (editing != null) {
                        ActiveWorkoutScreen(
                            split = split,
                            exercises = currentExercises,
                            history = remember(history, editing.id) { history.filter { it.id < editing.id } },
                            settings = settings,
                            userProfile = userProfile,
                            isEditingPastWorkout = true,
                            initialDurationSeconds = editing.durationSeconds,
                            initialPhotoPath = editing.photoPath,
                            haptics = haptics,
                            hasUnsavedEdits = currentExercises != editing.exercises,
                            onOpenExercise = { openExercise = it },
                            onChangeExerciseType = ::changeExerciseType,
                            onUpdateExercises = { currentExercises = it },
                            onReorderExercises = { from, to -> currentExercises = currentExercises.moved(from, to) },
                            onAddExercise = { _, _ -> },
                            onRemoveExercisePermanently = { exName ->
                                currentExercises = currentExercises.filterNot { it.name.equals(exName, ignoreCase = true) }
                            },
                            onMinimize = {
                                editingPastWorkout = null
                                currentExercises = LocalStorage.loadActiveSession(context)?.second ?: emptyList()
                            },
                            onDiscardWorkout = {
                                editingPastWorkout = null
                                currentExercises = LocalStorage.loadActiveSession(context)?.second ?: emptyList()
                            },
                            onFinishWorkout = { _, photoPath, skipped ->
                                haptics.confirm()
                                val updated = buildCompletedWorkout(
                                    editing.copy(photoPath = photoPath, skippedExercises = skipped),
                                    currentExercises,
                                    history
                                )
                                LocalStorage.updateCompletedWorkout(context, updated)
                                history = LocalStorage.loadHistory(context)
                                editingPastWorkout = null
                                currentExercises = LocalStorage.loadActiveSession(context)?.second ?: emptyList()
                            }
                        )
                    } else {
                        ActiveWorkoutScreen(
                            split = split,
                            exercises = currentExercises,
                            history = history,
                            settings = settings,
                            userProfile = userProfile,
                            isEditingPastWorkout = false,
                            initialPhotoPath = null,
                            haptics = haptics,
                            onOpenExercise = { openExercise = it },
                            onChangeExerciseType = ::changeExerciseType,
                            onUpdateExercises = { updated ->
                                currentExercises = updated
                                LocalStorage.saveActiveSession(context, split.title, updated)
                            },
                            onReorderExercises = { from, to ->
                                val reordered = currentExercises.moved(from, to)
                                currentExercises = reordered
                                LocalStorage.saveActiveSession(context, split.title, reordered)
                                LocalStorage.saveTemplateForSplit(context, split.title, reordered.map { it.name to it.targetMuscle })
                            },
                            onAddExercise = { name, muscle -> LocalStorage.addExerciseToSplitTemplate(context, split.title, name, muscle) },
                            onRemoveExercisePermanently = { exName ->
                                LocalStorage.removeExerciseFromSplitTemplate(context, split.title, exName)
                                val updated = currentExercises.filterNot { it.name.equals(exName, ignoreCase = true) }
                                currentExercises = updated
                                LocalStorage.saveActiveSession(context, split.title, updated)
                            },
                            onMinimize = { workoutExpanded = false },
                            onDiscardWorkout = {
                                haptics.tick()
                                LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)
                                LocalStorage.saveActiveSession(context, null, emptyList())
                                workoutExpanded = false
                                activeSplit = null
                                currentExercises = emptyList()
                            },
                            onFinishWorkout = { durationSecs, photoPath, skipped ->
                                haptics.confirm()
                                val now = Date()
                                val completed = buildCompletedWorkout(
                                    CompletedWorkout(
                                        id = now.time,
                                        splitTitle = split.title,
                                        dateKey = DateUtils.formatDayKey(now),
                                        displayDate = DateUtils.formatDisplayDate(now),
                                        durationSeconds = durationSecs,
                                        bodyWeightKg = userProfile.currentWeightKg,
                                        photoPath = photoPath,
                                        skippedExercises = skipped,
                                        exercises = emptyList()
                                    ),
                                    currentExercises,
                                    history
                                )
                                LocalStorage.saveCompletedWorkout(context, completed)
                                history = LocalStorage.loadHistory(context)
                                workoutExpanded = false
                                activeSplit = null
                                currentExercises = emptyList()
                                selectedTab = AppTab.TODAY
                            }
                        )
                    }
                }
            }
        }

        var shownProgramId by remember { mutableStateOf(editingProgramId) }
        if (editingProgramId != null) shownProgramId = editingProgramId
        AnimatedVisibility(
            visible = editingProgramId != null,
            enter = slideInHorizontallyBy(1f) + fadeIn(tween(150)),
            exit = fadeOut(tween(180))
        ) {
            shownProgramId?.let { id ->
                ProgramEditorScreen(
                    programId = id,
                    isActive = userProfile.splitProgram == id,
                    haptics = haptics,
                    onUseProgram = {
                        userProfile = userProfile.copy(splitProgram = id)
                        LocalStorage.saveUserProfile(context, userProfile)
                    },
                    onDeleted = {
                        if (userProfile.splitProgram == id) {
                            userProfile = userProfile.copy(splitProgram = DEFAULT_PROGRAM_ID)
                            LocalStorage.saveUserProfile(context, userProfile)
                        }
                        editingProgramId = null
                    },
                    onHistoryChanged = { history = LocalStorage.loadHistory(context) },
                    onBack = { editingProgramId = null }
                )
            }
        }

        // Keep the last name while the screen animates out.
        var shownExercise by remember { mutableStateOf(openExercise) }
        if (openExercise != null) shownExercise = openExercise
        AnimatedVisibility(
            visible = openExercise != null,
            enter = slideInHorizontallyBy(1f) + fadeIn(tween(150)),
            exit = fadeOut(tween(180))
        ) {
            shownExercise?.let { name ->
                ExerciseDetailScreen(
                    exerciseName = name,
                    history = history,
                    haptics = haptics,
                    // A past workout being edited still holds the old name and would write it back.
                    canRename = editingPastWorkout == null,
                    onRename = { newName ->
                        val renamed = LocalStorage.renameExercise(context, name, newName)
                        if (renamed) {
                            history = LocalStorage.loadHistory(context)
                            if (activeSplit != null) currentExercises = LocalStorage.loadActiveSession(context)?.second ?: currentExercises
                            openExercise = newName
                        }
                        renamed
                    },
                    onChangeType = { type -> changeExerciseType(name, type) },
                    onBack = { openExercise = null }
                )
            }
        }

        AnimatedVisibility(
            visible = showOnboarding,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.98f),
            exit = fadeOut(tween(250))
        ) {
            Box(Modifier.fillMaxSize().background(P.bgPrimary)) {
                OnboardingScreen(
                    initialProfile = userProfile,
                    unit = settings.weightUnit,
                    onSelectUnit = { onUpdateSettings(settings.copy(weightUnit = it, weightStep = it.defaultStep)) },
                    canGoBack = userProfile.hasCompletedOnboarding && userProfile.currentWeightKg > 0f,
                    haptics = haptics,
                    onBack = { showOnboarding = false },
                    onSaveProfile = { newProfile ->
                        val weightChanged = newProfile.currentWeightKg != userProfile.currentWeightKg
                        userProfile = newProfile
                        LocalStorage.saveUserProfile(context, newProfile)
                        if (weightChanged) weightHistory = LocalStorage.addWeightLog(context, newProfile.currentWeightKg)
                        showOnboarding = false
                    }
                )
            }
        }
    }
}

/** Creates a fresh exercise for today: one warm-up plus two work sets at the suggested weight. */
fun newExerciseEntry(
    context: Context,
    splitTitle: String,
    name: String,
    muscle: String,
    history: List<CompletedWorkout>,
    settings: AppSettings
): ExerciseEntry {
    val plan = liftPlanFor(splitTitle, name)
        ?: return newExerciseEntryDefault(context, splitTitle, name, muscle, history, settings)

    // A program's main lift: its sets come from a training max (its own, or another lift's for
    // nSuns' secondary lifts).
    val tm = LocalStorage.getTrainingMax(context, plan.trainingMaxOf)
    if (tm == null) {
        val whose = if (exerciseKey(plan.trainingMaxOf) == exerciseKey(name)) "a training max (tap the name)" else "a ${plan.trainingMaxOf} training max"
        return newExerciseEntryDefault(context, splitTitle, name, muscle, history, settings)
            .copy(planNote = "Set $whose to get your ${plan.scheme.label} weights")
    }
    val type = LocalStorage.exerciseType(context, name)
    val rest = LocalStorage.getExerciseDefaultRest(context, name, settings.restTimerSeconds)
    val day = planLiftDay(plan, tm, history)
    val note = buildList {
        add(day.title)
        if (day.weightKg == null) add("TM ${formatWeightKg(day.trainingMaxKg)}")
        day.amrapText()?.let(::add)
    }.joinToString(" · ")
    return ExerciseEntry(
        name = name, targetMuscle = muscle, restSeconds = rest, type = type, programmed = true,
        planNote = note,
        sets = day.sets.mapIndexed { i, p ->
            SetEntry(
                setNumber = i + 1,
                weight = day.weightFor(p),
                reps = "${p.reps}",
                isWarmup = p.warmup,
                intensityType = if (p.warmup) "WARMUP" else "NORMAL",
                restSeconds = if (p.warmup) minOf(60, rest) else rest
            )
        }
    )
}

/** A new entry without a percentage program: targets, last session, or plain defaults. */
private fun newExerciseEntryDefault(
    context: Context,
    splitTitle: String,
    name: String,
    muscle: String,
    history: List<CompletedWorkout>,
    settings: AppSettings
): ExerciseEntry {
    val type = LocalStorage.exerciseType(context, name)
    val rest = LocalStorage.getExerciseDefaultRest(context, name, settings.restTimerSeconds)
    val target = LocalStorage.getTarget(context, splitTitle, name)
    val workSetCount = target?.sets
    val targetReps = target?.repMax?.toString()

    if (!type.hasWeightSuggestions) {
        // No weight to suggest: start from your own last session, else plain defaults.
        val last = history.firstNotNullOfOrNull { w ->
            w.exercises.find { !it.isSkipped && exerciseKey(it.name) == exerciseKey(name) }?.workSets?.takeIf { it.isNotEmpty() }
        }
        val defaultSet = SetEntry(
            setNumber = 0,
            weight = "",
            reps = if (type.usesReps) targetReps ?: "10" else "",
            seconds = if (type == ExerciseType.DURATION) "60" else ""
        )
        val count = workSetCount ?: last?.size ?: if (type == ExerciseType.DISTANCE_TIME) 1 else 3
        val template = List(count) { i -> last?.getOrNull(i) ?: last?.lastOrNull() ?: defaultSet }
        return ExerciseEntry(
            name = name, targetMuscle = muscle, restSeconds = rest, type = type, target = target,
            sets = template.mapIndexed { i, s ->
                SetEntry(i + 1, weight = s.weight, reps = (if (type.usesReps) targetReps else null) ?: s.reps, seconds = s.seconds, distanceM = s.distanceM, restSeconds = rest)
            }
        )
    }

    val suggestion = suggestFromSplitHistory(splitTitle, ExerciseEntry(name, muscle, type = type, target = target), history, settings.weightStep)
    val unit = Units.current
    if (type == ExerciseType.WEIGHTED_BODYWEIGHT) {
        // Added weight; no warm-up set (the warm-up is the bodyweight version).
        val added = suggestion?.suggestedWeight?.ifBlank { null } ?: "0"
        return ExerciseEntry(
            name = name, targetMuscle = muscle, restSeconds = rest, type = type, target = target,
            sets = List(workSetCount ?: 3) { i -> SetEntry(i + 1, weight = added, reps = targetReps ?: "8", restSeconds = rest) }
        )
    }
    val workWeight = suggestion?.suggestedWeight?.ifBlank { null } ?: unit.stored(unit.defaultWorkWeight)
    // Warm-up at half the work weight, rounded down to a whole default step (2.5 kg / 5 lb).
    val half = unit.fromKg(parseWeightClean(workWeight, unit.toKg(unit.defaultWorkWeight))) * 0.5f
    val warmupWeight = unit.stored((half / unit.defaultStep).toInt() * unit.defaultStep)
    return ExerciseEntry(
        name = name,
        targetMuscle = muscle,
        restSeconds = rest,
        type = type,
        target = target,
        sets = listOf(SetEntry(1, weight = warmupWeight, reps = "8", isWarmup = true, intensityType = "WARMUP", restSeconds = minOf(60, rest))) +
            List(workSetCount ?: 2) { i -> SetEntry(i + 2, weight = workWeight, reps = targetReps ?: "8", restSeconds = rest) }
    )
}

private fun slideInHorizontallyBy(fraction: Float) =
    androidx.compose.animation.slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) {
        (it * fraction).toInt()
    }

@Composable
private fun BottomNavBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val P = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (P.isDark) 0.dp else 16.dp, spotColor = P.shadow.copy(alpha = 0.12f), ambientColor = P.shadow.copy(alpha = 0.06f))
            .background(P.bgCard)
    ) {
        if (P.isDark) Box(Modifier.fillMaxWidth().height(1.dp).background(P.borderSubtle))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val iconColor by animateColorAsState(if (isSelected) P.accentPrimary else P.textSecondary, label = "nav_icon")
                val pillColor by animateColorAsState(if (isSelected) P.accentSoft else P.bgCard.copy(alpha = 0f), label = "nav_pill")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .pressable(pressedScale = 0.92f, role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillColor)
                            .padding(horizontal = 18.dp, vertical = 5.dp)
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, style = AppType.caption, color = if (isSelected) P.textPrimary else P.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ResumeWorkoutBar(splitTitle: String, exercises: List<ExerciseEntry>, onResume: () -> Unit) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val startTime = remember(splitTitle) { LocalStorage.getActiveSessionStartTime(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var restLeft by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            restLeft = ((LocalStorage.getRestEndTimeMillis(context) - now) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }
    val active = exercises.filter { !it.isSkipped }
    val total = active.sumOf { it.sets.size }
    val done = active.sumOf { ex -> ex.sets.count { it.isCompleted } }

    AppCard(
        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp).fillMaxWidth(),
        elevated = true,
        onClick = onResume,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = if (total == 0) 0f else done / total.toFloat(),
                modifier = Modifier.size(40.dp),
                strokeWidth = 4.dp
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(P.accentPrimary))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$splitTitle in progress", style = AppType.bodyStrong, color = P.textPrimary)
                Text(
                    "${formatClock((now - startTime) / 1000L)} · $done of $total sets",
                    style = AppType.caption.copy(fontFeatureSettings = "tnum"),
                    color = P.textSecondary
                )
            }
            if (restLeft > 0) {
                Pill(formatClock(restLeft), color = P.accentSecondary, container = P.accentSecondarySoft, icon = Icons.Rounded.Timer)
                Spacer(Modifier.width(8.dp))
            }
            Text("Resume", style = AppType.label, color = P.accentPrimary)
        }
    }
}
