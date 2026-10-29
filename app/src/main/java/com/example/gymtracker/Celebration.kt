package com.example.gymtracker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class ConfettiPiece(
    val originX: Float,
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    val drift: Float
)

private const val CONFETTI_SECONDS = 2.4f

/**
 * A one-off burst of confetti from the top of [modifier]'s area that falls under gravity and
 * fades out. Draws nothing once it's over. Doesn't take touches.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, pieces: Int = 80) {
    val P = LocalPalette.current
    val colors = listOf(P.accentPrimary, P.warningText, P.successText, P.accentSecondary, Color.White)
    val parts = remember {
        val rnd = Random(System.nanoTime())
        List(pieces) {
            ConfettiPiece(
                originX = 0.3f + rnd.nextFloat() * 0.4f,
                // Upwards, spread over ~130°.
                angle = Math.toRadians(-90.0 + (rnd.nextFloat() - 0.5f) * 130.0).toFloat(),
                speed = 420f + rnd.nextFloat() * 520f,
                spin = (rnd.nextFloat() - 0.5f) * 3f,
                width = 6f + rnd.nextFloat() * 6f,
                height = 10f + rnd.nextFloat() * 8f,
                color = colors[rnd.nextInt(colors.size)],
                drift = (rnd.nextFloat() - 0.5f) * 2f
            )
        }
    }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(CONFETTI_SECONDS, tween((CONFETTI_SECONDS * 1000).toInt(), easing = LinearEasing)) }
    if (time.value >= CONFETTI_SECONDS) return

    Canvas(modifier) {
        val t = time.value
        val scale = density / 2.6f
        val gravity = 520f * scale
        val fade = (1f - (t / CONFETTI_SECONDS).let { it * it * it }).coerceIn(0f, 1f)
        parts.forEach { p ->
            // Air drag: the burst slows quickly, then the pieces flutter down.
            val travel = (1f - kotlin.math.exp(-2.2f * t)) / 2.2f
            val x = size.width * p.originX + cos(p.angle) * p.speed * scale * travel + sin(t * 4f + p.drift * 3f) * 14f * scale * p.drift
            val y = size.height * 0.08f + sin(p.angle) * p.speed * scale * travel + 0.5f * gravity * t * t
            val w = p.width * scale * 1.6f
            val h = p.height * scale * 1.6f
            rotate(degrees = p.spin * t * 360f, pivot = Offset(x, y)) {
                drawRect(p.color.copy(alpha = fade), topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h * (0.35f + 0.65f * kotlin.math.abs(cos(t * 6f + p.drift * 5f)))))
            }
        }
    }
}

/** Like [StatTile], but the number counts up from zero after [delayMillis]. */
@Composable
fun CountUpTile(
    target: Float,
    format: (Float) -> String,
    label: String,
    modifier: Modifier = Modifier,
    delayMillis: Long = 0L,
    valueColor: Color? = null
) {
    val P = LocalPalette.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(delayMillis)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(P.bgControl)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(format(target * progress.value), style = AppType.number.copy(fontSize = AppType.headline.fontSize), color = valueColor ?: P.textPrimary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = AppType.caption, color = P.textSecondary, maxLines = 1)
    }
}
