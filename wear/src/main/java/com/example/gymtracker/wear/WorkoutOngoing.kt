package com.example.gymtracker.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.gymtracker.watchlink.WatchState

/**
 * While a workout runs on the phone, an ongoing activity puts GymTracker on the watch face
 * (and at the top of the recents list): one tap returns to the app, and rests count down there.
 */
object WorkoutOngoing {
    private const val CHANNEL_ID = "workout"
    private const val NOTIFICATION_ID = 1

    fun update(context: Context, state: WatchState) {
        val nm = NotificationManagerCompat.from(context)
        if (!state.active) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            // Low importance: the watch shouldn't buzz on every update, only show it.
            NotificationChannel(CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val now = System.currentTimeMillis()
        val resting = state.restEndsAtMillis > now
        val text = when {
            resting -> "Resting"
            state.exerciseName == null -> "All sets done"
            else -> "${state.exerciseName} · ${state.setLabel}"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(state.workoutName)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)

        val status = if (resting) {
            // Ongoing timers count on the elapsed-realtime clock, not wall time.
            val zero = SystemClock.elapsedRealtime() + (state.restEndsAtMillis - now)
            Status.Builder().addTemplate("Rest #time#").addPart("time", Status.TimerPart(zero)).build()
        } else {
            Status.Builder().addTemplate(text).build()
        }
        OngoingActivity.Builder(context, NOTIFICATION_ID, notification)
            .setStaticIcon(R.drawable.ic_stat_workout)
            .setTouchIntent(open)
            .setStatus(status)
            .build()
            .apply(context)
        nm.notify(NOTIFICATION_ID, notification.build())
    }
}
