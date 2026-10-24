package com.example.gymtracker.wear

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import com.example.gymtracker.watchlink.WatchState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the watch screens on round displays and saves PNGs to wear/build/screenshots, to check
 * the layout without an emulator. Small = 40 mm Galaxy Watch, large = 44–47 mm.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_800_000_000_000L
    private val set = WatchState(
        active = true, workoutName = "Push", exerciseName = "Incline Dumbbell Bench Press", setLabel = "Set 2",
        primary = "102.5 kg", primaryLabel = "weight", secondary = "10", secondaryLabel = "reps",
        summary = "102.5 kg × 10", setsLeft = 11
    )
    private val resting = set.copy(restEndsAtMillis = now + 75_000, restTotalMillis = 120_000)

    private fun shot(name: String, state: WatchState?, large: Boolean = false, connected: Boolean = true, waiting: Boolean = false) {
        RuntimeEnvironment.setQualifiers(if (large) "w227dp-h227dp-round-xhdpi" else "w192dp-h192dp-round-xhdpi")
        compose.setContent { WatchScreen(state, now, connected, waiting, onAction = {}, onOpenOnPhone = {}) }
        compose.waitForIdle()
        // Drawn directly: captureToImage waits for a window frame Robolectric never reports.
        val bitmap = compose.activity.window.decorView.drawToBitmap()
        File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun set() = shot("set-small", set)
    @Test fun setLarge() = shot("set-large", set, large = true)
    @Test fun bodyweight() = shot("bodyweight-small", set.copy(exerciseName = "Pull-Up", primary = "12 reps", primaryLabel = "reps", secondary = null, secondaryLabel = null))
    @Test fun waiting() = shot("waiting-small", set, waiting = true)
    @Test fun rest() = shot("rest-small", resting)
    @Test fun restLarge() = shot("rest-large", resting, large = true)
    @Test fun noWorkout() = shot("no-workout-small", WatchState(active = false))
    @Test fun allDone() = shot("all-done-small", set.copy(exerciseName = null))
    @Test fun noPhone() = shot("no-phone-small", null, connected = false)
}
