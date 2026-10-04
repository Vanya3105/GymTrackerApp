# 🏋️ GymTracker for Android

A minimalist, high-performance offline workout tracker built for Android (optimized for Samsung One UI / Galaxy S26 Ultra), featuring split-isolated AI progressive overload, hardware-synced Home & Lock Screen interactive widgets, and background alarm-backed rest timers.

---

## ✨ Key Features

### 🚀 Interactive Home & Lock Screen Widgets
* **Live Rest Timer:** Displays a live second-by-second countdown chronometer right on your home screen and lock screen without draining battery.
* **In-Widget Set Control:** Adjust weight (`−` / `+`), adjust reps (`−` / `+`), and complete sets (`✓`) directly from your home screen or lock screen widget without opening the app.
* **Auto-Locking Rest Protection:** Automatically hides set logging controls while the rest timer is active and pops them back up the exact second your rest finishes.
* **Custom Themes & Opacity:** Long-press your home screen widget to access configuration settings for Dark/Light mode and custom background opacity (0–100%) with a live preview.
* **Non-Dismissible Lock Screen Live Widget:** Stays pinned to your lock screen during an active session (cannot be swiped away) and automatically disappears when you finish or cancel your workout.

### 🤖 Smart 6–8 Rep Failure AI Overload
* Analyzes past session logs for your current split.
* Tracks 2-session rep streaks in the 8+ or ≤6 rep zones to dynamically recommend when to increase weight (e.g., `+2.5kg`) or drop weight.

### ⏱️ Background Rest Alarms & Notifications
* Powered by Android's native `AlarmManager` so rest timer notifications and music-safe beeps fire precisely—even when your phone is locked in your pocket or the app is closed.

### 📊 Minimalist Logbook & Analytics
* **Clean Summary Cards:** Quick stat pills showing total exercises, work sets, and duration (`4 Exercises • 8 Work Sets • ⏱ 45 min`) instead of cluttered text walls.
* **Exercise Analytics:** Tap any exercise to view a 5-session progression chart, Estimated 1RM, Max Weight, Best Set, and permanent machine/seat setup notes.
* **Progress & Pump Photos:** Attach photo receipts to finished workouts to track physical progress.

---

## 🛠️ Tech Stack & Architecture
* **UI:** Jetpack Compose (Material 3, Crossfade transitions, custom high-contrast dark/light design systems).
* **Widgets & Lock Screen:** Android `RemoteViews`, `AppWidgetProvider`, and custom foreground notification views.
* **Persistence:** Local JSON storage (`LocalStorage`) via high-speed asynchronous writing with debouncing.
* **Background Tasks:** `AlarmManager` and `BroadcastReceiver` for exact-timing rest triggers.

---

## 📲 Getting Started
1. Clone or download this repository.
2. Open the project in **Android Studio**.
3. Sync Gradle and run on your Android device (Android 13+ / One UI recommended for full notification and widget features).
