package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class RestTimerTest {
    private val bench = Exercise(id = "b", name = "Bench press")

    @Test fun restStartsWithTheExercisesLengthOrTheDefault() {
        val t = restAfter(bench, 2, now = 1_000)!!
        assertEquals(1_000L + 90_000, t.endsAt); assertEquals(90, t.totalSeconds); assertEquals("Bench press · set 2", t.label)
        assertEquals(1_000L + 180_000, restAfter(bench.copy(restSeconds = 180), 1, now = 1_000)!!.endsAt)
        assertNull(restAfter(bench.copy(restSeconds = 0), 1, now = 1_000)) // timer off for this exercise
    }

    @Test fun remainingRoundsUpAndStopsAtZero() {
        val t = RestTimer(endsAt = 10_000, totalSeconds = 90, label = "x")
        assertEquals(10, t.remainingSeconds(0)); assertEquals(1, t.remainingSeconds(9_001)); assertEquals(0, t.remainingSeconds(12_000))
        assertFalse(t.done(9_999)); assertTrue(t.done(10_000))
    }

    @Test fun adjustingAddsOrRemovesFifteenSecondsButNeverEndsInThePast() {
        val t = RestTimer(endsAt = 60_000, totalSeconds = 60, label = "x")
        assertEquals(75_000L, t.adjusted(15, now = 0).endsAt)
        assertEquals(45_000L, t.adjusted(-15, now = 0).endsAt)
        assertEquals(55_000L, t.adjusted(-15, now = 55_000).endsAt)
    }

    @Test fun clockAndChoiceText() {
        assertEquals("1:23", clockText(83)); assertEquals("0:07", clockText(7)); assertEquals("3:00", clockText(180))
        assertEquals(listOf("60 s", "90 s", "2 min", "3 min", "Off"), REST_CHOICES.map(::restChoiceText))
    }

    @Test fun olderSavedExercisesHaveNoRestSetting() {
        val e = storageJson.decodeFromString(Exercise.serializer(), """{"id":"x","name":"Row"}""")
        assertNull(e.restSeconds)
        assertEquals(DEFAULT_REST_SECONDS, restSecondsFor(e))
    }
}
