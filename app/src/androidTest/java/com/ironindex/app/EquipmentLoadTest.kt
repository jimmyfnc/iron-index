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
        val vm = IronIndexViewModel(app, store)
        val snapshot = runBlocking { store.workouts.first().single { it.id == "draft" } }
        // Wait for the ViewModel's workouts to load, then save details with the (old) equipment choice.
        val deadline = System.currentTimeMillis() + 10_000
        runBlocking { vm.workouts.first { it.isNotEmpty() } }
        vm.editDetails("draft", snapshot.exercises.single().id, snapshot.exercises.single().exercise.copy(primaryMuscles = listOf("Quads")), plain)
        while (System.currentTimeMillis() < deadline && runBlocking { store.workouts.first().single { it.id == "draft" } }.exercises.single().exercise.primaryMuscles.isEmpty()) Thread.sleep(50)
        assertEquals(sled, runBlocking { store.equipment.first().single { it.id == "sled" } })
    }
}
