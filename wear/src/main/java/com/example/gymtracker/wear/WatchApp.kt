package com.example.gymtracker.wear

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
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

/** A tap the phone hasn't acknowledged after this long shows "Waiting for phone…". */
private const val SLOW_PHONE_MS = 1_500L

@Composable
fun WatchApp(link: PhoneLink) {
    val haptic = LocalHapticFeedback.current
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
    val since = link.waitingSince
    val slow by produceState(false, since) {
        value = false
        if (since > 0L) {
            delay((SLOW_PHONE_MS - (SystemClock.uptimeMillis() - since)).coerceAtLeast(0))
            value = true
        }
    }

    WatchScreen(
        state = state,
        now = now,
        phoneConnected = link.phoneConnected,
        waitingForPhone = slow,
        onAction = {
            // A firmer tick for finishing a set than for -/+.
            haptic.performHapticFeedback(if (it == WatchAction.COMPLETE_SET) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            link.send(it)
        },
        onOpenOnPhone = link::openOnPhone
    )
}

/** Everything on screen, from plain values, so it can be rendered without a phone (see the screenshot test). */
@Composable
internal fun WatchScreen(
    state: WatchState?,
    now: Long,
    phoneConnected: Boolean,
    waitingForPhone: Boolean,
    onAction: (WatchAction) -> Unit,
    onOpenOnPhone: () -> Unit
) {
    MaterialTheme(colors = Colors(primary = Orange, onPrimary = Color.Black, secondary = Orange, background = Color.Black)) {
        val restEnd = state?.restEndsAtMillis ?: 0L
        val onSetScreen = state != null && state.active && restEnd <= now && state.exerciseName != null
        // The set screen needs the top of the display for the exercise name.
        Scaffold(Modifier.background(Color.Black), timeText = { if (!onSetScreen) TimeText() }) {
            val note = when {
                !phoneConnected -> "Phone not connected"
                waitingForPhone -> "Waiting for phone…"
                else -> null
            }
            when {
                state == null -> Message(
                    if (phoneConnected) "Connecting…" else "Phone not connected",
                    "Keep your phone nearby with GymTracker installed"
                )
                !state.active -> Message("No workout", "Start one on your phone", onOpenOnPhone)
                restEnd > now -> RestScreen(state, restEnd - now, note, onAction)
                state.exerciseName == null -> Message("All sets done", "Save the workout on your phone", onOpenOnPhone)
                else -> SetScreen(state, note, onAction)
            }
        }
    }
}

private fun buzz(context: Context) {
    context.getSystemService(Vibrator::class.java)
        ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
}

@Composable
private fun Message(title: String, body: String, onOpenOnPhone: (() -> Unit)? = null) {
    var opened by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.body2, color = Muted, textAlign = TextAlign.Center)
        if (onOpenOnPhone != null) {
            Spacer(Modifier.height(10.dp))
            if (opened) {
                Text("Check your phone", style = MaterialTheme.typography.caption1, color = Orange)
            } else {
                CompactChip(
                    onClick = { onOpenOnPhone(); opened = true },
                    label = { Text("Open on phone") },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }
    if (opened) LaunchedEffect(Unit) { delay(4_000); opened = false }
}

/**
 * Everything for the set fits on a 40 mm watch without scrolling: one-line name, the set,
 * two -/+ rows and "Set done", kept inside the round edge.
 */
@Composable
private fun SetScreen(state: WatchState, note: String?, onAction: (WatchAction) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            state.exerciseName.orEmpty(),
            style = MaterialTheme.typography.button,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 38.dp)
        )
        Text(
            note ?: "${state.setLabel} · ${state.setsLeft} left",
            style = MaterialTheme.typography.caption2,
            color = if (note != null) Muted else Orange
        )
        Spacer(Modifier.height(2.dp))
        Stepper(state.primary, state.primaryLabel, { onAction(WatchAction.PRIMARY_MINUS) }, { onAction(WatchAction.PRIMARY_PLUS) })
        state.secondary?.let { value ->
            Stepper(value, state.secondaryLabel.orEmpty(), { onAction(WatchAction.SECONDARY_MINUS) }, { onAction(WatchAction.SECONDARY_PLUS) })
        }
        Spacer(Modifier.height(4.dp))
        CompactChip(
            onClick = { onAction(WatchAction.COMPLETE_SET) },
            label = { Text("Set done") },
            colors = ChipDefaults.primaryChipColors()
        )
    }
}

@Composable
private fun Stepper(value: String, label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        CompactButton(onClick = onMinus, colors = ButtonDefaults.buttonColors(backgroundColor = Control, contentColor = Color.White)) {
            Text("−", style = MaterialTheme.typography.title3)
        }
        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.title3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.caption3, color = Muted)
        }
        CompactButton(onClick = onPlus, colors = ButtonDefaults.buttonColors(backgroundColor = Control, contentColor = Orange)) {
            Text("+", style = MaterialTheme.typography.title3)
        }
    }
}

@Composable
private fun RestScreen(state: WatchState, remainingMillis: Long, note: String?, onAction: (WatchAction) -> Unit) {
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
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(note ?: "Rest", style = MaterialTheme.typography.caption1, color = Muted)
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
                CompactChip(onClick = { onAction(WatchAction.ADD_REST) }, label = { Text("+15s") }, colors = ChipDefaults.secondaryChipColors())
                CompactChip(onClick = { onAction(WatchAction.SKIP_REST) }, label = { Text("Skip") }, colors = ChipDefaults.primaryChipColors())
            }
        }
    }
}

/** 95 000 ms → "1:35", rounding up so the timer never shows 0:00 while still resting. */
internal fun clock(millis: Long): String {
    val total = ((millis + 999) / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
