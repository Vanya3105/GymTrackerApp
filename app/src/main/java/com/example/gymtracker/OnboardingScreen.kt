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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    initialProfile: UserProfile,
    canGoBack: Boolean,
    haptics: Haptics,
    onBack: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit
) {
    val P = LocalPalette.current
    var step by remember { mutableIntStateOf(0) }
    var age by remember { mutableStateOf(if (initialProfile.age > 0) initialProfile.age.toString() else "25") }
    var weight by remember { mutableStateOf(if (initialProfile.currentWeightKg > 0f) formatKgClean(initialProfile.currentWeightKg) else "75") }
    var program by remember { mutableStateOf(initialProfile.splitProgram) }

    BackHandler(enabled = step > 0 || canGoBack) {
        if (step > 0) step-- else onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0 || canGoBack) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { if (step > 0) step-- else onBack() }, container = Color.Transparent)
            } else {
                Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.weight(1f))
            StepDots(count = 2, current = step)
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
                    Text("Welcome", style = AppType.display, color = P.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A couple of quick questions so the app can track your progress and suggest weights.",
                        style = AppType.body,
                        color = P.textSecondary
                    )
                    Spacer(Modifier.height(32.dp))
                    Text("How old are you?", style = AppType.label, color = P.textSecondary)
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
                    Text("What do you weigh? (kg)", style = AppType.label, color = P.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    NumberStepper(
                        value = weight,
                        onValueChange = { v -> sanitizeDecimalInput(v)?.let { weight = it } },
                        onMinus = { weight = formatKgClean(((weight.toFloatOrNull() ?: 75f) - 0.5f).coerceAtLeast(20f)) },
                        onPlus = { weight = formatKgClean((weight.toFloatOrNull() ?: 75f) + 0.5f) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 56.dp
                    )
                } else {
                    Text("How do you train?", style = AppType.display, color = P.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text("Pick the split closest to yours. You can change it any time in Profile.", style = AppType.body, color = P.textSecondary)
                    Spacer(Modifier.height(24.dp))
                    SPLIT_PROGRAMS.values.forEach { prog ->
                        ProgramOption(prog, selected = program == prog.id) {
                            haptics.tick()
                            program = prog.id
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        val ageOk = (age.toIntOrNull() ?: 0) > 0
        val weightOk = (weight.toFloatOrNull() ?: 0f) > 0f
        PrimaryButton(
            text = if (step == 0) "Continue" else "Start training",
            enabled = if (step == 0) ageOk && weightOk else true,
            onClick = {
                haptics.tick()
                if (step == 0) step = 1
                else onSaveProfile(
                    UserProfile(
                        hasCompletedOnboarding = true,
                        age = age.toIntOrNull() ?: 0,
                        currentWeightKg = weight.toFloatOrNull() ?: 0f,
                        splitProgram = program
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (step == 0) {
            Text(
                "Stored only on this phone.",
                style = AppType.caption.copy(fontWeight = FontWeight.Normal),
                color = P.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun StepDots(count: Int, current: Int) {
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
