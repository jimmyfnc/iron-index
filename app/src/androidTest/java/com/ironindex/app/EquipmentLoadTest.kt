package com.ironindex.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EquipmentLoadTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val databaseName = "load-${newId()}.db"
    private val store = WorkoutStore(app, databaseName)
    private val plain = Equipment(id = "sled", type = "Leg press", brand = "Hammer Strength")
    private val sled = plain.copy(baseWeight = 167.0, loadMode = LoadMode.ADDED)
    private fun entry(e: Equipment, weight: String, done: Boolean) = WorkoutExercise(exercise = Exercise(id = "lp", name = "Leg press"), equipmentId = e.id, equipmentSnapshot = e,
        sets = listOf(SetEntry(weight = weight, reps = "10", complete = done)))
    private val past = Workout(id = "past", name = "Old legs", startedAt = 1, finishedAt = 2, exercises = listOf(entry(plain, "180", true)))
    private val routine = Workout(id = "routine", name = "Legs", isTemplate = true, exercises = listOf(entry(plain, "347", false)))
    private fun all() = runBlocking { store.workouts.first().associateBy { it.id } }

    @After fun cleanup() { store.close(); app.deleteDatabase(databaseName) }

    @Test fun keepTotalsLeavesHistoryAndConvertsRoutines() = runBlocking {
        store.saveEquipment(plain); store.save(past); store.save(routine)
        store.applyEquipment(sled, reinterpretPast = false)
        assertEquals(plain, all().getValue("past").exercises.single().equipmentSnapshot)       // history untouched
        assertEquals(180.0, all().getValue("past").volume / 10, 1e-9)
        assertEquals("180", all().getValue("routine").exercises.single().sets.single().weight) // 347 total kept as 180 + 167
        assertEquals(sled, store.equipment.first().single { it.id == "sled" })
    }

    @Test fun platesOnlyCountsTheSledInPastLogsToo() = runBlocking {
        store.saveEquipment(plain); store.save(past); store.save(routine)
        store.applyEquipment(sled, reinterpretPast = true)
        assertEquals(3470.0, all().getValue("past").volume, 1e-9)
        assertEquals("347", all().getValue("routine").exercises.single().sets.single().weight)
    }

    @Test fun startingARoutineConvertsTargetsWrittenBeforeTheChange() = runBlocking {
        store.saveEquipment(sled); store.save(routine) // routine still carries the old (plain) snapshot
        val id = store.start(routine, "lb")
        val started = all().getValue(id).exercises.single()
        assertEquals("180", started.sets.single().weight)
        assertEquals(sled, started.equipmentSnapshot)
    }

    @Test fun editingAMachineIntoAnothersNameIsRefused() = runBlocking {
        store.saveEquipment(plain)
        val other = store.saveEquipment(Equipment(type = "Hack squat"))
        val e = runCatching { store.applyEquipment(other.copy(type = "Leg press", brand = "Hammer Strength"), false) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        assertEquals(setOf("Leg press", "Hack squat"), store.equipment.first().filter { it.id in setOf("sled", other.id) }.map { it.type }.toSet())
    }

    @Test fun exerciseDetailsNeverRewriteTheCatalogFromASnapshot() {
        runBlocking { store.saveEquipment(plain); store.save(Workout(id = "draft", name = "Now", exercises = listOf(entry(plain, "", false)))) }
        runBlocking { store.applyEquipment(sled, false) }
        val vm = IronIndexViewModel(app, store, null)
        val snapshot = runBlocking { store.workouts.first().single { it.id == "draft" } }
        // Wait for the ViewModel's workouts to load, then save details with the (old) equipment choice.
        val deadline = System.currentTimeMillis() + 10_000
        runBlocking { vm.workouts.first { it.isNotEmpty() } }
        vm.editDetails("draft", snapshot.exercises.single().id, snapshot.exercises.single().exercise.copy(primaryMuscles = listOf("Quads")), plain)
        while (System.currentTimeMillis() < deadline && runBlocking { store.workouts.first().single { it.id == "draft" } }.exercises.single().exercise.primaryMuscles.isEmpty()) Thread.sleep(50)
        assertEquals(sled, runBlocking { store.equipment.first().single { it.id == "sled" } })
    }

    @Test fun renamingAMachineReachesPastSessionsButKeepsTheirLoad() = runBlocking {
        val loggedOnSled = past.copy(exercises = listOf(entry(sled, "180", true)))
        store.saveEquipment(sled); store.save(loggedOnSled)
        store.applyEquipment(sled.copy(brand = "Hammer", model = "Linear", baseWeight = 100.0), reinterpretPast = false)
        val snapshot = all().getValue("past").exercises.single().equipmentSnapshot!!
        assertEquals("Leg press · Hammer · Linear", snapshot.label)
        assertEquals(167.0, snapshot.baseWeight!!, 1e-9)
        assertEquals(3470.0, all().getValue("past").volume, 1e-9)
    }

    @Test fun switchingToTheRightMachineAfterLoggingKeepsWeightsAsTyped() {
        val other = Equipment(id = "lf", type = "Leg press", brand = "Life Fitness", baseWeight = 100.0, loadMode = LoadMode.ADDED)
        runBlocking { store.saveEquipment(plain); store.saveEquipment(other); store.save(Workout(id = "draft", name = "Now", exercises = listOf(entry(plain, "180", true)))) }
        val vm = IronIndexViewModel(app, store, null)
        runBlocking { vm.workouts.first { it.isNotEmpty() } }
        val current = runBlocking { store.workouts.first().single() }.exercises.single()
        vm.editDetails("draft", current.id, current.exercise, other)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && runBlocking { store.workouts.first().single() }.exercises.single().equipmentId != "lf") Thread.sleep(50)
        val moved = runBlocking { store.workouts.first().single() }
        assertEquals(other, moved.exercises.single().equipmentSnapshot)
        assertEquals(SetEntry(weight = "180", reps = "10", complete = true).copy(id = current.sets.single().id, groupId = current.sets.single().groupId), moved.exercises.single().sets.single())
        assertEquals(2800.0, moved.volume, 1e-9) // 180 plates + 100 sled
        while (System.currentTimeMillis() < deadline && vm.notice.value == null) Thread.sleep(50)
        assertEquals("Weights kept as typed. Totals now use this machine’s starting weight.", vm.notice.value)
        assertEquals(plain, runBlocking { store.equipment.first().single { it.id == "sled" } })
    }

    @Test fun pickingAMachineNeverOverwritesAFresherEdit() = runBlocking {
        store.saveEquipment(plain)
        store.applyEquipment(sled, reinterpretPast = false)
        assertEquals(sled, store.ensureEquipment(plain)) // the picker's copy is older than the edit
        assertEquals(sled, store.equipment.first().single { it.id == "sled" })
        val fresh = Equipment(type = "Hack squat")
        assertEquals(fresh, store.ensureEquipment(fresh))
    }

    @Test fun discardingASessionEditKeepsMachineEditsMadeDuringIt() {
        runBlocking { store.saveEquipment(plain); store.save(past) }
        val vm = IronIndexViewModel(app, store, null)
        vm.beginEdit(past)
        vm.updateEquipment(plain.copy(model = "Linear"), reinterpretPast = false)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && vm.editingOriginal.value?.exercises?.single()?.equipmentSnapshot?.model != "Linear") Thread.sleep(50)
        vm.discardEdit()
        while (System.currentTimeMillis() < deadline && vm.editingOriginal.value != null) Thread.sleep(50)
        val restored = all().getValue("past").exercises.single().equipmentSnapshot!!
        assertEquals("Linear", restored.model)
        assertEquals(plain.copy(model = "Linear"), restored)
    }
}
