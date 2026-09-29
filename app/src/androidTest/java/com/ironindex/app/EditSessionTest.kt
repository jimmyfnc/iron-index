package com.ironindex.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class EditSessionTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val databaseName = "edit-${newId()}.db"
    private val store = WorkoutStore(app, databaseName)
    private val session = Workout(name = "Monday bench", unit = "lb", startedAt = 1_000, finishedAt = 5_000, updatedAt = 5_000, exercises = listOf(
        WorkoutExercise(exercise = Exercise(name = "Bench press"), sets = listOf(SetEntry(weight = "25", reps = "5", complete = true), SetEntry(weight = "185", reps = "5")))))
    private fun awaitText(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun saved() = runBlocking { store.workouts.first().single() }
    private lateinit var vm: IronIndexViewModel
    /** Buttons at the bottom of the lazy list aren't composed until scrolled to. */
    private fun tapInList(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    private fun open() {
        runBlocking { store.save(session) }
        vm = IronIndexViewModel(app, store)
        compose.setContent { MaterialTheme(colorScheme = IronIndexColors) { IronIndexApp(vm) } }
        compose.runOnIdle { vm.opened.value = session.id }
        awaitText("SESSION COMPLETE")
        tapInList("Edit session")
        awaitText("EDITING SESSION")
    }

    @After fun cleanup() { store.close(); app.deleteDatabase(databaseName) }

    @Test fun fixATypoAndAForgottenSetThenDone() {
        open()
        // Backspace-and-retype, the way people fix a number: blank for a moment, then valid again.
        compose.onNodeWithContentDescription("Bench press set 1 weight in lb").performTextClearance()
        compose.waitUntil(10000) { saved().exercises.single().sets.first().let { it.weight.isEmpty() && it.complete && !it.counts } }
        compose.onNodeWithContentDescription("Bench press set 1 weight in lb").performTextInput("225")
        compose.onNodeWithContentDescription("Complete set 2").performScrollTo().performClick()
        compose.waitUntil(10000) { saved().allCompletedSets == 2 }
        tapInList("Done editing")
        awaitText("SESSION COMPLETE")
        compose.waitUntil(10000) { saved().editedAt != null }
        val w = saved()
        assertEquals(listOf("225", "185"), w.exercises.single().sets.map { it.weight })
        assertEquals(1_000L, w.startedAt)
        assertEquals(5_000L, w.finishedAt)
        compose.onNodeWithText("edited", substring = true).assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Edit session")) // back to the read-only view
    }

    @Test fun discardRestoresTheSessionExactly() {
        open()
        compose.onNodeWithContentDescription("Bench press set 1 weight in lb").performTextReplacement("999")
        compose.waitUntil(10000) { saved().exercises.single().sets.first().weight == "999" }
        tapInList("Discard changes")
        compose.waitUntil(10000) { saved().exercises.single().sets.first().weight == "25" }
        assertTrue(saved().sameContentAs(session))
        assertNull(saved().editedAt)
        // The open screen reloads the restored value instead of showing the discarded one.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Bench press set 1 weight in lb"))
        compose.waitUntil(10000) { compose.onAllNodes(hasContentDescription("Bench press set 1 weight in lb") and hasText("25")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("999")).assertCountEquals(0)
    }

    @Test fun leavingWithNoCheckedSetsKeepsTheOriginal() {
        open()
        compose.onNodeWithContentDescription("Complete set 1").performScrollTo().performClick()
        compose.waitUntil(10000) { saved().allCompletedSets == 0 }
        androidx.test.espresso.Espresso.pressBack() // the system Back gesture, not just the on-screen arrow
        compose.waitUntil(10000) { saved().allCompletedSets == 1 }
        assertTrue(saved().sameContentAs(session))
        awaitText("original session was kept")
    }
}
