package com.example.gymtracker.wear

import androidx.wear.tiles.TileService
import com.example.gymtracker.watchlink.WatchLink
import com.example.gymtracker.watchlink.WatchState
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Every workout change from the phone, also while the watch app is closed: refreshes the watch
 * face and the tile, and buzzes when a rest ran out (the phone no longer posts a notification
 * for that, which used to buzz the watch).
 */
class PhoneDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val state = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchLink.STATE_PATH }
            .lastNotNullOrNull { it.dataItem.data?.let { bytes -> WatchState.fromJson(String(bytes, Charsets.UTF_8)) } }
            ?: return
        val prefs = getSharedPreferences("rest", MODE_PRIVATE)
        val restEnd = prefs.getLong("end", 0L)
        if (restOver(restEnd, state.restEndsAtMillis, System.currentTimeMillis()) && !WatchActivity.visible) buzz(this)
        prefs.edit().putLong("end", state.restEndsAtMillis).apply()
        WorkoutOngoing.update(this, state)
        TileService.getUpdater(this).requestUpdate(WorkoutTileService::class.java)
    }

    private inline fun <T, R : Any> List<T>.lastNotNullOrNull(transform: (T) -> R?): R? = asReversed().firstNotNullOfOrNull(transform)
}

/**
 * A rest that was running ([previousEnd]) is over and the phone cleared it: buzz, unless it was
 * cut short (skipped, or a set ticked early), which the user did on purpose.
 */
internal fun restOver(previousEnd: Long, newEnd: Long, now: Long): Boolean =
    previousEnd > 0L && newEnd == 0L && now >= previousEnd - 2_000L
