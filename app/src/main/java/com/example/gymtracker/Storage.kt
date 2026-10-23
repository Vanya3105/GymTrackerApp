package com.example.gymtracker

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

// ============================================================================
// LOCAL STORAGE
// ============================================================================

/**
 * The app's single storage entry point. Two backends sit behind it:
 * - Room ([GymDatabase]) for history, body weight, split templates and per-exercise notes/rest.
 * - A small JSON file for profile, settings, the active session and widget settings.
 *
 * Reads come from memory and are safe on any thread. Writes update memory at once and are
 * applied to disk in order on one storage thread, so a Room write is always on disk before any
 * later JSON write (e.g. a finished workout lands before its active session is cleared).
 * Widgets and the lock screen are redrawn once per burst of edits. BroadcastReceivers must call
 * [flush] before returning.
 */
object LocalStorage {
    private const val TAG = "LocalStorage"
    private const val FILE_NAME = "gym_tracker_store_v2.json"
    private const val SURFACE_REFRESH_DEBOUNCE_MS = 250L

    /** JSON sections that moved to Room. Old store files and backups still carry them. */
    private val ROOM_SECTIONS = listOf("history", "weightHistory", "splitTemplates", "exerciseNotes", "exerciseRestDefaults", "exerciseTypes")

    private val lock = Any()
    private var cachedRoot: JSONObject? = null
    private var cachedData: StoreData? = null

    @Volatile private var ioThread: Thread? = null
    private val ioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "gym-storage").also { ioThread = it } }
    private val pendingSnapshot = AtomicReference<String?>(null)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshContext: Context? = null
    private val refreshRunnable = Runnable { refreshContext?.let(::refreshSurfacesNow) }

    private fun storeFile(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    private fun db(context: Context) = GymDatabase.get(context)

    // --- JSON backend ---

    private fun root(context: Context): JSONObject = synchronized(lock) {
        cachedRoot ?: run {
            val file = storeFile(context)
            val parsed = try {
                if (file.exists()) JSONObject(file.readText()) else JSONObject()
            } catch (e: Exception) {
                // Keep the unreadable file aside: the next save would otherwise overwrite all history.
                try {
                    file.copyTo(File(file.parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}"))
                } catch (_: Exception) {}
                JSONObject()
            }
            cachedRoot = parsed
            Units.set(unitIn(parsed))
            parsed
        }
    }

    private fun unitIn(root: JSONObject) = WeightUnit.fromKey(root.optJSONObject("settings")?.optString("weightUnit"))

    private inline fun edit(context: Context, notifyInApp: Boolean = false, block: (JSONObject) -> Unit) {
        synchronized(lock) {
            val root = root(context)
            block(root)
            persist(context, root)
        }
        scheduleSurfaceRefresh(context, notifyInApp)
    }

    private fun persist(context: Context, root: JSONObject) {
        val file = storeFile(context)
        pendingSnapshot.set(root.toString())
        ioExecutor.execute {
            val snapshot = pendingSnapshot.getAndSet(null) ?: return@execute
            writeAtomically(file, snapshot)
        }
    }

    private fun writeAtomically(file: File, content: String) {
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(file)) {
                file.writeText(content)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't write ${file.name}", e)
        }
    }

    // --- Room backend ---

    /** Runs [block] on the storage thread and waits for it. */
    private fun <T> onIoThread(context: Context, block: (GymDatabase) -> T): T {
        val db = db(context)
        if (Thread.currentThread() === ioThread) return block(db)
        return ioExecutor.submit(Callable { block(db) }).get()
    }

    private fun data(context: Context): StoreData = synchronized(lock) {
        cachedData ?: run {
            migrateJsonSectionsIfNeeded(context)
            onIoThread(context, RoomStore::load).also { cachedData = it }
        }
    }

    /** Applies [change] to the in-memory copy now and queues [write] for Room. */
    private fun editData(context: Context, change: (StoreData) -> StoreData, write: (GymDatabase) -> Unit) {
        synchronized(lock) {
            cachedData = change(data(context))
            val db = db(context)
            ioExecutor.execute {
                try {
                    write(db)
                } catch (e: Exception) {
                    Log.e(TAG, "Room write failed", e)
                }
            }
        }
        scheduleSurfaceRefresh(context, notifyInApp = false)
    }

    /**
     * One-time move of the JSON sections into Room (stores written before v3.1). The original file
     * is kept as `*.pre-room`. Merging over what Room already has makes a retry after an
     * interrupted migration harmless. The sections are removed from the JSON only after Room commits.
     */
    private fun migrateJsonSectionsIfNeeded(context: Context) {
        val root = root(context)
        if (ROOM_SECTIONS.none(root::has)) return

        val file = storeFile(context)
        val backup = File(file.parentFile, "$FILE_NAME.pre-room")
        if (!backup.exists()) writeAtomically(backup, root.toString())

        val legacy = StoreCodec.readData(root)
        onIoThread(context) { db -> RoomStore.replaceAll(db, RoomStore.load(db).mergedOver(legacy)) }

        ROOM_SECTIONS.forEach(root::remove)
        pendingSnapshot.set(null)
        onIoThread(context) { writeAtomically(file, root.toString()) }
    }

    private fun scheduleSurfaceRefresh(context: Context, notifyInApp: Boolean) {
        val appCtx = context.applicationContext
        refreshContext = appCtx
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, SURFACE_REFRESH_DEBOUNCE_MS)

        if (notifyInApp) {
            try {
                appCtx.sendBroadcast(Intent(ACTION_SESSION_SYNC).setPackage(appCtx.packageName))
            } catch (_: Exception) {}
        }
    }

    private fun refreshSurfacesNow(appCtx: Context) {
        GymWidgetProvider.refreshAllWidgets(appCtx)
        MuscleFatigueWidgetProvider.refreshAll(appCtx)
        SquarePulseWidgetProvider.refreshAll(appCtx)
        LockScreenWorkoutController.sync(appCtx)
    }

    /** Drops in-memory state and closes the database so a test can start from the files on disk. */
    @VisibleForTesting
    internal fun resetForTests() {
        ioExecutor.submit {}.get()
        synchronized(lock) {
            cachedRoot = null
            cachedData = null
            pendingSnapshot.set(null)
            GymDatabase.closeForTests()
        }
    }

    /** Redraws widgets now and blocks until pending writes reach disk. */
    fun flush(context: Context) {
        mainHandler.removeCallbacks(refreshRunnable)
        refreshSurfacesNow(context.applicationContext)
        try {
            ioExecutor.submit {}.get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }

    // --- Profile & body weight ---

    fun loadUserProfile(context: Context): UserProfile {
        val obj = root(context).optJSONObject("userProfile") ?: return UserProfile()
        return UserProfile(
            hasCompletedOnboarding = obj.optBoolean("hasCompletedOnboarding", false),
            age = obj.optInt("age", 0),
            currentWeightKg = obj.optDouble("currentWeightKg", 0.0).toFloat(),
            splitProgram = obj.optString("splitProgram", "PPL_ARNOLD")
        )
    }

    fun saveUserProfile(context: Context, profile: UserProfile) = edit(context) { root ->
        root.put("userProfile", JSONObject().apply {
            put("hasCompletedOnboarding", profile.hasCompletedOnboarding)
            put("age", profile.age)
            put("currentWeightKg", profile.currentWeightKg.toDouble())
            put("splitProgram", profile.splitProgram)
        })
    }

    /** Newest first. */
    fun loadWeightHistory(context: Context): List<WeightLogEntry> = data(context).weights

    fun addWeightLog(context: Context, weightKg: Float): List<WeightLogEntry> {
        val now = Date()
        // 0.01 kg keeps pound entries exact (180 lb → 81.65 kg → 180 lb).
        val rounded = (weightKg * 100f).roundToInt() / 100f
        val entry = WeightLogEntry(
            id = System.currentTimeMillis(),
            dateKey = DateUtils.formatDayKey(now),
            displayDate = DateUtils.formatDisplayDate(now),
            weightKg = rounded
        )
        editData(context, { it.copy(weights = listOf(entry) + it.weights) }) { RoomStore.addBodyWeight(it, entry) }
        edit(context) { root ->
            val profObj = root.optJSONObject("userProfile") ?: JSONObject().also { root.put("userProfile", it) }
            profObj.put("currentWeightKg", rounded.toDouble())
        }
        return loadWeightHistory(context)
    }

    fun deleteWeightLog(context: Context, id: Long): List<WeightLogEntry> {
        editData(context, { d -> d.copy(weights = d.weights.filter { it.id != id }) }) { RoomStore.deleteBodyWeight(it, id) }
        return loadWeightHistory(context)
    }

    // --- Widget settings (one entry per placed widget) ---

    fun loadWidgetSettings(context: Context, appWidgetId: Int, kind: String): WidgetSettings {
        val obj = root(context).optJSONObject("widgetSettings")?.optJSONObject(appWidgetId.toString())
            ?: return defaultWidgetSettings(kind)
        val optionsArr = obj.optJSONArray("options")
        return WidgetSettings(
            theme = obj.optString("theme", THEME_SYSTEM),
            opacityPercent = obj.optInt("opacityPercent", 100).coerceIn(0, 100),
            style = obj.optString("style", WIDGET_STYLE_STANDARD),
            options = optionsArr?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
                ?: defaultWidgetSettings(kind).options
        )
    }

    fun saveWidgetSettings(context: Context, appWidgetId: Int, settings: WidgetSettings) = edit(context) { root ->
        val all = root.optJSONObject("widgetSettings") ?: JSONObject().also { root.put("widgetSettings", it) }
        all.put(appWidgetId.toString(), JSONObject().apply {
            put("theme", settings.theme)
            put("opacityPercent", settings.opacityPercent.coerceIn(0, 100))
            put("style", settings.style)
            put("options", JSONArray(settings.options.toList()))
        })
    }

    fun deleteWidgetSettings(context: Context, appWidgetIds: IntArray) = edit(context) { root ->
        val all = root.optJSONObject("widgetSettings") ?: return@edit
        appWidgetIds.forEach { all.remove(it.toString()) }
    }

    // --- App settings ---

    fun loadSettings(context: Context): AppSettings {
        val obj = root(context).optJSONObject("settings") ?: return AppSettings()
        // Older versions stored one of four named themes; map them onto light/dark.
        val theme = when (val stored = obj.optString("themeChoice", THEME_SYSTEM)) {
            THEME_SYSTEM, THEME_LIGHT, THEME_DARK -> stored
            "ARCTIC" -> THEME_LIGHT
            else -> THEME_DARK
        }
        return AppSettings(
            themeChoice = theme,
            targetFps = obj.optInt("targetFps", 120),
            hapticsEnabled = obj.optBoolean("hapticsEnabled", true),
            weightStep = obj.optDouble("weightStepKg", 2.5).toFloat(), // key predates units; value is in weightUnit
            weightUnit = WeightUnit.fromKey(obj.optString("weightUnit")),
            restTimerSeconds = obj.optInt("restTimerSeconds", 90),
            keepScreenAwake = obj.optBoolean("keepScreenAwake", true),
            restSoundEnabled = obj.optBoolean("restSoundEnabled", true)
        )
    }

    fun saveSettings(context: Context, settings: AppSettings) = edit(context) { root ->
        root.put("settings", JSONObject().apply {
            put("targetFps", settings.targetFps)
            put("hapticsEnabled", settings.hapticsEnabled)
            put("weightStepKg", settings.weightStep.toDouble())
            put("weightUnit", settings.weightUnit.name)
            put("restTimerSeconds", settings.restTimerSeconds)
            put("keepScreenAwake", settings.keepScreenAwake)
            put("restSoundEnabled", settings.restSoundEnabled)
            put("themeChoice", settings.themeChoice)
        })
        Units.set(settings.weightUnit)
    }

    // --- Rest timer ---

    fun setRestEndTimeMillis(context: Context, endTimeMillis: Long, fromWidget: Boolean = false) {
        edit(context, notifyInApp = fromWidget) { root ->
            val activeObj = root.optJSONObject("activeSession") ?: return
            activeObj.put("restEndTimeMillis", endTimeMillis)
        }
        RestAlarmScheduler.scheduleOrCancel(context.applicationContext, endTimeMillis)
    }

    fun getRestEndTimeMillis(context: Context): Long =
        root(context).optJSONObject("activeSession")?.optLong("restEndTimeMillis", 0L) ?: 0L

    // --- Backup ---

    /** Same single-file format as before Room, so old and new builds can read each other's backups. */
    fun exportJsonBackupString(context: Context): String = synchronized(lock) {
        val out = JSONObject(root(context).toString())
        StoreCodec.writeData(out, data(context))
        out.toString(2)
    }

    fun importJsonBackupString(context: Context, jsonContent: String): Boolean {
        val parsed = try {
            JSONObject(jsonContent.removePrefix("﻿").trim())
        } catch (e: Exception) {
            return false
        }
        val restored = StoreCodec.readData(parsed)
        ROOM_SECTIONS.forEach(parsed::remove)
        synchronized(lock) {
            try {
                cachedData = onIoThread(context) { db ->
                    RoomStore.replaceAll(db, restored)
                    RoomStore.load(db)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                cachedData = null
                return false
            }
            cachedRoot = parsed
            Units.set(unitIn(parsed))
            persist(context, parsed)
        }
        scheduleSurfaceRefresh(context, notifyInApp = false)
        return true
    }

    // --- Per-exercise notes & rest defaults ---

    fun getExerciseNote(context: Context, exerciseName: String): String =
        data(context).notes[exerciseKey(exerciseName)].orEmpty()

    fun saveExerciseNote(context: Context, exerciseName: String, note: String) {
        val key = exerciseKey(exerciseName)
        val trimmed = note.trim()
        editData(context, { d ->
            d.copy(notes = if (trimmed.isEmpty()) d.notes - key else d.notes + (key to trimmed))
        }) { RoomStore.setNote(it, exerciseName, trimmed) }
    }

    fun getExerciseDefaultRest(context: Context, exerciseName: String, fallback: Int): Int =
        data(context).restDefaults[exerciseKey(exerciseName)] ?: fallback

    fun saveExerciseDefaultRest(context: Context, exerciseName: String, restSec: Int) {
        editData(context, { d -> d.copy(restDefaults = d.restDefaults + (exerciseKey(exerciseName) to restSec)) }) {
            RoomStore.setDefaultRest(it, exerciseName, restSec)
        }
    }

    /** The exercise's type: its own setting, else the library's, else weight & reps. */
    fun exerciseType(context: Context, exerciseName: String): ExerciseType =
        data(context).types?.get(exerciseKey(exerciseName))
            ?: ExerciseDatabase.find(exerciseName)?.type
            ?: ExerciseType.WEIGHT_REPS

    /**
     * Changes how an exercise is logged. Logged numbers are kept as they are, so switching back
     * restores the old view exactly.
     */
    fun setExerciseType(context: Context, exerciseName: String, type: ExerciseType) {
        synchronized(lock) {
            data(context)
            cachedData = onIoThread(context) { db ->
                RoomStore.setType(db, exerciseName, type)
                RoomStore.load(db)
            }
        }
        scheduleSurfaceRefresh(context, notifyInApp = true)
    }

    /**
     * Renames an exercise in history, templates, notes and the active session.
     * Returns false if the new name is blank or already used by another exercise.
     */
    fun renameExercise(context: Context, oldName: String, newName: String): Boolean {
        synchronized(lock) {
            data(context)
            val renamed = onIoThread(context) { db -> RoomStore.renameExercise(db, oldName, newName) }
            if (!renamed) return false
            cachedData = onIoThread(context, RoomStore::load)
            loadActiveSession(context)?.let { (split, exercises) ->
                saveActiveSession(context, split, exercises.map {
                    if (exerciseKey(it.name) == exerciseKey(oldName)) it.copy(name = newName.trim()) else it
                })
            }
        }
        scheduleSurfaceRefresh(context, notifyInApp = false)
        return true
    }

    // --- Split templates ---

    fun loadTemplateForSplit(context: Context, splitTitle: String): List<Pair<String, String>> =
        data(context).templates[splitTitle]
            ?: defaultTemplateForSplit(splitTitle).also { saveTemplateForSplit(context, splitTitle, it) }

    fun saveTemplateForSplit(context: Context, splitTitle: String, list: List<Pair<String, String>>) {
        editData(context, { it.copy(templates = it.templates + (splitTitle to list)) }) {
            RoomStore.saveTemplate(it, splitTitle, list)
        }
    }

    fun addExerciseToSplitTemplate(context: Context, splitTitle: String, name: String, muscle: String = "Custom") {
        val current = loadTemplateForSplit(context, splitTitle)
        if (current.none { it.first.equals(name, ignoreCase = true) }) {
            saveTemplateForSplit(context, splitTitle, current + (name to muscle))
        }
    }

    fun removeExerciseFromSplitTemplate(context: Context, splitTitle: String, name: String) {
        val current = loadTemplateForSplit(context, splitTitle)
        saveTemplateForSplit(context, splitTitle, current.filterNot { it.first.equals(name, ignoreCase = true) })
    }

    // --- Active session ---

    fun saveActiveSession(context: Context, splitTitle: String?, exercises: List<ExerciseEntry>, fromWidget: Boolean = false) {
        edit(context, notifyInApp = fromWidget) { root ->
            if (splitTitle == null) {
                root.remove("activeSession")
            } else {
                val prevObj = root.optJSONObject("activeSession")
                root.put("activeSession", JSONObject().apply {
                    put("splitTitle", splitTitle)
                    put("startTimeMillis", prevObj?.optLong("startTimeMillis", 0L)?.takeIf { it > 0L } ?: System.currentTimeMillis())
                    put("restEndTimeMillis", prevObj?.optLong("restEndTimeMillis", 0L) ?: 0L)
                    put("exercises", StoreCodec.serializeExercises(exercises))
                })
            }
        }
        if (splitTitle == null) RestAlarmScheduler.scheduleOrCancel(context.applicationContext, 0L)
    }

    fun getActiveSessionStartTime(context: Context): Long =
        root(context).optJSONObject("activeSession")?.optLong("startTimeMillis", 0L)?.takeIf { it > 0L }
            ?: System.currentTimeMillis()

    fun loadActiveSession(context: Context): Pair<String, List<ExerciseEntry>>? {
        val activeObj = root(context).optJSONObject("activeSession") ?: return null
        val splitTitle = activeObj.optString("splitTitle", "")
        if (splitTitle.isBlank()) return null
        // Types come from the exercise definitions, so a change of type shows up mid-workout too.
        return splitTitle to StoreCodec.deserializeExercises(activeObj.optJSONArray("exercises"))
            .map { it.copy(type = exerciseType(context, it.name)) }
    }

    // --- Workout history ---

    /** Saves the workout and ends the active session; the workout reaches disk first. */
    fun saveCompletedWorkout(context: Context, workout: CompletedWorkout) {
        editData(context, { d ->
            d.copy(history = (listOf(workout) + d.history.filter { it.id != workout.id }).sortedByDescending { it.id })
        }) { RoomStore.insertWorkout(it, workout) }
        edit(context) { root -> root.remove("activeSession") }
        RestAlarmScheduler.scheduleOrCancel(context.applicationContext, 0L)
    }

    fun updateCompletedWorkout(context: Context, updatedWorkout: CompletedWorkout) {
        editData(context, { d ->
            d.copy(history = d.history.map { if (it.id == updatedWorkout.id) updatedWorkout else it })
        }) { RoomStore.insertWorkout(it, updatedWorkout) }
    }

    fun deleteWorkoutFromHistory(context: Context, workoutId: Long) {
        editData(context, { d -> d.copy(history = d.history.filter { it.id != workoutId }) }) {
            RoomStore.deleteWorkout(it, workoutId)
        }
    }

    /** Newest first. The same list instance is returned until history changes, so callers can use it as a stable key. */
    fun loadHistory(context: Context): List<CompletedWorkout> = data(context).history
}

/** Entries in this copy win over [base] (matched by id or key). */
fun StoreData.mergedOver(base: StoreData): StoreData {
    val historyIds = history.mapTo(HashSet()) { it.id }
    val weightIds = weights.mapTo(HashSet()) { it.id }
    return StoreData(
        history = (history + base.history.filter { it.id !in historyIds }).sortedByDescending { it.id },
        weights = (weights + base.weights.filter { it.id !in weightIds }).sortedByDescending { it.id },
        templates = base.templates + templates,
        notes = base.notes + notes,
        restDefaults = base.restDefaults + restDefaults,
        // Unknown on either side (data from before types) means the merge can't know them either.
        types = types?.let { mine -> base.types?.let { it + mine } }
    )
}

/**
 * Pure JSON <-> model mapping for the store file, kept free of Context so it can be unit-tested
 * and reused by data migrations. Reading is lenient: older saves and hand-edited backups must still load.
 */
internal object StoreCodec {
    fun parseHistory(arr: JSONArray?): List<CompletedWorkout> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val skippedJson = obj.optJSONArray("skippedExercises")
            CompletedWorkout(
                id = obj.optLong("id"),
                splitTitle = obj.optString("splitTitle"),
                dateKey = obj.optString("dateKey"),
                displayDate = obj.optString("displayDate"),
                durationSeconds = obj.optLong("durationSeconds", 0L),
                bodyWeightKg = obj.optDouble("bodyWeightKg", 0.0).toFloat(),
                photoPath = obj.optString("photoPath", "").ifBlank { null },
                skippedExercises = skippedJson?.let { s -> (0 until s.length()).map { s.optString(it) } }.orEmpty(),
                exercises = deserializeExercises(obj.optJSONArray("exercises")),
                totalVolumeKg = obj.optInt("totalVolumeKg", 0),
                prsHit = obj.optInt("prsHit", 0)
            )
        }.sortedByDescending { it.id }
    }

    fun serializeWorkout(workout: CompletedWorkout) = JSONObject().apply {
        put("id", workout.id)
        put("splitTitle", workout.splitTitle)
        put("dateKey", workout.dateKey)
        put("displayDate", workout.displayDate)
        put("durationSeconds", workout.durationSeconds)
        put("bodyWeightKg", workout.bodyWeightKg.toDouble())
        put("photoPath", workout.photoPath ?: "")
        put("skippedExercises", JSONArray(workout.skippedExercises))
        put("exercises", serializeExercises(workout.exercises))
        put("totalVolumeKg", workout.totalVolumeKg)
        put("prsHit", workout.prsHit)
    }

    fun serializeExercises(exercises: List<ExerciseEntry>): JSONArray = JSONArray().apply {
        for (ex in exercises) {
            put(JSONObject().apply {
                put("name", ex.name)
                put("targetMuscle", ex.targetMuscle)
                put("restSeconds", ex.restSeconds)
                put("isSkipped", ex.isSkipped)
                put("isSuperset", ex.isSuperset)
                put("sets", JSONArray().apply {
                    for (s in ex.sets) {
                        put(JSONObject().apply {
                            put("setNumber", s.setNumber)
                            put("weight", s.weight.replace("kg", "", ignoreCase = true).trim())
                            put("reps", s.reps.replace("r", "", ignoreCase = true).trim())
                            put("isWarmup", s.isWarmup)
                            put("intensityType", s.intensityType)
                            put("isCompleted", s.isCompleted)
                            put("restSeconds", s.restSeconds)
                            if (s.seconds.isNotEmpty()) put("seconds", s.seconds)
                            if (s.distanceM.isNotEmpty()) put("distanceM", s.distanceM)
                        })
                    }
                })
            })
        }
    }

    fun deserializeExercises(arr: JSONArray?): List<ExerciseEntry> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val exObj = arr.getJSONObject(i)
            val exRest = exObj.optInt("restSeconds", 90)
            val setsArr = exObj.optJSONArray("sets") ?: JSONArray()
            val sets = (0 until setsArr.length()).map { j ->
                val sObj = setsArr.getJSONObject(j)
                // Older saves only had isWarmup; intensityType was added later.
                val legacyWarmup = sObj.optBoolean("isWarmup", false)
                val type = sObj.optString("intensityType", if (legacyWarmup) "WARMUP" else "NORMAL")
                SetEntry(
                    setNumber = sObj.optInt("setNumber", j + 1),
                    weight = (sObj.opt("weight")?.toString() ?: "20").replace("kg", "", ignoreCase = true).trim(),
                    reps = (sObj.opt("reps")?.toString() ?: "8").replace("r", "", ignoreCase = true).trim(),
                    isWarmup = legacyWarmup || type == "WARMUP",
                    intensityType = type,
                    isCompleted = sObj.optBoolean("isCompleted", false),
                    restSeconds = sObj.optInt("restSeconds", exRest),
                    seconds = sObj.optString("seconds", ""),
                    distanceM = sObj.optString("distanceM", "")
                )
            }
            ExerciseEntry(
                name = exObj.optString("name", ""),
                targetMuscle = exObj.optString("targetMuscle", ""),
                restSeconds = exRest,
                isSkipped = exObj.optBoolean("isSkipped", false),
                sets = sets,
                isSuperset = exObj.optBoolean("isSuperset", false)
            )
        }
    }

    // --- Sections stored in Room (still part of the backup format) ---

    fun readData(root: JSONObject): StoreData = StoreData(
        history = parseHistory(root.optJSONArray("history")),
        weights = parseWeights(root.optJSONArray("weightHistory")),
        templates = root.optJSONObject("splitTemplates")?.let { obj ->
            obj.keys().asSequence().associateWith { split ->
                val arr = obj.optJSONArray(split) ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.optJSONObject(i) ?: return@mapNotNull null
                    item.optString("name").takeIf { it.isNotBlank() }?.let { it to item.optString("muscle") }
                }
            }
        }.orEmpty(),
        notes = root.optJSONObject("exerciseNotes")?.let { obj ->
            obj.keys().asSequence().associate { exerciseKey(it) to obj.optString(it).trim() }.filterValues { it.isNotEmpty() }
        }.orEmpty(),
        restDefaults = root.optJSONObject("exerciseRestDefaults")?.let { obj ->
            obj.keys().asSequence().associate { exerciseKey(it) to obj.optInt(it, 90) }
        }.orEmpty(),
        // Missing in files from before exercise types: null tells Room to work them out safely.
        types = root.optJSONObject("exerciseTypes")?.let { obj ->
            obj.keys().asSequence().associate { exerciseKey(it) to ExerciseType.fromKey(obj.optString(it)) }
        }
    )

    fun writeData(root: JSONObject, data: StoreData) {
        root.put("history", JSONArray().apply { data.history.forEach { put(serializeWorkout(it)) } })
        root.put("weightHistory", JSONArray().apply {
            data.weights.forEach { w ->
                put(JSONObject().apply {
                    put("id", w.id)
                    put("dateKey", w.dateKey)
                    put("displayDate", w.displayDate)
                    put("weightKg", w.weightKg.toDouble())
                })
            }
        })
        root.put("splitTemplates", JSONObject().apply {
            data.templates.forEach { (split, items) ->
                put(split, JSONArray().apply {
                    items.forEach { (name, muscle) -> put(JSONObject().put("name", name).put("muscle", muscle)) }
                })
            }
        })
        root.put("exerciseNotes", JSONObject().apply { data.notes.forEach { (k, v) -> put(k, v) } })
        root.put("exerciseRestDefaults", JSONObject().apply { data.restDefaults.forEach { (k, v) -> put(k, v) } })
        data.types?.let { types -> root.put("exerciseTypes", JSONObject().apply { types.forEach { (k, v) -> put(k, v.name) } }) }
    }

    fun parseWeights(arr: JSONArray?): List<WeightLogEntry> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            WeightLogEntry(
                id = o.optLong("id"),
                dateKey = o.optString("dateKey"),
                displayDate = o.optString("displayDate"),
                weightKg = o.optDouble("weightKg", 0.0).toFloat()
            )
        }.sortedByDescending { it.id }
    }
}
