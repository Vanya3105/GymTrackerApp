package com.example.gymtracker

import java.util.Locale

/*
 * Personal recommendations from the setup answers: which split, which exercises, and how many
 * sets, reps and how much rest. Old profiles have no answers (all null) and get the app's
 * earlier defaults, unchanged.
 *
 * Where the numbers come from:
 * - ACSM position stand "Progression models in resistance training" (2009): novices 2–3 days a
 *   week at 8–12 reps, intermediate 3–4, advanced 4–5; heavier loads (1–6 reps) build the most
 *   strength.
 * - Schoenfeld et al. 2016–2019 meta-analyses: about 10+ hard sets a week per muscle grow more than
 *   fewer, and each muscle about twice a week; a wide range of loads (about 6–30 reps) grows muscle
 *   about equally once sets are hard.
 * - Singer et al. 2024 (rest-interval meta-analysis): a small benefit to resting more than 60 s,
 *   none past about 90 s for growth; heavy strength sets need more (2–3 min).
 * - Maeo et al. 2021 and 2023: seated leg curls and overhead triceps extensions (the muscle
 *   trained long) grew more than lying leg curls and pushdowns.
 */

enum class Experience(val label: String, val detail: String) {
    BEGINNER(tr("New"), tr("Under 6 months of lifting")),
    INTERMEDIATE(tr("Some"), tr("6 months to 2 years")),
    ADVANCED(tr("Experienced"), tr("2 years or more"))
}

enum class TrainingGoal(val label: String, val detail: String) {
    MUSCLE(tr("Build muscle"), tr("Grow size and shape")),
    STRENGTH(tr("Get stronger"), tr("Lift heavier on the big lifts")),
    GENERAL(tr("Get fit"), tr("Strength and health, without the extremes"))
}

enum class Equipment(val label: String, val detail: String) {
    GYM(tr("A gym"), tr("Barbells, machines and cables")),
    DUMBBELLS(tr("Dumbbells at home"), tr("Dumbbells and a bench")),
    BODYWEIGHT(tr("Bodyweight"), tr("No weights, a pull-up bar helps"))
}

/** Areas to go easy on: lifts that load them a lot are swapped for gentler ones. */
enum class Limit(val label: String) {
    SHOULDERS(tr("Shoulders")),
    KNEES(tr("Knees")),
    LOWER_BACK(tr("Lower back"))
}

/** Sets, reps and rest for one exercise. */
data class Prescription(val sets: Int, val repMin: Int, val repMax: Int, val restSeconds: Int) {
    val repText: String get() = "$repMin–$repMax"
}

/** The answers that matter, read from the profile; null when setup questions were never answered. */
private val UserProfile.answered: Boolean get() = experience != null && goal != null

/** The profile recommendations use, kept current like [Units]. */
object TrainingProfile {
    @Volatile var current: UserProfile = UserProfile()
}

// --- Split ---

/** The split that fits [profile]'s days, experience, goal and equipment, with why. */
fun recommendedProgram(profile: UserProfile): Pair<String, String>? {
    if (!profile.answered) return null
    val level = profile.experience!!
    val goal = profile.goal!!
    val days = profile.daysPerWeek.takeIf { it > 0 } ?: if (level == Experience.BEGINNER) 3 else 4
    // Percentage programs need a barbell and a rack.
    val barbell = profile.equipment == null || profile.equipment == Equipment.GYM
    val strength = goal == TrainingGoal.STRENGTH && barbell && level != Experience.BEGINNER
    val id = when {
        days <= 2 -> "FULL_BODY_2"
        days == 3 -> "FULL_BODY"
        level == Experience.BEGINNER -> if (days >= 4) "UPPER_LOWER" else "FULL_BODY"
        days == 4 -> when {
            strength && level == Experience.ADVANCED -> "WENDLER_531"
            strength -> "GZCLP"
            goal == TrainingGoal.MUSCLE && level == Experience.ADVANCED && barbell -> "PHUL"
            else -> "UPPER_LOWER"
        }
        strength && days == 5 -> "NSUNS_5"
        else -> "PPL_6"
    }
    val why = when (id) {
        "FULL_BODY_2" -> tr("Two full-body days still train every muscle twice a week.")
        "FULL_BODY" -> if (level == Experience.BEGINNER) tr("Three full-body days: the best start, every muscle twice or more a week, with practice on the main lifts.")
        else tr("Three full-body days train every muscle about three times a week.")
        "UPPER_LOWER" -> tr("Four days of upper and lower body: every muscle twice a week, with time to recover.")
        "GZCLP" -> tr("Heavy main lifts that go up each week, with lighter work for size: made for getting stronger.")
        "WENDLER_531" -> tr("Monthly waves on the four big lifts: steady strength for experienced lifters.")
        "PHUL" -> tr("Two heavy days and two muscle-building days: strength and size together.")
        "NSUNS_5" -> tr("High-volume percentage work five days a week: fast strength gains if you recover well.")
        else -> tr("Push, pull and legs twice a week: lots of volume for every muscle.")
    }
    return id to why
}

// --- Sets, reps and rest ---

/** The big multi-joint lifts that carry the heavy, low-rep work on a strength goal. */
private val MAIN_LIFTS = setOf(
    "back squat", "front squat", "box squat", "barbell bench press", "incline barbell bench press", "close-grip bench press",
    "overhead press", "deadlift", "trap bar deadlift", "sumo deadlift", "barbell row", "pendlay row", "weighted pull-up", "weighted dips"
)

internal fun isCompound(exerciseName: String): Boolean {
    val lower = exerciseName.lowercase(Locale.US)
    return listOf("squat", "bench", "press", "deadlift", "rdl", "row", "pull-up", "pullup", "chin-up", "pulldown", "dip", "lunge", "thrust", "push-up").any {
        lower.contains(it)
    } && !lower.contains("pushdown") && !lower.contains("extension") && !lower.contains("fly")
}

/** What [profile] should do for [exerciseName]; null without setup answers. */
fun prescriptionFor(profile: UserProfile, exerciseName: String): Prescription? {
    if (!profile.answered) return null
    val level = profile.experience!!
    val compound = isCompound(exerciseName)
    val main = exerciseName.lowercase(Locale.US) in MAIN_LIFTS
    // Two hard sets is plenty to start; more as recovery and skill grow.
    val sets = when (level) {
        Experience.BEGINNER -> if (compound) 3 else 2
        Experience.INTERMEDIATE -> 3
        Experience.ADVANCED -> if (compound) 4 else 3
    }
    return when (profile.goal!!) {
        TrainingGoal.STRENGTH -> when {
            main && level != Experience.BEGINNER -> Prescription(if (level == Experience.ADVANCED) 5 else 4, 3, 6, 180)
            // New lifters build strength fine with moderate reps while learning the lifts.
            main -> Prescription(3, 5, 8, 150)
            compound -> Prescription(sets, 6, 10, 120)
            else -> Prescription(sets, 8, 12, 90)
        }
        TrainingGoal.MUSCLE -> when {
            compound && level == Experience.BEGINNER -> Prescription(sets, 8, 12, 120)
            compound -> Prescription(sets, 6, 10, 120)
            else -> Prescription(sets, 10, 15, 90)
        }
        TrainingGoal.GENERAL -> if (compound) Prescription(sets.coerceAtMost(3), 8, 12, 90) else Prescription(2, 12, 15, 60)
    }
}

// --- Exercise choice ---

/** Library exercises that need a bar to hang from (home dumbbell setups don't have one). */
private val NEEDS_BAR = setOf(
    "pull-up", "chin-up", "neutral-grip pull-up", "negative pull-up", "scapular pull-up", "weighted pull-up", "muscle-up",
    "hanging leg raise", "hanging knee raise", "toes-to-bar", "dead hang"
)
/** Bodyweight moves that still need gear most homes don't have. */
private val NEEDS_GEAR = setOf("dips", "weighted dips", "weighted pull-up", "assisted dip", "glute-ham raise", "back extension", "ab wheel rollout", "decline sit-up", "box jump", "l-sit", "nordic curl")

/** Listed with dumbbells, but just as good with no weight at all. */
private val ALSO_BODYWEIGHT = setOf(
    "bulgarian split squat", "reverse lunge", "walking lunge", "step-up", "single-leg romanian deadlift", "curtsy lunge"
)

/** Whether [name] can be done with [equipment]. */
internal fun fitsEquipment(name: String, equipment: Equipment?): Boolean {
    if (equipment == null || equipment == Equipment.GYM) return true
    val info = ExerciseDatabase.find(name) ?: return true
    val key = name.lowercase(Locale.US)
    if (key in NEEDS_GEAR) return false
    return when (equipment) {
        Equipment.DUMBBELLS -> info.equipment in setOf("Dumbbell", "Bodyweight", "None") && key !in NEEDS_BAR
        Equipment.BODYWEIGHT -> info.equipment in setOf("Bodyweight", "None") || key in ALSO_BODYWEIGHT
        Equipment.GYM -> true
    }
}

/** Lifts that load each area a lot. */
internal val HARD_ON = mapOf(
    Limit.SHOULDERS to setOf(
        "overhead press", "push press", "seated barbell press", "smith machine shoulder press", "standing dumbbell press", "arnold press",
        "upright row", "dumbbell upright row", "cable upright row", "dips", "weighted dips", "tricep dips", "ring dips", "wide-grip bench press",
        "handstand push-up", "pike push-up", "behind-the-back wrist curl", "skull crusher", "dumbbell pullover", "muscle-up", "clap push-up"
    ),
    Limit.KNEES to setOf(
        "back squat", "front squat", "hack squat", "pendulum squat", "sissy squat", "jump squat", "jumping lunge", "box jump", "pistol squat",
        "shrimp squat", "walking lunge", "dumbbell lunge", "barbell lunge", "bulgarian split squat", "cossack squat", "pogo hops", "leg extension"
    ),
    Limit.LOWER_BACK to setOf(
        "deadlift", "deficit deadlift", "sumo deadlift", "rack pull", "good morning", "barbell row", "underhand barbell row", "pendlay row",
        "t-bar row", "bent-over dumbbell row", "stiff-leg deadlift", "romanian deadlift", "dumbbell romanian deadlift", "back squat", "front squat", "overhead press",
        "sit-up", "decline sit-up", "v-up", "superman hold", "back extension", "kettlebell swing"
    )
)

/**
 * Replacements in order of preference: the same movement and muscle first. The first one that
 * fits the equipment and limits, and isn't already on the day, is used.
 */
internal val ALTERNATIVES: Map<String, List<String>> = mapOf(
    // Chest
    "Barbell Bench Press" to listOf("Dumbbell Bench Press", "Neutral-Grip Dumbbell Press", "Machine Chest Press", "Push-Up"),
    "Incline Barbell Bench Press" to listOf("Incline Dumbbell Press", "Incline Machine Press", "Decline Push-Up"),
    "Wide-Grip Bench Press" to listOf("Dumbbell Bench Press", "Push-Up"),
    "Machine Chest Press" to listOf("Dumbbell Bench Press", "Push-Up"),
    "Incline Dumbbell Press" to listOf("Dumbbell Bench Press", "Decline Push-Up"),
    "Dumbbell Bench Press" to listOf("Push-Up"),
    "Pec Deck" to listOf("Cable Fly", "Dumbbell Fly", "Wide Push-Up"),
    "Cable Fly" to listOf("Pec Deck", "Dumbbell Fly", "Wide Push-Up"),
    "Dips" to listOf("Machine Chest Press", "Decline Push-Up", "Dumbbell Bench Press"),
    "Weighted Dips" to listOf("Machine Chest Press", "Dumbbell Bench Press", "Decline Push-Up"),
    // Back
    "Lat Pulldown" to listOf("Pull-Up", "Dumbbell Row", "Inverted Row"),
    "Close-Grip Lat Pulldown" to listOf("Neutral-Grip Pull-Up", "Dumbbell Pullover", "Inverted Row"),
    "Weighted Pull-Up" to listOf("Pull-Up", "Lat Pulldown", "Dumbbell Row", "Inverted Row"),
    "Pull-Up" to listOf("Lat Pulldown", "Dumbbell Row", "Inverted Row"),
    "Chin-Up" to listOf("Lat Pulldown", "Dumbbell Row", "Inverted Row"),
    "Straight-Arm Pulldown" to listOf("Dumbbell Pullover", "Superman Hold"),
    "Barbell Row" to listOf("Chest-Supported Row", "Bent-Over Dumbbell Row", "Chest-Supported Dumbbell Row", "Inverted Row"),
    "Pendlay Row" to listOf("Chest-Supported Row", "Bent-Over Dumbbell Row", "Chest-Supported Dumbbell Row", "Inverted Row"),
    "T-Bar Row" to listOf("Chest-Supported Row", "Chest-Supported Dumbbell Row", "Dumbbell Row", "Inverted Row"),
    "Seated Cable Row" to listOf("Chest-Supported Row", "Chest-Supported Dumbbell Row", "Inverted Row"),
    "Chest-Supported Row" to listOf("Seated Cable Row", "Chest-Supported Dumbbell Row", "Inverted Row"),
    "Dumbbell Row" to listOf("Chest-Supported Dumbbell Row", "Inverted Row"),
    "Bent-Over Dumbbell Row" to listOf("Chest-Supported Dumbbell Row", "Dumbbell Row", "Inverted Row"),
    "Face Pull" to listOf("Rear Delt Fly", "Reverse Pec Deck", "Superman Hold"),
    "Deadlift" to listOf("Trap Bar Deadlift", "Hip Thrust", "Dumbbell Romanian Deadlift", "Single-Leg Romanian Deadlift"),
    "Sumo Deadlift" to listOf("Hip Thrust", "Sumo Squat", "Single-Leg Hip Thrust"),
    // Shoulders
    "Overhead Press" to listOf("Seated Dumbbell Shoulder Press", "Landmine Press", "Standing Dumbbell Press", "Pike Push-Up"),
    "Seated Dumbbell Shoulder Press" to listOf("Landmine Press", "Machine Shoulder Press", "Pike Push-Up"),
    "Machine Shoulder Press" to listOf("Seated Dumbbell Shoulder Press", "Landmine Press", "Pike Push-Up"),
    "Cable Lateral Raise" to listOf("Lateral Raise", "Machine Lateral Raise"),
    "Lateral Raise" to listOf("Cable Lateral Raise"),
    "Rear Delt Fly" to listOf("Reverse Pec Deck", "Superman Hold"),
    "Upright Row" to listOf("Lateral Raise", "Cable Lateral Raise"),
    // Arms
    "Barbell Curl" to listOf("Dumbbell Curl", "Chin-Up"),
    "Preacher Curl" to listOf("Machine Preacher Curl", "Concentration Curl", "Chin-Up"),
    "Cable Curl" to listOf("Dumbbell Curl", "Chin-Up"),
    "Incline Dumbbell Curl" to listOf("Bayesian Cable Curl", "Chin-Up"),
    "Hammer Curl" to listOf("Rope Hammer Curl", "Chin-Up"),
    "Tricep Pushdown" to listOf("Overhead Cable Tricep Extension", "Overhead Dumbbell Extension", "Diamond Push-Up"),
    "Rope Pushdown" to listOf("Overhead Cable Tricep Extension", "Overhead Dumbbell Extension", "Diamond Push-Up"),
    "Overhead Cable Tricep Extension" to listOf("Overhead Dumbbell Extension", "Bodyweight Tricep Extension"),
    "Skull Crusher" to listOf("Overhead Cable Tricep Extension", "Dumbbell Skull Crusher", "Bodyweight Tricep Extension"),
    "Close-Grip Bench Press" to listOf("Close-Grip Dumbbell Press", "Diamond Push-Up"),
    // Legs
    "Back Squat" to listOf("Leg Press", "Hack Squat", "Goblet Squat", "Box Squat", "Bodyweight Squat"),
    "Front Squat" to listOf("Leg Press", "Goblet Squat", "Box Squat", "Bodyweight Squat"),
    "Hack Squat" to listOf("Leg Press", "Goblet Squat", "Box Squat", "Bodyweight Squat"),
    "Leg Press" to listOf("Goblet Squat", "Box Squat", "Bulgarian Split Squat", "Bodyweight Squat", "Glute Bridge"),
    "Leg Extension" to listOf("Reverse Lunge", "Step-Up", "Sissy Squat", "Wall Sit"),
    "Bulgarian Split Squat" to listOf("Reverse Lunge", "Leg Press", "Step-Up", "Bodyweight Squat"),
    "Walking Lunge" to listOf("Reverse Lunge", "Leg Press", "Bodyweight Squat"),
    "Romanian Deadlift" to listOf("Seated Leg Curl", "Dumbbell Romanian Deadlift", "Single-Leg Romanian Deadlift", "Sliding Leg Curl"),
    "Stiff-Leg Deadlift" to listOf("Lying Leg Curl", "Dumbbell Romanian Deadlift", "Single-Leg Romanian Deadlift", "Sliding Leg Curl"),
    "Lying Leg Curl" to listOf("Seated Leg Curl", "Sliding Leg Curl", "Single-Leg Romanian Deadlift"),
    "Seated Leg Curl" to listOf("Lying Leg Curl", "Sliding Leg Curl", "Single-Leg Romanian Deadlift"),
    "Hip Thrust" to listOf("Machine Hip Thrust", "Single-Leg Hip Thrust", "Bodyweight Glute Bridge"),
    "Glute Bridge" to listOf("Hip Thrust", "Single-Leg Glute Bridge", "Bodyweight Glute Bridge"),
    "Hip Abduction Machine" to listOf("Fire Hydrant"),
    "Cable Kickback" to listOf("Donkey Kick"),
    "Standing Calf Raise" to listOf("Dumbbell Calf Raise", "Single-Leg Calf Raise"),
    "Seated Calf Raise" to listOf("Seated Dumbbell Calf Raise", "Single-Leg Calf Raise"),
    "Leg Press Calf Raise" to listOf("Standing Calf Raise", "Dumbbell Calf Raise", "Single-Leg Calf Raise"),
    // Core
    "Cable Crunch" to listOf("Machine Crunch", "Crunch"),
    "Hanging Leg Raise" to listOf("Lying Leg Raise", "Reverse Crunch"),
    "Ab Wheel Rollout" to listOf("Dead Bug", "Plank"),
    // Forearms
    "Farmer's Carry" to listOf("Dead Hang")
)

/** Harder versions a new lifter gets an easier step of. */
internal val BEGINNER_EASIER = mapOf(
    "Weighted Pull-Up" to "Lat Pulldown",
    "Weighted Dips" to "Machine Chest Press",
    "Dips" to "Machine Chest Press",
    "Pendlay Row" to "Chest-Supported Row",
    "Deadlift" to "Romanian Deadlift",
    "Hack Squat" to "Leg Press"
)

/** For size: the version that trains the muscle long, which grew more in studies (see the top). */
internal val FOR_MUSCLE = mapOf(
    "Lying Leg Curl" to "Seated Leg Curl",
    "Tricep Pushdown" to "Overhead Cable Tricep Extension"
)

/** How many exercises a day: new lifters do best with fewer, done well. */
private fun dayCap(level: Experience?): Int = if (level == Experience.BEGINNER) 5 else 6

/**
 * [exercises] (name to muscle) made to fit [profile]: equipment, areas to go easy on, experience
 * and goal. Exercises with nothing suitable to swap to are dropped. Without answers it's unchanged.
 */
fun personalize(exercises: List<Pair<String, String>>, profile: UserProfile): List<Pair<String, String>> {
    if (!profile.answered && profile.equipment == null && profile.limits.isEmpty()) return exercises
    val avoid = profile.limits.flatMap { HARD_ON[it].orEmpty() }.toSet()
    fun ok(name: String) = fitsEquipment(name, profile.equipment) && name.lowercase(Locale.US) !in avoid && ExerciseDatabase.find(name) != null
    val out = ArrayList<Pair<String, String>>()
    // A swap mustn't land on an exercise already picked, or one further down the day that stays.
    fun free(name: String) = out.none { exerciseKey(it.first) == exerciseKey(name) } &&
        exercises.none { exerciseKey(it.first) == exerciseKey(name) && ok(it.first) }
    for ((original, muscle) in exercises) {
        var name = original
        if (profile.experience == Experience.BEGINNER) BEGINNER_EASIER[name]?.takeIf { ok(it) && free(it) }?.let { name = it }
        if (profile.goal == TrainingGoal.MUSCLE) FOR_MUSCLE[name]?.takeIf { ok(it) && free(it) }?.let { name = it }
        if (!ok(name)) {
            name = (ALTERNATIVES[name].orEmpty() + ALTERNATIVES[original].orEmpty())
                .firstOrNull { ok(it) && free(it) } ?: continue
        }
        if (out.any { exerciseKey(it.first) == exerciseKey(name) }) continue
        out += name to (ExerciseDatabase.find(name)?.muscle ?: muscle)
    }
    return out.take(dayCap(profile.experience))
}

/**
 * A first-session weight to fill in (kg) before there's any history: light enough to learn on.
 * Dumbbells are per hand; a barbell starts at the empty bar for new lifters. Null without answers.
 */
fun startingWeightKg(profile: UserProfile, exerciseName: String): Float? {
    if (!profile.answered) return null
    val info = ExerciseDatabase.find(exerciseName) ?: return null
    val new = profile.experience == Experience.BEGINNER
    val compound = isCompound(exerciseName)
    return when (info.equipment) {
        "Dumbbell" -> if (compound) (if (new) 8f else 14f) else (if (new) 4f else 8f)
        "Barbell", "Smith machine", "EZ bar", "Trap bar" -> if (new) 20f else 40f
        "Kettlebell" -> if (new) 8f else 16f
        "Machine", "Cable" -> if (compound) (if (new) 25f else 40f) else (if (new) 10f else 20f)
        else -> null
    }
}

/**
 * What a day trains, for under its name: the built-in line ("Incline press, pulldown, squat") while
 * its exercises are the built-in ones, else the muscles its fitted exercises work.
 */
fun daySubtitle(split: WorkoutSplit, profile: UserProfile = TrainingProfile.current): String {
    val base = defaultTemplateForSplit(split.title)
    val fitted = personalize(base, profile)
    if (base.isEmpty() || fitted == base) return shown(split.subtitle)
    return fitted.map { it.second }.distinct().take(4).joinToString(", ") { shown(it) }
}
