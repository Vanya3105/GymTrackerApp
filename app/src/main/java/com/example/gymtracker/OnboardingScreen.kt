package com.example.gymtracker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    initialProfile: UserProfile,
    unit: WeightUnit,
    onSelectUnit: (WeightUnit) -> Unit,
    canGoBack: Boolean,
    haptics: Haptics,
    onBack: () -> Unit,
    /** Opens the split builder; a split built there becomes [initialProfile]'s split. */
    onBuildSplit: (String) -> Unit,
    onSaveProfile: (UserProfile) -> Unit,
    /** 1 opens straight on the training questions (from Profile). */
    startStep: Int = 0
) {
    val P = LocalPalette.current
    var step by remember { mutableIntStateOf(startStep) }
    var age by remember { mutableStateOf(if (initialProfile.age > 0) initialProfile.age.toString() else "25") }
    // Kept in kg; the field shows and takes the chosen unit.
    var weightKg by remember { mutableStateOf(if (initialProfile.currentWeightKg > 0f) initialProfile.currentWeightKg.toString() else "75") }
    // Keyed so a split made in the builder is picked here too.
    var program by remember(initialProfile.splitProgram) { mutableStateOf(initialProfile.splitProgram) }
    var experience by remember { mutableStateOf(initialProfile.experience) }
    var goal by remember { mutableStateOf(initialProfile.goal) }
    var equipment by remember { mutableStateOf(initialProfile.equipment) }
    var days by remember { mutableIntStateOf(initialProfile.daysPerWeek.takeIf { it > 0 } ?: 3) }
    var limits by remember { mutableStateOf(initialProfile.limits) }
    // Picked by hand on the last step, so a recommendation no longer replaces it.
    var splitChosen by remember { mutableStateOf(initialProfile.hasCompletedOnboarding) }
    fun answers() = initialProfile.copy(
        hasCompletedOnboarding = true,
        age = age.toIntOrNull() ?: 0,
        currentWeightKg = weightKg.toFloatOrNull() ?: 0f,
        splitProgram = program,
        experience = experience, goal = goal, equipment = equipment, daysPerWeek = days, limits = limits
    )
    val recommended = recommendedProgram(answers())
    LaunchedEffect(step) {
        if (step == LAST_STEP && !splitChosen) recommended?.let { program = it.first }
    }
    val back: () -> Unit = { if (step > startStep) step-- else onBack() }

    BackHandler(enabled = step > startStep || canGoBack) { back() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > startStep || canGoBack) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"), back, container = Color.Transparent)
            } else {
                Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.weight(1f))
            StepDots(count = LAST_STEP + 1 - startStep, current = step - startStep)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it / 4 * dir } + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 4 * dir } + fadeOut(tween(150)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding_step"
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 32.dp)) {
                if (s == 0) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(P.accentSoft),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.FitnessCenter, null, tint = P.accentPrimary, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(20.dp))
                    Text(tr("Welcome"), style = AppType.display, color = P.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("A couple of quick questions so the app can track your progress and suggest weights."),
                        style = AppType.body,
                        color = P.textSecondary
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(tr("How old are you?"), style = AppType.label, color = P.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    NumberStepper(
                        value = age,
                        onValueChange = { v -> age = v.filter(Char::isDigit).take(3) },
                        onMinus = { age = ((age.toIntOrNull() ?: 25) - 1).coerceAtLeast(10).toString() },
                        onPlus = { age = ((age.toIntOrNull() ?: 25) + 1).toString() },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                        height = 56.dp
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("What do you weigh?"), style = AppType.label, color = P.textSecondary, modifier = Modifier.weight(1f))
                        SegmentedControl(
                            options = WeightUnit.entries.map { it to it.label },
                            selected = unit,
                            onSelect = { haptics.tick(); onSelectUnit(it) },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    WeightStepper(
                        storedKg = weightKg,
                        onChange = { weightKg = it },
                        step = unit.bodyStep,
                        minKg = 20f,
                        fallbackKg = 75f,
                        modifier = Modifier.fillMaxWidth(),
                        height = 56.dp
                    )
                } else if (s == 1) {
                    Text(tr("Your training"), style = AppType.display, color = P.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("So the app picks exercises, sets, reps and rest that suit you."), style = AppType.body, color = P.textSecondary)
                    Spacer(Modifier.height(24.dp))
                    Text(tr("How long have you been lifting?"), style = AppType.label, color = P.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Experience.entries.forEach { e ->
                        OptionCard(e.label, e.detail, experience == e) { haptics.tick(); experience = e }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(tr("What's your main goal?"), style = AppType.label, color = P.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    TrainingGoal.entries.forEach { g ->
                        OptionCard(g.label, g.detail, goal == g) { haptics.tick(); goal = g }
                    }
                } else if (s == 2) {
                    Text(tr("Your week"), style = AppType.display, color = P.textPrimary)
                    Spacer(Modifier.height(24.dp))
                    Text(tr("How many days a week can you train?"), style = AppType.label, color = P.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    SegmentedControl(
                        options = (2..6).map { it to "$it" },
                        selected = days,
                        onSelect = { haptics.tick(); days = it }
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(tr("Where do you train?"), style = AppType.label, color = P.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Equipment.entries.forEach { e ->
                        OptionCard(e.label, e.detail, equipment == e) { haptics.tick(); equipment = e }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(tr("Anything to go easy on?"), style = AppType.label, color = P.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    ToggleChips(Limit.entries.map { it to it.label }, limits) { l ->
                        haptics.tick()
                        limits = if (l in limits) limits - l else limits + l
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("Lifts that load it a lot are swapped for gentler ones. This isn't medical advice: if something hurts, see a professional."),
                        style = AppType.caption,
                        color = P.textTertiary
                    )
                } else {
                    Text(tr("How do you train?"), style = AppType.display, color = P.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Find your split, or build your own. You can change it any time in Profile."), style = AppType.body, color = P.textSecondary)
                    Spacer(Modifier.height(20.dp))
                    recommended?.let { (id, why) ->
                        RecommendedSplit(programFor(id).name, why, chosen = program == id) {
                            haptics.tick(); program = id; splitChosen = true
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    SplitPicker(
                        selectedId = program,
                        onSelect = { haptics.tick(); program = it.id; splitChosen = true },
                        onBuild = onBuildSplit
                    )
                }
            }
        }

        val ageOk = (age.toIntOrNull() ?: 0) > 0
        val weightOk = (weightKg.toFloatOrNull() ?: 0f) > 0f
        PrimaryButton(
            text = if (step < LAST_STEP) tr("Continue") else if (initialProfile.hasCompletedOnboarding) tr("Save") else tr("Start training"),
            enabled = when (step) {
                0 -> ageOk && weightOk
                1 -> experience != null && goal != null
                2 -> days > 0 && equipment != null
                else -> true
            },
            onClick = {
                haptics.tick()
                if (step < LAST_STEP) step++ else onSaveProfile(answers())
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (step == 0) {
            Text(
                tr("Stored only on this phone."),
                style = AppType.caption.copy(fontWeight = FontWeight.Normal),
                color = P.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
            )
        }
    }
}

private const val LAST_STEP = 3

/** One answer of a few, as a full-width card with a title and a line under it. */
@Composable
private fun OptionCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val P = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) P.accentSoft else P.bgControl)
            .border(1.5.dp, if (selected) P.accentPrimary else Color.Transparent, RoundedCornerShape(14.dp))
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.bodyStrong, color = P.textPrimary)
            Text(detail, style = AppType.caption, color = P.textSecondary)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = P.accentPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Chips that each turn on and off. */
@Composable
private fun <T> ToggleChips(options: List<Pair<T, String>>, selected: Set<T>, onToggle: (T) -> Unit) {
    val P = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value in selected
            Text(
                label,
                style = AppType.label,
                color = if (on) P.bgPrimary else P.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) P.textPrimary else P.bgControl)
                    .pressable(pressedScale = 0.94f) { onToggle(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** The split the answers point to, with why; tapping it picks it. */
@Composable
private fun RecommendedSplit(name: String, why: String, chosen: Boolean, onUse: () -> Unit) {
    val P = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(P.accentSoft)
            .pressable(enabled = !chosen, pressedScale = 0.98f, onClick = onUse)
            .padding(16.dp)
    ) {
        Text(if (chosen) tr("Recommended for you") else tr("Recommended for you: {0}", shown(name)), style = AppType.label, color = P.accentPrimary)
        Spacer(Modifier.height(4.dp))
        Text(why, style = AppType.body, color = P.textPrimary)
        if (!chosen) {
            Spacer(Modifier.height(8.dp))
            Text(tr("Tap to use it"), style = AppType.label, color = P.accentPrimary)
        }
    }
}

@Composable
internal fun StepDots(count: Int, current: Int) {
    val P = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, spring(dampingRatio = 0.7f), label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (i == current) P.accentPrimary else P.borderStrong)
            )
        }
    }
}
