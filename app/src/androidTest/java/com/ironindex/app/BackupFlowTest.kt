package com.ironindex.app

import android.app.Application
import android.net.Uri
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupFlowTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val sourceName = "backup-source-${newId()}.db"
    private val targetName = "backup-target-${newId()}.db"
    private val source = WorkoutStore(app, sourceName)
    private val target = WorkoutStore(app, targetName)
    private val file = File(app.cacheDir, "backup-${newId()}.json")
    private fun awaitText(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }

    private fun clearExportDate() = app.getSharedPreferences("ironindex", 0).edit().remove("lastExport").commit()
    @org.junit.Before fun setup() { clearExportDate() }

    @After fun cleanup() {
        clearExportDate()
        source.close(); target.close(); file.delete()
        app.deleteDatabase(sourceName); app.deleteDatabase(targetName)
    }

    @Test fun exportThenImportOnAFreshPhoneRestoresEverythingAndIsIdempotent() {
        val alex = Partner(name = "Alex", goal = "Bench 225", notes = "No dips")
        val machine = Equipment(type = "Chest press", brand = "Matrix", model = "Ultra")
        val custom = Exercise(name = "Landmine press", primaryMuscles = listOf("Chest"))
        val session = Workout(name = "Push day", unit = "kg", startedAt = 1_000, finishedAt = 2_000, partners = listOf(WorkoutPartner(alex.id, alex.name, true)),
            exercises = listOf(WorkoutExercise(exercise = custom, equipmentId = machine.id, equipmentSnapshot = machine, sets = listOf(
                SetEntry(weight = "60", reps = "8", complete = true), SetEntry(weight = "40", reps = "10", complete = true, participantId = alex.id)))))
        val routine = session.freshCopy(true, "Push routine")
        runBlocking { source.save(session); source.save(routine); source.savePartner(alex); source.saveEquipment(machine); source.saveExercise(custom) }

        val exporter = IronIndexViewModel(app, source)
        exporter.exportTo(Uri.fromFile(file))
        compose.waitUntil(10000) { exporter.lastExport.value != null && file.length() > 0 }
        assertTrue(file.readText().contains("\"format\":\"ironindex-backup\""))
        clearExportDate() // The import side plays a fresh phone that has never exported.

        val vm = IronIndexViewModel(app, target)
        compose.setContent { MaterialTheme(colorScheme = IronIndexColors) { IronIndexApp(vm) } }
        compose.onNodeWithText("Backup").performClick()
        awaitText("Backup & restore")
        compose.onNodeWithText("Never exported").assertExists()
        compose.runOnIdle { vm.readImport(Uri.fromFile(file)) }
        awaitText("Import this backup?")
        compose.onNodeWithText("1 new session", substring = true).assertExists()
        compose.onNodeWithText("Import").performClick()
        compose.waitUntil(10000) { runBlocking { target.workouts.first().size == 2 } }

        val restored = runBlocking { target.workouts.first() }
        assertEquals(runBlocking { source.workouts.first() }.toSet(), restored.toSet())
        assertEquals(listOf(alex), runBlocking { target.partners.first() })
        assertTrue(runBlocking { target.equipment.first() }.contains(machine))
        assertTrue(runBlocking { target.exercises.first() }.contains(custom))
        assertEquals(1, completedHistory(restored, alex.id).size)

        compose.runOnIdle { vm.readImport(Uri.fromFile(file)) }
        awaitText("Already up to date")
        compose.onNodeWithText("Done").performClick()
        assertEquals(2, runBlocking { target.workouts.first() }.size)
    }

    @Test fun importRePlansSoStartingAWorkoutAfterThePreviewNeverLeavesTwoDrafts() = runBlocking {
        val backupDraft = Workout(name = "Backup draft", exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Row"))))
        val finished = Workout(name = "Done", startedAt = 1, finishedAt = 2, exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Row"),
            sets = listOf(SetEntry(weight = "50", reps = "5", complete = true)))))
        val file = Backup(workouts = listOf(backupDraft, finished))
        assertEquals(2, target.planImport(file).workouts.size) // Preview on an empty phone includes the draft.
        val mine = target.start(null, "kg")                      // Then the user starts a workout before confirming.
        val applied = target.applyImport(file)
        assertEquals(1, applied.skippedDrafts)
        val drafts = target.workouts.first().filter { !it.isTemplate && it.finishedAt == null }
        assertEquals(listOf(mine), drafts.map { it.id })
        assertTrue(target.workouts.first().any { it.id == finished.id })
    }

    @Test fun aDamagedFileShowsAnErrorAndWritesNothing() {
        file.writeText("{\"format\":\"ironindex-backup\",\"workouts\":[{\"id\":")
        val vm = IronIndexViewModel(app, target)
        compose.setContent { MaterialTheme(colorScheme = IronIndexColors) { IronIndexApp(vm) } }
        compose.runOnIdle { vm.readImport(Uri.fromFile(file)) }
        awaitText("isn’t a readable IronIndex backup")
        assertTrue(runBlocking { target.workouts.first() }.isEmpty())
    }
}
