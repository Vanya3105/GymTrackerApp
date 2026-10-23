# GymTracker — notes for Claude

**At the start of every session, read `ROADMAP.md` and continue from its "Next up" section.**
When the user says "continue", that is what they mean.

## Workflow
- One branch per roadmap phase (`phase-N-name`); never commit to `master` directly.
- After each step: build + tests must pass, then commit and update `ROADMAP.md` (tick boxes, rewrite "Next up") in the same commit.
- Don't merge a phase into `master` until the user has tested it on a phone.
- Leave unrelated `.idea/` changes out of commits.

## Commands (Git Bash, from repo root)
- Unit tests: `./gradlew testDebugUnitTest`
- Debug APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `./gradlew assembleRelease` (signed with `keystore.properties` if present, else the debug key)

## Code map
- Kotlin package `com.example.gymtracker` (the `applicationId` is `com.vanyaevtushenko.gymtracker`).
- `Engine.kt`: storage (`LocalStorage`, `StoreCodec`), records/1RM, suggestions, rest alarms, dates.
- `Models.kt`: data classes. `ExerciseDatabase.kt`: built-in exercise library.
- Screens: `HomeScreen`, `ActiveWorkoutScreen`, `HistoryScreen`, `ProgressScreen`, `SettingsScreen`, `OnboardingScreen`.
- Design system: `Theme.kt` (`P` palette, `AppType`), `Components.kt`.
- Widgets + lock screen + share image: `WidgetsAndStoryShare.kt`, `WidgetConfigActivity.kt`.

## Conventions
- Compose + Material 3, custom design system; no DI framework, no ViewModels so far.
- Weights stored in kg. Old store files and backups must keep loading (see `StoreCodecTest`).
- Comments are sparse and explain *why*.
