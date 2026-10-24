package com.example.gymtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.reflect.KClass

// ============================================================================
// HEALTH CONNECT
// ============================================================================
// Finished workouts and body weight go to Health Connect (and from there to Samsung Health or
// Google Fit); weigh-ins from scales come back, and so does the heart rate a watch recorded
// during a workout. Everything is optional: with sharing off or Health Connect missing, every
// call here does nothing.

@androidx.compose.runtime.Immutable
data class HeartRateSummary(val average: Int, val max: Int)

object HealthSync {
    private const val TAG = "HealthSync"
    private const val PROVIDER = "com.google.android.apps.healthdata"
    private const val NOTES_MAX = 1000

    val WRITE_WORKOUTS = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val READ_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
    val WRITE_WEIGHT = HealthPermission.getWritePermission(WeightRecord::class)
    val READ_HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
    val PERMISSIONS = setOf(WRITE_WORKOUTS, READ_WEIGHT, WRITE_WEIGHT, READ_HEART_RATE)

    enum class Availability { AVAILABLE, NEEDS_INSTALL, UNAVAILABLE }

    /** Granted Health Connect permissions as last checked. Snapshot state, so screens follow it. */
    var granted by mutableStateOf(emptySet<String>())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val heartRates = ConcurrentHashMap<Long, HeartRateSummary>()

    fun availability(context: Context): Availability = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL
        else -> Availability.UNAVAILABLE
    }

    private fun client(context: Context) = HealthConnectClient.getOrCreate(context.applicationContext, PROVIDER)

    /** On in the app and allowed in Health Connect. */
    fun allows(context: Context, permission: String) = LocalStorage.isHealthSyncOn(context) && permission in granted

    /** Re-reads the granted permissions (they can change in Health Connect's own settings). */
    suspend fun refresh(context: Context) {
        granted = if (availability(context) == Availability.AVAILABLE) {
            try {
                client(context).permissionController.getGrantedPermissions()
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't read permissions", e)
                emptySet()
            }
        } else emptySet()
    }

    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .setPackage("com.android.vending")

    fun manageIntent(context: Context): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context, PROVIDER)

    /** Turns sharing off and hands every permission back. */
    fun disconnect(context: Context) {
        LocalStorage.setHealthSyncOn(context, false)
        heartRates.clear()
        scope.launch {
            try {
                client(context).permissionController.revokeAllPermissions()
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't revoke permissions", e)
            }
            refresh(context)
        }
    }

    // --- Writes (fire and forget; failures only logged, the app's own data is never affected) ---

    private fun write(context: Context, permission: String, block: suspend (HealthConnectClient) -> Unit) {
        val appCtx = context.applicationContext
        if (!allows(appCtx, permission)) return
        scope.launch {
            try {
                block(client(appCtx))
            } catch (e: Exception) {
                Log.e(TAG, "Health Connect write failed", e)
            }
        }
    }

    /** New or edited workout: same client id, newer version, so Health Connect replaces it. */
    fun workoutSaved(context: Context, workout: CompletedWorkout) = write(context, WRITE_WORKOUTS) {
        it.insertRecords(listOf(workoutRecord(workout, System.currentTimeMillis())))
        heartRates.remove(workout.id)
    }

    fun workoutDeleted(context: Context, workoutId: Long) = write(context, WRITE_WORKOUTS) {
        it.deleteRecords(ExerciseSessionRecord::class, emptyList(), listOf(workoutClientId(workoutId)))
    }

    fun weightLogged(context: Context, entry: WeightLogEntry) = write(context, WRITE_WEIGHT) {
        it.insertRecords(listOf(weightRecord(entry)))
    }

    fun weightDeleted(context: Context, id: Long) = write(context, WRITE_WEIGHT) {
        it.deleteRecords(WeightRecord::class, emptyList(), listOf(weightClientId(id)))
    }

    /** After connecting: everything already logged, so Health Connect isn't missing older workouts. */
    fun shareExisting(context: Context) {
        val history = LocalStorage.loadHistory(context)
        val imported = LocalStorage.importedWeightIds(context)
        val weights = LocalStorage.loadWeightHistory(context).filter { it.id !in imported }
        val version = System.currentTimeMillis()
        write(context, WRITE_WORKOUTS) { c -> history.chunked(500).forEach { c.insertRecords(it.map { w -> workoutRecord(w, version) }) } }
        write(context, WRITE_WEIGHT) { c -> weights.chunked(500).forEach { c.insertRecords(it.map(::weightRecord)) } }
    }

    // --- Reads ---

    private suspend fun <T : Record> readAll(context: Context, type: KClass<T>, range: TimeRangeFilter): List<T> {
        val c = client(context)
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val response = c.readRecords(ReadRecordsRequest(type, range, pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }

    /**
     * Copies weigh-ins logged elsewhere (a smart scale, Samsung Health) into the weight log.
     * Returns true when something new arrived.
     */
    suspend fun importWeights(context: Context): Boolean {
        if (!allows(context, READ_WEIGHT)) return false
        return try {
            val records = readAll(context, WeightRecord::class, TimeRangeFilter.after(Instant.EPOCH))
            val entries = records
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
                .map { weightEntryFrom(it.time, it.weight.inKilograms) }
            val before = LocalStorage.loadWeightHistory(context).size
            LocalStorage.importWeights(context, entries).size > before
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't read weights", e)
            false
        }
    }

    /** Average and max heart rate during [workout], or null if nothing was recorded (yet). */
    suspend fun heartRate(context: Context, workout: CompletedWorkout): HeartRateSummary? {
        heartRates[workout.id]?.let { return it }
        if (!allows(context, READ_HEART_RATE)) return null
        return try {
            val (start, end) = workoutTimes(workout)
            val bpm = readAll(context, HeartRateRecord::class, TimeRangeFilter.between(start, end))
                .flatMap { r -> r.samples.filter { it.time >= start && it.time <= end }.map { it.beatsPerMinute } }
            // Only hits are cached: Samsung Health can sync a watch's data minutes later.
            summarizeHeartRate(bpm)?.also { heartRates[workout.id] = it }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't read heart rate", e)
            null
        }
    }

    // --- Mapping (pure, unit-tested) ---

    fun workoutClientId(id: Long) = "workout-$id"
    fun weightClientId(id: Long) = "weight-$id"

    /** A workout's id is the moment it was finished; it started its duration before that. */
    fun workoutTimes(workout: CompletedWorkout): Pair<Instant, Instant> {
        val end = Instant.ofEpochMilli(workout.id)
        // Health Connect needs end after start; a workout saved without a timer gets a minute.
        return end.minusSeconds(workout.durationSeconds.coerceAtLeast(60)) to end
    }

    fun workoutRecord(workout: CompletedWorkout, version: Long): ExerciseSessionRecord {
        val (start, end) = workoutTimes(workout)
        val zone = ZoneId.systemDefault().rules
        return ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = zone.getOffset(start),
            endTime = end,
            endZoneOffset = zone.getOffset(end),
            metadata = Metadata.manualEntry(clientRecordId = workoutClientId(workout.id), clientRecordVersion = version),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = workout.splitTitle,
            notes = workoutNotes(workout)
        )
    }

    /**
     * The sets as text ("Bench Press: 80 kg × 8, 80 kg × 7"). Health Connect's per-exercise
     * segments need a start and end time for each exercise, which the app doesn't record.
     */
    fun workoutNotes(workout: CompletedWorkout): String = workout.exercises
        .filter { !it.isSkipped && it.workSets.isNotEmpty() }
        .joinToString("\n") { ex -> "${ex.name}: " + ex.workSets.joinToString(", ") { formatSet(it, ex.type) } }
        .take(NOTES_MAX)

    fun weightRecord(entry: WeightLogEntry): WeightRecord {
        val time = Instant.ofEpochMilli(entry.id)
        return WeightRecord(
            time = time,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
            weight = Mass.kilograms(entry.weightKg.toDouble()),
            metadata = Metadata.manualEntry(clientRecordId = weightClientId(entry.id), clientRecordVersion = 1)
        )
    }

    fun weightEntryFrom(time: Instant, kg: Double): WeightLogEntry {
        val date = Date.from(time)
        return WeightLogEntry(
            id = time.toEpochMilli(),
            dateKey = DateUtils.formatDayKey(date),
            displayDate = DateUtils.formatDisplayDate(date),
            weightKg = (kg * 100).roundToInt() / 100f
        )
    }

    fun summarizeHeartRate(bpm: List<Long>): HeartRateSummary? =
        if (bpm.isEmpty()) null else HeartRateSummary(bpm.average().roundToInt(), bpm.max().toInt())
}
