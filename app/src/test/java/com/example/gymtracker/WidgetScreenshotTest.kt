package com.example.gymtracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Date

/**
 * Draws the three home-screen widgets with sample data, in light and dark and at a few sizes, to
 * PNGs in app/build/widgets, to check their design without a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetScreenshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day = 24 * 60 * 60 * 1000L
    private val now = System.currentTimeMillis()

    private fun sets(weight: String, n: Int) = (1..n).map { SetEntry(it, weight, "8", isCompleted = true) }

    private fun workout(daysAgo: Int, split: String, vararg exercises: ExerciseEntry): CompletedWorkout {
        val id = now - daysAgo * day
        return CompletedWorkout(
            id = id, splitTitle = split, dateKey = DateUtils.formatDayKey(Date(id)), displayDate = DateUtils.formatDisplayDate(Date(id)),
            durationSeconds = 3600, bodyWeightKg = 80f, photoPath = null, skippedExercises = emptyList(), exercises = exercises.toList()
        )
    }

    @Before
    fun sampleData() {
        LocalStorage.saveUserProfile(context, UserProfile(hasCompletedOnboarding = true, age = 30, currentWeightKg = 80f, splitProgram = "CLASSIC_PPL"))
        listOf(
            workout(0, "Push", ExerciseEntry("Barbell Bench Press", "Chest", sets = sets("80", 4)), ExerciseEntry("Lateral Raise", "Shoulders", sets = sets("10", 3))),
            workout(1, "Pull", ExerciseEntry("Pull-Up", "Back", sets = sets("0", 4)), ExerciseEntry("Dumbbell Curl", "Biceps", sets = sets("14", 3)))
        ).forEach { LocalStorage.saveCompletedWorkout(context, it) }
    }

    private fun save(name: String, bitmap: Bitmap) {
        File("build/widgets/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Puts [widget] on a wallpaper-like backdrop so transparency and corners show. */
    private fun onBackdrop(widget: Bitmap, dark: Boolean): Bitmap {
        val pad = 24
        val out = Bitmap.createBitmap(widget.width + pad * 2, widget.height + pad * 2, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(if (dark) Color.rgb(28, 36, 48) else Color.rgb(196, 214, 230))
            drawBitmap(widget, pad.toFloat(), pad.toFloat(), null)
        }
        return out
    }

    private fun workoutWidget(settings: WidgetSettings, wDp: Int, hDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val parent = FrameLayout(context)
        val view = buildWorkoutWidgetViews(context, settings, compact = wDp < 210).apply(context, parent)
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun all(name: String) {
        listOf(THEME_LIGHT to false, THEME_DARK to true).forEach { (theme, dark) ->
            val suffix = if (dark) "dark" else "light"
            val workout = WidgetSettings(theme = theme, options = widgetOptionLabels(WIDGET_KIND_WORKOUT).map { it.first }.toSet())
            val recovery = WidgetSettings(theme = theme, options = widgetOptionLabels(WIDGET_KIND_RECOVERY).map { it.first }.toSet())
            val weekly = WidgetSettings(theme = theme, options = widgetOptionLabels(WIDGET_KIND_WEEKLY).map { it.first }.toSet())
            save("$name-workout-4x2-$suffix", onBackdrop(workoutWidget(workout, 300, 140), dark))
            save("$name-workout-2x2-$suffix", onBackdrop(workoutWidget(workout, 160, 160), dark))
            save("$name-recovery-4x2-$suffix", onBackdrop(MuscleFatigueWidgetProvider.render(context, recovery, 300, 140), dark))
            save("$name-weekly-2x2-$suffix", onBackdrop(SquarePulseWidgetProvider.render(context, weekly, 160, 160), dark))
            save("$name-recovery-2x2-$suffix", onBackdrop(MuscleFatigueWidgetProvider.render(context, recovery, 160, 160), dark))
            save("$name-weekly-4x2-$suffix", onBackdrop(SquarePulseWidgetProvider.render(context, weekly, 300, 140), dark))
        }
    }

    @Test
    fun idle() = all("idle")

    /** The static previews in the phone's widget picker, which are widget layouts too. */
    @Test
    fun pickerPreviews() {
        val density = context.resources.displayMetrics.density
        listOf(
            "workout" to Triple(R.layout.widget_preview_workout, 300, 140),
            "recovery" to Triple(R.layout.widget_preview_recovery, 300, 140),
            "weekly" to Triple(R.layout.widget_preview_weekly, 160, 160)
        ).forEach { (name, spec) ->
            val (layout, wDp, hDp) = spec
            val w = (wDp * density).toInt()
            val h = (hDp * density).toInt()
            val view = android.widget.RemoteViews(context.packageName, layout).apply(context, FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            save("preview-$name", onBackdrop(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }, dark = true))
        }
    }

    @Test
    fun hebrew() {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("he"))
        try {
            all("hebrew")
        } finally {
            java.util.Locale.setDefault(java.util.Locale.US)
        }
    }

    @Test
    fun duringAWorkout() {
        LocalStorage.saveActiveSession(
            context, "Push",
            listOf(
                ExerciseEntry("Barbell Bench Press", "Chest", sets = listOf(SetEntry(1, "80", "8", isCompleted = true), SetEntry(2, "80", "8"), SetEntry(3, "80", "8"))),
                ExerciseEntry("Lateral Raise", "Shoulders", sets = listOf(SetEntry(1, "10", "12"), SetEntry(2, "10", "12")))
            )
        )
        all("live")
    }
}
