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
    val originY: Float,
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    val drift: Float,
    val round: Boolean,
    /** Seconds after the start that this piece is fired. */
    val delay: Float
)

private const val CONFETTI_SECONDS = 3.2f

/**
 * A one-off burst of confetti over [modifier]'s area that falls under gravity and fades out:
 * from the top, and with [cannons] also from both bottom corners, aimed up and in. Draws nothing
 * once it's over. Doesn't take touches.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, pieces: Int = 80, cannons: Boolean = false) {
    val P = LocalPalette.current
    val colors = listOf(P.accentPrimary, P.warningText, P.successText, P.accentSecondary, Color.White)
    val parts = remember {
        val rnd = Random(System.nanoTime())
        fun piece(originX: Float, originY: Float, angleDeg: Double, spreadDeg: Double, speed: Float, delay: Float) = ConfettiPiece(
            originX = originX,
            originY = originY,
            angle = Math.toRadians(angleDeg + (rnd.nextFloat() - 0.5f) * spreadDeg).toFloat(),
            speed = speed * (0.7f + rnd.nextFloat() * 0.6f),
            spin = (rnd.nextFloat() - 0.5f) * 3f,
            width = 6f + rnd.nextFloat() * 6f,
            height = 10f + rnd.nextFloat() * 8f,
            color = colors[rnd.nextInt(colors.size)],
            drift = (rnd.nextFloat() - 0.5f) * 2f,
            round = rnd.nextFloat() < 0.3f,
            delay = delay
        )
        val top = List(pieces) { piece(0.3f + rnd.nextFloat() * 0.4f, 0.08f, -90.0, 130.0, 700f, 0f) }
        // The corner cannons fire a moment later, so the two bursts read as one celebration.
        val sides = if (cannons) List(pieces) { i ->
            val left = i % 2 == 0
            piece(if (left) -0.02f else 1.02f, 1.0f, if (left) -62.0 else -118.0, 26.0, 2600f, 0.15f + rnd.nextFloat() * 0.12f)
        } else emptyList()
        top + sides
    }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(CONFETTI_SECONDS, tween((CONFETTI_SECONDS * 1000).toInt(), easing = LinearEasing)) }
    if (time.value >= CONFETTI_SECONDS) return

    Canvas(modifier) {
        val scale = density / 2.6f
        val gravity = 520f * scale
        val fade = (1f - (time.value / CONFETTI_SECONDS).let { it * it * it }).coerceIn(0f, 1f)
        parts.forEach { p ->
            val t = time.value - p.delay
            if (t <= 0f) return@forEach
            // Air drag: the burst slows quickly, then the pieces flutter down.
            val travel = (1f - kotlin.math.exp(-2.2f * t)) / 2.2f
            val x = size.width * p.originX + cos(p.angle) * p.speed * scale * travel + sin(t * 4f + p.drift * 3f) * 14f * scale * p.drift
            val y = size.height * p.originY + sin(p.angle) * p.speed * scale * travel + 0.5f * gravity * t * t
            val w = p.width * scale * 1.6f
            val h = p.height * scale * 1.6f
            val color = p.color.copy(alpha = fade)
            if (p.round) {
                drawCircle(color, w * 0.45f, Offset(x, y))
            } else rotate(degrees = p.spin * t * 360f, pivot = Offset(x, y)) {
                drawRect(color, topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h * (0.35f + 0.65f * kotlin.math.abs(cos(t * 6f + p.drift * 5f)))))
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
