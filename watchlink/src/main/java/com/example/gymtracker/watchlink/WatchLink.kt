package com.example.gymtracker.watchlink

import org.json.JSONObject

/**
 * Phone → watch: the live workout as one Data Layer item at [STATE_PATH], already formatted by
 * the phone (units, exercise types), so the watch only displays it.
 * Watch → phone: one message per button press at [ACTION_PREFIX] + [WatchAction.name], and a
 * [REQUEST_STATE] message when the watch app opens.
 */
object WatchLink {
    const val STATE_PATH = "/gymtracker/session"
    const val ACTION_PREFIX = "/gymtracker/action/"
    const val REQUEST_STATE = "/gymtracker/request-state"

    fun actionPath(action: WatchAction) = ACTION_PREFIX + action.name

    fun actionFromPath(path: String): WatchAction? =
        if (path.startsWith(ACTION_PREFIX)) WatchAction.entries.firstOrNull { it.name == path.removePrefix(ACTION_PREFIX) } else null
}

/** The watch's buttons. "Primary" is the first box (weight, reps, time or distance), "secondary" the second. */
enum class WatchAction { PRIMARY_MINUS, PRIMARY_PLUS, SECONDARY_MINUS, SECONDARY_PLUS, COMPLETE_SET, ADD_REST, SKIP_REST }

data class WatchState(
    /** False when no workout is running on the phone. */
    val active: Boolean,
    val workoutName: String = "",
    /** Null once every set is done. */
    val exerciseName: String? = null,
    /** "Set 2", "Warm-up", "Drop set". */
    val setLabel: String = "",
    val primary: String = "",
    val primaryLabel: String = "",
    val secondary: String? = null,
    val secondaryLabel: String? = null,
    /** The set as text, for the rest screen: "80 kg × 8". */
    val summary: String = "",
    val setsLeft: Int = 0,
    /** Wall-clock time the rest ends (watch and phone clocks are synced); 0 when not resting. */
    val restEndsAtMillis: Long = 0L,
    /** Rest length when it started, for the progress ring. */
    val restTotalMillis: Long = 0L
) {
    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("active", active)
        put("workout", workoutName)
        exerciseName?.let { put("exercise", it) }
        put("setLabel", setLabel)
        put("primary", primary)
        put("primaryLabel", primaryLabel)
        secondary?.let { put("secondary", it) }
        secondaryLabel?.let { put("secondaryLabel", it) }
        put("summary", summary)
        put("setsLeft", setsLeft)
        put("restEndsAt", restEndsAtMillis)
        put("restTotal", restTotalMillis)
    }.toString()

    companion object {
        /** Unknown fields are ignored and missing ones get defaults, so either app can be updated first. */
        fun fromJson(json: String): WatchState? = try {
            val o = JSONObject(json)
            WatchState(
                active = o.optBoolean("active", false),
                workoutName = o.optString("workout"),
                exerciseName = if (o.has("exercise")) o.getString("exercise") else null,
                setLabel = o.optString("setLabel"),
                primary = o.optString("primary"),
                primaryLabel = o.optString("primaryLabel"),
                secondary = if (o.has("secondary")) o.getString("secondary") else null,
                secondaryLabel = if (o.has("secondaryLabel")) o.getString("secondaryLabel") else null,
                summary = o.optString("summary"),
                setsLeft = o.optInt("setsLeft"),
                restEndsAtMillis = o.optLong("restEndsAt"),
                restTotalMillis = o.optLong("restTotal")
            )
        } catch (e: Exception) {
            null
        }
    }
}
