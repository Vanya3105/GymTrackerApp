# GymTracker roadmap

Goal: make GymTracker comparable to Strong / Hevy / JEFIT while staying offline-first.

**How work happens**
- One branch per phase (`phase-N-name`). Every step: build + unit tests pass → commit → tick the box here in the same commit.
- At the end of a phase the owner installs the build and tests it on a phone, then the branch is merged to `master`.
- Keep **Next up** current — it is where a new session starts.

## Next up

Phase 0 is done and waiting for an on-device check, then merge to `master`.
Then start **Phase 1, step 1**: add Room + KSP and define the entities.

---

## Phase 0 — Setup ✅ (branch `phase-0-setup`)
- [x] `applicationId` → `com.vanyaevtushenko.gymtracker`, version 3.0.0 (code 30)
- [x] Release signing from git-ignored `keystore.properties`, debug-key fallback
- [x] JSON encode/decode moved to `StoreCodec` with tests (round trip and legacy formats)
- [x] Fix: an unreadable store file is copied aside instead of being silently overwritten
- [x] `ROADMAP.md` + `CLAUDE.md`

## Phase 1 — Room database (branch `phase-1-room`)
Riskiest phase: existing user data must migrate without loss.
- [ ] Add Room + KSP. Entities: `ExerciseDef` (stable id, name, muscle, equipment, type, isCustom), `Workout`, `WorkoutExercise`, `WorkoutSet`, `BodyWeight`, `SplitTemplate` + items, `ExerciseNote`/rest defaults
- [ ] Seed `ExerciseDef` from `ExerciseDatabase`. History references exercises by id, not name
- [ ] One-time migration JSON → Room using `StoreCodec`; keep the old JSON file as `*.migrated` backup; idempotent
- [ ] Repository layer replacing `LocalStorage` history/profile calls; settings, active session and widget settings may stay JSON or move to DataStore (decide in-phase)
- [ ] Widgets, lock screen and receivers read through the repository (keep the `flush()` contract for receivers)
- [ ] Backup export/import still uses the same JSON format (compatible with old backups)
- [ ] Renaming a custom exercise keeps its history
- [ ] Tests: migration from sample v3 JSON, DAO queries (Robolectric or instrumented)

## Phase 2 — Progress core
- [ ] Exercise detail screen: full history, charts for est. 1RM, best set, volume; rep PRs (1/3/5/8/10)
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
