package com.example.gymtracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Forgotten workouts and one body weight everywhere. */
@RunWith(RobolectricTestRunner::class)
class EverydayFixesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        LocalStorage.resetForTests()
        context.deleteDatabase("gymtracker.db")
        context.filesDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() = LocalStorage.resetForTests()

    private val bench = ExerciseEntry("Bench Press", sets = listOf(SetEntry(1, "80", "8"), SetEntry(2, "80", "8")))

    @Test
    fun lastActivity_movesOnlyWhenTheWorkoutChanges() {
        LocalStorage.saveActiveSession(context, "Push", listOf(bench))
        val started = LocalStorage.getActiveSessionLastActivity(context)
        assertTrue(started > 0L)

        Thread.sleep(5)
        LocalStorage.saveActiveSession(context, "Push", listOf(bench))
        assertEquals(started, LocalStorage.getActiveSessionLastActivity(context))

        Thread.sleep(5)
        val ticked = bench.copy(sets = bench.sets.mapIndexed { i, s -> if (i == 0) s.copy(isCompleted = true) else s })
        LocalStorage.saveActiveSession(context, "Push", listOf(ticked))
        assertTrue(LocalStorage.getActiveSessionLastActivity(context) > started)
    }

    @Test
    fun keepGoing_countsAsActivity() {
        LocalStorage.saveActiveSession(context, "Push", listOf(bench))
        val started = LocalStorage.getActiveSessionLastActivity(context)
        Thread.sleep(5)
        LocalStorage.touchActiveSession(context)
        assertTrue(LocalStorage.getActiveSessionLastActivity(context) > started)
    }

    @Test
    fun editingDuringARest_keepsWhenTheRestStarted() {
        LocalStorage.saveActiveSession(context, "Push", listOf(bench))
        LocalStorage.setRestEndTimeMillis(context, System.currentTimeMillis() + 90_000L)
        val restStarted = LocalStorage.getRestStartedMillis(context)
        assertTrue(restStarted > 0L)
        LocalStorage.saveActiveSession(context, "Push", listOf(bench.copy(sets = bench.sets + SetEntry(3, "80", "8"))))
        assertEquals(restStarted, LocalStorage.getRestStartedMillis(context))
    }

    @Test
    fun forgottenWorkout_savesAsEndingAtItsLastChange() {
        val ticked = bench.copy(sets = bench.sets.map { it.copy(isCompleted = true) })
        LocalStorage.saveActiveSession(context, "Push", listOf(ticked))
        val start = LocalStorage.getActiveSessionStartTime(context)
        val end = start + 50 * 60_000L

        val saved = finishActiveWorkout(context, end, fromWidget = false)!!
        assertEquals(end, saved.id)
        assertEquals(50 * 60L, saved.durationSeconds)
        assertEquals(DateUtils.formatDayKey(java.util.Date(end)), saved.dateKey)
        assertNull(LocalStorage.loadActiveSession(context))
        assertEquals(listOf(end), LocalStorage.loadHistory(context).map { it.id })
    }

    @Test
    fun deletingTheNewestWeighIn_putsTheProfileBackToTheOneBefore() {
        LocalStorage.saveUserProfile(context, UserProfile(hasCompletedOnboarding = true, currentWeightKg = 80f))
        val older = LocalStorage.addWeightLog(context, 80f).first()
        Thread.sleep(5)
        val newer = LocalStorage.addWeightLog(context, 82f).first()
        assertEquals(82f, LocalStorage.loadUserProfile(context).currentWeightKg)

        LocalStorage.deleteWeightLog(context, newer.id)
        assertEquals(80f, LocalStorage.loadUserProfile(context).currentWeightKg)

        // An older one going doesn't change the current weight.
        Thread.sleep(5)
        LocalStorage.addWeightLog(context, 81f)
        LocalStorage.deleteWeightLog(context, older.id)
        assertEquals(81f, LocalStorage.loadUserProfile(context).currentWeightKg)
    }

    @Test
    fun profileWeightSetWithoutAWeighIn_isLoggedOnce() {
        LocalStorage.addWeightLog(context, 82.5f)
        // Changed in Profile by an older build, which didn't log it.
        LocalStorage.saveUserProfile(context, UserProfile(hasCompletedOnboarding = true, currentWeightKg = 81f))
        Thread.sleep(5)

        LocalStorage.reconcileProfileWeight(context)
        assertEquals(listOf(81f, 82.5f), LocalStorage.loadWeightHistory(context).map { it.weightKg })

        LocalStorage.saveUserProfile(context, UserProfile(hasCompletedOnboarding = true, currentWeightKg = 79f))
        LocalStorage.reconcileProfileWeight(context)
        assertEquals(2, LocalStorage.loadWeightHistory(context).size)
    }

    @Test
    fun matchingProfileWeight_logsNothing() {
        LocalStorage.addWeightLog(context, 82.5f)
        LocalStorage.reconcileProfileWeight(context)
        assertEquals(1, LocalStorage.loadWeightHistory(context).size)
    }
}
