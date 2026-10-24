package com.example.gymtracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.gymtracker.watchlink.WatchAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchSyncTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        LocalStorage.resetForTests()
        context.deleteDatabase("gymtracker.db")
        context.filesDir.listFiles()?.forEach { it.delete() }
        LocalStorage.saveSettings(context, AppSettings(weightStep = 2.5f, restTimerSeconds = 90))
    }

    @After
    fun tearDown() = LocalStorage.resetForTests()

    private fun startWorkout() = LocalStorage.saveActiveSession(
        context, "Push", listOf(
            ExerciseEntry("Bench Press", sets = listOf(SetEntry(1, "80", "8", restSeconds = 120), SetEntry(2, "80", "8"))),
            ExerciseEntry("Plank", type = ExerciseType.DURATION, sets = listOf(SetEntry(1, "", "", seconds = "60")))
        )
    )

    @Test
    fun noWorkout_showsInactive() {
        assertFalse(watchStateOf(context).active)
    }

    @Test
    fun showsTheNextSet_formattedLikeTheWidget() {
        startWorkout()
        val s = watchStateOf(context)
        assertTrue(s.active)
        assertEquals("Push", s.workoutName)
        assertEquals("Bench Press", s.exerciseName)
        assertEquals("Set 1", s.setLabel)
        assertEquals("80 kg", s.primary)
        assertEquals("weight", s.primaryLabel)
        assertEquals("8", s.secondary)
        assertEquals("reps", s.secondaryLabel)
        assertEquals(3, s.setsLeft)
        assertEquals(0L, s.restEndsAtMillis)
    }

    @Test
    fun buttons_editTheSet_andCompletingStartsTheRest() {
        startWorkout()
        WatchSync.apply(context, WatchAction.PRIMARY_PLUS)
        WatchSync.apply(context, WatchAction.SECONDARY_MINUS)
        assertEquals("82.5 kg", watchStateOf(context).primary)
        assertEquals("7", watchStateOf(context).secondary)

        val before = System.currentTimeMillis()
        WatchSync.apply(context, WatchAction.COMPLETE_SET)
        val resting = watchStateOf(context)
        assertEquals("Set 2", resting.setLabel)
        assertEquals(120_000L, resting.restTotalMillis)
        assertTrue(resting.restEndsAtMillis >= before + 120_000L)
        // Taps on the set are ignored while resting (the watch shows the rest screen then).
        WatchSync.apply(context, WatchAction.PRIMARY_PLUS)
        assertEquals("80 kg", watchStateOf(context).primary)

        WatchSync.apply(context, WatchAction.ADD_REST)
        val longer = watchStateOf(context)
        assertEquals(resting.restEndsAtMillis + 15_000L, longer.restEndsAtMillis)
        assertEquals(135_000L, longer.restTotalMillis)

        WatchSync.apply(context, WatchAction.SKIP_REST)
        assertEquals(0L, watchStateOf(context).restEndsAtMillis)
    }

    @Test
    fun timeExercise_showsTimeOnly_andAllDoneHasNoExercise() {
        LocalStorage.saveActiveSession(context, "Core", listOf(ExerciseEntry("Plank", type = ExerciseType.DURATION, sets = listOf(SetEntry(1, "", "", seconds = "60", restSeconds = 0)))))
        val s = watchStateOf(context)
        assertEquals("1:00", s.primary)
        assertEquals("time", s.primaryLabel)
        assertNull(s.secondary)
        WatchSync.apply(context, WatchAction.COMPLETE_SET)
        assertNull(watchStateOf(context).exerciseName)
        assertTrue(watchStateOf(context).active)
    }
}
