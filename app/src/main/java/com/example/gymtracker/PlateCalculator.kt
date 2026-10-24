package com.example.gymtracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ============================================================================
// PLATE CALCULATOR
// ============================================================================
// Plates come in round numbers of their own unit, so all plate math is done in the display
// unit (kg or lb), never in stored kilograms.

/** A bar and the plates available for it, in one unit. Plates are unlimited pairs of each size. */
data class PlateSetup(val barWeight: Float, val plates: List<Float>)

data class BarLoad(val total: Float, val perSide: List<Float>)

/** [exact] when the target can be loaded; otherwise the nearest loads either side (null if none). */
data class PlateMath(val exact: BarLoad?, val lighter: BarLoad?, val heavier: BarLoad?)

val WeightUnit.barOptions: List<Float>
    get() = if (this == WeightUnit.KG) listOf(20f, 15f, 10f, 0f) else listOf(45f, 35f, 25f, 0f)

val WeightUnit.plateOptions: List<Float>
    get() = if (this == WeightUnit.KG) listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 2f, 1.25f, 1f, 0.5f)
    else listOf(55f, 45f, 35f, 25f, 10f, 5f, 2.5f, 1.25f)

fun defaultPlateSetup(unit: WeightUnit) =
    if (unit == WeightUnit.KG) PlateSetup(20f, listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f))
    else PlateSetup(45f, listOf(45f, 35f, 25f, 10f, 5f, 2.5f))

/**
 * Plates per side for [target]. Exact search rather than greedy: with gaps in the plate set,
 * greedy misses loads that exist (only 15s and 10s, 20 per side: greedy takes a 15 and is stuck,
 * 10 + 10 works). Among equal plate counts the heaviest plates go on first.
 */
fun calculatePlates(target: Float, setup: PlateSetup): PlateMath {
    val bar = setup.barWeight
    val barOnly = BarLoad(bar, emptyList())
    val hundredths = setup.plates.filter { it > 0f }.map { (it * 100).roundToInt() }.distinct().sortedDescending()
    val diff = ((target - bar) * 100).roundToInt()
    if (diff < 0) return PlateMath(null, null, barOnly)
    if (diff == 0) return PlateMath(barOnly, null, null)
    if (hundredths.isEmpty()) return PlateMath(null, barOnly, null)

    // fewest[s]: fewest plates making s hundredths per side, or MAX if impossible.
    val limit = diff / 2 + hundredths.max()
    val fewest = IntArray(limit + 1) { Int.MAX_VALUE }.also { it[0] = 0 }
    for (s in 1..limit) {
        for (p in hundredths) {
            if (p <= s && fewest[s - p] != Int.MAX_VALUE && fewest[s - p] + 1 < fewest[s]) fewest[s] = fewest[s - p] + 1
        }
    }

    fun loadFor(perSide: Int): BarLoad {
        val plates = mutableListOf<Float>()
        var s = perSide
        while (s > 0) {
            val p = hundredths.first { it <= s && fewest[s - it] == fewest[s] - 1 }
            plates += p / 100f
            s -= p
        }
        return BarLoad(bar + perSide * 2 / 100f, plates)
    }

    if (diff % 2 == 0 && fewest[diff / 2] != Int.MAX_VALUE) return PlateMath(loadFor(diff / 2), null, null)
    val below = (diff / 2 downTo 0).first { fewest[it] != Int.MAX_VALUE }
    val above = ((diff + 1) / 2..limit).firstOrNull { fewest[it] != Int.MAX_VALUE }
    return PlateMath(null, loadFor(below), above?.let(::loadFor))
}

/** "62.5 kg" for a value already in [unit] (plates and bars, not stored kg). */
private fun formatInUnit(value: Float, unit: WeightUnit) = "${formatKgClean(value)} ${unit.label}"

// --- Sheet ---

/** Opens from a set's menu. Edits the set's weight live; the bar and plates are remembered per unit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlateCalculatorSheet(storedKg: String, step: Float, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    val P = LocalPalette.current
    val context = LocalContext.current
    val unit = Units.current
    var setup by remember(unit) { mutableStateOf(LocalStorage.loadPlateSetup(context, unit)) }
    fun update(new: PlateSetup) {
        setup = new
        LocalStorage.savePlateSetup(context, unit, new)
    }

    val target = unit.fromKg(parseWeightClean(storedKg, 0f))
    val math = remember(target, setup) { calculatePlates(target, setup) }
    val shown = math.exact ?: math.lighter ?: math.heavier ?: BarLoad(setup.barWeight, emptyList())

    AppBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Text("Plate calculator", style = AppType.title, color = P.textPrimary)
            Spacer(Modifier.height(14.dp))
            WeightStepper(storedKg = storedKg, onChange = onChange, step = step, fallbackKg = 0f, height = 48.dp, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(18.dp))
            LoadedBar(shown.perSide, setup.plates.maxOrNull() ?: 1f, hasBar = setup.barWeight > 0f)
            Spacer(Modifier.height(10.dp))
            Text(
                if (shown.perSide.isEmpty()) "Nothing on the bar" else "Each side: " + shown.perSide.joinToString("  ·  ") { formatKgClean(it) },
                style = AppType.bodyStrong,
                color = P.textPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (math.exact == null) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.warningSurface).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, tint = P.warningText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${formatInUnit(target, unit)} can't be loaded with these plates.",
                            style = AppType.label.copy(fontWeight = FontWeight.Normal),
                            color = P.textPrimary
                        )
                    }
                    Row(Modifier.padding(top = 10.dp, start = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOfNotNull(math.lighter, math.heavier).forEach { load ->
                            Text(
                                "Use ${formatInUnit(load.total, unit)}",
                                style = AppType.label,
                                color = P.warningText,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(P.bgCard.copy(alpha = if (P.isDark) 0.35f else 0.8f))
                                    .pressable { onChange(unit.stored(load.total)) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Bar", style = AppType.label, color = P.textSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedControl(
                options = unit.barOptions.map { it to if (it == 0f) "No bar" else formatInUnit(it, unit) },
                selected = setup.barWeight,
                onSelect = { update(setup.copy(barWeight = it)) }
            )

            Spacer(Modifier.height(18.dp))
            Text("Plates you have", style = AppType.label, color = P.textSecondary)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                unit.plateOptions.forEach { plate ->
                    val on = plate in setup.plates
                    Text(
                        formatKgClean(plate),
                        style = AppType.number,
                        color = if (on) P.onAccentPrimary else P.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) P.accentPrimary else P.bgControl)
                            .pressable(pressedScale = 0.9f) {
                                update(setup.copy(plates = if (on) setup.plates - plate else (setup.plates + plate).sortedDescending()))
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

/** One side of a barbell, plates drawn to scale by weight, heaviest next to the collar. */
@Composable
private fun LoadedBar(perSide: List<Float>, heaviest: Float, hasBar: Boolean) {
    val P = LocalPalette.current
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val midY = size.height / 2
        val gap = 2.dp.toPx()
        val plateWidth = 12.dp.toPx()
        val collarX = 28.dp.toPx()
        if (hasBar) {
            val barH = 8.dp.toPx()
            drawRoundRect(P.borderStrong, Offset(0f, midY - barH / 2), Size(size.width, barH), CornerRadius(barH / 2))
            val collarW = 8.dp.toPx()
            val collarH = 28.dp.toPx()
            drawRoundRect(P.textTertiary, Offset(collarX - collarW, midY - collarH / 2), Size(collarW, collarH), CornerRadius(3.dp.toPx()))
        }
        var x = collarX + gap
        perSide.forEach { plate ->
            val ratio = (plate / heaviest).coerceIn(0f, 1f)
            val h = size.height * (0.3f + 0.7f * ratio)
            val color = lerp(P.accentSoft, P.accentPrimary, ratio)
            drawRoundRect(color, Offset(x, midY - h / 2), Size(plateWidth, h), CornerRadius(3.dp.toPx()))
            x += plateWidth + gap
        }
    }
}
