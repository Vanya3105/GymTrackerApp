package com.example.gymtracker

import android.content.Context
import android.util.Log
import com.example.gymtracker.watchlink.WatchAction
import com.example.gymtracker.watchlink.WatchLink
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

// ============================================================================
// WATCH (Wear OS remote)
// ============================================================================
// The phone keeps the only copy of the workout. The watch gets a ready-to-show snapshot after
// every change (same moment widgets redraw) and sends button presses back as messages.

object WatchSync {
    private const val TAG = "WatchSync"
    @Volatile private var lastSent: String? = null

    /** Sends the current state if it changed. Cheap without a watch: the Data Layer call just fails. */
    fun publish(context: Context) {
        val json = watchStateOf(context).toJson()
        if (json == lastSent) return
        lastSent = json
        try {
            val request = PutDataRequest.create(WatchLink.STATE_PATH).setData(json.toByteArray()).setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(request)
                .addOnFailureListener { lastSent = null }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't reach the Wearable API", e)
            lastSent = null
        }
    }

    /** The watch app opened: send the state even if it didn't change. */
    fun resend(context: Context) {
        lastSent = null
        publish(context)
    }

    fun apply(context: Context, action: WatchAction) {
        val restEnd = LocalStorage.getRestEndTimeMillis(context)
        when (action) {
            WatchAction.PRIMARY_MINUS -> GymWidgetProvider.applySetAction(context, GymWidgetProvider.ACTION_WEIGHT_MINUS)
            WatchAction.PRIMARY_PLUS -> GymWidgetProvider.applySetAction(context, GymWidgetProvider.ACTION_WEIGHT_PLUS)
            WatchAction.SECONDARY_MINUS -> GymWidgetProvider.applySetAction(context, GymWidgetProvider.ACTION_REPS_MINUS)
            WatchAction.SECONDARY_PLUS -> GymWidgetProvider.applySetAction(context, GymWidgetProvider.ACTION_REPS_PLUS)
            WatchAction.COMPLETE_SET -> GymWidgetProvider.applySetAction(context, GymWidgetProvider.ACTION_COMPLETE_SET)
            WatchAction.ADD_REST -> if (restEnd > System.currentTimeMillis()) {
                LocalStorage.setRestEndTimeMillis(context, restEnd + 15_000L, fromWidget = true)
            }
            WatchAction.SKIP_REST -> if (restEnd > 0L) LocalStorage.setRestEndTimeMillis(context, 0L, fromWidget = true)
        }
    }
}

/** Receives the watch's messages, also while the app is closed. */
class WatchListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val context = applicationContext
        if (event.path == WatchLink.REQUEST_STATE) {
            WatchSync.resend(context)
            return
        }
        val action = WatchLink.actionFromPath(event.path) ?: return
        WatchSync.apply(context, action)
        LocalStorage.flush(context)
    }
}
