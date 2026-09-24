package com.ironindex.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutStoreTest {
    @Test fun workoutSurvivesReopeningAndEditsRemainAtomic() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "test-${newId()}.db"
        val store = WorkoutStore(context, databaseName)
        val exercise = Exercise(name = "Test press", equipment = "Machine", brand = "Test brand")
        val id = store.start(null, "kg")
        try {
            store.edit(id) { it.copy(exercises = listOf(WorkoutExercise(exercise = exercise))) }
            coroutineScope {
                repeat(12) { launch { store.edit(id) { w -> w.copy(exercises = w.exercises.map { e -> e.copy(sets = e.sets + SetEntry(reps = "10", weight = "25", complete = true)) }) } } }
            }
            val reopened = WorkoutStore(context, databaseName)
            val restored = try { reopened.workouts.first().first { it.id == id } } finally { reopened.close() }
            assertEquals(13, restored.exercises.single().sets.size)
            assertEquals(12, restored.completedSets)
            assertEquals("Test brand", restored.exercises.single().exercise.brand)
            assertEquals(id, store.start(null, "lb"))
            store.edit(id) { it.copy(finishedAt = System.currentTimeMillis()) }
            val next = store.start(restored, "lb")
            try {
                assertNotEquals(id, next)
                val repeated = store.workouts.first().first { it.id == next }
                assertEquals(0, repeated.completedSets)
                assertEquals("kg", repeated.unit)
            } finally { store.delete(next) }
        } finally { store.close(); context.deleteDatabase(databaseName) }
    }

    @Test fun routinesReplaceEmptyDraftsButKeepRealOnesUntilTheUserDecides() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "start-${newId()}.db"
        val store = WorkoutStore(context, databaseName)
        val push = Workout(name = "Push", isTemplate = true, unit = "kg", exercises = listOf(WorkoutExercise(exercise = Exercise(name = "Press"))))
        val pull = push.copy(id = newId(), name = "Pull")
        try {
            val empty = store.start(null, "lb")
            val fromPush = store.start(push, "lb")
            assertNotEquals(empty, fromPush)
            assertTrue(store.workouts.first().none { it.id == empty })
            assertEquals(fromPush, store.start(pull, "lb"))
            assertEquals(fromPush, store.activeDraft()?.id)
            val fromPull = store.start(pull, "lb", replaceDraft = true)
            assertNotEquals(fromPush, fromPull)
            assertEquals(listOf("Pull"), store.workouts.first().filter { !it.isTemplate }.map { it.name })
            // The session date moves to the first checked set, and stays put afterwards.
            val created = store.activeDraft()!!.startedAt
            Thread.sleep(25)
            store.edit(fromPull) { w -> w.copy(exercises = w.exercises.map { e -> e.copy(sets = listOf(SetEntry(reps = "5", weight = "60", complete = true))) }) }
            val stamped = store.activeDraft()!!.startedAt
            assertTrue(stamped > created)
            store.edit(fromPull) { w -> w.copy(exercises = w.exercises.map { e -> e.copy(sets = e.sets + SetEntry(reps = "5", weight = "60", complete = true)) }) }
            assertEquals(stamped, store.activeDraft()!!.startedAt)
        } finally { store.close(); context.deleteDatabase(databaseName) }
    }
}
