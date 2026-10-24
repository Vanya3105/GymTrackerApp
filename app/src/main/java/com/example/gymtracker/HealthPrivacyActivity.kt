package com.example.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Health Connect opens this from its permission screen ("Read privacy policy"); it's required
 * before Health Connect will show the app's permission request at all.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val palette = paletteFor(this, LocalStorage.loadSettings(this).themeChoice)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
        setContent {
            GymTheme(palette) { HealthPrivacyScreen(onDone = ::finish) }
        }
    }
}

@Composable
private fun HealthPrivacyScreen(onDone: () -> Unit) {
    val P = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(P.bgPrimary)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Your health data", style = AppType.display, color = P.textPrimary)
        Spacer(Modifier.height(16.dp))
        listOf(
            "GymTracker works offline. It has no account and no server, and it never sends your data anywhere.",
            "When you connect Health Connect, GymTracker:",
            "• writes each workout you finish (name, start and end time, your sets as text) and the body weights you log, so they show up in apps like Samsung Health and Google Fit;",
            "• reads body weights from other apps and scales into your weight log;",
            "• reads the heart rate recorded during a workout, to show its average and maximum in your history.",
            "Nothing else is read. What GymTracker reads stays on this phone and is only used for those screens.",
            "You can stop at any time in Profile → Health Connect, or change what's shared in Health Connect's own settings. Data already written stays in Health Connect until you delete it there."
        ).forEach {
            Text(it, style = AppType.body, color = P.textSecondary)
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Done", onDone, Modifier.fillMaxWidth())
    }
}
