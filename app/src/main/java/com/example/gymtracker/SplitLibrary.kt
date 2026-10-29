package com.example.gymtracker

import java.util.Locale

/** Extra search words for built-in programs, so "ppl", "bro" or "531" find them. */
private val SPLIT_KEYWORDS = mapOf(
    "PPL_ARNOLD" to listOf("ppl", "arnold", "arms"),
    "CLASSIC_PPL" to listOf("ppl", "classic", "beginner"),
    "UPPER_LOWER" to listOf("ul", "upper lower", "twice a week"),
    "WENDLER_531" to listOf("wendler", "531", "strength", "powerlifting", "training max"),
    "GZCLP" to listOf("gzcl", "linear", "strength", "beginner", "tiers"),
    "NSUNS_5" to listOf("nsuns", "531", "strength", "percentages", "powerlifting"),
    "FULL_BODY" to listOf("fb", "total body", "whole body", "beginner"),
    "FULL_BODY_2" to listOf("fb", "total body", "whole body", "beginner", "minimal", "busy"),
    "BRO_SPLIT" to listOf("bro", "body part", "bodybuilding", "one muscle"),
    "PPL_6" to listOf("ppl", "twice", "bodybuilding", "high frequency"),
    "PHUL" to listOf("power", "hypertrophy", "strength", "upper lower", "ul")
)

private fun norm(s: String) = s.lowercase(Locale.US).replace(Regex("[-'/×]"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * Ranked split search across name, tagline, day names, keywords and "N days". Every typed word
 * must match something; names that start with the query rank first. [days] filters by days a week.
 */
fun searchSplits(query: String, programs: List<SplitProgramInfo>, days: Int? = null): List<SplitProgramInfo> {
    val pool = if (days == null) programs else programs.filter { it.weeklyGoal == days }
    val q = norm(query)
    if (q.isEmpty()) return pool
    val words = q.split(" ")
    return pool.mapNotNull { p ->
        val name = norm(p.name)
        val keywords = SPLIT_KEYWORDS[p.id].orEmpty().map(::norm)
        val haystack = buildString {
            append(name); append(' ')
            append(norm(p.tagline)); append(' ')
            p.splits.forEach { append(norm(it.title)); append(' '); append(norm(it.subtitle)); append(' ') }
            keywords.forEach { append(it); append(' ') }
            append("${p.weeklyGoal} day ${p.weeklyGoal} days")
        }
        // "531" should match "5/3/1", which normalizes to "5 3 1".
        val squashed = haystack.replace(" ", "")
        if (!words.all { haystack.contains(it) || squashed.contains(it) }) return@mapNotNull null
        val score = when {
            name.startsWith(q) || name.replace(" ", "").startsWith(q.replace(" ", "")) -> 0
            keywords.any { it.startsWith(q) } -> 1
            name.split(" ").any { it.startsWith(words.first()) } -> 2
            else -> 3
        }
        score to p
    }.sortedBy { it.first }.map { it.second }
}

/** Muscles offered when building a split (cardio has its own exercises but isn't a training day's focus). */
val BUILDER_MUSCLES = ExerciseDatabase.muscleGroups - "Cardio"

private val UPPER = setOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms")
private val LOWER = setOf("Quads", "Hamstrings", "Glutes", "Calves")

private fun joinMuscles(muscles: List<String>): String =
    if (muscles.size <= 1) muscles.joinToString() else muscles.dropLast(1).joinToString(", ") + " & " + muscles.last()

/**
 * A name for a day from its muscles, matching the built-in day names where it can ("Push",
 * "Legs", "Chest & Back"), so a new split picks up the exercises and history those days already have.
 */
fun dayTitleFor(muscles: Set<String>, index: Int): String {
    if (muscles.isEmpty()) return "Day ${index + 1}"
    val ordered = BUILDER_MUSCLES.filter { it in muscles }
    val upper = muscles.count { it in UPPER }
    val lower = muscles.count { it in LOWER }
    return when {
        muscles == setOf("Chest", "Shoulders", "Triceps") -> "Push"
        muscles == setOf("Back", "Biceps") || muscles == setOf("Back", "Biceps", "Forearms") -> "Pull"
        muscles == setOf("Biceps", "Triceps") || muscles == setOf("Biceps", "Triceps", "Forearms") -> "Arms"
        muscles == setOf("Shoulders", "Biceps", "Triceps") -> "Arms & Shoulders"
        ordered.size <= 2 -> joinMuscles(ordered)
        upper == 0 && "Quads" in muscles && "Hamstrings" in muscles -> "Legs"
        upper == 0 -> if (ordered.size == 3) joinMuscles(ordered) else "Lower"
        lower == 0 -> if ("Chest" in muscles && "Back" in muscles) "Upper" else if (ordered.size == 3) joinMuscles(ordered) else "Upper"
        upper >= 2 && lower >= 2 -> "Full Body"
        ordered.size == 3 -> joinMuscles(ordered)
        else -> "${ordered[0]} & ${ordered[1]} +${ordered.size - 2}"
    }
}

/**
 * Day names have to be unique within a program; repeated ones get A, B, C… ("Push A", "Push B"),
 * which is also how the built-in programs name repeated days.
 */
fun uniqueDayTitles(titles: List<String>): List<String> {
    val counts = titles.groupingBy { it.lowercase(Locale.US) }.eachCount()
    val seen = HashMap<String, Int>()
    val result = titles.map { t ->
        val key = t.lowercase(Locale.US)
        if ((counts[key] ?: 0) <= 1) t
        else {
            val n = seen.getOrDefault(key, 0)
            seen[key] = n + 1
            "$t ${'A' + n}"
        }
    }.toMutableList()
    // A suffixed name can still clash with one the user typed ("Push A" twice): number those.
    val used = HashSet<String>()
    result.indices.forEach { i ->
        var t = result[i]
        var n = 2
        while (!used.add(t.lowercase(Locale.US))) t = "${result[i]} ${n++}"
        result[i] = t
    }
    return result
}

/** A day of a quick layout: its name and muscles. */
data class LayoutDay(val title: String, val muscles: Set<String>)

/** Ready-made patterns the builder can fill the days with; each repeats over the week. */
enum class QuickLayout(val label: String, val days: List<LayoutDay>) {
    PPL("Push / Pull / Legs", listOf(
        LayoutDay("Push", setOf("Chest", "Shoulders", "Triceps")),
        LayoutDay("Pull", setOf("Back", "Biceps")),
        LayoutDay("Legs", setOf("Quads", "Hamstrings", "Glutes", "Calves"))
    )),
    UPPER_LOWER("Upper / Lower", listOf(
        LayoutDay("Upper", setOf("Chest", "Back", "Shoulders", "Biceps", "Triceps")),
        LayoutDay("Lower", setOf("Quads", "Hamstrings", "Glutes", "Calves", "Core"))
    )),
    FULL_BODY("Full body", listOf(
        LayoutDay("Full Body", setOf("Chest", "Back", "Shoulders", "Quads", "Hamstrings"))
    )),
    BRO("One muscle a day", listOf(
        LayoutDay("Chest", setOf("Chest")),
        LayoutDay("Back", setOf("Back")),
        LayoutDay("Shoulders", setOf("Shoulders")),
        LayoutDay("Arms", setOf("Biceps", "Triceps")),
        LayoutDay("Legs", setOf("Quads", "Hamstrings", "Glutes", "Calves"))
    ));

    fun fill(dayCount: Int): List<LayoutDay> = List(dayCount) { days[it % days.size] }

    companion object {
        /** What most people run on that many days. */
        fun suggestedFor(dayCount: Int): QuickLayout = when (dayCount) {
            1, 2 -> FULL_BODY
            3 -> PPL
            4 -> UPPER_LOWER
            5 -> BRO
            else -> PPL
        }
    }
}

/** Starter exercises per muscle, best first. All from [ExerciseDatabase]. */
internal val STARTER_EXERCISES = mapOf(
    "Chest" to listOf("Barbell Bench Press", "Incline Dumbbell Press", "Cable Fly", "Machine Chest Press", "Pec Deck", "Incline Barbell Bench Press"),
    "Back" to listOf("Lat Pulldown", "Barbell Row", "Seated Cable Row", "Chest-Supported Row", "Close-Grip Lat Pulldown", "Dumbbell Row"),
    "Shoulders" to listOf("Seated Dumbbell Shoulder Press", "Lateral Raise", "Rear Delt Fly", "Overhead Press", "Cable Lateral Raise", "Face Pull"),
    "Biceps" to listOf("Barbell Curl", "Incline Dumbbell Curl", "Hammer Curl", "Preacher Curl", "Cable Curl"),
    "Triceps" to listOf("Tricep Pushdown", "Overhead Cable Tricep Extension", "Skull Crusher", "Rope Pushdown", "Close-Grip Bench Press"),
    "Quads" to listOf("Back Squat", "Leg Press", "Leg Extension", "Hack Squat", "Bulgarian Split Squat"),
    "Hamstrings" to listOf("Romanian Deadlift", "Lying Leg Curl", "Seated Leg Curl", "Stiff-Leg Deadlift"),
    "Glutes" to listOf("Hip Thrust", "Hip Abduction Machine", "Cable Kickback", "Glute Bridge"),
    "Calves" to listOf("Standing Calf Raise", "Seated Calf Raise", "Leg Press Calf Raise"),
    "Core" to listOf("Cable Crunch", "Hanging Leg Raise", "Ab Wheel Rollout", "Plank"),
    "Forearms" to listOf("Wrist Curl", "Reverse Curl", "Farmer's Carry")
)

private val BIG_MUSCLES = setOf("Chest", "Back", "Shoulders", "Quads", "Hamstrings")

/**
 * Starting exercises (name to muscle) for a new day: about six, compounds first; with four or
 * more muscles the bigger ones get two. A day with one or two muscles goes deeper on each. [variant] > 0 (the B, C… copies
 * of a repeated day) starts further down each muscle's list, so Push B isn't a copy of Push A.
 */
fun starterExercises(muscles: Set<String>, variant: Int = 0): List<Pair<String, String>> {
    val ordered = BUILDER_MUSCLES.filter { it in muscles }
    if (ordered.isEmpty()) return emptyList()
    fun quota(m: String) = when (ordered.size) {
        1 -> 5
        2 -> 3
        3 -> 2
        else -> if (m in BIG_MUSCLES) 2 else 1
    }
    val budget = maxOf(6, ordered.size)
    val picked = ArrayList<Pair<String, String>>()
    for (round in 0 until 5) {
        for (m in ordered) {
            if (picked.size >= budget) break
            if (round >= quota(m)) continue
            val list = STARTER_EXERCISES[m].orEmpty()
            if (round >= list.size) continue
            val name = list[(variant * quota(m) + round) % list.size]
            if (picked.none { it.first == name }) picked += name to m
        }
    }
    return picked
}
