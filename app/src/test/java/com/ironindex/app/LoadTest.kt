package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class LoadTest {
    private val sled = Equipment(id = "sled", type = "Leg press", brand = "Hammer Strength", baseWeight = 167.0, baseUnit = "lb", loadMode = LoadMode.ADDED)
    private val bar = Equipment(id = "bar", type = "Olympic barbell", baseWeight = 45.0, loadMode = LoadMode.TOTAL)
    private fun entry(e: Equipment?, mode: ExerciseMode = ExerciseMode.BILATERAL) =
        WorkoutExercise(exercise = Exercise(id = "x", name = "Lift", mode = mode), equipmentId = e?.id, equipmentSnapshot = e)

    @Test fun platesOnlyAddsTheSledForTotalsVolumeAndAnalytics() {
        assertEquals(347.0, entry(sled).totalLoad(180.0, "lb"), 1e-9)
        val w = Workout(name = "Legs", unit = "lb", startedAt = 1, finishedAt = 2, exercises = listOf(entry(sled).copy(sets = listOf(SetEntry(weight = "180", reps = "10", complete = true)))))
        assertEquals(3470.0, w.volume, 1e-9)
        val log = completedHistory(listOf(w)).single()
        assertEquals(347.0, displayWeight(log.weightKg, "lb"), 1e-6)
        assertEquals(347.0, displayWeight(personalRecords(listOf(log)).single().weightKg, "lb"), 1e-6)
    }

    @Test fun totalModeAndPlainEquipmentAreUnchanged() {
        assertEquals(225.0, entry(bar).totalLoad(225.0, "lb"), 1e-9)
        assertEquals(50.0, entry(null).totalLoad(50.0, "kg"), 1e-9)
        assertEquals(50.0, entry(Equipment(type = "Dumbbells")).totalLoad(50.0, "kg"), 1e-9)
    }

    @Test fun startingWeightsConvertBetweenUnits() {
        val kgBar = Equipment(type = "Barbell", baseWeight = 20.0, baseUnit = "kg", loadMode = LoadMode.ADDED)
        assertEquals(100.0 + 44.092, entry(kgBar).totalLoad(100.0, "lb"), 0.01)
        assertEquals(75.75, entry(sled).totalLoad(0.0, "kg"), 0.01)
    }

    @Test fun hintsExplainWhatWasTyped() {
        assertEquals("+167 sled = 347 lb total", loadHint(entry(sled), "180", "lb"))
        assertEquals("+167 lb sled added to totals", loadHint(entry(sled), "", "lb"))
        assertEquals("45 bar + 90 per side (45 + 45)", loadHint(entry(bar), "225", "lb"))
        assertEquals("45 bar + 47.5 per side (45 + 2.5)", loadHint(entry(bar), "140", "lb"))
        assertEquals("Less than the 45 lb bar", loadHint(entry(bar), "30", "lb"))
        assertEquals("Includes the 45 lb bar", loadHint(entry(bar), "", "lb"))
        assertEquals("45 bar + 90 per side", loadHint(entry(bar), "225", "lb").let { it!!.substringBefore(" (") })
        assertEquals("45 bar + 20 lb", loadHint(entry(bar, ExerciseMode.SINGLE_ARM), "65", "lb"))
        assertNull(loadHint(entry(Equipment(type = "Dumbbells")), "50", "lb"))
        assertNull(loadHint(entry(null), "50", "lb"))
    }

    @Test fun plateBreakdownUsesStandardPlatesOrGivesUp() {
        assertEquals(listOf(45.0, 45.0), plateBreakdown(90.0, "lb"))
        assertEquals(listOf(20.0, 2.5), plateBreakdown(22.5, "kg"))
        assertNull(plateBreakdown(1.0, "lb"))
        assertEquals(emptyList<Double>(), plateBreakdown(0.0, "lb"))
    }

    @Test fun sledMachinesSuggestPlatesOnly() {
        listOf("Leg press", "45° leg press", "Hack squat", "Smith machine", "Belt squat", "Pendulum squat").forEach { assertEquals(it, LoadMode.ADDED, suggestedLoadMode(it)) }
        listOf("Olympic barbell", "Power bar", "Chest press", "").forEach { assertEquals(it, LoadMode.TOTAL, suggestedLoadMode(it)) }
        assertEquals("sled", baseName("Leg press")); assertEquals("bar", baseName("Smith machine")); assertEquals("bar", baseName("EZ bar")); assertEquals("base", baseName("Cable stack"))
    }

    @Test fun rebasingKeepsEveryTargetsTotal() {
        val plain = Equipment(id = "sled", type = "Leg press")
        val routineEntry = entry(plain).copy(sets = listOf(SetEntry(weight = "347", reps = "10"), SetEntry(weight = "100", reps = "10"), SetEntry(weight = "", reps = "10")))
        val rebased = routineEntry.rebasedTo(sled, "lb")
        assertEquals(listOf("180", "", ""), rebased.sets.map { it.weight }) // 347 total → 180 plates; 100 is below the sled, so cleared
        assertEquals(347.0, rebased.totalLoad(180.0, "lb"), 1e-9)
        assertEquals(sled, rebased.equipmentSnapshot)
        // Bar weight changes in total mode don't change what typed numbers mean.
        val bench = entry(bar).copy(sets = listOf(SetEntry(weight = "225")))
        assertEquals("225", bench.rebasedTo(bar.copy(baseWeight = 55.0), "lb").sets.single().weight)
        assertFalse(loadMeaningChanged(bar, bar.copy(baseWeight = 55.0)))
        assertTrue(loadMeaningChanged(plain, sled))
        assertTrue(loadMeaningChanged(sled, sled.copy(loadMode = LoadMode.TOTAL)))
    }

    @Test fun unusableStartingWeightsAreIgnoredNotCrashed() {
        listOf(0.0, -5.0, Double.NaN).forEach { assertNull(sled.copy(baseWeight = it).baseIn("lb")) }
        assertNull(sled.copy(baseUnit = "stone").baseIn("lb"))
        assertNull(loadHint(entry(sled.copy(baseWeight = 0.0)), "180", "lb"))
        assertEquals(180.0, entry(sled.copy(baseWeight = 0.0)).totalLoad(180.0, "lb"), 1e-9)
    }

    @Test fun importLinkingKeepsTheLoadSettingsSetsWereLoggedWith() {
        val loggedWith = sled.copy(id = "backup-sled")
        val phoneCopy = sled.copy(id = "phone-sled", baseWeight = 200.0)
        val session = Workout(id = "w", name = "Legs", startedAt = 1, finishedAt = 2, exercises = listOf(entry(loggedWith).copy(sets = listOf(SetEntry(weight = "180", reps = "10", complete = true)))))
        val plan = planImport(Backup(workouts = listOf(session), equipment = listOf(loggedWith)), listOf(Workout(name = "other")), emptyList(), listOf(phoneCopy), emptyList())
        val imported = plan.workouts.single().exercises.single()
        assertEquals("phone-sled", imported.equipmentId)
        assertEquals(167.0, imported.equipmentSnapshot?.baseWeight)
        assertEquals(347.0, imported.totalLoad(180.0, "lb"), 1e-9)
    }

    @Test fun olderEquipmentWithoutStartingWeightsStillLoads() {
        val old = storageJson.decodeFromString<Equipment>("""{"id":"e","type":"Chest press","brand":"Matrix"}""")
        assertNull(old.baseWeight); assertEquals(LoadMode.TOTAL, old.loadMode); assertNull(old.baseLabel)
        assertEquals("starts at 167 lb · log plates only", sled.baseLabel)
    }

    @Test fun renamingASnapshotKeepsTheLoadItWasLoggedWith() {
        val renamed = sled.renamedLike(Equipment(id = "sled", type = "Leg press", brand = "Hammer", model = "Linear", location = "Downtown", baseWeight = 100.0, loadMode = LoadMode.TOTAL))
        assertEquals("Leg press · Hammer · Linear · Downtown", renamed.label)
        assertEquals(167.0, renamed.baseWeight!!, 1e-9); assertEquals(LoadMode.ADDED, renamed.loadMode)
    }

    @Test fun equipmentUsesCountsOnlyWorkoutsWithTypedWeights() {
        val typed = Workout(name = "A", exercises = listOf(entry(sled).copy(sets = listOf(SetEntry(weight = "90")))))
        val blank = Workout(name = "B", exercises = listOf(entry(sled)))
        val other = Workout(name = "C", exercises = listOf(entry(bar).copy(sets = listOf(SetEntry(weight = "135")))))
        assertEquals(1, equipmentUses(listOf(typed, blank, other), "sled"))
    }
}
