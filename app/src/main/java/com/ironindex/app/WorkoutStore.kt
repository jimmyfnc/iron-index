package com.ironindex.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// One row is an atomic workout aggregate. No half-written session/exercise/set trees.
@Entity(tableName = "workouts")
data class WorkoutRow(@PrimaryKey val id: String, val payload: String)

@Entity(tableName = "exercises")
data class ExerciseRow(@PrimaryKey val id: String, val payload: String)

@Entity(tableName = "equipment")
data class EquipmentRow(@PrimaryKey val id: String, val payload: String)

@Entity(tableName = "partners")
data class PartnerRow(@PrimaryKey val id: String, val payload: String)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts") fun observeWorkouts(): Flow<List<WorkoutRow>>
    @Query("SELECT * FROM exercises") fun observeExercises(): Flow<List<ExerciseRow>>
    @Query("SELECT * FROM workouts WHERE id = :id") suspend fun get(id: String): WorkoutRow?
    @Query("SELECT * FROM workouts") suspend fun all(): List<WorkoutRow>
    @Upsert suspend fun put(row: WorkoutRow)
    @Upsert suspend fun putExercise(row: ExerciseRow)
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM equipment") fun observeEquipment(): Flow<List<EquipmentRow>>
    @Query("SELECT * FROM equipment") suspend fun allEquipment(): List<EquipmentRow>
    @Query("SELECT * FROM partners") fun observePartners(): Flow<List<PartnerRow>>
    @Upsert suspend fun putEquipment(row: EquipmentRow)
    @Upsert suspend fun putPartner(row: PartnerRow)
}

val storageJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `equipment` (`id` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `partners` (`id` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
        val updates = mutableListOf<Workout>()
        db.query("SELECT payload FROM workouts").use { cursor ->
            while (cursor.moveToNext()) {
                val workout = storageJson.decodeFromString<Workout>(cursor.getString(0))
                updates += workout.copy(exercises = workout.exercises.map { entry ->
                    val equipment = entry.equipmentProfile()
                    entry.copy(equipmentId = equipment?.id, equipmentSnapshot = equipment)
                })
            }
        }
        val equipment = mutableListOf<Equipment>()
        updates.forEach { workout ->
            equipment += workout.exercises.mapNotNull { it.equipmentSnapshot }
            db.execSQL("UPDATE workouts SET payload = ? WHERE id = ?", arrayOf(storageJson.encodeToString(workout), workout.id))
        }
        db.query("SELECT payload FROM exercises").use { cursor ->
            while (cursor.moveToNext()) legacyEquipment(storageJson.decodeFromString<Exercise>(cursor.getString(0)))?.let { equipment += it }
        }
        equipment.distinctBy { it.id }.forEach {
            db.execSQL("INSERT OR IGNORE INTO equipment(id, payload) VALUES (?, ?)", arrayOf(it.id, storageJson.encodeToString(it)))
        }
    }
}

@Database(entities = [WorkoutRow::class, ExerciseRow::class, EquipmentRow::class, PartnerRow::class], version = 2, exportSchema = true)
abstract class IronIndexDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao
}

class WorkoutStore(context: Context, databaseName: String = "iron-index.db") {
    private val db = Room.databaseBuilder(context.applicationContext, IronIndexDatabase::class.java, databaseName).addMigrations(MIGRATION_1_2).build()
    private val dao = db.dao()
    private val mutex = Mutex()
    private val json = storageJson
    val workouts = dao.observeWorkouts().map { rows -> rows.map { json.decodeFromString<Workout>(it.payload) }.sortedByDescending { it.startedAt } }
    val exercises = dao.observeExercises().map { rows ->
        (rows.map { json.decodeFromString<Exercise>(it.payload) } + starterExercises).distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }
    val equipment = dao.observeEquipment().map { rows ->
        (rows.map { json.decodeFromString<Equipment>(it.payload) } + starterExercises.mapNotNull { legacyEquipment(it) })
            .distinctBy { it.id }.sortedBy { it.label.lowercase() }
    }
    val partners = dao.observePartners().map { rows -> rows.map { json.decodeFromString<Partner>(it.payload) }.sortedBy { it.name.lowercase() } }
    private suspend fun write(workout: Workout) = dao.put(WorkoutRow(workout.id, json.encodeToString(workout)))
    suspend fun save(workout: Workout) = mutex.withLock { write(workout) }
    suspend fun edit(id: String, transform: (Workout) -> Workout) = mutex.withLock {
        dao.get(id)?.let { row ->
            val before = json.decodeFromString<Workout>(row.payload)
            write(transform(before).stampStart(before).copy(updatedAt = System.currentTimeMillis()))
        }
    }
    private suspend fun draft() = dao.all().map { json.decodeFromString<Workout>(it.payload) }.firstOrNull { !it.isTemplate && it.finishedAt == null }
    suspend fun activeDraft(): Workout? = mutex.withLock { draft() }
    /**
     * Opens the one unfinished workout, or creates it. Starting a routine/repeat replaces an empty draft;
     * a draft with exercises is replaced only when [replaceDraft] is true (the user chose to discard it).
     */
    suspend fun start(source: Workout?, unit: String, replaceDraft: Boolean = false): String = mutex.withLock {
        val existing = draft()
        if (existing != null) {
            if (!replaceDraft && (source == null || existing.exercises.isNotEmpty())) return@withLock existing.id
            dao.delete(existing.id)
        }
        val workout = source?.freshCopy(false) ?: Workout(name = "Freestyle workout", unit = unit)
        write(workout)
        workout.id
    }
    suspend fun saveExercise(exercise: Exercise) = dao.putExercise(ExerciseRow(exercise.id, json.encodeToString(exercise)))
    suspend fun saveEquipment(value: Equipment): Equipment = mutex.withLock {
        val existing = dao.allEquipment().map { json.decodeFromString<Equipment>(it.payload) }
            .firstOrNull { it.identity == value.identity && it.id != value.id }
        val saved = existing?.copy(archived = false) ?: value
        dao.putEquipment(EquipmentRow(saved.id, json.encodeToString(saved)))
        saved
    }
    suspend fun savePartner(partner: Partner) = dao.putPartner(PartnerRow(partner.id, json.encodeToString(partner)))
    suspend fun delete(id: String) = mutex.withLock { dao.delete(id) }
    fun close() = db.close()
}
