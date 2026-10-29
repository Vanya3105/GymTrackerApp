package com.example.gymtracker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetKeypadTest {
    @After
    fun resetUnit() {
        Units.set(WeightUnit.KG)
    }

    private fun type(start: String, keys: String, field: SetField, fresh: Boolean = true): String? {
        var text: String? = start
        var isFresh = fresh
        for (k in keys) {
            text = typeKey(text ?: return null, k, field, isFresh) ?: return null
            isFresh = false
        }
        return text
    }

    @Test
    fun firstDigitReplaces_thenDigitsAdd() {
        assertEquals("100", type("95", "100", SetField.WEIGHT))
        assertEquals("9510", type("95", "10", SetField.WEIGHT, fresh = false))
        // Backspace edits what's there instead of clearing it.
        assertEquals("9", typeKey("95", KEY_BACKSPACE, SetField.WEIGHT, fresh = true))
    }

    @Test
    fun decimals_onlyForWeightAndDistance_oneDotTwoPlaces() {
        assertEquals("62.5", type("", "62.5", SetField.WEIGHT))
        assertEquals("0.5", type("", ".5", SetField.WEIGHT))
        assertNull(type("", "62.5.", SetField.WEIGHT))
        assertNull(type("", "62.555", SetField.WEIGHT))
        assertNull(typeKey("8", '.', SetField.REPS, fresh = false))
        assertEquals("5", type("", "005", SetField.WEIGHT))
        assertEquals("0.25", type("", "0.25", SetField.DISTANCE))
    }

    @Test
    fun reps_atMostThreeDigits() {
        assertEquals("12", type("8", "12", SetField.REPS))
        assertNull(type("", "1000", SetField.REPS))
        assertEquals("0", type("8", "0", SetField.REPS))
    }

    @Test
    fun time_typesLikeAMicrowave() {
        val set = SetEntry(1, "", "", seconds = "90")
        assertEquals("130", editableText(set, SetField.TIME, ExerciseType.DURATION))
        val typed = type("130", "45", SetField.TIME)!!
        assertEquals("0:45", shownText(typed, SetField.TIME))
        assertEquals("45", withFieldText(set, SetField.TIME, typed).seconds)
        assertEquals("200", withFieldText(set, SetField.TIME, "320").seconds)
        assertEquals("", withFieldText(set, SetField.TIME, "").seconds)
    }

    @Test
    fun poundsAreTypedInPounds_andStoredInKg() {
        Units.set(WeightUnit.LB)
        val set = SetEntry(1, "61.235", "8")
        assertEquals("135", fieldText(set, SetField.WEIGHT, ExerciseType.WEIGHT_REPS))
        val stored = withFieldText(set, SetField.WEIGHT, "225")
        assertEquals("225", fieldText(stored, SetField.WEIGHT, ExerciseType.WEIGHT_REPS))
    }

    @Test
    fun steps_followTheUsersStepAndTheType() {
        val set = SetEntry(1, "80", "8", seconds = "60")
        assertEquals("82.5", steppedSet(set, SetField.WEIGHT, ExerciseType.WEIGHT_REPS, up = true, weightStep = 2.5f).weight)
        assertEquals("7", steppedSet(set, SetField.REPS, ExerciseType.WEIGHT_REPS, up = false, weightStep = 2.5f).reps)
        assertEquals("75", steppedSet(set, SetField.TIME, ExerciseType.DURATION, up = true, weightStep = 2.5f).seconds)
        assertEquals("120", steppedSet(set, SetField.TIME, ExerciseType.DISTANCE_TIME, up = true, weightStep = 2.5f).seconds)
        assertEquals("0", steppedSet(SetEntry(1, "", "0"), SetField.REPS, ExerciseType.BODYWEIGHT, up = false, weightStep = 2.5f).reps)
    }

    @Test
    fun next_goesAcrossTheSetThenDownToTheNextSet() {
        val ex = ExerciseEntry("Bench", sets = listOf(SetEntry(1, "80", "8"), SetEntry(2, "80", "8")))
        val first = FieldRef(0, 0, SetField.WEIGHT)
        assertEquals(FieldRef(0, 0, SetField.REPS), nextField(first, ex))
        assertEquals(FieldRef(0, 1, SetField.WEIGHT), nextField(FieldRef(0, 0, SetField.REPS), ex))
        assertNull(nextField(FieldRef(0, 1, SetField.REPS), ex))

        val run = ExerciseEntry("Run", type = ExerciseType.DISTANCE_TIME, sets = listOf(SetEntry(1, "", "")))
        assertEquals(listOf(SetField.DISTANCE, SetField.TIME), fieldsFor(run.type))
        assertEquals(FieldRef(3, 0, SetField.TIME), nextField(FieldRef(3, 0, SetField.DISTANCE), run))
    }
}
