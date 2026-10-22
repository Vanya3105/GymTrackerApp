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
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymTrackerRoot(activity = this)
        }
    }
}

@Composable
fun GymTrackerRoot(activity: Activity) {
    val context = LocalContext.current
    val view = LocalView.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        ensureNotificationChannelCreated(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var settings by remember { mutableStateOf(LocalStorage.loadSettings(context)) }
    val palette = when (settings.themeChoice) {
        "ARCTIC" -> LightEditorialPalette
        "CYBERPUNK" -> CyberpunkNeonPalette
        "OLED" -> MidnightOledPalette
        else -> DarkLuxuryPalette
    }

    LaunchedEffect(settings.targetFps, settings.isDarkMode) {
        applyDisplayRefreshRate(activity, settings.targetFps)
        val window = activity.window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !settings.isDarkMode
            isAppearanceLightNavigationBars = !settings.isDarkMode
        }
    }

    val triggerHaptic = remember(settings.hapticsEnabled, view) {
        {
            if (settings.hapticsEnabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (palette.isDark) {
                darkColorScheme(
                    background = palette.bgPrimary,
                    surface = palette.bgCard,
                    primary = palette.accentPrimary
                )
            } else {
                lightColorScheme(
                    background = palette.bgPrimary,
                    surface = palette.bgCard,
                    primary = palette.accentPrimary
                )
            }
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = palette.bgPrimary) {
                GymTrackerApp(
                    activity = activity,
                    settings = settings,
                    onUpdateSettings = { newSettings ->
                        settings = newSettings
                        LocalStorage.saveSettings(context, newSettings)
                        triggerHaptic()
                    },
                    onHaptic = triggerHaptic
                )
            }
        }
    }
}

private fun screenDepth(key: String): Int = when (key) {
    "ONBOARDING" -> 0
    "HOME" -> 1
    "WEIGHT_TRACKER", "WEEKLY", "SETTINGS", "ACTIVE_WORKOUT" -> 2
    "CALENDAR", "EDIT_PAST" -> 3
    else -> 1
}

@Composable
fun GymTrackerApp(
    activity: Activity,
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onHaptic: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var userProfile by remember { mutableStateOf(LocalStorage.loadUserProfile(context)) }
    var weightHistory by remember { mutableStateOf(LocalStorage.loadWeightHistory(context)) }
    var history by remember { mutableStateOf(LocalStorage.loadHistory(context)) }

    val activeProgram = remember(userProfile.splitProgram) {
        SPLIT_PROGRAMS[userProfile.splitProgram] ?: SPLIT_PROGRAMS["PPL_ARNOLD"]!!
    }
    val splits = activeProgram.splits

    var showOnboardingScreen by remember {
        mutableStateOf(!userProfile.hasCompletedOnboarding || userProfile.currentWeightKg <= 0f)
    }
    var showWeightTrackerScreen by remember { mutableStateOf(false) }
    var showWeeklySummaryScreen by remember { mutableStateOf(false) }
    var showMonthlyCalendarScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var editingPastWorkout by remember { mutableStateOf<CompletedWorkout?>(null) }

    val initialActive = remember { LocalStorage.loadActiveSession(context) }
    var selectedWorkout by remember {
        mutableStateOf(initialActive?.let { pair ->
            splits.find { it.title == pair.first } ?: WorkoutSplit(pair.first, "", "01")
        })
    }
    var currentExercises by remember {
        mutableStateOf(initialActive?.second ?: emptyList())
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    val reloadFromDisk = rememberUpdatedState {
        val latestActive = LocalStorage.loadActiveSession(context)
        if (latestActive != null && editingPastWorkout == null) {
            selectedWorkout = splits.find { it.title == latestActive.first }
                ?: WorkoutSplit(latestActive.first, "", "01")
            currentExercises = latestActive.second
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadFromDisk.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                reloadFromDisk.value()
            }
        }
        val filter = IntentFilter(ACTION_SESSION_SYNC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    val isWorkoutScreenOpen = selectedWorkout != null || editingPastWorkout != null
    LaunchedEffect(isWorkoutScreenOpen, settings.keepScreenAwake) {
        if (isWorkoutScreenOpen && settings.keepScreenAwake) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val currentScreenKey = when {
        showOnboardingScreen -> "ONBOARDING"
        editingPastWorkout != null -> "EDIT_PAST"
        selectedWorkout != null -> "ACTIVE_WORKOUT"
        showWeightTrackerScreen -> "WEIGHT_TRACKER"
        showSettingsScreen -> "SETTINGS"
        showMonthlyCalendarScreen -> "CALENDAR"
        showWeeklySummaryScreen -> "WEEKLY"
        else -> "HOME"
    }

    // System Edge-Swipe Back Navigation for all non-workout screens
    // (Active Workout & Edit Past handle their own BackHandler inside ActiveWorkoutScreen)
    BackHandler(enabled = currentScreenKey != "ACTIVE_WORKOUT" && currentScreenKey != "EDIT_PAST") {
        onHaptic()
        when (currentScreenKey) {
            "CALENDAR" -> showMonthlyCalendarScreen = false
            "WEEKLY" -> showWeeklySummaryScreen = false
            "SETTINGS" -> showSettingsScreen = false
            "WEIGHT_TRACKER" -> showWeightTrackerScreen = false
            "ONBOARDING" -> {
                if (userProfile.hasCompletedOnboarding && userProfile.currentWeightKg > 0f) {
                    showOnboardingScreen = false
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < 2000L) {
                        activity.finish()
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(context, "Swipe back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "HOME" -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    activity.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Swipe back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startWorkout(split: WorkoutSplit) {
        onHaptic()
        val savedTemplate = LocalStorage.loadTemplateForSplit(context, split.title)
        val entries = savedTemplate.map { (exName, muscle) ->
            val dummy = ExerciseEntry(exName, muscle)
            val advice = evaluateSplitIsolatedAi(split.title, dummy, history, settings.weightStepKg)
            val workWeight = advice?.suggestedWeight?.ifBlank { "40" } ?: "40"
            val warmupWeight = ((workWeight.toFloatOrNull() ?: 40f) * 0.5f).toInt().toString()
            val exRest = LocalStorage.getExerciseDefaultRest(context, exName, settings.restTimerSeconds)

            ExerciseEntry(
                name = exName,
                targetMuscle = muscle,
                restSeconds = exRest,
                isSkipped = false,
                sets = listOf(
                    SetEntry(1, weight = warmupWeight, reps = "8", isWarmup = true, restSeconds = minOf(60, exRest)),
                    SetEntry(2, weight = workWeight, reps = "8", isWarmup = false, restSeconds = exRest),
                    SetEntry(3, weight = workWeight, reps = "8", isWarmup = false, restSeconds = exRest)
                )
            )
        }

        selectedWorkout = split
        currentExercises = entries
        LocalStorage.saveActiveSession(context, split.title, entries)
    }

    AnimatedContent(
        targetState = currentScreenKey,
        transitionSpec = {
            val isForward = screenDepth(targetState) >= screenDepth(initialState)
            val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
                durationMillis = 280,
                easing = FastOutSlowInEasing
            )
            val fadeSpec = tween<Float>(durationMillis = 220)

            if (isForward) {
                (slideInHorizontally(animationSpec = slideSpec) { fullWidth -> fullWidth / 4 } + fadeIn(fadeSpec))
                    .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { fullWidth -> -fullWidth / 5 } + fadeOut(fadeSpec))
            } else {
                (slideInHorizontally(animationSpec = slideSpec) { fullWidth -> -fullWidth / 5 } + fadeIn(fadeSpec))
                    .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { fullWidth -> fullWidth / 4 } + fadeOut(fadeSpec))
            }.using(SizeTransform(clip = false))
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            "ONBOARDING" -> {
                OnboardingScreen(
                    initialProfile = userProfile,
                    onHaptic = onHaptic,
                    onSaveProfile = { newProfile ->
                        userProfile = newProfile
                        LocalStorage.saveUserProfile(context, newProfile)
                        weightHistory = LocalStorage.addWeightLog(context, newProfile.currentWeightKg)
                        showOnboardingScreen = false
                    }
                )
            }
            "WEIGHT_TRACKER" -> {
                WeightTrackerScreen(
                    userProfile = userProfile,
                    weightHistory = weightHistory,
                    onHaptic = onHaptic,
                    onLogWeight = { newKg ->
                        onHaptic()
                        weightHistory = LocalStorage.addWeightLog(context, newKg)
                        userProfile = LocalStorage.loadUserProfile(context)
                    },
                    onDeleteWeight = { id ->
                        onHaptic()
                        weightHistory = LocalStorage.deleteWeightLog(context, id)
                    },
                    onBack = {
                        onHaptic()
                        showWeightTrackerScreen = false
                    }
                )
            }
            "EDIT_PAST" -> {
                val target = editingPastWorkout
                if (target != null) {
                    val splitObj = splits.find { it.title == target.splitTitle }
                        ?: WorkoutSplit(target.splitTitle, "", "01")
                    ActiveWorkoutScreen(
                        split = splitObj,
                        exercises = currentExercises,
                        history = history,
                        settings = settings,
                        userProfile = userProfile,
                        isEditingPastWorkout = true,
                        initialPhotoPath = target.photoPath,
                        onHaptic = onHaptic,
                        onUpdateExercises = { updated -> currentExercises = updated },
                        onReorderExercises = { fromIdx, toIdx ->
                            val mutable = currentExercises.toMutableList()
                            val moved = mutable.removeAt(fromIdx)
                            mutable.add(toIdx, moved)
                            currentExercises = mutable
                        },
                        onAddCustomExercise = {},
                        onRemoveExercisePermanently = { exName ->
                            currentExercises = currentExercises.filterNot { it.name.equals(exName, ignoreCase = true) }
                        },
                        onCancelWorkout = {
                            onHaptic()
                            editingPastWorkout = null
                            currentExercises = emptyList()
                        },
                        onFinishWorkout = { _, updatedPhotoPath, skippedList ->
                            onHaptic()
                            val performedOnly = currentExercises.filter { !it.isSkipped }
                            val updatedWorkout = target.copy(
                                photoPath = updatedPhotoPath,
                                skippedExercises = skippedList,
                                exercises = performedOnly
                            )
                            LocalStorage.updateCompletedWorkout(context, updatedWorkout)
                            history = LocalStorage.loadHistory(context)
                            editingPastWorkout = null
                            currentExercises = emptyList()
                        }
                    )
                }
            }
            "ACTIVE_WORKOUT" -> {
                val activeSplit = selectedWorkout
                if (activeSplit != null) {
                    ActiveWorkoutScreen(
                        split = activeSplit,
                        exercises = currentExercises,
                        history = history,
                        settings = settings,
                        userProfile = userProfile,
                        isEditingPastWorkout = false,
                        initialPhotoPath = null,
                        onHaptic = onHaptic,
                        onUpdateExercises = { updated ->
                            currentExercises = updated
                            LocalStorage.saveActiveSession(context, activeSplit.title, updated)
                        },
                        onReorderExercises = { fromIdx, toIdx ->
                            val mutable = currentExercises.toMutableList()
                            val moved = mutable.removeAt(fromIdx)
                            mutable.add(toIdx, moved)
                            currentExercises = mutable
                            LocalStorage.saveActiveSession(context, activeSplit.title, mutable)
                            LocalStorage.saveTemplateForSplit(
                                context,
                                activeSplit.title,
                                mutable.map { it.name to it.targetMuscle }
                            )
                        },
                        onAddCustomExercise = { customName ->
                            LocalStorage.addExerciseToSplitTemplate(context, activeSplit.title, customName)
                        },
                        onRemoveExercisePermanently = { exName ->
                            LocalStorage.removeExerciseFromSplitTemplate(context, activeSplit.title, exName)
                            val updated = currentExercises.filterNot { it.name.equals(exName, ignoreCase = true) }
                            currentExercises = updated
                            LocalStorage.saveActiveSession(context, activeSplit.title, updated)
                        },
                        onCancelWorkout = {
                            onHaptic()
                            selectedWorkout = null
                            currentExercises = emptyList()
                            LocalStorage.saveActiveSession(context, null, emptyList())
                        },
                        onFinishWorkout = { durationSecs, photoPath, skippedList ->
                            onHaptic()
                            val now = Date()
                            val performedOnly = currentExercises.filter { !it.isSkipped }
                            val completed = CompletedWorkout(
                                id = System.currentTimeMillis(),
                                splitTitle = activeSplit.title,
                                dateKey = DateUtils.formatDayKey(now),
                                displayDate = DateUtils.formatDisplayDate(now),
                                durationSeconds = durationSecs,
                                bodyWeightKg = userProfile.currentWeightKg,
                                photoPath = photoPath,
                                skippedExercises = skippedList,
                                exercises = performedOnly
                            )
                            LocalStorage.saveCompletedWorkout(context, completed)
                            history = LocalStorage.loadHistory(context)
                            selectedWorkout = null
                            currentExercises = emptyList()
                        }
                    )
                }
            }
            "SETTINGS" -> {
                SettingsScreen(
                    settings = settings,
                    userProfile = userProfile,
                    onUpdateSettings = onUpdateSettings,
                    onUpdateProfile = { updatedProf ->
                        onHaptic()
                        userProfile = updatedProf
                        LocalStorage.saveUserProfile(context, updatedProf)
                    },
                    onOpenOnboarding = {
                        onHaptic()
                        showSettingsScreen = false
                        showOnboardingScreen = true
                    },
                    onDataRestored = {
                        history = LocalStorage.loadHistory(context)
                        userProfile = LocalStorage.loadUserProfile(context)
                        weightHistory = LocalStorage.loadWeightHistory(context)
                        onUpdateSettings(LocalStorage.loadSettings(context))
                    },
                    onBack = {
                        onHaptic()
                        showSettingsScreen = false
                    }
                )
            }
            "CALENDAR" -> {
                HistoryCalendarScreen(
                    history = history,
                    onEditWorkout = { workoutToEdit ->
                        onHaptic()
                        currentExercises = workoutToEdit.exercises
                        editingPastWorkout = workoutToEdit
                    },
                    onDeleteWorkout = { id ->
                        onHaptic()
                        LocalStorage.deleteWorkoutFromHistory(context, id)
                        history = LocalStorage.loadHistory(context)
                    },
                    onBackToWeekly = {
                        onHaptic()
                        showMonthlyCalendarScreen = false
                    }
                )
            }
            "WEEKLY" -> {
                WeeklySummaryScreen(
                    history = history,
                    userProfile = userProfile,
                    onEditWorkout = { workoutToEdit ->
                        onHaptic()
                        currentExercises = workoutToEdit.exercises
                        editingPastWorkout = workoutToEdit
                    },
                    onDeleteWorkout = { id ->
                        onHaptic()
                        LocalStorage.deleteWorkoutFromHistory(context, id)
                        history = LocalStorage.loadHistory(context)
                    },
                    onBackHome = {
                        onHaptic()
                        showWeeklySummaryScreen = false
                    },
                    onOpenMonthlyCalendar = {
                        onHaptic()
                        showMonthlyCalendarScreen = true
                    }
                )
            }
            else -> {
                val currentWeekSummary = remember(history, userProfile.splitProgram) {
                    computeWeekSummary(history, weekOffset = 0, splitProgramId = userProfile.splitProgram)
                }
                HomeScreen(
                    splits = splits,
                    currentWeek = currentWeekSummary,
                    userProfile = userProfile,
                    activeProgramName = activeProgram.name,
                    onSelectWorkout = { startWorkout(it) },
                    onOpenWeightTracker = {
                        onHaptic()
                        showWeightTrackerScreen = true
                    },
                    onOpenWeeklySummary = {
                        onHaptic()
                        showWeeklySummaryScreen = true
                    },
                    onOpenSettings = {
                        onHaptic()
                        showSettingsScreen = true
                    }
                )
            }
        }
    }
}