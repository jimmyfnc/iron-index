package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class MuscleFilterTest {
    @Test fun broadGroupsIncludeSpecificTargetsButSpecificFiltersStayDistinct() {
        val curl = starterExercises.first { it.id == "curl" }
        assertTrue(curl.matchesMuscles(" ARMS "))
        assertTrue(curl.matchesMuscles("curl", "Biceps"))
        assertFalse(curl.matchesMuscles("", "Triceps"))
        assertFalse(curl.matchesMuscles("press", "Biceps"))
    }
    @Test fun secondaryAndCustomTargetsAreSearchableAndDeduplicated() {
        val press = Exercise(name = "Press", primaryMuscles = listOf("Chest"),
            secondaryMuscles = listOf(" Triceps ", "chest", ""), target = "Controlled tempo")
        assertEquals(listOf("Chest", "Triceps"), press.muscleTargets())
        assertTrue(press.matchesMuscles("triceps"))
        assertTrue(press.matchesMuscles("tempo", "Arms"))
        val custom = Exercise(name = "Custom", primaryMuscles = listOf("My specific target"))
        assertTrue(custom.matchesMuscles("specific", "My specific target"))
        assertTrue(muscleFilters(listOf(press, custom)).containsAll(listOf("Arms", "Triceps", "My specific target")))
    }
    @Test fun legacySnapshotsRemainBroadWithoutInventingSpecificTargets() {
        val legacy = Exercise(name = "Old curl", muscleGroup = "Arms")
        assertTrue(legacy.matchesMuscles("arms"))
        assertFalse(legacy.matchesMuscles("", "Biceps"))
        assertTrue(Exercise(name = "Unknown").matchesMuscles(""))
    }
}
