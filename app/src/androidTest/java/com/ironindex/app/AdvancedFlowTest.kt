package com.ironindex.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedFlowTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val databaseName = "advanced-${newId()}.db"
    private val store = WorkoutStore(app, databaseName)
    private val holder = ViewModelStore()
    private lateinit var vm: IronIndexViewModel
    private fun awaitText(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun launch(workout: Workout) {
        vm = ViewModelProvider(holder, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return IronIndexViewModel(app, store) as T
            }
        })[IronIndexViewModel::class.java]
        compose.setContent { MaterialTheme { IronIndexApp(vm) } }
        compose.runOnIdle { vm.opened.value = workout.id }
        awaitText(workout.name)
    }

    @After fun cleanup() {
        holder.clear()
        store.close()
        app.deleteDatabase(databaseName)
    }

    @Test fun partnerAndUnilateralInputsAreIndependentAndPersisted() {
        val exercise = Exercise(name = "One-arm curl", mode = ExerciseMode.SINGLE_ARM, primaryMuscles = listOf("Biceps"))
        val equipment = Equipment(type = "Curl machine", brand = "Matrix", model = "Ultra", location = "Gym A")
        val entry = WorkoutExercise(exercise = exercise, equipmentId = equipment.id, equipmentSnapshot = equipment, sets = newSetGroup(exercise.mode, null))
        val workout = Workout(name = "Unilateral test", exercises = listOf(entry))
        val alex = Partner(name = "Alex")
        runBlocking { store.save(workout); store.savePartner(alex); store.saveEquipment(equipment) }
        launch(workout)
        compose.onNodeWithText("Partners").performClick()
        awaitText("Workout partners")
        compose.onNodeWithContentDescription("Attach Alex").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Track sets for Alex").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Track sets for Alex").performClick()
        compose.onNodeWithText("Done").performClick()
        awaitText("Alex")
        compose.onNodeWithText("Alex").performClick()
        compose.onNodeWithText("Alex").assertIsSelected()
        compose.onAllNodesWithText("lb").onFirst().performScrollTo().performTextInput("20")
        compose.onAllNodesWithText("reps").onFirst().performScrollTo().performTextInput("8")
        compose.waitUntil(10000) { runCatching { compose.onNodeWithContentDescription("Complete set 1 Left").assertIsEnabled() }.isSuccess }
        compose.onNodeWithContentDescription("Complete set 1 Left").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Complete set 1 Right").performScrollTo().assertIsOff()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Unilateral test").performScrollTo()
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithText("You").assertIsSelected()
        compose.onNodeWithContentDescription("Complete set 1 Left").performScrollTo().assertIsOff()
        compose.onAllNodesWithText("20").assertCountEquals(0)
        val restored = runBlocking { store.workouts.first().single() }
        assertEquals(0, restored.completedSets)
        assertEquals(1, restored.allCompletedSets)
        assertEquals("20", restored.exercises.single().sets.single { it.participantId == alex.id && it.side == Side.LEFT }.weight)
        assertTrue(restored.exercises.single().sets.single { it.participantId == alex.id && it.side == Side.RIGHT }.weight.isBlank())
    }

    @Test fun partnerCanSkipAndRejoinExercisesAndProfileShowsHistory() {
        val alex = Partner(name = "Alex", goal = "Bench 225", notes = "Left shoulder: no deep dips")
        val squat = Workout(name = "Tmp").copy(partners = listOf(WorkoutPartner(alex.id, alex.name, true)))
            .newEntry(Exercise(name = "Squat"), null)
        val workout = Workout(name = "Pair day", partners = listOf(WorkoutPartner(alex.id, alex.name, true)), exercises = listOf(squat))
        val finished = Workout(name = "Old pair day", startedAt = 1_000, finishedAt = 2_000, partners = listOf(WorkoutPartner(alex.id, alex.name, true)),
            exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Bench press"), sets = listOf(SetEntry(weight = "135", reps = "5", complete = true, participantId = alex.id)))))
        runBlocking { store.save(workout); store.save(finished); store.savePartner(alex) }
        launch(workout)
        compose.onNodeWithText("Alex").performClick()
        awaitText("Goal · Bench 225")
        compose.onNodeWithText("Left shoulder: no deep dips").assertExists()
        compose.onNodeWithContentDescription("Skip Squat for Alex").performScrollTo().performClick()
        awaitText("Not in Alex’s workout")
        compose.waitUntil(10000) { runBlocking { !store.workouts.first().first { it.id == workout.id }.exercises.single().includes(alex.id) } }
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithContentDescription("Skip Squat for you").assertExists()
        compose.onNodeWithText("Alex").performClick()
        compose.onNodeWithContentDescription("Add Squat for Alex").performClick()
        compose.waitUntil(10000) { runBlocking { store.workouts.first().first { it.id == workout.id }.exercises.single().includes(alex.id) } }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Partners").performClick()
        awaitText("Partner Manager")
        compose.onNodeWithText("History & stats").performClick()
        awaitText("Last trained ${dateLabel(1_000)}")
        compose.onNodeWithText("Old pair day", substring = true).assertExists()
        compose.onNodeWithText("Progress charts").performClick()
        awaitText("Your work, in focus.")
        androidx.test.espresso.Espresso.pressBack()
        awaitText("Last trained ${dateLabel(1_000)}")
        compose.onNodeWithText("Old pair day", substring = true).performClick()
        awaitText("SESSION COMPLETE")
        compose.onNodeWithText("Alex").assertIsSelected()
    }

    @Test fun longPressDragReordersAndPreservesSetValues() {
        val a = WorkoutExercise(exercise = Exercise(name = "Alpha press"), sets = listOf(SetEntry(weight = "25", reps = "8")))
        val b = WorkoutExercise(exercise = Exercise(name = "Beta row"))
        val workout = Workout(name = "Order test", exercises = listOf(a, b))
        runBlocking { store.save(workout) }
        launch(workout)
        compose.onNodeWithText("Reorder").performClick()
        awaitText("Exercise order")
        val first = compose.onNodeWithContentDescription("Drag Alpha press").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription("Drag Beta row").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Drag Alpha press").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(Offset(center.x, center.y + second.center.y - first.center.y), 400)
            advanceEventTime(250)
            up()
        }
        compose.onNodeWithText("Done ordering").performClick()
        compose.waitUntil(10000) { runBlocking { store.workouts.first().single().exercises.first().id == b.id } }
        val restored = runBlocking { store.workouts.first().single() }
        assertEquals(listOf(b.id, a.id), restored.exercises.map { it.id })
        assertEquals("25", restored.exercises.last().sets.single().weight)
    }

    @Test fun equipmentCatalogAndProgressScreenShowRealData() {
        val equipment = Equipment(type = "Incline press", brand = "Hammer Strength", model = "ISO")
        val entry = WorkoutExercise(exercise = Exercise(name = "Incline press", primaryMuscles = listOf("Chest")), equipmentId = equipment.id, equipmentSnapshot = equipment,
            sets = listOf(SetEntry(weight = "100", reps = "10", complete = true)))
        val workout = Workout(name = "Chart test", finishedAt = System.currentTimeMillis(), exercises = listOf(entry))
        runBlocking { store.save(workout); store.saveEquipment(equipment) }
        launch(workout)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Equipment").performClick()
        awaitText("Equipment library")
        compose.onNodeWithText("Add equipment").performClick()
        compose.onNodeWithText("Equipment type *").performTextInput("Chest press")
        compose.onNodeWithText("Brand").performTextInput("Life Fitness")
        compose.onNodeWithText("Model").performScrollTo().performTextInput("Insignia")
        compose.onNodeWithText("Gym / nickname (optional)").performScrollTo().performTextInput("Downtown")
        compose.onNodeWithText("Save equipment").performClick()
        compose.waitUntil(10000) { runBlocking { store.equipment.first().any { it.brand == "Life Fitness" && it.model == "Insignia" && it.location == "Downtown" } } }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Progress").performClick()
        awaitText("Your work, in focus.")
        compose.onAllNodesWithText("1,000.0 lb × reps").onFirst().assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Sets by muscle"))
        compose.onNodeWithText("Chest").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Exercise progression"))
        compose.onAllNodesWithText("Weight").onFirst().assertExists()
    }
}
