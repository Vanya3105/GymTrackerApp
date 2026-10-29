package com.example.gymtracker.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class WatchActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START = "start"
    }

    private lateinit var link: PhoneLink

    // Needed for the workout on the watch face (an ongoing notification).
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) link.state?.let { WorkoutOngoing.update(this, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link = PhoneLink(this)
        setContent { WatchApp(link) }
        // Not again when the activity is recreated (e.g. after a configuration change).
        if (savedInstanceState == null) startFrom(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startFrom(intent)
    }

    /** The tile's Start chip names the day to start; the phone ignores it if a workout is already running. */
    private fun startFrom(intent: Intent?) {
        val day = intent?.getStringExtra(EXTRA_START) ?: return
        intent.removeExtra(EXTRA_START)
        link.startWorkout(day)
    }

    // Listening only while visible: the Data Layer keeps the latest state for the next start,
    // and PhoneDataService handles changes while the app is closed.
    override fun onStart() {
        super.onStart()
        link.start()
    }

    override fun onStop() {
        link.stop()
        super.onStop()
    }
}
