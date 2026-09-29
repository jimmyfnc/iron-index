package com.ironindex.app

/**
 * Fills a newly added entry with each person's sets from the last finished session of the same exercise on
 * the same equipment and sides mode, unchecked, so logging a repeat is tap-to-check. People with no such
 * session keep the entry's blank starting sets.
 *
 * Numbers keep their meaning: if that session was logged in the other unit they're converted, and if the
 * machine's starting weight or logging style changed since, typed weights are re-based to the same totals.
 */
fun WorkoutExercise.prefilledFrom(history: List<Workout>, workout: Workout): WorkoutExercise {
    // Editing a past session looks only at sessions before it, like its "Last time" line; a live draft uses everything.
    val finished = history.filter { !it.isTemplate && it.finishedAt != null && it.id != workout.id && (workout.finishedAt == null || it.startedAt < workout.startedAt) }
        .sortedByDescending { it.startedAt }
    val people = sets.map { it.participantId }.distinct()
    return copy(sets = people.flatMap { person ->
        fun WorkoutExercise.matches() = exercise.id == this@prefilledFrom.exercise.id && equipmentKey() == this@prefilledFrom.equipmentKey() &&
            exercise.mode == this@prefilledFrom.exercise.mode && sets.any { it.counts && it.participantId == person }
        val session = finished.firstOrNull { w -> w.exercises.any { it.matches() } } ?: return@flatMap sets.filter { it.participantId == person }
        // Every matching entry that day (for example heavy sets, then back-off sets), in order.
        val entries = session.exercises.filter { it.matches() }
        val theirs = entries.first().copy(sets = entries.flatMap { e -> e.sets.filter { it.counts && it.participantId == person } })
        // Re-base onto today's machine settings (in that session's unit), then convert units if they differ.
        val rebased = equipmentProfile()?.let { theirs.rebasedTo(it, session.unit) } ?: theirs
        val groups = mutableMapOf<String, String>()
        rebased.sets.map { s ->
            val weight = s.weight.toWeightOrNull()?.let { if (session.unit == workout.unit) s.weight else weightText(convertWeight(it, session.unit, workout.unit)) }.orEmpty()
            SetEntry(reps = s.reps, weight = weight, participantId = person, side = s.side, groupId = groups.getOrPut(s.groupId) { newId() })
        }
    })
}
