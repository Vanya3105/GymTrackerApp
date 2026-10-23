package com.example.gymtracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** End-to-end: a real v3 store file on disk → LocalStorage → Room. */
@RunWith(RobolectricTestRunner::class)
class StorageMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storeFile get() = File(context.filesDir, "gym_tracker_store_v2.json")
    private val v3Json = javaClass.classLoader!!.getResource("store_v3.json")!!.readText()

    @Before
    fun setUp() {
        LocalStorage.resetForTests()
        context.deleteDatabase("gymtracker.db")
        context.filesDir.listFiles()?.forEach { it.delete() }
        storeFile.writeText(v3Json)
    }

    @After
    fun tearDown() = LocalStorage.resetForTests()

    private fun restart() {
        LocalStorage.flush(context)
        LocalStorage.resetForTests()
    }

    @Test
    fun migratesEverySection() {
        val history = LocalStorage.loadHistory(context)
        assertEquals(listOf(1759500000000, 1759000000000), history.map { it.id })

        val push = history.last()
        assertEquals(listOf("Bench Press", "Cable Fly", "My Weird Press"), push.exercises.map { it.name })
        assertEquals(listOf("40", "80", "65"), push.exercises[0].sets.map { it.weight })
        assertEquals(listOf("WARMUP", "NORMAL", "DROP"), push.exercises[0].sets.map { it.intensityType })
        assertTrue(push.exercises[1].isSkipped)
        assertTrue(push.exercises[2].isSuperset)
        assertEquals(listOf("Cable Fly"), push.skippedExercises)
        assertEquals(4200, push.totalVolumeKg)
        // The lowercase "bench press" from a later workout is now the same exercise.
        assertEquals("Bench Press", history.first().exercises.single().name)
        assertEquals("/data/photo.jpg", history.first().photoPath)

        assertEquals(listOf(81.2f, 80.4f), LocalStorage.loadWeightHistory(context).map { it.weightKg })
        assertEquals(listOf("Bench Press" to "Chest", "My Weird Press" to "Custom"), LocalStorage.loadTemplateForSplit(context, "Push"))
        assertEquals("Seat on 4, grip ring finger", LocalStorage.getExerciseNote(context, "Bench Press"))
        assertEquals(150, LocalStorage.getExerciseDefaultRest(context, "BENCH PRESS", 90))
    }

    @Test
    fun keepsJsonOnlyStateAndBacksUpTheOriginalFile() {
        LocalStorage.loadHistory(context)
        restart()

        val onDisk = JSONObject(storeFile.readText())
        assertFalse(onDisk.has("history"))
        assertFalse(onDisk.has("weightHistory"))
        assertFalse(onDisk.has("exerciseNotes"))
        assertEquals(JSONObject(v3Json).toString(), JSONObject(File(context.filesDir, "gym_tracker_store_v2.json.pre-room").readText()).toString())

        assertEquals("CLASSIC_PPL", LocalStorage.loadUserProfile(context).splitProgram)
        assertEquals(120, LocalStorage.loadSettings(context).restTimerSeconds)
        assertEquals(80, LocalStorage.loadWidgetSettings(context, 42, "workout").opacityPercent)
        assertEquals("Pull", LocalStorage.loadActiveSession(context)?.first)
        assertEquals(2, LocalStorage.loadHistory(context).size) // now read from Room
    }

    @Test
    fun interruptedMigration_rerunsWithoutLosingNewerWorkouts() {
        LocalStorage.loadHistory(context)
        LocalStorage.saveCompletedWorkout(context, LocalStorage.loadHistory(context).first().copy(id = 1761000000000))
        restart()
        // Simulate a crash after Room committed but before the JSON sections were removed.
        storeFile.writeText(v3Json)

        assertEquals(listOf(1761000000000, 1759500000000, 1759000000000), LocalStorage.loadHistory(context).map { it.id })
    }

    @Test
    fun writesSurviveARestart() {
        LocalStorage.saveExerciseNote(context, "My Weird Press", "slow eccentric")
        LocalStorage.addWeightLog(context, 82f)
        LocalStorage.deleteWorkoutFromHistory(context, 1759000000000)
        LocalStorage.saveTemplateForSplit(context, "Legs", listOf("Barbell Back Squat" to "Quads"))
        restart()

        assertEquals("slow eccentric", LocalStorage.getExerciseNote(context, "my weird press"))
        assertEquals(82f, LocalStorage.loadWeightHistory(context).first().weightKg)
        assertEquals(listOf(1759500000000), LocalStorage.loadHistory(context).map { it.id })
        assertEquals(listOf("Barbell Back Squat" to "Quads"), LocalStorage.loadTemplateForSplit(context, "Legs"))
    }

    @Test
    fun saveCompletedWorkout_endsTheActiveSession() {
        val done = LocalStorage.loadHistory(context).first().copy(id = 1761000000000)
        LocalStorage.saveCompletedWorkout(context, done)
        restart()
        assertEquals(null, LocalStorage.loadActiveSession(context))
        assertEquals(1761000000000, LocalStorage.loadHistory(context).first().id)
    }

    @Test
    fun backupExport_matchesTheOldFormat_andImportsBack() {
        val exported = LocalStorage.exportJsonBackupString(context)
        val original = JSONObject(v3Json)
        val json = JSONObject(exported)
        for (key in listOf("userProfile", "settings", "widgetSettings", "activeSession")) {
            assertEquals(key, original.getJSONObject(key).toString(), json.getJSONObject(key).toString())
        }
        assertEquals(2, json.getJSONArray("history").length())
        assertEquals(2, json.getJSONArray("weightHistory").length())
        assertNotNull(json.getJSONObject("splitTemplates").getJSONArray("Push"))

        val before = LocalStorage.loadHistory(context)
        LocalStorage.deleteWorkoutFromHistory(context, 1759000000000)
        assertTrue(LocalStorage.importJsonBackupString(context, exported))
        assertEquals(before, LocalStorage.loadHistory(context))
        restart()
        assertEquals(before, LocalStorage.loadHistory(context))
        assertEquals("Seat on 4, grip ring finger", LocalStorage.getExerciseNote(context, "bench press"))
    }

    @Test
    fun importingAnOldBackup_replacesCurrentData() {
        LocalStorage.loadHistory(context)
        val old = JSONObject(v3Json).apply { getJSONArray("history").remove(1) }
        assertTrue(LocalStorage.importJsonBackupString(context, old.toString()))
        assertEquals(listOf(1759000000000), LocalStorage.loadHistory(context).map { it.id })
        assertFalse(LocalStorage.importJsonBackupString(context, "not json"))
    }

    @Test
    fun renameExercise_updatesHistoryAndActiveSession() {
        // Never logged or saved anywhere → nothing to rename.
        assertFalse(LocalStorage.renameExercise(context, "Barbell Row", "Pendlay Row"))

        LocalStorage.saveActiveSession(context, "Push", listOf(ExerciseEntry("my weird press", "Custom")))
        assertTrue(LocalStorage.renameExercise(context, "My Weird Press", "Landmine Press"))
        assertEquals("Landmine Press", LocalStorage.loadActiveSession(context)!!.second.single().name)
        restart()
        assertEquals("Landmine Press", LocalStorage.loadHistory(context).last().exercises[2].name)
        assertEquals("Landmine Press", LocalStorage.loadTemplateForSplit(context, "Push")[1].first)
    }

    @Test
    fun weightUnit_persists_andIsAppliedOnAnyFirstStorageAccess() {
        assertEquals(WeightUnit.KG, LocalStorage.loadSettings(context).weightUnit) // v3 file has no unit
        LocalStorage.saveSettings(context, LocalStorage.loadSettings(context).copy(weightUnit = WeightUnit.LB, weightStep = 5f))
        restart()
        Units.set(WeightUnit.KG)
        // A widget's first storage call (not loadSettings) must already switch the display unit.
        LocalStorage.loadActiveSession(context)
        assertEquals(WeightUnit.LB, Units.current)
        assertEquals(5f, LocalStorage.loadSettings(context).weightStep)
        Units.set(WeightUnit.KG)
    }

    @Test
    fun renamingADayOfACustomizedProgram_keepsHistory_butLeavesTheBuiltInItsExercises() {
        val copy = SPLIT_PROGRAMS.getValue("CLASSIC_PPL").copy(id = "custom-1", name = "My PPL")
        LocalStorage.saveProgram(context, copy)
        LocalStorage.saveTemplateForSplit(context, "Pull", listOf("Barbell Row" to "Back"))
        LocalStorage.renameSplit(context, "custom-1", "Pull", "Back Day")
        restart()

        val history = LocalStorage.loadHistory(context)
        assertEquals("Back Day", history.first { it.id == 1759500000000 }.splitTitle) // history follows
        assertEquals(listOf("Barbell Row" to "Back"), LocalStorage.loadTemplateForSplit(context, "Back Day"))
        assertEquals(listOf("Barbell Row" to "Back"), LocalStorage.loadTemplateForSplit(context, "Pull")) // built-ins keep theirs
        assertEquals(listOf("Push", "Back Day", "Legs"), programFor("custom-1").splits.map { it.title })
        assertEquals("Back Day", LocalStorage.loadActiveSession(context)?.first) // the open workout follows too
    }

    @Test
    fun freshInstall_startsEmpty() {
        storeFile.delete()
        assertTrue(LocalStorage.loadHistory(context).isEmpty())
        assertFalse(File(context.filesDir, "gym_tracker_store_v2.json.pre-room").exists())
    }
}
