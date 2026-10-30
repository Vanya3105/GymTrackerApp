package com.example.gymtracker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils as AndroidDateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.automirrored.rounded.Logout
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
import androidx.compose.material.icons.rounded.MoveToInbox
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
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Date
import org.json.JSONObject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun SettingsScreen(
    settings: AppSettings,
    userProfile: UserProfile,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateProfile: (UserProfile) -> Unit,
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
    // A picked backup waits here while the owner chooses to add it or replace everything with it.
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(content.removePrefix("\uFEFF").trim()) }.getOrNull()
                if (json != null && StoreCodec.looksLikeBackup(json)) pendingBackup = content
                else Toast.makeText(context, "That file isn't a GymTracker backup", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var otherAppFile by remember { mutableStateOf<String?>(null) }
    val otherAppLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                otherAppFile = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) {
                Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            }
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
                GoogleBackupSection(onRestoreContent = { pendingBackup = it })
                Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
                AutoBackupSection()
                Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(P.borderSubtle))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // With automatic backup on, "Back up now" above does this job.
                    if (!AutoBackup.status.isOn) {
                        SecondaryButton("Save backup", { exportLauncher.launch("gymtracker_backup.json") }, Modifier.weight(1f), icon = Icons.Rounded.FileDownload)
                    }
                    SecondaryButton("Restore", { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), icon = Icons.Rounded.FileUpload)
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    "Import from Strong or Hevy",
                    { otherAppLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.MoveToInbox
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Restore can add a backup to your data (to combine backups) or replace it. Imports from Strong and Hevy are added to your history. A copy of your data is kept first either way.",
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

    pendingBackup?.let { content ->
        RestoreChoiceDialog(
            onAdd = {
                pendingBackup = null
                val result = LocalStorage.addBackupJsonString(context, content)
                if (result != null) {
                    onDataRestored()
                    Toast.makeText(context, addedMessage(result), Toast.LENGTH_LONG).show()
                } else Toast.makeText(context, "Couldn't add that backup", Toast.LENGTH_SHORT).show()
            },
            onReplace = {
                pendingBackup = null
                if (LocalStorage.importJsonBackupString(context, content)) {
                    onDataRestored()
                    Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "That file isn't a GymTracker backup", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { pendingBackup = null }
        )
    }
    otherAppFile?.let { text ->
        OtherAppImportSheet(
            text = text,
            defaultUnit = settings.weightUnit,
            onDone = { result ->
                otherAppFile = null
                if (result != null) {
                    onDataRestored()
                    Toast.makeText(context, addedMessage(result), Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { otherAppFile = null }
        )
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

private fun addedMessage(r: CombineResult): String = buildString {
    append(if (r.addedWorkouts == 1) "Added 1 workout" else "Added ${r.addedWorkouts} workouts")
    if (r.addedWeighIns > 0) append(" and ${r.addedWeighIns} weigh-in${if (r.addedWeighIns == 1) "" else "s"}")
    if (r.alreadyHere > 0) append(" (${r.alreadyHere} ${if (r.alreadyHere == 1) "was" else "were"} already here)")
}

/** Restore used to replace everything; adding the backup to what's here now comes first. */
@Composable
private fun RestoreChoiceDialog(onAdd: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    val P = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.bgElevated,
        title = { Text("Restore backup", style = AppType.headline, color = P.textPrimary) },
        text = {
            Column {
                ChoiceRow("Add to my data", "Workouts and weigh-ins from the backup join yours. Nothing here is removed.", onAdd)
                Spacer(Modifier.height(8.dp))
                ChoiceRow("Replace everything", "Your data becomes exactly what the backup holds.", onReplace, danger = true)
                Spacer(Modifier.height(8.dp))
                Text("A copy of your data is kept first either way.", style = AppType.caption, color = P.textTertiary)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.textSecondary, style = AppType.label) } }
    )
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, onClick: () -> Unit, danger: Boolean = false) {
    val P = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.bgControl)
            .pressable(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, style = AppType.bodyStrong, color = if (danger) P.dangerText else P.textPrimary)
        Text(subtitle, style = AppType.caption, color = P.textSecondary)
    }
}

/**
 * Shows what a Strong or Hevy export holds before anything is added: workouts, sets, dates,
 * renamed exercises, and (for Strong, whose files don't say) which unit its weights are in.
 */
@Composable
private fun OtherAppImportSheet(text: String, defaultUnit: WeightUnit, onDone: (CombineResult?) -> Unit, onDismiss: () -> Unit) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var unit by remember { mutableStateOf(defaultUnit) }
    var adding by remember { mutableStateOf(false) }
    val parsed by produceState<Pair<ImportedHistory?, Int>?>(null, text, unit) {
        value = withContext(Dispatchers.Default) {
            val history = LocalStorage.loadHistory(context)
            val h = parseWorkoutExport(text, unit, loggedExerciseNames(history), LocalStorage.loadUserProfile(context).currentWeightKg)
            val have = history.mapTo(HashSet()) { it.id }
            h to (h?.workouts?.count { it.id in have } ?: 0)
        }
    }
    AppBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            val state = parsed
            val h = state?.first
            when {
                state == null -> Text("Reading the file…", style = AppType.body, color = P.textSecondary)
                h == null -> {
                    Text("Not a Strong or Hevy export", style = AppType.title, color = P.textPrimary)
                    Text(
                        "Export from Strong (Settings → Export Strong Data) or Hevy (Settings → Export & Import Data → Export Workouts), then pick the .csv file here.",
                        style = AppType.body,
                        color = P.textSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                else -> {
                    val already = state.second
                    val first = h.workouts.minOf { it.id }
                    val last = h.workouts.maxOf { it.id }
                    Text("Import from ${h.source.label}", style = AppType.title, color = P.textPrimary)
                    Text(
                        "${h.workouts.size} workouts · ${String.format(Locale.US, "%,d", h.setCount)} sets · " +
                            "${DateUtils.formatShortDayYear(Date(first))} – ${DateUtils.formatShortDayYear(Date(last))}",
                        style = AppType.body,
                        color = P.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (already > 0) {
                        Text("$already of them are already here and stay as they are.", style = AppType.caption, color = P.textTertiary)
                    }
                    if (!h.unitInFile) {
                        Spacer(Modifier.height(16.dp))
                        Text("Weights in this file are in", style = AppType.label, color = P.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        ChoiceChips(options = listOf(WeightUnit.KG to "Kilograms (kg)", WeightUnit.LB to "Pounds (lb)"), selected = unit, onSelect = { unit = it })
                        Text(
                            "${h.source.label} doesn't save the unit in its file: pick the one you used there.",
                            style = AppType.caption,
                            color = P.textTertiary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (h.renamed.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Matched to this app's exercises", style = AppType.label, color = P.textSecondary)
                        h.renamed.entries.take(4).forEach { (from, to) ->
                            Text("$from → $to", style = AppType.caption, color = P.textPrimary, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (h.renamed.size > 4) {
                            Text("and ${h.renamed.size - 4} more", style = AppType.caption, color = P.textTertiary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(
                        if (adding) "Adding…" else "Add to my history",
                        {
                            adding = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { LocalStorage.addData(context, h.asStoreData()) }
                                if (result == null) Toast.makeText(context, "Couldn't add the workouts", Toast.LENGTH_SHORT).show()
                                onDone(result)
                            }
                        },
                        Modifier.fillMaxWidth(),
                        enabled = !adding && h.workouts.size > already
                    )
                    Text(
                        "Nothing here is changed or removed. A copy of your data is kept first.",
                        style = AppType.caption,
                        color = P.textTertiary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Off: what signing in does and the button. On: the account, the last backup or what went wrong,
 * Back up now, Restore from Drive, Sign out. Right after signing in on a phone with nothing logged
 * (a new phone), the backups in Drive are offered straight away.
 */
@Composable
private fun GoogleBackupSection(onRestoreContent: (String) -> Unit) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = DriveBackup.status
    LaunchedEffect(Unit) { DriveBackup.refreshStatus(context) }
    var signingIn by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    fun afterSignIn(step: DriveBackup.SignInStep) {
        signingIn = false
        when (step) {
            is DriveBackup.SignInStep.Failed -> if (step.message.isNotEmpty()) Toast.makeText(context, step.message, Toast.LENGTH_LONG).show()
            is DriveBackup.SignInStep.NeedsUser -> Unit
            is DriveBackup.SignInStep.Done -> scope.launch {
                val found = withContext(Dispatchers.IO) { DriveBackup.listBackups(context) }.orEmpty()
                if (found.isNotEmpty() && LocalStorage.loadHistory(context).isEmpty()) choosing = true
                else DriveBackup.backUpNow(context)
            }
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        DriveBackup.finishSignIn(context, result.data, ::afterSignIn)
    }
    fun signIn() {
        signingIn = true
        DriveBackup.startSignIn(context) { step ->
            if (step is DriveBackup.SignInStep.NeedsUser) {
                consent.launch(IntentSenderRequest.Builder(step.intent).build())
            } else afterSignIn(step)
        }
    }

    if (!status.signedIn) {
        Text("Back up to Google", style = AppType.bodyStrong, color = P.textPrimary)
        Text(
            "Sign in and your workouts are saved to your own Google Drive after every workout. On a new phone, sign in and everything comes back.",
            style = AppType.caption,
            color = P.textSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (signingIn) "Signing in…" else "Sign in with Google", { signIn() }, Modifier.fillMaxWidth(), enabled = !signingIn, height = 48.dp)
        Text(
            "Backups go to a private app folder in your Drive. Only this app can read them; nothing goes to us.",
            style = AppType.caption,
            color = P.textTertiary,
            modifier = Modifier.padding(top = 8.dp)
        )
    } else {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (status.error != null) Icons.Rounded.ErrorOutline else Icons.Rounded.CloudDone,
                null,
                tint = if (status.error != null) P.dangerText else P.successText,
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Backing up to Google Drive", style = AppType.bodyStrong, color = P.textPrimary)
                Text(status.email.orEmpty(), style = AppType.caption, color = P.textSecondary)
                Text(
                    when {
                        status.running -> "Backing up…"
                        status.error != null -> status.error
                        status.lastSuccessMillis > 0L -> "Last backup " + AndroidDateUtils.formatDateTime(
                            context, status.lastSuccessMillis,
                            AndroidDateUtils.FORMAT_SHOW_DATE or AndroidDateUtils.FORMAT_SHOW_TIME or AndroidDateUtils.FORMAT_ABBREV_MONTH
                        )
                        else -> "Backs up after your next workout"
                    },
                    style = AppType.caption,
                    color = if (status.error != null && !status.running) P.dangerText else P.textSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (status.error == "Google needs you to sign in again.") {
                SecondaryButton("Sign in again", { signIn() }, Modifier.weight(1f))
            } else {
                SecondaryButton("Back up now", { DriveBackup.backUpNow(context) }, Modifier.weight(1f), enabled = !status.running)
            }
            SecondaryButton("Restore", { choosing = true }, Modifier.weight(1f))
        }
        LinkRow(Icons.AutoMirrored.Rounded.Logout, "Sign out") { confirmSignOut = true }
    }

    if (choosing) {
        DriveRestoreSheet(
            onPick = { content -> choosing = false; onRestoreContent(content) },
            onDismiss = {
                choosing = false
                DriveBackup.backUpNow(context)
            }
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = P.bgElevated,
            title = { Text("Sign out of Google?", style = AppType.headline, color = P.textPrimary) },
            text = { Text("Your backups stay in Google Drive, but new workouts won't be backed up there.", style = AppType.body, color = P.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; DriveBackup.signOut(context) }) { Text("Sign out", color = P.dangerText, style = AppType.label) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel", color = P.textSecondary, style = AppType.label) } }
        )
    }
}

/** The backups in Drive, newest first; picking one hands its contents to Restore (add or replace). */
@Composable
private fun DriveRestoreSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf<String?>(null) }
    val files by produceState<List<DriveBackup.DriveBackupFile>?>(null) {
        value = withContext(Dispatchers.IO) { DriveBackup.listBackups(context) } ?: emptyList()
    }
    AppBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Restore from Google Drive", style = AppType.title, color = P.textPrimary)
            Spacer(Modifier.height(4.dp))
            val list = files
            when {
                list == null -> Text("Looking for your backups…", style = AppType.body, color = P.textSecondary)
                list.isEmpty() -> Text("No backups in this Google account yet.", style = AppType.body, color = P.textSecondary)
                else -> {
                    Text("Pick one. Next you choose to add it to your data or replace it.", style = AppType.caption, color = P.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    list.take(12).forEachIndexed { i, f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(enabled = downloading == null, pressedScale = 0.98f) {
                                downloading = f.file.id
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) { DriveBackup.download(context, f.file) }
                                    downloading = null
                                    if (text == null) Toast.makeText(context, "Couldn't download that backup", Toast.LENGTH_SHORT).show()
                                    else onPick(text)
                                }
                            }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    AndroidDateUtils.formatDateTime(
                                        context, f.modifiedMillis,
                                        AndroidDateUtils.FORMAT_SHOW_DATE or AndroidDateUtils.FORMAT_SHOW_TIME or AndroidDateUtils.FORMAT_SHOW_YEAR
                                    ),
                                    style = AppType.bodyStrong,
                                    color = P.textPrimary
                                )
                                Text(
                                    (if (i == 0) "Newest · " else "") + "${(f.sizeBytes + 1023) / 1024} KB",
                                    style = AppType.caption,
                                    color = P.textSecondary
                                )
                            }
                            if (downloading == f.file.id) Text("Downloading…", style = AppType.caption, color = P.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
