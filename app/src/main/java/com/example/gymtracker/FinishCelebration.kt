package com.example.gymtracker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The moment after Save: a badge that pops in, confetti, a short chime, and what the workout
 * meant (workout number, new bests and what they beat, the streak) with Share and Done.
 */
@Composable
fun WorkoutCelebration(data: ShareData, haptics: Haptics, onShare: () -> Unit, onDone: () -> Unit) {
    val P = LocalPalette.current
    BackHandler(onBack = onDone)
    val bests = data.bests
    val badge = remember { Animatable(0f) }
    val content = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { badge.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        delay(180)
        haptics.confirm()
        content.animateTo(1f, tween(420))
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(P.bgPrimary)
            .background(Brush.radialGradient(listOf(P.accentSoft, P.bgPrimary), radius = 1400f))
            // Swallows touches meant for the screen underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            Box(
                Modifier.size(112.dp).graphicsLayer { scaleX = badge.value; scaleY = badge.value }
                    .clip(CircleShape).background(if (bests.isNotEmpty()) P.warningSurface else P.successSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (bests.isNotEmpty()) Icons.Rounded.EmojiEvents else Icons.Rounded.Check,
                    null,
                    tint = if (bests.isNotEmpty()) P.warningText else P.successText,
                    modifier = Modifier.size(60.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.graphicsLayer { alpha = content.value; translationY = (1f - content.value) * 24.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when {
                        bests.size > 1 -> "${bests.size} new bests!"
                        bests.size == 1 -> "New best!"
                        else -> "Workout done!"
                    },
                    style = AppType.display,
                    color = P.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    "${data.title} · your ${ordinal(data.workoutNumber)} workout" +
                        if (data.weekStreak >= 2) " · ${data.weekStreak} weeks in a row" else "",
                    style = AppType.body,
                    color = P.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountUpTile(data.durationSeconds.toFloat(), { formatDuration(it.toLong()) }, "Time", Modifier.weight(1f), delayMillis = 350)
                    CountUpTile(data.workSets.toFloat(), { "${it.roundToInt()}" }, "Sets", Modifier.weight(1f), delayMillis = 450)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountUpTile(data.volumeKg.toFloat(), { formatVolume(it.roundToInt()) }, "Volume", Modifier.weight(1f), delayMillis = 550)
                    // No "0 new bests": without one, the tile counts exercises.
                    if (bests.isNotEmpty()) CountUpTile(
                        bests.size.toFloat(), { "${it.roundToInt()}" }, if (bests.size == 1) "New best" else "New bests", Modifier.weight(1f),
                        delayMillis = 650, valueColor = P.warningText
                    ) else CountUpTile(
                        data.lifts.size.toFloat(), { "${it.roundToInt()}" }, if (data.lifts.size == 1) "Exercise" else "Exercises", Modifier.weight(1f),
                        delayMillis = 650
                    )
                }
                volumeComparison(data.volumeKg)?.let { thing ->
                    Text("You lifted $thing.", style = AppType.label, color = P.textSecondary, modifier = Modifier.padding(top = 12.dp))
                }
                data.volumeChangePct?.takeIf { it > 0 }?.let { pct ->
                    Text("$pct% more volume than your last ${data.title}", style = AppType.label, color = P.successText, modifier = Modifier.padding(top = 4.dp))
                }
                if (bests.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.bgCard).padding(16.dp)) {
                        bests.take(4).forEachIndexed { i, b ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.EmojiEvents, null, tint = P.warningText, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(b.name, style = AppType.bodyStrong, color = P.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(b.before?.let { "Was $it" } ?: "First time logged", style = AppType.caption, color = P.textSecondary)
                                }
                                Text(b.set, style = AppType.bodyStrong.copy(fontFeatureSettings = "tnum"), color = P.textPrimary)
                            }
                        }
                        if (bests.size > 4) Text("and ${bests.size - 4} more", style = AppType.caption, color = P.textTertiary, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Done", onDone, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Share", onShare, Modifier.fillMaxWidth(), icon = Icons.Rounded.IosShare)
                Spacer(Modifier.height(24.dp))
            }
        }
        ConfettiBurst(Modifier.fillMaxSize(), pieces = if (bests.isNotEmpty()) 160 else 110)
    }
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/**
 * A short rising chime, made in code (no sound file): G5, C6, E6, G6, each a soft bell that
 * fades. Quiet unless the phone's sound is on; plays as a notification-type sound, so it ducks
 * music instead of stopping it.
 */
object WorkoutChime {
    fun play(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        Thread {
            runCatching {
                val rate = 44_100
                val notes = floatArrayOf(783.99f, 1046.5f, 1318.51f, 1567.98f)
                val step = (rate * 0.09).toInt()
                val ring = (rate * 0.7).toInt()
                val total = step * (notes.size - 1) + ring
                val mix = FloatArray(total)
                notes.forEachIndexed { i, f ->
                    val start = i * step
                    for (s in 0 until ring) {
                        val t = s.toFloat() / rate
                        // Bell: the note plus a soft octave, a fast attack and an exponential fade.
                        val env = (1f - exp(-t * 400f)) * exp(-t * 6f)
                        val v = (sin(2 * PI * f * t) + 0.25 * sin(2 * PI * f * 2 * t)).toFloat() * env
                        mix[start + s] += v * 0.22f
                    }
                }
                val pcm = ShortArray(total) { (mix[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((total * 1000L / rate) + 200)
                track.release()
            }
        }.apply { isDaemon = true }.start()
    }
}
