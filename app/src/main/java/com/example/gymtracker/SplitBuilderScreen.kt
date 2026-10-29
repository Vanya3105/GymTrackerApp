package com.example.gymtracker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One day while building: its muscles, and its name unless the user typed one. */
private data class BuilderDay(val muscles: Set<String>, val title: String, val manualTitle: Boolean = false)

private fun LayoutDay.toBuilderDay() = BuilderDay(muscles, title)

/**
 * Step-by-step split builder: days a week, what each day trains, then the exercises each day
 * starts with. Nothing is saved until "Create split"; then the split becomes the active one.
 */
@Composable
fun SplitBuilderScreen(
    initialName: String,
    haptics: Haptics,
    onCreated: (SplitProgramInfo) -> Unit,
    onClose: () -> Unit
) {
    val P = LocalPalette.current
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var dayCount by remember { mutableIntStateOf(3) }
    var layout by remember { mutableStateOf<QuickLayout?>(QuickLayout.PPL) }
    var days by remember { mutableStateOf(QuickLayout.PPL.fill(3).map { it.toBuilderDay() }) }
    // Set once a day is changed by hand, so changing the day count no longer refills every day.
    var touched by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initialName) }
    // Exercise lists the user changed on the review step, by day name + muscles.
    val editedExercises = remember { mutableStateMapOf<String, List<Pair<String, String>>>() }
    var renamingDay by remember { mutableStateOf<Int?>(null) }
    var pickingFor by remember { mutableStateOf<Int?>(null) }

    val titles = uniqueDayTitles(days.mapIndexed { i, d -> if (d.manualTitle || d.muscles.isNotEmpty()) d.title else "Day ${i + 1}" })
    fun dayKey(i: Int) = titles[i] + "|" + days[i].muscles.sorted().joinToString()
    // Days that already exist (a built-in day or one the user has) keep their exercises and history.
    val existing = titles.map { LocalStorage.existingTemplateForSplit(context, it) }
    val exercises = titles.indices.map { i ->
        editedExercises[dayKey(i)] ?: existing[i] ?: starterExercises(days[i].muscles, variantOf(titles[i]))
    }

    fun setDays(count: Int) {
        dayCount = count
        days = if (!touched) {
            val l = QuickLayout.suggestedFor(count)
            layout = l
            l.fill(count).map { it.toBuilderDay() }
        } else {
            List(count) { i -> days.getOrNull(i) ?: BuilderDay(emptySet(), "") }
        }
    }

    fun back() { if (step > 0) step-- else onClose() }
    BackHandler(onBack = ::back)

    Column(
        Modifier
            .fillMaxSize()
            .background(P.bgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                if (step == 0) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                if (step == 0) "Close" else "Back",
                ::back,
                container = Color.Transparent
            )
            Spacer(Modifier.weight(1f))
            StepDots(count = 3, current = step)
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
            label = "builder_step"
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 16.dp)) {
                when (s) {
                    0 -> DaysStep(dayCount, haptics) { setDays(it) }
                    1 -> MusclesStep(
                        days = days,
                        titles = titles,
                        layout = layout,
                        haptics = haptics,
                        onLayout = { l ->
                            layout = l
                            touched = false
                            days = l.fill(dayCount).map { it.toBuilderDay() }
                        },
                        onToggle = { i, muscle ->
                            haptics.tick()
                            touched = true
                            layout = null
                            days = days.toMutableList().also { list ->
                                val d = list[i]
                                val muscles = if (muscle in d.muscles) d.muscles - muscle else d.muscles + muscle
                                list[i] = d.copy(muscles = muscles, title = if (d.manualTitle) d.title else dayTitleFor(muscles, i))
                            }
                        },
                        onRename = { renamingDay = it }
                    )
                    else -> ReviewStep(
                        name = name,
                        placeholder = "My $dayCount-day split",
                        onName = { name = it },
                        titles = titles,
                        days = days.map { it.muscles },
                        exercises = exercises,
                        shared = existing.map { it != null },
                        onRemove = { i, j ->
                            haptics.tick()
                            editedExercises[dayKey(i)] = exercises[i].filterIndexed { k, _ -> k != j }
                        },
                        onAdd = { pickingFor = it }
                    )
                }
            }
        }

        val missingMuscles = titles.indices.firstOrNull { days[it].muscles.isEmpty() && !days[it].manualTitle }
        val missingExercises = titles.indices.firstOrNull { exercises[it].isEmpty() }
        val (label, enabled) = when (step) {
            0 -> "Continue" to true
            1 -> if (missingMuscles != null) "Pick muscles for ${titles[missingMuscles]}" to false else "Continue" to true
            else -> if (missingExercises != null) "Add exercises to ${titles[missingExercises]}" to false else "Create split" to true
        }
        PrimaryButton(
            text = label,
            enabled = enabled,
            icon = if (step == 2 && enabled) Icons.Rounded.Check else null,
            onClick = {
                if (step < 2) { haptics.tick(); step++ }
                else {
                    haptics.confirm()
                    val program = SplitProgramInfo(
                        id = newProgramId(),
                        name = name.trim().ifBlank { "My $dayCount-day split" },
                        tagline = "",
                        weeklyGoal = dayCount,
                        splits = titles.mapIndexed { i, t ->
                            WorkoutSplit(t, BUILDER_MUSCLES.filter { it in days[i].muscles }.joinToString(", "), "%02d".format(i + 1))
                        }
                    )
                    titles.forEachIndexed { i, t -> LocalStorage.saveTemplateForSplit(context, t, exercises[i]) }
                    LocalStorage.saveProgram(context, program)
                    onCreated(program)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    renamingDay?.let { i ->
        RenameDayDialog(
            current = titles[i],
            onDismiss = { renamingDay = null },
            onSave = { newTitle ->
                touched = true
                days = days.toMutableList().also { list ->
                    list[i] = if (newTitle.isBlank()) list[i].copy(title = dayTitleFor(list[i].muscles, i), manualTitle = false)
                    else list[i].copy(title = newTitle.trim(), manualTitle = true)
                }
                renamingDay = null
            }
        )
    }

    pickingFor?.let { i ->
        ExercisePickerSheet(
            existingNames = exercises[i].map { it.first }.toSet(),
            onPick = { info ->
                haptics.tick()
                val key = dayKey(i)
                editedExercises[key] = (editedExercises[key] ?: exercises[i]) + (info.name to info.muscle)
            },
            onCreateCustom = { exName, type ->
                LocalStorage.setExerciseType(context, exName, type)
                val key = dayKey(i)
                editedExercises[key] = (editedExercises[key] ?: exercises[i]) + (exName to "")
            },
            onDismiss = { pickingFor = null },
            addedLabel = "Already on this day"
        )
    }
}

/** "Push B" is the second Push day: start its exercises further down each muscle's list. */
private fun variantOf(title: String): Int {
    val last = title.substringAfterLast(' ', "")
    return if (last.length == 1 && last[0] in 'A'..'G') last[0] - 'A' else 0
}

@Composable
private fun ColumnScope.DaysStep(dayCount: Int, haptics: Haptics, onChange: (Int) -> Unit) {
    val P = LocalPalette.current
    Text("How many days a week?", style = AppType.display, color = P.textPrimary)
    Spacer(Modifier.height(6.dp))
    Text("Count the different workouts in your week. You can change everything later.", style = AppType.body, color = P.textSecondary)
    Spacer(Modifier.height(40.dp))

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircleIconButton(Icons.Rounded.Remove, "Fewer days", { haptics.tick(); onChange(dayCount - 1) }, enabled = dayCount > 1, size = 56.dp)
        Text(
            "$dayCount",
            style = AppType.bigNumber.copy(fontSize = 72.sp, lineHeight = 80.sp),
            color = P.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp)
        )
        CircleIconButton(Icons.Rounded.Add, "More days", { haptics.tick(); onChange(dayCount + 1) }, enabled = dayCount < 7, size = 56.dp)
    }
    Text(
        if (dayCount == 1) "day a week" else "days a week",
        style = AppType.body,
        color = P.textSecondary,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(7) { i ->
            val on = i < dayCount
            val bg by animateColorAsState(if (on) P.accentPrimary else P.bgControl, label = "day_pip")
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(bg))
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "Most people on $dayCount ${if (dayCount == 1) "day" else "days"} run: ${QuickLayout.suggestedFor(dayCount).label}",
        style = AppType.caption,
        color = P.textTertiary,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
}

@Composable
private fun MusclesStep(
    days: List<BuilderDay>,
    titles: List<String>,
    layout: QuickLayout?,
    haptics: Haptics,
    onLayout: (QuickLayout) -> Unit,
    onToggle: (day: Int, muscle: String) -> Unit,
    onRename: (Int) -> Unit
) {
    val P = LocalPalette.current
    Text("What does each day train?", style = AppType.display, color = P.textPrimary)
    Spacer(Modifier.height(6.dp))
    Text("Start from a layout, then tap muscles to change any day.", style = AppType.body, color = P.textSecondary)
    Spacer(Modifier.height(16.dp))

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickLayout.entries.forEach { l ->
            Chip(l.label, selected = l == layout, strong = true) { haptics.tick(); onLayout(l) }
        }
    }
    Spacer(Modifier.height(20.dp))

    days.forEachIndexed { i, day ->
        AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(P.accentSoft), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = AppType.label, color = P.accentPrimary)
                }
                Spacer(Modifier.width(10.dp))
                Text(titles[i], style = AppType.headline, color = P.textPrimary, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Rounded.Edit, "Rename ${titles[i]}", { onRename(i) }, size = 34.dp, container = Color.Transparent, tint = P.textSecondary)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BUILDER_MUSCLES.forEach { m -> Chip(m, selected = m in day.muscles) { onToggle(i, m) } }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, strong: Boolean = false, onClick: () -> Unit) {
    val P = LocalPalette.current
    val bg by animateColorAsState(
        when {
            selected && strong -> P.textPrimary
            selected -> P.accentPrimary
            else -> P.bgControl
        },
        label = "chip_bg"
    )
    val fg by animateColorAsState(
        when {
            selected && strong -> P.bgPrimary
            selected -> P.onAccentPrimary
            else -> P.textSecondary
        },
        label = "chip_fg"
    )
    Text(
        text,
        style = AppType.label,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .pressable(pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun ReviewStep(
    name: String,
    placeholder: String,
    onName: (String) -> Unit,
    titles: List<String>,
    days: List<Set<String>>,
    exercises: List<List<Pair<String, String>>>,
    shared: List<Boolean>,
    onRemove: (day: Int, index: Int) -> Unit,
    onAdd: (day: Int) -> Unit
) {
    val P = LocalPalette.current
    Text("Your split", style = AppType.display, color = P.textPrimary)
    Spacer(Modifier.height(6.dp))
    Text("Name it and check each day's exercises. Sets and reps are set during workouts.", style = AppType.body, color = P.textSecondary)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text("Name") },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(20.dp))

    titles.forEachIndexed { i, title ->
        AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
            Text(title, style = AppType.headline, color = P.textPrimary)
            val muscles = BUILDER_MUSCLES.filter { it in days[i] }.joinToString(", ")
            val note = if (shared[i]) "Same exercises as your other $title days" else null
            listOfNotNull(muscles.ifBlank { null }, note).forEach {
                Text(it, style = AppType.caption, color = P.textSecondary)
            }
            Spacer(Modifier.height(6.dp))
            exercises[i].forEachIndexed { j, (exName, muscle) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(exName, style = AppType.body, color = P.textPrimary)
                        if (muscle.isNotBlank()) Text(muscle, style = AppType.caption, color = P.textTertiary)
                    }
                    CircleIconButton(Icons.Rounded.Close, "Remove $exName", { onRemove(i, j) }, size = 34.dp, container = Color.Transparent, tint = P.textTertiary)
                }
            }
            Text(
                "Add exercise",
                style = AppType.label,
                color = P.accentPrimary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { onAdd(i) }.padding(vertical = 10.dp, horizontal = 2.dp)
            )
        }
    }
}

/** Blank resets the day to its automatic name. */
@Composable
private fun RenameDayDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val P = LocalPalette.current
    var field by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.bgElevated,
        title = { Text("Name this day", style = AppType.headline, color = P.textPrimary) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text("Like Push or Upper A") },
                supportingText = { Text("Leave it empty to name it after its muscles.") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        },
        confirmButton = { TextButton(onClick = { onSave(field.text) }) { Text("Save", color = P.accentPrimary, style = AppType.label) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.textSecondary, style = AppType.label) } }
    )
}
