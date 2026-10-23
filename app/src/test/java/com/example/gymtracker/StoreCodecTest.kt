package com.example.gymtracker

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCodecTest {

    private val sample = CompletedWorkout(
        id = 1700000000000, splitTitle = "Push", dateKey = "2025-10-12", displayDate = "Oct 12, 2025 • 10:00",
        durationSeconds = 3600, bodyWeightKg = 80.5f, photoPath = "/photos/a.jpg", skippedExercises = listOf("Fly"),
        exercises = listOf(
            ExerciseEntry(
                name = "Bench Press", targetMuscle = "Chest", restSeconds = 120, isSuperset = true,
                sets = listOf(
                    SetEntry(1, "40", "10", isWarmup = true, intensityType = "WARMUP", isCompleted = true, restSeconds = 60),
                    SetEntry(2, "62.5", "8", intensityType = "DROP", isCompleted = true, restSeconds = 120)
                )
            ),
            ExerciseEntry(name = "Fly", targetMuscle = "Chest", isSkipped = true)
        ),
        totalVolumeKg = 500, prsHit = 1
    )

    @Test
    fun workout_roundTripsUnchanged() {
        val arr = JSONArray().put(StoreCodec.serializeWorkout(sample))
        // Through a string too, exactly like the file on disk.
        assertEquals(listOf(sample), StoreCodec.parseHistory(JSONArray(arr.toString())))
    }

    @Test
    fun parseHistory_sortsNewestFirstAndHandlesMissingArray() {
        val arr = JSONArray()
            .put(StoreCodec.serializeWorkout(sample.copy(id = 1)))
            .put(StoreCodec.serializeWorkout(sample.copy(id = 3)))
            .put(StoreCodec.serializeWorkout(sample.copy(id = 2)))
        assertEquals(listOf(3L, 2L, 1L), StoreCodec.parseHistory(arr).map { it.id })
        assertTrue(StoreCodec.parseHistory(null).isEmpty())
    }

    @Test
    fun parseHistory_fillsDefaultsForMinimalOldWorkouts() {
        val w = StoreCodec.parseHistory(JSONArray("""[{"id": 5, "splitTitle": "Pull"}]""")).single()
        assertEquals("Pull", w.splitTitle)
        assertNull(w.photoPath) // stored as "" or missing → null
        assertTrue(w.exercises.isEmpty())
        assertTrue(w.skippedExercises.isEmpty())
        assertEquals(0, w.totalVolumeKg)
    }

    @Test
    fun deserializeExercises_readsLegacySetFormat() {
        // Early versions: numbers with unit suffixes, no intensityType, no per-set rest.
        val json = """[{"name": "Row", "restSeconds": 75, "sets": [
            {"weight": "60kg", "reps": "8r", "isWarmup": true},
            {"weight": 62.5, "reps": 6}
        ]}]"""
        val ex = StoreCodec.deserializeExercises(JSONArray(json)).single()
        val (warm, work) = ex.sets
        assertEquals("60", warm.weight)
        assertEquals("8", warm.reps)
        assertEquals("WARMUP", warm.intensityType)
        assertTrue(warm.isWarmupSet)
        assertEquals(1, warm.setNumber)
        assertEquals(75, warm.restSeconds) // falls back to exercise rest
        assertEquals("62.5", work.weight)
        assertEquals("6", work.reps)
        assertEquals("NORMAL", work.intensityType)
        assertFalse(work.isWarmupSet)
        assertEquals(2, work.setNumber)
    }

    @Test
    fun deserializeExercises_intensityTypeWarmupImpliesWarmupFlag() {
        val json = """[{"name": "Row", "sets": [{"weight": "40", "reps": "10", "intensityType": "WARMUP"}]}]"""
        assertTrue(StoreCodec.deserializeExercises(JSONArray(json)).single().sets.single().isWarmup)
    }

    @Test
    fun serializeExercises_stripsUnitSuffixes() {
        val ex = ExerciseEntry("Curl", sets = listOf(SetEntry(1, "15 kg", "12r")))
        val set = StoreCodec.serializeExercises(listOf(ex)).getJSONObject(0).getJSONArray("sets").getJSONObject(0)
        assertEquals("15", set.getString("weight"))
        assertEquals("12", set.getString("reps"))
    }

    @Test
    fun timeAndDistanceSets_roundTrip_andOldSetsGetEmptyValues() {
        val ex = ExerciseEntry("Running", sets = listOf(SetEntry(1, "", "", seconds = "1500", distanceM = "5000")))
        val back = StoreCodec.deserializeExercises(StoreCodec.serializeExercises(listOf(ex))).single().sets.single()
        assertEquals("1500", back.seconds)
        assertEquals("5000", back.distanceM)
        val old = StoreCodec.deserializeExercises(JSONArray("""[{"name": "Row", "sets": [{"weight": "60", "reps": "8"}]}]""")).single().sets.single()
        assertEquals("", old.seconds)
        assertEquals("", old.distanceM)
    }

    @Test
    fun exerciseTypes_areWrittenAndRead_andUnknownWhenMissing() {
        val root = JSONObject()
        StoreCodec.writeData(root, StoreData(types = mapOf("plank" to ExerciseType.DURATION)))
        assertEquals(mapOf("plank" to ExerciseType.DURATION), StoreCodec.readData(JSONObject(root.toString())).types)
        assertNull(StoreCodec.readData(JSONObject("""{"history": []}""")).types)
    }

    @Test
    fun exportedBackupShape_isReadable() {
        // Guards the backup format users already have on disk.
        val root = JSONObject().put("history", JSONArray().put(StoreCodec.serializeWorkout(sample)))
        val restored = StoreCodec.parseHistory(JSONObject(root.toString(2)).optJSONArray("history"))
        assertEquals(sample, restored.single())
    }
}
