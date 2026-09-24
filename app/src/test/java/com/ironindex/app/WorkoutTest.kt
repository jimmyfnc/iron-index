package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutTest {
    @Test fun onlyValidSetsCanBeLogged() {
        assertTrue(SetEntry(reps = "10", weight = "0").valid())
        assertTrue(SetEntry(reps = "8", weight = "22.5").valid())
        listOf("", "-1", "NaN", "Infinity", "100000", "abc").forEach { assertFalse(SetEntry(reps = "10", weight = it).valid()) }
        listOf("", "0", "-1", "1.5", "10000", "abc").forEach { assertFalse(SetEntry(reps = it, weight = "50").valid()) }
    }

    @Test fun repeatingPreservesEquipmentAndTargetsButResetsIdentityAndCompletion() {
        val old = Workout(name = "Push day", unit = "kg", finishedAt = 100,
            exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Chest press", equipment = "Plate-loaded", brand = "Hammer Strength"),
                sets = listOf(SetEntry(reps = "10", weight = "25", complete = true)))))
        val repeat = old.freshCopy(false)
        assertNotEquals(old.id, repeat.id)
        assertNull(repeat.finishedAt)
        assertEquals("kg", repeat.unit)
        assertEquals(old.exercises[0].exercise, repeat.exercises[0].exercise)
        assertNotEquals(old.exercises[0].id, repeat.exercises[0].id)
        assertNotEquals(old.exercises[0].sets[0].id, repeat.exercises[0].sets[0].id)
        assertEquals("25", repeat.exercises[0].sets[0].weight)
        assertFalse(repeat.exercises[0].sets[0].complete)
        assertTrue(old.exercises[0].sets[0].complete)
    }

    @Test fun volumeExcludesUnfinishedSets() {
        val workout = Workout(name = "Test", exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Press"), sets = listOf(
            SetEntry(reps = "10", weight = "22.5", complete = true),
            SetEntry(reps = "10", weight = "100", complete = false),
            SetEntry(reps = "8", weight = "0", complete = true),
        ))))
        assertEquals(225.0, workout.volume, 0.001)
        assertEquals(2, workout.completedSets)
    }

    @Test fun serializationPreservesPartialInputForResuming() {
        val workout = Workout(name = "Draft", exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Row"),
            sets = listOf(SetEntry(reps = "", weight = "2.")))))
        // Same encoder as storage: it writes defaults, so time-based defaults can't be regenerated on decode.
        assertEquals(workout, storageJson.decodeFromString<Workout>(storageJson.encodeToString(workout)))
    }

    @Test fun commaDecimalsAreAcceptedAndUnitLocksOnceAWeightIsEntered() {
        assertEquals(22.5, "22,5".toWeightOrNull()!!, 0.0)
        assertTrue(SetEntry(reps = "5", weight = "22,5").valid())
        assertNull("1,2,3".toWeightOrNull())
        val w = Workout(name = "U", exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Row"), sets = listOf(SetEntry(reps = "5")))))
        assertFalse(w.unitLocked)
        assertTrue(w.copy(exercises = w.exercises.map { it.copy(sets = listOf(SetEntry(weight = "20"))) }).unitLocked)
    }

    @Test fun sessionDateIsStampedAtTheFirstCheckedSetOnly() {
        val draft = Workout(name = "D", startedAt = 1, exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Row"), sets = listOf(SetEntry(reps = "5", weight = "20")))))
        val checked = draft.copy(exercises = draft.exercises.map { it.copy(sets = it.sets.map { s -> s.copy(complete = true) }) })
        assertEquals(500, checked.stampStart(draft, now = 500).startedAt)
        assertEquals(1, draft.stampStart(draft, now = 500).startedAt)
        assertEquals(1, checked.stampStart(checked, now = 500).startedAt)
        assertEquals(1, checked.copy(finishedAt = 2).stampStart(draft, now = 500).startedAt)
        assertEquals(1, checked.copy(isTemplate = true).stampStart(draft, now = 500).startedAt)
    }

    @Test fun defaultUnitFollowsRegion() {
        assertEquals("lb", defaultUnit("US"))
        assertEquals("kg", defaultUnit("DE"))
        assertEquals("kg", defaultUnit("GB"))
    }

    @Test fun onlyRealRenamesAskAboutExerciseIdentity() {
        val bench = Exercise(name = "Bench press")
        assertFalse(bench.renamedTo("bench press "))
        assertFalse(bench.renamedTo(""))
        assertTrue(bench.renamedTo("Incline DB press"))
    }

    @Test fun movingASessionKeepsTimeOfDayAndDuration() {
        val zone = java.time.ZoneOffset.UTC
        val start = java.time.LocalDateTime.of(2026, 9, 24, 18, 30).toInstant(zone).toEpochMilli()
        val w = Workout(name = "Late", startedAt = start, finishedAt = start + 3_600_000)
        val moved = w.movedTo(java.time.LocalDate.of(2026, 9, 21), zone)
        assertEquals(java.time.LocalDateTime.of(2026, 9, 21, 18, 30).toInstant(zone).toEpochMilli(), moved.startedAt)
        assertEquals(3_600_000L, moved.finishedAt!! - moved.startedAt)
    }

    @Test fun movingToTodayNeverEndsInTheFuture() {
        val zone = java.time.ZoneOffset.UTC
        val evening = java.time.LocalDateTime.of(2026, 9, 20, 18, 30).toInstant(zone).toEpochMilli()
        val now = java.time.LocalDateTime.of(2026, 9, 24, 9, 0).toInstant(zone).toEpochMilli()
        val moved = Workout(name = "W", startedAt = evening, finishedAt = evening + 3_600_000).movedTo(java.time.LocalDate.of(2026, 9, 24), zone, now)
        assertEquals(now, moved.finishedAt)
        assertEquals(now - 3_600_000, moved.startedAt)
    }

    @Test fun aCheckedSetBeingCorrectedDoesNotCountUntilValid() {
        val blank = SetEntry(weight = "", reps = "5", complete = true)
        assertFalse(blank.counts)
        val w = Workout(name = "W", exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Row"), sets = listOf(blank, blank.copy(id = newId(), weight = "100")))))
        assertEquals(1, w.allCompletedSets)
        assertEquals(500.0, w.volume, 0.0)
        assertTrue(blank.copy(weight = "225").counts)
    }

    @Test fun contentComparisonIgnoresSaveBookkeeping() {
        val w = Workout(name = "S", updatedAt = 1, exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Row"))))
        assertTrue(w.sameContentAs(w.copy(updatedAt = 99, editedAt = 5)))
        assertFalse(w.sameContentAs(w.copy(name = "T")))
    }
}
