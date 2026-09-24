package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class PartnerWorkoutTest {
    private val curl = Exercise(id = "curl", name = "Curl")
    private val squat = Exercise(id = "squat", name = "Squat")
    private val base = Workout(name = "Shared", partners = listOf(WorkoutPartner("alex", "Alex", true), WorkoutPartner("sam", "Sam", false)))

    @Test fun newExercisesMirrorToYouAndTrackedPartnersByDefault() {
        val entry = base.newEntry(curl, null)
        assertEquals(listOf(null, "alex"), entry.sets.map { it.participantId })
        assertTrue(entry.skippedBy.isEmpty())
    }

    @Test fun partnerOnlyExerciseSkipsEveryoneElseIncludingUntrackedPartners() {
        val entry = base.newEntry(curl, null, partnerOnly = "alex")
        assertEquals(listOf("alex"), entry.sets.map { it.participantId })
        assertFalse(entry.includes(null))
        assertFalse(entry.includes("sam"))
        assertTrue(entry.includes("alex"))
    }

    @Test fun skippingClearsOnlyThatPersonAndIncludingRestoresASetGroup() {
        val entry = base.newEntry(curl, null).let { e -> e.copy(sets = e.sets.map { if (it.participantId == null) it.copy(weight = "25") else it.copy(weight = "15") }) }
        val w = base.copy(exercises = listOf(entry))
        val skipped = w.skipExercise(entry.id, "alex")
        val after = skipped.exercises.single()
        assertFalse(after.includes("alex"))
        assertEquals(listOf("25"), after.sets.map { it.weight })
        val restored = skipped.includeExercise(entry.id, "alex").exercises.single()
        assertTrue(restored.includes("alex"))
        assertEquals(1, restored.sets.count { it.participantId == "alex" })
        val included = skipped.includeExercise(entry.id, "alex")
        assertEquals(included, included.includeExercise(entry.id, "alex"))
    }

    @Test fun completedSetsBlockSkipping() {
        val entry = base.newEntry(curl, null).let { e -> e.copy(sets = e.sets.map { it.copy(weight = "20", reps = "8", complete = it.participantId == "alex") }) }
        val w = base.copy(exercises = listOf(entry))
        assertThrows(IllegalArgumentException::class.java) { w.skipExercise(entry.id, "alex") }
        assertFalse(w.skipExercise(entry.id, null).exercises.single().includes(null))
    }

    @Test fun modeChangeRebuildsSetsOnlyForPeopleDoingTheExercise() {
        val entry = base.newEntry(curl, null).copy(skippedBy = listOf(SELF_PARTICIPANT))
        val sets = base.startingSets(entry, ExerciseMode.SINGLE_ARM)
        assertEquals(listOf("alex", "alex"), sets.map { it.participantId })
        assertEquals(listOf(Side.LEFT, Side.RIGHT), sets.map { it.side })
    }

    @Test fun repeatingDropsPartnerOnlyExercisesAndResetsSkips() {
        val shared = base.newEntry(squat, null).let { it.copy(skippedBy = listOf("alex"), sets = it.sets.filter { s -> s.participantId == null }) }
        val w = base.copy(exercises = listOf(shared, base.newEntry(curl, null, partnerOnly = "alex")))
        val repeat = w.freshCopy(false)
        assertEquals(listOf("Squat"), repeat.exercises.map { it.exercise.name })
        assertTrue(repeat.exercises.single().skippedBy.isEmpty())
    }

    @Test fun legacyPayloadsDecodeWithDefaults() {
        val partner = storageJson.decodeFromString<Partner>("""{"id":"p","name":"Alex","archived":false}""")
        assertEquals("", partner.goal)
        assertEquals("", partner.notes)
        val entry = storageJson.decodeFromString<WorkoutExercise>("""{"id":"e","exercise":{"id":"x","name":"Row"},"sets":[]}""")
        assertTrue(entry.skippedBy.isEmpty())
        assertTrue(entry.includes(null))
    }

    @Test fun personSummaryCountsOnlyThatPersonsCompletedSets() {
        fun session(at: Long, participant: String?) = Workout(name = "S", unit = "kg", startedAt = at, finishedAt = at + 1, exercises = listOf(
            WorkoutExercise(exercise = curl, sets = listOf(SetEntry(weight = "10", reps = "10", complete = true, participantId = participant), SetEntry(weight = "10", reps = "10", participantId = participant)))))
        val summary = personSummary(completedHistory(listOf(session(100, "alex"), session(200, "alex"), session(300, null)), "alex"))
        assertEquals(PersonSummary(2, 2, 200.0, 200), summary)
        assertEquals(PersonSummary(0, 0, 0.0, null), personSummary(emptyList()))
    }
}
