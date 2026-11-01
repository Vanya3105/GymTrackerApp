package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every name the app saves in English and shows through [shown] (exercises, muscles, equipment,
 * the built-in programs and their days, the split builder's layouts) has Hebrew.
 */
class HebrewNamesTest {
    private fun names(): Set<String> = buildSet {
        ExerciseDatabase.all.forEach { add(it.name); add(it.muscle); add(it.equipment) }
        addAll(RECOVERY_GROUPS)
        add("Hams")
        SPLIT_PROGRAMS.values.forEach { p ->
            add(p.name); add(p.tagline)
            p.splits.forEach { add(it.title); add(it.subtitle) }
        }
        QuickLayout.entries.forEach { l -> add(l.label); l.days.forEach { add(it.title) } }
        add(QUICK_WORKOUT_TITLE)
        // What the split builder can name a day besides joined muscles ("Chest & Back").
        addAll(listOf("Push", "Pull", "Arms", "Arms & Shoulders", "Legs", "Lower", "Upper", "Full Body"))
    }

    @Test
    fun everyNameHasHebrew() {
        val missing = names().filter { it.isNotBlank() && HebrewText.ofName(it) == null }.sorted()
        assertEquals("", missing.joinToString("\n"))
    }

    @Test
    fun everyGuideHasHebrewWithTheSameSteps() {
        val wrong = ExerciseDatabase.all.mapNotNull { info ->
            val en = ExerciseGuides.find(info.name) ?: return@mapNotNull null
            val he = HebrewGuides.find(info.name)
            if (he == null || he.steps.size != en.steps.size || he.tip.isBlank()) info.name else null
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun madeUpDayNamesAreBuiltFromTheirParts() {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("he"))
        try {
            assertEquals("חזה וגב", shown("Chest & Back"))
            assertEquals("חזה, גב ויד קדמית", shown("Chest, Back & Biceps"))
            assertEquals("יום 2", shown("Day 2"))
            assertEquals("${shown("Push")} B", shown("Push B"))
            assertEquals("My own day", shown("My own day"))
        } finally {
            java.util.Locale.setDefault(java.util.Locale.US)
        }
    }
}
