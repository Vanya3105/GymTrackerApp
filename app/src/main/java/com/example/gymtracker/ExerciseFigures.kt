package com.example.gymtracker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import kotlin.math.*

/*
 * Drawn exercise figures: one side-on figure in the app's colours, posed at the start and end of
 * each exercise and moved between the two. Poses are written in figure units with y pointing up
 * and the floor at 0 (a standing figure is about 87 tall); angles are degrees from straight up,
 * clockwise, so 90 points forward (right) and 180 points down.
 */

private const val TORSO = 27f
private const val NECK = 9.5f
private const val HEAD = 6.4f
private const val UPPER_ARM = 15f
private const val FOREARM = 14f
private const val THIGH = 21f
private const val SHIN = 20f
private const val PLATE = 11f

/** Where the far-side hand or foot sits when a pose doesn't place it: just behind the near one. */
private val FAR = Offset(-2.5f, 0f)

/**
 * A hand or foot position: in the room (it stays put, like hands on the floor), or [onBody]:
 * relative to the shoulder or hip, so it moves with the body and swings in an arc around it.
 */
private class Target(val x: Float, val y: Float, val onBody: Boolean = false)

private class Pose(
    val hip: Offset,
    val torso: Float,
    val head: Float = 0f,
    val hand: Target,
    val foot: Target,
    val farHand: Target? = null,
    val farFoot: Target? = null,
    val toe: Float = 90f,
    val farToe: Float = toe
)

private enum class Held { Barbell, Dumbbell, Kettlebell }

private class Bench(val left: Float, val right: Float, val top: Float)

/** [elbow] and [knee] say which way the joints bend; [thumb] is the moment shown in lists. */
private class Figure(
    val start: Pose,
    val end: Pose,
    val held: Held? = null,
    val thumb: Float = 0f,
    val elbow: Offset = Offset(-0.4f, -1f),
    val knee: Offset = Offset(1f, 0.3f),
    val farKnee: Offset = knee,
    val bench: Bench? = null,
    val bar: Offset? = null,
    val floor: Boolean = true
)

private class Limb(val root: Offset, val joint: Offset, val end: Offset)

private class Body(
    val hip: Offset, val shoulder: Offset, val head: Offset,
    val arm: Limb, val farArm: Limb, val leg: Limb, val farLeg: Limb,
    val toe: Float, val farToe: Float
)

private val standing = Offset(0f, 43.5f)
private fun room(x: Float, y: Float) = Target(x, y)
private fun body(x: Float, y: Float) = Target(x, y, onBody = true)

private val figures: Map<String, Figure> = mapOf(
    "Back Squat" to Figure(
        Pose(standing, 6f, hand = body(-3f, 1f), foot = room(2f, 3f)),
        Pose(Offset(-15f, 21f), 42f, head = -25f, hand = body(-3f, 1f), foot = room(2f, 3f)),
        held = Held.Barbell, thumb = 1f, elbow = Offset(-1f, -1f)
    ),
    "Barbell Bench Press" to Figure(
        Pose(Offset(20f, 22f), -90f, hand = body(0f, 29f), foot = room(38f, 3f)),
        Pose(Offset(20f, 22f), -90f, hand = body(3f, 10f), foot = room(38f, 3f)),
        held = Held.Barbell, elbow = Offset(0.5f, -1f), knee = Offset(0.3f, 1f),
        bench = Bench(-22f, 34f, 16f)
    ),
    "Deadlift" to Figure(
        Pose(Offset(-12f, 24f), 62f, head = -30f, hand = room(4f, PLATE), foot = room(0f, 3f)),
        Pose(Offset(-1f, 43.5f), -3f, hand = room(-1.5f, 43f), foot = room(0f, 3f)),
        held = Held.Barbell, elbow = Offset(-1f, 0f)
    ),
    "Pull-Up" to Figure(
        Pose(Offset(6.5f, 43f), -5f, hand = room(6f, 98f), foot = body(-7f, -34f), toe = 160f),
        Pose(Offset(8f, 69f), -12f, head = 5f, hand = room(6f, 98f), foot = body(-7f, -34f), toe = 160f),
        thumb = 1f, elbow = Offset(0.6f, -1f), knee = Offset(1f, 0f), bar = Offset(6f, 100f), floor = false
    ),
    "Push-Up" to Figure(
        Pose(Offset(-1.7f, 21.5f), 69.3f, head = 10f, hand = room(25f, 2f), foot = room(-40f, 7f), toe = 160f),
        Pose(Offset(0.9f, 10f), 85.8f, head = 5f, hand = room(25f, 2f), foot = room(-40f, 7f), toe = 160f),
        elbow = Offset(-0.6f, 1f)
    ),
    "Overhead Press" to Figure(
        Pose(standing, -2f, hand = body(6f, 1f), foot = room(1f, 3f)),
        Pose(standing, -4f, hand = body(1f, 28.5f), foot = room(1f, 3f)),
        held = Held.Barbell, thumb = 1f, elbow = Offset(1f, -1f)
    ),
    "Dumbbell Curl" to Figure(
        Pose(standing, 0f, hand = body(1.5f, -28.5f), foot = room(1f, 3f)),
        Pose(standing, 0f, hand = body(8.4f, -3.8f), foot = room(1f, 3f)),
        held = Held.Dumbbell, thumb = 1f, elbow = Offset(-1f, -0.3f)
    ),
    "Reverse Lunge" to Figure(
        Pose(Offset(-5f, 40f), 2f, hand = body(1f, -28f), foot = room(12f, 3f), farFoot = room(-24f, 7f), farToe = 125f),
        Pose(Offset(-5f, 21f), 2f, hand = body(1f, -28f), foot = room(12f, 3f), farFoot = room(-24f, 7f), farToe = 125f),
        held = Held.Dumbbell, thumb = 1f, farKnee = Offset(0.4f, -1f)
    ),
    "Plank" to Figure(
        Pose(Offset(-3.5f, 13.6f), 80.7f, head = 8f, hand = body(14f, -15f), foot = room(-44f, 7f), toe = 160f),
        Pose(Offset(-3.5f, 14.4f), 80.7f, head = 8f, hand = body(14f, -15f), foot = room(-44f, 7f), toe = 160f),
        elbow = Offset(-1f, -1f)
    ),
    "Kettlebell Swing" to Figure(
        Pose(Offset(-13f, 33f), 62f, head = -30f, hand = body(-12f, -26.5f), foot = room(0f, 3f)),
        Pose(standing, -4f, hand = body(28.8f, -2f), foot = room(0f, 3f)),
        held = Held.Kettlebell, thumb = 1f, elbow = Offset(-1f, -1f)
    )
).mapKeys { exerciseKey(it.key) }

object ExerciseFigures {
    fun has(name: String): Boolean = exerciseKey(name) in figures
}

// --- Posing ---

private fun dir(degrees: Float): Offset {
    val r = Math.toRadians(degrees.toDouble())
    return Offset(sin(r).toFloat(), cos(r).toFloat())
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun mix(a: Target, b: Target, t: Float): Target {
    if (!a.onBody) return Target(mix(a.x, b.x, t), mix(a.y, b.y, t))
    val from = atan2(a.y, a.x)
    var to = atan2(b.y, b.x)
    if (to - from > PI) to -= 2 * PI.toFloat()
    if (to - from < -PI) to += 2 * PI.toFloat()
    val angle = mix(from, to, t)
    val r = mix(hypot(a.x, a.y), hypot(b.x, b.y), t)
    return Target(r * cos(angle), r * sin(angle), onBody = true)
}

private fun dot(a: Offset, b: Offset) = a.x * b.x + a.y * b.y

/** Two-bone reach from [root] towards [target], bending the middle joint the [bend] way. */
private fun reach(root: Offset, target: Offset, upper: Float, lower: Float, bend: Offset): Limb {
    val v = target - root
    val d = v.getDistance().coerceIn(abs(upper - lower) + 0.01f, upper + lower - 0.01f)
    val base = atan2(v.y, v.x)
    val spread = acos(((upper * upper + d * d - lower * lower) / (2 * upper * d)).coerceIn(-1f, 1f))
    val a = root + Offset(cos(base + spread), sin(base + spread)) * upper
    val b = root + Offset(cos(base - spread), sin(base - spread)) * upper
    val joint = if (dot(a - root, bend) >= dot(b - root, bend)) a else b
    val rest = target - joint
    val len = rest.getDistance()
    return Limb(root, joint, if (len < 0.001f) joint else joint + rest / len * lower)
}

private fun Figure.at(t: Float): Body {
    val a = start
    val b = end
    val hip = lerp(a.hip, b.hip, t)
    val torso = mix(a.torso, b.torso, t)
    val shoulder = hip + dir(torso) * TORSO
    val head = shoulder + dir(torso + mix(a.head, b.head, t)) * NECK
    fun place(target: Target, root: Offset) = if (target.onBody) root + Offset(target.x, target.y) else Offset(target.x, target.y)
    fun pick(near: Target, far: Target?, other: Target?, root: Offset): Offset =
        if (far != null && other != null) place(mix(far, other, t), root) else place(near, root) + FAR
    val handT = mix(a.hand, b.hand, t)
    val footT = mix(a.foot, b.foot, t)
    val hand = place(handT, shoulder)
    val foot = place(footT, hip)
    return Body(
        hip, shoulder, head,
        reach(shoulder, hand, UPPER_ARM, FOREARM, elbow),
        reach(shoulder, pick(handT, a.farHand, b.farHand, shoulder), UPPER_ARM, FOREARM, elbow),
        reach(hip, foot, THIGH, SHIN, knee),
        reach(hip, pick(footT, a.farFoot, b.farFoot, hip), THIGH, SHIN, farKnee),
        mix(a.toe, b.toe, t), mix(a.farToe, b.farToe, t)
    )
}

private class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

/** The space the whole movement takes, so the figure stays still in its frame while it moves. */
private fun Figure.bounds(): Bounds {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    fun add(p: Offset, r: Float) {
        minX = min(minX, p.x - r); maxX = max(maxX, p.x + r)
        minY = min(minY, p.y - r); maxY = max(maxY, p.y + r)
    }
    for (i in 0..8) {
        val b = at(i / 8f)
        add(b.hip, 7f); add(b.shoulder, 7f); add(b.head, HEAD + 1f)
        listOf(b.arm, b.farArm, b.leg, b.farLeg).forEach { add(it.joint, 5f); add(it.end, 4f) }
        add(b.leg.end + dir(b.toe) * 6f, 2.5f); add(b.farLeg.end + dir(b.farToe) * 6f, 2.5f)
        when (held) {
            Held.Barbell -> add(b.arm.end, PLATE + 1.5f)
            Held.Kettlebell -> add(b.arm.end + (b.arm.end - b.arm.joint) / FOREARM * 6f, 6.5f)
            else -> {}
        }
    }
    bench?.let { add(Offset(it.left, it.top), 1f); add(Offset(it.right, 0f), 1f) }
    bar?.let { add(it + Offset(0f, 6f), 3f) }
    if (floor) add(Offset(minX, 0f), 1f)
    return Bounds(minX, minY, maxX, maxY)
}

// --- Drawing ---

private class Palette(
    val near: Color, val far: Color, val accent: Color, val gear: Color, val floor: Color, val background: Color
)

/** Figure units to pixels: x right, y flipped so the floor is at the bottom. */
private class Frame(val scale: Float, val left: Float, val top: Float) {
    fun px(p: Offset) = Offset(left + p.x * scale, top - p.y * scale)
}

private fun fit(b: Bounds, size: Size): Frame {
    val w = b.maxX - b.minX
    val h = b.maxY - b.minY
    val s = min(size.width / w, size.height / h)
    return Frame(s, (size.width - w * s) / 2 - b.minX * s, (size.height - h * s) / 2 + b.maxY * s)
}

private fun DrawScope.drawFigure(f: Figure, b: Body, k: Frame, c: Palette, loads: FloatArray) {
    fun seg(a: Offset, z: Offset, width: Float, color: Color) =
        drawLine(color, k.px(a), k.px(z), width * k.scale, StrokeCap.Round)
    fun dot(p: Offset, r: Float, color: Color) = drawCircle(color, r * k.scale, k.px(p))
    fun load(group: String) = loads.getOrElse(RECOVERY_GROUPS.indexOf(group)) { 0f }
    fun heat(base: Color, group: String, far: Boolean): Color? {
        val l = load(group)
        if (l <= 0f) return null
        val strength = (if (l >= 1f) 1f else if (l >= 0.5f) 0.6f else 0.35f) * (if (far) 0.7f else 1f)
        return lerp(base, c.accent, strength)
    }
    /** A muscle along one side of a limb; [side] 1 is the front of a limb hanging down. */
    fun muscle(a: Offset, z: Offset, width: Float, side: Float, from: Float, to: Float, color: Color?) {
        color ?: return
        val d = (z - a) / max((z - a).getDistance(), 0.001f)
        val off = Offset(-d.y, d.x) * (side * width * 0.22f)
        seg(lerp(a, z, from) + off, lerp(a, z, to) + off, width * 0.5f, color)
    }

    fun arm(l: Limb, far: Boolean) {
        val base = if (far) c.far else c.near
        seg(l.root, l.joint, 6.2f, base)
        seg(l.joint, l.end, 5.2f, base)
        heat(base, "Shoulders", far)?.let { seg(l.root, lerp(l.root, l.joint, 0.3f), 6.2f, it) }
        muscle(l.root, l.joint, 6.2f, 1f, 0.3f, 0.9f, heat(base, "Biceps", far))
        muscle(l.root, l.joint, 6.2f, -1f, 0.3f, 0.9f, heat(base, "Triceps", far))
        heat(base, "Forearms", far)?.let { seg(lerp(l.joint, l.end, 0.12f), lerp(l.joint, l.end, 0.75f), 3.4f, it) }
        dot(l.end, 2.7f, base)
    }

    fun leg(l: Limb, toe: Float, far: Boolean) {
        val base = if (far) c.far else c.near
        seg(l.end - dir(toe) * 2f, l.end + dir(toe) * 6f, 3.8f, base)
        seg(l.joint, l.end, 6.4f, base)
        seg(l.root, l.joint, 8.6f, base)
        muscle(l.root, l.joint, 8.6f, -1f, 0f, 0.35f, heat(base, "Glutes", far))
        muscle(l.root, l.joint, 8.6f, -1f, 0.38f, 0.9f, heat(base, "Hamstrings", far))
        muscle(l.root, l.joint, 8.6f, 1f, 0.12f, 0.88f, heat(base, "Quads", far))
        muscle(l.joint, l.end, 6.4f, -1f, 0.1f, 0.6f, heat(base, "Calves", far))
    }

    fun held(l: Limb, far: Boolean) {
        val gear = if (far) lerp(c.gear, c.background, 0.45f) else c.gear
        when (f.held) {
            Held.Dumbbell -> {
                dot(l.end, 3.9f, gear)
                dot(l.end, 1.3f, c.background)
            }
            Held.Kettlebell -> if (!far) {
                val bell = l.end + (l.end - l.joint) / FOREARM * 6f
                seg(l.end, bell, 2.2f, gear)
                dot(bell, 5.2f, gear)
            }
            else -> {}
        }
    }

    if (f.floor) drawLine(c.floor, Offset(0f, k.top), Offset(size.width, k.top), 1.2f * k.scale)
    f.bench?.let {
        listOf(it.left + 6f, it.right - 6f).forEach { x -> seg(Offset(x, it.top - 3f), Offset(x, 0.8f), 2.2f, c.floor) }
        val tl = k.px(Offset(it.left, it.top))
        drawRoundRect(
            c.floor, tl, Size((it.right - it.left) * k.scale, 4.5f * k.scale), CornerRadius(2f * k.scale)
        )
    }

    arm(b.farArm, far = true)
    held(b.farArm, far = true)
    leg(b.farLeg, b.farToe, far = true)

    val up = (b.shoulder - b.hip) / TORSO
    val tHip = b.hip + up * 1f
    val tTop = b.shoulder - up * 2.5f
    seg(tHip, tTop, 12.5f, c.near)
    muscle(tHip, tTop, 12.5f, -1f, 0.55f, 0.92f, heat(c.near, "Chest", false))
    muscle(tHip, tTop, 12.5f, 1f, 0.3f, 0.92f, heat(c.near, "Back", false))
    muscle(tHip, tTop, 12.5f, -1f, 0.08f, 0.5f, heat(c.near, "Core", false))
    seg(b.shoulder, b.head, 4.6f, c.near)
    dot(b.head, HEAD, c.near)

    if (f.held == Held.Barbell) {
        // The near plate, in front of the body; the near arm is drawn over it so the hand grips the bar.
        dot(b.arm.end, PLATE, c.background)
        drawCircle(c.gear, PLATE * k.scale - 1.2f * k.scale, k.px(b.arm.end), style = Stroke(2.4f * k.scale))
        dot(b.arm.end, 2.4f, c.gear)
    }
    leg(b.leg, b.toe, far = false)
    arm(b.arm, far = false)
    held(b.arm, far = false)

    f.bar?.let {
        seg(it, it + Offset(0f, 6f), 1.6f, c.floor)
        dot(it, 2.3f, c.gear)
    }
}

/**
 * The drawn figure for [name], moving between the start and end of the exercise when [moving],
 * otherwise held at its most recognisable moment (for small list pictures). Nothing when there's
 * no figure for it. [background] is what it sits on, used to cut out the barbell plates.
 */
@Composable
fun ExerciseFigure(name: String, modifier: Modifier = Modifier, moving: Boolean = true, background: Color = LocalPalette.current.bgControl) {
    val figure = remember(name) { figures[exerciseKey(name)] } ?: return
    val P = LocalPalette.current
    val loads = remember(name) { muscleLoads("", name, ExerciseDatabase.find(name)?.muscle ?: "") }
    val bounds = remember(name) { figure.bounds() }
    val t = remember(name) { Animatable(if (moving) 0f else figure.thumb) }
    if (moving) LaunchedEffect(name) {
        while (true) {
            delay(600)
            t.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
            delay(500)
            t.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
        }
    }
    val palette = remember(P, background) { paletteFor(P, background) }
    Canvas(modifier) {
        drawFigure(figure, figure.at(t.value), fit(bounds, size), palette, loads)
    }
}

private fun paletteFor(P: GymPalette, background: Color) = Palette(
    near = P.textSecondary,
    far = lerp(P.textSecondary, background, 0.45f),
    accent = P.accentPrimary,
    gear = P.textPrimary,
    floor = lerp(P.textTertiary, background, 0.35f),
    background = background
)

/** One moment ([t] from 0 = start to 1 = end) of [name]'s figure in an [area] at the origin, for screenshot tests. */
internal fun DrawScope.drawExerciseFigure(name: String, t: Float, P: GymPalette, area: Size, background: Color = P.bgControl) {
    val figure = figures.getValue(exerciseKey(name))
    val loads = muscleLoads("", name, ExerciseDatabase.find(name)?.muscle ?: "")
    drawFigure(figure, figure.at(t), fit(figure.bounds(), area), paletteFor(P, background), loads)
}

internal val figureNames: Set<String> get() = figures.keys
