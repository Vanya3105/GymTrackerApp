package com.example.gymtracker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils as AndroidDateUtils
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
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlin.math.roundToInt

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

    var editing by remember { mutableStateOf<ProfileField?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Profile", style = AppType.display, color = P.textPrimary, modifier = Modifier.statusBarsPadding().padding(top = 20.dp))
        }

        item {
            // Plain rows that open an editor: steppers made the profile look like a form.
            SettingsGroup("You", padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ValueRow("Age", if (userProfile.age > 0) "${userProfile.age}" else "Not set") { editing = ProfileField.AGE }
                Box(Modifier.fillMaxWidth().height(1.dp).background(P.borderSubtle))
                ValueRow("Body weight", if (userProfile.currentWeightKg > 0f) formatWeightKg(userProfile.currentWeightKg) else "Not set") { editing = ProfileField.WEIGHT }
            }
        }

        item {
            SettingsGroup("Training split") {
                val current = programFor(userProfile.splitProgram)
                SplitPicker(
                    selectedId = current.id,
                    onSelect = { onUpdateProfile(userProfile.copy(splitProgram = it.id)) },
                    onBuild = onBuildSplit,
                    onEdit = { onEditProgram(it.id) },
                    cardActionLabel = if (current.isBuiltIn) "Customize" else "Edit",
                    contained = false,
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
                Text("Weight step", style = AppType.label, color = P.textSecondary)
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
                AutoBackupSection()
                Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Save backup", { exportLauncher.launch("gymtracker_backup.json") }, Modifier.weight(1f), icon = Icons.Rounded.FileDownload)
                    SecondaryButton("Restore", { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), icon = Icons.Rounded.FileUpload)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "A backup is a single file. Restore takes one from anywhere, including the backup folder, and keeps a copy of what it replaces.",
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

        // Technical options last: most people never need them.
        item {
            SettingsGroup("Advanced") {
                ToggleRow(
                    title = "Smooth scrolling",
                    subtitle = "Uses your screen's highest refresh rate (120 Hz). Turn off to save battery.",
                    checked = settings.targetFps >= 120,
                    onCheckedChange = { onUpdateSettings(settings.copy(targetFps = if (it) 120 else 60)) }
                )
            }
        }
    }

    when (editing) {
        ProfileField.AGE -> EditNumberSheet(
            title = "Age",
            initial = if (userProfile.age > 0) "${userProfile.age}" else "",
            unit = null,
            decimal = false,
            step = 1f,
            onDismiss = { editing = null },
            onSave = { v -> onUpdateProfile(userProfile.copy(age = v.toInt().coerceIn(10, 120))); editing = null }
        )
        ProfileField.WEIGHT -> EditNumberSheet(
            title = "Body weight",
            initial = if (userProfile.currentWeightKg > 0f) settings.weightUnit.number(userProfile.currentWeightKg) else "",
            unit = settings.weightUnit.label,
            decimal = true,
            step = settings.weightUnit.bodyStep,
            note = "To track a trend, log weigh-ins on the Progress tab.",
            onDismiss = { editing = null },
            onSave = { v -> onUpdateProfile(userProfile.copy(currentWeightKg = settings.weightUnit.toKg(v).coerceAtLeast(20f))); editing = null }
        )
        null -> {}
    }
}

private enum class ProfileField { AGE, WEIGHT }

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    val P = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.99f, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppType.body, color = P.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = AppType.body.copy(fontFeatureSettings = "tnum"), color = P.textSecondary)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = P.textTertiary, modifier = Modifier.size(20.dp))
    }
}

/** One number, typed or nudged, then saved. */
@Composable
private fun EditNumberSheet(
    title: String,
    initial: String,
    unit: String?,
    decimal: Boolean,
    step: Float,
    note: String? = null,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    val P = LocalPalette.current
    var input by remember { mutableStateOf(initial) }
    val value = input.toFloatOrNull()
    fun nudge(delta: Float) {
        val next = ((value ?: 0f) + delta).coerceAtLeast(0f)
        input = if (decimal) formatKgClean((next * 10).roundToInt() / 10f) else "${next.roundToInt()}"
    }
    AppBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding()) {
            Text(title, style = AppType.title, color = P.textPrimary)
            if (note != null) Text(note, style = AppType.caption, color = P.textSecondary)
            Spacer(Modifier.height(20.dp))
            NumberStepper(
                value = input,
                onValueChange = { v -> if (decimal) sanitizeDecimalInput(v)?.let { input = it } else input = v.filter(Char::isDigit).take(3) },
                onMinus = { nudge(-step) },
                onPlus = { nudge(step) },
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp
            )
            if (unit != null) Text(unit, style = AppType.caption, color = P.textTertiary, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Save", onClick = { value?.takeIf { it > 0f }?.let(onSave) }, enabled = (value ?: 0f) > 0f, modifier = Modifier.fillMaxWidth())
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

/** Off: what it does and "Choose a folder". On: the folder, the last backup or what went wrong. */
@Composable
private fun AutoBackupSection() {
    val P = LocalPalette.current
    val context = LocalContext.current
    val status = AutoBackup.status
    LaunchedEffect(Unit) { AutoBackup.refreshStatus(context) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            try {
                AutoBackup.setFolder(context, tree)
            } catch (_: SecurityException) {
                Toast.makeText(context, "That folder can't be used for backups. Try another one.", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun choose() {
        try {
            pickFolder.launch(null)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No file picker on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    if (!status.isOn) {
        Text("Automatic backup", style = AppType.bodyStrong, color = P.textPrimary)
        Text(
            "Saves a copy to a folder you choose after every workout. Pick a Google Drive or OneDrive folder and a new phone can get everything back.",
            style = AppType.caption,
            color = P.textSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Choose a folder", { choose() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.FolderOpen)
        return
    }

    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (status.error != null) Icons.Rounded.ErrorOutline else Icons.Rounded.CloudDone,
            null,
            tint = if (status.error != null) P.dangerText else P.successText,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Automatic backup is on", style = AppType.bodyStrong, color = P.textPrimary)
            Text("Folder: ${status.folderName}", style = AppType.caption, color = P.textSecondary)
            Text(
                when {
                    status.running -> "Backing up…"
                    status.error != null -> status.error
                    status.lastSuccessMillis > 0L -> "Last backup " + AndroidDateUtils.formatDateTime(
                        context, status.lastSuccessMillis,
                        AndroidDateUtils.FORMAT_SHOW_DATE or AndroidDateUtils.FORMAT_SHOW_TIME or AndroidDateUtils.FORMAT_ABBREV_MONTH
                    )
                    else -> "No backup yet"
                },
                style = AppType.caption,
                color = if (status.error != null && !status.running) P.dangerText else P.textSecondary
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Back up now", { AutoBackup.backUpNow(context) }, Modifier.weight(1f), enabled = !status.running)
        SecondaryButton("Change folder", { choose() }, Modifier.weight(1f))
    }
    var confirmOff by remember { mutableStateOf(false) }
    LinkRow(Icons.Rounded.CloudOff, "Turn off automatic backup") { confirmOff = true }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            containerColor = P.bgElevated,
            title = { Text("Turn off automatic backup?", style = AppType.headline, color = P.textPrimary) },
            text = { Text("Backups already in the folder stay there, but new workouts won't be copied.", style = AppType.body, color = P.textSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmOff = false; AutoBackup.turnOff(context) }) { Text("Turn off", color = P.dangerText, style = AppType.label) }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Keep it on", color = P.textSecondary, style = AppType.label) } }
        )
    }
}

@Composable
private fun SharedRow(label: String, shared: Boolean) {
    val P = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (shared) Icons.Rounded.Check else Icons.Rounded.Close,
            if (shared) "Shared" else "Not shared",
            tint = if (shared) P.successText else P.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = AppType.body, color = if (shared) P.textPrimary else P.textTertiary)
    }
}

@Composable
private fun SettingsGroup(title: String, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    val P = LocalPalette.current
    Column {
        Text(title, style = AppType.overline, color = P.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        AppCard(Modifier.fillMaxWidth(), contentPadding = padding, content = content)
    }
}

@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val P = LocalPalette.current
    // Wraps instead of scrolling, so no option hides past the card's edge.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            // Selected reads as inverted rather than orange: the accent is for actions.
            val bg by animateColorAsState(if (isSel) P.textPrimary else P.bgControl, label = "chip_bg")
            val fg by animateColorAsState(if (isSel) P.bgPrimary else P.textPrimary, label = "chip_fg")
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
