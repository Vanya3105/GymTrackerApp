package com.example.gymtracker

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    settings: AppSettings,
    userProfile: UserProfile,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateProfile: (UserProfile) -> Unit,
    onOpenOnboarding: () -> Unit,
    onReplayTips: () -> Unit,
    onDataRestored: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val json = LocalStorage.exportJsonBackupString(context)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Couldn't save the backup", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (LocalStorage.importJsonBackupString(context, content)) {
                    onDataRestored()
                    Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "That file isn't a GymTracker backup", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Profile", style = AppType.display, color = P.textPrimary, modifier = Modifier.statusBarsPadding().padding(top = 20.dp))
        }

        item {
            SettingsGroup("You") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledStepper("Age", Modifier.weight(1f)) {
                        NumberStepper(
                            value = if (userProfile.age > 0) userProfile.age.toString() else "",
                            onValueChange = { v -> onUpdateProfile(userProfile.copy(age = v.filter(Char::isDigit).take(3).toIntOrNull() ?: 0)) },
                            onMinus = { onUpdateProfile(userProfile.copy(age = maxOf(10, userProfile.age - 1))) },
                            onPlus = { onUpdateProfile(userProfile.copy(age = userProfile.age + 1)) },
                            keyboardType = KeyboardType.Number
                        )
                    }
                    LabeledStepper("Weight (kg)", Modifier.weight(1f)) {
                        NumberStepper(
                            value = formatKgClean(userProfile.currentWeightKg),
                            onValueChange = { v ->
                                sanitizeDecimalInput(v)?.let { onUpdateProfile(userProfile.copy(currentWeightKg = it.toFloatOrNull() ?: 0f)) }
                            },
                            onMinus = { onUpdateProfile(userProfile.copy(currentWeightKg = maxOf(20f, userProfile.currentWeightKg - 0.5f))) },
                            onPlus = { onUpdateProfile(userProfile.copy(currentWeightKg = userProfile.currentWeightKg + 0.5f)) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Training split", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                SPLIT_PROGRAMS.values.forEach { prog ->
                    ProgramOption(prog, selected = userProfile.splitProgram == prog.id) {
                        onUpdateProfile(userProfile.copy(splitProgram = prog.id))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        item {
            SettingsGroup("Appearance") {
                SegmentedControl(
                    options = listOf(THEME_SYSTEM to "System", THEME_LIGHT to "Light", THEME_DARK to "Dark"),
                    selected = settings.themeChoice,
                    onSelect = { onUpdateSettings(settings.copy(themeChoice = it)) }
                )
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "Smooth scrolling",
                    subtitle = "Uses your screen's highest refresh rate (120 Hz)",
                    checked = settings.targetFps >= 120,
                    onCheckedChange = { onUpdateSettings(settings.copy(targetFps = if (it) 120 else 60)) }
                )
            }
        }

        item {
            SettingsGroup("Workouts") {
                Text("Weight buttons change by", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(
                    options = WEIGHT_STEP_PRESETS.map { it to "${formatKgClean(it)} kg" },
                    selected = settings.weightStepKg,
                    onSelect = { onUpdateSettings(settings.copy(weightStepKg = it)) }
                )
                Spacer(Modifier.height(16.dp))
                Text("Default rest between sets", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(
                    options = listOf(0 to "Off", 60 to "1 min", 90 to "1:30", 120 to "2 min", 180 to "3 min", 240 to "4 min"),
                    selected = settings.restTimerSeconds,
                    onSelect = { onUpdateSettings(settings.copy(restTimerSeconds = it)) }
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    "Sound when rest ends",
                    "A short beep that lowers your music instead of stopping it",
                    settings.restSoundEnabled
                ) { onUpdateSettings(settings.copy(restSoundEnabled = it)) }
                ToggleRow("Keep screen on", "While a workout is open", settings.keepScreenAwake) {
                    onUpdateSettings(settings.copy(keepScreenAwake = it))
                }
                ToggleRow("Vibration", "Light feedback when you tap", settings.hapticsEnabled) {
                    onUpdateSettings(settings.copy(hapticsEnabled = it))
                }
            }
        }

        item {
            SettingsGroup("Your data") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Save backup", { exportLauncher.launch("gymtracker_backup.json") }, Modifier.weight(1f), icon = Icons.Rounded.FileDownload)
                    SecondaryButton("Restore", { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), icon = Icons.Rounded.FileUpload)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Everything stays on this phone. A backup is a single file you can keep anywhere.",
                    style = AppType.caption,
                    color = P.textTertiary
                )
            }
        }

        item {
            SettingsGroup("Help") {
                LinkRow(Icons.Rounded.Lightbulb, "Show tips again") {
                    TutorialPrefs.resetAllTutorials(context)
                    onReplayTips()
                }
                LinkRow(Icons.Rounded.Tune, "Redo first-time setup", onOpenOnboarding)
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Widgets, null, tint = P.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Widgets: add them from your home screen. Long-press a widget and choose its settings to change the theme, transparency and what it shows.",
                        style = AppType.caption,
                        color = P.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val P = LocalPalette.current
    Column {
        Text(title, style = AppType.overline, color = P.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        AppCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun LabeledStepper(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val P = LocalPalette.current
    Column(modifier) {
        Text(label, style = AppType.label, color = P.textSecondary)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
fun ProgramOption(prog: SplitProgramInfo, selected: Boolean, onClick: () -> Unit) {
    val P = LocalPalette.current
    val border by animateColorAsState(if (selected) P.accentPrimary else P.borderSubtle, label = "prog_border")
    val bg by animateColorAsState(if (selected) P.accentSoft else P.bgCard.copy(alpha = 0f), label = "prog_bg")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(prog.name, style = AppType.bodyStrong, color = P.textPrimary)
            Text("${prog.tagline} · ${prog.weeklyGoal} days a week", style = AppType.caption, color = P.textSecondary)
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) P.accentPrimary else P.bgControl),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = P.onAccentPrimary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val P = LocalPalette.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (value, label) ->
            val isSel = value == selected
            val bg by animateColorAsState(if (isSel) P.accentPrimary else P.bgControl, label = "chip_bg")
            val fg by animateColorAsState(if (isSel) P.onAccentPrimary else P.textPrimary, label = "chip_fg")
            Text(
                label,
                style = AppType.label,
                color = fg,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .pressable(pressedScale = 0.94f) { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val P = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(pressedScale = 0.99f, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = P.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = AppType.body, color = P.textPrimary)
    }
}
