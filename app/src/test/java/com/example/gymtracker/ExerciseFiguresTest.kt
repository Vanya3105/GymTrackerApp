package com.example.gymtracker

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every drawn figure is a library exercise, and a contact sheet of all of them (start, middle,
 * end; dark and light) goes to app/build/figures/figures.png to check the poses without a phone.
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
    fun contactSheet() {
        val names = figureNames.toList()
        val cell = Size(330f, 220f)
        val moments = listOf(0f, 0.5f, 1f)
        val palettes = listOf(DarkPalette, LightPalette)
        val bitmap = Bitmap.createBitmap((cell.width * moments.size * palettes.size).toInt(), (cell.height * names.size).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(android.graphics.Canvas(bitmap))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            names.forEachIndexed { row, name ->
                palettes.forEachIndexed { p, palette ->
                    moments.forEachIndexed { m, t ->
                        val left = (p * moments.size + m) * cell.width
                        val top = row * cell.height
                        drawRect(palette.bgControl, Offset(left, top), cell)
                        translate(left + 12f, top + 12f) {
                            drawExerciseFigure(name, t, palette, Size(cell.width - 24f, cell.height - 24f))
                        }
                    }
                }
            }
        }
        File("build/figures/figures.png").apply { parentFile?.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.getPixel(5, 5) == DarkPalette.bgControl.toArgb())
    }
}
