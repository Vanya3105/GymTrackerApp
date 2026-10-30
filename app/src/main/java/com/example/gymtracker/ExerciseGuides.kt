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
    ).mapKeys { exerciseKey(it.key) }
}
