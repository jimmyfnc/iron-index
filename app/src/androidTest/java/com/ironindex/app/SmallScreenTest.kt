package com.ironindex.app

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Labels must never wrap or break mid-word ("Equipme / nt"), even on a small phone with large text. */
@RunWith(AndroidJUnit4::class)
class SmallScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val databaseName = "small-${newId()}.db"
    private val store = WorkoutStore(app, databaseName)
    private val holder = ViewModelStore()

    @After fun cleanup() { holder.clear(); store.close(); app.deleteDatabase(databaseName) }

    // Unmerged nodes, so the label itself is measured rather than the button or field that contains it.
    private fun assertOneLine(node: SemanticsNodeInteraction, what: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        val layout = layouts.single()
        assertEquals("$what should stay on one line", 1, layout.lineCount)
        assertFalse("$what should not be clipped", layout.hasVisualOverflow)
    }

    /** A 360dp phone with Samsung's large text, and a 340dp one (display zoom turned up) with normal text. */
    @Test fun labelsStayOnOneLineOnASmallPhoneWithLargeText() = checkLabels(360, 1.3f)
    @Test fun labelsStayOnOneLineOnANarrowZoomedPhone() = checkLabels(340, 1.0f)

    private fun checkLabels(widthDp: Int, fontScale: Float) {
        val sled = Equipment(type = "Leg press", baseWeight = 167.0, loadMode = LoadMode.ADDED)
        val workout = Workout(name = "Legs").let { w -> w.copy(exercises = listOf(w.newEntry(Exercise(name = "Leg press"), sled), w.newEntry(Exercise(name = "Curl"), null).copy(sets = (1..10).map { SetEntry() }))) }
        runBlocking { store.saveEquipment(sled); store.save(workout) }
        val vm = ViewModelProvider(holder, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return IronIndexViewModel(app, store, null) as T
            }
        })[IronIndexViewModel::class.java]
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale) then DeviceConfigurationOverride.ForcedSize(DpSize(widthDp.dp, 720.dp))) {
                MaterialTheme(colorScheme = IronIndexColors) { IronIndexApp(vm) }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Equipment").fetchSemanticsNodes().isNotEmpty() }
        listOf("Partners", "Equipment", "Exercises", "Backup").forEach { assertOneLine(compose.onNodeWithText(it, useUnmergedTree = true), "Home button $it") }
        compose.runOnIdle { vm.opened.value = workout.id }
        compose.waitUntil(10000) { compose.onAllNodesWithText("plates lb").fetchSemanticsNodes().isNotEmpty() }
        assertOneLine(compose.onNodeWithText("plates lb", useUnmergedTree = true), "Set field label plates lb")
        assertOneLine(compose.onAllNodesWithText("reps", useUnmergedTree = true).onFirst(), "Set field label reps")
        // The second card is below the fold on a screen this small.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Curl set 1 weight in lb"))
        assertOneLine(compose.onAllNodesWithText("lb", useUnmergedTree = true).onFirst(), "Set field label lb")
        // Two-digit set numbers stay whole too ("1 / 0" would be a wrap).
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Curl set 10 weight in lb"))
        assertOneLine(compose.onNodeWithText("10", useUnmergedTree = true), "Set number 10")
    }
}
