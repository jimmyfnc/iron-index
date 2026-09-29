package com.ironindex.app

/**
 * What IronIndex shares with Health Connect for one finished session. Only your own training is
 * shared — partners' sets stay here — and only the outline (name, time, type), never sets or weights.
 */
data class HealthSession(
    /** Stable per workout, so edits and date moves update the same Health Connect record. */
    val clientId: String,
    /** Grows with every change ([Workout.updatedAt]); Health Connect keeps the highest version. */
    val version: Long,
    val start: Long,
    val end: Long,
    val title: String,
    val notes: String,
)

private const val HEALTH_ID_PREFIX = "ironindex-workout-"
fun healthClientId(workoutId: String) = HEALTH_ID_PREFIX + workoutId

/** Health Connect rejects sessions that end where they start; imported or instant sessions get a minute. */
private const val MIN_SESSION_MS = 60_000L

/**
 * The session to share, or null when there's nothing of yours to share: routines, workouts in progress,
 * and sessions where only partners logged sets.
 */
fun Workout.healthSession(): HealthSession? {
    val finished = finishedAt ?: return null
    if (isTemplate) return null
    val sets = completedSetsFor(null)
    if (sets == 0) return null
    val exercises = exercises.count { e -> e.sets.any { it.counts && it.participantId == null } }
    return HealthSession(
        clientId = healthClientId(id),
        version = updatedAt,
        start = startedAt,
        end = maxOf(finished, startedAt + MIN_SESSION_MS),
        title = name,
        notes = "${count(exercises, "exercise")} · ${count(sets, "set")} · logged in IronIndex",
    )
}

/** What a sync must do: write new or changed sessions (by workout id), remove ids that are no longer shareable. */
data class HealthPlan(val write: Map<String, HealthSession>, val remove: List<String>, val shared: Int)

/**
 * Compares shareable [finishedSessions] with what was already shared ([done]: workout id → version).
 * Unchanged sessions are skipped, so a record the lifter deleted in another app isn't pushed back
 * until the session itself changes; anything shared that's gone or no longer yours is removed.
 */
fun healthPlan(finishedSessions: List<Workout>, done: Map<String, Long>): HealthPlan {
    val wanted = finishedSessions.mapNotNull { w -> w.healthSession()?.let { w.id to it } }.toMap()
    return HealthPlan(
        write = wanted.filter { (id, session) -> done[id] != session.version },
        remove = (done.keys - wanted.keys).sorted(),
        shared = wanted.size,
    )
}
