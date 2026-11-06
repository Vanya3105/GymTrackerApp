package com.example.gymtracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The app's one way to say something briefly ("Backup saved", "Workout deleted · Undo"), in place
 * of Android toasts, which look like the system's and can't offer Undo. Callable from anywhere on
 * the main thread. A new message replaces the one showing.
 */
object AppSnackbar {
    class Message(val text: String, val action: String?, val onAction: (() -> Unit)?, val id: Long)

    var current by mutableStateOf<Message?>(null)
        private set
    private var nextId = 0L

    // Bottom sheets are windows of their own, drawn over the screen's host: the newest host shows.
    internal val hosts = mutableStateListOf<Any>()

    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        current = Message(text, action, onAction, ++nextId)
    }

    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

/** Long enough to read and reach Undo; plain messages go sooner. */
private const val SNACKBAR_MS = 3_000L
private const val SNACKBAR_WITH_ACTION_MS = 8_000L

@Composable
fun AppSnackbarHost(modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    val token = remember { Any() }
    DisposableEffect(token) {
        AppSnackbar.hosts.add(token)
        onDispose { AppSnackbar.hosts.remove(token) }
    }
    val message = AppSnackbar.current.takeIf { AppSnackbar.hosts.lastOrNull() === token }

    // Android lengthens this for people who need more time (TalkBack, the accessibility timeout setting).
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message?.id) {
        val m = message ?: return@LaunchedEffect
        val base = if (m.action != null) SNACKBAR_WITH_ACTION_MS else SNACKBAR_MS
        delay(accessibility?.calculateRecommendedTimeoutMillis(base, containsIcons = false, containsText = true, containsControls = m.action != null) ?: base)
        AppSnackbar.dismiss(m.id)
    }

    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedContent(
            targetState = message,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(150)))
                    .togetherWith(slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(150)))
            },
            contentKey = { it?.id },
            label = "snackbar"
        ) { m ->
            if (m == null) return@AnimatedContent
            var dragX by remember { mutableFloatStateOf(0f) }
            Row(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = dragX
                        alpha = 1f - (abs(dragX) / 600f).coerceIn(0f, 0.8f)
                    }
                    // Swiped sideways, it goes away, like Android's own.
                    .draggable(
                        rememberDraggableState { dragX += it },
                        Orientation.Horizontal,
                        onDragStopped = { if (abs(dragX) > 150f) AppSnackbar.dismiss(m.id) else dragX = 0f }
                    )
                    .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = P.shadow.copy(alpha = 0.25f))
                    .clip(RoundedCornerShape(14.dp))
                    // Inverted, like a selected chip: stands apart from cards in both themes.
                    .background(P.textPrimary)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(start = 16.dp, end = if (m.action != null) 4.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(m.text, style = AppType.body, color = P.bgPrimary, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
                if (m.action != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        m.action,
                        style = AppType.label,
                        color = if (P.isDark) P.accentPrimary.darken(0.15f) else P.accentPrimary.lighten(0.1f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pressable(pressedScale = 0.94f) {
                                AppSnackbar.dismiss(m.id)
                                m.onAction?.invoke()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
