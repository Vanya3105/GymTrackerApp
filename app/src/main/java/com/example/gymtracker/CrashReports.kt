package com.example.gymtracker

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash reports through Firebase Crashlytics: when the app crashes, the error and where in the
 * code it happened, the phone model and Android version go to the developer. No workouts, body
 * weight or account details. On by default; Profile → Advanced turns them off. Does nothing in a
 * build without the Firebase project's config (google-services.json).
 */
object CrashReports {
    private const val PREFS = "crash_reports"
    private const val KEY_ON = "on"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)

    /** Whether this build can send reports at all. */
    fun available(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        apply(context)
    }

    /** Applies the choice; call once at start. */
    fun apply(context: Context) {
        if (!available(context)) return
        try {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = isOn(context)
        } catch (_: Exception) {}
    }
}
