package com.ironindex.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

const val BACKUP_FORMAT = "ironindex-backup"
const val BACKUP_VERSION = 1
/** Refuse files larger than this before parsing. Years of training are a few MB, and parsing needs several times the file size in memory. */
const val BACKUP_MAX_BYTES = 15L * 1024 * 1024

/** A full, self-contained copy of the user's data. Starter exercises are omitted; custom ones are kept. */
@Serializable
data class Backup(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val appVersion: String = "",
    val exportedAt: Long = System.currentTimeMillis(),
    val workouts: List<Workout> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val partners: List<Partner> = emptyList(),
)

fun Backup.encode(): String = storageJson.encodeToString(this)

class BackupException(message: String) : IllegalArgumentException(message)

/** Parses a backup file, rejecting anything that isn't a complete IronIndex backup this version can read. */
fun parseBackup(text: String): Backup {
    // Editors such as Notepad may prepend a byte-order mark.
    val backup = try { storageJson.decodeFromString<Backup>(text.removePrefix("\uFEFF")) } catch (e: Exception) {
        throw BackupException("This file isn’t a readable IronIndex backup. It may be incomplete or damaged.")
    }
    if (backup.format != BACKUP_FORMAT) throw BackupException("This file isn’t an IronIndex backup.")
    if (backup.version > BACKUP_VERSION) throw BackupException("This backup was made by a newer version of IronIndex. Update the app, then import it.")
    val ids = backup.workouts.map { it.id } + backup.exercises.map { it.id } + backup.equipment.map { it.id } + backup.partners.map { it.id }
    if (ids.any { it.isBlank() }) throw BackupException("This backup has records without IDs and can’t be imported safely.")
    return backup
}

/** What an import will write. Existing data is never deleted. */
data class ImportPlan(
    val workouts: List<Workout>,
    val exercises: List<Exercise>,
    val equipment: List<Equipment>,
    val partners: List<Partner>,
    val newSessions: Int,
    val newRoutines: Int,
    val updatedWorkouts: Int,
    val unchanged: Int,
    val skippedDrafts: Int,
    /** Backup equipment/exercises that matched ones already on this phone and were linked to them instead of duplicated. */
    val matched: Int = 0,
    /** The phone already had workouts, so anything deleted since the backup was made will come back. */
    val phoneHadWorkouts: Boolean = false,
) {
    val isEmpty get() = workouts.isEmpty() && exercises.isEmpty() && equipment.isEmpty() && partners.isEmpty()
    fun summary(): String = buildList {
        if (newSessions > 0) add(count(newSessions, "new session"))
        if (newRoutines > 0) add(count(newRoutines, "new routine"))
        if (updatedWorkouts > 0) add("${count(updatedWorkouts, "newer copy")} of existing workouts")
        if (partners.isNotEmpty()) add(count(partners.size, "partner"))
        if (equipment.isNotEmpty()) add("${equipment.size} equipment")
        if (exercises.isNotEmpty()) add(count(exercises.size, "custom exercise"))
        if (matched > 0 && !isEmpty) add("${count(matched, "machine/exercise")} linked to ones already here")
    }.joinToString(" · ").ifEmpty { "Nothing new: everything in this backup is already on this phone." }
}

/**
 * Merges by ID so importing the same file twice changes nothing.
 * - Workouts: add missing ones; replace an existing one only when the backup's copy was edited later,
 *   and never let an unfinished copy replace a finished session.
 * - Only one unfinished workout may exist. A backup draft is imported only onto a phone with no workouts
 *   (a fresh restore), so a draft discarded here can't come back as the active workout.
 * - Catalog records (exercises, equipment, partners): the phone's copy wins. Backup equipment with the same
 *   type/brand/model/gym, or a custom exercise with the same name and sides, is linked to the phone's record
 *   so histories aren't split between duplicates.
 */
fun planImport(backup: Backup, existingWorkouts: List<Workout>, existingExercises: List<Exercise>, existingEquipment: List<Equipment>, existingPartners: List<Partner>): ImportPlan {
    val byId = existingWorkouts.associateBy { it.id }
    val localDraft = existingWorkouts.firstOrNull { !it.isTemplate && it.finishedAt == null }
    val exerciseIds = existingExercises.map { it.id }.toSet()
    val equipmentIds = existingEquipment.map { it.id }.toSet()
    val partnerIds = existingPartners.map { it.id }.toSet()

    val localEquipment = existingEquipment.associateBy { it.identity }
    val equipmentMap = backup.equipment.filter { it.id !in equipmentIds }
        .mapNotNull { e -> localEquipment[e.identity]?.let { e.id to it } }.toMap()
    fun exerciseKey(x: Exercise) = x.name.trim().lowercase(java.util.Locale.ROOT) to x.mode
    val localExercises = existingExercises.associateBy(::exerciseKey)
    val exerciseMap = backup.exercises.filter { it.id !in exerciseIds }
        .mapNotNull { x -> localExercises[exerciseKey(x)]?.let { x.id to it.id } }.toMap()
    fun Exercise.remapped() = copy(id = exerciseMap[id] ?: id, defaultEquipmentId = defaultEquipmentId?.let { equipmentMap[it]?.id ?: it })
    fun Workout.remapped() = if (equipmentMap.isEmpty() && exerciseMap.isEmpty()) this else copy(exercises = exercises.map { e ->
        val local = e.equipmentId?.let(equipmentMap::get)
        // Take the phone's id and label, but keep the load settings these sets were logged with so totals don't change.
        val snapshot = local?.let { l -> e.equipmentSnapshot.let { old -> l.copy(baseWeight = old?.baseWeight, baseUnit = old?.baseUnit ?: "lb", loadMode = old?.loadMode ?: LoadMode.TOTAL) } }
        e.copy(exercise = e.exercise.remapped(), equipmentId = local?.id ?: e.equipmentId, equipmentSnapshot = snapshot ?: e.equipmentSnapshot)
    })

    var newSessions = 0; var newRoutines = 0; var updated = 0; var unchanged = 0; var skippedDrafts = 0
    val workouts = backup.workouts.distinctBy { it.id }.mapNotNull { w ->
        val current = byId[w.id]
        val isDraft = !w.isTemplate && w.finishedAt == null
        when {
            current == null && isDraft && existingWorkouts.isNotEmpty() -> { skippedDrafts++; null }
            current == null -> { if (w.isTemplate) newRoutines++ else newSessions++; w.remapped() }
            current.finishedAt != null && w.finishedAt == null -> { unchanged++; null }
            isDraft && localDraft != null && localDraft.id != w.id -> { skippedDrafts++; null }
            w.updatedAt > current.updatedAt -> { updated++; w.remapped() }
            else -> { unchanged++; null }
        }
    }
    return ImportPlan(
        workouts = workouts,
        exercises = backup.exercises.distinctBy { it.id }.filter { it.id !in exerciseIds && it.id !in exerciseMap }.map { it.remapped() },
        equipment = backup.equipment.distinctBy { it.id }.filter { it.id !in equipmentIds && it.id !in equipmentMap },
        partners = backup.partners.distinctBy { it.id }.filter { it.id !in partnerIds },
        newSessions = newSessions, newRoutines = newRoutines, updatedWorkouts = updated, unchanged = unchanged, skippedDrafts = skippedDrafts,
        matched = equipmentMap.size + exerciseMap.size, phoneHadWorkouts = existingWorkouts.isNotEmpty(),
    )
}

/** Reads UTF-8 text, refusing anything over [limit] bytes without loading it all. */
fun readLimited(input: java.io.InputStream, limit: Long): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) throw BackupException("This file is too large to be an IronIndex backup.")
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}

fun backupFileName(now: java.time.LocalDate = java.time.LocalDate.now()) = "ironindex-backup-$now.json"
