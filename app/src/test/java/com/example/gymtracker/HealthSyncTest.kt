package com.example.gymtracker

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HealthSyncTest {
    private fun set(w: String, r: String, warmup: Boolean = false) =
        SetEntry(1, w, r, isWarmup = warmup, intensityType = if (warmup) "WARMUP" else "NORMAL", isCompleted = true)

    private val workout = CompletedWorkout(
        id = 1_760_000_000_000,
        splitTitle = "Push",
        dateKey = "2025-10-09",
        displayDate = "",
        durationSeconds = 3_600,
        bodyWeightKg = 80f,
        photoPath = null,
        skippedExercises = listOf("Dips"),
        exercises = listOf(
            ExerciseEntry("Bench Press", sets = listOf(set("40", "10", warmup = true), set("80", "8"), set("80", "7"))),
            ExerciseEntry("Dips", isSkipped = true, sets = listOf(set("0", "10"))),
            ExerciseEntry("Plank", type = ExerciseType.DURATION, sets = listOf(SetEntry(1, "", "", seconds = "90", isCompleted = true)))
        )
    )

    @Test
    fun workoutBecomesAStrengthSession_endingWhenItWasFinished() {
        val record = HealthSync.workoutRecord(workout, version = 5)
        assertEquals(Instant.ofEpochMilli(workout.id), record.endTime)
        assertEquals(Instant.ofEpochMilli(workout.id).minusSeconds(3_600), record.startTime)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, record.exerciseType)
        assertEquals("Push", record.title)
        assertEquals("workout-${workout.id}", record.metadata.clientRecordId)
        assertEquals(5L, record.metadata.clientRecordVersion)
    }

    @Test
    fun notesListWorkSets_skippingWarmupsAndSkippedExercises() {
        assertEquals("Bench Press: 80 kg × 8, 80 kg × 7\nPlank: 1:30", HealthSync.workoutNotes(workout))
    }

    @Test
    fun aWorkoutWithoutATimer_stillGetsAValidTimeRange() {
        val (start, end) = HealthSync.workoutTimes(workout.copy(durationSeconds = 0))
        assertEquals(60, end.epochSecond - start.epochSecond)
    }

    @Test
    fun heartRateSummary() {
        assertNull(HealthSync.summarizeHeartRate(emptyList()))
        assertEquals(HeartRateSummary(average = 120, max = 150), HealthSync.summarizeHeartRate(listOf(100, 110, 120, 150, 120)))
    }

    @Test
    fun weighInsKeepTheirTimeAndRoundTo10Grams() {
        val entry = HealthSync.weightEntryFrom(Instant.ofEpochMilli(1_760_000_000_000), 80.4567)
        assertEquals(1_760_000_000_000, entry.id)
        assertEquals(80.46f, entry.weightKg)
        assertEquals("weight-${entry.id}", HealthSync.weightRecord(entry).metadata.clientRecordId)
    }
}
