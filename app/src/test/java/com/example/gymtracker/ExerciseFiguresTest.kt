package com.example.gymtracker

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every library exercise has a drawn figure, and contact sheets of them (start, middle, end) go
 * to app/build/figures/ to check the poses without a phone: one sheet per muscle group in dark,
 * and one sample in light.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExerciseFiguresTest {
    @Test
    fun everyFigureIsInTheLibrary() {
        val stray = figureNames.filter { ExerciseDatabase.find(it) == null }
        assertTrue("Figures for names not in the library: $stray", stray.isEmpty())
    }

    @Test
    fun everyLibraryExerciseHasAFigure() {
        val missing = ExerciseDatabase.all.map { it.name }.filter { !ExerciseFigures.has(it) }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun contactSheets() {
        ExerciseDatabase.all.groupBy { it.muscle }.forEach { (muscle, exercises) ->
            sheet("figures-${muscle.lowercase()}", exercises.map { it.name }.filter { ExerciseFigures.has(it) }, DarkPalette)
        }
        sheet("figures-light", listOf("Back Squat", "Lateral Raise", "Cable Fly", "Cycling"), LightPalette)
    }

    private fun sheet(file: String, names: List<String>, palette: GymPalette) {
        if (names.isEmpty()) return
        val cell = Size(240f, 160f)
        val moments = listOf(0f, 0.5f, 1f)
        val perRow = 2
        val rows = (names.size + perRow - 1) / perRow
        val bitmap = Bitmap.createBitmap((cell.width * moments.size * perRow).toInt(), (cell.height * rows).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(android.graphics.Canvas(bitmap))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            drawRect(palette.bgPrimary)
            names.forEachIndexed { i, name ->
                moments.forEachIndexed { m, t ->
                    val left = ((i % perRow) * moments.size + m) * cell.width
                    val top = (i / perRow) * cell.height
                    drawRect(palette.bgControl, Offset(left + 2f, top + 2f), Size(cell.width - 4f, cell.height - 4f))
                    translate(left + 10f, top + 10f) {
                        drawExerciseFigure(name, t, palette, Size(cell.width - 20f, cell.height - 20f))
                    }
                }
            }
        }
        File("build/figures/$file.png").apply { parentFile?.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
