package com.ironindex.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun versionOneWorkoutsMigrateWithoutChangingEnteredData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${newId()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val payload = """{"id":"old-session","name":"Field test","unit":"lb","startedAt":123,"updatedAt":456,"finishedAt":789,"exercises":[{"id":"entry","exercise":{"id":"press","name":"Incline press","equipment":"Plate loaded","brand":"Hammer Strength ISO","muscleGroup":"Chest"},"sets":[{"id":"old-set","reps":"12","weight":"90","complete":true}]}]}"""
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE workouts (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE exercises (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
            db.execSQL("INSERT INTO workouts VALUES (?, ?)", arrayOf("old-session", payload))
            db.version = 1
        }
        val store = WorkoutStore(context, name)
        try {
            val workout = store.workouts.first().single()
            assertEquals("old-session", workout.id)
            assertEquals(123L, workout.startedAt)
            assertEquals(456L, workout.updatedAt)
            assertEquals(789L, workout.finishedAt)
            assertEquals("lb", workout.unit)
            val entry = workout.exercises.single()
            val set = entry.sets.single()
            assertEquals("old-set", set.id)
            assertEquals("12", set.reps)
            assertEquals("90", set.weight)
            assertTrue(set.complete)
            assertEquals(Side.BOTH, set.side)
            assertNull(set.participantId)
            assertEquals("Hammer Strength ISO", entry.equipmentSnapshot!!.brand)
            assertEquals("", entry.equipmentSnapshot!!.model)
            assertTrue(store.equipment.first().any { it.id == entry.equipmentId })
            assertEquals(1080.0, workout.volume, 0.001)
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
