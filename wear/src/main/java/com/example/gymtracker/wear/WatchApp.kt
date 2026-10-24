package com.example.gymtracker.wear

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.gymtracker.watchlink.WatchAction
import com.example.gymtracker.watchlink.WatchState
import kotlinx.coroutines.delay

private val Orange = Color(0xFFFF6B1A)
private val Muted = Color(0xFF9A9A9A)
private val Control = Color(0xFF262626)

@Composable
fun WatchApp(link: PhoneLink) {
    MaterialTheme(colors = Colors(primary = Orange, onPrimary = Color.Black, secondary = Orange, background = Color.Black)) {
        val haptic = LocalHapticFeedback.current
        val act: (WatchAction) -> Unit = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            link.send(it)
        }
        val state = link.state
        val restEnd = state?.restEndsAtMillis ?: 0L
        // Ticks only while a rest is running.
        val now by produceState(System.currentTimeMillis(), restEnd) {
            value = System.currentTimeMillis()
            while (restEnd > value) {
                delay(200)
                value = System.currentTimeMillis()
            }
        }
        // While the app is on screen; otherwise the phone's "Rest's over" notification buzzes the watch.
        val context = LocalContext.current
        LaunchedEffect(restEnd) {
            val wait = restEnd - System.currentTimeMillis()
            if (wait > 0) {
                delay(wait)
                buzz(context)
            }
        }

        Scaffold(timeText = { TimeText() }) {
            when {
                state == null -> Message(
                    if (link.phoneConnected) "Connecting…" else "Phone not connected",
                    "Open GymTracker on your phone"
                )
                !state.active -> Message("No workout", "Start one on your phone")
                restEnd > now -> RestScreen(state, restEnd - now, act)
                state.exerciseName == null -> Message("All sets done", "Save the workout on your phone")
                else -> SetScreen(state, act)
            }
        }
    }
}

private fun buzz(context: Context) {
    context.getSystemService(Vibrator::class.java)
        ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.body2, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SetScreen(state: WatchState, act: (WatchAction) -> Unit) {
    // Scrolls with the bezel or crown when the second row doesn't fit.
    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                state.exerciseName.orEmpty(),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        item {
            Text("${state.setLabel} · ${state.setsLeft} left", style = MaterialTheme.typography.caption1, color = Orange)
        }
        item { Stepper(state.primary, state.primaryLabel, { act(WatchAction.PRIMARY_MINUS) }, { act(WatchAction.PRIMARY_PLUS) }) }
        state.secondary?.let { value ->
            item { Stepper(value, state.secondaryLabel.orEmpty(), { act(WatchAction.SECONDARY_MINUS) }, { act(WatchAction.SECONDARY_PLUS) }) }
        }
        item {
            Chip(
                onClick = { act(WatchAction.COMPLETE_SET) },
                label = { Text("Set done", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun Stepper(value: String, label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompactButton(onClick = onMinus, colors = ButtonDefaults.buttonColors(backgroundColor = Control, contentColor = Color.White)) {
            Text("−", style = MaterialTheme.typography.title3)
        }
        Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.title2, maxLines = 1)
            Text(label, style = MaterialTheme.typography.caption2, color = Muted)
        }
        CompactButton(onClick = onPlus, colors = ButtonDefaults.buttonColors(backgroundColor = Control, contentColor = Orange)) {
            Text("+", style = MaterialTheme.typography.title3)
        }
    }
}

@Composable
private fun RestScreen(state: WatchState, remainingMillis: Long, act: (WatchAction) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state.restTotalMillis > 0) {
            CircularProgressIndicator(
                progress = (remainingMillis.toFloat() / state.restTotalMillis).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize().padding(3.dp),
                indicatorColor = Orange,
                trackColor = Control,
                strokeWidth = 6.dp
            )
        }
        Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Rest", style = MaterialTheme.typography.caption1, color = Muted)
            Text(clock(remainingMillis), style = MaterialTheme.typography.display2)
            state.exerciseName?.let {
                Text(
                    "Next: $it",
                    style = MaterialTheme.typography.caption2,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(state.summary, style = MaterialTheme.typography.caption1, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactChip(onClick = { act(WatchAction.ADD_REST) }, label = { Text("+15s") }, colors = ChipDefaults.secondaryChipColors())
                CompactChip(onClick = { act(WatchAction.SKIP_REST) }, label = { Text("Skip") }, colors = ChipDefaults.primaryChipColors())
            }
        }
    }
}

/** 95 000 ms → "1:35", rounding up so the timer never shows 0:00 while still resting. */
internal fun clock(millis: Long): String {
    val total = ((millis + 999) / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
