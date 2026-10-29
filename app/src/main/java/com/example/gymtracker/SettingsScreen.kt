package com.example.gymtracker

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LinkOff
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
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    userProfile: UserProfile,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateProfile: (UserProfile) -> Unit,
    onOpenOnboarding: () -> Unit,
    onReplayTips: () -> Unit,
    onDataRestored: () -> Unit,
    onEditProgram: (String) -> Unit,
    onBuildSplit: (String) -> Unit
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
                    LabeledStepper("Weight (${settings.weightUnit.label})", Modifier.weight(1f)) {
                        WeightStepper(
                            storedKg = if (userProfile.currentWeightKg > 0f) userProfile.currentWeightKg.toString() else "",
                            onChange = { onUpdateProfile(userProfile.copy(currentWeightKg = it.toFloatOrNull() ?: 0f)) },
                            step = settings.weightUnit.bodyStep,
                            minKg = 20f,
                            fallbackKg = 75f
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Training split", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                val current = programFor(userProfile.splitProgram)
                SplitPicker(
                    selectedId = current.id,
                    onSelect = { onUpdateProfile(userProfile.copy(splitProgram = it.id)) },
                    onBuild = onBuildSplit,
                    onEdit = { onEditProgram(it.id) },
                    cardActionLabel = if (current.isBuiltIn) "Customize" else "Edit",
                    onCardAction = {
                        if (current.isBuiltIn) {
                            // Built-in programs stay as they are; edits go to a copy.
                            val copy = customizedCopy(current)
                            LocalStorage.saveProgram(context, copy)
                            onUpdateProfile(userProfile.copy(splitProgram = copy.id))
                            onEditProgram(copy.id)
                        } else onEditProgram(current.id)
                    }
                )
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
                Text("Units", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf(WeightUnit.KG to "Kilograms (kg)", WeightUnit.LB to "Pounds (lb)"),
                    selected = settings.weightUnit,
                    // Switching only changes how weights are shown; the step resets to the new unit's default.
                    onSelect = { if (it != settings.weightUnit) onUpdateSettings(settings.copy(weightUnit = it, weightStep = it.defaultStep)) }
                )
                Spacer(Modifier.height(16.dp))
                Text("Weight buttons change by", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(
                    options = settings.weightUnit.stepPresets.map { it to "${formatKgClean(it)} ${settings.weightUnit.label}" },
                    selected = settings.weightStep,
                    onSelect = { onUpdateSettings(settings.copy(weightStep = it)) }
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

        item { HealthConnectGroup(onImported = onDataRestored) }

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
private fun HealthConnectGroup(onImported: () -> Unit) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val availability = remember { HealthSync.availability(context) }
    var on by remember { mutableStateOf(LocalStorage.isHealthSyncOn(context)) }
    val granted = HealthSync.granted
    LaunchedEffect(Unit) { HealthSync.refresh(context) }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Couldn't open Health Connect", Toast.LENGTH_SHORT).show()
        }
    }

    val askPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { _ ->
        scope.launch {
            HealthSync.refresh(context)
            if (HealthSync.granted.isEmpty()) {
                // Android stops showing the dialog after two refusals; access can still be given in Health Connect.
                Toast.makeText(context, "Nothing was allowed. You can allow it in Health Connect.", Toast.LENGTH_LONG).show()
                return@launch
            }
            LocalStorage.setHealthSyncOn(context, true)
            on = true
            HealthSync.shareExisting(context)
            if (HealthSync.importWeights(context)) onImported()
        }
    }

    SettingsGroup("Health Connect") {
        when (availability) {
            HealthSync.Availability.UNAVAILABLE ->
                Text("Health Connect isn't available on this phone.", style = AppType.caption, color = P.textTertiary)
            HealthSync.Availability.NEEDS_INSTALL -> {
                Text(
                    "Install or update Health Connect to share workouts and body weight with Samsung Health and Google Fit.",
                    style = AppType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = P.textSecondary
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Get Health Connect", { open(HealthSync.installIntent()) }, Modifier.fillMaxWidth())
            }
            HealthSync.Availability.AVAILABLE -> if (!on || granted.isEmpty()) {
                Text(
                    "Share your workouts and body weight with Samsung Health, Google Fit and other apps, bring in weigh-ins from your scale, and see your watch's heart rate in your history.",
                    style = AppType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = P.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Connect", { askPermissions.launch(HealthSync.PERMISSIONS) }, Modifier.fillMaxWidth())
                LinkRow(Icons.AutoMirrored.Rounded.OpenInNew, "Open Health Connect") { open(HealthSync.manageIntent(context)) }
            } else {
                SharedRow("Workouts you finish", HealthSync.WRITE_WORKOUTS in granted)
                SharedRow("Body weight you log", HealthSync.WRITE_WEIGHT in granted)
                SharedRow("Weigh-ins from scales and other apps", HealthSync.READ_WEIGHT in granted)
                SharedRow("Heart rate during workouts", HealthSync.READ_HEART_RATE in granted)
                if (!granted.containsAll(HealthSync.PERMISSIONS)) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Allow the rest", { askPermissions.launch(HealthSync.PERMISSIONS) }, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                LinkRow(Icons.AutoMirrored.Rounded.OpenInNew, "Manage in Health Connect") { open(HealthSync.manageIntent(context)) }
                LinkRow(Icons.Rounded.LinkOff, "Stop sharing") {
                    HealthSync.disconnect(context)
                    on = false
                }
            }
        }
    }
}

@Composable
private fun SharedRow(label: String, shared: Boolean) {
    val P = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (shared) Icons.Rounded.Check else Icons.Rounded.Close,
            if (shared) "Shared" else "Not shared",
            tint = if (shared) P.accentPrimary else P.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = AppType.body, color = if (shared) P.textPrimary else P.textTertiary)
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
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val P = LocalPalette.current
    // Wraps instead of scrolling, so no option hides past the card's edge.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
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
        Text(text, style = AppType.body, color = P.textPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = P.textTertiary, modifier = Modifier.size(20.dp))
    }
}
