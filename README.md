# GymTracker for Android

A simple, offline workout log. Pick your split, follow the suggested weights, and log sets from the app, your home screen or your lock screen.

---

## What it does

### Training
* **Today screen:** your next workout, this week's progress (with a dot for every day you trained) and how rested each muscle group is.
* **Live workout:** steppers for weight and reps, one tap to mark a set done, and "last time" numbers under every exercise.
* **Weight suggestions:** plain-language tips based on your past sessions of the same split. Two sessions in a row at 8+ reps means go up; two at 6 or fewer means go down. Tap "Use" to fill in the weight.
* **Exercise library:** 130 exercises you can search by name, muscle, equipment or nickname ("rdl", "ohp"). You can still create your own.
* **Set types:** tap a set number to mark it as a warm-up, drop set or rest-pause, change its rest, or delete it.
* **Supersets:** link exercises so the rest timer starts after the whole round.
* **Drag to reorder:** hold the handle on an exercise and drag. The card follows your finger and the others slide out of the way.
* **Kilograms or pounds:** pick in Profile (or during setup). Switching only changes how weights are shown and typed; your history is untouched.
* **Rest timer:** a floating countdown with −15 / +15 / skip, plus an alarm-backed beep and notification that work with the phone locked.
* **Minimize a workout:** go back to browse your history mid-session; a "Resume" bar brings you back.

### History & progress
* Week and month views, each workout card with share, edit and delete.
* Body weight log with a trend chart, muscle recovery details and your best lifts (estimated 1-rep max).
* **Exercise pages:** every session of an exercise, a chart of estimated 1-rep max, top weight or volume (3 months, 1 year or all time; tap to inspect), your best weight for 1, 3, 5, 8, 10 and 12 reps, and rename. Open one from Progress or from an exercise's notes during a workout.
* Shareable workout image with your stats and top sets, over your photo if you added one.

### Widgets
* **Workout:** your next workout, or the current set with weight/rep buttons and a done button. It hides the buttons while you rest.
* **Recovery:** how rested each muscle group is and your sets this week.
* **This week:** workouts done against your goal, what's next, and weekly volume.
* Every widget has its own settings (long-press → settings): theme (match app, light, dark), plain or tinted background, opacity, and what it shows.
* While training, the same workout card appears on the lock screen.

### Look & feel
* Two themes, light and dark, or follow the system.
* Bottom tabs: Today, History, Progress, Profile.

---

## Tech
* **UI:** Jetpack Compose with Material 3, a custom design system (`Theme.kt`, `Components.kt`) and Material icons.
* **Widgets & lock screen:** `RemoteViews`, `AppWidgetProvider`, bitmaps drawn at each widget's real size.
* **Storage:** `LocalStorage` is the single entry point. Workout history, body weight, split templates and exercise notes live in a Room database, with exercises stored once by id. Settings, profile, the active workout and widget settings live in a small JSON file. Reads come from memory; writes reach disk in order on one background thread. Widget and lock-screen refreshes are batched. Backups are still a single JSON file.
* **Rest alarms:** `AlarmManager` + `BroadcastReceiver`.

---

## Getting started
1. Clone the repository.
2. Open it in **Android Studio**.
3. Sync Gradle and run it on a device (Android 7.0+; Android 12+ for the best widget experience).
