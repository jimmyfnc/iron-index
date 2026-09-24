package com.ironindex.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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
    @Query("SELECT * FROM exercises") suspend fun allExercises(): List<ExerciseRow>
    @Query("SELECT * FROM partners") suspend fun allPartners(): List<PartnerRow>
    @Upsert suspend fun putEquipment(row: EquipmentRow)
    @Upsert suspend fun putPartner(row: PartnerRow)
}

val storageJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Decodes each stored row, skipping (and reporting) any that can't be read so one bad row can't take down the app. */
inline fun <T> List<String>.decodeEach(onError: (Throwable) -> Unit = {}, decode: (String) -> T): List<T> =
    mapNotNull { payload -> try { decode(payload) } catch (e: Exception) { onError(e); null } }

private fun logUnreadable(e: Throwable) { runCatching { android.util.Log.w("IronIndex", "Skipped an unreadable saved row", e) } }

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
    val workouts = dao.observeWorkouts().map { rows -> rows.map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Workout>(it) }.sortedByDescending { it.startedAt } }
        .flowOn(Dispatchers.Default)
    val exercises = dao.observeExercises().map { rows ->
        (rows.map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Exercise>(it) } + starterExercises).distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default)
    val equipment = dao.observeEquipment().map { rows ->
        (rows.map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Equipment>(it) } + starterExercises.mapNotNull { legacyEquipment(it) })
            .distinctBy { it.id }.sortedBy { it.label.lowercase() }
    }.flowOn(Dispatchers.Default)
    val partners = dao.observePartners().map { rows -> rows.map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Partner>(it) }.sortedBy { it.name.lowercase() } }
        .flowOn(Dispatchers.Default)
    private suspend fun write(workout: Workout) = dao.put(WorkoutRow(workout.id, json.encodeToString(workout)))
    suspend fun save(workout: Workout) = mutex.withLock { write(workout) }
    suspend fun edit(id: String, transform: (Workout) -> Workout) = mutex.withLock {
        dao.get(id)?.let { row ->
            val before = json.decodeFromString<Workout>(row.payload)
            write(transform(before).stampStart(before).copy(updatedAt = System.currentTimeMillis()))
        }
    }
    private suspend fun draft() = dao.all().map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Workout>(it) }.firstOrNull { !it.isTemplate && it.finishedAt == null }
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
        val existing = dao.allEquipment().map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Equipment>(it) }
            .firstOrNull { it.identity == value.identity && it.id != value.id }
        val saved = existing?.copy(archived = false) ?: value
        dao.putEquipment(EquipmentRow(saved.id, json.encodeToString(saved)))
        saved
    }
    private class Snapshot(val workouts: List<Workout>, val exercises: List<Exercise>, val equipment: List<Equipment>, val partners: List<Partner>) {
        fun plan(backup: Backup) = planImport(backup, workouts, exercises, equipment, partners)
    }
    private suspend fun snapshot() = Snapshot(
        dao.all().map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Workout>(it) },
        dao.allExercises().map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Exercise>(it) },
        dao.allEquipment().map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Equipment>(it) },
        dao.allPartners().map { it.payload }.decodeEach(::logUnreadable) { json.decodeFromString<Partner>(it) },
    )

    /** Everything the user has created, read under the edit lock so the copy is consistent. */
    suspend fun exportBackup(appVersion: String): Backup = mutex.withLock {
        val now = snapshot()
        Backup(appVersion = appVersion, workouts = now.workouts, exercises = now.exercises, equipment = now.equipment, partners = now.partners)
    }

    suspend fun planImport(backup: Backup): ImportPlan = mutex.withLock { snapshot().plan(backup) }

    /** Writes an import in one transaction: all of it lands, or none of it does. Re-plans under the lock so edits made meanwhile are respected. */
    suspend fun applyImport(backup: Backup): ImportPlan = mutex.withLock {
        val plan = snapshot().plan(backup)
        db.withTransaction {
            plan.workouts.forEach { write(it) }
            plan.exercises.forEach { dao.putExercise(ExerciseRow(it.id, json.encodeToString(it))) }
            plan.equipment.forEach { dao.putEquipment(EquipmentRow(it.id, json.encodeToString(it))) }
            plan.partners.forEach { dao.putPartner(PartnerRow(it.id, json.encodeToString(it))) }
        }
        plan
    }
    suspend fun savePartner(partner: Partner) = dao.putPartner(PartnerRow(partner.id, json.encodeToString(partner)))
    suspend fun delete(id: String) = mutex.withLock { dao.delete(id) }
    fun close() = db.close()
}
