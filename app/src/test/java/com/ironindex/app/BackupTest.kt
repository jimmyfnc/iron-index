package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val press = Exercise(id = "custom-press", name = "Landmine press")
    private val machine = Equipment(id = "eq", type = "Chest press", brand = "Matrix")
    private val alex = Partner(id = "alex", name = "Alex", goal = "Bench 225")
    private fun session(id: String, updatedAt: Long = 10, finished: Boolean = true) = Workout(id = id, name = "S-$id", startedAt = 5, updatedAt = updatedAt,
        finishedAt = if (finished) 6 else null, exercises = listOf(WorkoutExercise(exercise = press, sets = listOf(SetEntry(weight = "50", reps = "8", complete = true)))))
    private val routine = Workout(id = "r", name = "Push", isTemplate = true)
    private val backup = Backup(appVersion = "0.4.0", exportedAt = 99, workouts = listOf(session("a"), session("b"), routine),
        exercises = listOf(press), equipment = listOf(machine), partners = listOf(alex))

    @Test fun roundTripPreservesEverything() {
        assertEquals(backup, parseBackup(backup.encode()))
    }

    @Test fun importingIntoAnEmptyPhoneAddsEverythingAndTwiceChangesNothing() {
        val plan = planImport(backup, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(2, plan.newSessions); assertEquals(1, plan.newRoutines)
        assertEquals(listOf(press), plan.exercises); assertEquals(listOf(machine), plan.equipment); assertEquals(listOf(alex), plan.partners)
        val again = planImport(backup, plan.workouts, plan.exercises, plan.equipment, plan.partners)
        assertTrue(again.isEmpty)
        assertEquals(3, again.unchanged)
        assertEquals("Nothing new: everything in this backup is already on this phone.", again.summary())
    }

    @Test fun newerWorkoutsReplaceOlderOnesButNeverTheReverse() {
        val local = listOf(session("a", updatedAt = 50), session("b", updatedAt = 1))
        val plan = planImport(backup, local, emptyList(), emptyList(), emptyList())
        assertEquals(listOf("b", "r"), plan.workouts.map { it.id })
        assertEquals(1, plan.updatedWorkouts); assertEquals(1, plan.unchanged)
    }

    @Test fun thePhonesCatalogCopyWins() {
        val renamed = alex.copy(name = "Alexandra")
        val plan = planImport(backup, emptyList(), listOf(press.copy(name = "Local")), listOf(machine), listOf(renamed))
        assertTrue(plan.exercises.isEmpty() && plan.equipment.isEmpty() && plan.partners.isEmpty())
    }

    @Test fun aSecondDraftIsSkippedButTheSameDraftCanUpdate() {
        val draft = session("draft", updatedAt = 20, finished = false)
        val withDraft = backup.copy(workouts = backup.workouts + draft)
        val otherLocalDraft = session("mine", finished = false)
        val skipped = planImport(withDraft, listOf(otherLocalDraft), emptyList(), emptyList(), emptyList())
        assertEquals(1, skipped.skippedDrafts)
        assertTrue(skipped.workouts.none { it.id == "draft" })
        val same = planImport(withDraft, listOf(draft.copy(updatedAt = 5)), emptyList(), emptyList(), emptyList())
        assertEquals(1, same.updatedWorkouts)
        assertEquals(0, same.skippedDrafts)
    }

    @Test fun anUnfinishedCopyNeverReplacesAFinishedSession() {
        val finishedHere = session("a", updatedAt = 10)
        val stillOpenInFile = session("a", updatedAt = 99, finished = false)
        val plan = planImport(Backup(workouts = listOf(stillOpenInFile)), listOf(finishedHere), emptyList(), emptyList(), emptyList())
        assertTrue(plan.workouts.isEmpty())
        assertEquals(1, plan.unchanged)
    }

    @Test fun draftsOnlyRestoreOntoAnEmptyPhoneSoDiscardedDraftsStayGone() {
        val draft = session("draft", finished = false)
        val file = Backup(workouts = listOf(draft))
        assertEquals(listOf("draft"), planImport(file, emptyList(), emptyList(), emptyList(), emptyList()).workouts.map { it.id })
        val onUsedPhone = planImport(file, listOf(session("old")), emptyList(), emptyList(), emptyList())
        assertTrue(onUsedPhone.workouts.isEmpty())
        assertEquals(1, onUsedPhone.skippedDrafts)
        assertTrue(onUsedPhone.phoneHadWorkouts)
    }

    @Test fun matchingEquipmentAndExercisesAreLinkedInsteadOfDuplicated() {
        val localMachine = machine.copy(id = "local-eq")
        val localPress = press.copy(id = "local-press", name = " landmine PRESS ")
        val fromFile = session("a").let { w -> w.copy(exercises = w.exercises.map { it.copy(equipmentId = machine.id, equipmentSnapshot = machine,
            exercise = press.copy(defaultEquipmentId = machine.id)) }) }
        val plan = planImport(Backup(workouts = listOf(fromFile), exercises = listOf(press), equipment = listOf(machine)),
            listOf(session("unrelated")), listOf(localPress), listOf(localMachine), emptyList())
        assertTrue(plan.equipment.isEmpty() && plan.exercises.isEmpty())
        val entry = plan.workouts.single().exercises.single()
        assertEquals("local-eq", entry.equipmentId)
        assertEquals("local-eq", entry.equipmentSnapshot?.id)
        assertEquals("local-press", entry.exercise.id)
        assertEquals("local-eq", entry.exercise.defaultEquipmentId)
        assertEquals(2, plan.matched)
        assertTrue(plan.summary().contains("linked"))
        // Importing again writes nothing and says so.
        val again = planImport(Backup(workouts = listOf(fromFile), exercises = listOf(press), equipment = listOf(machine)),
            listOf(session("unrelated"), plan.workouts.single()), listOf(localPress), listOf(localMachine), emptyList())
        assertTrue(again.isEmpty)
        assertFalse(again.summary().contains("linked"))
    }

    @Test fun byteOrderMarksFromDesktopEditorsAreIgnored() {
        assertEquals(backup, parseBackup("﻿" + backup.encode()))
    }

    @Test fun damagedForeignAndFutureFilesAreRejectedCleanly() {
        fun rejects(text: String, message: String) {
            val e = assertThrows(BackupException::class.java) { parseBackup(text) }
            assertTrue(e.message, e.message!!.contains(message))
        }
        rejects("", "readable")
        rejects(backup.encode().dropLast(40), "readable")
        rejects("""{"format":"something-else","version":1}""", "isn’t an IronIndex backup")
        rejects(backup.copy(version = BACKUP_VERSION + 1).encode(), "newer version")
        rejects(backup.copy(partners = listOf(alex.copy(id = ""))).encode(), "without IDs")
    }

    @Test fun backupsFromOlderVersionsWithoutNewerFieldsStillImport() {
        // Shape written before per-person skips, partner goals/notes and appVersion existed.
        val old = """{"format":"ironindex-backup","version":1,"exportedAt":1,
            "workouts":[{"id":"w","name":"Legs","unit":"kg","startedAt":1,"updatedAt":1,"finishedAt":2,
              "exercises":[{"id":"e","exercise":{"id":"squat","name":"Squat"},"sets":[{"id":"s","reps":"5","weight":"100","complete":true}]}]}],
            "partners":[{"id":"p","name":"Sam"}]}"""
        val parsed = parseBackup(old)
        assertTrue(parsed.workouts.single().exercises.single().skippedBy.isEmpty())
        assertEquals("", parsed.partners.single().goal)
        assertEquals(1, completedHistory(parsed.workouts).size)
    }

    @Test fun oversizedFilesAreRefusedWithoutReadingEverything() {
        assertEquals("hello", readLimited("hello".byteInputStream(), 10))
        assertThrows(BackupException::class.java) { readLimited(ByteArray(200_000).inputStream(), 100_000) }
    }

    @Test fun fileNameCarriesTheDate() {
        assertEquals("ironindex-backup-2026-09-24.json", backupFileName(java.time.LocalDate.of(2026, 9, 24)))
    }
}
