package com.example.gymtracker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * A front and a back figure with each of [RECOVERY_GROUPS] drawn as simple rounded shapes.
 * [loads] is in [RECOVERY_GROUPS] order, as [muscleLoads] gives it: 1 = the main muscle (accent),
 * 0.5 / 0.25 = helping muscles (lighter accent), 0 = not worked. The colours fade in when shown.
 */
@Composable
fun MuscleMap(loads: FloatArray, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    val reveal = remember(loads.contentHashCode()) { Animatable(0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, tween(500)) }
    val base = if (P.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    val bone = if (P.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
    fun fill(group: String): Color {
        val load = loads.getOrElse(RECOVERY_GROUPS.indexOf(group)) { 0f }
        if (load <= 0f) return base
        val strength = when {
            load >= 1f -> 1f
            load >= 0.5f -> 0.55f
            else -> 0.32f
        } * reveal.value
        return lerpColor(base, P.accentPrimary, strength)
    }
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(true, false).forEach { front ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(width = 104.dp, height = 220.dp)) {
                    val s = size.width / 100f
                    drawFigure(front, s, ::fill, bone)
                }
                Text(if (front) "Front" else "Back", style = AppType.caption, color = P.textTertiary)
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)

/** One figure in a 100 × 212 box (scaled by [s]): head, joints and hands in [bone], muscles by group. */
private fun DrawScope.drawFigure(front: Boolean, s: Float, fill: (String) -> Color, bone: Color) {
    fun oval(cx: Float, cy: Float, w: Float, h: Float, c: Color) =
        drawOval(c, Offset((cx - w / 2) * s, (cy - h / 2) * s), Size(w * s, h * s))
    fun both(cx: Float, cy: Float, w: Float, h: Float, c: Color) {
        oval(cx, cy, w, h, c); oval(100 - cx, cy, w, h, c)
    }
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) =
        drawRoundRect(c, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s))
    fun poly(c: Color, vararg pts: Float) {
        val p = Path()
        p.moveTo(pts[0] * s, pts[1] * s)
        var i = 2
        while (i < pts.size) { p.lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
        p.close()
        drawPath(p, c)
    }
    fun mirrored(c: Color, vararg pts: Float) {
        poly(c, *pts)
        poly(c, *FloatArray(pts.size) { i -> if (i % 2 == 0) 100 - pts[i] else pts[i] })
    }

    // Head, neck, hands, knees and feet: not muscles, just the figure.
    oval(50f, 13f, 20f, 23f, bone)
    rect(45f, 23f, 10f, 9f, 3f, bone)
    both(15f, 112f, 8f, 11f, bone)
    both(41f, 160f, 11f, 9f, bone)
    both(41f, 206f, 12f, 7f, bone)

    val shoulders = fill("Shoulders")
    val forearms = fill("Forearms")
    val calves = fill("Calves")
    both(27f, 41f, 17f, 16f, shoulders)
    both(17f, 92f, 9f, 28f, forearms)
    both(41f, 182f, 12f, 34f, calves)

    if (front) {
        val chest = fill("Chest")
        mirrored(chest, 49f, 36f, 33f, 38f, 30f, 50f, 36f, 60f, 49f, 60f)
        both(21f, 63f, 11f, 24f, fill("Biceps"))
        val core = fill("Core")
        for (row in 0 until 3) for (side in 0..1) rect(if (side == 0) 41.5f else 50.5f, 63f + row * 11.5f, 8f, 10f, 3f, core)
        both(36.5f, 82f, 5f, 28f, core)
        val quads = fill("Quads")
        mirrored(quads, 49f, 104f, 34f, 104f, 33f, 128f, 37f, 152f, 46f, 154f, 49f, 132f)
    } else {
        val back = fill("Back")
        // Traps, then the two lats with a gap down the spine, then the lower back.
        poly(back, 50f, 29f, 36f, 39f, 49f, 56f)
        poly(back, 50f, 29f, 64f, 39f, 51f, 56f)
        mirrored(back, 35f, 45f, 30f, 56f, 35f, 80f, 48.5f, 90f, 48.5f, 60f)
        mirrored(back, 44f, 92f, 48.5f, 92f, 48.5f, 102f, 44f, 102f)
        both(21f, 63f, 11f, 24f, fill("Triceps"))
        both(41.5f, 110f, 17f, 18f, fill("Glutes"))
        both(41f, 138f, 14f, 36f, fill("Hamstrings"))
    }
}
