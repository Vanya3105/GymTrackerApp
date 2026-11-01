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

/**
 * Muscles to prioritise. A priority muscle gets more weekly sets (growth rises with sets: Schoenfeld
 * et al. 2017; Pelland et al.), a session more where the week allows, and is trained first in the
 * day, when fresh (muscles trained first gain more: Simão et al. 2012; Nunes et al. 2021).
 */
enum class Focus(val label: String, val detail: String, val muscles: Set<String>) {
    BALANCED(tr("Balanced"), tr("Every muscle the same"), emptySet()),
    UPPER(tr("Upper body"), tr("Chest, back and shoulders"), setOf("Chest", "Back", "Shoulders")),
    ARMS(tr("Arms"), tr("Biceps and triceps"), setOf("Biceps", "Triceps")),
    LOWER(tr("Legs and glutes"), tr("Quads, hamstrings and glutes"), setOf("Quads", "Hamstrings", "Glutes"))
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

/**
 * The split for [profile]: always one built from the answers ([planWeek]), so a different answer
 * gives a different week. The built-in programs stay there to pick by hand. With why.
 */
fun recommendedProgram(profile: UserProfile): Pair<String, String>? {
    if (!profile.answered) return null
    return RECOMMENDED_PLAN_ID to planReason(profile)
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
    "Pull-Up" to "Lat Pulldown",
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
    if (!profile.answered && profile.equipment == null && profile.limits.isEmpty() && profile.skipMuscles.isEmpty()) return exercises
    val avoid = profile.limits.flatMap { HARD_ON[it].orEmpty() }.toSet()
    fun skipped(name: String, muscle: String) = (ExerciseDatabase.find(name)?.muscle ?: muscle) in profile.skipMuscles
    fun ok(name: String) = fitsEquipment(name, profile.equipment) && name.lowercase(Locale.US) !in avoid &&
        ExerciseDatabase.find(name).let { it != null && it.muscle !in profile.skipMuscles }
    val out = ArrayList<Pair<String, String>>()
    // A swap mustn't land on an exercise already picked, or one further down the day that stays.
    fun free(name: String) = out.none { exerciseKey(it.first) == exerciseKey(name) } &&
        exercises.none { exerciseKey(it.first) == exerciseKey(name) && ok(it.first) }
    for ((original, muscle) in exercises) {
        if (skipped(original, muscle)) continue
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

// --- The plan: a week built from the answers ---

const val RECOMMENDED_PLAN_ID = "recommended-plan"

/** Skipping any of these changes what a week should look like; skipping the rest just drops a few exercises. */
internal val MAJOR_MUSCLES = setOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes")
private val PUSH_MUSCLES = setOf("Chest", "Shoulders", "Triceps")
private val PULL_MUSCLES = setOf("Back", "Biceps", "Forearms")
private val LEG_MUSCLES = setOf("Quads", "Hamstrings", "Glutes", "Calves")
/** Muscles that also work hard in other muscles' lifts (presses, rows, squats), so need fewer sets of their own. */
private val SMALL_MUSCLES = setOf("Biceps", "Triceps", "Forearms", "Calves", "Core")

fun trainedMuscles(profile: UserProfile): Set<String> = BUILDER_MUSCLES.filter { it !in profile.skipMuscles }.toSet()

/** How the week is laid out. */
internal enum class Layout(val label: String) {
    FULL_BODY(tr("full body every day")),
    UPPER_LOWER(tr("upper / lower")),
    UPPER_LOWER_FULL(tr("upper, lower and full body")),
    UPPER_LOWER_PPL(tr("upper / lower, then push / pull / legs")),
    PUSH_PULL_LEGS(tr("push / pull / legs")),
    PUSH_PULL(tr("push / pull")),
    /** Upper three times, lower twice: an upper body priority. */
    UPPER_FOCUS(tr("upper / lower with an extra upper day")),
    /** Lower three times, upper twice: a legs and glutes priority. */
    LOWER_FOCUS(tr("lower / upper with an extra lower day")),
    /** Chest, back and shoulders on one day, arms with legs on the other: arms trained fresh, not after presses and rows. */
    TORSO_LIMBS(tr("torso / arms and legs")),
    /** Chest & back, shoulders & arms, legs, twice: each part twice with its own day. */
    ARNOLD(tr("chest & back, shoulders & arms, legs"))
}

/**
 * The layout for [days] a week:
 * - 2 days, or a new lifter on 3: full body, every muscle at each session.
 * - 3 days: full body for strength and fitness (strength gains more from training a lift often,
 *   Pelland et al.); upper / lower / full for size once past the first months.
 * - 4 days: upper / lower twice.
 * - 5 days: upper / lower / full twice-ish for new lifters, strength and fitness; upper / lower
 *   then push / pull / legs for size (more sets fit when each day covers fewer muscles).
 * - 6 days: push / pull / legs twice for size; otherwise upper / lower / full twice.
 * Every muscle ends up trained at least twice a week (Schoenfeld et al. 2016).
 */
internal fun layoutFor(days: Int, level: Experience, goal: TrainingGoal, focus: Focus = Focus.BALANCED): Layout = when {
    days <= 2 -> Layout.FULL_BODY
    // Up to three days a week, full body is what gets every muscle twice; a priority shows in sets and order.
    focus == Focus.UPPER && days >= 4 -> if (days == 6 && goal == TrainingGoal.MUSCLE) Layout.ARNOLD else Layout.UPPER_FOCUS
    focus == Focus.LOWER && days >= 4 -> Layout.LOWER_FOCUS
    focus == Focus.ARMS && days >= 4 -> if (days == 6) Layout.ARNOLD else Layout.TORSO_LIMBS
    days == 3 -> if (level != Experience.BEGINNER && goal == TrainingGoal.MUSCLE) Layout.UPPER_LOWER_FULL else Layout.FULL_BODY
    days == 4 -> Layout.UPPER_LOWER
    goal == TrainingGoal.MUSCLE && level != Experience.BEGINNER -> if (days == 5) Layout.UPPER_LOWER_PPL else Layout.PUSH_PULL_LEGS
    else -> Layout.UPPER_LOWER_FULL
}

/** The muscles of each day: [layout] over [days], fitted to the muscles that are trained. */
internal fun weekMuscles(trained: Set<String>, days: Int, layout: Layout): List<Set<String>> {
    // Rows, pull-ups and curls already work the grip hard; forearms get their own work only without them.
    val muscles = if ("Back" in trained || "Biceps" in trained) trained - "Forearms" else trained
    var push = muscles intersect PUSH_MUSCLES
    var pull = muscles intersect PULL_MUSCLES
    var legs = muscles intersect LEG_MUSCLES
    // Forearms alone aren't worth a day: without back and biceps they ride along on another one.
    if ("Back" !in pull && "Biceps" !in pull && pull.isNotEmpty()) {
        if (push.isNotEmpty()) push = push + pull else legs = legs + pull
        pull = emptySet()
    }
    val upper = push + pull
    val kinds = listOf(push, pull, legs).count { it.isNotEmpty() }
    if (kinds <= 1) return List(days) { muscles }
    val core = if ("Core" in muscles) setOf("Core") else emptySet()
    val lower = if (legs.isNotEmpty()) legs + core else legs
    val full = muscles
    // Without legs, "lower" days don't exist: push and pull days take their place.
    val week: List<Set<String>> = if (legs.isEmpty()) {
        when (layout) {
            Layout.FULL_BODY -> listOf(full)
            Layout.UPPER_LOWER_FULL -> listOf(push + core, pull, full)
            else -> listOf(push, pull + core)
        }
    } else if (upper.isEmpty()) {
        listOf(lower)
    } else when (layout) {
        Layout.FULL_BODY -> listOf(full)
        Layout.UPPER_LOWER -> listOf(upper, lower)
        Layout.UPPER_LOWER_FULL -> if (days == 6) listOf(upper, lower, full) else if (days == 5) listOf(upper, lower, full, upper, lower) else listOf(upper, lower, full)
        Layout.UPPER_LOWER_PPL -> listOf(upper, lower, push, pull, lower)
        Layout.PUSH_PULL_LEGS -> listOf(push, pull, lower)
        Layout.PUSH_PULL -> listOf(push, pull + core)
        Layout.UPPER_FOCUS -> when (days) {
            4 -> listOf(upper, lower, upper, full)
            5 -> listOf(upper, lower, upper, lower, upper)
            else -> listOf(upper, lower, upper, lower, upper, full)
        }
        Layout.LOWER_FOCUS -> when (days) {
            4 -> listOf(lower, upper, lower, full)
            5 -> listOf(lower, upper, lower, upper, lower)
            else -> listOf(lower, upper, lower, upper, lower, full)
        }
        Layout.TORSO_LIMBS -> {
            val arms = muscles intersect setOf("Biceps", "Triceps", "Forearms")
            val torso = upper - arms
            val limbs = legs + arms + core
            if (torso.isEmpty() || limbs.isEmpty()) listOf(upper, lower)
            else if (days == 5) listOf(torso, limbs, torso, limbs, full) else listOf(torso, limbs)
        }
        Layout.ARNOLD -> {
            val chestBack = muscles intersect setOf("Chest", "Back")
            val shouldersArms = muscles intersect setOf("Shoulders", "Biceps", "Triceps", "Forearms")
            listOf(chestBack, shouldersArms, lower)
        }
    }
    // A day whose muscles are all skipped (push days without chest, shoulders and triceps) goes.
    val kept = week.filter { it.isNotEmpty() }.ifEmpty { listOf(muscles) }
    return List(days) { kept[it % kept.size] }
}

/**
 * Hard sets a week for [muscle]. Growth keeps rising with weekly sets, with diminishing returns
 * (Schoenfeld et al. 2017; Pelland et al.), and trained lifters need more than beginners; strength
 * levels off much sooner; for health and fitness modest amounts give most of the benefit (ACSM
 * 2026). Arms, calves and core get less of their own: presses, rows and squats work them too.
 */
internal fun weeklySets(profile: UserProfile, muscle: String): Int {
    val level = profile.experience ?: Experience.INTERMEDIATE
    val base = when (profile.goal ?: TrainingGoal.GENERAL) {
        TrainingGoal.MUSCLE -> when (level) { Experience.BEGINNER -> 10; Experience.INTERMEDIATE -> 14; Experience.ADVANCED -> 18 }
        TrainingGoal.STRENGTH -> when (level) { Experience.BEGINNER -> 8; Experience.INTERMEDIATE -> 10; Experience.ADVANCED -> 12 }
        TrainingGoal.GENERAL -> when (level) { Experience.BEGINNER -> 6; Experience.INTERMEDIATE -> 8; Experience.ADVANCED -> 10 }
    }
    val sets = if (muscle in SMALL_MUSCLES) (base * 0.6f).toInt().coerceAtLeast(3) else base
    // A priority muscle gets about a third more; arms as a priority get as much as a big muscle.
    val focus = focusOf(profile)
    return when {
        muscle !in focus.muscles -> sets
        focus == Focus.ARMS -> base
        else -> (sets * 1.33f).toInt()
    }
}

/** [profile]'s focus, or balanced when its muscles are all skipped. */
internal fun focusOf(profile: UserProfile): Focus {
    val f = profile.focus ?: Focus.BALANCED
    return if (f.muscles.isNotEmpty() && f.muscles.all { it in profile.skipMuscles }) Focus.BALANCED else f
}

/**
 * Exercises per muscle, best first, for each goal. For size, ones that load the muscle long
 * (incline curls, overhead extensions, seated leg curls: Maeo et al.); for strength, the barbell
 * lifts being trained and their helpers; for fitness, simple compound moves that are easy to learn.
 * Equipment, limits and experience are fitted afterwards ([personalize]).
 */
private val PICKS: Map<TrainingGoal, Map<String, List<String>>> = mapOf(
    TrainingGoal.MUSCLE to mapOf(
        "Chest" to listOf("Incline Dumbbell Press", "Barbell Bench Press", "Cable Fly", "Machine Chest Press", "Dips", "Pec Deck"),
        "Back" to listOf("Lat Pulldown", "Chest-Supported Row", "Pull-Up", "Seated Cable Row", "Barbell Row", "Close-Grip Lat Pulldown"),
        "Shoulders" to listOf("Cable Lateral Raise", "Seated Dumbbell Shoulder Press", "Lateral Raise", "Rear Delt Fly", "Face Pull"),
        "Biceps" to listOf("Incline Dumbbell Curl", "Bayesian Cable Curl", "Preacher Curl", "Hammer Curl"),
        "Triceps" to listOf("Overhead Cable Tricep Extension", "Skull Crusher", "Tricep Pushdown", "Overhead Dumbbell Extension"),
        "Quads" to listOf("Hack Squat", "Back Squat", "Leg Extension", "Leg Press", "Bulgarian Split Squat"),
        "Hamstrings" to listOf("Seated Leg Curl", "Romanian Deadlift", "Lying Leg Curl", "Stiff-Leg Deadlift"),
        "Glutes" to listOf("Hip Thrust", "Bulgarian Split Squat", "Hip Abduction Machine", "Cable Kickback"),
        "Calves" to listOf("Standing Calf Raise", "Seated Calf Raise"),
        "Core" to listOf("Cable Crunch", "Hanging Leg Raise", "Ab Wheel Rollout"),
        "Forearms" to listOf("Wrist Curl", "Reverse Curl")
    ),
    TrainingGoal.STRENGTH to mapOf(
        "Chest" to listOf("Barbell Bench Press", "Close-Grip Bench Press", "Incline Barbell Bench Press", "Weighted Dips"),
        "Back" to listOf("Barbell Row", "Weighted Pull-Up", "Lat Pulldown", "Pendlay Row", "Chest-Supported Row"),
        "Shoulders" to listOf("Overhead Press", "Push Press", "Lateral Raise", "Face Pull"),
        "Biceps" to listOf("Barbell Curl", "Hammer Curl"),
        "Triceps" to listOf("Close-Grip Bench Press", "Skull Crusher", "Tricep Pushdown"),
        "Quads" to listOf("Back Squat", "Front Squat", "Leg Press", "Bulgarian Split Squat"),
        "Hamstrings" to listOf("Deadlift", "Romanian Deadlift", "Lying Leg Curl"),
        "Glutes" to listOf("Hip Thrust", "Bulgarian Split Squat"),
        "Calves" to listOf("Standing Calf Raise", "Seated Calf Raise"),
        "Core" to listOf("Ab Wheel Rollout", "Hanging Leg Raise", "Plank"),
        "Forearms" to listOf("Farmer's Carry", "Wrist Curl")
    ),
    TrainingGoal.GENERAL to mapOf(
        "Chest" to listOf("Dumbbell Bench Press", "Machine Chest Press", "Incline Dumbbell Press", "Push-Up"),
        "Back" to listOf("Lat Pulldown", "Seated Cable Row", "Dumbbell Row", "Inverted Row"),
        "Shoulders" to listOf("Seated Dumbbell Shoulder Press", "Lateral Raise", "Face Pull"),
        "Biceps" to listOf("Dumbbell Curl", "Hammer Curl"),
        "Triceps" to listOf("Tricep Pushdown", "Overhead Dumbbell Extension"),
        "Quads" to listOf("Goblet Squat", "Leg Press", "Reverse Lunge", "Step-Up"),
        "Hamstrings" to listOf("Romanian Deadlift", "Seated Leg Curl"),
        "Glutes" to listOf("Hip Thrust", "Glute Bridge", "Reverse Lunge"),
        "Calves" to listOf("Standing Calf Raise", "Single-Leg Calf Raise"),
        "Core" to listOf("Plank", "Dead Bug", "Pallof Press", "Side Plank"),
        "Forearms" to listOf("Farmer's Carry", "Dead Hang")
    )
)

/**
 * Harder versions for lifters past their first months: free-weight and bodyweight compounds that
 * need more skill and stability, which a new lifter does better to learn on a machine or a
 * dumbbell first. Intermediate lifters get the first group, experienced ones both.
 */
private val INTERMEDIATE_STEP_UP = mapOf(
    "Goblet Squat" to "Back Squat",
    "Dead Bug" to "Hanging Knee Raise",
    "Machine Chest Press" to "Incline Dumbbell Press",
    "Dumbbell Curl" to "Incline Dumbbell Curl"
)
private val ADVANCED_STEP_UP = mapOf(
    "Lat Pulldown" to "Pull-Up",
    "Dumbbell Bench Press" to "Barbell Bench Press",
    "Dumbbell Row" to "Barbell Row",
    "Reverse Lunge" to "Bulgarian Split Squat",
    "Plank" to "Ab Wheel Rollout",
    "Hanging Knee Raise" to "Hanging Leg Raise",
    "Leg Press" to "Front Squat",
    "Seated Dumbbell Shoulder Press" to "Overhead Press",
    "Incline Dumbbell Press" to "Incline Barbell Bench Press",
    "Chest-Supported Row" to "Pendlay Row",
    "Hack Squat" to "Back Squat"
)

private fun steppedUp(name: String, level: Experience): String = when (level) {
    Experience.BEGINNER -> name
    Experience.INTERMEDIATE -> INTERMEDIATE_STEP_UP[name] ?: name
    Experience.ADVANCED -> INTERMEDIATE_STEP_UP[name]?.let { ADVANCED_STEP_UP[it] ?: it } ?: ADVANCED_STEP_UP[name] ?: name
}

/** The order muscles are trained in a day: big compound work first, while fresh; arms, calves and core last. */
private val TRAINING_ORDER = listOf("Quads", "Chest", "Back", "Hamstrings", "Glutes", "Shoulders", "Triceps", "Biceps", "Calves", "Core", "Forearms")

/** One exercise of a planned day, with the sets and reps it's planned for. */
data class PlannedExercise(val name: String, val muscle: String, val target: ExerciseTarget)

/** One day of a plan. */
data class PlannedDay(val title: String, val muscles: Set<String>, val exercises: List<PlannedExercise>)

/** Roughly how long [exercises] take: each set plus its rest, and a minute to set up each exercise. */
internal fun minutesFor(exercises: List<PlannedExercise>, profile: UserProfile): Int =
    exercises.sumOf { e -> 60 + e.target.sets * ((prescriptionFor(profile, e.name)?.restSeconds ?: 90) + 40) } / 60

/**
 * The whole week for [profile]: days, their exercises, and sets and reps for each. Each muscle's
 * weekly sets ([weeklySets]) are shared over the days that train it, no more than about ten in one
 * session (more add little, Pelland et al.), as whole exercises of the planned sets each, choosing
 * further down the muscle's list on each later day so repeated days differ. A day longer than the
 * session time drops its smallest-muscle work first.
 */
fun planWeek(profile: UserProfile): List<PlannedDay> {
    if (!profile.answered) return emptyList()
    val level = profile.experience!!
    val goal = profile.goal!!
    val days = profile.daysPerWeek.takeIf { it > 0 } ?: 3
    val trained = trainedMuscles(profile).ifEmpty { return emptyList() }
    val focus = focusOf(profile)
    val layout = layoutFor(days, level, goal, focus)
    val dayMuscles = weekMuscles(trained, days, layout)
    val titles = uniqueDayTitles(dayMuscles.mapIndexed { i, m -> dayTitleFor((m - "Core").ifEmpty { m }, i) })
    val minutes = profile.sessionMinutes.takeIf { it > 0 } ?: 60
    val picks = PICKS.getValue(goal)
    val timesSeen = HashMap<String, Int>()
    val perSessionCap = if (goal == TrainingGoal.STRENGTH) 6 else 10

    return dayMuscles.mapIndexed { d, muscles ->
        val chosen = ArrayList<PlannedExercise>()
        // Priority muscles first, while fresh; then the usual order.
        val order = TRAINING_ORDER.filter { it in focus.muscles } + TRAINING_ORDER.filter { it !in focus.muscles }
        for (muscle in order.filter { it in muscles }) {
            val frequency = dayMuscles.count { muscle in it }.coerceAtLeast(1)
            val sets = (weeklySets(profile, muscle).toFloat() / frequency).let { kotlin.math.round(it).toInt() }.coerceIn(2, perSessionCap)
            val seen = timesSeen.getOrDefault(muscle, 0)
            timesSeen[muscle] = seen + 1
            val list = picks[muscle].orEmpty().map { steppedUp(it, level) }.distinct()
            if (list.isEmpty()) continue
            // A compound lift first (rotating through them day to day), then, for more sets, an
            // isolation one: bench then flyes, not two kinds of squat. For back the two kinds are
            // the two directions of pulling: a pull-up or pulldown, and a row.
            fun kind(name: String) = if (muscle == "Back") !name.contains("Row") else isCompound(name)
            val compounds = list.filter(::kind)
            val isolations = list.filterNot(::kind)
            val first = compounds.indices.map { compounds[(seen + it) % compounds.size] }
            val second = isolations.indices.map { isolations[(seen + it) % isolations.size] }
            // Back days take turns leading with a pull-up and with a row, so a short session that
            // only fits one still trains both directions over the week.
            val order = (if (muscle == "Back" && seen % 2 == 1) second + first else first + second).ifEmpty { list }
            var left = sets
            var firstWasCompound: Boolean? = null
            for (candidate in order) {
                if (left <= 0) break
                val fitted = personalize(listOf(candidate to muscle), profile).firstOrNull() ?: continue
                if (chosen.any { exerciseKey(it.name) == exerciseKey(fitted.first) }) continue
                // The second exercise of a muscle is the other kind when there is one.
                if (firstWasCompound != null && kind(fitted.first) == firstWasCompound &&
                    order.any { c -> kind(c) != firstWasCompound && personalize(listOf(c to muscle), profile).isNotEmpty() }
                ) continue
                val p = prescriptionFor(profile, fitted.first) ?: continue
                // One more set on this exercise (up to four) rather than a second exercise for a single set.
                val n = if (left - p.sets == 1) (p.sets + 1).coerceAtMost(4).coerceAtLeast(p.sets) else minOf(p.sets, left).coerceAtLeast(2)
                // Counted for the muscle being planned: a deadlift for hamstrings is listed under back.
                chosen += PlannedExercise(fitted.first, muscle, ExerciseTarget(n, p.repMin, p.repMax))
                if (firstWasCompound == null) firstWasCompound = kind(fitted.first)
                left -= n
            }
        }
        // Too long, or too many exercises for the session (about 4 at 30 min to 9 at 90): cut in
        // order of what matters least: a small muscle's second exercise, sets above three, a big
        // muscle's second exercise, small-muscle work, sets above two, then the last exercise.
        // Fewer exercises done properly beat many done once or twice.
        val maxExercises = ((minutes + 15) / 10).coerceIn(4, 9)
        // Extra exercises of a muscle, the muscle with the most first (so a third chest exercise
        // goes before the only row).
        // Priority muscles are cut last, so their extra work survives a short session.
        fun seconds() = chosen.groupBy { it.muscle }.values.filter { it.size > 1 }
            .sortedWith(compareBy<List<PlannedExercise>>({ it.first().muscle !in focus.muscles }, { it.size }, { g -> g.sumOf { e -> e.target.sets } })).map { it.last() }
        fun shave(floor: Int): Boolean {
            val over = chosen.filter { it.target.sets > floor }
            val biggest = (over.filter { it.muscle !in focus.muscles }.ifEmpty { over }).maxByOrNull { it.target.sets } ?: return false
            chosen[chosen.indexOf(biggest)] = biggest.copy(target = biggest.target.copy(sets = biggest.target.sets - 1))
            return true
        }
        while (chosen.size > 3 && (chosen.size > maxExercises || minutesFor(chosen, profile) > minutes)) {
            val tooMany = chosen.size > maxExercises
            seconds().lastOrNull { it.muscle in SMALL_MUSCLES && it.muscle !in focus.muscles }?.let { chosen.remove(it); continue }
            if (!tooMany && shave(3)) continue
            seconds().lastOrNull { it.muscle !in focus.muscles }?.let { chosen.remove(it); continue }
            chosen.lastOrNull { it.muscle in SMALL_MUSCLES && it.muscle !in focus.muscles }?.let { chosen.remove(it); continue }
            seconds().lastOrNull()?.let { chosen.remove(it); continue }
            // A lift down to two sets does little: drop the last exercise first while there are plenty.
            if (chosen.size > 4 || tooMany || !shave(2)) {
                chosen.remove(chosen.lastOrNull { it.muscle !in focus.muscles } ?: chosen.last())
            }
        }
        PlannedDay(titles[d], muscles, chosen)
    }
}

/** Why this plan: the layout, how often and how much each muscle is trained, and how long a session is. */
fun planReason(profile: UserProfile): String {
    val week = planWeek(profile)
    if (week.isEmpty()) return ""
    val level = profile.experience!!
    val goal = profile.goal!!
    val focus = focusOf(profile)
    val layout = layoutFor(week.size, level, goal, focus)
    val perMuscle = week.flatMap { it.exercises }.filter { it.muscle in MAJOR_MUSCLES }
        .groupBy { it.muscle }.mapValues { (_, e) -> e.sumOf { it.target.sets } }
    val typicalSets = perMuscle.values.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
    val times = week.flatMap { it.muscles }.groupingBy { it }.eachCount().filterKeys { it in MAJOR_MUSCLES }.values.minOrNull() ?: 1
    val minutes = week.maxOf { minutesFor(it.exercises, profile) }
    val first = tr("{0} days of {1}: each muscle {2} times a week, about {3} hard sets weekly.", week.size, layout.label, times, typicalSets)
    val second = when (goal) {
        TrainingGoal.MUSCLE -> when (level) {
            Experience.BEGINNER -> tr("Enough to grow fast while you learn the lifts.")
            Experience.INTERMEDIATE -> tr("A middle dose: growth keeps rising with sets, with smaller returns.")
            Experience.ADVANCED -> tr("Experienced lifters need more sets to keep growing.")
        }
        TrainingGoal.STRENGTH -> tr("Heavy main lifts first and often; strength needs fewer sets than size.")
        TrainingGoal.GENERAL -> tr("A moderate dose of simple compound moves gives most of the health benefit.")
    }
    val third = when (focus) {
        Focus.BALANCED -> ""
        Focus.ARMS -> tr("Arms get more sets and come first, trained fresh rather than after presses and rows.") + " "
        else -> tr("More sets for {0}, trained first in the session, while you're fresh.", focus.label.lowercase()) + " "
    }
    return "$first $second $third" + tr("About {0} min a session.", minutes)
}

/** The plan as a program, its days named and listing their muscles; null without answers. */
fun generatedPlan(profile: UserProfile): SplitProgramInfo? {
    val week = planWeek(profile).ifEmpty { return null }
    return SplitProgramInfo(
        id = RECOMMENDED_PLAN_ID,
        name = "Your plan",
        tagline = "",
        weeklyGoal = week.size,
        splits = week.mapIndexed { i, d -> WorkoutSplit(d.title, BUILDER_MUSCLES.filter { it in d.muscles }.joinToString(", "), "%02d".format(i + 1)) }
    )
}

/** A plan day's exercises (name to muscle). */
fun planDayExercises(profile: UserProfile, index: Int): List<Pair<String, String>> =
    planWeek(profile).getOrNull(index)?.exercises.orEmpty().map { it.name to it.muscle }
