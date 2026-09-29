package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class ExerciseMergeTest {
    private val curl = starterExercises.single { it.id == "curl" } // "Biceps curl", built in
    private val customCurl = Exercise(id = "c1", name = "Biceps Curl", primaryMuscles = listOf("Biceps"))
    private val machine = Equipment(id = "m", type = "Bicep Curl", brand = "Matrix")

    @Test fun namesThatDifferOnlyInCaseSpacingOrPluralAreDuplicates() {
        assertEquals(exerciseNameKey("Biceps curl"), exerciseNameKey("  biceps CURLS "))
        assertEquals(exerciseNameKey("Lat pull-down"), exerciseNameKey("Lat Pulldown"))
        assertNotEquals(exerciseNameKey("Seated row"), exerciseNameKey("Seated Row M"))
    }

    @Test fun duplicateGroupsPutTheBuiltInFirstAndIgnoreDifferentSides() {
        val oneArm = Exercise(id = "c2", name = "Biceps curls", mode = ExerciseMode.SINGLE_ARM)
        val groups = duplicateGroups(listOf(customCurl, curl, oneArm, Exercise(id = "c3", name = "Fly")))
        assertEquals(listOf(listOf("curl", "c1")), groups.map { g -> g.map { it.id } })
    }

    @Test fun mergeTargetsListLikelyDuplicatesFirstAndOnlySameSides() {
        val library = listOf(Exercise(id = "a", name = "Arnold press"), curl, customCurl, Exercise(id = "u", name = "Curl one arm", mode = ExerciseMode.SINGLE_ARM))
        assertEquals(listOf("curl", "a"), mergeTargets(customCurl, library).map { it.id })
    }

    @Test fun builtInsAndDifferentSidesCantBeMergedAway() {
        assertNull(mergeProblem(customCurl, curl))
        assertNotNull(mergeProblem(curl, customCurl))
        assertNotNull(mergeProblem(customCurl, customCurl))
        assertNotNull(mergeProblem(customCurl, curl.copy(mode = ExerciseMode.SINGLE_ARM)))
    }

    @Test fun mergingRewritesEntriesButKeepsSetsMachinesAndSetup() {
        val sets = listOf(SetEntry(weight = "40", reps = "12", complete = true))
        val setup = listOf(SetupValue(null, "Seat height", "3"))
        val w = Workout(name = "Arms", exercises = listOf(
            WorkoutExercise(id = "e1", exercise = customCurl, equipmentId = machine.id, equipmentSnapshot = machine, sets = sets, setup = setup),
            WorkoutExercise(id = "e2", exercise = Exercise(id = "other", name = "Fly")),
        ))
        val merged = w.withExerciseMerged("c1", curl)
        val entry = merged.exercises.first()
        assertEquals("curl", entry.exercise.id); assertEquals("Biceps curl", entry.exercise.name)
        assertEquals(sets, entry.sets); assertEquals(machine, entry.equipmentSnapshot); assertEquals(setup, entry.setup)
        assertEquals("other", merged.exercises[1].exercise.id)
        assertSame(merged, merged.withExerciseMerged("c1", curl)) // nothing left to merge: unchanged
    }

    @Test fun pluralsAndEmptyNamesAreHandled() {
        assertEquals(exerciseNameKey("Bench press"), exerciseNameKey("Bench presses"))
        assertEquals("benchpress", exerciseNameKey("Bench press"))
        assertEquals("", exerciseNameKey("!!!"))
        assertTrue(duplicateGroups(listOf(Exercise(id = "x", name = "!!!"), Exercise(id = "y", name = "???"))).isEmpty())
    }

    @Test fun eachEntryKeepsItsOwnSidesAndMachine() {
        val oneArmTarget = Exercise(id = "row1", name = "Cable row", mode = ExerciseMode.SINGLE_ARM)
        val legacy = Exercise(id = "c9", name = "Cable Row", equipment = "Cable machine", brand = "Matrix")
        val w = Workout(name = "Back", exercises = listOf(WorkoutExercise(exercise = legacy, sets = listOf(SetEntry(weight = "80", reps = "10", complete = true)))))
        val entry = w.withExerciseMerged("c9", oneArmTarget).exercises.single()
        assertEquals(ExerciseMode.BILATERAL, entry.exercise.mode)
        assertEquals("row1", entry.exercise.id)
        assertEquals("Cable machine · Matrix", entry.equipmentProfile()?.label)
    }
}
