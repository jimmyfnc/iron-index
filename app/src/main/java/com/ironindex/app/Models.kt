package com.ironindex.app

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun count(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"

@Serializable
data class Exercise(
    val id: String = newId(),
    val name: String,
    val equipment: String = "",
    val brand: String = "",
    val muscleGroup: String = "",
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val target: String = "",
    val mode: ExerciseMode = ExerciseMode.BILATERAL,
    val defaultEquipmentId: String? = null,
)

@Serializable enum class ExerciseMode { BILATERAL, SINGLE_ARM, SINGLE_LEG }
@Serializable enum class Side { BOTH, LEFT, RIGHT }

@Serializable
data class Equipment(
    val id: String = newId(),
    val type: String,
    val brand: String = "",
    val model: String = "",
    val location: String = "",
    val archived: Boolean = false,
) {
    val label get() = listOf(type, brand, model, location).filter { it.isNotBlank() }.joinToString(" · ")
    val identity get() = listOf(type, brand, model, location).joinToString("|") { it.trim().lowercase(java.util.Locale.ROOT) }
}

@Serializable data class Partner(val id: String = newId(), val name: String, val archived: Boolean = false, val goal: String = "", val notes: String = "")
@Serializable data class WorkoutPartner(val id: String, val name: String, val trackSets: Boolean = false)

/** Stable IDs connect legacy equipment across sessions without guessing brand/model splits. */
fun legacyEquipment(exercise: Exercise): Equipment? {
    if (exercise.equipment.isBlank() && exercise.brand.isBlank()) return null
    val value = Equipment(type = exercise.equipment.trim(), brand = exercise.brand.trim())
    return value.copy(id = UUID.nameUUIDFromBytes(value.identity.toByteArray(Charsets.UTF_8)).toString())
}

fun Exercise.primaryTargets(): List<String> = (primaryMuscles.ifEmpty { listOf(muscleGroup) })
    .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }

@Serializable
data class SetEntry(
    val id: String = newId(),
    val reps: String = "",
    val weight: String = "",
    val complete: Boolean = false,
    val participantId: String? = null,
    val side: Side = Side.BOTH,
    val groupId: String = id,
) {
    fun valid(): Boolean = reps.toIntOrNull()?.let { it in 1..9999 } == true &&
        weight.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..99999.0 } == true
}

@Serializable
data class WorkoutExercise(
    val id: String = newId(),
    val exercise: Exercise,
    val sets: List<SetEntry> = listOf(SetEntry()),
    val equipmentId: String? = null,
    val equipmentSnapshot: Equipment? = null,
    /** People not doing this entry. You are [SELF_PARTICIPANT]; partners use their profile id. */
    val skippedBy: List<String> = emptyList(),
)

const val SELF_PARTICIPANT = "self"
private fun String?.participantKey() = this ?: SELF_PARTICIPANT

fun WorkoutExercise.equipmentProfile(): Equipment? = equipmentSnapshot ?: legacyEquipment(exercise)
fun WorkoutExercise.equipmentKey(): String? = equipmentId ?: equipmentProfile()?.id
fun WorkoutExercise.includes(participantId: String?): Boolean = participantId.participantKey() !in skippedBy

fun newSetGroup(mode: ExerciseMode, participantId: String?, previous: List<SetEntry> = emptyList()): List<SetEntry> {
    val group = newId()
    val sides = if (mode == ExerciseMode.BILATERAL) listOf(Side.BOTH) else listOf(Side.LEFT, Side.RIGHT)
    return sides.map { side ->
        val last = previous.lastOrNull { it.participantId == participantId && it.side == side }
        SetEntry(reps = last?.reps.orEmpty(), weight = last?.weight.orEmpty(), participantId = participantId, side = side, groupId = group)
    }
}

fun Workout.moveExercise(id: String, beforeId: String?): Workout {
    val entry = exercises.find { it.id == id } ?: return this
    if (beforeId == id) return this
    val remaining = exercises.filterNot { it.id == id }.toMutableList()
    val destination = beforeId?.let { target -> remaining.indexOfFirst { it.id == target }.takeIf { it >= 0 } } ?: remaining.size
    remaining.add(destination, entry)
    return copy(exercises = remaining)
}

/** One set group for you and each tracked partner still doing [entry]. */
fun Workout.startingSets(entry: WorkoutExercise, mode: ExerciseMode): List<SetEntry> =
    (listOf<String?>(null) + partners.filter { it.trackSets }.map { it.id }).filter { entry.includes(it) }.flatMap { newSetGroup(mode, it) }

/** Mirrors a new exercise to everyone, or gives it only to [partnerOnly] (for example, while viewing their chip). */
fun Workout.newEntry(exercise: Exercise, equipment: Equipment?, partnerOnly: String? = null): WorkoutExercise {
    val entry = WorkoutExercise(exercise = exercise, equipmentId = equipment?.id, equipmentSnapshot = equipment,
        skippedBy = if (partnerOnly == null) emptyList() else listOf(SELF_PARTICIPANT) + partners.map { it.id }.filter { it != partnerOnly })
    return entry.copy(sets = startingSets(entry, exercise.mode))
}

fun Workout.skipExercise(entryId: String, participantId: String?): Workout = copy(exercises = exercises.map { e ->
    if (e.id != entryId) e else {
        require(e.sets.none { it.participantId == participantId && it.complete }) { "Uncheck completed sets before skipping this exercise." }
        e.copy(sets = e.sets.filterNot { it.participantId == participantId }, skippedBy = (e.skippedBy + participantId.participantKey()).distinct())
    }
})

fun Workout.includeExercise(entryId: String, participantId: String?): Workout = copy(exercises = exercises.map { e ->
    if (e.id != entryId) e else e.copy(skippedBy = e.skippedBy - participantId.participantKey(),
        sets = if (e.sets.any { it.participantId == participantId }) e.sets else e.sets + newSetGroup(e.exercise.mode, participantId))
})

@Serializable
data class Workout(
    val id: String = newId(),
    val name: String,
    val isTemplate: Boolean = false,
    val unit: String = "lb",
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val exercises: List<WorkoutExercise> = emptyList(),
    val partners: List<WorkoutPartner> = emptyList(),
) {
    val completedSets get() = completedSetsFor(null)
    val allCompletedSets get() = exercises.sumOf { e -> e.sets.count { it.complete } }
    val volume get() = volumeFor(null)
    fun completedSetsFor(participantId: String?) = exercises.sumOf { e -> e.sets.count { it.complete && it.participantId == participantId } }
    fun volumeFor(participantId: String?) = exercises.sumOf { e -> e.sets.filter { it.complete && it.participantId == participantId }.sumOf {
        (it.weight.toDoubleOrNull() ?: 0.0) * (it.reps.toIntOrNull() ?: 0)
    } }

    fun freshCopy(template: Boolean, title: String = name): Workout = Workout(
        name = title.trim().ifBlank { "My workout" }, isTemplate = template, unit = unit,
        // Partners choose whether to join each session; don't carry their attendance, sets or partner-only exercises.
        exercises = exercises.filter { it.includes(null) }.map { e ->
            val groupIds = mutableMapOf<String, String>()
            e.copy(id = newId(), skippedBy = emptyList(), sets = e.sets.filter { it.participantId == null }.map {
                it.copy(id = newId(), groupId = groupIds.getOrPut(it.groupId) { newId() }, complete = false)
            })
        },
    )
}

val starterExercises = listOf(
    Exercise("bench-press", "Bench press", "Barbell", muscleGroup = "Chest"),
    Exercise("squat", "Squat", "Barbell", muscleGroup = "Legs"),
    Exercise("deadlift", "Deadlift", "Barbell", muscleGroup = "Back"),
    Exercise("lat-pulldown", "Lat pulldown", "Cable machine", muscleGroup = "Back"),
    Exercise("leg-press", "Leg press", "Leg press machine", muscleGroup = "Legs"),
    Exercise("chest-press", "Chest press", "Chest press machine", muscleGroup = "Chest"),
    Exercise("shoulder-press", "Shoulder press", "Dumbbells", muscleGroup = "Shoulders"),
    Exercise("row", "Seated row", "Cable machine", muscleGroup = "Back"),
    Exercise("curl", "Biceps curl", "Dumbbells", muscleGroup = "Biceps"),
    Exercise("triceps", "Triceps pushdown", "Cable machine", muscleGroup = "Triceps"),
    Exercise("lateral-raise", "Lateral raise", "Dumbbells", muscleGroup = "Shoulders"),
    Exercise("leg-curl", "Leg curl", "Leg curl machine", muscleGroup = "Legs"),
    Exercise("leg-extension", "Leg extension", "Leg extension machine", muscleGroup = "Legs"),
    Exercise("pushup", "Push-up", "Bodyweight", muscleGroup = "Chest"),
    Exercise("pullup", "Pull-up", "Pull-up bar", muscleGroup = "Back"),
)
