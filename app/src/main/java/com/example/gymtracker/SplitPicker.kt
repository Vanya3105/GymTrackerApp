package com.example.gymtracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The split choice on onboarding and Profile: the current split, a search bar that opens every
 * split to search through, and a way to build your own.
 */
@Composable
fun SplitPicker(
    selectedId: String,
    onSelect: (SplitProgramInfo) -> Unit,
    /** Opens the split builder, named after what was typed (may be blank). */
    onBuild: (String) -> Unit,
    /** Edit one of the user's own splits; null hides the edit buttons. */
    onEdit: ((SplitProgramInfo) -> Unit)? = null,
    /** "Customize" / "Edit" on the current split's card. */
    cardActionLabel: String? = null,
    onCardAction: (() -> Unit)? = null
) {
    val P = LocalPalette.current
    var searching by remember { mutableStateOf(false) }
    val selected = programFor(selectedId)

    SearchBarButton("Search splits") { searching = true }
    Spacer(Modifier.height(12.dp))
    CurrentSplitCard(selected, cardActionLabel, onCardAction)
    Spacer(Modifier.height(4.dp))
    Text(
        "Don't see yours? Build your own",
        style = AppType.label,
        color = P.accentPrimary,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { onBuild("") }.padding(vertical = 10.dp, horizontal = 4.dp)
    )

    if (searching) {
        SplitPickerSheet(
            selectedId = selectedId,
            onSelect = { onSelect(it); searching = false },
            onBuild = { searching = false; onBuild(it) },
            onEdit = onEdit?.let { edit -> { p: SplitProgramInfo -> searching = false; edit(p) } },
            onDismiss = { searching = false }
        )
    }
}

/** Looks like a search field; opens the real one. */
@Composable
private fun SearchBarButton(hint: String, onClick: () -> Unit) {
    val P = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(P.bgControl)
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = P.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(hint, style = AppType.body, color = P.textTertiary)
    }
}

@Composable
private fun CurrentSplitCard(program: SplitProgramInfo, actionLabel: String?, onAction: (() -> Unit)?) {
    val P = LocalPalette.current
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your split", style = AppType.caption, color = P.textSecondary)
                Text(program.name, style = AppType.headline, color = P.textPrimary)
            }
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    style = AppType.label,
                    color = P.accentPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(daysLabel(program.weeklyGoal), style = AppType.caption, color = P.textSecondary)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            program.splits.forEach { day ->
                Text(
                    day.title,
                    style = AppType.label,
                    color = P.textPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(P.bgControl).padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

fun daysLabel(days: Int) = if (days == 1) "1 day a week" else "$days days a week"

/** Search every split (built-in and the user's), filter by days a week, or start building a new one. */
@Composable
fun SplitPickerSheet(
    selectedId: String,
    onSelect: (SplitProgramInfo) -> Unit,
    onBuild: (String) -> Unit,
    onEdit: ((SplitProgramInfo) -> Unit)?,
    onDismiss: () -> Unit
) {
    val P = LocalPalette.current
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var days by remember { mutableStateOf<Int?>(null) }
    val programs = allPrograms()
    val results = remember(query, days, programs) { searchSplits(query, programs, days) }
    val dayOptions = remember(programs) { programs.map { it.weeklyGoal }.distinct().sorted() }
    val listState = rememberLazyListState()
    val trimmed = query.trim()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // Scrolling the suggestions puts the keyboard away so more of them show.
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) focus.clearFocus() }

    AppBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Choose a split", style = AppType.title, color = P.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    "Done",
                    style = AppType.label,
                    color = P.accentPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onDismiss).padding(8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

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
                    if (query.isEmpty()) Text("Try \"ppl\", \"upper lower\" or \"4 days\"", style = AppType.body, color = P.textTertiary)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = AppType.body.copy(color = P.textPrimary),
                        cursorBrush = SolidColor(P.accentPrimary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                }
                if (query.isNotEmpty()) {
                    CircleIconButton(Icons.Rounded.Close, "Clear search", { query = "" }, size = 28.dp, container = Color.Transparent, tint = P.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf<Int?>(null) + dayOptions) { d ->
                    val isSel = days == d
                    val bg by animateColorAsState(if (isSel) P.textPrimary else P.bgControl, label = "days_bg")
                    val fg by animateColorAsState(if (isSel) P.bgPrimary else P.textSecondary, label = "days_fg")
                    Text(
                        if (d == null) "Any days" else if (d == 1) "1 day" else "$d days",
                        style = AppType.label,
                        color = fg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .pressable(pressedScale = 0.94f) { days = if (isSel) null else d }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val own = results.filter { !it.isBuiltIn }
            val builtIn = results.filter { it.isBuiltIn }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                item(key = "build") {
                    BuildRow(if (trimmed.isEmpty()) "Build your own split" else "Build \"$trimmed\"") { onBuild(trimmed) }
                }
                if (own.isNotEmpty()) {
                    item(key = "own_header") { ListLabel("Your splits") }
                    items(own, key = { it.id }) { p ->
                        SplitRow(p, p.id == selectedId, onEdit?.let { { it(p) } }, Modifier.animateItem()) { onSelect(p) }
                    }
                }
                if (builtIn.isNotEmpty()) {
                    item(key = "builtin_header") { ListLabel(if (trimmed.isEmpty() && days == null) "Popular splits" else "Splits") }
                    items(builtIn, key = { it.id }) { p ->
                        SplitRow(p, p.id == selectedId, null, Modifier.animateItem()) { onSelect(p) }
                    }
                }
                if (results.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(Icons.Rounded.Search, "No splits match", "Build it yourself: pick your days and the muscles for each.")
                    }
                }
            }
        }
    }
}

@Composable
private fun ListLabel(text: String) {
    val P = LocalPalette.current
    Text(text, style = AppType.overline, color = P.textTertiary, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun BuildRow(title: String, onClick: () -> Unit) {
    val P = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(P.accentSoft)
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(P.accentPrimary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = P.onAccentPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.bodyStrong, color = P.accentPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Pick your days and what each one trains", style = AppType.caption, color = P.textSecondary)
        }
    }
}

@Composable
private fun SplitRow(
    program: SplitProgramInfo,
    selected: Boolean,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val P = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) P.accentSoft else Color.Transparent)
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(program.name, style = AppType.bodyStrong, color = if (selected) P.accentPrimary else P.textPrimary)
            Text(
                "${daysLabel(program.weeklyGoal)} · ${program.splits.joinToString(", ") { it.title }}",
                style = AppType.caption,
                color = P.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onEdit != null) {
            CircleIconButton(Icons.Rounded.Edit, "Edit ${program.name}", onEdit, size = 34.dp, container = Color.Transparent, tint = P.textSecondary)
        }
        if (selected) {
            Box(Modifier.padding(horizontal = 6.dp).size(22.dp).clip(CircleShape).background(P.accentPrimary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Selected", tint = P.onAccentPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
