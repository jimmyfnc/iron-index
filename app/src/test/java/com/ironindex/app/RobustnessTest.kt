package com.ironindex.app

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class RobustnessTest {
    @Test fun unreadableRowsAreSkippedAndReportedInsteadOfCrashing() {
        val good = Workout(name = "Good")
        val errors = mutableListOf<Throwable>()
        val rows = listOf(storageJson.encodeToString(good), "{not json", """{"id":"x","name":"Bad","unit":"lb","exercises":[{"exercise":{"id":"e","name":"Row","mode":"FUTURE_MODE"}}]}""")
        val decoded = rows.decodeEach({ errors += it }) { storageJson.decodeFromString<Workout>(it) }
        assertEquals(listOf(good), decoded)
        assertEquals(2, errors.size)
    }

    @Test fun earlierSetsAndLastSessionMatchPreviousSession() {
        val press = Exercise(id = "p", name = "Press")
        fun session(at: Long) = Workout(name = "S$at", startedAt = at, finishedAt = at + 1, exercises = listOf(
            WorkoutExercise(exercise = press, sets = listOf(SetEntry(weight = "$at", reps = "5", complete = true)))))
        val history = listOf(session(100), session(200), session(900))
        val draft = Workout(name = "Now", startedAt = 500, exercises = listOf(WorkoutExercise(exercise = press)))
        val earlier = earlierSets(completedHistory(history), draft, draft.exercises.single())
        assertEquals(listOf("100", "200"), earlier.map { it.set.weight })
        assertEquals(listOf("200"), lastSession(earlier).map { it.set.weight })
        assertEquals(lastSession(earlier), previousSession(history, draft, draft.exercises.single(), null))
    }
}
