package com.example.gymtracker

import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class ExerciseInfo(
    val name: String,
    val muscle: String,
    val equipment: String,
    val aliases: List<String> = emptyList()
)

/** Built-in exercise library used by the "Add exercise" search. */
object ExerciseDatabase {

    val muscleGroups = listOf(
        "Chest", "Back", "Shoulders", "Biceps", "Triceps",
        "Quads", "Hamstrings", "Glutes", "Calves", "Core", "Forearms"
    )

    private fun ex(name: String, muscle: String, equipment: String, vararg aliases: String) =
        ExerciseInfo(name, muscle, equipment, aliases.toList())

    val all: List<ExerciseInfo> = listOf(
        // Chest
        ex("Barbell Bench Press", "Chest", "Barbell", "flat bench", "bp"),
        ex("Incline Barbell Bench Press", "Chest", "Barbell", "incline bench"),
        ex("Decline Barbell Bench Press", "Chest", "Barbell"),
        ex("Dumbbell Bench Press", "Chest", "Dumbbell", "db bench"),
        ex("Incline Dumbbell Press", "Chest", "Dumbbell", "incline db"),
        ex("Decline Dumbbell Press", "Chest", "Dumbbell"),
        ex("Smith Machine Bench Press", "Chest", "Smith machine"),
        ex("Smith Machine Incline Press", "Chest", "Smith machine"),
        ex("Machine Chest Press", "Chest", "Machine", "chest press"),
        ex("Incline Machine Press", "Chest", "Machine"),
        ex("Pec Deck", "Chest", "Machine", "butterfly", "machine fly"),
        ex("Cable Fly", "Chest", "Cable", "cable crossover"),
        ex("Low-to-High Cable Fly", "Chest", "Cable", "upper chest fly"),
        ex("High-to-Low Cable Fly", "Chest", "Cable"),
        ex("Dumbbell Fly", "Chest", "Dumbbell", "db fly"),
        ex("Incline Dumbbell Fly", "Chest", "Dumbbell"),
        ex("Push-Up", "Chest", "Bodyweight", "pushup", "press up"),
        ex("Weighted Dips", "Chest", "Bodyweight", "chest dips"),
        ex("Dips", "Chest", "Bodyweight", "parallel bar dips"),

        // Back
        ex("Pull-Up", "Back", "Bodyweight", "pullup"),
        ex("Weighted Pull-Up", "Back", "Bodyweight"),
        ex("Chin-Up", "Back", "Bodyweight", "chinup"),
        ex("Lat Pulldown", "Back", "Cable", "pulldown"),
        ex("Close-Grip Lat Pulldown", "Back", "Cable", "v-bar pulldown"),
        ex("Single-Arm Lat Pulldown", "Back", "Cable"),
        ex("Straight-Arm Pulldown", "Back", "Cable", "lat pushdown", "pullover"),
        ex("Barbell Row", "Back", "Barbell", "bent over row", "bb row"),
        ex("Pendlay Row", "Back", "Barbell"),
        ex("T-Bar Row", "Back", "Machine", "tbar"),
        ex("Dumbbell Row", "Back", "Dumbbell", "one arm row", "db row"),
        ex("Chest-Supported Row", "Back", "Machine", "seal row"),
        ex("Chest-Supported Dumbbell Row", "Back", "Dumbbell", "incline row"),
        ex("Seated Cable Row", "Back", "Cable", "cable row", "low row"),
        ex("Machine Row", "Back", "Machine", "seated row"),
        ex("Meadows Row", "Back", "Barbell", "landmine row"),
        ex("Deadlift", "Back", "Barbell", "conventional deadlift", "dl"),
        ex("Rack Pull", "Back", "Barbell"),
        ex("Back Extension", "Back", "Bodyweight", "hyperextension"),
        ex("Machine Pullover", "Back", "Machine"),
        ex("Dumbbell Shrug", "Back", "Dumbbell", "traps", "shrugs"),
        ex("Barbell Shrug", "Back", "Barbell", "traps"),

        // Shoulders
        ex("Overhead Press", "Shoulders", "Barbell", "ohp", "military press", "standing press"),
        ex("Seated Dumbbell Shoulder Press", "Shoulders", "Dumbbell", "db shoulder press"),
        ex("Machine Shoulder Press", "Shoulders", "Machine"),
        ex("Smith Machine Shoulder Press", "Shoulders", "Smith machine"),
        ex("Arnold Press", "Shoulders", "Dumbbell"),
        ex("Lateral Raise", "Shoulders", "Dumbbell", "side raise", "lateral raises"),
        ex("Cable Lateral Raise", "Shoulders", "Cable"),
        ex("Machine Lateral Raise", "Shoulders", "Machine"),
        ex("Lean-Away Lateral Raise", "Shoulders", "Dumbbell"),
        ex("Front Raise", "Shoulders", "Dumbbell"),
        ex("Rear Delt Fly", "Shoulders", "Dumbbell", "reverse fly", "rear delt"),
        ex("Reverse Pec Deck", "Shoulders", "Machine", "rear delt machine"),
        ex("Face Pull", "Shoulders", "Cable", "rear delt"),
        ex("Upright Row", "Shoulders", "Barbell"),
        ex("Cable Y-Raise", "Shoulders", "Cable"),

        // Biceps
        ex("Barbell Curl", "Biceps", "Barbell", "bb curl"),
        ex("EZ-Bar Curl", "Biceps", "EZ bar"),
        ex("Dumbbell Curl", "Biceps", "Dumbbell", "db curl"),
        ex("Incline Dumbbell Curl", "Biceps", "Dumbbell"),
        ex("Hammer Curl", "Biceps", "Dumbbell", "hammer curls"),
        ex("Preacher Curl", "Biceps", "EZ bar"),
        ex("Machine Preacher Curl", "Biceps", "Machine"),
        ex("Cable Curl", "Biceps", "Cable"),
        ex("Bayesian Cable Curl", "Biceps", "Cable"),
        ex("Concentration Curl", "Biceps", "Dumbbell"),
        ex("Spider Curl", "Biceps", "Dumbbell"),
        ex("Rope Hammer Curl", "Biceps", "Cable"),

        // Triceps
        ex("Tricep Pushdown", "Triceps", "Cable", "pushdown", "triceps pushdown"),
        ex("Rope Pushdown", "Triceps", "Cable", "rope tricep"),
        ex("Overhead Cable Tricep Extension", "Triceps", "Cable", "overhead extension"),
        ex("Overhead Dumbbell Extension", "Triceps", "Dumbbell", "french press"),
        ex("Skull Crusher", "Triceps", "EZ bar", "lying tricep extension", "skullcrusher"),
        ex("Close-Grip Bench Press", "Triceps", "Barbell", "cgbp"),
        ex("JM Press", "Triceps", "Barbell"),
        ex("Tricep Dips", "Triceps", "Bodyweight", "bench dips"),
        ex("Machine Tricep Extension", "Triceps", "Machine"),
        ex("Single-Arm Cable Extension", "Triceps", "Cable"),
        ex("Dumbbell Kickback", "Triceps", "Dumbbell"),

        // Quads
        ex("Back Squat", "Quads", "Barbell", "squat", "barbell squat"),
        ex("Front Squat", "Quads", "Barbell"),
        ex("Hack Squat", "Quads", "Machine"),
        ex("Pendulum Squat", "Quads", "Machine"),
        ex("Smith Machine Squat", "Quads", "Smith machine"),
        ex("Goblet Squat", "Quads", "Dumbbell"),
        ex("Leg Press", "Quads", "Machine"),
        ex("Leg Extension", "Quads", "Machine", "quad extension"),
        ex("Bulgarian Split Squat", "Quads", "Dumbbell", "bss", "split squat"),
        ex("Walking Lunge", "Quads", "Dumbbell", "lunges"),
        ex("Reverse Lunge", "Quads", "Dumbbell"),
        ex("Step-Up", "Quads", "Dumbbell"),
        ex("Sissy Squat", "Quads", "Bodyweight"),
        ex("Belt Squat", "Quads", "Machine"),

        // Hamstrings
        ex("Romanian Deadlift", "Hamstrings", "Barbell", "rdl"),
        ex("Dumbbell Romanian Deadlift", "Hamstrings", "Dumbbell", "db rdl"),
        ex("Stiff-Leg Deadlift", "Hamstrings", "Barbell", "sldl"),
        ex("Seated Leg Curl", "Hamstrings", "Machine", "hamstring curl"),
        ex("Lying Leg Curl", "Hamstrings", "Machine"),
        ex("Standing Leg Curl", "Hamstrings", "Machine"),
        ex("Nordic Curl", "Hamstrings", "Bodyweight", "nordic ham curl"),
        ex("Good Morning", "Hamstrings", "Barbell"),
        ex("Glute-Ham Raise", "Hamstrings", "Bodyweight", "ghr"),

        // Glutes
        ex("Hip Thrust", "Glutes", "Barbell", "barbell hip thrust"),
        ex("Machine Hip Thrust", "Glutes", "Machine"),
        ex("Glute Bridge", "Glutes", "Barbell"),
        ex("Cable Kickback", "Glutes", "Cable", "glute kickback"),
        ex("Hip Abduction Machine", "Glutes", "Machine", "abductor"),
        ex("Hip Adduction Machine", "Glutes", "Machine", "adductor"),
        ex("Sumo Deadlift", "Glutes", "Barbell"),
        ex("Cable Pull-Through", "Glutes", "Cable"),

        // Calves
        ex("Standing Calf Raise", "Calves", "Machine", "calf raise"),
        ex("Seated Calf Raise", "Calves", "Machine"),
        ex("Leg Press Calf Raise", "Calves", "Machine"),
        ex("Smith Machine Calf Raise", "Calves", "Smith machine"),
        ex("Single-Leg Calf Raise", "Calves", "Bodyweight"),

        // Core
        ex("Cable Crunch", "Core", "Cable", "abs"),
        ex("Hanging Leg Raise", "Core", "Bodyweight", "abs", "leg raise"),
        ex("Hanging Knee Raise", "Core", "Bodyweight", "abs"),
        ex("Ab Wheel Rollout", "Core", "Bodyweight", "abs"),
        ex("Plank", "Core", "Bodyweight", "abs"),
        ex("Decline Sit-Up", "Core", "Bodyweight", "abs", "situp"),
        ex("Machine Crunch", "Core", "Machine", "abs"),
        ex("Pallof Press", "Core", "Cable", "anti rotation"),
        ex("Russian Twist", "Core", "Bodyweight", "obliques"),
        ex("Woodchopper", "Core", "Cable", "obliques"),

        // Forearms
        ex("Wrist Curl", "Forearms", "Dumbbell"),
        ex("Reverse Wrist Curl", "Forearms", "Dumbbell"),
        ex("Reverse Curl", "Forearms", "EZ bar"),
        ex("Farmer's Carry", "Forearms", "Dumbbell", "farmers walk", "grip"),
        ex("Dead Hang", "Forearms", "Bodyweight", "grip")
    )

    private fun norm(s: String) = s.lowercase(Locale.US).replace("-", " ").replace("'", "")

    /**
     * Ranked search across name, aliases, muscle and equipment. Every typed word must match
     * something; names that start with the query rank first.
     */
    fun search(query: String, muscle: String? = null): List<ExerciseInfo> {
        val pool = if (muscle == null) all else all.filter { it.muscle == muscle }
        val q = norm(query.trim())
        if (q.isEmpty()) return pool.sortedBy { it.name }
        val words = q.split(" ").filter { it.isNotBlank() }

        return pool.mapNotNull { info ->
            val name = norm(info.name)
            val haystack = buildString {
                append(name); append(' ')
                info.aliases.forEach { append(norm(it)); append(' ') }
                append(norm(info.muscle)); append(' ')
                append(norm(info.equipment))
            }
            if (!words.all { haystack.contains(it) }) return@mapNotNull null
            val score = when {
                name.startsWith(q) -> 0
                info.aliases.any { norm(it).startsWith(q) } -> 1
                name.split(" ").any { it.startsWith(words.first()) } -> 2
                else -> 3
            }
            score to info
        }.sortedWith(compareBy({ it.first }, { it.second.name.length })).map { it.second }
    }

    fun find(name: String): ExerciseInfo? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
