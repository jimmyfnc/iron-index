package com.ironindex.app

import java.util.Locale

/** Built-in exercises ship with the app and can't be removed from the library. */
val Exercise.isBuiltIn: Boolean get() = starterExercises.any { it.id == id }

/**
 * Names that differ only in case, spacing, punctuation or a plural ending match ("Biceps Curl" / "biceps curls",
 * "Lat pull-down" / "Lat Pulldown", "Bench presses" / "Bench press"). Empty for names with no letters or digits.
 */
fun exerciseNameKey(name: String): String {
    val key = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    return when {
        key.endsWith("sses") -> key.dropLast(2)                      // presses -> press
        key.endsWith("s") && !key.endsWith("ss") -> key.dropLast(1)  // curls -> curl, but press stays press
        else -> key
    }
}

/** Groups of library exercises done the same way whose names look like the same movement, built-in first. */
fun duplicateGroups(library: List<Exercise>): List<List<Exercise>> =
    library.filter { exerciseNameKey(it.name).isNotEmpty() }.groupBy { exerciseNameKey(it.name) to it.mode }.values.filter { it.size > 1 }
        .map { group -> group.sortedWith(compareBy<Exercise>({ !it.isBuiltIn }, { it.name.lowercase(Locale.ROOT) })) }
        .sortedBy { it.first().name.lowercase(Locale.ROOT) }

/** Exercises [from] can merge into: same sides mode, not itself; likely duplicates first, then by name. */
fun mergeTargets(from: Exercise, library: List<Exercise>): List<Exercise> =
    library.filter { it.id != from.id && it.mode == from.mode }
        .sortedWith(compareBy<Exercise>({ exerciseNameKey(it.name) != exerciseNameKey(from.name) }, { it.name.lowercase(Locale.ROOT) }))

/** Why [from] can't merge into [into], or null when it can. */
fun mergeProblem(from: Exercise, into: Exercise): String? = when {
    from.id == into.id -> "Pick a different exercise to merge into."
    from.isBuiltIn -> "Built-in exercises stay in the library. Merge the other one into ${from.name} instead."
    from.mode != into.mode -> "${from.name} and ${into.name} are logged differently (both sides vs one side at a time), so their sets can't be combined."
    else -> null
}

/**
 * Every entry of exercise [fromId] becomes [into]: its name, muscles and notes. Sets, equipment, setup, people
 * and each entry's own sides mode stay exactly as logged (an entry may have been switched to one side at a time
 * before sets were entered), so history remains split by machine and sides as before.
 */
fun Workout.withExerciseMerged(fromId: String, into: Exercise): Workout {
    if (exercises.none { it.exercise.id == fromId }) return this
    return copy(exercises = exercises.map { e ->
        if (e.exercise.id != fromId) e
        else e.copy(
            exercise = into.copy(equipment = "", brand = "", mode = e.exercise.mode, defaultEquipmentId = e.exercise.defaultEquipmentId),
            // Old entries that only knew their machine through the exercise's legacy fields keep it as a snapshot.
            equipmentId = e.equipmentKey(), equipmentSnapshot = e.equipmentProfile(),
        )
    })
}
