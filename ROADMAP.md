# GymTracker roadmap

Goal: make GymTracker comparable to Strong / Hevy / JEFIT while staying offline-first.

**How work happens**
- One branch per phase (`phase-N-name`). Every step: build + unit tests pass → commit → tick the box here in the same commit.
- At the end of a phase the owner installs the build and tests it on a phone, then the branch is merged to `master`.
- Keep **Next up** current — it is where a new session starts.

## Next up

Phase 4 in progress on branch `phase-4-routines`. Steps 1 (own programs) and 2 (targets) are committed.
Step 2 still needs an emulator check of the target dialog (Profile > Edit program > a day > tap an exercise) and of a new workout using a target; unit tests cover the logic.
Next: **Phase 4, step 3**: built-in percentage programs (5/3/1, GZCLP, nSuns) with a training max per lift.

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

## Phase 4 — Routines & programs (branch `phase-4-routines`)
- [x] Your own programs (Room v3, auto-migrated): Profile lists built-in + yours; "Customize" copies a built-in, "New program" starts blank; the editor sets name, workouts per week and days (add, rename, reorder, remove) and each day's exercise list. Built-in programs stay read-only presets
- [x] Days are identified by title, shared across programs. Renaming a day moves its history unless another of *your* programs uses the old title, and copies its exercise list if any other program (built-ins included) still uses it
- [x] Per-exercise targets on a day (sets × rep range; Room v4 auto-migrated, backup `splitTargets`): new workouts lay out that many sets at the top of the range, the workout card shows "Plan 3 × 8–12", coaching moves up at the top and down below the bottom of the range (defaults unchanged without a target). Rest stays per exercise as before
- [ ] Built-in programs: 5/3/1, GZCLP, nSuns (percentage-based, with training max)

## Phase 5 — Gym tools
- [ ] Plate calculator (bar weight, available plates, kg/lb)
- [ ] Warm-up set generator
- [ ] Optional RPE/RIR per set; feed it into weight suggestions

## Phase 6 — Data in/out
- [ ] CSV export
- [ ] Import from Strong and Hevy CSV (map exercise names to the library)
- [ ] Auto-backup to a user-chosen folder (SAF), with rotation
- [ ] Health Connect: write workouts + body weight, read body weight

## Phase 7 — Stats & polish
- [ ] Weekly sets per muscle vs target range, body heat map
- [ ] Yearly calendar heat map, streaks
- [ ] Body measurements; progress-photo side-by-side
- [ ] Exercise instructions (cues + primary/secondary muscles)
- [ ] PR celebration on finish; per-workout notes

## Phase 8 — Reach
- [ ] Move UI strings to `strings.xml` (localization-ready)
- [ ] Wear OS companion module (current set, log, rest timer)

---

## Decisions log
- 2026-10-23: `applicationId` changed from `com.example.gymtracker`. Android sees this as a new app: export a backup from the old app and import it into the new one. The Kotlin `namespace`/package stays `com.example.gymtracker` for now (renaming it is cosmetic and touches every file).
- 2026-10-23: Room holds history, body weight, templates and per-exercise notes/rest. Settings, profile, active session and widget settings stay in the JSON file: they are small and written on every tap. `LocalStorage` stays synchronous via an in-memory copy, so the UI didn't need rewriting. Set weight/reps stay strings in Room for now (exact round trip of half-typed sets); Phase 3 revisits this with exercise types.
- 2026-10-23: The exercise detail screen scans the in-memory history instead of querying Room. History is already in memory and a scan of a few hundred workouts is instant; a Room query would add async loading for no gain.
- 2026-10-23: Units are display-only; the database and backups stay in kg. The current unit is Compose snapshot state (`Units.current`) set from settings on first storage access, so screens redraw on change and widgets/notifications use it without a Context-aware formatter.
