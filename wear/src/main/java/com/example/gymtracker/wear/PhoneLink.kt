package com.example.gymtracker.wear

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.gymtracker.watchlink.WatchAction
import com.example.gymtracker.watchlink.WatchLink
import com.example.gymtracker.watchlink.WatchState
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable

/**
 * The watch's side of the Data Layer: the workout state the phone publishes, and button
 * presses sent back. The phone does all the work; the watch never edits the workout itself.
 */
class PhoneLink(context: Context) {
    private val appCtx = context.applicationContext
    private val dataClient = Wearable.getDataClient(appCtx)
    private val messageClient = Wearable.getMessageClient(appCtx)
    private val nodeClient = Wearable.getNodeClient(appCtx)

    /** Null until the phone has answered once. */
    var state by mutableStateOf<WatchState?>(null)
        private set

    var phoneConnected by mutableStateOf(true)
        private set

    private val listener = DataClient.OnDataChangedListener { events ->
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WatchLink.STATE_PATH) read(e.dataItem.data)
        }
    }

    fun start() {
        dataClient.addListener(listener)
        // The Data Layer keeps the last state the phone sent, so this shows something at once.
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WatchLink.STATE_PATH).build()
        dataClient.getDataItems(uri).addOnSuccessListener { items ->
            items.forEach { read(it.data) }
            items.release()
        }
        sendPath(WatchLink.REQUEST_STATE)
    }

    fun stop() {
        dataClient.removeListener(listener)
    }

    fun send(action: WatchAction) = sendPath(WatchLink.actionPath(action))

    private fun sendPath(path: String) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                phoneConnected = nodes.isNotEmpty()
                nodes.forEach { messageClient.sendMessage(it.id, path, null) }
            }
            .addOnFailureListener { phoneConnected = false }
    }

    private fun read(bytes: ByteArray?) {
        bytes?.let { WatchState.fromJson(String(it, Charsets.UTF_8)) }?.let { state = it }
    }
}
