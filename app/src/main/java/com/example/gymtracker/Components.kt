package com.example.gymtracker

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

// ============================================================================
// PRESS FEEDBACK
// ============================================================================

/** Clickable with ripple plus a small springy scale-down while pressed. */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press_scale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, role = role, onClick = onClick)
}

// ============================================================================
// SURFACES
// ============================================================================

val CardShape = RoundedCornerShape(20.dp)

/**
 * The app's base card. Light mode lifts it with a soft warm shadow; dark mode separates it with a
 * slightly lighter tone and a hairline border (shadows don't read on dark backgrounds).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color? = null,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val P = LocalPalette.current
    val bg = color ?: if (elevated) P.bgElevated else P.bgCard
    val shadowed = if (!P.isDark) {
        modifier.shadow(
            elevation = if (elevated) 10.dp else 4.dp,
            shape = shape,
            ambientColor = P.shadow.copy(alpha = 0.10f),
            spotColor = P.shadow.copy(alpha = 0.14f)
        )
    } else modifier
    Column(
        modifier = shadowed
            .clip(shape)
            .background(bg)
            .border(1.dp, if (P.isDark) P.borderSubtle else P.borderSubtle.copy(alpha = 0.6f), shape)
            .then(if (onClick != null) Modifier.pressable(pressedScale = 0.985f, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp
) {
    val P = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    val bg = if (enabled) {
        Brush.verticalGradient(listOf(P.accentPrimary, P.accentPrimary.darken(0.08f)))
    } else SolidColor(P.bgControl)
    Row(
        modifier = modifier
            .height(height)
            .then(
                if (enabled && !P.isDark) Modifier.shadow(8.dp, shape, spotColor = P.accentPrimary.copy(alpha = 0.35f))
                else Modifier
            )
            .clip(shape)
            .background(bg)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (enabled) P.onAccentPrimary else P.textTertiary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = AppType.bodyStrong, color = fg)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    height: Dp = 48.dp
) {
    val P = LocalPalette.current
    val fg = if (enabled) contentColor ?: P.textPrimary else P.textTertiary
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(P.bgControl)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = AppType.label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color? = null,
    container: Color? = null,
    enabled: Boolean = true
) {
    val P = LocalPalette.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container ?: P.bgControl)
            .pressable(enabled = enabled, pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription,
            tint = if (enabled) tint ?: P.textPrimary else P.textTertiary,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.textSecondary,
    container: Color = LocalPalette.current.bgControl,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = AppType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = color, maxLines = 1)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val P = LocalPalette.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AppType.headline, color = P.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = AppType.label,
                color = P.accentPrimary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val P = LocalPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(P.bgControl)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RollingText(value, style = AppType.number.copy(fontSize = AppType.headline.fontSize), color = valueColor ?: P.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = AppType.caption, color = P.textSecondary, maxLines = 1)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(P.bgControl),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = P.textSecondary, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = AppType.bodyStrong, color = P.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, style = AppType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = P.textSecondary, textAlign = TextAlign.Center)
    }
}

// ============================================================================
// CONTROLS
// ============================================================================

/** Segmented control whose highlight slides between options. */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val P = LocalPalette.current
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(P.bgControl)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segment_indicator"
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(if (P.isDark) 0.dp else 2.dp, RoundedCornerShape(11.dp), spotColor = P.shadow.copy(alpha = 0.2f))
                .clip(RoundedCornerShape(11.dp))
                .background(if (P.isDark) P.borderStrong else P.bgCard)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, (value, label) ->
                val isSel = i == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) { onSelect(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = AppType.label, color = if (isSel) P.textPrimary else P.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val P = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(pressedScale = 0.99f, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.body, color = P.textPrimary)
            if (subtitle != null) Text(subtitle, style = AppType.caption, color = P.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = P.onAccentPrimary,
                checkedTrackColor = P.accentPrimary,
                uncheckedThumbColor = P.textSecondary,
                uncheckedTrackColor = P.bgControl,
                uncheckedBorderColor = P.borderStrong
            )
        )
    }
}

/** − value + control. The middle can be typed into. */
@Composable
fun NumberStepper(
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    height: Dp = 40.dp
) {
    val P = LocalPalette.current
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(P.bgControl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(Icons.Rounded.Remove, "Decrease", enabled, P.textSecondary, onMinus)
        if (enabled) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = AppType.number.copy(color = P.textPrimary, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(P.accentPrimary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) Text("0", style = AppType.number, color = P.textTertiary)
                        inner()
                    }
                }
            )
        } else {
            Text(value.ifEmpty { "0" }, style = AppType.number, color = P.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        StepperButton(Icons.Rounded.Add, "Increase", enabled, P.accentPrimary, onPlus)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    val P = LocalPalette.current
    Box(
        Modifier
            .width(34.dp)
            .fillMaxHeight()
            .pressable(enabled = enabled, pressedScale = 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (enabled) tint else P.textTertiary, modifier = Modifier.size(18.dp))
    }
}

// ============================================================================
// DATA VISUALS
// ============================================================================

/** Text that rolls vertically when its value changes (counts, timers). */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(220)))
                .togetherWith(slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(150)))
        },
        modifier = modifier,
        label = "rolling_text"
    ) { value -> Text(value, style = style, color = color, maxLines = 1) }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    color: Color = LocalPalette.current.accentPrimary,
    trackColor: Color = LocalPalette.current.bgControl,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow),
        label = "ring"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun AnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = LocalPalette.current.accentPrimary,
    trackColor: Color = LocalPalette.current.bgControl
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(trackColor)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/**
 * [NumberStepper] for a weight stored as a kg string but shown and typed in the current unit.
 * Keeps its own text while typing so half-typed input ("62.") survives the kg round trip.
 */
@Composable
fun WeightStepper(
    storedKg: String,
    onChange: (String) -> Unit,
    step: Float,
    modifier: Modifier = Modifier,
    minKg: Float = 0f,
    fallbackKg: Float = 20f,
    height: Dp = 40.dp,
    onStep: () -> Unit = {}
) {
    val unit = Units.current
    val shown = if (storedKg.isBlank()) "" else unit.number(parseWeightClean(storedKg, fallbackKg))
    var text by remember(unit) { mutableStateOf(shown) }
    LaunchedEffect(shown) { if (text.toFloatOrNull() != shown.toFloatOrNull()) text = shown }

    fun stepBy(delta: Float) {
        onStep()
        onChange(stepWeight(storedKg, delta, fallbackKg, minKg))
    }

    NumberStepper(
        value = text,
        onValueChange = { v ->
            sanitizeDecimalInput(v)?.let { t ->
                text = t
                onChange(t.toFloatOrNull()?.let(unit::stored) ?: "")
            }
        },
        onMinus = { stepBy(-step) },
        onPlus = { stepBy(step) },
        modifier = modifier,
        height = height
    )
}

/** Smooth line chart with a soft fill that draws itself in from left to right. */
@Composable
fun TrendChart(points: List<Pair<String, Float>>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val P = LocalPalette.current
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    val maxV = points.maxOf { it.second }
    val minV = points.minOf { it.second }
    val range = (maxV - minV).takeIf { it > 0f } ?: 1f

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val padY = 10.dp.toPx()
            val padX = 6.dp.toPx()
            val stepX = (size.width - padX * 2) / (points.size - 1)
            val pts = points.mapIndexed { i, (_, v) ->
                Offset(padX + i * stepX, size.height - padY - (v - minV) / range * (size.height - padY * 2))
            }
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val prev = pts[i - 1]
                    val cur = pts[i]
                    val midX = (prev.x + cur.x) / 2
                    cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            clipRect(right = size.width * reveal.value + 6.dp.toPx()) {
                drawPath(fill, Brush.verticalGradient(listOf(P.accentPrimary.copy(alpha = 0.22f), Color.Transparent)))
                drawPath(line, P.accentPrimary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                pts.forEach { pt ->
                    drawCircle(P.bgCard, 5.dp.toPx(), pt)
                    drawCircle(P.accentPrimary, 3.dp.toPx(), pt)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { (label, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatWeightNumber(v), style = AppType.caption.copy(fontFeatureSettings = "tnum"), color = P.textPrimary)
                    Text(label, style = AppType.caption, color = P.textTertiary)
                }
            }
        }
    }
}

// ============================================================================
// MOTION HELPERS
// ============================================================================

/** Fades and lifts content in on first appearance, staggered by [index]. */
fun Modifier.enterAnimation(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(40L * index.coerceAtMost(8))
        progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

// ============================================================================
// BOTTOM SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val P = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        containerColor = P.bgElevated,
        contentColor = P.textPrimary,
        scrimColor = Color.Black.copy(alpha = if (P.isDark) 0.6f else 0.35f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(P.borderStrong)
            )
        },
        content = content
    )
}

// ============================================================================
// COLOR HELPERS
// ============================================================================

fun Color.darken(amount: Float) = Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)
fun Color.lighten(amount: Float) = Color(red + (1 - red) * amount, green + (1 - green) * amount, blue + (1 - blue) * amount, alpha)

fun toneColors(tone: SuggestionTone, P: GymPalette): Pair<Color, Color> = when (tone) {
    SuggestionTone.GOOD -> P.successSurface to P.successText
    SuggestionTone.CAUTION -> P.warningSurface to P.warningText
    SuggestionTone.NEUTRAL -> P.accentSecondarySoft to P.accentSecondary
}

// ============================================================================
// FIRST-RUN TIPS (spotlight overlay)
// ============================================================================

@Immutable
data class TutorialStepInfo(val key: String, val title: String, val description: String)

object TutorialPrefs {
    private const val PREFS_NAME = "gym_tracker_tutorial_prefs"
    private const val KEY_HOME_DONE = "has_seen_home_tutorial_v2"
    private const val KEY_WORKOUT_DONE = "has_seen_workout_tutorial_v2"

    var replayGeneration by mutableIntStateOf(0)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeenHomeTutorial(context: Context) = prefs(context).getBoolean(KEY_HOME_DONE, false)
    fun markHomeTutorialSeen(context: Context) = prefs(context).edit().putBoolean(KEY_HOME_DONE, true).apply()
    fun hasSeenWorkoutTutorial(context: Context) = prefs(context).getBoolean(KEY_WORKOUT_DONE, false)
    fun markWorkoutTutorialSeen(context: Context) = prefs(context).edit().putBoolean(KEY_WORKOUT_DONE, true).apply()

    fun resetAllTutorials(context: Context) {
        prefs(context).edit().putBoolean(KEY_HOME_DONE, false).putBoolean(KEY_WORKOUT_DONE, false).apply()
        replayGeneration++
    }
}

@Composable
fun SpotlightTutorialOverlay(
    steps: List<TutorialStepInfo>,
    currentStepIndex: Int,
    targetBounds: Map<String, Rect>,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit
) {
    if (currentStepIndex !in steps.indices) return
    val P = LocalPalette.current
    val step = steps[currentStepIndex]
    val rect = targetBounds[step.key]

    // The highlight glides between targets instead of jumping.
    val left by animateFloatAsState(rect?.left ?: 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "hl_l")
    val top by animateFloatAsState(rect?.top ?: 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "hl_t")
    val width by animateFloatAsState(rect?.width ?: 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "hl_w")
    val height by animateFloatAsState(rect?.height ?: 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "hl_h")

    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val cardAtBottom = rect == null || rect.center.y < screenHeightPx * 0.52f

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(100f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            if (width > 0f && height > 0f) {
                val pad = 8.dp.toPx()
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left - pad, top - pad),
                    size = Size(width + pad * 2, height + pad * 2),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = if (cardAtBottom) Arrangement.Bottom else Arrangement.Top
        ) {
            AppCard(elevated = true, contentPadding = PaddingValues(20.dp)) {
                Text("Tip ${currentStepIndex + 1} of ${steps.size}", style = AppType.overline, color = P.accentPrimary)
                Spacer(Modifier.height(6.dp))
                AnimatedContent(targetState = step, label = "tip") { s ->
                    Column {
                        Text(s.title, style = AppType.headline, color = P.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(s.description, style = AppType.body, color = P.textSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Skip",
                        style = AppType.label,
                        color = P.textSecondary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onSkip).padding(8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    if (currentStepIndex > 0) {
                        SecondaryButton("Back", onPrev, height = 44.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    PrimaryButton(if (currentStepIndex == steps.lastIndex) "Got it" else "Next", onNext, height = 44.dp)
                }
            }
        }
    }
}
