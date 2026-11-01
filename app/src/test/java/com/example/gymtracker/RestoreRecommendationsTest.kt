package com.example.gymtracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A restored backup brings back the days as they were, not as the answers on this phone would fit them. */
@RunWith(RobolectricTestRunner::class)
class RestoreRecommendationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gym = UserProfile(
        hasCompletedOnboarding = true, age = 30, currentWeightKg = 80f, splitProgram = "FULL_BODY",
        experience = Experience.INTERMEDIATE, goal = TrainingGoal.MUSCLE, equipment = Equipment.GYM, daysPerWeek = 3
    )
    private val bodyweight = gym.copy(equipment = Equipment.BODYWEIGHT)

    @Before
    fun setUp() {
        LocalStorage.resetForTests()
        context.deleteDatabase("gymtracker.db")
        context.filesDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() = LocalStorage.resetForTests()

    private fun days() = programFor("FULL_BODY").splits.map { LocalStorage.loadTemplateForSplit(context, it.title).map { e -> e.first } }

    @Test
    fun restoringAGymBackupBringsBackGymDays() {
        LocalStorage.saveUserProfile(context, gym)
        // Only the day that was shown got saved before the backup; the others are worked out when needed.
        LocalStorage.loadTemplateForSplit(context, "Full Body A")
        val gymDays = days()
        val backup = LocalStorage.exportJsonBackupString(context)

        // This phone moved to bodyweight training and its days were fitted to that.
        days()
        LocalStorage.saveUserProfile(context, bodyweight)
        LocalStorage.refitBuiltInDays(context, gym, bodyweight)

        LocalStorage.importJsonBackupString(context, backup)
        LocalStorage.loadUserProfile(context)
        assertEquals(gymDays, days())
    }

    @Test
    fun restoringAnOldBackupWithoutAnswersBringsBackTheBuiltInDays() {
        LocalStorage.saveUserProfile(context, gym.copy(experience = null, goal = null, equipment = null, daysPerWeek = 0))
        val plainDays = days()
        val backup = LocalStorage.exportJsonBackupString(context)

        LocalStorage.saveUserProfile(context, bodyweight)
        LocalStorage.refitBuiltInDays(context, UserProfile(), bodyweight)
        days()

        LocalStorage.importJsonBackupString(context, backup)
        LocalStorage.loadUserProfile(context)
        assertEquals(plainDays, days())
    }
}
