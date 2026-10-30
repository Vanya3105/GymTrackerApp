package com.example.gymtracker

import androidx.compose.runtime.Immutable

/** How to do a built-in exercise: a few plain steps and the one thing people most often get wrong. */
@Immutable
data class ExerciseGuide(val steps: List<String>, val tip: String)

/** Written for this app; keyed by the library's exercise names (see [ExerciseDatabase]). */
object ExerciseGuides {
    fun find(name: String): ExerciseGuide? = guides[exerciseKey(name)]

    private fun g(tip: String, vararg steps: String) = ExerciseGuide(steps.toList(), tip)

    private val guides: Map<String, ExerciseGuide> = mapOf(
        // Chest
        "Barbell Bench Press" to g(
            "Keep your feet flat and your shoulder blades pinched back for the whole set.",
            "Lie on the bench with your eyes under the bar.",
            "Grip the bar a little wider than your shoulders and unrack it over your chest.",
            "Lower it to your mid-chest with your elbows about 45° from your body.",
            "Press back up until your arms are straight."
        ),
        "Incline Barbell Bench Press" to g(
            "A low incline (about 30°) works the upper chest; steeper turns it into a shoulder press.",
            "Set the bench to about 30° and lie back with your eyes under the bar.",
            "Grip a little wider than your shoulders and unrack.",
            "Lower the bar to your upper chest, just under the collarbones.",
            "Press up and slightly back until your arms are straight."
        ),
        "Decline Barbell Bench Press" to g(
            "Have someone hand you the bar: unracking from a decline is awkward.",
            "Hook your legs under the pads and lie back on the decline bench.",
            "Grip a little wider than your shoulders and unrack.",
            "Lower the bar to your lower chest.",
            "Press it back up over your chest."
        ),
        "Dumbbell Bench Press" to g(
            "Kick the dumbbells up off your knees as you lie back; don't hoist them from the floor.",
            "Sit with the dumbbells on your knees, then lie back and bring them over your chest.",
            "Lower them to the sides of your chest, elbows about 45° from your body.",
            "Press them up and slightly together until your arms are straight."
        ),
        "Incline Dumbbell Press" to g(
            "Let the dumbbells come down deep for a full stretch, but stop if your shoulders complain.",
            "Set the bench to 30–45° and lie back with the dumbbells over your upper chest.",
            "Lower them to the sides of your upper chest.",
            "Press up and slightly together until your arms are straight."
        ),
        "Decline Dumbbell Press" to g(
            "Use a moderate weight: getting heavy dumbbells into position on a decline is hard.",
            "Lock your legs under the pads and lie back with the dumbbells over your lower chest.",
            "Lower them to the sides of your lower chest.",
            "Press them back up until your arms are straight."
        ),
        "Smith Machine Bench Press" to g(
            "Set the safety stops just above your chest before you start.",
            "Lie on a bench under the bar so it lines up with your mid-chest.",
            "Grip a little wider than your shoulders and twist the bar to unhook it.",
            "Lower it to your mid-chest, then press back up."
        ),
        "Smith Machine Incline Press" to g(
            "Move the bench until the bar touches your upper chest, not your neck.",
            "Set an incline bench (about 30°) under the bar.",
            "Grip a little wider than your shoulders and unhook the bar.",
            "Lower to your upper chest, then press back up."
        ),
        "Machine Chest Press" to g(
            "Set the seat so the handles are level with your mid-chest.",
            "Sit with your back flat on the pad and grip the handles.",
            "Press forward until your arms are straight, without locking hard.",
            "Bring the handles back slowly until you feel a stretch in your chest."
        ),
        "Incline Machine Press" to g(
            "Keep your shoulder blades back against the pad as you press.",
            "Sit with your back on the pad and the handles at upper-chest height.",
            "Press up and forward until your arms are straight.",
            "Lower slowly until you feel a stretch in your chest."
        ),
        "Pec Deck" to g(
            "Keep a slight bend in your elbows the whole time; don't turn it into a press.",
            "Set the seat so the handles are at chest height and sit back against the pad.",
            "With your arms open and elbows slightly bent, squeeze the handles together in front of you.",
            "Open your arms slowly until you feel a stretch across your chest."
        ),
        "Cable Fly" to g(
            "Think of hugging a big tree: the shape of your arms shouldn't change.",
            "Set both pulleys at shoulder height, take a handle in each hand and step forward.",
            "With a slight bend in your elbows, bring your hands together in front of your chest.",
            "Let your arms open slowly until you feel a stretch."
        ),
        "Low-to-High Cable Fly" to g(
            "Finish with your hands at about chin height to work the upper chest.",
            "Set both pulleys low, take a handle in each hand and step forward.",
            "With your elbows slightly bent, sweep your hands up and together in front of your face.",
            "Lower them slowly back down and out."
        ),
        "High-to-Low Cable Fly" to g(
            "Lean forward a little and finish with your hands around belly height.",
            "Set both pulleys high, take a handle in each hand and step forward.",
            "With your elbows slightly bent, sweep your hands down and together in front of your hips.",
            "Let them rise slowly back out to the sides."
        ),
        "Dumbbell Fly" to g(
            "Stop lowering when your elbows are level with the bench; deeper only stresses the shoulders.",
            "Lie on a flat bench with the dumbbells above your chest, palms facing each other.",
            "With a slight bend in your elbows, open your arms out to the sides.",
            "Bring them back together over your chest in the same arc."
        ),
        "Incline Dumbbell Fly" to g(
            "Use lighter weights than for the press: flyes are about the stretch, not the load.",
            "Lie on a bench set to about 30° with the dumbbells above your upper chest.",
            "With a slight bend in your elbows, open your arms out to the sides.",
            "Bring them back together over your chest."
        ),
        "Push-Up" to g(
            "Keep your body in one straight line: hips shouldn't sag or pike up.",
            "Hands a little wider than your shoulders, body straight from head to heels.",
            "Lower your chest towards the floor, elbows about 45° from your body.",
            "Push back up until your arms are straight."
        ),
        "Weighted Dips" to g(
            "Only add weight once you can do clean bodyweight dips for 10 or more.",
            "Hang the weight from a dip belt and support yourself on the bars, arms straight.",
            "Lean slightly forward and lower until your shoulders are just below your elbows.",
            "Press back up until your arms are straight."
        ),
        "Dips" to g(
            "Lean forward for more chest, stay upright for more triceps.",
            "Support yourself on the bars with your arms straight.",
            "Lean slightly forward and bend your elbows to lower your body.",
            "Stop when your shoulders are just below your elbows, then press back up."
        ),
        "Assisted Dip" to g(
            "More assistance makes it easier: lower it over time as you get stronger.",
            "Choose the assistance weight and kneel or stand on the pad.",
            "Holding the handles, bend your elbows to lower your body.",
            "Press back up until your arms are straight."
        ),

        // Back
        "Pull-Up" to g(
            "Start each rep from a full hang and pull your elbows down towards your ribs.",
            "Hang from the bar with your hands a little wider than your shoulders, palms facing away.",
            "Pull yourself up until your chin is over the bar.",
            "Lower yourself all the way down under control."
        ),
        "Assisted Pull-Up" to g(
            "More assistance makes it easier: lower it over time as you get stronger.",
            "Set the assistance weight and kneel or stand on the pad.",
            "Grip the handles a little wider than your shoulders and pull until your chin is over the bar.",
            "Lower yourself slowly to straight arms."
        ),
        "Weighted Pull-Up" to g(
            "Keep the same full range as bodyweight pull-ups; don't cut the reps short.",
            "Hang the weight from a belt and grip the bar a little wider than your shoulders.",
            "Pull yourself up until your chin is over the bar.",
            "Lower yourself to a full hang."
        ),
        "Chin-Up" to g(
            "Palms facing you lets your biceps help, so most people manage more chin-ups than pull-ups.",
            "Hang from the bar with your hands shoulder-width apart, palms facing you.",
            "Pull yourself up until your chin is over the bar.",
            "Lower yourself all the way down."
        ),
        "Inverted Row" to g(
            "The more horizontal your body, the harder it gets.",
            "Lie under a bar set at hip height and grip it a little wider than your shoulders.",
            "Keep your body straight with your heels on the floor.",
            "Pull your chest up to the bar, then lower back down."
        ),
        "Lat Pulldown" to g(
            "Pull with your elbows, not your hands, and don't lean far back.",
            "Sit with your thighs under the pads and grip the bar a little wider than your shoulders.",
            "Pull the bar down to your upper chest, leaning back only slightly.",
            "Let it rise slowly until your arms are straight."
        ),
        "Close-Grip Lat Pulldown" to g(
            "Let the handle go all the way up each rep for a full stretch.",
            "Attach a V-handle, sit with your thighs under the pads and hold it with your palms facing.",
            "Pull the handle down to your upper chest.",
            "Let it rise slowly until your arms are straight."
        ),
        "Single-Arm Lat Pulldown" to g(
            "Let your shoulder rise at the top for a full stretch, then pull it down first.",
            "Attach a single handle and sit facing the machine.",
            "Pull the handle down to the side of your chest, elbow going towards your hip.",
            "Let it rise slowly until your arm is straight. Switch sides."
        ),
        "Straight-Arm Pulldown" to g(
            "Keep your arms straight: if your elbows bend, it turns into a triceps pushdown.",
            "Set a bar or rope high and stand facing the cable, leaning slightly forward.",
            "With straight arms, pull it down in an arc to your thighs.",
            "Let it rise back to head height."
        ),
        "Barbell Row" to g(
            "Keep your back flat; if you have to jerk the weight up, it's too heavy.",
            "Hold the bar with a shoulder-width grip and hinge forward until your torso is near 45°.",
            "Pull the bar to your lower ribs, squeezing your shoulder blades together.",
            "Lower it until your arms are straight."
        ),
        "Pendlay Row" to g(
            "Each rep starts from the floor, so reset your back before every pull.",
            "Bend over with your back flat and nearly level with the floor, bar on the ground.",
            "Pull the bar explosively to your lower chest.",
            "Lower it back to the floor."
        ),
        "T-Bar Row" to g(
            "Keep your chest up and your back flat; don't round to reach further.",
            "Straddle the bar or stand on the platform and grab the handles.",
            "Hinge forward with your back flat.",
            "Pull the handles to your chest, then lower until your arms are straight."
        ),
        "Dumbbell Row" to g(
            "Pull the dumbbell towards your hip, not straight up to your shoulder.",
            "Put one knee and hand on a bench, back flat, dumbbell in the other hand.",
            "Pull the dumbbell up towards your hip.",
            "Lower it until your arm is straight. Switch sides."
        ),
        "Chest-Supported Row" to g(
            "The pad takes your lower back out of it, so you can focus on squeezing.",
            "Lie face down on the pad and grip the handles.",
            "Pull them towards you, squeezing your shoulder blades together.",
            "Let them go forward until your arms are straight."
        ),
        "Chest-Supported Dumbbell Row" to g(
            "Keep your chest on the bench the whole time.",
            "Lie face down on a bench set to about 30° with a dumbbell in each hand.",
            "Pull the dumbbells up towards your hips.",
            "Lower them until your arms are straight."
        ),
        "Seated Cable Row" to g(
            "Sit tall; don't rock back and forth to move the weight.",
            "Sit with your feet on the platform, knees slightly bent, holding the handle.",
            "Pull the handle to your belly, squeezing your shoulder blades together.",
            "Let it go forward until your arms are straight and you feel a stretch."
        ),
        "Machine Row" to g(
            "Set the seat so the handles line up with your lower chest.",
            "Sit with your chest against the pad and grip the handles.",
            "Pull them back, squeezing your shoulder blades together.",
            "Let them go forward until your arms are straight."
        ),
        "Meadows Row" to g(
            "Use straps if your grip gives out before your back does.",
            "Stand side-on to a landmine bar and grip the end with your near hand.",
            "Hinge forward with your back flat.",
            "Pull the bar up towards your hip, then lower it. Switch sides."
        ),
        "Deadlift" to g(
            "Push the floor away with your legs and keep the bar touching your legs all the way.",
            "Stand with your feet hip-width apart, the bar over your mid-foot.",
            "Bend down and grip the bar just outside your legs, back flat, chest up.",
            "Stand up by pushing through your legs until you're upright.",
            "Lower the bar back down along your legs."
        ),
        "Rack Pull" to g(
            "Start with the bar just below or above your knees in the rack.",
            "Set the bar on the safeties at about knee height.",
            "Grip it, flatten your back and brace.",
            "Stand up until you're upright, then lower it back to the safeties."
        ),
        "Back Extension" to g(
            "Stop when your body is in a straight line; don't arch backwards.",
            "Set the pad just below your hips and hook your feet under the rollers.",
            "Lower your upper body by bending at the hips.",
            "Raise it back up until your body is straight."
        ),
        "Machine Pullover" to g(
            "Keep your arms nearly straight and let your back do the work.",
            "Sit with your back against the pad and your arms on the arm pads overhead.",
            "Pull your arms down in an arc to your sides.",
            "Let them rise slowly back overhead."
        ),
        "Dumbbell Shrug" to g(
            "Shrug straight up towards your ears; don't roll your shoulders.",
            "Stand holding a dumbbell in each hand at your sides.",
            "Lift your shoulders as high as you can.",
            "Hold for a moment, then lower them."
        ),
        "Barbell Shrug" to g(
            "Keep your arms straight: the shoulders move, the elbows don't.",
            "Stand holding a barbell in front of your thighs, shoulder-width grip.",
            "Lift your shoulders straight up towards your ears.",
            "Hold for a moment, then lower them."
        ),

        // Shoulders
        "Overhead Press" to g(
            "Squeeze your glutes and brace your stomach so your lower back doesn't arch.",
            "Hold the bar at your collarbones with a grip just wider than your shoulders.",
            "Press it straight overhead, moving your head back slightly to clear the bar.",
            "Lower it back to your collarbones."
        ),
        "Seated Dumbbell Shoulder Press" to g(
            "Stop just short of the dumbbells touching at the top to keep tension on the shoulders.",
            "Sit on a bench with an upright back, dumbbells at shoulder height.",
            "Press them overhead until your arms are straight.",
            "Lower them back to shoulder height."
        ),
        "Machine Shoulder Press" to g(
            "Set the seat so the handles start at about shoulder height.",
            "Sit with your back against the pad and grip the handles.",
            "Press up until your arms are straight.",
            "Lower slowly back to the start."
        ),
        "Smith Machine Shoulder Press" to g(
            "Sit so the bar passes just in front of your face.",
            "Set a bench upright under the bar.",
            "Unhook the bar at about chin height.",
            "Press it overhead, then lower it back to chin height."
        ),
        "Arnold Press" to g(
            "Turn your palms as you press, finishing with them facing forward.",
            "Sit with the dumbbells in front of your shoulders, palms facing you.",
            "Press up while turning your palms outwards.",
            "Reverse the movement on the way down."
        ),
        "Lateral Raise" to g(
            "Lead with your elbows and stop at shoulder height; swinging just moves the weight, not the muscle.",
            "Stand holding a dumbbell at each side, elbows slightly bent.",
            "Raise your arms out to the sides until they're level with your shoulders.",
            "Lower them slowly."
        ),
        "Cable Lateral Raise" to g(
            "The cable keeps tension at the bottom, where dumbbells don't.",
            "Stand side-on to a low pulley and take the handle in your far hand.",
            "Raise your arm out to the side to shoulder height.",
            "Lower it slowly. Switch sides."
        ),
        "Machine Lateral Raise" to g(
            "Line your shoulders up with the machine's pivot.",
            "Sit with your arms against the pads.",
            "Raise your arms out to the sides until they're level with your shoulders.",
            "Lower them slowly."
        ),
        "Lean-Away Lateral Raise" to g(
            "Leaning away makes the start of the lift harder, where a normal raise is easy.",
            "Hold a post with one hand and lean away from it, dumbbell in the other hand.",
            "Raise the dumbbell out to the side to shoulder height.",
            "Lower it slowly. Switch sides."
        ),
        "Front Raise" to g(
            "Stop at eye level; higher brings in your traps.",
            "Stand holding dumbbells in front of your thighs.",
            "Raise them in front of you with straight arms to eye level.",
            "Lower them slowly."
        ),
        "Rear Delt Fly" to g(
            "Think of pushing the dumbbells out wide, not squeezing your shoulder blades.",
            "Hinge forward with a flat back, dumbbells hanging under your chest.",
            "Raise your arms out to the sides with a slight bend in your elbows.",
            "Lower them slowly."
        ),
        "Reverse Pec Deck" to g(
            "Use the handles at shoulder height and keep your arms nearly straight.",
            "Sit facing the pad with the handles in front of you.",
            "Open your arms out wide until they're in line with your body.",
            "Bring them back slowly."
        ),
        "Face Pull" to g(
            "Finish with your hands beside your ears and your elbows high.",
            "Set a rope at face height and hold an end in each hand.",
            "Pull the rope towards your face, splitting the ends apart.",
            "Let it go back until your arms are straight."
        ),
        "Upright Row" to g(
            "Use a grip at least shoulder-width and stop at chest height to spare your shoulders.",
            "Stand holding the bar in front of your thighs.",
            "Pull it up along your body to about chest height, elbows leading.",
            "Lower it slowly."
        ),
        "Cable Y-Raise" to g(
            "Use a light weight: this is for the shoulders' upper and rear parts, not strength.",
            "Set both pulleys low and cross the cables, each hand holding the opposite side.",
            "Raise your arms up and out into a Y shape.",
            "Lower them slowly."
        ),

        // Biceps
        "Barbell Curl" to g(
            "Keep your elbows by your sides; if your body rocks, the weight is too heavy.",
            "Stand holding the bar with a shoulder-width, palms-up grip.",
            "Curl it up towards your shoulders.",
            "Lower it until your arms are straight."
        ),
        "EZ-Bar Curl" to g(
            "The angled bar is easier on the wrists than a straight bar.",
            "Hold the EZ bar on the angled grips, palms up.",
            "Curl it up towards your shoulders, elbows by your sides.",
            "Lower it until your arms are straight."
        ),
        "Dumbbell Curl" to g(
            "Turn your palms up as you curl for a full squeeze.",
            "Stand holding a dumbbell in each hand at your sides.",
            "Curl them up towards your shoulders, elbows by your sides.",
            "Lower them until your arms are straight."
        ),
        "Incline Dumbbell Curl" to g(
            "Let your arms hang straight down behind you: the stretch is the point.",
            "Sit back on a bench set to about 45° with your arms hanging down.",
            "Curl the dumbbells up without moving your elbows forward.",
            "Lower them until your arms are straight."
        ),
        "Hammer Curl" to g(
            "Keep your palms facing each other the whole time.",
            "Stand holding dumbbells at your sides, palms facing in.",
            "Curl them up towards your shoulders.",
            "Lower them until your arms are straight."
        ),
        "Preacher Curl" to g(
            "Don't lock out hard at the bottom; stop just short of fully straight.",
            "Sit at the preacher bench with your upper arms flat on the pad.",
            "Curl the bar up towards your shoulders.",
            "Lower it slowly until your arms are nearly straight."
        ),
        "Machine Preacher Curl" to g(
            "Line your elbows up with the machine's pivot.",
            "Sit with your upper arms on the pad and grip the handles.",
            "Curl them up towards your shoulders.",
            "Lower them slowly."
        ),
        "Cable Curl" to g(
            "Step back slightly so the cable pulls at your arms from the start.",
            "Attach a bar to a low pulley and hold it palms up.",
            "Curl it up towards your shoulders, elbows by your sides.",
            "Lower it until your arms are straight."
        ),
        "Bayesian Cable Curl" to g(
            "Your arm starts behind your body, so the biceps work at full stretch.",
            "Set a low pulley behind you, hold the handle and step forward.",
            "With your arm behind you, curl the handle forward and up.",
            "Let it go back slowly. Switch sides."
        ),
        "Concentration Curl" to g(
            "Brace your elbow against your inner thigh so only your forearm moves.",
            "Sit on a bench and lean forward, elbow on your inner thigh.",
            "Curl the dumbbell up towards your shoulder.",
            "Lower it until your arm is straight. Switch sides."
        ),
        "Spider Curl" to g(
            "Your arms hang straight down, which keeps tension at the top of the curl.",
            "Lie face down on an incline bench with your arms hanging down.",
            "Curl the dumbbells or bar up.",
            "Lower them until your arms are straight."
        ),
        "Rope Hammer Curl" to g(
            "Keep your palms facing each other, as in a dumbbell hammer curl.",
            "Attach a rope to a low pulley and hold an end in each hand.",
            "Curl the rope up towards your shoulders.",
            "Lower it until your arms are straight."
        ),

        // Triceps
        "Tricep Pushdown" to g(
            "Pin your elbows to your sides: only your forearms move.",
            "Attach a bar to a high pulley and hold it with your palms down.",
            "Push it down until your arms are straight.",
            "Let it rise until your forearms are level with the floor."
        ),
        "Rope Pushdown" to g(
            "Pull the ends of the rope apart at the bottom.",
            "Attach a rope to a high pulley and hold an end in each hand.",
            "Push it down until your arms are straight, spreading the ends.",
            "Let it rise until your forearms are level with the floor."
        ),
        "Overhead Cable Tricep Extension" to g(
            "Keep your elbows pointing forward; don't let them flare out.",
            "Face away from the pulley with the rope behind your head.",
            "Straighten your arms forward and up.",
            "Let your hands come back behind your head."
        ),
        "Overhead Dumbbell Extension" to g(
            "Lower the dumbbell behind your head, not down your back.",
            "Sit or stand holding one dumbbell overhead with both hands.",
            "Bend your elbows to lower it behind your head.",
            "Straighten your arms to lift it back up."
        ),
        "Skull Crusher" to g(
            "Lower the bar behind the top of your head rather than to your forehead.",
            "Lie on a bench holding the bar over your chest with arms straight.",
            "Bend your elbows to lower the bar towards the top of your head.",
            "Straighten your arms to lift it back."
        ),
        "Close-Grip Bench Press" to g(
            "Shoulder-width is close enough; a narrower grip only hurts the wrists.",
            "Lie on the bench and grip the bar at shoulder width.",
            "Lower it to your lower chest, elbows close to your body.",
            "Press it back up."
        ),
        "JM Press" to g(
            "A mix of close-grip bench and skull crusher: keep the weight moderate.",
            "Lie on the bench with a close grip, bar over your chest.",
            "Lower the bar towards your chin, letting your elbows bend forward.",
            "Press it back up."
        ),
        "Tricep Dips" to g(
            "Stay upright to keep the work on the triceps.",
            "Support yourself on parallel bars or a bench behind you.",
            "Bend your elbows to lower your body, keeping them pointing back.",
            "Press back up until your arms are straight."
        ),
        "Machine Tricep Extension" to g(
            "Line your elbows up with the machine's pivot.",
            "Sit with your upper arms on the pad and grip the handles.",
            "Straighten your arms against the resistance.",
            "Let them bend back slowly."
        ),
        "Single-Arm Cable Extension" to g(
            "Working one arm at a time evens out a weaker side.",
            "Hold a single handle from a high pulley.",
            "Push it down until your arm is straight, elbow by your side.",
            "Let it rise slowly. Switch sides."
        ),
        "Dumbbell Kickback" to g(
            "Keep your upper arm still and level with your body.",
            "Hinge forward with a flat back, upper arm along your side.",
            "Straighten your arm behind you.",
            "Bend it back slowly. Switch sides."
        ),

        // Quads
        "Back Squat" to g(
            "Keep your whole foot on the floor and your knees pointing the same way as your toes.",
            "Rest the bar on your upper back and stand with your feet about shoulder-width.",
            "Brace your stomach and sit down between your heels, knees forward and out.",
            "Go as deep as you can with a flat back, then stand back up."
        ),
        "Front Squat" to g(
            "Keep your elbows high so the bar stays on your shoulders.",
            "Rest the bar on the front of your shoulders, elbows high.",
            "Squat down with your chest up.",
            "Stand back up, leading with your elbows."
        ),
        "Hack Squat" to g(
            "Feet lower on the platform works the quads more; higher brings in the glutes.",
            "Stand with your back against the pad and your shoulders under the pads.",
            "Release the safety and bend your knees to lower yourself.",
            "Push back up without locking your knees."
        ),
        "Pendulum Squat" to g(
            "Let your knees travel well forward: the machine's arc makes it comfortable.",
            "Stand on the platform with your shoulders under the pads.",
            "Bend your knees to lower yourself as deep as you can.",
            "Push back up."
        ),
        "Smith Machine Squat" to g(
            "Set your feet slightly in front of the bar.",
            "Stand under the bar with it across your upper back.",
            "Unhook it and bend your knees to squat down.",
            "Push back up."
        ),
        "Goblet Squat" to g(
            "A great way to learn the squat: the weight in front helps you stay upright.",
            "Hold a dumbbell against your chest with both hands.",
            "Squat down between your knees, keeping your chest up.",
            "Stand back up."
        ),
        "Leg Press" to g(
            "Don't let your lower back lift off the seat at the bottom.",
            "Sit with your back flat and your feet shoulder-width on the platform.",
            "Release the safety and bend your knees towards your chest.",
            "Push the platform back up without locking your knees."
        ),
        "Leg Extension" to g(
            "Pause for a second at the top.",
            "Sit with the pad on your lower shins and line your knees up with the pivot.",
            "Straighten your legs.",
            "Lower them slowly."
        ),
        "Bulgarian Split Squat" to g(
            "Most of your weight goes on the front leg; the back foot only balances.",
            "Stand in front of a bench and rest the top of one foot on it behind you.",
            "Lower your back knee towards the floor.",
            "Push back up through your front foot. Switch legs."
        ),
        "Walking Lunge" to g(
            "Take long enough steps that your front knee doesn't go far past your toes.",
            "Stand holding dumbbells at your sides.",
            "Step forward and lower your back knee towards the floor.",
            "Push up and bring your back foot forward into the next step."
        ),
        "Reverse Lunge" to g(
            "Stepping back is easier on the knees than a forward lunge.",
            "Stand holding dumbbells at your sides.",
            "Step back and lower your back knee towards the floor.",
            "Push through your front foot to return. Switch legs."
        ),
        "Step-Up" to g(
            "Push through the foot on the box; don't spring off the floor.",
            "Stand facing a box or bench holding dumbbells.",
            "Step up with one foot and stand up fully on the box.",
            "Step back down. Switch legs."
        ),
        "Sissy Squat" to g(
            "Hold something for balance; it's a hard exercise for the knees, so start shallow.",
            "Stand holding a support, on your toes.",
            "Lean back and bend your knees, letting them travel forward.",
            "Straighten back up."
        ),
        "Belt Squat" to g(
            "The weight hangs from your hips, so your back stays out of it.",
            "Attach the belt around your hips and stand on the platform.",
            "Squat down.",
            "Stand back up."
        ),

        // Hamstrings
        "Romanian Deadlift" to g(
            "Push your hips back like closing a door behind you; the bar stays against your legs.",
            "Stand holding the bar at hip height, knees slightly bent.",
            "Push your hips back and lower the bar down your legs with a flat back.",
            "Stop when you feel a strong stretch in your hamstrings, then stand back up."
        ),
        "Dumbbell Romanian Deadlift" to g(
            "Keep the dumbbells close to your legs the whole way.",
            "Stand holding dumbbells in front of your thighs, knees slightly bent.",
            "Push your hips back and lower the dumbbells with a flat back.",
            "Stand back up when you feel a strong stretch."
        ),
        "Stiff-Leg Deadlift" to g(
            "Your knees stay nearly straight, so use less weight than a Romanian deadlift.",
            "Stand holding the bar with your knees almost straight.",
            "Hinge at the hips and lower the bar with a flat back.",
            "Stand back up."
        ),
        "Seated Leg Curl" to g(
            "Lean forward slightly for a better stretch.",
            "Sit with the pad on the back of your lower legs and the thigh pad locked down.",
            "Curl your heels down and under the seat.",
            "Let them rise slowly."
        ),
        "Lying Leg Curl" to g(
            "Keep your hips pressed into the pad.",
            "Lie face down with the pad just above your heels.",
            "Curl your heels towards your glutes.",
            "Lower them slowly."
        ),
        "Standing Leg Curl" to g(
            "Work one leg at a time and keep your hips still.",
            "Stand at the machine with the pad behind one ankle.",
            "Curl your heel up towards your glute.",
            "Lower it slowly. Switch legs."
        ),
        "Nordic Curl" to g(
            "Very hard: lower as slowly as you can and use your hands to push back up at first.",
            "Kneel with your ankles held down by a partner or pad.",
            "Keeping your body straight, slowly lower yourself forward.",
            "Catch yourself with your hands and pull back up with your hamstrings."
        ),
        "Good Morning" to g(
            "Start light; it's easy to overload your lower back.",
            "Rest the bar on your upper back and bend your knees slightly.",
            "Push your hips back and bow forward with a flat back.",
            "Stand back up."
        ),
        "Glute-Ham Raise" to g(
            "If it's too hard, push off with your hands at the bottom.",
            "Lock your feet in the machine with your knees just behind the pad.",
            "Lower your body forward until it's level with the floor.",
            "Curl yourself back up with your hamstrings."
        ),

        // Glutes
        "Hip Thrust" to g(
            "Tuck your chin and keep your ribs down so your back doesn't arch at the top.",
            "Sit with your upper back against a bench and the bar across your hips.",
            "Plant your feet so your shins are vertical at the top.",
            "Drive your hips up until your body is straight, squeeze, then lower."
        ),
        "Machine Hip Thrust" to g(
            "Pause for a second at the top.",
            "Sit in the machine with the pad across your hips and your feet flat.",
            "Drive your hips up until your body is straight.",
            "Lower them slowly."
        ),
        "Glute Bridge" to g(
            "Push through your heels and squeeze your glutes at the top.",
            "Lie on your back with your knees bent and the bar across your hips.",
            "Lift your hips until your body is straight from shoulders to knees.",
            "Lower them back down."
        ),
        "Cable Kickback" to g(
            "Keep your back flat: the leg moves, your lower back doesn't.",
            "Strap the cuff to one ankle and face the low pulley.",
            "Lean forward slightly and kick that leg back.",
            "Bring it back slowly. Switch legs."
        ),
        "Hip Abduction Machine" to g(
            "Lean forward slightly to work the glutes more.",
            "Sit with the pads on the outside of your knees.",
            "Push your knees out as far as you can.",
            "Bring them back slowly."
        ),
        "Hip Adduction Machine" to g(
            "Control the way back out; don't let the weights slam.",
            "Sit with the pads on the inside of your knees.",
            "Squeeze your knees together.",
            "Let them open slowly."
        ),
        "Sumo Deadlift" to g(
            "Push your knees out over your toes as you lift.",
            "Stand with a wide stance, toes turned out, bar over your mid-foot.",
            "Grip the bar between your legs with a flat back.",
            "Stand up by pushing the floor apart with your feet, then lower the bar."
        ),
        "Cable Pull-Through" to g(
            "It's a hip hinge: push your hips back, not down.",
            "Face away from a low pulley holding the rope between your legs.",
            "Push your hips back and let the rope pull your hands through.",
            "Stand up by driving your hips forward."
        ),

        // Calves
        "Standing Calf Raise" to g(
            "Pause at the bottom for the stretch and at the top for the squeeze.",
            "Stand on the platform with the balls of your feet on the edge.",
            "Rise up onto your toes as high as you can.",
            "Lower your heels below the platform."
        ),
        "Seated Calf Raise" to g(
            "Seated works the deeper calf muscle more than standing.",
            "Sit with the pad on your thighs and the balls of your feet on the edge.",
            "Raise your heels as high as you can.",
            "Lower them below the platform."
        ),
        "Leg Press Calf Raise" to g(
            "Keep the safety catches in reach in case your feet slip.",
            "Sit in the leg press with the balls of your feet on the bottom edge.",
            "Push the platform with your toes.",
            "Let it come back slowly for a stretch."
        ),
        "Smith Machine Calf Raise" to g(
            "Stand on a plate or step so your heels can go below your toes.",
            "Stand under the bar with the balls of your feet on a step.",
            "Rise up onto your toes.",
            "Lower your heels below the step."
        ),
        "Single-Leg Calf Raise" to g(
            "Hold onto something for balance.",
            "Stand on one foot on a step with your heel hanging off.",
            "Rise up onto your toes as high as you can.",
            "Lower your heel below the step. Switch legs."
        ),

        // Core
        "Cable Crunch" to g(
            "Curl your spine; don't just bend at the hips.",
            "Kneel facing a high pulley holding the rope beside your head.",
            "Crunch down, bringing your elbows towards your knees.",
            "Rise back up slowly."
        ),
        "Hanging Leg Raise" to g(
            "Curl your pelvis up at the top; just lifting your legs works the hip flexors.",
            "Hang from a bar with your arms straight.",
            "Raise your legs until they're level with the floor or higher.",
            "Lower them slowly without swinging."
        ),
        "Hanging Knee Raise" to g(
            "An easier version of the leg raise; avoid swinging.",
            "Hang from a bar with your arms straight.",
            "Bring your knees up towards your chest.",
            "Lower them slowly."
        ),
        "Ab Wheel Rollout" to g(
            "Only roll out as far as you can without your lower back sagging.",
            "Kneel holding the wheel under your shoulders.",
            "Roll forward slowly, keeping your back flat.",
            "Pull back to the start with your stomach."
        ),
        "Plank" to g(
            "Squeeze your glutes and stomach; your body is one straight line.",
            "Rest on your forearms and toes.",
            "Keep your body straight from head to heels.",
            "Hold for the time."
        ),
        "Side Plank" to g(
            "Keep your hips up; don't let them sag.",
            "Lie on your side, propped on one forearm, feet stacked.",
            "Lift your hips so your body is straight.",
            "Hold for the time. Switch sides."
        ),
        "Hollow Body Hold" to g(
            "Press your lower back into the floor the whole time.",
            "Lie on your back with your arms overhead.",
            "Lift your shoulders and legs a few centimetres off the floor.",
            "Hold for the time."
        ),
        "Decline Sit-Up" to g(
            "Come up slowly; don't throw yourself up with your arms.",
            "Hook your feet under the pads on a decline bench.",
            "Lower your back towards the bench.",
            "Curl back up."
        ),
        "Machine Crunch" to g(
            "Exhale as you crunch.",
            "Sit in the machine and hold the handles or pads.",
            "Crunch forward, curling your spine.",
            "Return slowly."
        ),
        "Pallof Press" to g(
            "The point is not moving: resist the cable pulling you round.",
            "Stand side-on to a cable at chest height, handle at your chest.",
            "Press it straight out in front of you.",
            "Hold, then bring it back. Switch sides."
        ),
        "Russian Twist" to g(
            "Turn your shoulders, not just your arms.",
            "Sit with your knees bent and lean back slightly, holding a weight.",
            "Turn your upper body to one side, then the other.",
            "Keep your feet down to make it easier, raised to make it harder."
        ),
        "Woodchopper" to g(
            "Turn through your hips and upper back together.",
            "Stand side-on to a high pulley, holding the handle with both hands.",
            "Pull it down and across your body to the opposite hip.",
            "Return slowly. Switch sides."
        ),

        // Forearms
        "Wrist Curl" to g(
            "Let the dumbbell roll down to your fingers for a fuller range.",
            "Sit with your forearms on your thighs, palms up, wrists over your knees.",
            "Curl your wrists up.",
            "Lower them slowly."
        ),
        "Reverse Wrist Curl" to g(
            "Use a light weight: the back of the forearm is weaker.",
            "Sit with your forearms on your thighs, palms down.",
            "Raise the backs of your hands.",
            "Lower them slowly."
        ),
        "Reverse Curl" to g(
            "Keep your wrists straight as you curl.",
            "Hold the bar palms down at shoulder width.",
            "Curl it up towards your shoulders.",
            "Lower it slowly."
        ),
        "Farmer's Carry" to g(
            "Stand tall and take short, quick steps.",
            "Pick up a heavy dumbbell in each hand.",
            "Walk with your shoulders back and your stomach braced.",
            "Go for a set distance or time, then put them down carefully."
        ),
        "Dead Hang" to g(
            "Relax your shoulders and let them stretch.",
            "Grip a pull-up bar with your hands shoulder-width apart.",
            "Hang with your arms straight and your feet off the floor.",
            "Hold for as long as you can."
        ),

        // Cardio
        "Running" to g(
            "Most runs should be easy enough to talk in full sentences.",
            "Start with a few minutes of easy jogging.",
            "Run at a pace you can keep for the whole time or distance.",
            "Finish with a few minutes of walking."
        ),
        "Treadmill" to g(
            "A 1% incline is closer to running outside.",
            "Start walking at a slow speed.",
            "Increase to your running or walking pace.",
            "Slow down for the last few minutes."
        ),
        "Rowing Machine" to g(
            "Legs, then back, then arms on the way back; arms, back, legs on the way forward.",
            "Strap your feet in and hold the handle with straight arms.",
            "Push with your legs, then lean back slightly and pull the handle to your ribs.",
            "Reverse the order to slide back."
        ),
        "Cycling" to g(
            "Set the saddle so your leg is almost straight at the bottom of each pedal stroke.",
            "Warm up with a few minutes of easy pedalling.",
            "Ride at your planned pace or effort.",
            "Cool down with easy pedalling."
        ),
        "Stationary Bike" to g(
            "Set the saddle so your leg is almost straight at the bottom.",
            "Start pedalling at a low resistance.",
            "Raise the resistance to your planned effort.",
            "Ease off for the last few minutes."
        ),
        "Elliptical" to g(
            "Stand tall rather than leaning on the handles.",
            "Step on and start striding slowly.",
            "Increase the resistance to your planned effort.",
            "Slow down for the last few minutes."
        ),
        "Walking" to g(
            "A brisk walk counts: you should feel your breathing pick up.",
            "Walk at a comfortable pace for a few minutes.",
            "Pick up to a brisk pace.",
            "Slow down for the last few minutes."
        ),
        "Stair Climber" to g(
            "Don't lean on the rails; let your legs do the work.",
            "Step on and hold the rails lightly.",
            "Climb at a steady pace.",
            "Slow down for the last minute."
        ),
        "Jump Rope" to g(
            "Small jumps from the balls of your feet; turn the rope with your wrists.",
            "Hold the handles at hip height, rope behind you.",
            "Swing the rope over and jump just high enough to clear it.",
            "Keep a steady rhythm."
        )
    ).mapKeys { exerciseKey(it.key) } + more().mapKeys { exerciseKey(it.key) }

    /** The wider library's guides; a separate function to stay under the JVM's method size limit. */
    private fun more(): Map<String, ExerciseGuide> = mapOf(
        "Incline Push-Up" to g(
            "The higher your hands, the easier it is: lower the surface as you get stronger.",
            "Put your hands on a bench or box, a little wider than your shoulders.",
            "Walk your feet back until your body is one straight line.",
            "Lower your chest to the edge.",
            "Push back up until your arms are straight."
        ),
        "Decline Push-Up" to g(
            "Feet up shifts the work to your upper chest and shoulders; keep your hips from sagging.",
            "Put your feet on a bench and your hands on the floor, shoulder-width apart.",
            "Hold your body in one straight line.",
            "Lower your chest to the floor.",
            "Push back up."
        ),
        "Wide Push-Up" to g(
            "Go only as wide as feels good on your shoulders; elbows still point a bit back.",
            "Start in a push-up position with your hands well outside your shoulders.",
            "Lower your chest towards the floor.",
            "Push back up until your arms are straight."
        ),
        "Knee Push-Up" to g(
            "Keep a straight line from knees to head: don't push your hips up into the air.",
            "Kneel and put your hands on the floor a little wider than your shoulders.",
            "Lean forward so your body is straight from your knees to your head.",
            "Lower your chest to the floor.",
            "Push back up."
        ),
        "Archer Push-Up" to g(
            "A step towards a one-arm push-up: let the straight arm help less over time.",
            "Start in a push-up with your hands very wide.",
            "Lower towards one hand, bending that arm while the other stays straight.",
            "Push back up to the middle.",
            "Alternate sides, or do all reps on one side first."
        ),
        "Clap Push-Up" to g(
            "Land with soft elbows; if you can't clap yet, just push hard enough for your hands to leave the floor.",
            "Start in a push-up position.",
            "Lower your chest, then push up as hard as you can.",
            "Clap while your hands are in the air.",
            "Land with your hands back in place and go straight into the next rep."
        ),
        "Single-Arm Push-Up" to g(
            "Spread your feet wide: it makes balancing much easier.",
            "Start in a push-up with your feet wide and one hand under your chest.",
            "Put the other hand behind your back.",
            "Lower your chest, keeping your hips square to the floor.",
            "Push back up."
        ),
        "Ring Push-Up" to g(
            "Turn the rings out at the top: it works the chest harder and steadies them.",
            "Set the rings a few centimetres off the floor and hold them in a push-up position.",
            "Lower your chest between the rings, keeping them close to your body.",
            "Push back up and turn your palms slightly forward at the top."
        ),
        "Dumbbell Pullover" to g(
            "Stop when you feel a big stretch, not when your arms are touching the floor.",
            "Lie across or along a bench holding one dumbbell over your chest with both hands.",
            "With a slight bend in your elbows, lower it back over your head.",
            "Pull it back over your chest."
        ),
        "Dumbbell Floor Press" to g(
            "Let your upper arms rest on the floor for a moment each rep before pressing.",
            "Lie on the floor with your knees bent and a dumbbell in each hand over your chest.",
            "Lower them until your upper arms touch the floor.",
            "Press back up."
        ),
        "Neutral-Grip Dumbbell Press" to g(
            "Easier on sore shoulders than a normal bench press.",
            "Lie on a flat bench with the dumbbells over your chest, palms facing each other.",
            "Lower them to the sides of your chest, elbows close to your body.",
            "Press back up."
        ),
        "Cable Chest Press" to g(
            "Stagger your feet so the cables don't pull you backwards.",
            "Set the handles at chest height and stand facing away, one foot forward.",
            "Hold the handles by your chest.",
            "Press forward until your arms are straight, then come back slowly."
        ),
        "Incline Cable Fly" to g(
            "Keep the same small bend in your elbows the whole time.",
            "Put an incline bench between two low cables and lie back with a handle in each hand.",
            "Open your arms wide until you feel a stretch.",
            "Bring the handles together over your upper chest."
        ),
        "Wide-Grip Bench Press" to g(
            "Wider means a shorter path but more shoulder stress; stay just outside the rings on the bar.",
            "Lie on the bench and grip the bar well wider than your shoulders.",
            "Unrack and lower the bar to your mid-chest.",
            "Press back up."
        ),
        "Barbell Floor Press" to g(
            "Great when you have no bench; pause with your arms on the floor.",
            "Lie on the floor under the bar in a rack, knees bent.",
            "Unrack and lower the bar until your upper arms touch the floor.",
            "Press back up."
        ),
        "Kettlebell Floor Press" to g(
            "Keep your wrist straight with the bell resting on the back of your forearm.",
            "Lie on the floor with a kettlebell in one hand over your chest.",
            "Lower it until your upper arm touches the floor.",
            "Press back up. Do all reps, then switch sides."
        ),
        "Svend Press" to g(
            "Squeeze the plates together hard the whole time: that squeeze is the exercise.",
            "Stand and press two small plates together in front of your chest.",
            "Push them straight out until your arms are straight.",
            "Bring them back to your chest."
        ),
        "Neutral-Grip Pull-Up" to g(
            "Often the easiest pull-up on the elbows: a good place to start.",
            "Hang from parallel handles, palms facing each other.",
            "Pull your chest up towards the handles.",
            "Lower all the way down."
        ),
        "Negative Pull-Up" to g(
            "Take 3–5 seconds on the way down: the slow part builds strength for your first pull-up.",
            "Jump or step up so your chin is over the bar.",
            "Lower yourself as slowly as you can.",
            "Step back up and repeat."
        ),
        "Scapular Pull-Up" to g(
            "Your arms stay straight: only your shoulder blades move.",
            "Hang from a bar with straight arms.",
            "Pull your shoulder blades down and together so your body rises a few centimetres.",
            "Let them back up slowly."
        ),
        "Muscle-Up" to g(
            "Pull to your lower chest, not your chin: you need that height to get over the bar.",
            "Hang from the bar with a slightly wider than shoulder grip.",
            "Pull explosively up towards your lower chest.",
            "Lean forward and turn your wrists over the bar.",
            "Push up until your arms are straight, then lower back down with control."
        ),
        "Ring Row" to g(
            "Walk your feet forward to make it harder, back to make it easier.",
            "Hold the rings and lean back with straight arms, body in one line.",
            "Pull your chest up to your hands.",
            "Lower back down slowly."
        ),
        "Rope Climb" to g(
            "Use your feet: pinch the rope between them so your legs do much of the work.",
            "Grab the rope high and pull yourself up.",
            "Wrap the rope around one leg and pinch it with the other foot.",
            "Stand up on your feet, reach higher and repeat.",
            "Climb down hand under hand: don't slide."
        ),
        "Superman Hold" to g(
            "Lift by squeezing your glutes and back, not by craning your neck.",
            "Lie face down with your arms stretched out in front.",
            "Lift your arms, chest and legs off the floor.",
            "Hold, breathing steadily."
        ),
        "Kettlebell Row" to g(
            "Pull your elbow back towards your hip, not up to your shoulder.",
            "Hinge forward with one hand on a bench and a kettlebell in the other.",
            "Row the bell to your hip.",
            "Lower it until your arm is straight."
        ),
        "Renegade Row" to g(
            "Feet wide and hips still: don't let your body twist as you row.",
            "Start in a push-up position with your hands on two dumbbells or kettlebells.",
            "Row one weight to your hip while balancing on the other.",
            "Put it down and row with the other hand."
        ),
        "Bent-Over Dumbbell Row" to g(
            "Keep your back flat and your head in line with it.",
            "Hold a dumbbell in each hand and hinge forward until your chest is nearly level.",
            "Row both dumbbells to your sides.",
            "Lower them until your arms are straight."
        ),
        "Underhand Barbell Row" to g(
            "Palms up brings in more biceps and lets you pull lower, towards your belly.",
            "Hold the bar with palms facing forward-up, shoulder-width apart.",
            "Hinge forward with a flat back.",
            "Row the bar to your lower stomach.",
            "Lower it until your arms are straight."
        ),
        "Single-Arm Cable Row" to g(
            "Let your shoulder go forward at the start for a full stretch.",
            "Sit at a low cable with a single handle.",
            "Pull the handle to your side, elbow close to your body.",
            "Let it go forward until your arm is straight, then switch sides."
        ),
        "Underhand Lat Pulldown" to g(
            "Pull your elbows down to your sides; it's easier on the shoulders for many people.",
            "Hold the bar with palms facing you, shoulder-width apart.",
            "Pull it to your upper chest.",
            "Let it up until your arms are straight."
        ),
        "Trap Bar Deadlift" to g(
            "Easier on the lower back than a barbell deadlift: a great first deadlift.",
            "Stand in the middle of the trap bar and grip the handles.",
            "Sit your hips back, chest up and back flat.",
            "Stand up by pushing the floor away.",
            "Lower it back down the same way."
        ),
        "Deficit Deadlift" to g(
            "Only a few centimetres higher: enough to make the start harder without rounding your back.",
            "Stand on a low plate or platform with the bar over your mid-foot.",
            "Grip the bar, chest up and back flat.",
            "Stand up with the bar close to your legs.",
            "Lower it back down."
        ),
        "Cable Shrug" to g(
            "Straight up and down: no rolling your shoulders.",
            "Hold a bar on a low cable with straight arms.",
            "Lift your shoulders up towards your ears.",
            "Lower them slowly."
        ),
        "Kettlebell High Pull" to g(
            "The pull comes from your legs and hips; your arms just guide it up.",
            "Stand wide with a kettlebell on the floor between your feet.",
            "Squat down and grip the handle with both hands.",
            "Stand up fast and pull the bell to your chest, elbows high.",
            "Lower it back down."
        ),
        "Pike Push-Up" to g(
            "The higher your hips, the more it works your shoulders. Raise your feet on a box to make it harder.",
            "Start in a push-up and walk your feet in until your hips are high, like an upside-down V.",
            "Bend your arms to lower the top of your head towards the floor.",
            "Push back up."
        ),
        "Handstand Push-Up" to g(
            "Put a cushion under your head until you trust your lockout.",
            "Kick up into a handstand against a wall, hands a little wider than your shoulders.",
            "Lower your head towards the floor.",
            "Press back up until your arms are straight."
        ),
        "Handstand Hold" to g(
            "Push the floor away and squeeze your legs together: a straight, tight body is easier to hold.",
            "Put your hands on the floor a hand's width from a wall.",
            "Kick up so your heels rest on the wall.",
            "Hold with straight arms, looking at the floor between your hands."
        ),
        "Push Press" to g(
            "A quick dip and drive with the legs lets you press more than a strict press.",
            "Hold the bar on the front of your shoulders.",
            "Dip a few centimetres by bending your knees.",
            "Drive up through your legs and press the bar overhead.",
            "Lower it back to your shoulders."
        ),
        "Standing Dumbbell Press" to g(
            "Squeeze your glutes so your lower back doesn't arch.",
            "Stand holding the dumbbells at shoulder height.",
            "Press them overhead until your arms are straight.",
            "Lower them back to your shoulders."
        ),
        "Seated Barbell Press" to g(
            "Move your head back a little to let the bar pass, then push it through once the bar is past.",
            "Sit on an upright bench with the bar racked at shoulder height.",
            "Unrack with your hands just outside your shoulders.",
            "Press the bar overhead.",
            "Lower it to your upper chest."
        ),
        "Kettlebell Press" to g(
            "Start with the bell resting on your forearm, knuckles up, wrist straight.",
            "Clean the kettlebells to your shoulders.",
            "Press them overhead until your arms are straight.",
            "Lower them back to your shoulders."
        ),
        "Landmine Press" to g(
            "The angle is kind to shoulders: good if overhead pressing hurts.",
            "Put one end of a barbell in a landmine or a corner.",
            "Hold the other end at your shoulder with one hand.",
            "Press it up and forward until your arm is straight.",
            "Lower it back to your shoulder."
        ),
        "Cable Front Raise" to g(
            "Raise to eye height; higher than that takes the work off the front of the shoulder.",
            "Stand with your back to a low cable, the handle between your legs.",
            "With a slight bend in your elbow, raise it in front of you to eye height.",
            "Lower it slowly."
        ),
        "Plate Front Raise" to g(
            "Hold the plate at 3 and 9 o'clock with your arms nearly straight.",
            "Stand holding a plate in front of your thighs.",
            "Raise it in front of you to eye height.",
            "Lower it slowly."
        ),
        "Cable Rear Delt Fly" to g(
            "Lead with your elbows and keep your shoulders down.",
            "Set two cables at shoulder height and hold the opposite handles, arms crossed.",
            "Pull your arms out and back until they're in line with your body.",
            "Return slowly."
        ),
        "Dumbbell Upright Row" to g(
            "Stop at chest height; higher can pinch the shoulder.",
            "Stand holding the dumbbells in front of your thighs.",
            "Lift them up your body, elbows leading, to chest height.",
            "Lower them slowly."
        ),
        "Cable Upright Row" to g(
            "A wider grip is easier on the shoulders.",
            "Hold a bar on a low cable with your hands about shoulder-width.",
            "Pull it up to chest height, elbows leading.",
            "Lower it slowly."
        ),
        "Cuban Press" to g(
            "Go light: this is for healthy shoulders, not for heavy weights.",
            "Hold light dumbbells and raise your elbows out to shoulder height.",
            "Rotate your forearms up until they point at the ceiling.",
            "Press the dumbbells overhead.",
            "Reverse the steps on the way down."
        ),
        "Cable External Rotation" to g(
            "Keep your elbow pinned to your side; a towel under your arm helps.",
            "Stand side-on to a cable at elbow height, holding the handle with the far hand.",
            "With your elbow bent at 90°, rotate your forearm out away from your body.",
            "Return slowly."
        ),
        "Dumbbell External Rotation" to g(
            "Use a very light weight and go slowly.",
            "Lie on your side with a light dumbbell in your top hand, elbow on your side.",
            "With your elbow bent at 90°, rotate the dumbbell up.",
            "Lower it slowly."
        ),
        "Kettlebell Halo" to g(
            "Keep your ribs down and your hips still; only your arms move.",
            "Hold a kettlebell upside down by the horns at chest height.",
            "Circle it around your head, close to your neck.",
            "Go the other way on the next rep."
        ),
        "Turkish Get-Up" to g(
            "Look at the bell the whole time and learn it with a shoe on your fist before using a weight.",
            "Lie on your back with the kettlebell pressed up in one hand.",
            "Roll onto your other elbow, then your hand, keeping the bell straight up.",
            "Lift your hips, sweep your leg under and come up to a kneel.",
            "Stand up, then reverse every step back down to the floor."
        ),
        "Kettlebell Snatch" to g(
            "Punch your hand through at the top so the bell doesn't slam your forearm.",
            "Swing the kettlebell back between your legs with one hand.",
            "Drive your hips forward and pull the bell up close to your body.",
            "Punch your hand up so it finishes overhead with a straight arm.",
            "Let it drop back into the next swing."
        ),
        "Clean and Press" to g(
            "Two lifts in one: get the clean right first, then press.",
            "Stand with the bar over your mid-foot.",
            "Pull it fast from the floor and catch it on the front of your shoulders.",
            "Press it overhead.",
            "Lower it to your shoulders, then to the floor."
        ),
        "Alternating Dumbbell Curl" to g(
            "One arm rests while the other works, so you can often go a little heavier.",
            "Stand with a dumbbell in each hand, palms forward.",
            "Curl one dumbbell up, keeping your elbow by your side.",
            "Lower it, then curl the other."
        ),
        "Seated Dumbbell Curl" to g(
            "Sitting stops you swinging the weight up.",
            "Sit at the end of a bench with a dumbbell in each hand.",
            "Curl them up, elbows by your sides.",
            "Lower them until your arms are straight."
        ),
        "Incline Hammer Curl" to g(
            "Let your arms hang straight down behind you at the bottom.",
            "Lie back on an incline bench with the dumbbells hanging, palms facing in.",
            "Curl them up, keeping your palms facing in.",
            "Lower them slowly."
        ),
        "Cross-Body Hammer Curl" to g(
            "Curl towards the opposite shoulder; it hits the outer part of the arm.",
            "Stand with the dumbbells at your sides, palms facing in.",
            "Curl one dumbbell up and across towards your other shoulder.",
            "Lower it and switch arms."
        ),
        "Zottman Curl" to g(
            "Up palms-up, down palms-down: the slow lowering works your forearms.",
            "Curl the dumbbells up with your palms facing up.",
            "At the top, turn your palms down.",
            "Lower them slowly palms down, then turn them back up at the bottom."
        ),
        "Drag Curl" to g(
            "Drag the bar up your body: your elbows go back, not forward.",
            "Hold a barbell in front of your thighs, palms forward.",
            "Pull it up along your body while your elbows move behind you.",
            "Lower it the same way."
        ),
        "Overhead Cable Curl" to g(
            "Keep your upper arms level with your shoulders; only your forearms move.",
            "Stand between two high cables holding a handle in each hand, arms out to the sides.",
            "Curl the handles towards your ears.",
            "Straighten your arms slowly."
        ),
        "Single-Arm Cable Curl" to g(
            "Step forward a little so the cable pulls on your arm even at the bottom.",
            "Hold a handle on a low cable with one hand.",
            "Curl it up, elbow by your side.",
            "Lower it slowly, then switch arms."
        ),
        "Cable Preacher Curl" to g(
            "The cable keeps tension at the top, where a dumbbell gets easy.",
            "Put a preacher bench in front of a low cable and hold the bar.",
            "Curl it up with your arms resting on the pad.",
            "Lower it until your arms are nearly straight."
        ),
        "Machine Bicep Curl" to g(
            "Line your elbows up with the machine's pivot.",
            "Sit with your arms on the pad and grip the handles.",
            "Curl the handles up.",
            "Lower them slowly."
        ),
        "Diamond Push-Up" to g(
            "If your wrists complain, put your hands just inside shoulder width instead.",
            "Start in a push-up with your hands together under your chest, thumbs and fingers touching.",
            "Lower your chest to your hands, elbows close to your body.",
            "Push back up."
        ),
        "Bodyweight Tricep Extension" to g(
            "The lower the bar, the harder it is. Start on a high bar or a bench.",
            "Hold a bar at hip height and lean forward into a push-up position.",
            "Bend only your elbows so your forehead goes towards the bar.",
            "Straighten your arms to push back up."
        ),
        "Ring Dips" to g(
            "Much harder than bar dips because the rings move: master bar dips first.",
            "Push up to straight arms on the rings, keeping them close to your body.",
            "Lower until your shoulders are just below your elbows.",
            "Press back up and turn the rings out at the top."
        ),
        "V-Bar Pushdown" to g(
            "Elbows pinned to your sides; only your forearms move.",
            "Hold the V-bar on a high cable.",
            "Push it down until your arms are straight.",
            "Let it come up to just above your elbows."
        ),
        "Reverse-Grip Pushdown" to g(
            "Use a lighter weight than a normal pushdown.",
            "Hold the bar on a high cable with palms up.",
            "Push it down until your arms are straight.",
            "Let it up slowly."
        ),
        "Dumbbell Skull Crusher" to g(
            "Palms facing each other is easier on the elbows than with a bar.",
            "Lie on a bench with the dumbbells over your chest, palms facing each other.",
            "Bend your elbows to lower them beside your head.",
            "Straighten your arms."
        ),
        "Close-Grip Dumbbell Press" to g(
            "Press the dumbbells together the whole time.",
            "Lie on a bench holding two dumbbells together over your chest.",
            "Lower them to your chest, elbows close to your body.",
            "Press back up."
        ),
        "Tate Press" to g(
            "Elbows point out and the dumbbells touch your chest: it's an odd one, go light.",
            "Lie on a bench with the dumbbells over your chest, ends touching.",
            "Bend your elbows out to the sides so the dumbbells come down to your chest.",
            "Straighten your arms."
        ),
        "Single-Arm Overhead Extension" to g(
            "Hold your elbow with your free hand to keep it pointing up.",
            "Hold a dumbbell overhead with one hand.",
            "Bend your elbow to lower it behind your head.",
            "Straighten your arm, then switch sides."
        ),
        "Bodyweight Squat" to g(
            "Go as deep as you can with your heels down and your chest up.",
            "Stand with your feet shoulder-width, toes slightly out.",
            "Sit down between your heels, arms forward for balance.",
            "Stand back up."
        ),
        "Jump Squat" to g(
            "Land softly and go straight into the next squat.",
            "Stand with your feet shoulder-width.",
            "Squat down, then jump as high as you can.",
            "Land softly with bent knees."
        ),
        "Pistol Squat" to g(
            "Holding a small weight out in front actually makes balancing easier.",
            "Stand on one leg with the other leg out in front.",
            "Squat down on the standing leg as low as you can.",
            "Stand back up without putting the other foot down."
        ),
        "Shrimp Squat" to g(
            "Start by touching your back knee down to a cushion.",
            "Stand on one leg and hold the other foot behind you.",
            "Bend your standing leg until your back knee touches the floor.",
            "Stand back up."
        ),
        "Cossack Squat" to g(
            "Keep the straight leg's toes pointing up and both heels down.",
            "Stand with your feet very wide.",
            "Squat down onto one leg while the other stays straight.",
            "Push back up and go to the other side."
        ),
        "Wall Sit" to g(
            "Knees over your ankles and thighs level with the floor.",
            "Lean your back against a wall.",
            "Slide down until your thighs are level with the floor.",
            "Hold."
        ),
        "Jumping Lunge" to g(
            "Land softly with your front knee over your foot.",
            "Start in a lunge.",
            "Jump up and swap legs in the air.",
            "Land in a lunge with the other leg forward."
        ),
        "Box Jump" to g(
            "Step down, don't jump down: it's easier on your knees and Achilles.",
            "Stand facing a sturdy box.",
            "Swing your arms back, bend your knees and jump onto the box.",
            "Land softly in a squat and stand up.",
            "Step back down."
        ),
        "Box Squat" to g(
            "Sit back to the box and pause; don't bounce off it.",
            "Set a box behind you at about knee height.",
            "With the bar on your back, sit back until you're sitting on the box.",
            "Pause, then stand up."
        ),
        "Overhead Squat" to g(
            "Push the bar up hard the whole time; start with an empty bar or a stick.",
            "Hold the bar overhead with a wide grip and straight arms.",
            "Squat down, keeping the bar over the middle of your feet.",
            "Stand back up."
        ),
        "Dumbbell Squat" to g(
            "Let the dumbbells hang at your sides and keep your chest up.",
            "Stand with a dumbbell in each hand at your sides.",
            "Squat down until your thighs are level with the floor.",
            "Stand back up."
        ),
        "Kettlebell Front Squat" to g(
            "The bells rest on your forearms; keep your elbows in close.",
            "Hold two kettlebells at your shoulders.",
            "Squat down as deep as you can with your chest up.",
            "Stand back up."
        ),
        "Barbell Lunge" to g(
            "Take a long enough step that your front knee stays over your foot.",
            "Stand with the bar on your upper back.",
            "Step forward and lower your back knee towards the floor.",
            "Push back to standing and switch legs."
        ),
        "Dumbbell Lunge" to g(
            "Stand tall: a straight upper body keeps the work in the legs.",
            "Stand with a dumbbell in each hand.",
            "Step forward and lower your back knee towards the floor.",
            "Push back to standing and switch legs."
        ),
        "Single-Leg Leg Extension" to g(
            "Good for evening out a weaker leg.",
            "Sit in the leg extension with one leg under the pad.",
            "Straighten your leg.",
            "Lower it slowly, then switch legs."
        ),
        "Kettlebell Thruster" to g(
            "Use the drive out of the squat to push the bells up.",
            "Hold the kettlebells at your shoulders.",
            "Squat down.",
            "Stand up fast and press them overhead in one move.",
            "Bring them back to your shoulders for the next squat."
        ),
        "Sled Push" to g(
            "Arms straight or bent, lean in at about 45° and take short, fast steps.",
            "Hold the sled's poles with your body leaning forward.",
            "Drive it forward with short, powerful steps.",
            "Push for the distance, rest and repeat."
        ),
        "Power Clean" to g(
            "The bar stays close: think 'jump and shrug', not 'pull with the arms'.",
            "Stand with the bar over your mid-foot and grip it just outside your legs.",
            "Lift it to your knees with a flat back.",
            "Jump with straight arms, shrug and pull yourself under the bar.",
            "Catch it on the front of your shoulders in a quarter squat."
        ),
        "Hang Clean" to g(
            "Starting from the hips makes it easier to learn than a clean from the floor.",
            "Stand holding the bar at your hips.",
            "Slide it down to just above your knees.",
            "Jump and shrug, then pull yourself under the bar.",
            "Catch it on the front of your shoulders."
        ),
        "Clean and Jerk" to g(
            "Learn it with a coach if you can: it's a skill as much as strength.",
            "Clean the bar from the floor to your shoulders.",
            "Dip and drive the bar up.",
            "Step under it with straight arms, one foot forward.",
            "Bring your feet together and lower the bar."
        ),
        "Snatch" to g(
            "Start with a PVC pipe or empty bar: positions first, weight later.",
            "Grip the bar very wide with the bar over your mid-foot.",
            "Lift it past your knees, then jump and shrug.",
            "Pull yourself under the bar and catch it overhead in a squat.",
            "Stand up with the bar overhead."
        ),
        "Single-Leg Romanian Deadlift" to g(
            "Point your back leg's toes at the floor to keep your hips square.",
            "Stand on one leg with a weight in the opposite hand.",
            "Hinge forward as your back leg lifts behind you.",
            "Go down until you feel the stretch, then stand back up."
        ),
        "Stability Ball Leg Curl" to g(
            "Keep your hips up the whole time; it's harder than it looks.",
            "Lie on your back with your heels on a stability ball.",
            "Lift your hips off the floor.",
            "Pull the ball towards you by bending your knees.",
            "Roll it back out without dropping your hips."
        ),
        "Sliding Leg Curl" to g(
            "Use socks on a smooth floor or a towel; go slowly on the way out.",
            "Lie on your back with your heels on sliders or a towel.",
            "Lift your hips.",
            "Slide your heels in towards you, then slowly back out."
        ),
        "Kettlebell Clean" to g(
            "Keep the bell close so it rolls onto your forearm instead of banging it.",
            "Swing the kettlebell back between your legs.",
            "Drive your hips forward and pull the bell up close to your body.",
            "Let it roll onto your forearm at your shoulder.",
            "Drop it back into the swing for the next rep."
        ),
        "Kettlebell Swing" to g(
            "It's a hip snap, not a squat and not an arm raise: the bell floats up on its own.",
            "Stand with your feet a bit wider than your shoulders, bell in both hands.",
            "Hinge and swing the bell back between your legs.",
            "Snap your hips forward to swing it to chest height.",
            "Let it fall back and go straight into the next swing."
        ),
        "Bodyweight Glute Bridge" to g(
            "Squeeze your glutes at the top for a second.",
            "Lie on your back, knees bent, feet flat.",
            "Push through your heels to lift your hips.",
            "Lower back down."
        ),
        "Single-Leg Glute Bridge" to g(
            "Keep your hips level: don't let one side drop.",
            "Lie on your back with one foot on the floor and the other leg in the air.",
            "Push through the heel on the floor to lift your hips.",
            "Lower back down, then switch legs."
        ),
        "Single-Leg Hip Thrust" to g(
            "Tuck your chin and look forward, not at the ceiling.",
            "Sit with your upper back against a bench and one foot on the floor.",
            "Lift the other leg and push through the planted heel.",
            "Raise your hips until your body is straight, then lower."
        ),
        "Donkey Kick" to g(
            "Keep your knee bent and push your heel up to the ceiling.",
            "Get on your hands and knees.",
            "Lift one leg up behind you, knee bent, until your thigh is level.",
            "Lower it and repeat, then switch legs."
        ),
        "Fire Hydrant" to g(
            "Don't lean away: keep your weight centred over your hands.",
            "Get on your hands and knees.",
            "Lift one knee out to the side, keeping it bent.",
            "Lower it and repeat, then switch legs."
        ),
        "Curtsy Lunge" to g(
            "Step back and across, like a curtsy; keep your front knee over your foot.",
            "Stand with a dumbbell in each hand.",
            "Step one leg back and behind the other.",
            "Lower your back knee towards the floor.",
            "Push back to standing and switch legs."
        ),
        "Sumo Squat" to g(
            "Wide feet, toes out, and push your knees out as you go down.",
            "Stand wide holding a dumbbell between your legs.",
            "Squat down with your chest up.",
            "Stand back up."
        ),
        "Reverse Hyperextension" to g(
            "Lift with your glutes, not by swinging your legs.",
            "Lie face down on the pad with your hips at the edge.",
            "Lift your legs up behind you until they're in line with your body.",
            "Lower them slowly."
        ),
        "Bodyweight Calf Raise" to g(
            "Do them on a step so your heels can drop below your toes.",
            "Stand on the edge of a step, holding something for balance.",
            "Rise as high as you can on your toes.",
            "Lower your heels below the step."
        ),
        "Dumbbell Calf Raise" to g(
            "Pause at the bottom: bouncing takes the work away from the calves.",
            "Stand holding a dumbbell in each hand.",
            "Rise as high as you can on your toes.",
            "Lower your heels slowly."
        ),
        "Barbell Calf Raise" to g(
            "Stand on a plate to get a deeper stretch.",
            "Stand with a barbell on your back.",
            "Rise as high as you can on your toes.",
            "Lower your heels slowly."
        ),
        "Donkey Calf Raise" to g(
            "Bending at the hips stretches the calves further.",
            "Bend forward at the hips with your toes on a block, on a donkey calf machine.",
            "Rise as high as you can on your toes.",
            "Lower your heels below the block."
        ),
        "Seated Dumbbell Calf Raise" to g(
            "Seated works the lower calf muscle; do both seated and standing.",
            "Sit with the balls of your feet on a plate and a dumbbell on your knee.",
            "Raise your heels as high as you can.",
            "Lower them slowly."
        ),
        "Pogo Hops" to g(
            "Stiff ankles and quick contacts: spend as little time on the ground as possible.",
            "Stand tall on the balls of your feet.",
            "Hop up and down using mostly your ankles.",
            "Keep a quick, steady rhythm."
        ),
        "Crunch" to g(
            "Lift your shoulders, not your head: keep a fist's space under your chin.",
            "Lie on your back with your knees bent.",
            "Curl your shoulders off the floor.",
            "Lower back down."
        ),
        "Sit-Up" to g(
            "Hook your feet under something for a fuller sit-up.",
            "Lie on your back with your knees bent.",
            "Sit up all the way.",
            "Lower back down slowly."
        ),
        "Bicycle Crunch" to g(
            "Slow is better: twist your shoulder, not just your elbow.",
            "Lie on your back with your hands by your head and your knees up.",
            "Bring one knee in as you twist the opposite shoulder towards it.",
            "Switch sides in a pedalling motion."
        ),
        "Reverse Crunch" to g(
            "Curl your hips off the floor; don't just swing your legs.",
            "Lie on your back with your knees bent up over your hips.",
            "Curl your hips up off the floor, knees towards your chest.",
            "Lower them back slowly."
        ),
        "Lying Leg Raise" to g(
            "Press your lower back into the floor; bend your knees if it lifts.",
            "Lie on your back with your legs straight.",
            "Raise your legs until they point at the ceiling.",
            "Lower them slowly without touching the floor."
        ),
        "Toes-to-Bar" to g(
            "Pull the bar down towards your hips as you lift your legs.",
            "Hang from a bar.",
            "Lift your legs up until your toes touch the bar.",
            "Lower them with control."
        ),
        "L-Sit" to g(
            "Start with bent knees (a tuck sit) and straighten one leg at a time as you get stronger.",
            "Put your hands on parallel bars, the floor or two boxes.",
            "Push down to lift your body.",
            "Hold your legs straight out in front, level with the floor."
        ),
        "Dragon Flag" to g(
            "Lower only as far as you can keep your body straight.",
            "Lie on a bench and grip it behind your head.",
            "Lift your whole body up so only your upper back touches.",
            "Lower it slowly in one straight line."
        ),
        "V-Up" to g(
            "Reach for your toes and meet in the middle.",
            "Lie on your back with your arms over your head.",
            "Lift your legs and upper body at the same time to touch your toes.",
            "Lower back down slowly."
        ),
        "Dead Bug" to g(
            "Breathe out as you reach and keep your lower back on the floor.",
            "Lie on your back with your arms up and your knees bent over your hips.",
            "Lower one arm and the opposite leg towards the floor.",
            "Bring them back and switch sides."
        ),
        "Bird Dog" to g(
            "Move slowly enough that a cup on your back wouldn't fall.",
            "Get on your hands and knees.",
            "Reach one arm forward and the opposite leg back.",
            "Bring them back and switch sides."
        ),
        "Mountain Climbers" to g(
            "Keep your hips level with your shoulders.",
            "Start in a push-up position.",
            "Drive one knee towards your chest, then switch legs quickly.",
            "Keep going at a steady pace."
        ),
        "Flutter Kicks" to g(
            "Small kicks; your lower back stays on the floor.",
            "Lie on your back with your legs straight and a little off the floor.",
            "Kick your legs up and down in small, quick movements.",
            "Keep going for the time."
        ),
        "Heel Touches" to g(
            "Keep your shoulders off the floor the whole set.",
            "Lie on your back with your knees bent and your shoulders lifted.",
            "Reach to one side to touch your heel.",
            "Reach to the other side."
        ),
        "Oblique Crunch" to g(
            "Curl your ribs towards your hip on that side.",
            "Lie on your side with your knees bent and your hand by your head.",
            "Crunch your shoulder up towards your hip.",
            "Lower back down, then switch sides."
        ),
        "Stability Ball Crunch" to g(
            "Let your back curl over the ball at the bottom for a bigger range.",
            "Sit on a stability ball and walk forward until your lower back rests on it.",
            "Crunch your shoulders up.",
            "Lower back over the ball."
        ),
        "Stability Ball Knee Tuck" to g(
            "Keep your shoulders over your hands.",
            "Start in a push-up with your shins on a stability ball.",
            "Pull your knees in towards your chest, rolling the ball.",
            "Roll it back out."
        ),
        "Bear Crawl" to g(
            "Knees just off the floor and your back flat as a table.",
            "Get on your hands and feet with your knees just off the floor.",
            "Move forward with the opposite hand and foot together.",
            "Keep crawling forward and back."
        ),
        "Cable Lift" to g(
            "Turn from your hips and keep your arms nearly straight.",
            "Stand side-on to a low cable holding the handle with both hands.",
            "Pull it up and across your body to above the far shoulder.",
            "Return slowly, then switch sides."
        ),
        "Dumbbell Side Bend" to g(
            "Hold the weight in one hand only; two cancel each other out.",
            "Stand with a dumbbell in one hand.",
            "Lean to that side.",
            "Come back up past the middle, then switch sides after the set."
        ),
        "Landmine Rotation" to g(
            "Your arms stay long; the turn comes from your hips and core.",
            "Hold the end of a landmine bar overhead with both hands.",
            "Lower it in an arc to one hip.",
            "Bring it back over your head and down to the other hip."
        ),
        "Medicine Ball Slam" to g(
            "Use a slam ball, not a bouncy one, and hinge to pick it up.",
            "Hold the ball overhead.",
            "Slam it down in front of you as hard as you can.",
            "Pick it up and repeat."
        ),
        "Kettlebell Windmill" to g(
            "Look at the bell the whole time and keep that arm straight up.",
            "Press a kettlebell overhead with one hand, feet turned away from that arm.",
            "Push your hip out and slide your free hand down your leg.",
            "Stand back up, keeping the bell overhead."
        ),
        "Wrist Roller" to g(
            "Roll up and also roll down slowly: both directions count.",
            "Hold a wrist roller out in front with straight arms.",
            "Roll the weight up by turning your wrists.",
            "Roll it back down slowly."
        ),
        "Plate Pinch" to g(
            "Smooth sides out: pinch with your fingers and thumb.",
            "Pinch two plates together, smooth sides out.",
            "Lift them and hold at your side.",
            "Put them down before your grip opens, then switch hands."
        ),
        "Behind-the-Back Wrist Curl" to g(
            "Let the bar roll down to your fingertips for a bigger range.",
            "Hold a barbell behind your back, palms facing back.",
            "Curl your wrists up.",
            "Lower the bar slowly."
        ),
        "Suitcase Carry" to g(
            "Stand tall; don't lean away from the weight.",
            "Pick up one heavy dumbbell or kettlebell.",
            "Walk tall for the time or distance.",
            "Switch hands and walk back."
        ),
        "Burpee" to g(
            "Step back instead of jumping if you need to: keep moving.",
            "Squat down and put your hands on the floor.",
            "Jump your feet back to a push-up position.",
            "Jump your feet back in.",
            "Jump up with your arms overhead."
        ),
        "Jumping Jacks" to g(
            "Land on the balls of your feet.",
            "Stand with your feet together and arms at your sides.",
            "Jump your feet out as your arms go overhead.",
            "Jump back in."
        ),
        "High Knees" to g(
            "Knees to hip height and stay on the balls of your feet.",
            "Run in place, driving your knees up to hip height.",
            "Pump your arms.",
            "Keep a fast pace."
        ),
        "Battle Ropes" to g(
            "Stay in a half squat and keep the waves going all the way to the anchor.",
            "Hold the end of each rope and stand in a half squat.",
            "Whip the ropes up and down fast, one arm after the other.",
            "Keep going for the time."
        ),
        "Sprints" to g(
            "Warm up well first: a sprint is all-out, but start each one at 80% for the first few weeks.",
            "Warm up with an easy jog.",
            "Run as fast as you can for the distance.",
            "Walk back to recover, then repeat."
        ),
        "Hiking" to g(
            "Short steps on the climbs; go at a pace you can still talk.",
            "Start at an easy pace.",
            "Keep a steady pace on the climbs.",
            "Take your time on the way down."
        ),
        "Swimming" to g(
            "Mix in some slow laps to rest instead of stopping at the wall.",
            "Warm up with a few easy laps.",
            "Swim at a steady pace.",
            "Finish with a few easy laps."
        ),
        "Recumbent Bike" to g(
            "Set the seat so your knee is just slightly bent at the far point.",
            "Sit with your back against the seat and your feet on the pedals.",
            "Pedal at a steady pace.",
            "Slow down for the last few minutes."
        ),
        "Ski Erg" to g(
            "Hinge at the hips: the pull comes from your whole body, not just your arms.",
            "Stand facing the machine and hold the handles overhead.",
            "Pull them down by bending your hips and knees.",
            "Let them back up and repeat."
        ),
        "Assault Bike" to g(
            "Push and pull the handles: your arms make it much harder.",
            "Sit on the bike with your hands on the handles.",
            "Pedal and push-pull the handles together.",
            "Keep a steady pace, or go all-out in short bursts."
        )
    )
}
