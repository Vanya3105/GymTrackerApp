package com.example.gymtracker.wear

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.DeviceParametersBuilders
import com.example.gymtracker.watchlink.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WatchSurfacesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val now = System.currentTimeMillis()
    private val set = WatchState(
        active = true, workoutName = "Push", exerciseName = "Bench Press", setLabel = "Set 2",
        primary = "80 kg", primaryLabel = "weight", secondary = "8", secondaryLabel = "reps", summary = "80 kg × 8", setsLeft = 5
    )
    private val device = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(192).setScreenHeightDp(192).setScreenDensity(2f)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
        .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
        .build()

    @Test
    fun tileBuildsForEveryState() {
        val resting = set.copy(restEndsAtMillis = now + 60_000, restTotalMillis = 90_000)
        val idle = WatchState(active = false, days = listOf("Pull", "Push"))
        for (state in listOf(null, WatchState(active = false), idle, set, resting, set.copy(exerciseName = null))) {
            assertNotNull(workoutTile(app, state, device, now).tileTimeline)
        }
        // A rest's end changes the tile, so it asks to be refreshed then.
        assertTrue(workoutTile(app, resting, device, now).freshnessIntervalMillis in 60_000L..62_000L)
        assertEquals(0L, workoutTile(app, set, device, now).freshnessIntervalMillis)
    }

    @Test
    fun watchFaceActivity_followsTheWorkout() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = app.getSystemService(NotificationManager::class.java)

        WorkoutOngoing.update(app, set)
        val shown = shadowOf(nm).allNotifications.single()
        assertTrue(shown.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(NotificationCompat.CATEGORY_WORKOUT, shown.category)
        assertEquals("Bench Press · Set 2", shown.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())

        WorkoutOngoing.update(app, set.copy(restEndsAtMillis = now + 60_000))
        assertEquals("Resting", shadowOf(nm).allNotifications.single().extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())

        WorkoutOngoing.update(app, WatchState(active = false))
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }
}
