package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AnalyticsTest {
    private val press = Exercise(id = "press", name = "Incline press", primaryMuscles = listOf("Chest"), secondaryMuscles = listOf("Triceps", "chest"))
    private val hammer = Equipment(id = "hammer", type = "Incline press", brand = "Hammer Strength", model = "A")
    private val matrix = Equipment(id = "matrix", type = "Incline press", brand = "Matrix", model = "A")
    private fun session(weight: String, unit: String = "kg", equipment: Equipment = hammer, date: Long = 100, participant: String? = null, side: Side = Side.BOTH, group: String = "round") =
        Workout(name = "Push", unit = unit, startedAt = date, finishedAt = date + 1, exercises = listOf(WorkoutExercise(exercise = press,
            equipmentId = equipment.id, equipmentSnapshot = equipment,
            sets = listOf(SetEntry(weight = weight, reps = "10", complete = true, participantId = participant, side = side, groupId = group)))))

    @Test fun historySeparatesEquipmentAndPeopleAndConvertsUnits() {
        val a = session("100")
        val b = session("220.462262", "lb", date = 200)
        val c = session("999", equipment = matrix)
        val partner = session("900", participant = "friend")
        val logs = completedHistory(listOf(a, b, c, partner))
        val key = logs.first { it.entry.equipmentKey() == hammer.id }.key
        val points = performanceSeries(logs, key, Metric.WEIGHT, "kg")
        assertEquals(2, points.size)
        assertEquals(points[0].value, points[1].value, 0.0001)
        assertEquals(3, logs.size)
        assertEquals(1, completedHistory(listOf(a, partner), "friend").size)
        assertEquals(2, personalRecords(logs).size)
    }

    @Test fun unfinishedInvalidAndTemplateSetsDoNotCount() {
        val w = session("100")
        val invalid = w.copy(id = newId(), exercises = w.exercises.map { it.copy(sets = listOf(SetEntry(reps = "10", weight = "NaN", complete = true))) })
        assertEquals(1, completedHistory(listOf(w, w.copy(isTemplate = true), w.copy(finishedAt = null), invalid)).size)
    }

    @Test fun leftAndRightPairOnlyWithinSameRoundAndSession() {
        val left = session("20", side = Side.LEFT)
        val right = left.exercises.single().sets.single().copy(id = newId(), side = Side.RIGHT, weight = "15")
        val w = left.copy(exercises = left.exercises.map { it.copy(sets = it.sets + right + right.copy(id = newId(), groupId = "other")) })
        val comparisons = sideDifferences(completedHistory(listOf(w)))
        assertEquals(1, comparisons.size)
        assertEquals(25.0, comparisons.single().volumePercent!!, 0.001)
        assertEquals(2, personalRecords(completedHistory(listOf(w))).size)
    }

    @Test fun muscleCountsSeparatePrimaryAndSecondaryWithoutDuplicateAttribution() {
        val result = muscleWork(completedHistory(listOf(session("10"))))
        assertEquals(MuscleWork("chest", 1, 0), result.find { it.name == "chest" })
        assertEquals(MuscleWork("triceps", 0, 1), result.find { it.name == "triceps" })
    }

    @Test fun estimateAndBaselineUseEligibleSetsOnly() {
        val a = session("100", date = 100)
        val b = session("110", date = 200)
        val logs = completedHistory(listOf(a, b))
        assertEquals(133.3333, logs[0].estimatedMaxKg!!, 0.001)
        assertEquals(110.0, performanceSeries(logs, logs[0].key, Metric.PERFORMANCE, "kg").last().value, 0.001)
        assertNull(logs[0].copy(set = logs[0].set.copy(reps = "11")).estimatedMaxKg)
        assertNull(logs[0].copy(set = logs[0].set.copy(weight = "0")).estimatedMaxKg)
        assertEquals(100.0, logs[0].copy(set = logs[0].set.copy(reps = "1")).estimatedMaxKg!!, 0.001)
    }

    @Test fun weeksStartMondayAndEmptyPeriodsAppear() {
        val monday = LocalDate.of(2026, 9, 21)
        val w = session("10", date = monday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val buckets = volumeBuckets(completedHistory(listOf(w)), Period.WEEK, "kg", monday.plusDays(2), ZoneOffset.UTC)
        assertEquals(12, buckets.size)
        assertEquals(100.0, buckets.last().value, 0.001)
        assertEquals(0.0, buckets.first().value, 0.001)
        assertEquals(monday, periodStart(monday.plusDays(6), Period.WEEK))
    }

    @Test fun reorderPreservesEntryIdentityAndSets() {
        val a = session("10").exercises.single(); val b = a.copy(id = "b"); val c = a.copy(id = "c")
        val w = Workout(name = "Order", exercises = listOf(a, b, c))
        val reordered = w.moveExercise(c.id, a.id)
        assertEquals(listOf(c.id, a.id, b.id), reordered.exercises.map { it.id })
        assertEquals(c.sets, reordered.exercises.first().sets)
        assertEquals(listOf(a.id, b.id, c.id), reordered.moveExercise(c.id, null).exercises.map { it.id })
    }

    @Test fun repeatDoesNotCopyPartnersAndKeepsLeftRightIndependent() {
        val sets = newSetGroup(ExerciseMode.SINGLE_ARM, null).map { it.copy(weight = if (it.side == Side.LEFT) "10" else "12", reps = "8", complete = true) } + newSetGroup(ExerciseMode.SINGLE_ARM, "friend")
        val workout = Workout(name = "Arms", partners = listOf(WorkoutPartner("friend", "Friend", true)), exercises = listOf(WorkoutExercise(exercise = press.copy(mode = ExerciseMode.SINGLE_ARM), sets = sets)))
        val repeat = workout.freshCopy(false)
        assertTrue(repeat.partners.isEmpty())
        assertEquals(2, repeat.exercises.single().sets.size)
        assertEquals(listOf("10", "12"), repeat.exercises.single().sets.map { it.weight })
        assertFalse(repeat.exercises.single().sets.any { it.complete })
        assertEquals(1, repeat.exercises.single().sets.map { it.groupId }.distinct().size)
        assertNotEquals(sets.first().groupId, repeat.exercises.single().sets.first().groupId)
    }
}
