package com.example.gymtracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** The share data, plus every card on every background drawn to app/build/screenshots/share-*.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = Units.set(WeightUnit.KG)

    private fun at(text: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(text)!!.time
    private fun set(kg: String, reps: String, warm: Boolean = false) =
        SetEntry(0, kg, reps, isWarmup = warm, intensityType = if (warm) "WARMUP" else "NORMAL", isCompleted = true)
    private fun ex(name: String, muscle: String, vararg sets: SetEntry) = ExerciseEntry(name, muscle, sets = sets.toList())
    private fun workout(time: String, split: String, vararg exercises: ExerciseEntry): CompletedWorkout {
        val stats = computeWorkoutStats(exercises.toList(), emptyList())
        return CompletedWorkout(
            id = at(time), splitTitle = split, dateKey = "", displayDate = "", durationSeconds = 3600, bodyWeightKg = 80f,
            photoPath = null, skippedExercises = emptyList(), exercises = exercises.toList(), totalVolumeKg = stats.volumeKg
        )
    }

    /** Three weeks of Push and Pull, newest first, as the app keeps history. */
    private val history = listOf(
        workout("2026-10-27 18:00", "Pull", ex("Barbell Row", "Back", set("70", "8"), set("70", "8"))),
        workout("2026-10-22 18:00", "Push", ex("Barbell Bench Press", "Chest", set("80", "8"), set("80", "8"), set("80", "7")), ex("Overhead Press", "Shoulders", set("45", "8"))),
        workout("2026-10-15 18:00", "Push", ex("Barbell Bench Press", "Chest", set("77.5", "8"))),
        workout("2026-10-08 18:00", "Pull", ex("Barbell Row", "Back", set("65", "8")))
    )

    private val today = listOf(
        ex("Barbell Bench Press", "Chest", set("40", "8", warm = true), set("82.5", "8"), set("82.5", "8"), set("82.5", "7")),
        ex("Overhead Press", "Shoulders", set("45", "10"), set("45", "9")),
        ex("Incline Dumbbell Press", "Chest", set("30", "10"), set("30", "10")),
        ex("Lateral Raise", "Shoulders", set("10", "15"), set("10", "14"), set("10", "12")),
        ex("Triceps Pushdown", "Triceps", set("25", "12"), set("25", "12")),
        ex("Cable Fly", "Chest", set("15", "12"), set("15", "12")),
        ex("Dips", "Chest", set("0", "12"))
    )

    private fun data() = buildShareData("Push", 4320, today, history, at("2026-10-29 18:30"), null)

    @Test
    fun data_comparesWithYou() {
        val d = data()
        assertEquals(5, d.workoutNumber)
        assertEquals(5, d.workoutsThisMonth)
        // Weeks of Oct 25, Oct 18 (Push on the 22nd), Oct 11 (the 15th), Oct 4 (the 8th).
        assertEquals(4, d.weekStreak)
        assertEquals(listOf("Barbell Bench Press", "Overhead Press"), d.bests.map { it.name }.take(2))
        assertEquals("80 kg × 8", d.bests.first().before)
        assertTrue(d.volumeChangePct!! > 100)
        assertEquals(d.muscles.sortedByDescending { it.second }, d.muscles)
        assertTrue(d.muscles.take(2).map { it.first }.containsAll(listOf("Chest", "Shoulders")))
        assertEquals(listOf(ShareCard.SUMMARY, ShareCard.BESTS, ShareCard.MUSCLES, ShareCard.STREAK), d.cards)
    }

    @Test
    fun firstWorkout_hasNoBestsCard_andNoStreak() {
        val d = buildShareData("Legs", 3000, listOf(ex("Back Squat", "Quads", set("60", "8"))), emptyList(), at("2026-10-29 18:30"), null)
        assertEquals(1, d.workoutNumber)
        assertEquals(1, d.weekStreak)
        assertTrue(d.bests.isEmpty())
        assertNull(d.volumeChangePct)
        assertEquals(listOf(ShareCard.SUMMARY, ShareCard.MUSCLES, ShareCard.STREAK), d.cards)
    }

    @Test
    fun volumeComparison_picksSomethingYouCanPicture() {
        assertNull(volumeComparison(300))
        assertEquals("about a grand piano", volumeComparison(500))
        assertEquals("about 3 small cars", volumeComparison(4_000))
        assertEquals("about an elephant", volumeComparison(7_000))
        assertEquals("about 2 city buses", volumeComparison(25_000))
    }

    @Test
    fun drawsEveryCard() {
        Units.set(WeightUnit.LB)
        val d = data()
        val out = File("build/screenshots").apply { mkdirs() }
        for (card in d.cards) for (bg in listOf(ShareBackground.DARK, ShareBackground.LIGHT, ShareBackground.CLEAR)) {
            val bmp = renderShareCard(context, d, card, bg)
            assertEquals(1080, bmp.width)
            assertEquals(1920, bmp.height)
            // Clear is shown over a stand-in photo, the way it will be used.
            val shown = if (bg == ShareBackground.CLEAR) overStandInPhoto(bmp) else bmp
            File(out, "share-${card.name.lowercase()}-${bg.name.lowercase()}.png").outputStream().use { shown.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun overStandInPhoto(sticker: Bitmap): Bitmap {
        val photo = Bitmap.createBitmap(sticker.width, sticker.height, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawRect(0f, 0f, 1080f, 1920f, Paint().apply {
                shader = LinearGradient(0f, 0f, 1080f, 1920f, intArrayOf(Color.rgb(120, 140, 150), Color.rgb(200, 190, 170), Color.rgb(70, 80, 90)), null, Shader.TileMode.CLAMP)
            })
            drawBitmap(sticker, 0f, 0f, null)
        }
        return photo
    }
}
