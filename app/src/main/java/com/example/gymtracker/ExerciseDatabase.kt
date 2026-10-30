package com.example.gymtracker

import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class ExerciseInfo(
    val name: String,
    val muscle: String,
    val equipment: String,
    val aliases: List<String> = emptyList(),
    val type: ExerciseType = ExerciseType.WEIGHT_REPS,
    /** The everyday exercises; search lists them before the wider library's equally good matches. */
    val common: Boolean = true
)

/** Built-in exercise library used by the "Add exercise" search. */
object ExerciseDatabase {

    val muscleGroups = listOf(
        "Chest", "Back", "Shoulders", "Biceps", "Triceps",
        "Quads", "Hamstrings", "Glutes", "Calves", "Core", "Forearms", "Cardio"
    )

    private fun ex(name: String, muscle: String, equipment: String, vararg aliases: String) =
        ExerciseInfo(name, muscle, equipment, aliases.toList())

    /** Same as [ex] for exercises that aren't logged as weight × reps. */
    private fun ex(name: String, muscle: String, equipment: String, type: ExerciseType, vararg aliases: String) =
        ExerciseInfo(name, muscle, equipment, aliases.toList(), type)

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
        ex("Push-Up", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "pushup", "press up"),
        ex("Weighted Dips", "Chest", "Bodyweight", ExerciseType.WEIGHTED_BODYWEIGHT, "chest dips"),
        ex("Dips", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "parallel bar dips"),
        ex("Assisted Dip", "Chest", "Machine", ExerciseType.ASSISTED, "assisted dips", "dip machine"),

        // Back
        ex("Pull-Up", "Back", "Bodyweight", ExerciseType.BODYWEIGHT, "pullup"),
        ex("Assisted Pull-Up", "Back", "Machine", ExerciseType.ASSISTED, "assisted pullup", "band pull-up"),
        ex("Weighted Pull-Up", "Back", "Bodyweight", ExerciseType.WEIGHTED_BODYWEIGHT),
        ex("Chin-Up", "Back", "Bodyweight", ExerciseType.BODYWEIGHT, "chinup"),
        ex("Inverted Row", "Back", "Bodyweight", ExerciseType.BODYWEIGHT, "australian pull-up", "body row"),
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
        ex("Back Extension", "Back", "Bodyweight", ExerciseType.BODYWEIGHT, "hyperextension"),
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
        ex("Tricep Dips", "Triceps", "Bodyweight", ExerciseType.BODYWEIGHT, "bench dips"),
        ex("Machine Tricep Extension", "Triceps", "Machine"),
        ex("Single-Arm Cable Extension", "Triceps", "Cable"),
        ex("Dumbbell Kickback", "Triceps", "Dumbbell"),

        // Quads
        ex("Back Squat", "Quads", "Barbell", "squat", "barbell squat"),
        ex("Front Squat", "Quads", "Barbell"),
        ex("Hack Squat", "Quads", "Machine"),
        ex("Pendulum Squat", "Quads", "Machine"),
        ex("Smith Machine Squat", "Quads", "Smith machine"),
        ex("Goblet Squat", "Quads", "Dumbbell", "kettlebell goblet squat"),
        ex("Leg Press", "Quads", "Machine"),
        ex("Leg Extension", "Quads", "Machine", "quad extension"),
        ex("Bulgarian Split Squat", "Quads", "Dumbbell", "bss", "split squat"),
        ex("Walking Lunge", "Quads", "Dumbbell", "lunges"),
        ex("Reverse Lunge", "Quads", "Dumbbell"),
        ex("Step-Up", "Quads", "Dumbbell"),
        ex("Sissy Squat", "Quads", "Bodyweight", ExerciseType.BODYWEIGHT),
        ex("Belt Squat", "Quads", "Machine"),

        // Hamstrings
        ex("Romanian Deadlift", "Hamstrings", "Barbell", "rdl"),
        ex("Dumbbell Romanian Deadlift", "Hamstrings", "Dumbbell", "db rdl"),
        ex("Stiff-Leg Deadlift", "Hamstrings", "Barbell", "sldl"),
        ex("Seated Leg Curl", "Hamstrings", "Machine", "hamstring curl"),
        ex("Lying Leg Curl", "Hamstrings", "Machine"),
        ex("Standing Leg Curl", "Hamstrings", "Machine"),
        ex("Nordic Curl", "Hamstrings", "Bodyweight", ExerciseType.BODYWEIGHT, "nordic ham curl"),
        ex("Good Morning", "Hamstrings", "Barbell"),
        ex("Glute-Ham Raise", "Hamstrings", "Bodyweight", ExerciseType.BODYWEIGHT, "ghr"),

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
        ex("Hanging Leg Raise", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "leg raise"),
        ex("Hanging Knee Raise", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs"),
        ex("Ab Wheel Rollout", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs"),
        ex("Plank", "Core", "Bodyweight", ExerciseType.DURATION, "abs", "hold"),
        ex("Side Plank", "Core", "Bodyweight", ExerciseType.DURATION, "obliques", "hold"),
        ex("Hollow Body Hold", "Core", "Bodyweight", ExerciseType.DURATION, "abs", "hold"),
        ex("Decline Sit-Up", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "situp"),
        ex("Machine Crunch", "Core", "Machine", "abs"),
        ex("Pallof Press", "Core", "Cable", "anti rotation"),
        ex("Russian Twist", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "obliques"),
        ex("Woodchopper", "Core", "Cable", "obliques"),

        // Forearms
        ex("Wrist Curl", "Forearms", "Dumbbell"),
        ex("Reverse Wrist Curl", "Forearms", "Dumbbell"),
        ex("Reverse Curl", "Forearms", "EZ bar"),
        ex("Farmer's Carry", "Forearms", "Dumbbell", "farmers walk", "grip"),
        ex("Dead Hang", "Forearms", "Bodyweight", ExerciseType.DURATION, "grip", "hold"),

        // Cardio
        ex("Running", "Cardio", "None", ExerciseType.DISTANCE_TIME, "run", "jog"),
        ex("Treadmill", "Cardio", "Machine", ExerciseType.DISTANCE_TIME, "run", "incline walk"),
        ex("Rowing Machine", "Cardio", "Machine", ExerciseType.DISTANCE_TIME, "rower", "erg", "row"),
        ex("Cycling", "Cardio", "None", ExerciseType.DISTANCE_TIME, "bike", "ride"),
        ex("Stationary Bike", "Cardio", "Machine", ExerciseType.DISTANCE_TIME, "bike", "spin"),
        ex("Elliptical", "Cardio", "Machine", ExerciseType.DISTANCE_TIME, "cross trainer"),
        ex("Walking", "Cardio", "None", ExerciseType.DISTANCE_TIME, "walk"),
        ex("Stair Climber", "Cardio", "Machine", ExerciseType.DURATION, "stairmaster", "stepper"),
        ex("Jump Rope", "Cardio", "None", ExerciseType.DURATION, "skipping")
    ) + more().map { it.copy(common = false) }

    /**
     * The wider library (2026-10-30): bodyweight and calisthenics, kettlebells, Olympic lifts
     * and more gym variations, picked from free-exercise-db. Search ranks them after the
     * everyday exercises above. Its own function to stay under the JVM's method size limit.
     */
    private fun more(): List<ExerciseInfo> = listOf(
        // Chest
        ex("Incline Push-Up", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "easy push-up", "bench push-up"),
        ex("Decline Push-Up", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "feet elevated push-up"),
        ex("Wide Push-Up", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "wide grip push-up"),
        ex("Knee Push-Up", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "kneeling push-up", "easy push-up"),
        ex("Archer Push-Up", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "calisthenics"),
        ex("Clap Push-Up", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "plyo push-up", "explosive push-up"),
        ex("Single-Arm Push-Up", "Chest", "Bodyweight", ExerciseType.BODYWEIGHT, "one arm push-up", "calisthenics"),
        ex("Ring Push-Up", "Chest", "Rings", ExerciseType.BODYWEIGHT, "trx push-up", "suspended push-up"),
        ex("Dumbbell Pullover", "Chest", "Dumbbell", "pullover"),
        ex("Dumbbell Floor Press", "Chest", "Dumbbell", "floor press"),
        ex("Neutral-Grip Dumbbell Press", "Chest", "Dumbbell", "hammer press", "db press"),
        ex("Cable Chest Press", "Chest", "Cable", "standing chest press"),
        ex("Incline Cable Fly", "Chest", "Cable", "incline fly"),
        ex("Wide-Grip Bench Press", "Chest", "Barbell", "wide bench"),
        ex("Barbell Floor Press", "Chest", "Barbell", "floor press"),
        ex("Kettlebell Floor Press", "Chest", "Kettlebell", "kb floor press"),
        ex("Svend Press", "Chest", "Plate", "plate press"),

        // Back
        ex("Neutral-Grip Pull-Up", "Back", "Bodyweight", ExerciseType.BODYWEIGHT, "parallel grip pull-up", "hammer pull-up"),
        ex("Negative Pull-Up", "Back", "Bodyweight", ExerciseType.BODYWEIGHT, "eccentric pull-up", "slow pull-up"),
        ex("Scapular Pull-Up", "Back", "Bodyweight", ExerciseType.BODYWEIGHT, "scap pull-up", "calisthenics"),
        ex("Muscle-Up", "Back", "Bodyweight", ExerciseType.BODYWEIGHT, "calisthenics", "bar muscle up"),
        ex("Ring Row", "Back", "Rings", ExerciseType.BODYWEIGHT, "trx row", "suspended row"),
        ex("Rope Climb", "Back", "Rope", ExerciseType.BODYWEIGHT, "climb"),
        ex("Superman Hold", "Back", "Bodyweight", ExerciseType.DURATION, "superman", "back raise"),
        ex("Kettlebell Row", "Back", "Kettlebell", "kb row"),
        ex("Renegade Row", "Back", "Dumbbell", "plank row"),
        ex("Bent-Over Dumbbell Row", "Back", "Dumbbell", "two arm dumbbell row"),
        ex("Underhand Barbell Row", "Back", "Barbell", "reverse grip row", "yates row"),
        ex("Single-Arm Cable Row", "Back", "Cable", "one arm cable row"),
        ex("Underhand Lat Pulldown", "Back", "Cable", "reverse grip pulldown"),
        ex("Trap Bar Deadlift", "Back", "Trap bar", "hex bar deadlift"),
        ex("Deficit Deadlift", "Back", "Barbell"),
        ex("Cable Shrug", "Back", "Cable", "traps"),
        ex("Kettlebell High Pull", "Back", "Kettlebell", "sumo high pull"),

        // Shoulders
        ex("Pike Push-Up", "Shoulders", "Bodyweight", ExerciseType.BODYWEIGHT, "calisthenics", "shoulder push-up"),
        ex("Handstand Push-Up", "Shoulders", "Bodyweight", ExerciseType.BODYWEIGHT, "hspu", "calisthenics"),
        ex("Handstand Hold", "Shoulders", "Bodyweight", ExerciseType.DURATION, "wall handstand", "calisthenics"),
        ex("Push Press", "Shoulders", "Barbell"),
        ex("Standing Dumbbell Press", "Shoulders", "Dumbbell", "standing db press"),
        ex("Seated Barbell Press", "Shoulders", "Barbell", "seated military press"),
        ex("Kettlebell Press", "Shoulders", "Kettlebell", "kb press", "kettlebell overhead press"),
        ex("Landmine Press", "Shoulders", "Barbell", "landmine"),
        ex("Cable Front Raise", "Shoulders", "Cable"),
        ex("Plate Front Raise", "Shoulders", "Plate", "plate raise"),
        ex("Cable Rear Delt Fly", "Shoulders", "Cable", "reverse cable fly", "rear delt"),
        ex("Dumbbell Upright Row", "Shoulders", "Dumbbell"),
        ex("Cable Upright Row", "Shoulders", "Cable"),
        ex("Cuban Press", "Shoulders", "Dumbbell", "rotator cuff"),
        ex("Cable External Rotation", "Shoulders", "Cable", "rotator cuff", "external rotation"),
        ex("Dumbbell External Rotation", "Shoulders", "Dumbbell", "rotator cuff"),
        ex("Kettlebell Halo", "Shoulders", "Kettlebell", "halo"),
        ex("Turkish Get-Up", "Shoulders", "Kettlebell", "tgu", "get up"),
        ex("Kettlebell Snatch", "Shoulders", "Kettlebell", "kb snatch"),
        ex("Clean and Press", "Shoulders", "Barbell"),

        // Biceps
        ex("Alternating Dumbbell Curl", "Biceps", "Dumbbell", "alternate curl"),
        ex("Seated Dumbbell Curl", "Biceps", "Dumbbell"),
        ex("Incline Hammer Curl", "Biceps", "Dumbbell"),
        ex("Cross-Body Hammer Curl", "Biceps", "Dumbbell", "pinwheel curl"),
        ex("Zottman Curl", "Biceps", "Dumbbell"),
        ex("Drag Curl", "Biceps", "Barbell"),
        ex("Overhead Cable Curl", "Biceps", "Cable", "double biceps cable curl"),
        ex("Single-Arm Cable Curl", "Biceps", "Cable", "one arm cable curl"),
        ex("Cable Preacher Curl", "Biceps", "Cable"),
        ex("Machine Bicep Curl", "Biceps", "Machine", "bicep machine"),

        // Triceps
        ex("Diamond Push-Up", "Triceps", "Bodyweight", ExerciseType.BODYWEIGHT, "close grip push-up", "triangle push-up"),
        ex("Bodyweight Tricep Extension", "Triceps", "Bodyweight", ExerciseType.BODYWEIGHT, "bodyweight skull crusher", "calisthenics"),
        ex("Ring Dips", "Triceps", "Rings", ExerciseType.BODYWEIGHT, "calisthenics"),
        ex("V-Bar Pushdown", "Triceps", "Cable", "v bar pushdown"),
        ex("Reverse-Grip Pushdown", "Triceps", "Cable", "underhand pushdown"),
        ex("Dumbbell Skull Crusher", "Triceps", "Dumbbell", "lying dumbbell extension"),
        ex("Close-Grip Dumbbell Press", "Triceps", "Dumbbell", "crush press"),
        ex("Tate Press", "Triceps", "Dumbbell"),
        ex("Single-Arm Overhead Extension", "Triceps", "Dumbbell", "one arm overhead extension"),

        // Quads
        ex("Bodyweight Squat", "Quads", "Bodyweight", ExerciseType.BODYWEIGHT, "air squat", "squat"),
        ex("Jump Squat", "Quads", "Bodyweight", ExerciseType.BODYWEIGHT, "squat jump", "plyometric"),
        ex("Pistol Squat", "Quads", "Bodyweight", ExerciseType.BODYWEIGHT, "single leg squat", "calisthenics"),
        ex("Shrimp Squat", "Quads", "Bodyweight", ExerciseType.BODYWEIGHT, "calisthenics", "single leg squat"),
        ex("Cossack Squat", "Quads", "Bodyweight", ExerciseType.BODYWEIGHT, "side squat"),
        ex("Wall Sit", "Quads", "Bodyweight", ExerciseType.DURATION, "wall squat", "hold"),
        ex("Jumping Lunge", "Quads", "Bodyweight", ExerciseType.BODYWEIGHT, "split jump", "plyometric"),
        ex("Box Jump", "Quads", "Bodyweight", ExerciseType.BODYWEIGHT, "plyometric", "jump"),
        ex("Box Squat", "Quads", "Barbell"),
        ex("Overhead Squat", "Quads", "Barbell", "ohs"),
        ex("Dumbbell Squat", "Quads", "Dumbbell", "db squat"),
        ex("Kettlebell Front Squat", "Quads", "Kettlebell", "double kettlebell squat"),
        ex("Barbell Lunge", "Quads", "Barbell"),
        ex("Dumbbell Lunge", "Quads", "Dumbbell", "forward lunge", "lunges"),
        ex("Single-Leg Leg Extension", "Quads", "Machine", "one leg extension"),
        ex("Kettlebell Thruster", "Quads", "Kettlebell", "thruster"),
        ex("Sled Push", "Quads", "Sled", ExerciseType.DISTANCE_TIME, "prowler"),
        ex("Power Clean", "Quads", "Barbell", "clean", "olympic"),
        ex("Hang Clean", "Quads", "Barbell", "olympic"),
        ex("Clean and Jerk", "Quads", "Barbell", "olympic"),
        ex("Snatch", "Quads", "Barbell", "olympic"),

        // Hamstrings
        ex("Single-Leg Romanian Deadlift", "Hamstrings", "Dumbbell", "single leg rdl", "sl rdl"),
        ex("Stability Ball Leg Curl", "Hamstrings", "Ball", ExerciseType.BODYWEIGHT, "ball leg curl", "swiss ball curl"),
        ex("Sliding Leg Curl", "Hamstrings", "Bodyweight", ExerciseType.BODYWEIGHT, "slider curl", "towel leg curl"),
        ex("Kettlebell Clean", "Hamstrings", "Kettlebell", "kb clean"),

        // Glutes
        ex("Kettlebell Swing", "Glutes", "Kettlebell", "kb swing", "russian swing"),
        ex("Bodyweight Glute Bridge", "Glutes", "Bodyweight", ExerciseType.BODYWEIGHT, "glute bridge", "hip bridge"),
        ex("Single-Leg Glute Bridge", "Glutes", "Bodyweight", ExerciseType.BODYWEIGHT, "single leg bridge"),
        ex("Single-Leg Hip Thrust", "Glutes", "Bodyweight", ExerciseType.BODYWEIGHT, "single leg hip thrust"),
        ex("Donkey Kick", "Glutes", "Bodyweight", ExerciseType.BODYWEIGHT, "glute kickback", "quadruped kickback"),
        ex("Fire Hydrant", "Glutes", "Bodyweight", ExerciseType.BODYWEIGHT, "hip abduction"),
        ex("Curtsy Lunge", "Glutes", "Dumbbell"),
        ex("Sumo Squat", "Glutes", "Dumbbell", "plie squat", "sumo dumbbell squat"),
        ex("Reverse Hyperextension", "Glutes", "Machine", "reverse hyper"),

        // Calves
        ex("Bodyweight Calf Raise", "Calves", "Bodyweight", ExerciseType.BODYWEIGHT, "calf raise"),
        ex("Dumbbell Calf Raise", "Calves", "Dumbbell"),
        ex("Barbell Calf Raise", "Calves", "Barbell"),
        ex("Donkey Calf Raise", "Calves", "Machine"),
        ex("Seated Dumbbell Calf Raise", "Calves", "Dumbbell"),
        ex("Pogo Hops", "Calves", "Bodyweight", ExerciseType.BODYWEIGHT, "pogo jumps", "plyometric"),

        // Core
        ex("Crunch", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "crunches"),
        ex("Sit-Up", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "situp"),
        ex("Bicycle Crunch", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "obliques", "bicycle"),
        ex("Reverse Crunch", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs"),
        ex("Lying Leg Raise", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "leg raise"),
        ex("Toes-to-Bar", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "ttb", "calisthenics"),
        ex("L-Sit", "Core", "Bodyweight", ExerciseType.DURATION, "abs", "calisthenics", "hold"),
        ex("Dragon Flag", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "calisthenics"),
        ex("V-Up", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "jackknife"),
        ex("Dead Bug", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs"),
        ex("Bird Dog", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs"),
        ex("Mountain Climbers", "Core", "Bodyweight", ExerciseType.DURATION, "abs", "cardio"),
        ex("Flutter Kicks", "Core", "Bodyweight", ExerciseType.DURATION, "abs"),
        ex("Heel Touches", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "abs", "obliques"),
        ex("Oblique Crunch", "Core", "Bodyweight", ExerciseType.BODYWEIGHT, "obliques", "side crunch"),
        ex("Stability Ball Crunch", "Core", "Ball", ExerciseType.BODYWEIGHT, "ball crunch", "swiss ball crunch"),
        ex("Stability Ball Knee Tuck", "Core", "Ball", ExerciseType.BODYWEIGHT, "ball pull-in", "swiss ball tuck"),
        ex("Bear Crawl", "Core", "Bodyweight", ExerciseType.DURATION, "crawl", "cardio"),
        ex("Cable Lift", "Core", "Cable", "low to high woodchop", "obliques"),
        ex("Dumbbell Side Bend", "Core", "Dumbbell", "obliques"),
        ex("Landmine Rotation", "Core", "Barbell", "landmine 180", "obliques"),
        ex("Medicine Ball Slam", "Core", "Medicine ball", "ball slam", "slam ball"),
        ex("Kettlebell Windmill", "Core", "Kettlebell", "windmill", "obliques"),

        // Forearms
        ex("Wrist Roller", "Forearms", "Other", "grip"),
        ex("Plate Pinch", "Forearms", "Plate", ExerciseType.DURATION, "grip", "pinch grip"),
        ex("Behind-the-Back Wrist Curl", "Forearms", "Barbell", "grip"),
        ex("Suitcase Carry", "Forearms", "Dumbbell", ExerciseType.DURATION, "one arm carry", "grip"),

        // Cardio
        ex("Burpee", "Cardio", "Bodyweight", ExerciseType.BODYWEIGHT, "burpees"),
        ex("Jumping Jacks", "Cardio", "Bodyweight", ExerciseType.DURATION, "star jumps"),
        ex("High Knees", "Cardio", "Bodyweight", ExerciseType.DURATION, "running in place"),
        ex("Battle Ropes", "Cardio", "Battle ropes", ExerciseType.DURATION, "ropes"),
        ex("Sprints", "Cardio", "None", ExerciseType.DISTANCE_TIME, "sprint", "intervals"),
        ex("Hiking", "Cardio", "None", ExerciseType.DISTANCE_TIME, "hike", "trail walk"),
        ex("Swimming", "Cardio", "None", ExerciseType.DISTANCE_TIME, "swim", "laps"),
        ex("Recumbent Bike", "Cardio", "Machine", ExerciseType.DISTANCE_TIME, "bike"),
        ex("Ski Erg", "Cardio", "Machine", ExerciseType.DISTANCE_TIME, "skierg", "ski machine"),
        ex("Assault Bike", "Cardio", "Machine", ExerciseType.DISTANCE_TIME, "air bike", "fan bike", "echo bike")
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
        }.sortedWith(compareBy({ it.first }, { !it.second.common }, { it.second.name.length })).map { it.second }
    }

    fun find(name: String): ExerciseInfo? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
