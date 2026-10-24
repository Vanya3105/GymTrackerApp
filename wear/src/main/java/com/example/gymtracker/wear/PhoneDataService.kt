package com.example.gymtracker.wear

import androidx.wear.tiles.TileService
import com.example.gymtracker.watchlink.WatchLink
import com.example.gymtracker.watchlink.WatchState
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Every workout change from the phone, also while the watch app is closed: refreshes the watch face and the tile. */
class PhoneDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val state = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchLink.STATE_PATH }
            .lastNotNullOrNull { it.dataItem.data?.let { bytes -> WatchState.fromJson(String(bytes, Charsets.UTF_8)) } }
            ?: return
        WorkoutOngoing.update(this, state)
        TileService.getUpdater(this).requestUpdate(WorkoutTileService::class.java)
    }

    private inline fun <T, R : Any> List<T>.lastNotNullOrNull(transform: (T) -> R?): R? = asReversed().firstNotNullOfOrNull(transform)
}
