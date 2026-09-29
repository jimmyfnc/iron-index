package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class HealthSessionTest {
    private val alex = "alex"
    private fun session(sets: List<SetEntry>, start: Long = 1_000_000, end: Long? = start + 45 * 60_000, template: Boolean = false, updatedAt: Long = 7) =
        Workout(id = "w1", name = "Push day", isTemplate = template, startedAt = start, finishedAt = end, updatedAt = updatedAt,
            exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Bench"), sets = sets), WorkoutExercise(exercise = Exercise(name = "Fly"), sets = sets.take(1))))

    private val mine = listOf(SetEntry(weight = "135", reps = "8", complete = true), SetEntry(weight = "155", reps = "5", complete = true))

    @Test fun aFinishedSessionOfYourOwnIsShared() {
        val s = session(mine).healthSession()!!
        assertEquals("ironindex-workout-w1", s.clientId)
        assertEquals(7L, s.version)
        assertEquals(1_000_000L, s.start); assertEquals(1_000_000L + 45 * 60_000, s.end)
        assertEquals("Push day", s.title)
        assertEquals("2 exercises · 3 sets · logged in IronIndex", s.notes)
    }

    @Test fun routinesDraftsAndPartnerOnlySessionsAreNotShared() {
        assertNull(session(mine, template = true).healthSession())
        assertNull(session(mine, end = null).healthSession())
        assertNull(session(listOf(SetEntry(weight = "95", reps = "10", complete = true, participantId = alex))).healthSession())
        assertNull(session(listOf(SetEntry(weight = "135", reps = "8", complete = false))).healthSession())
    }

    @Test fun partnersSetsDontCountTowardsYourSession() {
        val s = session(mine + SetEntry(weight = "95", reps = "10", complete = true, participantId = alex)).healthSession()!!
        assertEquals("2 exercises · 3 sets · logged in IronIndex", s.notes)
    }

    @Test fun aSessionThatEndsWhereItStartsLastsAMinute() {
        val s = session(mine, start = 5_000, end = 5_000).healthSession()!!
        assertEquals(65_000L, s.end)
    }

    @Test fun syncWritesOnlyNewOrChangedSessionsAndRemovesWhatsGone() {
        val a = session(mine).copy(id = "a", updatedAt = 10)
        val b = session(mine).copy(id = "b", updatedAt = 20)
        val emptied = session(emptyList()).copy(id = "c")
        // a unchanged, b edited since, c emptied, d deleted from IronIndex, e is new.
        val e = session(mine).copy(id = "e", updatedAt = 5)
        val plan = healthPlan(listOf(a, b, emptied, e), mapOf("a" to 10L, "b" to 15L, "c" to 3L, "d" to 9L))
        assertEquals(setOf("b", "e"), plan.write.keys)
        assertEquals(listOf("c", "d"), plan.remove)
        assertEquals(3, plan.shared)
        assertTrue(healthPlan(listOf(a), mapOf("a" to 10L)).let { it.write.isEmpty() && it.remove.isEmpty() })
    }
}
