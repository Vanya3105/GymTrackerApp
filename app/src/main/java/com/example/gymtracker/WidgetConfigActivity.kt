package com.example.gymtracker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/**
 * Settings for one placed widget (any of the three kinds): theme, background style, transparency
 * and what it shows, with a live preview. Opens when a widget is added or reconfigured.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, resultValue)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val kind = widgetKindFor(this, appWidgetId)
        val appTheme = LocalStorage.loadSettings(this).themeChoice

        setContent {
            GymTheme(paletteFor(this, appTheme)) {
                WidgetSettingsScreen(
                    appWidgetId = appWidgetId,
                    kind = kind,
                    onSave = { settings ->
                        LocalStorage.saveWidgetSettings(this, appWidgetId, settings)
                        LocalStorage.flush(this)
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun WidgetSettingsScreen(appWidgetId: Int, kind: String, onSave: (WidgetSettings) -> Unit) {
    val P = LocalPalette.current
    val context = LocalContext.current
    var settings by remember { mutableStateOf(LocalStorage.loadWidgetSettings(context, appWidgetId, kind)) }
    val title = when (kind) {
        WIDGET_KIND_RECOVERY -> "Recovery widget"
        WIDGET_KIND_WEEKLY -> "Weekly widget"
        else -> "Workout widget"
    }

    Column(Modifier.fillMaxSize().background(P.bgPrimary).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp)
        ) {
            Text(title, style = AppType.display, color = P.textPrimary)
            Text("Changes show in the preview straight away.", style = AppType.body, color = P.textSecondary)
            Spacer(Modifier.height(20.dp))

            WidgetPreview(kind, settings)
            Spacer(Modifier.height(20.dp))

            AppCard(Modifier.fillMaxWidth()) {
                Text("Theme", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf(THEME_SYSTEM to "Match app", THEME_LIGHT to "Light", THEME_DARK to "Dark"),
                    selected = settings.theme,
                    onSelect = { settings = settings.copy(theme = it) }
                )
                Spacer(Modifier.height(16.dp))
                Text("Background", style = AppType.label, color = P.textSecondary)
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf(WIDGET_STYLE_STANDARD to "Plain", WIDGET_STYLE_TINTED to "Tinted"),
                    selected = settings.style,
                    onSelect = { settings = settings.copy(style = it) }
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    Text("Opacity", style = AppType.label, color = P.textSecondary, modifier = Modifier.weight(1f))
                    Text("${settings.opacityPercent}%", style = AppType.label.copy(fontFeatureSettings = "tnum"), color = P.textPrimary)
                }
                Slider(
                    value = settings.opacityPercent.toFloat(),
                    onValueChange = { settings = settings.copy(opacityPercent = (it / 5f).roundToInt() * 5) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = P.accentPrimary,
                        activeTrackColor = P.accentPrimary,
                        inactiveTrackColor = P.bgControl
                    )
                )
            }
            Spacer(Modifier.height(14.dp))
            AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                widgetOptionLabels(kind).forEach { (key, label) ->
                    ToggleRow(label, checked = key in settings.options, onCheckedChange = { on ->
                        settings = settings.copy(options = if (on) settings.options + key else settings.options - key)
                    })
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        PrimaryButton(
            "Save",
            onClick = { onSave(settings) },
            icon = Icons.Rounded.Check,
            modifier = Modifier.padding(20.dp).fillMaxWidth()
        )
    }
}

/** The real widget, drawn over a wallpaper-like backdrop so transparency is visible. */
@Composable
private fun WidgetPreview(kind: String, settings: WidgetSettings) {
    val context = LocalContext.current
    val (wDp, hDp) = when (kind) {
        WIDGET_KIND_RECOVERY -> 320 to 150
        WIDGET_KIND_WEEKLY -> 170 to 170
        else -> 300 to 150
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6E8CA8), Color(0xFFB98C6E), Color(0xFF3E4A5C)))),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        if (kind == WIDGET_KIND_WORKOUT) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    frame.addView(buildWorkoutWidgetViews(context, settings, compact = false).apply(context, frame))
                },
                modifier = Modifier.size(wDp.dp, hDp.dp)
            )
        } else {
            val bitmap = remember(settings, kind) {
                if (kind == WIDGET_KIND_RECOVERY) MuscleFatigueWidgetProvider.render(context, settings, wDp, hDp)
                else SquarePulseWidgetProvider.render(context, settings, wDp, hDp)
            }
            Image(bitmap.asImageBitmap(), contentDescription = "Widget preview", contentScale = ContentScale.Fit, modifier = Modifier.size(wDp.dp, hDp.dp))
        }
    }
}
