package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class PrefillTest {
    private val alex = "alex"
    private val bench = Exercise(id = "bench", name = "Bench press")
    private val bar = Equipment(id = "bar", type = "Barbell")

    private fun done(weight: String, reps: String, person: String? = null, group: String = newId(), side: Side = Side.BOTH) =
        SetEntry(weight = weight, reps = reps, complete = true, participantId = person, side = side, groupId = group)

    private fun session(start: Long, sets: List<SetEntry>, unit: String = "lb", equipment: Equipment? = bar, exercise: Exercise = bench) =
        Workout(id = "s$start", name = "S$start", unit = unit, startedAt = start, finishedAt = start + 1,
            exercises = listOf(WorkoutExercise(exercise = exercise, equipmentId = equipment?.id, equipmentSnapshot = equipment, sets = sets)))

    private fun today(unit: String = "lb", partners: List<WorkoutPartner> = emptyList()) = Workout(id = "today", name = "Today", unit = unit, partners = partners)

    @Test fun aNewEntryStartsWithLastSessionsSetsUnchecked() {
        val history = listOf(session(1, listOf(done("135", "10"))), session(2, listOf(done("135", "8"), done("155", "5"), SetEntry(weight = "175", reps = "2"))))
        val w = today()
        val entry = w.newEntry(bench, bar).prefilledFrom(history, w)
        assertEquals(listOf("155" to "5", "135" to "8").sortedBy { it.first }, entry.sets.map { it.weight to it.reps }.sortedBy { it.first })
        assertEquals(listOf("135", "155"), entry.sets.map { it.weight }) // order kept, the unchecked 175 left out
        assertTrue(entry.sets.none { it.complete })
        assertEquals(2, entry.sets.map { it.groupId }.distinct().size)
    }

    @Test fun onlyTheSameExerciseEquipmentAndSidesCount() {
        val dumbbells = Equipment(id = "db", type = "Dumbbells")
        val w = today()
        val otherMachine = listOf(session(5, listOf(done("60", "10")), equipment = dumbbells))
        assertEquals(listOf(""), w.newEntry(bench, bar).prefilledFrom(otherMachine, w).sets.map { it.weight })
        val singleArm = bench.copy(mode = ExerciseMode.SINGLE_ARM)
        assertEquals(listOf("", ""), w.newEntry(singleArm, bar).prefilledFrom(listOf(session(5, listOf(done("135", "8")))), w).sets.map { it.weight })
    }

    @Test fun eachPersonGetsTheirOwnLastSets() {
        val w = today(partners = listOf(WorkoutPartner(alex, "Alex", trackSets = true)))
        val history = listOf(session(1, listOf(done("95", "12", alex))), session(2, listOf(done("185", "5"))))
        val entry = w.newEntry(bench, bar).prefilledFrom(history, w)
        assertEquals(listOf("185"), entry.sets.filter { it.participantId == null }.map { it.weight })
        assertEquals(listOf("95"), entry.sets.filter { it.participantId == alex }.map { it.weight })
    }

    @Test fun weightsConvertWhenTheUnitChanged() {
        val w = today(unit = "kg")
        val entry = w.newEntry(bench, bar).prefilledFrom(listOf(session(1, listOf(done("100", "5")), unit = "lb")), w)
        assertEquals("45.4", entry.sets.single().weight)
    }

    @Test fun platesOnlyNumbersKeepTheirTotalsIfTheSledChanged() {
        val plain = Equipment(id = "lp", type = "Leg press")
        val sled = plain.copy(baseWeight = 167.0, loadMode = LoadMode.ADDED)
        val w = today()
        // Logged as 347 total before the sled was set up; today the machine is plates-only.
        val entry = w.newEntry(Exercise(id = "lp", name = "Leg press"), sled).prefilledFrom(listOf(session(1, listOf(done("347", "10")), equipment = plain, exercise = Exercise(id = "lp", name = "Leg press"))), w)
        assertEquals("180", entry.sets.single().weight)
    }

    @Test fun unilateralRoundsStayPaired() {
        val curl = Exercise(id = "curl", name = "Curl", mode = ExerciseMode.SINGLE_ARM)
        val w = today()
        val history = listOf(session(1, listOf(done("30", "10", group = "g1", side = Side.LEFT), done("25", "10", group = "g1", side = Side.RIGHT)), exercise = curl))
        val entry = w.newEntry(curl, bar).prefilledFrom(history, w)
        assertEquals(listOf(Side.LEFT to "30", Side.RIGHT to "25"), entry.sets.map { it.side to it.weight })
        assertEquals(1, entry.sets.map { it.groupId }.distinct().size)
    }

    @Test fun noHistoryKeepsBlankStartingSets() {
        val w = today()
        assertEquals(listOf(""), w.newEntry(bench, bar).prefilledFrom(emptyList(), w).sets.map { it.weight })
    }

    @Test fun editingAPastSessionOnlyLooksAtSessionsBeforeIt() {
        val history = listOf(session(1, listOf(done("135", "8"))), session(9, listOf(done("185", "3"))))
        val editing = Workout(id = "past", name = "Past", startedAt = 5, finishedAt = 6)
        assertEquals(listOf("135"), editing.newEntry(bench, bar).prefilledFrom(history, editing).sets.map { it.weight })
    }

    @Test fun aPersonWhoSkippedTheLatestSessionGetsTheirEarlierOne() {
        val w = today(partners = listOf(WorkoutPartner(alex, "Alex", trackSets = true)))
        val history = listOf(session(1, listOf(done("95", "12", alex))), session(2, listOf(done("185", "5"))), session(3, listOf(done("190", "5"))))
        assertEquals(listOf("95"), w.newEntry(bench, bar).prefilledFrom(history, w).sets.filter { it.participantId == alex }.map { it.weight })
    }

    @Test fun repeatedEntriesThatDayAreAllUsed() {
        val heavy = WorkoutExercise(exercise = bench, equipmentId = bar.id, equipmentSnapshot = bar, sets = listOf(done("225", "3")))
        val backOff = WorkoutExercise(exercise = bench, equipmentId = bar.id, equipmentSnapshot = bar, sets = listOf(done("185", "8")))
        val day = Workout(id = "d", name = "Day", startedAt = 1, finishedAt = 2, exercises = listOf(heavy, backOff))
        val w = today()
        assertEquals(listOf("225", "185"), w.newEntry(bench, bar).prefilledFrom(listOf(day), w).sets.map { it.weight })
    }

    @Test fun routinesAndTheCurrentWorkoutAreIgnored() {
        val routine = session(1, listOf(done("300", "1"))).copy(isTemplate = true)
        val w = today().copy(finishedAt = null)
        val self = session(2, listOf(done("400", "1"))).copy(id = w.id)
        assertEquals(listOf(""), w.newEntry(bench, bar).prefilledFrom(listOf(routine, self), w).sets.map { it.weight })
    }
}
