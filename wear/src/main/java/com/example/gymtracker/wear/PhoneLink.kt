package com.example.gymtracker.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.example.gymtracker.watchlink.WatchAction
import com.example.gymtracker.watchlink.WatchLink
import com.example.gymtracker.watchlink.WatchState
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.MessageClient
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

    /** Uptime of the oldest tap the phone hasn't acknowledged yet; 0 when none. */
    var waitingSince by mutableLongStateOf(0L)
        private set

    private val dataListener = DataClient.OnDataChangedListener { events ->
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WatchLink.STATE_PATH) read(e.dataItem.data)
        }
    }

    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path == WatchLink.ACK) waitingSince = 0L
    }

    fun start() {
        dataClient.addListener(dataListener)
        messageClient.addListener(messageListener)
        // The Data Layer keeps the last state the phone sent, so this shows something at once.
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WatchLink.STATE_PATH).build()
        dataClient.getDataItems(uri).addOnSuccessListener { items ->
            items.forEach { read(it.data) }
            items.release()
        }
        sendPath(WatchLink.REQUEST_STATE)
    }

    fun stop() {
        dataClient.removeListener(dataListener)
        messageClient.removeListener(messageListener)
        waitingSince = 0L
    }

    fun send(action: WatchAction) {
        if (waitingSince == 0L) waitingSince = SystemClock.uptimeMillis()
        sendPath(WatchLink.actionPath(action))
    }

    /** Starts the program day [title] on the phone. */
    fun startWorkout(title: String) {
        if (waitingSince == 0L) waitingSince = SystemClock.uptimeMillis()
        sendPath(WatchLink.START_WORKOUT, title.toByteArray(Charsets.UTF_8))
    }

    /** Opens GymTracker on the phone, e.g. to start or save a workout. */
    fun openOnPhone() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WatchLink.OPEN_PHONE_APP_URI)).addCategory(Intent.CATEGORY_BROWSABLE)
        RemoteActivityHelper(appCtx, ContextCompat.getMainExecutor(appCtx)).startRemoteActivity(intent)
    }

    private fun sendPath(path: String, data: ByteArray? = null) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                phoneConnected = nodes.isNotEmpty()
                nodes.forEach { messageClient.sendMessage(it.id, path, data) }
            }
            .addOnFailureListener { phoneConnected = false }
    }

    private fun read(bytes: ByteArray?) {
        bytes?.let { WatchState.fromJson(String(it, Charsets.UTF_8)) }?.let { state = it }
    }
}
