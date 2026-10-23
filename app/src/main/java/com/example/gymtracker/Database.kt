package com.example.gymtracker

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import org.json.JSONArray
import java.util.Locale

// ============================================================================
// ROOM SCHEMA
// ============================================================================
// History, body weight, split templates and per-exercise notes live here. Small key-value
// state that changes on every tap (settings, profile, active session, widgets) stays in the
// JSON store. Everything is reached through LocalStorage, which keeps an in-memory copy.

/** One row per exercise ever used. History points at the id, so a rename keeps the history. */
@Entity(tableName = "exercises", indices = [Index(value = ["nameKey"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val muscle: String,
    val note: String = "",
    val defaultRestSeconds: Int? = null
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: Long,
    val splitTitle: String,
    val dateKey: String,
    val displayDate: String,
    val durationSeconds: Long,
    val bodyWeightKg: Float,
    val photoPath: String?,
    val skippedExercises: List<String>,
    val totalVolumeKg: Int,
    val prsHit: Int
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"])
    ],
    indices = [Index("workoutId"), Index("exerciseId")]
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetMuscle: String,
    val restSeconds: Int,
    val isSkipped: Boolean,
    val isSuperset: Boolean
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = WorkoutExerciseEntity::class, parentColumns = ["id"], childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId")]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val position: Int,
    val setNumber: Int,
    // Kept as typed so a half-filled set round-trips exactly; parsing happens in the model layer.
    val weight: String,
    val reps: String,
    val intensityType: String,
    val isWarmup: Boolean,
    val isCompleted: Boolean,
    val restSeconds: Int
)

@Entity(tableName = "body_weight")
data class BodyWeightEntity(
    @PrimaryKey val id: Long,
    val dateKey: String,
    val displayDate: String,
    val weightKg: Float
)

@Entity(
    tableName = "split_template_items",
    primaryKeys = ["splitTitle", "position"],
    foreignKeys = [ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"])],
    indices = [Index("exerciseId")]
)
data class SplitTemplateItemEntity(
    val splitTitle: String,
    val position: Int,
    val exerciseId: Long,
    val muscle: String
)

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>): String = JSONArray(list).toString()

    @TypeConverter
    fun toStringList(raw: String): List<String> {
        val arr = try { JSONArray(raw) } catch (_: Exception) { return emptyList() }
        return (0 until arr.length()).map { arr.optString(it) }
    }
}

@Dao
interface GymDao {
    @Query("SELECT * FROM exercises") fun exercises(): List<ExerciseEntity>
    @Query("SELECT * FROM exercises WHERE nameKey = :key") fun exerciseByKey(key: String): ExerciseEntity?
    @Insert fun insertExercise(e: ExerciseEntity): Long
    @Query("UPDATE exercises SET note = :note WHERE id = :id") fun setExerciseNote(id: Long, note: String)
    @Query("UPDATE exercises SET defaultRestSeconds = :sec WHERE id = :id") fun setExerciseRest(id: Long, sec: Int?)
    @Query("UPDATE exercises SET name = :name, nameKey = :key WHERE id = :id") fun renameExercise(id: Long, name: String, key: String)

    @Query("SELECT * FROM workouts") fun workouts(): List<WorkoutEntity>
    @Query("SELECT * FROM workout_exercises") fun workoutExercises(): List<WorkoutExerciseEntity>
    @Query("SELECT * FROM workout_sets") fun workoutSets(): List<WorkoutSetEntity>
    @Insert fun insertWorkout(w: WorkoutEntity)
    @Insert fun insertWorkoutExercise(e: WorkoutExerciseEntity): Long
    @Insert fun insertSets(sets: List<WorkoutSetEntity>)
    @Query("DELETE FROM workouts WHERE id = :id") fun deleteWorkout(id: Long)

    @Query("SELECT * FROM body_weight") fun bodyWeights(): List<BodyWeightEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertBodyWeight(w: BodyWeightEntity)
    @Query("DELETE FROM body_weight WHERE id = :id") fun deleteBodyWeight(id: Long)

    @Query("SELECT * FROM split_template_items ORDER BY splitTitle, position") fun templateItems(): List<SplitTemplateItemEntity>
    @Insert fun insertTemplateItems(items: List<SplitTemplateItemEntity>)
    @Query("DELETE FROM split_template_items WHERE splitTitle = :splitTitle") fun deleteTemplate(splitTitle: String)

    @Query("DELETE FROM workouts") fun clearWorkouts()
    @Query("DELETE FROM body_weight") fun clearBodyWeights()
    @Query("DELETE FROM split_template_items") fun clearTemplates()
    @Query("DELETE FROM exercises") fun clearExercises()
}

@Database(
    entities = [
        ExerciseEntity::class, WorkoutEntity::class, WorkoutExerciseEntity::class, WorkoutSetEntity::class,
        BodyWeightEntity::class, SplitTemplateItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class GymDatabase : RoomDatabase() {
    abstract fun dao(): GymDao

    companion object {
        @Volatile private var instance: GymDatabase? = null

        fun get(context: Context): GymDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GymDatabase::class.java, "gymtracker.db")
                .build().also { instance = it }
        }

        internal fun closeForTests() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}

// ============================================================================
// MODEL <-> ROOM MAPPING
// ============================================================================

/** Everything that lives in Room, in model form. Also the shape of the backup's data sections. */
data class StoreData(
    val history: List<CompletedWorkout> = emptyList(),
    val weights: List<WeightLogEntry> = emptyList(),
    val templates: Map<String, List<Pair<String, String>>> = emptyMap(),
    val notes: Map<String, String> = emptyMap(),
    val restDefaults: Map<String, Int> = emptyMap()
)

fun exerciseKey(name: String): String = name.trim().lowercase(Locale.US)

/**
 * Blocking Room operations in model terms. Callers run these off the main thread
 * (LocalStorage uses its own database thread); tests call them directly.
 */
object RoomStore {

    /** Notes and rest defaults are keyed by [exerciseKey], newest workouts and weigh-ins first. */
    fun load(db: GymDatabase): StoreData {
        val dao = db.dao()
        val exercises = dao.exercises().associateBy { it.id }
        val setsByExercise = dao.workoutSets().groupBy { it.workoutExerciseId }
        val exercisesByWorkout = dao.workoutExercises().groupBy { it.workoutId }

        val history = dao.workouts().map { w ->
            CompletedWorkout(
                id = w.id, splitTitle = w.splitTitle, dateKey = w.dateKey, displayDate = w.displayDate,
                durationSeconds = w.durationSeconds, bodyWeightKg = w.bodyWeightKg, photoPath = w.photoPath,
                skippedExercises = w.skippedExercises,
                exercises = exercisesByWorkout[w.id].orEmpty().sortedBy { it.position }.map { we ->
                    ExerciseEntry(
                        name = exercises[we.exerciseId]?.name.orEmpty(),
                        targetMuscle = we.targetMuscle,
                        restSeconds = we.restSeconds,
                        isSkipped = we.isSkipped,
                        isSuperset = we.isSuperset,
                        sets = setsByExercise[we.id].orEmpty().sortedBy { it.position }.map { s ->
                            SetEntry(
                                setNumber = s.setNumber, weight = s.weight, reps = s.reps, isWarmup = s.isWarmup,
                                intensityType = s.intensityType, isCompleted = s.isCompleted, restSeconds = s.restSeconds
                            )
                        }
                    )
                },
                totalVolumeKg = w.totalVolumeKg, prsHit = w.prsHit
            )
        }.sortedByDescending { it.id }

        val weights = dao.bodyWeights()
            .map { WeightLogEntry(it.id, it.dateKey, it.displayDate, it.weightKg) }
            .sortedByDescending { it.id }

        val templates = dao.templateItems().groupBy { it.splitTitle }.mapValues { (_, items) ->
            items.map { (exercises[it.exerciseId]?.name.orEmpty()) to it.muscle }
        }

        return StoreData(
            history = history,
            weights = weights,
            templates = templates,
            notes = exercises.values.filter { it.note.isNotBlank() }.associate { it.nameKey to it.note },
            restDefaults = exercises.values.mapNotNull { e -> e.defaultRestSeconds?.let { e.nameKey to it } }.toMap()
        )
    }

    /** Wipes Room and writes [data] in one transaction. Used by the first-run migration and backup restore. */
    fun replaceAll(db: GymDatabase, data: StoreData) = db.runInTransaction {
        val dao = db.dao()
        dao.clearTemplates()
        dao.clearWorkouts() // cascades to exercises-in-workout and sets
        dao.clearBodyWeights()
        dao.clearExercises()

        // The first spelling of a name wins for differently-cased duplicates, so go templates
        // first, then oldest workouts: that keeps how the user originally typed it.
        data.templates.forEach { (split, items) -> saveTemplate(db, split, items) }
        data.history.sortedBy { it.id }.forEach { insertWorkout(db, it) }
        data.weights.forEach { dao.insertBodyWeight(BodyWeightEntity(it.id, it.dateKey, it.displayDate, it.weightKg)) }
        data.notes.forEach { (key, note) -> setNote(db, key, note) }
        data.restDefaults.forEach { (key, sec) -> setDefaultRest(db, key, sec) }
    }

    /** Finds the exercise by name (case-insensitive) or creates it, returning its id. */
    fun exerciseId(db: GymDatabase, name: String, muscle: String = ""): Long {
        val dao = db.dao()
        val key = exerciseKey(name)
        dao.exerciseByKey(key)?.let { return it.id }
        // Notes arrive keyed by lowercase name, so take spelling and muscle from the library when we can.
        val library = ExerciseDatabase.find(name)
        return dao.insertExercise(
            ExerciseEntity(name = library?.name ?: name.trim(), nameKey = key, muscle = library?.muscle ?: muscle)
        )
    }

    /** Inserts the workout, replacing any stored workout with the same id. */
    fun insertWorkout(db: GymDatabase, workout: CompletedWorkout) = db.runInTransaction {
        val dao = db.dao()
        dao.deleteWorkout(workout.id)
        dao.insertWorkout(
            WorkoutEntity(
                id = workout.id, splitTitle = workout.splitTitle, dateKey = workout.dateKey, displayDate = workout.displayDate,
                durationSeconds = workout.durationSeconds, bodyWeightKg = workout.bodyWeightKg, photoPath = workout.photoPath,
                skippedExercises = workout.skippedExercises, totalVolumeKg = workout.totalVolumeKg, prsHit = workout.prsHit
            )
        )
        workout.exercises.forEachIndexed { i, ex ->
            val rowId = dao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutId = workout.id, exerciseId = exerciseId(db, ex.name, ex.targetMuscle), position = i,
                    targetMuscle = ex.targetMuscle, restSeconds = ex.restSeconds, isSkipped = ex.isSkipped, isSuperset = ex.isSuperset
                )
            )
            dao.insertSets(ex.sets.mapIndexed { j, s ->
                WorkoutSetEntity(
                    workoutExerciseId = rowId, position = j, setNumber = s.setNumber, weight = s.weight, reps = s.reps,
                    intensityType = s.intensityType, isWarmup = s.isWarmup, isCompleted = s.isCompleted, restSeconds = s.restSeconds
                )
            })
        }
    }

    fun deleteWorkout(db: GymDatabase, id: Long) = db.dao().deleteWorkout(id)

    fun addBodyWeight(db: GymDatabase, entry: WeightLogEntry) =
        db.dao().insertBodyWeight(BodyWeightEntity(entry.id, entry.dateKey, entry.displayDate, entry.weightKg))

    fun deleteBodyWeight(db: GymDatabase, id: Long) = db.dao().deleteBodyWeight(id)

    fun saveTemplate(db: GymDatabase, splitTitle: String, items: List<Pair<String, String>>) = db.runInTransaction {
        val dao = db.dao()
        dao.deleteTemplate(splitTitle)
        dao.insertTemplateItems(items.mapIndexed { i, (name, muscle) ->
            SplitTemplateItemEntity(splitTitle, i, exerciseId(db, name, muscle), muscle)
        })
    }

    fun setNote(db: GymDatabase, name: String, note: String) =
        db.dao().setExerciseNote(exerciseId(db, name), note.trim())

    fun setDefaultRest(db: GymDatabase, name: String, sec: Int) =
        db.dao().setExerciseRest(exerciseId(db, name), sec)

    /**
     * Renames an exercise everywhere (history, templates, notes). Returns false if another
     * exercise already has that name, since merging two histories is a separate decision.
     */
    fun renameExercise(db: GymDatabase, oldName: String, newName: String): Boolean {
        val dao = db.dao()
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        var ok = false
        db.runInTransaction {
            val existing = dao.exerciseByKey(exerciseKey(oldName)) ?: return@runInTransaction
            val clash = dao.exerciseByKey(exerciseKey(trimmed))
            if (clash != null && clash.id != existing.id) return@runInTransaction
            dao.renameExercise(existing.id, trimmed, exerciseKey(trimmed))
            ok = true
        }
        return ok
    }
}
