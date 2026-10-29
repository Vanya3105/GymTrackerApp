package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitLibraryTest {

    private val builtIns = SPLIT_PROGRAMS.values.toList()
    private fun ids(query: String, days: Int? = null) = searchSplits(query, builtIns, days).map { it.id }

    @Test
    fun search_emptyQueryListsEverySplitInOrder() {
        assertEquals(builtIns.map { it.id }, ids(""))
    }

    @Test
    fun search_findsByNameKeywordDayAndCount() {
        assertEquals(setOf("PPL_ARNOLD", "CLASSIC_PPL", "PPL_6"), ids("push").take(3).toSet())
        assertTrue("PPL_6" in ids("ppl"))
        assertTrue("BRO_SPLIT" in ids("bro"))
        assertEquals(listOf("WENDLER_531", "NSUNS_5"), ids("531").take(2))
        assertEquals("WENDLER_531", ids("5/3/1").first())
        assertTrue("PHUL" in ids("hypertrophy"))
        // A day name finds the programs that have it.
        assertTrue(ids("shoulders").containsAll(listOf("BRO_SPLIT", "PPL_ARNOLD")))
        assertTrue(ids("6 days").all { id -> SPLIT_PROGRAMS.getValue(id).weeklyGoal == 6 })
        assertTrue(ids("zzz").isEmpty())
    }

    @Test
    fun search_everyWordMustMatch() {
        assertEquals(listOf("UPPER_LOWER", "PHUL"), ids("upper lower").sortedBy { it != "UPPER_LOWER" })
    }

    @Test
    fun search_filtersByDays() {
        assertEquals(listOf("FULL_BODY_2"), ids("", days = 2))
        assertTrue(ids("ppl", days = 3) == listOf("CLASSIC_PPL"))
    }

    @Test
    fun dayTitle_matchesBuiltInDayNames() {
        assertEquals("Push", dayTitleFor(setOf("Chest", "Shoulders", "Triceps"), 0))
        assertEquals("Pull", dayTitleFor(setOf("Back", "Biceps"), 0))
        assertEquals("Legs", dayTitleFor(setOf("Quads", "Hamstrings", "Calves"), 0))
        assertEquals("Arms", dayTitleFor(setOf("Biceps", "Triceps"), 0))
        assertEquals("Arms & Shoulders", dayTitleFor(setOf("Shoulders", "Biceps", "Triceps"), 0))
        assertEquals("Chest & Back", dayTitleFor(setOf("Back", "Chest"), 0))
        assertEquals("Chest", dayTitleFor(setOf("Chest"), 0))
        assertEquals("Upper", dayTitleFor(setOf("Chest", "Back", "Shoulders", "Biceps"), 0))
        assertEquals("Full Body", dayTitleFor(setOf("Chest", "Back", "Quads", "Hamstrings"), 0))
        assertEquals("Chest, Triceps & Core", dayTitleFor(setOf("Core", "Triceps", "Chest"), 0))
        assertEquals("Day 3", dayTitleFor(emptySet(), 2))
    }

    @Test
    fun uniqueTitles_lettersRepeatedDaysAndNumbersClashes() {
        assertEquals(listOf("Push A", "Pull A", "Legs A", "Push B", "Pull B", "Legs B"),
            uniqueDayTitles(listOf("Push", "Pull", "Legs", "Push", "Pull", "Legs")))
        assertEquals(listOf("Push", "Pull", "Legs"), uniqueDayTitles(listOf("Push", "Pull", "Legs")))
        assertEquals(listOf("Push A", "Push B", "Push A 2"), uniqueDayTitles(listOf("Push", "Push", "Push A")))
    }

    @Test
    fun layouts_produceTheBuiltInDayNames() {
        fun titles(l: QuickLayout, n: Int) = uniqueDayTitles(l.fill(n).map { it.title })
        assertEquals(SPLIT_PROGRAMS.getValue("UPPER_LOWER").splits.map { it.title }, titles(QuickLayout.UPPER_LOWER, 4))
        assertEquals(SPLIT_PROGRAMS.getValue("CLASSIC_PPL").splits.map { it.title }, titles(QuickLayout.PPL, 3))
        assertEquals(SPLIT_PROGRAMS.getValue("PPL_6").splits.map { it.title }, titles(QuickLayout.PPL, 6))
        assertEquals(SPLIT_PROGRAMS.getValue("FULL_BODY").splits.map { it.title }, titles(QuickLayout.FULL_BODY, 3))
        assertEquals(SPLIT_PROGRAMS.getValue("BRO_SPLIT").splits.map { it.title }, titles(QuickLayout.BRO, 5))
        // Each layout day's muscles name it the same way, so editing then undoing a muscle keeps the name.
        QuickLayout.entries.flatMap { it.days }.filter { it.title != "Lower" }.forEach {
            assertEquals(it.title, dayTitleFor(it.muscles, 0))
        }
    }

    @Test
    fun starterExercises_comeFromTheLibraryAndCoverEveryMuscle() {
        STARTER_EXERCISES.forEach { (muscle, names) ->
            names.forEach { name ->
                val info = ExerciseDatabase.find(name)
                assertNotNull("$name missing from library", info)
                assertEquals(name, muscle, info!!.muscle)
            }
        }
        assertEquals(BUILDER_MUSCLES.toSet(), STARTER_EXERCISES.keys)

        val push = starterExercises(setOf("Chest", "Shoulders", "Triceps"))
        assertEquals(6, push.size)
        assertEquals("Barbell Bench Press", push.first().first)
        assertEquals(setOf("Chest", "Shoulders", "Triceps"), push.map { it.second }.toSet())
        assertEquals(5, starterExercises(setOf("Chest")).size)
        // Every muscle gets at least one exercise, even on a day with more muscles than the budget.
        val all = starterExercises(BUILDER_MUSCLES.toSet())
        assertEquals(BUILDER_MUSCLES.toSet(), all.map { it.second }.toSet())
        assertTrue(starterExercises(emptySet()).isEmpty())
    }

    @Test
    fun starterExercises_secondCopyOfADayIsDifferent() {
        val a = starterExercises(setOf("Chest", "Shoulders", "Triceps"), 0).map { it.first }
        val b = starterExercises(setOf("Chest", "Shoulders", "Triceps"), 1).map { it.first }
        assertTrue(a.intersect(b.toSet()).isEmpty())
        assertEquals(b.size, b.distinct().size)
    }
}
