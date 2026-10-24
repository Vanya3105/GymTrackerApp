package com.example.gymtracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** New id for a user program. */
fun newProgramId(): String = "custom-${System.currentTimeMillis()}"

/** An editable copy of a built-in program; its days keep their titles, so exercises and history carry over. */
fun customizedCopy(builtIn: SplitProgramInfo): SplitProgramInfo =
    builtIn.copy(id = newProgramId(), name = "${builtIn.name} (mine)")

/**
 * Edits one of the user's programs: name, days per week and the days themselves (add, rename,
 * reorder, delete, and each day's exercise list). Every change is saved as it happens.
 */
@Composable
fun ProgramEditorScreen(
    programId: String,
    isActive: Boolean,
    haptics: Haptics,
    onUseProgram: () -> Unit,
    onDeleted: () -> Unit,
    /** A day was renamed, which can rename logged workouts too. */
    onHistoryChanged: () -> Unit,
    onBack: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    // Snapshot state: re-reads as the program is saved.
    val program = UserPrograms.all.find { it.id == programId } ?: return
    var renaming by remember { mutableStateOf<WorkoutSplit?>(null) }
    var addingDay by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember(programId) { mutableStateOf(program.name) }

    fun save(updated: SplitProgramInfo) = LocalStorage.saveProgram(context, updated)

    LazyColumn(
        Modifier.fillMaxSize().background(P.bgPrimary),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                CircleIconButton(Icons.Rounded.DeleteOutline, "Delete program", { confirmDelete = true }, tint = P.dangerText)
            }
        }
        item { Text("Edit program", style = AppType.display, color = P.textPrimary) }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { v ->
                    name = v
                    if (v.isNotBlank()) save(program.copy(name = v.trim()))
                },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Workouts per week", style = AppType.bodyStrong, color = P.textPrimary)
                    Text("Your weekly goal on Today and in widgets", style = AppType.caption, color = P.textSecondary)
                }
                NumberStepper(
                    value = "${program.weeklyGoal}",
                    onValueChange = { v -> v.filter(Char::isDigit).toIntOrNull()?.let { save(program.copy(weeklyGoal = it.coerceIn(1, 7))) } },
                    onMinus = { haptics.tick(); save(program.copy(weeklyGoal = (program.weeklyGoal - 1).coerceAtLeast(1))) },
                    onPlus = { haptics.tick(); save(program.copy(weeklyGoal = (program.weeklyGoal + 1).coerceAtMost(7))) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        if (!isActive) {
            item { PrimaryButton("Use this program", { haptics.confirm(); onUseProgram() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Check) }
        }

        item { SectionHeader("Days") }
        if (program.splits.isEmpty()) {
            item { EmptyState(Icons.Rounded.FitnessCenter, "No days yet", "Add a day, then pick its exercises.") }
        }
        itemsIndexed(program.splits, key = { _, s -> s.title }) { i, split ->
            val count = remember(split.title, UserPrograms.all) { LocalStorage.loadTemplateForSplit(context, split.title).size }
            AppCard(Modifier.fillMaxWidth().animateItem(), onClick = { editingDay = split.title }, contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(split.title, style = AppType.bodyStrong, color = P.textPrimary)
                        val sub = listOf(split.subtitle, if (count == 1) "1 exercise" else "$count exercises").filter { it.isNotBlank() }
                        Text(sub.joinToString(" · "), style = AppType.caption, color = P.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CircleIconButton(Icons.Rounded.ArrowUpward, "Move up", {
                        haptics.tick(); save(program.copy(splits = program.splits.moved(i, i - 1)))
                    }, enabled = i > 0, size = 34.dp, container = Color.Transparent)
                    CircleIconButton(Icons.Rounded.ArrowDownward, "Move down", {
                        haptics.tick(); save(program.copy(splits = program.splits.moved(i, i + 1)))
                    }, enabled = i < program.splits.lastIndex, size = 34.dp, container = Color.Transparent)
                    CircleIconButton(Icons.Rounded.Edit, "Rename day", { renaming = split }, size = 34.dp, container = Color.Transparent)
                }
            }
        }
        item {
            SecondaryButton("Add day", { addingDay = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
        item {
            Text(
                "Days with the same name share their exercise list and history, in every program.",
                style = AppType.caption,
                color = P.textTertiary
            )
        }
    }

    renaming?.let { split ->
        DayDialog(
            title = "Rename day",
            initialTitle = split.title,
            initialSubtitle = split.subtitle,
            taken = program.splits.map { it.title }.filter { it != split.title },
            onDismiss = { renaming = null },
            onSave = { title, subtitle ->
                if (title != split.title) {
                    LocalStorage.renameSplit(context, program.id, split.title, title)
                    onHistoryChanged()
                }
                val latest = UserPrograms.all.find { it.id == program.id } ?: program
                save(latest.copy(splits = latest.splits.map { if (it.title == title) it.copy(subtitle = subtitle) else it }))
                renaming = null
            },
            onDelete = if (program.splits.size > 1) {
                {
                    save(program.copy(splits = program.splits.filter { it.title != split.title }))
                    renaming = null
                }
            } else null
        )
    }

    if (addingDay) {
        DayDialog(
            title = "Add day",
            initialTitle = "",
            initialSubtitle = "",
            taken = program.splits.map { it.title },
            onDismiss = { addingDay = false },
            onSave = { title, subtitle ->
                haptics.confirm()
                save(program.copy(splits = program.splits + WorkoutSplit(title, subtitle, "%02d".format(program.splits.size + 1))))
                addingDay = false
                editingDay = title
            },
            onDelete = null
        )
    }

    editingDay?.let { title -> DayExercisesSheet(title, haptics, onDismiss = { editingDay = null }) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = P.bgElevated,
            title = { Text("Delete ${program.name}?", style = AppType.headline, color = P.textPrimary) },
            text = {
                Text(
                    "Your logged workouts and each day's exercises are kept." + if (isActive) " You'll switch back to a built-in program." else "",
                    style = AppType.body,
                    color = P.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    LocalStorage.deleteProgram(context, program.id)
                    onDeleted()
                }) { Text("Delete", color = P.dangerText, style = AppType.label) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = P.textSecondary, style = AppType.label) } }
        )
    }
}

/** Title + description for a day. Titles must be unique within the program. */
@Composable
private fun DayDialog(
    title: String,
    initialTitle: String,
    initialSubtitle: String,
    taken: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, subtitle: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val P = LocalPalette.current
    // Opens focused with the current name selected, so typing replaces it.
    var titleField by remember { mutableStateOf(TextFieldValue(initialTitle, TextRange(0, initialTitle.length))) }
    var subtitle by remember { mutableStateOf(initialSubtitle) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val trimmed = titleField.text.trim()
    val clash = taken.any { it.equals(trimmed, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.bgElevated,
        title = { Text(title, style = AppType.headline, color = P.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = titleField,
                    onValueChange = { titleField = it },
                    label = { Text("Name, like Push or Upper A") },
                    singleLine = true,
                    isError = clash,
                    supportingText = if (clash) ({ Text("This program already has a day with that name.") }) else null,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Focus (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Remove this day", onDelete, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteOutline, contentColor = P.dangerText)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty() && !clash, onClick = { onSave(trimmed, subtitle.trim()) }) {
                Text("Save", color = P.accentPrimary, style = AppType.label)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.textSecondary, style = AppType.label) } }
    )
}

/** A day's exercise list: reorder, remove, add from the library. */
@Composable
private fun DayExercisesSheet(dayTitle: String, haptics: Haptics, onDismiss: () -> Unit) {
    val P = LocalPalette.current
    val context = LocalContext.current
    var items by remember(dayTitle) { mutableStateOf(LocalStorage.loadTemplateForSplit(context, dayTitle)) }
    var picking by remember { mutableStateOf(false) }
    var targetFor by remember { mutableStateOf<String?>(null) }
    var targetsVersion by remember { mutableIntStateOf(0) }

    fun save(list: List<Pair<String, String>>) {
        items = list
        LocalStorage.saveTemplateForSplit(context, dayTitle, list)
    }

    AppBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(dayTitle, style = AppType.title, color = P.textPrimary)
            Text("These start every $dayTitle workout. You can still change things during a workout.", style = AppType.caption, color = P.textSecondary)
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text("No exercises yet.", style = AppType.body, color = P.textTertiary, modifier = Modifier.padding(vertical = 12.dp))
            }
            items.forEachIndexed { i, (name, muscle) ->
                val target = remember(name, targetsVersion) { LocalStorage.getTarget(context, dayTitle, name) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(pressedScale = 0.99f) { targetFor = name }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = AppType.bodyStrong, color = P.textPrimary)
                        val type = LocalStorage.exerciseType(context, name)
                        val sub = listOfNotNull(muscle.ifBlank { null }, type.takeIf { it != ExerciseType.WEIGHT_REPS }?.label)
                        if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = AppType.caption, color = P.textSecondary)
                        Text(
                            target?.let { if (type.usesReps) "Plan: ${it.label} reps" else "Plan: ${it.sets} sets" } ?: "Tap to set a plan",
                            style = AppType.caption,
                            color = if (target != null) P.accentPrimary else P.textTertiary
                        )
                    }
                    CircleIconButton(Icons.Rounded.ArrowUpward, "Move up", { haptics.tick(); save(items.moved(i, i - 1)) },
                        enabled = i > 0, size = 34.dp, container = Color.Transparent)
                    CircleIconButton(Icons.Rounded.ArrowDownward, "Move down", { haptics.tick(); save(items.moved(i, i + 1)) },
                        enabled = i < items.lastIndex, size = 34.dp, container = Color.Transparent)
                    CircleIconButton(Icons.Rounded.Close, "Remove", { haptics.tick(); save(items.filterIndexed { j, _ -> j != i }) },
                        size = 34.dp, container = Color.Transparent, tint = P.dangerText)
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Add exercise", { picking = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }

    targetFor?.let { name ->
        val type = LocalStorage.exerciseType(context, name)
        TargetDialog(
            name = name,
            usesReps = type.usesReps,
            current = LocalStorage.getTarget(context, dayTitle, name),
            onDismiss = { targetFor = null },
            onSave = { target ->
                haptics.tick()
                LocalStorage.setTarget(context, dayTitle, name, target)
                targetsVersion++
                targetFor = null
            }
        )
    }

    if (picking) {
        ExercisePickerSheet(
            existingNames = items.map { it.first }.toSet(),
            onPick = { info -> haptics.tick(); save(items + (info.name to info.muscle)) },
            onCreateCustom = { name, type ->
                LocalStorage.setExerciseType(context, name, type)
                save(items + (name to ""))
            },
            onDismiss = { picking = false },
            addedLabel = "Already on this day"
        )
    }
}

/** Sets × rep range for one exercise on a day; "No plan" clears it. */
@Composable
private fun TargetDialog(
    name: String,
    usesReps: Boolean,
    current: ExerciseTarget?,
    onDismiss: () -> Unit,
    onSave: (ExerciseTarget?) -> Unit
) {
    val P = LocalPalette.current
    var sets by remember { mutableIntStateOf(current?.sets ?: 3) }
    var repMin by remember { mutableIntStateOf(current?.repMin ?: 8) }
    var repMax by remember { mutableIntStateOf(current?.repMax ?: 12) }

    @Composable
    fun row(label: String, value: Int, onValue: (Int) -> Unit, range: IntRange) {
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = AppType.body, color = P.textPrimary, modifier = Modifier.weight(1f))
            NumberStepper(
                value = "$value",
                onValueChange = { v -> v.filter(Char::isDigit).toIntOrNull()?.let { onValue(it.coerceIn(range)) } },
                onMinus = { onValue((value - 1).coerceIn(range)) },
                onPlus = { onValue((value + 1).coerceIn(range)) },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.width(140.dp)
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.bgElevated,
        title = { Text(name, style = AppType.headline, color = P.textPrimary) },
        text = {
            Column {
                Text(
                    if (usesReps) "New workouts start with these sets. Hit the top of the range on every set and the app suggests more weight."
                    else "New workouts start with this many sets.",
                    style = AppType.caption,
                    color = P.textSecondary
                )
                Spacer(Modifier.height(10.dp))
                row("Sets", sets, { sets = it }, 1..10)
                if (usesReps) {
                    row("Reps from", repMin, { repMin = it; if (repMax < it) repMax = it }, 1..50)
                    row("Reps up to", repMax, { repMax = it; if (repMin > it) repMin = it }, 1..50)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ExerciseTarget(sets, repMin, repMax)) }) { Text("Save", color = P.accentPrimary, style = AppType.label) }
        },
        dismissButton = {
            TextButton(onClick = { onSave(null) }) { Text(if (current != null) "No plan" else "Cancel", color = P.textSecondary, style = AppType.label) }
        }
    )
}

@Composable
private fun fieldColors() = LocalPalette.current.let { P ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = P.accentPrimary,
        unfocusedBorderColor = P.borderStrong,
        focusedLabelColor = P.accentPrimary,
        unfocusedLabelColor = P.textSecondary,
        focusedTextColor = P.textPrimary,
        unfocusedTextColor = P.textPrimary
    )
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
