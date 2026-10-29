package com.example.gymtracker.wear

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.gymtracker.watchlink.WatchLink
import com.example.gymtracker.watchlink.WatchState
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import java.util.Date

private const val RESOURCES_VERSION = "1"
private const val ORANGE = 0xFFFF6B1A.toInt()
private const val MUTED = 0xFF9A9A9A.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/**
 * The GymTracker tile: the current set, the rest end time, or the day that's up next with a
 * chip that starts it.
 * Reads the last state the phone published (the Data Layer keeps it), so it works offline too.
 */
class WorkoutTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WatchLink.STATE_PATH).build()
            fun answer(state: WatchState?) {
                completer.set(workoutTile(this, state, requestParams.deviceConfiguration, System.currentTimeMillis()))
            }
            Wearable.getDataClient(this).getDataItems(uri)
                .addOnSuccessListener { items ->
                    val state = items.firstNotNullOfOrNull { item -> item.data?.let { WatchState.fromJson(String(it, Charsets.UTF_8)) } }
                    items.release()
                    answer(state)
                }
                .addOnFailureListener { answer(null) }
            "workoutTile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "workoutTileResources"
        }
}

internal fun workoutTile(context: Context, state: WatchState?, device: DeviceParameters, now: Long): TileBuilders.Tile {
    val resting = state != null && state.restEndsAtMillis > now
    val tile = TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(tileLayout(context, state, device, now)))
    // A rest's end changes what the tile says; the phone's update usually arrives first anyway.
    if (resting) tile.setFreshnessIntervalMillis(state.restEndsAtMillis - now + 1_000L)
    return tile.build()
}

private fun tileLayout(context: Context, state: WatchState?, device: DeviceParameters, now: Long): LayoutElementBuilders.LayoutElement {
    val upNext = state?.days?.firstOrNull()?.takeIf { !state.active }
    val (label, title, detail) = when {
        upNext != null -> Triple("Up next", upNext, "Tap Start to begin")
        state == null || !state.active -> Triple("GymTracker", "No workout", "Start one on your phone")
        state.restEndsAtMillis > now -> Triple(
            state.workoutName, "Resting",
            "Until ${DateFormat.getTimeFormat(context).format(Date(state.restEndsAtMillis))}" +
                (state.exerciseName?.let { "\nNext: $it" } ?: "")
        )
        state.exerciseName == null -> Triple(state.workoutName, "All sets done", "Open to finish it")
        else -> Triple(state.workoutName, state.exerciseName.orEmpty(), "${state.setLabel} · ${state.summary}")
    }

    fun text(value: String, typography: Int, color: Int, lines: Int = 1) = Text.Builder(context, value)
        .setTypography(typography)
        .setColor(argb(color))
        .setMaxLines(lines)
        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        .build()

    val activity = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(context.packageName)
        .setClassName(WatchActivity::class.java.name)
    upNext?.let { activity.addKeyToExtraMapping(WatchActivity.EXTRA_START, ActionBuilders.stringExtra(it)) }
    val open = ModifiersBuilders.Clickable.Builder()
        .setId(if (upNext != null) "start" else "open")
        .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
        .build()

    return PrimaryLayout.Builder(device)
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(text(label, Typography.TYPOGRAPHY_CAPTION1, ORANGE))
        .setContent(
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text(title, Typography.TYPOGRAPHY_TITLE2, WHITE, lines = 2))
                .addContent(text(detail, Typography.TYPOGRAPHY_BODY2, MUTED, lines = 2))
                .build()
        )
        .setPrimaryChipContent(
            CompactChip.Builder(context, if (upNext != null) "Start" else "Open", open, device)
                .setChipColors(ChipColors(ORANGE, BLACK))
                .build()
        )
        .build()
}
