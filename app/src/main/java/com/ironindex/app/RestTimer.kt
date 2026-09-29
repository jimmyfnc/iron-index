package com.ironindex.app

import kotlinx.serialization.Serializable

/** Rest between sets when an exercise has no length of its own. */
const val DEFAULT_REST_SECONDS = 90

/** Choices offered in exercise details; 0 turns the timer off for that exercise. */
val REST_CHOICES = listOf(60, 90, 120, 180, 0)

/** The rest countdown after checking a set. Times are wall-clock milliseconds so it survives the app closing. */
@Serializable
data class RestTimer(val endsAt: Long, val totalSeconds: Int, val label: String) {
    fun remainingSeconds(now: Long): Int = ((endsAt - now + 999) / 1000).toInt().coerceAtLeast(0)
    fun done(now: Long) = now >= endsAt
    /** −15 / +15 seconds; never ends before [now]. */
    fun adjusted(deltaSeconds: Int, now: Long): RestTimer {
        val end = maxOf(now, endsAt + deltaSeconds * 1000L)
        return copy(endsAt = end, totalSeconds = maxOf(1, totalSeconds + deltaSeconds))
    }
}

/** The rest length for [exercise], or null when its timer is off. */
fun restSecondsFor(exercise: Exercise): Int? = (exercise.restSeconds ?: DEFAULT_REST_SECONDS).takeIf { it > 0 }

/** Starts a countdown after set [setNumber] of [exercise], or null when that exercise's timer is off. */
fun restAfter(exercise: Exercise, setNumber: Int, now: Long): RestTimer? =
    restSecondsFor(exercise)?.let { RestTimer(endsAt = now + it * 1000L, totalSeconds = it, label = "${exercise.name} · set $setNumber") }

/** 83 → "1:23", 7 → "0:07". */
fun clockText(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** 90 → "90 s", 120 → "2 min", 0 → "Off". */
fun restChoiceText(seconds: Int): String = when {
    seconds <= 0 -> "Off"
    seconds % 60 == 0 && seconds >= 120 -> "${seconds / 60} min"
    else -> "$seconds s"
}
