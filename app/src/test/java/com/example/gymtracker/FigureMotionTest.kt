package com.example.gymtracker

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every figure moves smoothly: played in small steps, no elbow, knee, hand or foot jumps further
 * in one step than the movement as a whole would explain (a joint flipping to the other side).
 */
class FigureMotionTest {
    private val steps = 400

    private fun Body.points(): List<Pair<String, Offset>> = listOf(
        "elbow" to arm.joint, "hand" to arm.end, "far elbow" to farArm.joint, "far hand" to farArm.end,
        "knee" to leg.joint, "foot" to leg.end, "far knee" to farLeg.joint, "far foot" to farLeg.end
    )

    /** Each jump in one step, as "name: part jumps size at t"; empty when smooth. */
    private fun jumps(name: String, figure: Figure): List<String> {
        val frames = (0..steps).map { figure.at(it / steps.toFloat()).points() }
        val out = ArrayList<String>()
        for (p in frames[0].indices) {
            val moves = (1..steps).map { (frames[it][p].second - frames[it - 1][p].second).getDistance() }
            val typical = moves.sorted()[moves.size / 2].coerceAtLeast(0.05f)
            moves.forEachIndexed { i, d ->
                // A 4-unit hop in one of 400 steps is a visible pop on screen (the snatch's fast
                // turnover comes close); so is 12x the usual step.
                if (d > 4f && d > typical * 12f) out += "$name: ${frames[0][p].first} jumps ${"%.1f".format(d)} at t=${"%.3f".format((i + 1) / steps.toFloat())}"
            }
        }
        return out
    }

    @Test
    fun noJointJumps() {
        val all = figures.flatMap { (name, f) -> jumps(name, f) }
        assertEquals(emptyList<String>(), all)
    }
}
