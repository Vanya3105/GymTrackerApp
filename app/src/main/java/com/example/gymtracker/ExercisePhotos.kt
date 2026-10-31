package com.example.gymtracker

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Start and end photos for the built-in exercises, from free-exercise-db on GitHub
 * (github.com/yuhonas/free-exercise-db). Downloaded the first time they're shown and kept on the
 * phone, so they work offline after that.
 */
object ExercisePhotos {
    /**
     * For the owner's own use for now: the repo is public domain, but where its photos come from
     * isn't documented. Turn this off (or replace the photos) before a public release.
     */
    const val ENABLED = true

    private const val BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

    /** Library name -> free-exercise-db id. Missing = no photo close enough to be helpful. */
    private val ids: Map<String, String> = mapOf(
        "Barbell Bench Press" to "Barbell_Bench_Press_-_Medium_Grip",
        "Incline Barbell Bench Press" to "Barbell_Incline_Bench_Press_-_Medium_Grip",
        "Decline Barbell Bench Press" to "Decline_Barbell_Bench_Press",
        "Dumbbell Bench Press" to "Dumbbell_Bench_Press",
        "Incline Dumbbell Press" to "Incline_Dumbbell_Press",
        "Decline Dumbbell Press" to "Decline_Dumbbell_Bench_Press",
        "Smith Machine Bench Press" to "Smith_Machine_Bench_Press",
        "Smith Machine Incline Press" to "Smith_Machine_Incline_Bench_Press",
        "Machine Chest Press" to "Leverage_Chest_Press",
        "Incline Machine Press" to "Leverage_Incline_Chest_Press",
        "Pec Deck" to "Butterfly",
        "Cable Fly" to "Cable_Crossover",
        "Low-to-High Cable Fly" to "Low_Cable_Crossover",
        "High-to-Low Cable Fly" to "Cable_Crossover",
        "Dumbbell Fly" to "Dumbbell_Flyes",
        "Incline Dumbbell Fly" to "Incline_Dumbbell_Flyes",
        "Push-Up" to "Pushups",
        "Weighted Dips" to "Dips_-_Chest_Version",
        "Dips" to "Dips_-_Chest_Version",
        "Assisted Dip" to "Dip_Machine",
        "Pull-Up" to "Pullups",
        "Assisted Pull-Up" to "Band_Assisted_Pull-Up",
        "Weighted Pull-Up" to "Weighted_Pull_Ups",
        "Chin-Up" to "Chin-Up",
        "Inverted Row" to "Inverted_Row",
        "Lat Pulldown" to "Wide-Grip_Lat_Pulldown",
        "Close-Grip Lat Pulldown" to "Close-Grip_Front_Lat_Pulldown",
        "Single-Arm Lat Pulldown" to "One_Arm_Lat_Pulldown",
        "Straight-Arm Pulldown" to "Straight-Arm_Pulldown",
        "Barbell Row" to "Bent_Over_Barbell_Row",
        "Pendlay Row" to "Bent_Over_Barbell_Row",
        "T-Bar Row" to "T-Bar_Row_with_Handle",
        "Dumbbell Row" to "One-Arm_Dumbbell_Row",
        "Chest-Supported Row" to "Lying_T-Bar_Row",
        "Chest-Supported Dumbbell Row" to "Dumbbell_Incline_Row",
        "Seated Cable Row" to "Seated_Cable_Rows",
        "Machine Row" to "Leverage_Iso_Row",
        "Meadows Row" to "One-Arm_Long_Bar_Row",
        "Deadlift" to "Barbell_Deadlift",
        "Rack Pull" to "Rack_Pulls",
        "Back Extension" to "Hyperextensions_Back_Extensions",
        "Dumbbell Shrug" to "Dumbbell_Shrug",
        "Barbell Shrug" to "Barbell_Shrug",
        "Overhead Press" to "Standing_Military_Press",
        "Seated Dumbbell Shoulder Press" to "Seated_Dumbbell_Press",
        "Machine Shoulder Press" to "Leverage_Shoulder_Press",
        "Smith Machine Shoulder Press" to "Smith_Machine_Overhead_Shoulder_Press",
        "Arnold Press" to "Arnold_Dumbbell_Press",
        "Lateral Raise" to "Side_Lateral_Raise",
        "Cable Lateral Raise" to "Cable_Seated_Lateral_Raise",
        "Front Raise" to "Front_Dumbbell_Raise",
        "Rear Delt Fly" to "Seated_Bent-Over_Rear_Delt_Raise",
        "Reverse Pec Deck" to "Reverse_Machine_Flyes",
        "Face Pull" to "Face_Pull",
        "Upright Row" to "Upright_Barbell_Row",
        "Barbell Curl" to "Barbell_Curl",
        "EZ-Bar Curl" to "EZ-Bar_Curl",
        "Dumbbell Curl" to "Dumbbell_Bicep_Curl",
        "Incline Dumbbell Curl" to "Incline_Dumbbell_Curl",
        "Hammer Curl" to "Hammer_Curls",
        "Preacher Curl" to "Preacher_Curl",
        "Machine Preacher Curl" to "Machine_Preacher_Curls",
        "Cable Curl" to "Standing_Biceps_Cable_Curl",
        "Concentration Curl" to "Concentration_Curls",
        "Spider Curl" to "Spider_Curl",
        "Rope Hammer Curl" to "Cable_Hammer_Curls_-_Rope_Attachment",
        "Tricep Pushdown" to "Triceps_Pushdown",
        "Rope Pushdown" to "Triceps_Pushdown_-_Rope_Attachment",
        "Overhead Cable Tricep Extension" to "Cable_Rope_Overhead_Triceps_Extension",
        "Overhead Dumbbell Extension" to "Standing_Dumbbell_Triceps_Extension",
        "Skull Crusher" to "EZ-Bar_Skullcrusher",
        "Close-Grip Bench Press" to "Close-Grip_Barbell_Bench_Press",
        "JM Press" to "JM_Press",
        "Tricep Dips" to "Dips_-_Triceps_Version",
        "Machine Tricep Extension" to "Machine_Triceps_Extension",
        "Single-Arm Cable Extension" to "Cable_One_Arm_Tricep_Extension",
        "Dumbbell Kickback" to "Tricep_Dumbbell_Kickback",
        "Back Squat" to "Barbell_Squat",
        "Front Squat" to "Front_Barbell_Squat",
        "Hack Squat" to "Hack_Squat",
        "Smith Machine Squat" to "Smith_Machine_Squat",
        "Goblet Squat" to "Goblet_Squat",
        "Leg Press" to "Leg_Press",
        "Leg Extension" to "Leg_Extensions",
        "Bulgarian Split Squat" to "Split_Squat_with_Dumbbells",
        "Walking Lunge" to "Bodyweight_Walking_Lunge",
        "Reverse Lunge" to "Dumbbell_Rear_Lunge",
        "Step-Up" to "Dumbbell_Step_Ups",
        "Sissy Squat" to "Weighted_Sissy_Squat",
        "Romanian Deadlift" to "Romanian_Deadlift",
        "Dumbbell Romanian Deadlift" to "Stiff-Legged_Dumbbell_Deadlift",
        "Stiff-Leg Deadlift" to "Stiff-Legged_Barbell_Deadlift",
        "Seated Leg Curl" to "Seated_Leg_Curl",
        "Lying Leg Curl" to "Lying_Leg_Curls",
        "Standing Leg Curl" to "Standing_Leg_Curl",
        "Nordic Curl" to "Natural_Glute_Ham_Raise",
        "Good Morning" to "Good_Morning",
        "Glute-Ham Raise" to "Glute_Ham_Raise",
        "Hip Thrust" to "Barbell_Hip_Thrust",
        "Glute Bridge" to "Barbell_Glute_Bridge",
        "Cable Kickback" to "One-Legged_Cable_Kickback",
        "Hip Abduction Machine" to "Thigh_Abductor",
        "Hip Adduction Machine" to "Thigh_Adductor",
        "Sumo Deadlift" to "Sumo_Deadlift",
        "Cable Pull-Through" to "Pull_Through",
        "Standing Calf Raise" to "Standing_Calf_Raises",
        "Seated Calf Raise" to "Seated_Calf_Raise",
        "Leg Press Calf Raise" to "Calf_Press_On_The_Leg_Press_Machine",
        "Smith Machine Calf Raise" to "Smith_Machine_Calf_Raise",
        "Single-Leg Calf Raise" to "Calf_Raise_On_A_Dumbbell",
        "Cable Crunch" to "Cable_Crunch",
        "Hanging Leg Raise" to "Hanging_Leg_Raise",
        "Hanging Knee Raise" to "Knee_Hip_Raise_On_Parallel_Bars",
        "Ab Wheel Rollout" to "Ab_Roller",
        "Plank" to "Plank",
        "Side Plank" to "Side_Bridge",
        "Decline Sit-Up" to "Decline_Crunch",
        "Machine Crunch" to "Ab_Crunch_Machine",
        "Pallof Press" to "Pallof_Press",
        "Russian Twist" to "Russian_Twist",
        "Woodchopper" to "Standing_Cable_Wood_Chop",
        "Wrist Curl" to "Seated_Dumbbell_Palms-Up_Wrist_Curl",
        "Reverse Wrist Curl" to "Seated_Dumbbell_Palms-Down_Wrist_Curl",
        "Reverse Curl" to "Reverse_Barbell_Curl",
        "Farmer's Carry" to "Farmers_Walk",
        "Running" to "Trail_Running_Walking",
        "Treadmill" to "Jogging_Treadmill",
        "Rowing Machine" to "Rowing_Stationary",
        "Cycling" to "Bicycling",
        "Stationary Bike" to "Bicycling_Stationary",
        "Elliptical" to "Elliptical_Trainer",
        "Walking" to "Walking_Treadmill",
        "Stair Climber" to "Stairmaster",
        "Jump Rope" to "Rope_Jumping",
        "Incline Push-Up" to "Incline_Push-Up",
        "Decline Push-Up" to "Push-Ups_With_Feet_Elevated",
        "Wide Push-Up" to "Push-Up_Wide",
        "Clap Push-Up" to "Plyo_Push-up",
        "Single-Arm Push-Up" to "Single-Arm_Push-Up",
        "Ring Push-Up" to "Suspended_Push-Up",
        "Dumbbell Pullover" to "Straight-Arm_Dumbbell_Pullover",
        "Dumbbell Floor Press" to "Dumbbell_Floor_Press",
        "Neutral-Grip Dumbbell Press" to "Dumbbell_Bench_Press_with_Neutral_Grip",
        "Cable Chest Press" to "Cable_Chest_Press",
        "Incline Cable Fly" to "Incline_Cable_Flye",
        "Wide-Grip Bench Press" to "Wide-Grip_Barbell_Bench_Press",
        "Barbell Floor Press" to "Floor_Press",
        "Kettlebell Floor Press" to "One-Arm_Kettlebell_Floor_Press",
        "Svend Press" to "Svend_Press",
        "Neutral-Grip Pull-Up" to "V-Bar_Pullup",
        "Scapular Pull-Up" to "Scapular_Pull-Up",
        "Muscle-Up" to "Muscle_Up",
        "Ring Row" to "Suspended_Row",
        "Rope Climb" to "Rope_Climb",
        "Kettlebell Row" to "One-Arm_Kettlebell_Row",
        "Renegade Row" to "Alternating_Renegade_Row",
        "Bent-Over Dumbbell Row" to "Bent_Over_Two-Dumbbell_Row",
        "Underhand Barbell Row" to "Reverse_Grip_Bent-Over_Rows",
        "Single-Arm Cable Row" to "Seated_One-arm_Cable_Pulley_Rows",
        "Underhand Lat Pulldown" to "Underhand_Cable_Pulldowns",
        "Trap Bar Deadlift" to "Trap_Bar_Deadlift",
        "Deficit Deadlift" to "Deficit_Deadlift",
        "Cable Shrug" to "Cable_Shrugs",
        "Kettlebell High Pull" to "Kettlebell_Sumo_High_Pull",
        "Handstand Push-Up" to "Handstand_Push-Ups",
        "Push Press" to "Push_Press",
        "Standing Dumbbell Press" to "Standing_Dumbbell_Press",
        "Seated Barbell Press" to "Seated_Barbell_Military_Press",
        "Kettlebell Press" to "Two-Arm_Kettlebell_Military_Press",
        "Landmine Press" to "Single-Arm_Linear_Jammer",
        "Cable Front Raise" to "Front_Cable_Raise",
        "Plate Front Raise" to "Front_Plate_Raise",
        "Cable Rear Delt Fly" to "Cable_Rear_Delt_Fly",
        "Dumbbell Upright Row" to "Standing_Dumbbell_Upright_Row",
        "Cable Upright Row" to "Upright_Cable_Row",
        "Cuban Press" to "Cuban_Press",
        "Cable External Rotation" to "External_Rotation_with_Cable",
        "Dumbbell External Rotation" to "External_Rotation",
        "Kettlebell Halo" to "Kettlebell_Halo",
        "Turkish Get-Up" to "Kettlebell_Turkish_Get-Up_Squat_style",
        "Kettlebell Snatch" to "One-Arm_Kettlebell_Snatch",
        "Clean and Press" to "Clean_and_Press",
        "Alternating Dumbbell Curl" to "Dumbbell_Alternate_Bicep_Curl",
        "Seated Dumbbell Curl" to "Seated_Dumbbell_Curl",
        "Incline Hammer Curl" to "Incline_Hammer_Curls",
        "Cross-Body Hammer Curl" to "Cross_Body_Hammer_Curl",
        "Zottman Curl" to "Zottman_Curl",
        "Drag Curl" to "Drag_Curl",
        "Overhead Cable Curl" to "Overhead_Cable_Curl",
        "Single-Arm Cable Curl" to "Standing_One-Arm_Cable_Curl",
        "Cable Preacher Curl" to "Cable_Preacher_Curl",
        "Machine Bicep Curl" to "Machine_Bicep_Curl",
        "Diamond Push-Up" to "Push-Ups_-_Close_Triceps_Position",
        "Bodyweight Tricep Extension" to "Body_Tricep_Press",
        "Ring Dips" to "Ring_Dips",
        "V-Bar Pushdown" to "Triceps_Pushdown_-_V-Bar_Attachment",
        "Reverse-Grip Pushdown" to "Reverse_Grip_Triceps_Pushdown",
        "Dumbbell Skull Crusher" to "Lying_Dumbbell_Tricep_Extension",
        "Close-Grip Dumbbell Press" to "Close-Grip_Dumbbell_Press",
        "Tate Press" to "Tate_Press",
        "Single-Arm Overhead Extension" to "Standing_One-Arm_Dumbbell_Triceps_Extension",
        "Bodyweight Squat" to "Bodyweight_Squat",
        "Jump Squat" to "Freehand_Jump_Squat",
        "Pistol Squat" to "Kettlebell_Pistol_Squat",
        "Jumping Lunge" to "Split_Jump",
        "Box Jump" to "Front_Box_Jump",
        "Box Squat" to "Box_Squat",
        "Overhead Squat" to "Overhead_Squat",
        "Dumbbell Squat" to "Dumbbell_Squat",
        "Kettlebell Front Squat" to "Front_Squats_With_Two_Kettlebells",
        "Barbell Lunge" to "Barbell_Lunge",
        "Dumbbell Lunge" to "Dumbbell_Lunges",
        "Single-Leg Leg Extension" to "Single-Leg_Leg_Extension",
        "Kettlebell Thruster" to "Kettlebell_Thruster",
        "Sled Push" to "Sled_Push",
        "Power Clean" to "Power_Clean",
        "Hang Clean" to "Hang_Clean",
        "Clean and Jerk" to "Clean_and_Jerk",
        "Snatch" to "Snatch",
        "Single-Leg Romanian Deadlift" to "Kettlebell_One-Legged_Deadlift",
        "Stability Ball Leg Curl" to "Ball_Leg_Curl",
        "Sliding Leg Curl" to "Platform_Hamstring_Slides",
        "Kettlebell Clean" to "One-Arm_Kettlebell_Clean",
        "Kettlebell Swing" to "One-Arm_Kettlebell_Swings",
        "Bodyweight Glute Bridge" to "Butt_Lift_Bridge",
        "Single-Leg Glute Bridge" to "Single_Leg_Glute_Bridge",
        "Donkey Kick" to "Glute_Kickback",
        "Sumo Squat" to "Plie_Dumbbell_Squat",
        "Reverse Hyperextension" to "Reverse_Hyperextension",
        "Dumbbell Calf Raise" to "Standing_Dumbbell_Calf_Raise",
        "Barbell Calf Raise" to "Standing_Barbell_Calf_Raise",
        "Donkey Calf Raise" to "Donkey_Calf_Raises",
        "Seated Dumbbell Calf Raise" to "Dumbbell_Seated_One-Leg_Calf_Raise",
        "Crunch" to "Crunches",
        "Sit-Up" to "Sit-Up",
        "Bicycle Crunch" to "Air_Bike",
        "Reverse Crunch" to "Reverse_Crunch",
        "Lying Leg Raise" to "Flat_Bench_Lying_Leg_Raise",
        "Toes-to-Bar" to "Hanging_Pike",
        "V-Up" to "Jackknife_Sit-Up",
        "Dead Bug" to "Dead_Bug",
        "Mountain Climbers" to "Mountain_Climbers",
        "Flutter Kicks" to "Flutter_Kicks",
        "Heel Touches" to "Alternate_Heel_Touchers",
        "Oblique Crunch" to "Oblique_Crunches_-_On_The_Floor",
        "Stability Ball Crunch" to "Exercise_Ball_Crunch",
        "Stability Ball Knee Tuck" to "Exercise_Ball_Pull-In",
        "Cable Lift" to "Standing_Cable_Lift",
        "Dumbbell Side Bend" to "Dumbbell_Side_Bend",
        "Landmine Rotation" to "Landmine_180s",
        "Medicine Ball Slam" to "Overhead_Slam",
        "Kettlebell Windmill" to "Kettlebell_Windmill",
        "Wrist Roller" to "Wrist_Roller",
        "Plate Pinch" to "Plate_Pinch",
        "Behind-the-Back Wrist Curl" to "Standing_Palms-Up_Barbell_Behind_The_Back_Wrist_Curl",
        "Battle Ropes" to "Battling_Ropes",
        "Recumbent Bike" to "Recumbent_Bike"
    ).mapKeys { exerciseKey(it.key) }

    /** Library names covered, for the test that keeps this in step with [ExerciseDatabase]. */
    internal val mappedNames: Set<String> get() = ids.keys

    /** The photo set for the library exercise [name], or null (custom exercises have none). */
    fun idFor(name: String): String? = if (ENABLED) ids[exerciseKey(name)] else null

    fun has(name: String): Boolean = idFor(name) != null

    private val memory = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val downloads = Semaphore(4)

    fun cached(id: String, frame: Int, maxPx: Int): ImageBitmap? = memory.get("$id/$frame/${bucket(maxPx)}")

    /** Frame 0 = start position, 1 = end. Null when offline and not downloaded yet. */
    suspend fun load(context: Context, id: String, frame: Int, maxPx: Int): ImageBitmap? {
        val key = "$id/$frame/${bucket(maxPx)}"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = fileFor(context, id, frame)
            if (!file.exists()) downloads.withPermit { if (!file.exists()) download(id, frame, file) }
            if (!file.exists()) return@withContext null
            decode(file, bucket(maxPx))?.also { memory.put(key, it) }
        }
    }

    // A few sizes rather than every pixel width, so the cache is shared between screens.
    private fun bucket(px: Int) = when {
        px <= 200 -> 200
        px <= 480 -> 480
        else -> 900
    }

    private fun fileFor(context: Context, id: String, frame: Int) =
        File(File(context.noBackupFilesDir, "exercise_photos"), "${id}_$frame.jpg")

    private fun download(id: String, frame: Int, file: File) {
        runCatching {
            val conn = URL("$BASE$id/$frame.jpg").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            try {
                if (conn.responseCode != 200) return
                file.parentFile?.mkdirs()
                val part = File(file.parentFile, file.name + ".part")
                conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                part.renameTo(file)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun decode(file: File, maxPx: Int): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

/** One photo of [id], faded in once it's loaded; [placeholder] until then (and when offline). */
@Composable
private fun PhotoFrame(id: String, frame: Int, maxPx: Int, modifier: Modifier, placeholder: @Composable () -> Unit) {
    val context = LocalContext.current
    var image by remember(id, frame) { mutableStateOf(ExercisePhotos.cached(id, frame, maxPx)) }
    val alpha = remember(id, frame) { Animatable(if (image != null) 1f else 0f) }
    LaunchedEffect(id, frame) {
        if (image == null) {
            image = ExercisePhotos.load(context, id, frame, maxPx)
            if (image != null) alpha.animateTo(1f, tween(220))
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image == null || alpha.value < 1f) placeholder()
        image?.let {
            Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha.value })
        }
    }
}

/**
 * A small square photo of [name] for lists, or, when there's none, a plain square with a dumbbell
 * (only where [showEmpty]; elsewhere nothing is drawn so rows without photos don't get a gap).
 */
@Composable
fun ExerciseThumb(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp, showEmpty: Boolean = true) {
    val P = LocalPalette.current
    val shape = RoundedCornerShape(size * 0.24f)
    if (ExerciseFigures.has(name)) {
        ExerciseFigure(name, modifier.size(size).clip(shape).background(P.bgControl).padding(size * 0.1f), moving = false)
        return
    }
    val id = remember(name) { ExercisePhotos.idFor(name) }
    if (id == null && !showEmpty) return
    val px = with(LocalDensity.current) { size.roundToPx() }
    val empty: @Composable () -> Unit = {
        Icon(Icons.Rounded.FitnessCenter, null, tint = P.textTertiary, modifier = Modifier.size(size * 0.42f))
    }
    Box(modifier.size(size).clip(shape).background(if (id != null) Color.White else P.bgControl)) {
        if (id == null) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) { empty() }
        else PhotoFrame(id, 0, px, Modifier.matchParentSize()) { Box(Modifier.fillMaxSize().background(P.bgControl)) }
    }
}

/**
 * The start and end photos of [name], switching every 1.4 s so the movement reads like a short
 * clip. Nothing when there are no photos for it.
 */
@Composable
fun ExercisePhotoClip(name: String, modifier: Modifier = Modifier) {
    val P = LocalPalette.current
    if (ExerciseFigures.has(name)) {
        ExerciseFigure(name, modifier.clip(RoundedCornerShape(14.dp)).background(P.bgControl).padding(12.dp))
        return
    }
    val id = remember(name) { ExercisePhotos.idFor(name) } ?: return
    var frame by remember(name) { mutableIntStateOf(0) }
    LaunchedEffect(name) {
        while (true) {
            delay(1400)
            frame = 1 - frame
        }
    }
    val context = LocalContext.current
    // The end frame loads with the start one, so the first switch doesn't flash the placeholder.
    LaunchedEffect(id) { ExercisePhotos.load(context, id, 1, 900) }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White)) {
        Crossfade(frame, Modifier.matchParentSize(), animationSpec = tween(350), label = "photo_frame") { f ->
            PhotoFrame(id, f, 900, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(P.bgControl)) }
        }
    }
}
