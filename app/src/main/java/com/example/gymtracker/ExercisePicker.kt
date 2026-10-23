package com.example.gymtracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet for adding exercises: search the built-in library (name, muscle, equipment or
 * nickname like "rdl"), filter by muscle, tap to add. Several can be added before closing.
 */
@Composable
fun ExercisePickerSheet(
    existingNames: Set<String>,
    onPick: (ExerciseInfo) -> Unit,
    onCreateCustom: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val P = LocalPalette.current
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    val results = remember(query, muscle) { ExerciseDatabase.search(query, muscle) }
    val lowerExisting = remember(existingNames) { existingNames.map { it.lowercase() }.toSet() }
    val trimmed = query.trim()
    val canCreate = trimmed.length >= 2 &&
        results.none { it.name.equals(trimmed, ignoreCase = true) } &&
        trimmed.lowercase() !in lowerExisting

    AppBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Add exercise", style = AppType.title, color = P.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    "Done",
                    style = AppType.label,
                    color = P.accentPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onDismiss).padding(8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

            // Search field
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(P.bgControl)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = P.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search exercises", style = AppType.body, color = P.textTertiary)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = AppType.body.copy(color = P.textPrimary),
                        cursorBrush = SolidColor(P.accentPrimary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    CircleIconButton(Icons.Rounded.Close, "Clear search", { query = "" }, size = 28.dp, container = Color.Transparent, tint = P.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))

            // Muscle filter
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf<String?>(null) + ExerciseDatabase.muscleGroups) { group ->
                    val selected = muscle == group
                    val bg by animateColorAsState(if (selected) P.textPrimary else P.bgControl, label = "filter_bg")
                    val fg by animateColorAsState(if (selected) P.bgPrimary else P.textSecondary, label = "filter_fg")
                    Text(
                        group ?: "All",
                        style = AppType.label,
                        color = fg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .pressable(pressedScale = 0.94f) { muscle = group }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(results, key = { it.name }) { info ->
                    val added = info.name.lowercase() in lowerExisting
                    PickerRow(
                        title = info.name,
                        subtitle = "${info.muscle} · ${info.equipment}",
                        added = added,
                        onClick = { if (!added) onPick(info) },
                        modifier = Modifier.animateItem()
                    )
                }
                if (canCreate) {
                    item(key = "create") {
                        PickerRow(
                            title = "Create \"$trimmed\"",
                            subtitle = "Add your own exercise",
                            added = false,
                            leadingAccent = true,
                            onClick = {
                                onCreateCustom(trimmed)
                                query = ""
                            }
                        )
                    }
                }
                if (results.isEmpty() && !canCreate) {
                    item { EmptyState(Icons.Rounded.Search, "No matches", "Try a different word, like \"row\" or \"cable\".") }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    added: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingAccent: Boolean = false
) {
    val P = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pressable(enabled = !added, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                title,
                style = AppType.bodyStrong,
                color = when {
                    added -> P.textSecondary
                    leadingAccent -> P.accentPrimary
                    else -> P.textPrimary
                }
            )
            Text(if (added) "In this workout" else subtitle, style = AppType.caption, color = P.textSecondary)
        }
        AnimatedContent(
            targetState = added,
            transitionSpec = { scaleIn(spring(dampingRatio = 0.5f)).togetherWith(scaleOut()) },
            label = "picker_check"
        ) { isAdded ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (isAdded) P.successSurface else P.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = if (isAdded) "Added" else "Add",
                    tint = if (isAdded) P.successText else P.accentPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
