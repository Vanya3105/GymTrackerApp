# GymTracker roadmap

Goal: make GymTracker comparable to Strong / Hevy / JEFIT while staying offline-first.

**How work happens**
- One branch per phase (`phase-N-name`). Every step: build + unit tests pass → commit → tick the box here in the same commit.
- At the end of a phase the owner installs the build and tests it on a phone, then the branch is merged to `master`.
- Keep **Next up** current — it is where a new session starts.

## Next up

Phase 1 is done on branch `phase-1-room`, waiting for an on-device check by the owner, then merge to `master`.
Then start **Phase 2, step 1**: the exercise detail screen. Use `GymDao` queries for per-exercise history instead of scanning all workouts.

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

## Phase 2 — Progress core
- [ ] Exercise detail screen: full history, charts for est. 1RM, best set, volume; rep PRs (1/3/5/8/10)
- [ ] Rename an exercise from its detail screen (uses `LocalStorage.renameExercise`)
- [ ] Open it from history, the live workout and the progress screen
- [ ] Units: kg/lb setting; store kg, convert for display and input; lb-friendly weight steps

## Phase 3 — Exercise types
- [ ] Types: weight×reps, bodyweight reps, weighted bodyweight, assisted (negative), duration, distance+duration
- [ ] Logger UI per type, widgets, stats/volume rules, PR rules, suggestions per type
- [ ] Library exercises get correct default types

## Phase 4 — Routines & programs
- [ ] Create, rename, reorder and delete your own splits and programs
- [ ] Per-exercise targets (sets × rep range, rest)
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
