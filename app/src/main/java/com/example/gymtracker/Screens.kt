package com.example.gymtracker

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================================
// 1. ONBOARDING & PROFILE SCREEN
// ============================================================================

@Composable
fun OnboardingScreen(
    initialProfile: UserProfile,
    onHaptic: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit
) {
    val P = LocalPalette.current
    var draftProgram by remember { mutableStateOf(initialProfile.splitProgram) }
    var draftAge by remember { mutableStateOf(if (initialProfile.age > 0) initialProfile.age.toString() else "") }
    var draftWeight by remember { mutableStateOf(if (initialProfile.currentWeightKg > 0f) formatKgClean(initialProfile.currentWeightKg) else "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = "WELCOME • ATHLETE SETUP",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = P.accentPrimary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "BUILD YOUR PROFILE",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = P.textPrimary,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = "Enter your age, current body weight, and select your training split.",
            fontSize = 13.sp,
            color = P.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = P.bgCard,
                border = BorderStroke(1.5.dp, if (draftAge.isNotBlank()) P.accentPrimary else P.borderStrong),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("YOUR AGE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = P.textSecondary)
                    BasicTextField(
                        value = draftAge,
                        onValueChange = { draftAge = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = P.textPrimary),
                        cursorBrush = SolidColor(P.accentPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 40.dp)
                            .padding(top = 8.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draftAge.isEmpty()) Text("Age", color = P.textSecondary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                inner()
                            }
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                color = P.bgCard,
                border = BorderStroke(1.5.dp, if (draftWeight.isNotBlank()) P.accentPrimary else P.borderStrong),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("WEIGHT (KG)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = P.textSecondary)
                    BasicTextField(
                        value = draftWeight,
                        onValueChange = { draftWeight = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = P.accentPrimary),
                        cursorBrush = SolidColor(P.accentPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 40.dp)
                            .padding(top = 8.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draftWeight.isEmpty()) Text("Kg", color = P.textSecondary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                inner()
                            }
                        }
                    )
                }
            }
        }

        Text(
            text = "WHAT SPLIT DO YOU RUN?",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            color = P.textSecondary,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(SPLIT_PROGRAMS.keys.toList()) { progKey ->
                val prog = SPLIT_PROGRAMS[progKey]!!
                val isSelected = draftProgram == progKey
                SpringButton(
                    scaleTo = 0.97f,
                    onClick = {
                        onHaptic()
                        draftProgram = progKey
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = P.bgCard,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) P.accentPrimary else P.borderSubtle),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(prog.name, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = P.textPrimary)
                                Surface(
                                    color = if (isSelected) P.accentPrimary else P.bgControl,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${prog.weeklyGoal} DAYS / WK",
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) P.onAccentPrimary else P.textSecondary
                                    )
                                }
                            }
                            Text(prog.tagline, fontSize = 12.sp, color = P.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            item {
                SpringButton(
                    scaleTo = 0.96f,
                    onClick = {
                        val age = draftAge.toIntOrNull() ?: 0
                        val weight = draftWeight.toFloatOrNull() ?: 0f
                        if (age <= 0 || weight <= 0f) return@SpringButton
                        onHaptic()
                        onSaveProfile(
                            UserProfile(
                                hasCompletedOnboarding = true,
                                age = age,
                                currentWeightKg = weight,
                                splitProgram = draftProgram
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        color = P.accentPrimary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("SAVE & START TRAINING ⚡", fontSize = 16.sp, fontWeight = FontWeight.Black, color = P.onAccentPrimary)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. HOME SCREEN (Includes Spotlight Tutorial + AI Muscle Fatigue + Share)
// ============================================================================

@Composable
fun HomeScreen(
    splits: List<WorkoutSplit>,
    currentWeek: WeekSummaryData,
    userProfile: UserProfile,
    activeProgramName: String,
    onSelectWorkout: (WorkoutSplit) -> Unit,
    onOpenWeightTracker: () -> Unit,
    onOpenWeeklySummary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val allHistory = remember(currentWeek) { LocalStorage.loadHistory(context) }
    val targetCount = currentWeek.weeklyGoal
    val weeklyProgress = (currentWeek.completedCoreSplits.size.toFloat() / targetCount).coerceIn(0f, 1f)
    val sessionsLeft = maxOf(0, targetCount - currentWeek.completedCoreSplits.size)

    val tutorialBounds = remember { mutableStateMapOf<String, Rect>() }
    var showHomeTutorial by remember(TutorialPrefs.replayGeneration) {
        mutableStateOf(!TutorialPrefs.hasSeenHomeTutorial(context))
    }
    var tutorialStepIndex by remember(TutorialPrefs.replayGeneration) { mutableIntStateOf(0) }

    val homeTutorialSteps = remember {
        listOf(
            TutorialStepInfo(
                key = "HOME_HEADER",
                title = "Body Weight & Custom Settings",
                description = "Tap your Body Weight pill anytime to log daily weigh-ins and track your trendline. Tap ⚙ to switch themes, weight increments, or replay this tutorial."
            ),
            TutorialStepInfo(
                key = "HOME_WEEKLY",
                title = "Weekly Microcycle & Story Share",
                description = "Tracks your weekly split goal automatically. Once you log a session, tap '📤 Share' here to export a Strava-style workout card with your pump photo!"
            ),
            TutorialStepInfo(
                key = "HOME_FATIGUE",
                title = "AI Muscle Recovery & MAV Volume",
                description = "Models 72-hour muscle recovery and counts your weekly working sets per muscle group (aiming for the 6–16 set Optimal Hypertrophy Zone)."
            ),
            TutorialStepInfo(
                key = "HOME_SPLIT_FIRST",
                title = "Start Your Next Split",
                description = "The 'UP NEXT' badge highlights your recommended workout for today. Tap any split card to launch a live workout with AI weight targets!"
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${activeProgramName.uppercase()} • $targetCount DAYS/WK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.8.sp,
                        color = P.accentPrimary
                    )
                    Text("WORKOUTS", fontSize = 30.sp, fontWeight = FontWeight.Black, color = P.textPrimary)
                }
                Row(
                    modifier = Modifier.onGloballyPositioned { coords ->
                        tutorialBounds["HOME_HEADER"] = coords.boundsInRoot()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpringButton(scaleTo = 0.93f, onClick = onOpenWeightTracker) {
                        Surface(
                            color = P.bgCard,
                            border = BorderStroke(1.dp, P.accentSecondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("BODY WEIGHT", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = P.textSecondary)
                                Text("⚖️ ${formatKgClean(userProfile.currentWeightKg)} kg", fontSize = 14.sp, fontWeight = FontWeight.Black, color = P.accentSecondary)
                            }
                        }
                    }
                    SpringButton(onClick = onOpenSettings) {
                        Surface(
                            color = P.bgCard,
                            border = BorderStroke(1.dp, P.borderSubtle),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚙", fontSize = 18.sp, color = P.textPrimary)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                tutorialBounds["HOME_WEEKLY"] = coords.boundsInRoot()
                            },
                        color = P.bgCard,
                        border = BorderStroke(1.dp, P.borderSubtle),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "WEEKLY MICROCYCLE TARGET",
                                        color = P.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.4.sp
                                    )
                                    Text(
                                        text = if (weeklyProgress >= 1f) "Weekly Goal Crushed ⚡" else "Keep the Momentum",
                                        color = P.textPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (currentWeek.workouts.isNotEmpty()) {
                                        SpringButton(
                                            scaleTo = 0.92f,
                                            onClick = {
                                                val latest = currentWeek.workouts.first()
                                                shareAiWorkoutReport(
                                                    context = context,
                                                    splitTitle = latest.splitTitle,
                                                    durationSeconds = latest.durationSeconds,
                                                    exercises = latest.exercises,
                                                    history = allHistory,
                                                    bodyWeightKg = userProfile.currentWeightKg,
                                                    photoPath = latest.photoPath
                                                )
                                            }
                                        ) {
                                            Surface(
                                                color = P.bgControl,
                                                border = BorderStroke(1.dp, P.accentSecondary),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    text = "📤 Share",
                                                    color = P.accentSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                    Surface(color = P.accentPrimary, shape = RoundedCornerShape(10.dp)) {
                                        Text(
                                            text = "${currentWeek.completedCoreSplits.size} / $targetCount",
                                            color = P.onAccentPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            AnimatedProgressBar(
                                progress = weeklyProgress,
                                height = 8.dp,
                                bgColor = P.bgControl,
                                fillColor = P.accentPrimary,
                                modifier = Modifier.padding(vertical = 14.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (weeklyProgress >= 1f) "Bonus session unlocked" else "$sessionsLeft session${if (sessionsLeft == 1) "" else "s"} remaining this week",
                                    color = P.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "NEXT: ${currentWeek.recommendedSplit.uppercase()}",
                                    color = P.accentSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                item {
                    AiMuscleFatigueCard(
                        history = allHistory,
                        P = P,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            tutorialBounds["HOME_FATIGUE"] = coords.boundsInRoot()
                        }
                    )
                }

                itemsIndexed(splits) { idx, split ->
                    val isRecommended = split.title == currentWeek.recommendedSplit
                    val wasDoneThisWeek = currentWeek.workouts.any { it.splitTitle == split.title }

                    SpringButton(
                        scaleTo = 0.96f,
                        onClick = { onSelectWorkout(split) },
                        modifier = if (idx == 0) {
                            Modifier.onGloballyPositioned { coords ->
                                tutorialBounds["HOME_SPLIT_FIRST"] = coords.boundsInRoot()
                            }
                        } else Modifier
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = P.bgCard,
                            border = BorderStroke(if (isRecommended) 1.5.dp else 1.dp, if (isRecommended) P.accentPrimary else P.borderSubtle),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isRecommended) P.accentPrimary else P.bgControl,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = split.code,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isRecommended) P.onAccentPrimary else P.textSecondary
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(split.title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = P.textPrimary)
                                        if (isRecommended) {
                                            Surface(
                                                color = P.bgControl,
                                                border = BorderStroke(1.dp, P.accentPrimary),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "UP NEXT",
                                                    color = P.accentPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (wasDoneThisWeek) {
                                            Surface(color = P.successSurface, shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    text = "✓ LOGGED",
                                                    color = P.successText,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = split.subtitle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = P.textSecondary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Text(
                                    text = "→",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isRecommended) P.accentPrimary else P.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpringButton(scaleTo = 0.96f, onClick = onOpenWeightTracker) {
                    Surface(
                        color = P.bgCard,
                        border = BorderStroke(1.dp, P.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("⚖️ LOG WEIGHT", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = P.accentSecondary)
                        }
                    }
                }
                SpringButton(scaleTo = 0.97f, onClick = onOpenWeeklySummary, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = P.bgCard,
                        border = BorderStroke(1.dp, P.borderSubtle),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("WEEKLY SUMMARY & LOGS", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = P.textPrimary)
                            Text("→", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = P.accentPrimary)
                        }
                    }
                }
            }
        }

        if (showHomeTutorial) {
            SpotlightTutorialOverlay(
                steps = homeTutorialSteps,
                currentStepIndex = tutorialStepIndex,
                targetBounds = tutorialBounds,
                P = P,
                onNext = {
                    if (tutorialStepIndex < homeTutorialSteps.lastIndex) {
                        tutorialStepIndex++
                    } else {
                        TutorialPrefs.markHomeTutorialSeen(context)
                        showHomeTutorial = false
                    }
                },
                onPrev = {
                    if (tutorialStepIndex > 0) tutorialStepIndex--
                },
                onSkip = {
                    TutorialPrefs.markHomeTutorialSeen(context)
                    showHomeTutorial = false
                }
            )
        }
    }
}

// ============================================================================
// 3. DAILY BODY WEIGHT TRACKER SCREEN
// ============================================================================

@Composable
fun WeightTrackerScreen(
    userProfile: UserProfile,
    weightHistory: List<WeightLogEntry>,
    onHaptic: () -> Unit,
    onLogWeight: (Float) -> Unit,
    onDeleteWeight: (Long) -> Unit,
    onBack: () -> Unit
) {
    val P = LocalPalette.current
    var todayWeightInput by remember { mutableStateOf(if (userProfile.currentWeightKg > 0) formatKgClean(userProfile.currentWeightKg) else "") }

    val chartPoints = weightHistory.take(6).reversed().map {
        it.displayDate.substringBefore(",") to it.weightKg
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpringButton(onClick = { onHaptic(); onBack() }) {
                Text("← HOME", color = P.accentPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("BODY WEIGHT LOG", fontSize = 18.sp, fontWeight = FontWeight.Black, color = P.textPrimary)
            Spacer(modifier = Modifier.width(56.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            color = P.bgCard,
            border = BorderStroke(1.5.dp, P.accentPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("REGISTER TODAY'S WEIGH-IN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = P.accentPrimary, letterSpacing = 1.2.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StepperControl(
                        value = todayWeightInput,
                        allowKeyboardTyping = true,
                        onValueChange = { todayWeightInput = it },
                        onStepDown = {
                            onHaptic()
                            val cur = todayWeightInput.toFloatOrNull() ?: userProfile.currentWeightKg
                            todayWeightInput = formatKgClean(cur - 0.5f)
                        },
                        onStepUp = {
                            onHaptic()
                            val cur = todayWeightInput.toFloatOrNull() ?: userProfile.currentWeightKg
                            todayWeightInput = formatKgClean(cur + 0.5f)
                        },
                        P = P,
                        modifier = Modifier.weight(1f)
                    )
                    SpringButton(
                        scaleTo = 0.94f,
                        onClick = {
                            val v = todayWeightInput.toFloatOrNull()
                            if (v != null && v > 0f) onLogWeight(v)
                        }
                    ) {
                        Surface(
                            color = P.accentPrimary,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                Text("SAVE KG", color = P.onAccentPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        if (chartPoints.size >= 2) {
            Spacer(modifier = Modifier.height(14.dp))
            TrendLineMiniChart("BODY WEIGHT TREND (LAST 6 LOGS)", chartPoints, P)
        }

        Text(
            text = "WEIGH-IN HISTORY",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = P.textSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (weightHistory.isEmpty()) {
                item {
                    Text(
                        text = "No weight logs recorded yet. Log today's weight above!",
                        color = P.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp)
                    )
                }
            } else {
                itemsIndexed(weightHistory) { idx, entry ->
                    val prevEntry = weightHistory.getOrNull(idx + 1)
                    val diff = if (prevEntry != null) (Math.round((entry.weightKg - prevEntry.weightKg) * 10f) / 10f) else 0f
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = P.bgCard,
                        border = BorderStroke(1.dp, P.borderSubtle),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${formatKgClean(entry.weightKg)} kg", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = P.textPrimary)
                                Text(entry.displayDate, fontSize = 11.sp, color = P.textSecondary, modifier = Modifier.padding(top = 2.dp))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (prevEntry != null) {
                                    Surface(color = P.bgControl, shape = RoundedCornerShape(6.dp)) {
                                        val diffColor = if (diff > 0f) P.accentPrimary else if (diff < 0f) P.accentSecondary else P.textSecondary
                                        val diffStr = if (diff > 0f) "+${diff}kg" else if (diff < 0f) "${diff}kg" else "0.0kg"
                                        Text(
                                            text = diffStr,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = diffColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                SpringButton(scaleTo = 0.9f, onClick = { onHaptic(); onDeleteWeight(entry.id) }) {
                                    Text("✕", color = P.dangerText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. WEEKLY SUMMARY & HISTORY SCREEN
// ============================================================================

@Composable
fun WeeklySummaryScreen(
    history: List<CompletedWorkout>,
    userProfile: UserProfile,
    onEditWorkout: (CompletedWorkout) -> Unit,
    onDeleteWorkout: (Long) -> Unit,
    onBackHome: () -> Unit,
    onOpenMonthlyCalendar: () -> Unit
) {
    val P = LocalPalette.current
    var weekOffset by remember { mutableIntStateOf(0) }
    val weekData = remember(history, weekOffset, userProfile.splitProgram) {
        computeWeekSummary(history, weekOffset, userProfile.splitProgram)
    }
    var workoutPendingDelete by remember { mutableStateOf<CompletedWorkout?>(null) }
    var fullScreenPhotoPath by remember { mutableStateOf<String?>(null) }

    val totalWorkSets = remember(weekData) {
        var count = 0
        for (w in weekData.workouts) {
            for (ex in w.exercises) {
                count += ex.sets.count { !it.isWarmup && it.intensityType != "WARMUP" && parseRepsClean(it.reps, 0) > 0 }
            }
        }
        count
    }

    val totalVolumeKg = remember(weekData) {
        var vol = 0.0f
        for (w in weekData.workouts) {
            for (ex in w.exercises) {
                for (s in ex.sets) {
                    if (!s.isWarmup && s.intensityType != "WARMUP") {
                        val kg = parseWeightClean(s.weight, 0f)
                        val reps = parseRepsClean(s.reps, 0)
                        vol += (kg * reps)
                    }
                }
            }
        }
        vol.toInt()
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
                    val programInfo = SPLIT_PROGRAMS[userProfile.splitProgram] ?: SPLIT_PROGRAMS["PPL_ARNOLD"]!!
                    programInfo.splits.map { it.title }.take(3).forEach { coreSplit ->
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

        Spacer(modifier = Modifier.height(12.dp))

        AiMuscleFatigueCard(history = history, P = P, compact = true)

        Spacer(modifier = Modifier.height(12.dp))

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
                            allHistory = history,
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
    allHistory: List<CompletedWorkout> = emptyList(),
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onViewPhoto: (String) -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val photoBitmap = remember(workout.photoPath) {
        PhotoStorage.loadImageBitmap(workout.photoPath)
    }

    val exerciseCount = remember(workout.exercises) {
        var count = 0
        for (ex in workout.exercises) {
            if (ex.sets.any { it.weight.isNotBlank() || it.reps.isNotBlank() }) {
                count++
            }
        }
        count.coerceAtLeast(workout.exercises.size)
    }

    val totalSetsCount = remember(workout.exercises) {
        var count = 0
        for (ex in workout.exercises) {
            count += ex.sets.count { !it.isWarmup && it.intensityType != "WARMUP" && parseRepsClean(it.reps, 0) > 0 }
        }
        count
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
                            .clickable {
                                shareAiWorkoutReport(
                                    context = context,
                                    splitTitle = workout.splitTitle,
                                    durationSeconds = workout.durationSeconds,
                                    exercises = workout.exercises,
                                    history = allHistory,
                                    bodyWeightKg = workout.bodyWeightKg,
                                    photoPath = workout.photoPath
                                )
                            },
                        color = P.accentSecondary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            text = "Share ↗",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = P.accentSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

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

            if (workout.skippedExercises.isNotEmpty()) {
                Text(
                    text = "⏭ Skipped: ${workout.skippedExercises.joinToString(", ")}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = P.warningText,
                    modifier = Modifier.padding(top = 8.dp)
                )
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

// ============================================================================
// 5. MONTHLY CALENDAR SCREEN
// ============================================================================

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
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                )
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
                        allHistory = history,
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

// ============================================================================
// 6. SETTINGS SCREEN (Optimized with Reusable Section Card)
// ============================================================================

@Composable
private fun SettingsSectionCard(
    title: String,
    P: GymPalette,
    titleColor: Color = P.textPrimary,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = P.bgCard,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = titleColor
            )
            content()
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    userProfile: UserProfile,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateProfile: (UserProfile) -> Unit,
    onOpenOnboarding: () -> Unit,
    onDataRestored: () -> Unit,
    onBack: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val json = LocalStorage.exportJsonBackupString(context)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                if (LocalStorage.importJsonBackupString(context, content)) {
                    onDataRestored()
                    Toast.makeText(context, "Backup restored!", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpringButton(onClick = onBack) {
                Text("← BACK", color = P.accentPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("SETTINGS & PROFILE", fontSize = 18.sp, fontWeight = FontWeight.Black, color = P.textPrimary)
            Spacer(modifier = Modifier.width(56.dp))
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // REPLAY INTERACTIVE TUTORIAL BUTTON
            SpringButton(
                scaleTo = 0.96f,
                onClick = {
                    TutorialPrefs.resetAllTutorials(context)
                    Toast.makeText(context, "Interactive Tutorial reset! Launching guide...", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = P.bgCard,
                    border = BorderStroke(1.5.dp, P.accentSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "🎯 REPLAY INTERACTIVE APP TUTORIAL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = P.accentSecondary
                        )
                    }
                }
            }

            SpringButton(scaleTo = 0.96f, onClick = onOpenOnboarding) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = P.bgCard,
                    border = BorderStroke(1.5.dp, P.accentPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text("⚡ RE-OPEN STARTING SETUP SCREEN", fontSize = 13.sp, fontWeight = FontWeight.Black, color = P.accentPrimary)
                    }
                }
            }

            SettingsSectionCard(
                title = "ATHLETE PROFILE & TRAINING SPLIT",
                P = P,
                titleColor = P.accentPrimary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = P.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                        StepperControl(
                            value = userProfile.age.toString(),
                            allowKeyboardTyping = true,
                            onValueChange = { onUpdateProfile(userProfile.copy(age = it.toIntOrNull() ?: 0)) },
                            onStepDown = { onUpdateProfile(userProfile.copy(age = maxOf(10, userProfile.age - 1))) },
                            onStepUp = { onUpdateProfile(userProfile.copy(age = userProfile.age + 1)) },
                            P = P
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BODY WEIGHT (KG)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = P.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                        StepperControl(
                            value = formatKgClean(userProfile.currentWeightKg),
                            allowKeyboardTyping = true,
                            onValueChange = { onUpdateProfile(userProfile.copy(currentWeightKg = it.toFloatOrNull() ?: 0f)) },
                            onStepDown = { onUpdateProfile(userProfile.copy(currentWeightKg = maxOf(20f, userProfile.currentWeightKg - 0.5f))) },
                            onStepUp = { onUpdateProfile(userProfile.copy(currentWeightKg = userProfile.currentWeightKg + 0.5f)) },
                            P = P
                        )
                    }
                }
                Text("ACTIVE SPLIT PROGRAM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = P.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SPLIT_PROGRAMS.keys.forEach { progKey ->
                        val prog = SPLIT_PROGRAMS[progKey]!!
                        val isSelected = userProfile.splitProgram == progKey
                        SpringButton(scaleTo = 0.97f, onClick = { onUpdateProfile(userProfile.copy(splitProgram = progKey)) }) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isSelected) P.accentPrimary else P.bgControl,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(prog.name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (isSelected) P.onAccentPrimary else P.textPrimary)
                                    Text("${prog.weeklyGoal}d / wk", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (isSelected) P.onAccentPrimary else P.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(title = "THEME PALETTE", P = P) {
                Spacer(modifier = Modifier.height(10.dp))
                val themeOptions = listOf(
                    listOf("OBSIDIAN" to "Obsidian Volt", "ARCTIC" to "Arctic Light"),
                    listOf("CYBERPUNK" to "Cyberpunk Neon", "OLED" to "Midnight OLED")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    themeOptions.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (themeKey, label) ->
                                val selected = settings.themeChoice == themeKey
                                SpringButton(
                                    scaleTo = 0.95f,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onUpdateSettings(
                                            settings.copy(
                                                isDarkMode = themeKey != "ARCTIC",
                                                themeChoice = themeKey
                                            )
                                        )
                                    }
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        color = if (selected) P.accentPrimary else P.bgControl,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (selected) P.onAccentPrimary else P.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(title = "DISPLAY REFRESH RATE", P = P) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(120, 60).forEach { fps ->
                        SpringButton(
                            scaleTo = 0.95f,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateSettings(settings.copy(targetFps = fps)) }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                color = if (settings.targetFps == fps) P.accentPrimary else P.bgControl,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("$fps Hz", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (settings.targetFps == fps) P.onAccentPrimary else P.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(title = "MACHINE & DUMBBELL WEIGHT INCREMENTS (+ / −)", P = P) {
                val stepPresets = listOf(0.5f, 1.0f, 1.25f, 2.0f, 2.5f, 5.0f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stepPresets.take(3).forEach { step ->
                            SpringButton(
                                scaleTo = 0.94f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp),
                                onClick = { onUpdateSettings(settings.copy(weightStepKg = step)) }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    color = if (settings.weightStepKg == step) P.accentPrimary else P.bgControl,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${step}kg", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (settings.weightStepKg == step) P.onAccentPrimary else P.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stepPresets.takeLast(3).forEach { step ->
                            SpringButton(
                                scaleTo = 0.94f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                                onClick = { onUpdateSettings(settings.copy(weightStepKg = step)) }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    color = if (settings.weightStepKg == step) P.accentPrimary else P.bgControl,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${step}kg", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (settings.weightStepKg == step) P.onAccentPrimary else P.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(title = "REST TIMER & ALERT SOUND", P = P) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "Off", 60 to "60s", 90 to "90s", 120 to "2m", 180 to "3m").forEach { (sec, lbl) ->
                        SpringButton(
                            scaleTo = 0.94f,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateSettings(settings.copy(restTimerSeconds = sec)) }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                color = if (settings.restTimerSeconds == sec) P.accentPrimary else P.bgControl,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(lbl, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (settings.restTimerSeconds == sec) P.onAccentPrimary else P.textPrimary)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(true to "Music-Safe Alert On", false to "Silent").forEach { (valOn, label) ->
                        SpringButton(
                            scaleTo = 0.95f,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateSettings(settings.copy(restSoundEnabled = valOn)) }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                color = if (settings.restSoundEnabled == valOn) P.accentPrimary else P.bgControl,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (settings.restSoundEnabled == valOn) P.onAccentPrimary else P.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(title = "KEEP SCREEN ON DURING WORKOUT", P = P) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(true to "Keep Awake", false to "Normal Timeout").forEach { (valOn, label) ->
                        SpringButton(
                            scaleTo = 0.95f,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateSettings(settings.copy(keepScreenAwake = valOn)) }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                color = if (settings.keepScreenAwake == valOn) P.accentPrimary else P.bgControl,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (settings.keepScreenAwake == valOn) P.onAccentPrimary else P.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(title = "VIBRATION FEEDBACK (HAPTICS)", P = P) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(true to "On", false to "Off").forEach { (valOn, label) ->
                        SpringButton(
                            scaleTo = 0.95f,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateSettings(settings.copy(hapticsEnabled = valOn)) }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                color = if (settings.hapticsEnabled == valOn) P.accentPrimary else P.bgControl,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (settings.hapticsEnabled == valOn) P.onAccentPrimary else P.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(title = "BACKUP & RESTORE DATA", P = P) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpringButton(
                        scaleTo = 0.95f,
                        modifier = Modifier.weight(1f),
                        onClick = { exportLauncher.launch("gym_tracker_backup.json") }
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            color = P.bgControl,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Export Backup", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = P.accentPrimary)
                            }
                        }
                    }
                    SpringButton(
                        scaleTo = 0.95f,
                        modifier = Modifier.weight(1f),
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            color = P.bgControl,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Restore Backup", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = P.accentSecondary)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}