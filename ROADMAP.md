# GymTracker roadmap

Goal: make GymTracker comparable to Strong / Hevy / JEFIT while staying offline-first.

**How work happens**
- One branch per phase (`phase-N-name`). Every step: build + unit tests pass → commit → tick the box here in the same commit.
- At the end of a phase the owner installs the build and tests it on a phone, then the branch is merged to `master`.
- Keep **Next up** current — it is where a new session starts.

## Next up

Phases 0–6 are merged into `master` (v3.6.0). **Coaching v2** (branch `coaching-v2`) is done and waits for the owner to try it on a phone before merging. Then: the owner wants to rework the start screen and the split picker (they'll describe it). After that: **Phase 7, step 1**: CSV export (branch `phase-7-data`).

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
