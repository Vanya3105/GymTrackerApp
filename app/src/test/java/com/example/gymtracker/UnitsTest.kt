package com.example.gymtracker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {

    @After
    fun resetUnit() = Units.set(WeightUnit.KG)

    private fun set(w: String, r: String) = SetEntry(setNumber = 1, weight = w, reps = r, isCompleted = true)

    private fun workout(id: Long, vararg exercises: ExerciseEntry) = CompletedWorkout(
        id = id, splitTitle = "Push", dateKey = "", displayDate = "Day $id", durationSeconds = 0,
        bodyWeightKg = 80f, photoPath = null, skippedExercises = emptyList(), exercises = exercises.toList()
    )

    // --- Kilograms: same strings as before units existed ---

    @Test
    fun kg_isAnIdentity() {
        assertEquals("62.5", WeightUnit.KG.stored(62.5f))
        assertEquals("62.5", WeightUnit.KG.number(62.5f))
        assertEquals("62.5 kg", formatWeightKg(62.5f))
        assertEquals("62.5", stepWeight("60", 2.5f))
        assertEquals("0", stepWeight("1", -2.5f)) // never below zero
        assertEquals("54", scaleWeight("60", 0.9f))
        assertEquals("2.5", scaleWeight("2", 0.5f)) // floor of one step
        assertEquals("1,250 kg", formatVolume(1250))
        assertEquals("12.5k kg", formatVolume(12_500))
    }

    // --- Pounds ---

    @Test
    fun lb_valuesSurviveTheKgRoundTripExactly() {
        val lb = WeightUnit.LB
        var v = 2.5f
        while (v <= 1000f) {
            val back = lb.number(parseWeightClean(lb.stored(v), 0f))
            assertEquals("$v lb", formatKgClean(v), back)
            v += 2.5f
        }
        assertEquals("61.235", lb.stored(135f))
    }

    @Test
    fun lb_displayAndSteps() {
        Units.set(WeightUnit.LB)
        assertEquals("135 lb", formatWeightKg(61.235f))
        assertEquals("100 lb", formatWeightKg(45.359237f))
        assertEquals("140", WeightUnit.LB.number(parseWeightClean(stepWeight(WeightUnit.LB.stored(135f), 5f), 0f)))
        assertEquals("121.5", WeightUnit.LB.number(parseWeightClean(scaleWeight(WeightUnit.LB.stored(135f), 0.9f), 0f)))
        assertEquals("2,205 lb", formatVolume(1000))
        assertEquals("100 lb × 5", formatSet(set(WeightUnit.LB.stored(100f), "5")))
    }

    @Test
    fun steps_snapOffGridValuesToTheNextMultiple() {
        Units.set(WeightUnit.LB)
        fun lb(stored: String) = WeightUnit.LB.number(parseWeightClean(stored, 0f))
        val converted = "40" // 40 kg = 88.2 lb, left over from before switching units
        assertEquals("90", lb(stepWeight(converted, 5f)))
        assertEquals("85", lb(stepWeight(converted, -5f)))
        assertEquals("95", lb(stepWeight(stepWeight(converted, 5f), 5f)))
        Units.set(WeightUnit.KG)
        assertEquals("62.5", stepWeight("61", 2.5f))
        assertEquals("60", stepWeight("61", -2.5f))
        assertEquals("20", stepWeight("20", -2.5f, minKg = 20f))
    }

    @Test
    fun lb_bodyWeightAt001KgReadsBackExactly() {
        // LocalStorage rounds weigh-ins to 0.01 kg; every 0.1 lb value must survive that.
        val lb = WeightUnit.LB
        var tenths = 900 // 90.0 lb
        while (tenths <= 4000) {
            val value = tenths / 10f
            val storedKg = Math.round(lb.toKg(value) * 100f) / 100f
            assertEquals("$value lb", formatKgClean(value), lb.number(storedKg))
            tenths += 1
        }
    }

    @Test
    fun lb_splitHistorySuggestion_landsOnWholeSteps() {
        Units.set(WeightUnit.LB)
        val w135 = WeightUnit.LB.stored(135f)
        val history = listOf(
            workout(2, ExerciseEntry("Barbell Bench Press", sets = listOf(set(w135, "8")))),
            workout(1, ExerciseEntry("Barbell Bench Press", sets = listOf(set(w135, "9"))))
        )
        val advice = suggestFromSplitHistory("Push", ExerciseEntry("Barbell Bench Press"), history, 5f)!!
        assertEquals("140", WeightUnit.LB.number(parseWeightClean(advice.suggestedWeight, 0f)))
        assertTrue(advice.message, advice.message.contains("140 lb"))
    }

    @Test
    fun lb_todaySuggestion_usesPoundsInMessages() {
        Units.set(WeightUnit.LB)
        val w = WeightUnit.LB.stored(100f)
        val ex = ExerciseEntry("Cable Curl", sets = listOf(set(w, "12"), set(w, "12")))
        val advice = suggestNextWeight("Arms", ex, emptyList(), 5f)
        assertTrue(advice.message, advice.message.contains("105 lb"))
        assertEquals("105", WeightUnit.LB.number(parseWeightClean(advice.suggestedWeight, 0f)))
    }

    @Test
    fun unknownUnitKey_fallsBackToKg() {
        assertEquals(WeightUnit.KG, WeightUnit.fromKey(null))
        assertEquals(WeightUnit.KG, WeightUnit.fromKey(""))
        assertEquals(WeightUnit.LB, WeightUnit.fromKey("LB"))
    }
}
