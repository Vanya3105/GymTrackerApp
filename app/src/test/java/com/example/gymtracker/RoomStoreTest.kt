package com.example.gymtracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class RoomStoreTest {

    private lateinit var db: GymDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GymDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun set(n: Int, w: String, r: String, type: String = "NORMAL") =
        SetEntry(setNumber = n, weight = w, reps = r, intensityType = type, isWarmup = type == "WARMUP", isCompleted = true, restSeconds = 90)

    private fun workout(id: Long, vararg exercises: ExerciseEntry) = CompletedWorkout(
        id = id, splitTitle = "Push", dateKey = "2025-10-0$id", displayDate = "Oct $id, 2025 • 10:00", durationSeconds = 3600,
        bodyWeightKg = 80f, photoPath = null, skippedExercises = listOf("Cable Fly"), exercises = exercises.toList(),
        totalVolumeKg = 1000, prsHit = 1
    )

    private val bench = ExerciseEntry(
        "Barbell Bench Press", "Chest", restSeconds = 120,
        sets = listOf(set(1, "40", "10", "WARMUP"), set(2, "80", "6"), set(3, "", "")) // half-filled set survives
    )
    private val custom = ExerciseEntry("My Press", "Custom", isSuperset = true, sets = listOf(set(1, "20", "12")))

    private val full = StoreData(
        history = listOf(workout(2, bench), workout(1, bench, custom)),
        weights = listOf(WeightLogEntry(2, "b", "B", 81.2f), WeightLogEntry(1, "a", "A", 80.4f)),
        templates = mapOf("Push" to listOf("Barbell Bench Press" to "Chest", "My Press" to "Custom")),
        notes = mapOf("barbell bench press" to "grip wide"),
        restDefaults = mapOf("my press" to 75),
        types = mapOf("barbell bench press" to ExerciseType.WEIGHT_REPS, "my press" to ExerciseType.WEIGHT_REPS)
    )

    @Test
    fun replaceAll_thenLoad_returnsTheSameData() {
        RoomStore.replaceAll(db, full)
        assertEquals(full, RoomStore.load(db))
    }

    @Test
    fun replaceAll_replacesEverythingPreviouslyStored() {
        RoomStore.replaceAll(db, full)
        val smaller = StoreData(history = listOf(workout(9, custom)), types = mapOf("my press" to ExerciseType.WEIGHT_REPS))
        RoomStore.replaceAll(db, smaller)
        assertEquals(smaller, RoomStore.load(db))
    }

    @Test
    fun exerciseIdentity_isCaseInsensitive_andFirstSpellingWins() {
        val a = RoomStore.exerciseId(db, "My Press")
        assertEquals(a, RoomStore.exerciseId(db, "my press "))
        RoomStore.insertWorkout(db, workout(1, custom.copy(name = "MY PRESS")))
        assertEquals("My Press", RoomStore.load(db).history.single().exercises.single().name)
    }

    @Test
    fun libraryExercisesTakeLibrarySpelling() {
        RoomStore.setNote(db, "barbell bench press", "note")
        RoomStore.insertWorkout(db, workout(1, bench.copy(name = "barbell bench press")))
        assertEquals("Barbell Bench Press", RoomStore.load(db).history.single().exercises.single().name)
    }

    @Test
    fun insertWorkout_withSameId_replacesIt() {
        RoomStore.insertWorkout(db, workout(1, bench, custom))
        RoomStore.insertWorkout(db, workout(1, custom).copy(splitTitle = "Edited"))
        val stored = RoomStore.load(db).history.single()
        assertEquals("Edited", stored.splitTitle)
        assertEquals(listOf("My Press"), stored.exercises.map { it.name })
    }

    @Test
    fun deleteWorkout_removesItsExercisesAndSets() {
        RoomStore.insertWorkout(db, workout(1, bench, custom))
        RoomStore.deleteWorkout(db, 1)
        assertTrue(db.dao().workoutExercises().isEmpty())
        assertTrue(db.dao().workoutSets().isEmpty())
        assertEquals(2, db.dao().exercises().size) // exercise definitions (and their notes) stay
    }

    @Test
    fun rename_keepsHistoryTemplatesAndNotes() {
        RoomStore.replaceAll(db, full)
        assertTrue(RoomStore.renameExercise(db, "my press", "Landmine Press"))
        val after = RoomStore.load(db)
        assertEquals(listOf("Landmine Press"), after.history.last().exercises.drop(1).map { it.name })
        assertEquals("Landmine Press", after.templates.getValue("Push")[1].first)
        assertEquals(75, after.restDefaults["landmine press"])
    }

    @Test
    fun rename_refusesNameUsedByAnotherExercise_butAllowsCaseChange() {
        RoomStore.replaceAll(db, full)
        assertFalse(RoomStore.renameExercise(db, "My Press", "barbell bench press"))
        assertFalse(RoomStore.renameExercise(db, "My Press", "  "))
        assertTrue(RoomStore.renameExercise(db, "My Press", "MY PRESS"))
        assertEquals("MY PRESS", RoomStore.load(db).templates.getValue("Push")[1].first)
    }

    @Test
    fun typesTimeAndDistance_roundTrip() {
        val plank = ExerciseEntry("Plank", "Core", type = ExerciseType.DURATION,
            sets = listOf(SetEntry(1, "", "", seconds = "60", isCompleted = true)))
        val run = ExerciseEntry("Running", "Cardio", type = ExerciseType.DISTANCE_TIME,
            sets = listOf(SetEntry(1, "", "", seconds = "1500", distanceM = "5000", isCompleted = true)))
        val data = StoreData(
            history = listOf(workout(1, plank, run)),
            types = mapOf("plank" to ExerciseType.DURATION, "running" to ExerciseType.DISTANCE_TIME)
        )
        RoomStore.replaceAll(db, data)
        assertEquals(data, RoomStore.load(db))
    }

    @Test
    fun setType_keepsLoggedNumbers_soSwitchingBackRestoresTheView() {
        val pullUp = ExerciseEntry("Pull-Up", "Back", type = ExerciseType.WEIGHT_REPS, sets = listOf(set(1, "20", "8")))
        RoomStore.insertWorkout(db, workout(1, pullUp))
        RoomStore.setType(db, "pull-up", ExerciseType.WEIGHTED_BODYWEIGHT)
        val changed = RoomStore.load(db).history.single().exercises.single()
        assertEquals(ExerciseType.WEIGHTED_BODYWEIGHT, changed.type)
        assertEquals("20", changed.sets.single().weight)
        RoomStore.setType(db, "Pull-Up", ExerciseType.WEIGHT_REPS)
        assertEquals(pullUp, RoomStore.load(db).history.single().exercises.single())
    }

    @Test
    fun replaceAll_withUnknownTypes_appliesLibraryTypesWithoutHidingData() {
        RoomStore.replaceAll(db, StoreData(
            history = listOf(workout(1,
                ExerciseEntry("Pull-Up", sets = listOf(set(1, "20", "8"))),   // weighted → stays weight & reps
                ExerciseEntry("Push-Up", sets = listOf(set(1, "0", "15"))),   // no weight → bodyweight
                ExerciseEntry("Plank", sets = listOf(set(1, "0", "60")))      // logged as reps → stays
            )),
            templates = mapOf("Core" to listOf("Side Plank" to "Core")),       // never logged → library type
            types = null
        ))
        val types = RoomStore.load(db).types!!
        assertEquals(ExerciseType.WEIGHT_REPS, types["pull-up"])
        assertEquals(ExerciseType.BODYWEIGHT, types["push-up"])
        assertEquals(ExerciseType.WEIGHT_REPS, types["plank"])
        assertEquals(ExerciseType.DURATION, types["side plank"])
    }

    @Test
    fun newExercise_takesLibraryTypeOrTheGivenOne() {
        RoomStore.exerciseId(db, "Running")
        RoomStore.exerciseId(db, "Sled Push", type = ExerciseType.DISTANCE_TIME)
        val types = RoomStore.load(db).types!!
        assertEquals(ExerciseType.DISTANCE_TIME, types["running"])
        assertEquals(ExerciseType.DISTANCE_TIME, types["sled push"])
    }

    private val myPpl = SplitProgramInfo(
        id = "custom-1", name = "My PPL", tagline = "Mine", weeklyGoal = 5,
        splits = listOf(WorkoutSplit("Push", "Chest", "01"), WorkoutSplit("Pull", "Back", "02"))
    )

    @Test
    fun programs_roundTrip_inOrder() {
        val other = myPpl.copy(id = "custom-2", name = "Other", splits = listOf(WorkoutSplit("Legs", "", "01")))
        val data = StoreData(programs = listOf(other, myPpl))
        RoomStore.replaceAll(db, data)
        assertEquals(data.programs, RoomStore.load(db).programs)
        RoomStore.deleteProgram(db, "custom-2")
        assertEquals(listOf(myPpl), RoomStore.load(db).programs)
        assertTrue(db.dao().programSplits().none { it.programId == "custom-2" }) // days go with it
    }

    @Test
    fun renameSplit_movesTemplateAndHistory_orCopiesWhenStillUsed() {
        RoomStore.saveTemplate(db, "Push", listOf("Barbell Bench Press" to "Chest"))
        RoomStore.insertWorkout(db, workout(1, bench).copy(splitTitle = "Push"))

        RoomStore.renameSplit(db, "Push", "Push Day", keepTemplate = true, keepHistory = true)
        var data = RoomStore.load(db)
        assertEquals(data.templates["Push"], data.templates["Push Day"])
        assertEquals("Push", data.history.single().splitTitle)

        RoomStore.renameSplit(db, "Push", "Chest Day", keepTemplate = false, keepHistory = false)
        data = RoomStore.load(db)
        assertEquals(null, data.templates["Push"])
        assertEquals(listOf("Barbell Bench Press" to "Chest"), data.templates["Chest Day"])
        assertEquals("Chest Day", data.history.single().splitTitle)
    }

    @Test
    fun targets_roundTrip_withTheirDay() {
        val data = full.copy(targets = mapOf("Push" to mapOf("barbell bench press" to ExerciseTarget(4, 5, 8))))
        RoomStore.replaceAll(db, data)
        assertEquals(data, RoomStore.load(db))
        RoomStore.renameSplit(db, "Push", "Chest Day", keepTemplate = true, keepHistory = true)
        assertEquals(ExerciseTarget(4, 5, 8), RoomStore.load(db).targets["Chest Day"]?.get("barbell bench press"))
    }

    @Test
    fun mergedOver_keepsNewerEntriesAndAddsMissingOnes() {
        val room = StoreData(history = listOf(workout(3, custom), workout(1, custom).copy(splitTitle = "Room")))
        val json = StoreData(history = listOf(workout(2, bench), workout(1, bench)), notes = mapOf("x" to "y"))
        val merged = room.mergedOver(json)
        assertEquals(listOf(3L, 2L, 1L), merged.history.map { it.id })
        assertEquals("Room", merged.history.last().splitTitle)
        assertEquals("y", merged.notes["x"])
        assertNull(room.mergedOver(json.copy(types = null)).types) // unknown stays unknown
    }
}
