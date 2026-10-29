# GymTracker roadmap

Goal: make GymTracker comparable to Strong / Hevy / JEFIT while staying offline-first.

**How work happens**
- One branch per phase (`phase-N-name`). Every step: build + unit tests pass → commit → tick the box here in the same commit.
- At the end of a phase the owner installs the build and tests it on a phone, then the branch is merged to `master`.
- Keep **Next up** current — it is where a new session starts.

## Next up

Everything through **Design polish 2** is merged into `master` (2026-10-29, at the owner's request, before a phone test): performance pass 2, Watch v2 and Design polish 2. Still to try on the phone and Galaxy Watch (reinstall both apps, the watch protocol changed): the set keypad (Next, −/+, Done, back closes it), "Use …" and "Go lighter for the rest", swipe-left on a weigh-in, the body-weight chart, Profile's editors, light mode; on the watch: start from the list and the tile, the bezel, undo, the overview, finish. Fixes from that test go on a new branch. Then: **Phase 7, step 1**: CSV export (branch `phase-7-data`).

---

## Phase 0 — Setup ✅ (branch `phase-0-setup`)
- [x] `applicationId` → `com.vanyaevtushenko.gymtracker`, version 3.0.0 (code 30)
- [x] Release signing from git-ignored `keystore.properties`, debug-key fallback
- [x] JSON encode/decode moved to `StoreCodec` with tests (round trip and legacy formats)
- [x] Fix: an unreadable store file is copied aside instead of being silently overwritten
- [x] `ROADMAP.md` + `CLAUDE.md`

## Phase 1 — Room database ✅ (branch `phase-1-room`)
- [x] Room 2.8 + KSP. Entities: `exercises` (stable id, case-insensitive unique name, muscle, note, default rest), `workouts`, `workout_exercises`, `workout_sets`, `body_weight`, `split_template_items`. Schema v1 exported to `app/schemas/`
- [x] History and templates reference exercises by id. Library exercises take the library spelling and muscle
- [x] One-time JSON → Room migration on first launch. The original is kept as `gym_tracker_store_v2.json.pre-room`. Merges over Room, so a retry after a crash loses nothing
- [x] `LocalStorage` stays the only API (no call-site changes). Reads from memory; Room and JSON writes are ordered on one storage thread
- [x] Widgets, lock screen and receivers unchanged; `flush()` now waits for Room writes too
- [x] Backup export/import keeps the old single-file format (old ↔ new builds compatible)
- [x] `LocalStorage.renameExercise` updates history, templates, notes and the active session (UI comes in Phase 2)
- [x] Tests: `RoomStoreTest`, `StorageMigrationTest` (Robolectric, real v3 fixture), and mutation-checked
- [x] Verified on emulator: migration, debug + R8 release builds, finishing a workout then force-stopping

## Phase 2 — Progress core ✅ (branch `phase-2-progress`)
- [x] Exercise detail screen (`ExerciseDetailScreen.kt`, stats in `ExerciseStats.kt`): every session, chart of est. 1RM / top weight / volume on a time axis (3M / 1Y / All, tap or drag to inspect), rep records for 1/3/5/8/10/12, setup note
- [x] Rename from the detail screen (hidden while editing a past workout, which would write the old name back)
- [x] Opens from Progress → Best lifts (now "See all") and from a workout's "Notes & history" sheet → "Full history" (live or past workouts from History)
- [x] Estimated 1RMs are rounded to 0.5 kg once, everywhere (tiles, chart, records, live "new best" check), so equal lifts never count as a record
- [x] Units (`Units.kt`): kg/lb in Profile and onboarding. Everything stays stored in kg; lb is display and input only (4-decimal kg so 135 lb reads back as 135). Coaching steps and rounds in the display unit; lb step presets 1/2.5/5/10. Widgets, notifications and the share image follow the unit
- [x] +/− buttons snap an off-grid value to the next step (88.2 lb → 90 → 95), so switching units doesn't leave odd numbers
- [x] Body-weight entries stored to 0.01 kg (was 0.1) so pound entries read back exactly

## Phase 3 — Exercise types ✅ (branch `phase-3-exercise-types`)
- [x] Data layer: `ExerciseType` (weight & reps, bodyweight, weighted bodyweight, assisted, time, distance & time) stored on the exercise; `seconds` and `distanceM` on sets. Room v2 with `MIGRATION_1_2`, tested by `DatabaseMigrationTest` against the committed v1 schema
- [x] Migration never hides logged data: library types are applied only where lossless (`typeForExistingExercise`); everything else stays weight & reps until changed by hand. Same rule for old backups/stores (no `exerciseTypes` section)
- [x] Per-type rules: what counts as a work set, volume (weight & reps and added weight only), records and PRs (most reps, least assistance, longest time, longest distance), no weight suggestions for non-weight types
- [x] Library: types for bodyweight exercises, new Assisted Pull-Up/Dip, Inverted Row, holds, and a Cardio group (running, rowing, bike, …)
- [x] Logging UI per type: set rows and headers per type (`DurationStepper` types like a microwave timer, `DistanceStepper` in km/mi), drop sets only for loaded lifts, new exercises start from your last session of it, custom exercises ask for a type, "Logged as" in Notes & history changes it
- [x] Results per type: exercise page tiles and chart metrics (reps, assistance, time, distance, pace), rep records only for loaded lifts, Progress lists every type with its own best, widget/lock-screen boxes and +/- follow the type, set-done notification and share image use per-type wording. Zero added weight / zero assistance reads as a bodyweight set

## Phase 4 — Routines & programs ✅ (branch `phase-4-routines`)
- [x] Your own programs (Room v3, auto-migrated): Profile lists built-in + yours; "Customize" copies a built-in, "New program" starts blank; the editor sets name, workouts per week and days (add, rename, reorder, remove) and each day's exercise list. Built-in programs stay read-only presets
- [x] Days are identified by title, shared across programs. Renaming a day moves its history unless another of *your* programs uses the old title, and copies its exercise list if any other program (built-ins included) still uses it
- [x] Per-exercise targets on a day (sets × rep range; Room v4 auto-migrated, backup `splitTargets`): new workouts lay out that many sets at the top of the range, the workout card shows "Plan 3 × 8–12", coaching moves up at the top and down below the bottom of the range (defaults unchanged without a target). Rest stays per exercise as before
- [x] 5/3/1 (`PercentPrograms.kt`, Room v5 for training maxes): four days built around Squat/Bench/Deadlift/Press; warm-ups 40/50/60% + the week's sets rounded down to plates, last set "as many as you can"; week and cycle derived from sessions since the TM was set; +2.5/5 kg (5/10 lb) per cycle. TM is set from the exercise page or Notes & history (prefilled at 90% of best e1RM) and re-lays out an untouched exercise mid-workout
- [x] GZCLP (T1 5×3+ → 6×2+ → 10×1+, T2 3×10 → 3×8 → 3×6; add a step on success, next stage on a miss, restart at 85% after the last stage) and nSuns 5-day (9-set percentage days; secondary lifts use another lift's TM; each "1+" set adds 0–3 plate steps to the TM). All derived from history; GZCLP's T3 lifts are ordinary exercises

## Phase 5 — Gym tools ✅ (branch `phase-5-gym-tools`, v3.5.0)
- [x] Save safety: a restore only accepts a GymTracker backup (any other JSON used to wipe everything) and first keeps a copy of the data it replaces (`before-import-*.json`, last 3); the JSON store keeps its previous version as `.bak` and falls back to it if the file is unreadable
- [x] Plate calculator (`PlateCalculator.kt`): from a set's number menu on bar lifts (barbell, EZ bar, Smith machine, and custom exercises whose equipment is unknown). Edits the set's weight live, draws one side of the bar, lists plates per side. Exact search, not greedy, so gaps in the plate set don't hide loads; if the weight can't be made it offers the nearest lighter/heavier load. Bar (or no bar) and owned plates saved per unit in the JSON store (`plateSetup`), so they travel in backups
- [x] Brand: new launcher icon (vector adaptive icon + monochrome for themed icons; bitmaps only for Android 7) and a launch screen (core-splashscreen): dark brand background, the arc draws in and the dumbbell settles, then the logo grows and fades into the app. Held at least 850 ms on Android 12+ so the animation isn't cut off
- Warm-up set generator and RPE/RIR moved to Phase 8 (owner chose watch + Health Connect first)

## Phase 6 — Watch & Health Connect ✅ (branch `phase-6-watch-health`, v3.6.0)
Target: Samsung Galaxy Watch 4 or newer (Wear OS 3+). The watch is a remote for the phone's workout; the phone stays the single source of truth.
- [x] Health Connect setup: Settings → "Health Connect" row (connect, see what's shared, disconnect); permission rationale screen with the privacy text Health Connect requires; works without Health Connect installed (row hidden or "Install" link)
- [x] Write finished workouts as strength-training sessions (title, start/end, the work sets as notes: Health Connect segments need per-exercise times the app doesn't record). Existing history is shared once on connecting. `clientRecordId` = workout id, so editing a past workout updates it and deleting it deletes it
- [x] Body weight both ways: write weights logged in the app; read weigh-ins from Samsung Health / a smart scale into the weight log (no duplicates of our own records)
- [x] Heart rate per workout: read the heart rate the watch recorded during a workout's time window; show average/max on the History card. Read on demand, not stored (Samsung Health syncs to Health Connect with a delay, so it can appear later). Checked on the emulator except heart rate, which needs the owner's watch
- [x] `:wear` module (Compose for Wear OS, minSdk 30, same `applicationId` and signing key so the Data Layer pairs them). `:watchlink` holds the protocol both sides use (`WatchState` JSON, `WatchAction`, paths)
- [x] Phone → watch: live session state, formatted like the widget (exercise, set, the two values with labels, sets left, rest end and length), sent over the Data Layer whenever it changes (`WatchSync.publish` from `refreshSurfacesNow`)
- [x] Watch → phone: −/+ on both values, set done, +15 s and skip rest (`WatchListenerService`, also while the phone app is closed), through the widget's `applySetAction`
- [x] Watch UI: current set (scrolls with the bezel), rest countdown ring with +15 s / Skip and a buzz at the end while on screen, messages for no workout / all sets done / phone not connected
- [x] Watch polish: ongoing activity (GymTracker on the watch face during a workout, with the rest counting down; `WorkoutOngoing`), kept current while the app is closed by `PhoneDataService`; a GymTracker tile (current set, rest end time, Open); "Open on phone" when there's no workout or all sets are done; the phone acknowledges every tap so a slow phone shows "Waiting for phone…"; firmer haptic for Set done
- [x] Set screen fits a 40 mm watch without scrolling. Checked with Robolectric screenshots of every screen on 192 dp and 227 dp round displays (`WatchScreenshotTest`, PNGs in `wear/build/screenshots`)
- [x] Tested by the owner on their Galaxy Watch (a Wear emulator pair was skipped: the PC runs out of memory with two emulators)

## Coaching v2 ✅ (branch `coaching-v2`, between phases 6 and 7)
Weight suggestions rewritten from published guidance (all in `Coaching.kt`, tested by `CoachingTest`):
- [x] Double progression: hold a weight until *every* set at it reaches the top of the range (back-off and drop sets don't count), then add weight. Inside the range: "stay and add a rep", showing last time's reps
- [x] Jump size from the estimated 1RM: one step normally; beat the top by 2+ reps on every set and it jumps further, as long as the reps should stay in range and the jump is ≤ ~10% (ACSM: 2–10%). When one step is >10% of the load (light dumbbells), it asks for 2 extra reps first. Weighted bodyweight counts body weight in the load
- [x] Going down: well under the range once (3+ reps short) or under it twice at the same weight → the weight the e1RM says gives mid-range reps (≤15% off); 3 sessions at one weight without more reps → reset ~10%; e1RM falling 3 sessions running → ~10% lighter. Just under once → hold
- [x] Breaks: ≥2 weeks off → repeat before moving up; ≥4 weeks → ease back in at 90%; ≥8 weeks → 80%
- [x] During the workout: first set 2+ reps under the range → lighter for the rest; first set 3+ over → heavier (≤15%); reps crashing later → rest longer or lighter. Losing a rep or two per set is treated as normal fatigue
- [x] Once every set is done the card shows next time's plan (no "Use" button). "Go lighter" rounds down to the step
- [x] Default ranges 6–10 (compound) / 8–12 (isolation), unified (they disagreed before); a day's target overrides; even sets of ≤5 twice running (5×5, 3×3) read as strength work (3–5), not misses
- [x] Uses the same day's history first, any day's when this day has none (was: none)
- [x] Muscle recovery per muscle (Room-free, computed from history): 11 groups instead of 5 (Biceps, Triceps, Forearms split from "Arms"; Quads, Hamstrings, Glutes, Calves from "Legs"; Core added). Each exercise loads its target muscle fully and assisting muscles by half (triceps/front delts on presses, biceps on rows and pulldowns, forearms on deadlifts and hammer curls) or a quarter (grip, traps). Recovery time per muscle (quads/hamstrings/back ~72 h, glutes 60, chest/shoulders 48, triceps 42, biceps/calves 36, forearms/core 30), longer with more sets; sessions close together stack. Home and the widget show the least rested muscles that fit; Progress lists all. Tested by `RecoveryTest`

## Split picker ✅ (branch `split-picker`, after Coaching v2)
- [x] Onboarding and Profile show the current split, a search bar and "Build your own" instead of every split in a list. The search (`SplitPicker.kt`, ranked by `searchSplits` in `SplitLibrary.kt`) opens as a sheet: name, day names, keywords ("ppl", "bro", "531") and "4 days"; filter by days a week; your own splits first with an edit button (Profile)
- [x] Four more built-in splits: Full body 2-day, Bro split, Push / Pull / Legs ×2, PHUL, with starting exercises
- [x] Split builder (`SplitBuilderScreen.kt`): days a week → each day's muscles (quick layouts PPL / Upper-Lower / Full body / One muscle a day, then tap muscles; days name themselves, rename by hand) → review: name, each day's exercises (remove, add from the library). Nothing is saved until "Create split", which makes it the active split
- [x] Days are named like the built-in ones where the muscles match ("Push", "Legs", repeated days "Upper A"/"Upper B"), so they reuse those days' exercises and history; new names get starter exercises picked per muscle (`starterExercises`, a B day gets different ones). Tested by `SplitLibraryTest`

## Design polish 1 ✅ (branch `design-polish`, after the split picker)
- [x] Reordering exercises in a workout: picking one up (grip, or long-press the name) collapses every card to a one-line header with its set count, so cards are easy to move past each other; the held card stays under the finger while the others shrink, and the dropped one stays put while they expand (`DragDropState` scrolls to pin it until the layout stops changing size). "Use last time's numbers", "Skipped" and "Add exercise" step aside while dragging. Haptics: firm on pick-up, tick per swap, soft on drop
- [x] Program editor: days and each day's exercises reorder by drag (grip or long-press) instead of up/down arrows
- [x] Workout: the next set to do (across the workout) has an accent outline; an exercise gets a check badge once all its work sets are done
- [x] Bottom bar: one pill slides to the selected tab with a little overshoot; icons go from outlined to filled with a small bounce. The "workout in progress" bar is hidden on Today, which already has a Resume card
- [x] Split builder: the day count rolls, day names slide when they change, the name field always shows the default name, clearer note for days that already exist; the "Your split" card crossfades on change
- [x] Finish sheet: a check badge pops in (a trophy and "New best!" when records fell, with a confetti burst); the stats count up one after another; a "New bests" list with each record set; volume compared with the last session of the same day
- [x] History: each card lists its exercises (work sets, best set, a trophy on new bests, "+N more"); swipe sideways between weeks and months, which slide in from the matching side; the week header stays put; kinder empty states ("A rest week", "Rest day")
- [x] One rule for the next set everywhere (`nextSetIndex`): the first unfinished set after the last finished one, so a skipped warm-up no longer holds the workout outline, rest bar, rest alert, widget and watch on it. Tested by `NextSetTest`; best set of a session by `BestSetTest`
- [x] Professional pass: Inter as the app font (bundled variable font, `res/font/inter.ttf`, OFL in `licenses/`; display optical size for headings, tabular digits), every Material text slot included; a status-bar scrim on the tabs, exercise page and program editor so content no longer scrolls under the clock
- [x] Progress: Recovery as a two-column grid (percent, bar, weekly sets, last trained) with "How it works" behind a tap; "1 set" not "1 sets"; the body-weight header compares what it shows (profile weight vs the latest weigh-in, "−3.3 lb since Oct 2") instead of the last two weigh-ins
- [x] Workout: coaching advice on a quiet surface with a thin tone-coloured edge; "Use …" hidden when the sets are already at that weight. Today: day rows show their number in the program and a round play button. Profile: white switch thumbs, option chips wrap instead of hiding past the card edge, chevrons on rows that open something. Checked in dark and light

## Watch v2 (branch `watch-v2`, after performance pass 2)
The watch was only a remote for the current set. Now it can run a whole workout:
- [x] Start from the watch: with no workout it lists the program's days, the one that's up next first (`computeWeekSummary`, same pick as Today). The tile shows the up-next day and its chip starts it in one tap. The phone ignores a start while a workout runs or for a day not in the program
- [x] Bezel / crown: turns the outlined value on the set screen (tap the other value to move the outline), and adds or takes off 15 s during a rest. One step per bezel click; crown movement adds up
- [x] Set screen: "Set 2 of 4" for the exercise, a thin arc of the whole workout's progress along the edge
- [x] Overview (tap the exercise name or "Next: …"): elapsed time, sets done, each exercise with its progress (current outlined, done ones ticked), Finish, Undo, Open on phone. Swipe right to go back
- [x] Undo the last ticked set (rest screen and overview): un-ticks the last finished set before the next one, in the previous exercise if needed, and ends its rest
- [x] Finish from the watch, with a confirmation that says how many sets aren't ticked. Saved like Finish → Save on the phone (no photo); the phone's open workout closes itself instead of offering to save it twice. Afterwards a "Workout saved" screen (time, sets, volume, new bests) for 30 minutes, also after finishing on the phone
- [x] `./gradlew assembleRelease` builds both apps again: the watch asks for the notification permission from Compose (as the phone does) instead of `registerForActivityResult`, which lint flagged because Play services pulls in Fragment 1.1
- [x] Protocol stays compatible both ways (new fields are optional; v2 in the JSON). Tests: `WatchSyncTest` (start, finish, undo, shorter rest, progress), `WatchLinkTest`, screenshots of every new screen at 40 and 44+ mm

## Design polish 2 (branch `design-polish-2`, after Watch v2)
From a design review: the app was clean but everything spoke at the same volume.
- [x] Workout: set values are plain numbers; tapping one opens a keypad (`SetKeypad.kt`: digits, −/+ by the step, Next moves weight → reps → next set), like Strong and Hevy. Four round buttons gone from every set row. Typing rules tested by `SetKeypadTest`
- [x] Coaching is one quiet line with "Use …" when it applies; "Go lighter for the rest" moved to the exercise menu
- [x] Accent restraint: orange only for the main action and "now" (Start/Finish/Next, the next set, the rest timer, the selected tab). −/+ buttons, "See all"-style links and hints are neutral; the blue second accent is gone from app screens; selected chips are inverted
- [x] Fewer zeros and boxes: Today's week shows a count and quiet day marks; recovery is one sentence when nothing needs rest; other days are one grouped list; History hides an empty week's zero totals and points to the last workout; Progress recovery is a plain list, not tiles in a card
- [x] Body-weight chart: one readout (tap or drag to inspect), first/last dates only. Weigh-ins delete by swiping left instead of an × on each row
- [x] Profile: Age and Body weight are rows that open an editor; Training split has its own section (no card in a card); Smooth scrolling moved to Advanced
- [x] Own tab icons (`AppIcons.kt`: dumbbell, calendar, chart, person on one grid and stroke, part-filled when selected) and a drawn empty-state scene

## Phase 7 — Data in/out
- [ ] CSV export
- [ ] Import from Strong and Hevy CSV (map exercise names to the library)
- [ ] Auto-backup to a user-chosen folder (SAF), with rotation

## Phase 8 — Gym tools 2
- [ ] Warm-up set generator
- [ ] Optional RPE/RIR per set; feed it into weight suggestions

## Phase 9 — Stats & polish
- [ ] Weekly sets per muscle vs target range, body heat map
- [ ] Yearly calendar heat map, streaks
- [ ] Body measurements; progress-photo side-by-side
- [ ] Exercise instructions (cues + primary/secondary muscles)
- [ ] PR celebration on finish; per-workout notes

## Phase 10 — Reach
- [ ] Move UI strings to `strings.xml` (localization-ready)

---

## Decisions log
- 2026-10-29: Watch v2 keeps the phone as the only copy of the workout: starting, finishing and undo are commands the phone carries out, like the set buttons, so there is still nothing to sync or merge. Finishing on the watch skips the photo and the finish sheet's celebration; the watch shows its own summary instead.
- 2026-10-29: Performance pass 2 (release build, Pixel 7 emulator, profile compiled with `cmd package compile -m speed-profile` before every run, builds alternated). The UI thread was already cheap (≈2–4 ms a frame) and scrolling/month swipes are bound by the emulator's GPU (full-screen fades), so the fixes target the main thread: `pressable` and `enterAnimation` are Modifier.Nodes now (press scale drawn by the click's indication, no per-item state or graphics layer at composition), the widget/lock-screen/watch refresh runs on its own background thread (it took 13–60 ms on the main thread after every edit), tutorial spotlight positions are only tracked while the tips show (they were state writes on every scroll frame), History/finish/workout records use the per-exercise index, calendar keys skip `String.format`, and History photos decode off the main thread. Ticking sets: UI p50 3.0–4.4 → ~2.2 ms, worst frame up to 50 → ≤ 11 ms, worst late frame start 42 → ≤ 7.5 ms. Cold start unchanged (~1 s, including the 550 ms splash hold).
- 2026-10-24: Recovery model: helper muscles count half a set (the fractional counting that fit best in Pelland et al.'s volume meta-regression). Recovery windows follow practitioner summaries of Morán-Navarro 2017 / Brigatto 2019 (bigger muscles and long eccentrics recover slower). The app doesn't know how close to failure sets were, so it assumes hard sets; RPE/RIR (Phase 8) could shorten or lengthen the windows later.
- 2026-10-24: Coaching v2 rules come from: double progression (hold until every set hits the top of the range); ACSM 2009 progression stand (raise load 2–10% once the target is beaten by 1–2 reps); Plotkin et al. 2022 (adding reps and adding load grow muscle about equally, so "add a rep" is real progress); the fatigue meta-analysis summarised by Stronger by Science (to failure, set 2 is ~70% of set 1's reps, set 3 ~55%, so reps falling a little is normal); detraining reviews (little strength lost in 2–3 weeks off, more after ~4); and the common 10% reset after repeated failed sessions (linear-progression programs, Helms' novice rule). Epley for e1RM, as the rest of the app uses. All the cut-offs are rules of thumb, not study results.
- 2026-10-23: `applicationId` changed from `com.example.gymtracker`. Android sees this as a new app: export a backup from the old app and import it into the new one. The Kotlin `namespace`/package stays `com.example.gymtracker` for now (renaming it is cosmetic and touches every file).
- 2026-10-23: Room holds history, body weight, templates and per-exercise notes/rest. Settings, profile, active session and widget settings stay in the JSON file: they are small and written on every tap. `LocalStorage` stays synchronous via an in-memory copy, so the UI didn't need rewriting. Set weight/reps stay strings in Room for now (exact round trip of half-typed sets); Phase 3 revisits this with exercise types.
- 2026-10-23: The exercise detail screen scans the in-memory history instead of querying Room. History is already in memory and a scan of a few hundred workouts is instant; a Room query would add async loading for no gain.
- 2026-10-23: Units are display-only; the database and backups stay in kg. The current unit is Compose snapshot state (`Units.current`) set from settings on first storage access, so screens redraw on change and widgets/notifications use it without a Context-aware formatter.
- 2026-10-24: Percentage programs never store progress: week (5/3/1), stage (GZCLP) and TM increases (5/3/1 cycles, nSuns 1+ sets) are recomputed from history since the TM was set. Setting a TM restarts the count. Program days are linked to their main lifts by day title (`PROGRAM_LIFTS`), so renaming such a day makes it a normal day.
- 2026-10-24: Watch + Health Connect moved ahead of data in/out, at the owner's request. The watch is a remote, not a standalone logger: the phone keeps the only copy of the session, so there's no sync conflict to solve. Heart rate comes from Health Connect (Galaxy Watch → Samsung Health → Health Connect) instead of the watch app measuring it.
- 2026-10-24: `minSdk` 24 → 26 (Android 8) for Health Connect's client library. Health Connect itself needs Android 9+; below that its Profile section says it isn't available. The app wasn't published, so nobody lost it; launcher icon bitmaps went too (adaptive icons cover 26+).
- 2026-10-24: Health Connect writes live in `LocalStorage` (save/edit/delete workout, log/delete weight), so no screen can forget them; they are no-ops unless sharing is on and allowed. Weigh-ins read from Health Connect are remembered by id (`healthImportedWeights`): never written back, and a deleted one isn't re-imported.
- 2026-10-24: Performance pass (release build, Pixel 7 emulator). Cold start 1.35–1.9 s → 0.91–0.97 s; first frame ~0.7–1.3 s → ~0.35–0.45 s, no more "skipped frames" at launch. How: a baseline profile (`app/src/main/baseline-prof.txt`, all app code precompiled at install), a shorter splash (hold 850 → 550 ms, animation 1 s → 0.7 s), set numbers parsed once per `SetEntry` instead of on every read, and single-pass history scans (recovery stops once every muscle group's last session is found; Progress groups history per exercise). `HistoryScanEquivalenceTest` keeps the old scans as references and checks identical results on random histories.
- 2026-10-24: The rest-over buzz on the wrist comes from two places: the watch app buzzes while it's on screen, and otherwise the phone's existing "Rest's over" notification (exact alarm on the phone) is mirrored to the watch. Scheduling it on the watch would need the exact-alarm permission, which Wear OS denies by default.
