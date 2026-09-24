package com.ironindex.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.MaterialTheme
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutFlowTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val databaseName = "flow-${newId()}.db"
    private val store = WorkoutStore(app, databaseName)
    private val holder = ViewModelStore()
    @Before fun setup() {
        val vm = ViewModelProvider(holder, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return IronIndexViewModel(app, store) as T
            }
        })[IronIndexViewModel::class.java]
        compose.setContent { MaterialTheme { IronIndexApp(vm) } }
    }
    @After fun cleanup() { holder.clear(); store.close(); app.deleteDatabase(databaseName) }

    private fun awaitText(text: String) {
        compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun muscleChipsAndSearchNarrowTheExerciseLibrary() {
        compose.onNodeWithText("Start a workout").performClick()
        awaitText("Add first exercise")
        compose.onNodeWithText("Add first exercise").performClick()
        awaitText("Find your next move")
        compose.onNodeWithContentDescription("Filter muscles: Biceps").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Filter muscles: Biceps").assertIsSelected()
        compose.onNode(hasText("Biceps curl") and hasClickAction()).assertExists()
        compose.onNode(hasText("Triceps pushdown") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Filter muscles: Triceps").performScrollTo().performClick()
        compose.onNode(hasText("Triceps pushdown") and hasClickAction()).assertExists()
        compose.onNode(hasText("Biceps curl") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Filter muscles: All").performScrollTo().performClick()
        compose.onNodeWithText("Search exercises or muscles").performTextInput("arms")
        compose.onNode(hasText("Biceps curl") and hasClickAction()).assertExists()
        compose.onNode(hasText("Triceps pushdown") and hasClickAction()).assertExists()
        compose.onNodeWithText("Search exercises or muscles").performTextReplacement("no such target")
        compose.onNodeWithText("No matches. Try All muscles or clear your search.").assertExists()
    }

    @Test fun sameCurlCanUseDumbbellsAndCablesWithoutDuplicatingTheExercise() {
        compose.onNodeWithText("Start a workout").performClick()
        awaitText("Add first exercise")
        listOf("Dumbbells", "Cable machine").forEachIndexed { index, equipment ->
            if (index == 0) compose.onNodeWithText("Add first exercise").performClick()
            else compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Add exercise"))
                .also { compose.onNodeWithText("Add exercise").performClick() }
            awaitText("Find your next move")
            compose.onNodeWithText("Search exercises or muscles").performTextInput("Biceps")
            androidx.test.espresso.Espresso.closeSoftKeyboard() // the IME animating closed can swallow the tap
            compose.waitForIdle()
            compose.onNode(hasText("Biceps curl") and hasClickAction()).performClick()
            awaitText("Equipment for Biceps curl")
            compose.onNodeWithText("Search type, brand, model or gym").performTextInput(equipment)
            compose.onNode(hasText(equipment) and !hasSetTextAction()).performClick()
            compose.waitUntil(10000) { runBlocking { store.workouts.first().single().exercises.size == index + 1 } }
        }
        val saved = runBlocking { store.workouts.first().single() }
        assertEquals(listOf("curl", "curl"), saved.exercises.map { it.exercise.id })
        assertEquals(listOf("Dumbbells", "Cable machine"), saved.exercises.map { it.equipmentProfile()?.type })
        assertNotEquals(saved.exercises[0].equipmentKey(), saved.exercises[1].equipmentKey())
        val routine = saved.freshCopy(true)
        runBlocking { store.save(routine) }
        val restored = runBlocking { store.workouts.first().first { it.id == routine.id } }.freshCopy(false)
        assertEquals(saved.exercises.map { it.equipmentKey() }, restored.exercises.map { it.equipmentKey() })
        assertEquals(listOf("curl", "curl"), restored.exercises.map { it.exercise.id })
    }

    @Test fun logSaveResumeFinishAndRepeatWorkout() {
        compose.onNodeWithText("Start a workout").performClick()
        awaitText("Add first exercise")
        compose.onNodeWithText("Add first exercise").performClick()
        awaitText("Find your next move")
        compose.onNodeWithText("Search exercises or muscles").performTextInput("Bench")
        // The IME animating closed can swallow the first tap; dismiss it and wait for the result row.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Bench press").performClick()
        awaitText("Equipment for Bench press")
        compose.onNodeWithText("Barbell").performClick()
        awaitText("reps")
        compose.onNode(hasText("lb") and hasSetTextAction()).performTextInput("135")
        compose.onNodeWithText("reps").performTextInput("8")
        compose.waitUntil(10000) { runCatching { compose.onNodeWithContentDescription("Complete set 1").assertIsEnabled() }.isSuccess }
        compose.onNodeWithContentDescription("Complete set 1").performClick()
        awaitText("1 set logged · autosaves on this phone")
        compose.onNodeWithContentDescription("Back").performClick()
        awaitText("Resume workout")
        compose.onNodeWithText("Resume workout").performClick()
        awaitText("reps")
        compose.onNodeWithText("135").assertExists()
        compose.onNodeWithContentDescription("Complete set 1").assertIsOn()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Save as a routine"))
        compose.onNodeWithText("Save as a routine").performClick()
        compose.onNodeWithText("Workout name").performTextReplacement("Test push routine")
        compose.onNodeWithText("Save").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").performClick()
        compose.onNodeWithText("Nice work. Wrap it up?").assertExists()
        compose.onAllNodesWithText("Finish workout").onLast().performClick()
        awaitText("History")
        compose.onNodeWithText("History").performClick()
        awaitText("View session")
        compose.onNodeWithText("View session").performClick()
        awaitText("1 set logged · 1080 lb total volume")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Repeat workout"))
        compose.onNodeWithText("Repeat workout").performClick()
        awaitText("0 sets logged · autosaves on this phone")
        compose.onNodeWithText("135").assertExists()
        compose.onNodeWithContentDescription("Complete set 1").assertIsOff()
    }
}
