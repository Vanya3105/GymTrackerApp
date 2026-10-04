package com.example.gymtracker

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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

    // Automatically request Android 13+ Notification Permission on app start
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
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
    val palette = if (settings.isDarkMode) DarkLuxuryPalette else LightEditorialPalette

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

@Composable
fun GymTrackerApp(
    activity: Activity,
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onHaptic: () -> Unit
) {
    val context = LocalContext.current

    val splits = remember {
        listOf(
            WorkoutSplit("Push", "Chest • Shoulders • Triceps", "01"),
            WorkoutSplit("Pull", "Lats • Upper Back • Biceps", "02"),
            WorkoutSplit("Arms & Shoulders", "Delts • Biceps • Triceps", "03"),
            WorkoutSplit("Chest & Back", "Torso Antagonist Hypertrophy", "04")
        )
    }

    var history by remember { mutableStateOf(LocalStorage.loadHistory(context)) }
    var showWeeklySummaryScreen by remember { mutableStateOf(false) }
    var showMonthlyCalendarScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var editingPastWorkout by remember { mutableStateOf<CompletedWorkout?>(null) }

    val initialActive = remember { LocalStorage.loadActiveSession(context) }
    var selectedWorkout by remember {
        mutableStateOf(initialActive?.let { pair -> splits.find { it.title == pair.first } })
    }
    var currentExercises by remember {
        mutableStateOf(initialActive?.second ?: emptyList())
    }

    val isWorkoutScreenOpen = selectedWorkout != null || editingPastWorkout != null
    LaunchedEffect(isWorkoutScreenOpen, settings.keepScreenAwake) {
        if (isWorkoutScreenOpen && settings.keepScreenAwake) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val currentWeekSummary = remember(history) { computeWeekSummary(history, weekOffset = 0) }

    fun startWorkout(split: WorkoutSplit) {
        onHaptic()
        val savedTemplate = LocalStorage.loadTemplateForSplit(context, split.title)
        val entries = savedTemplate.map { (exName, muscle) ->
            val dummy = ExerciseEntry(exName, muscle)
            val advice = evaluateSplitIsolatedAi(split.title, dummy, history, settings.weightStepKg)
            val workWeight = advice?.suggestedWeight?.ifBlank { "40" } ?: "40"
            val warmupWeight = ((workWeight.toFloatOrNull() ?: 40f) * 0.5f).toInt().toString()

            ExerciseEntry(
                name = exName,
                targetMuscle = muscle,
                sets = listOf(
                    SetEntry(1, weight = warmupWeight, reps = "8", isWarmup = true),
                    SetEntry(2, weight = workWeight, reps = "8", isWarmup = false),
                    SetEntry(3, weight = workWeight, reps = "8", isWarmup = false)
                )
            )
        }

        selectedWorkout = split
        currentExercises = entries
        LocalStorage.saveActiveSession(context, split.title, entries)
    }

    val currentScreenKey = when {
        editingPastWorkout != null -> "EDIT_PAST"
        selectedWorkout != null -> "ACTIVE_WORKOUT"
        showSettingsScreen -> "SETTINGS"
        showMonthlyCalendarScreen -> "CALENDAR"
        showWeeklySummaryScreen -> "WEEKLY"
        else -> "HOME"
    }

    Crossfade(
        targetState = currentScreenKey,
        animationSpec = tween(durationMillis = 180),
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            "EDIT_PAST" -> {
                val target = editingPastWorkout
                if (target != null) {
                    val splitObj = splits.find { it.title == target.splitTitle }
                        ?: WorkoutSplit(target.splitTitle, "", "00")
                    ActiveWorkoutScreen(
                        split = splitObj,
                        exercises = currentExercises,
                        history = history,
                        settings = settings,
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
                        onFinishWorkout = { _, updatedPhotoPath ->
                            onHaptic()
                            val updatedWorkout = target.copy(
                                photoPath = updatedPhotoPath,
                                exercises = currentExercises
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
                        onFinishWorkout = { durationSecs, photoPath ->
                            onHaptic()
                            val now = Date()
                            val completed = CompletedWorkout(
                                id = System.currentTimeMillis(),
                                splitTitle = activeSplit.title,
                                dateKey = DateUtils.formatDayKey(now),
                                displayDate = DateUtils.formatDisplayDate(now),
                                durationSeconds = durationSecs,
                                photoPath = photoPath,
                                exercises = currentExercises
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
                    onUpdateSettings = onUpdateSettings,
                    onDataRestored = {
                        history = LocalStorage.loadHistory(context)
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
                HomeScreen(
                    splits = splits,
                    currentWeek = currentWeekSummary,
                    settings = settings,
                    onSelectWorkout = { startWorkout(it) },
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