package com.ironindex.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.max

data class PerformanceKey(val exerciseId: String, val equipmentId: String?, val mode: ExerciseMode, val side: Side)
data class LoggedSet(val workout: Workout, val entry: WorkoutExercise, val set: SetEntry) {
    val key get() = PerformanceKey(entry.exercise.id, entry.equipmentKey(), entry.exercise.mode, set.side)
    /** Total load (bar/sled included for plates-only equipment) in kg. */
    val weightKg get() = convertWeight(entry.totalLoad(set.weight.toWeightOrNull() ?: 0.0, workout.unit), workout.unit, "kg")
    val reps get() = set.reps.toIntOrNull() ?: 0
    val volumeKg get() = weightKg * reps
    val estimatedMaxKg: Double? get() = when {
        weightKg <= 0 || reps !in 1..10 -> null
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }
}

fun completedHistory(workouts: List<Workout>, participantId: String? = null): List<LoggedSet> = workouts
    .filter { !it.isTemplate && it.finishedAt != null }
    .sortedBy { it.startedAt }
    .flatMap { w -> w.exercises.flatMap { e -> e.sets.filter { it.complete && it.valid() && it.participantId == participantId }.map { LoggedSet(w, e, it) } } }

/** One person's completed sets of [entry] (same exercise, equipment and side mode) from sessions before [current]. */
fun earlierSets(logs: List<LoggedSet>, current: Workout, entry: WorkoutExercise): List<LoggedSet> = logs.filter {
    it.workout.id != current.id && it.workout.startedAt < current.startedAt &&
        it.entry.exercise.id == entry.exercise.id && it.entry.equipmentKey() == entry.equipmentKey() && it.entry.exercise.mode == entry.exercise.mode
}

fun lastSession(earlier: List<LoggedSet>): List<LoggedSet> = earlier.groupBy { it.workout.id }.values.maxByOrNull { it.first().workout.startedAt }.orEmpty()

fun previousSession(workouts: List<Workout>, current: Workout, entry: WorkoutExercise, participantId: String?): List<LoggedSet> =
    lastSession(earlierSets(completedHistory(workouts, participantId), current, entry))

data class PersonSummary(val sessions: Int, val sets: Int, val volumeKg: Double, val lastSession: Long?)
fun personSummary(logs: List<LoggedSet>) = PersonSummary(logs.map { it.workout.id }.distinct().size, logs.size, logs.sumOf { it.volumeKg }, logs.maxOfOrNull { it.workout.startedAt })

enum class Metric(val label: String) { WEIGHT("Weight"), REPS("Reps"), ESTIMATED_MAX("Est. 1RM"), VOLUME("Volume"), PERFORMANCE("Performance") }
data class ChartPoint(val date: Long, val value: Double, val label: String = "")
fun displayWeight(kg: Double, unit: String): Double = if (unit == "lb") kg / 0.45359237 else kg

fun performanceSeries(logs: List<LoggedSet>, key: PerformanceKey, metric: Metric, unit: String): List<ChartPoint> {
    val sessions = logs.filter { it.key == key }.groupBy { it.workout.id }.values.sortedBy { it.first().workout.startedAt }
    val points = sessions.mapNotNull { sets ->
        val value = when (metric) {
            Metric.WEIGHT -> displayWeight(sets.maxOf { it.weightKg }, unit)
            Metric.REPS -> sets.maxOf { it.reps }.toDouble()
            Metric.ESTIMATED_MAX, Metric.PERFORMANCE -> sets.mapNotNull { it.estimatedMaxKg }.maxOrNull()?.let { displayWeight(it, unit) }
            Metric.VOLUME -> displayWeight(sets.sumOf { it.volumeKg }, unit)
        }
        value?.let { ChartPoint(sets.first().workout.startedAt, it) }
    }
    if (metric != Metric.PERFORMANCE) return points
    val baseline = points.firstOrNull()?.value?.takeIf { it > 0 } ?: return emptyList()
    return points.map { it.copy(value = it.value / baseline * 100.0) }
}

enum class Period(val label: String) { WEEK("Weekly"), MONTH("Monthly") }
fun periodStart(date: LocalDate, period: Period): LocalDate = if (period == Period.MONTH) date.withDayOfMonth(1) else date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
fun volumeBuckets(logs: List<LoggedSet>, period: Period, unit: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<ChartPoint> {
    val end = periodStart(today, period)
    val grouped = logs.groupBy { periodStart(Instant.ofEpochMilli(it.workout.startedAt).atZone(zone).toLocalDate(), period) }
    return (11 downTo 0).map { offset ->
        val date = if (period == Period.MONTH) end.minusMonths(offset.toLong()) else end.minusWeeks(offset.toLong())
        ChartPoint(date.atStartOfDay(zone).toInstant().toEpochMilli(), displayWeight(grouped[date].orEmpty().sumOf { it.volumeKg }, unit))
    }
}

data class MuscleWork(val name: String, val primarySets: Int, val secondarySets: Int)
fun muscleWork(logs: List<LoggedSet>): List<MuscleWork> {
    val primary = mutableMapOf<String, Int>(); val secondary = mutableMapOf<String, Int>()
    logs.forEach { log ->
        val first = log.entry.exercise.primaryTargets().map { it.lowercase(java.util.Locale.ROOT) }.toSet()
        val second = log.entry.exercise.secondaryMuscles.map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() && it !in first }.toSet()
        first.forEach { primary[it] = (primary[it] ?: 0) + 1 }
        second.forEach { secondary[it] = (secondary[it] ?: 0) + 1 }
    }
    return (primary.keys + secondary.keys).map { MuscleWork(it, primary[it] ?: 0, secondary[it] ?: 0) }.sortedByDescending { it.primarySets + it.secondarySets }
}

data class PersonalRecord(val key: PerformanceKey, val exercise: String, val equipment: String, val weightKg: Double, val reps: Int, val estimatedMaxKg: Double?, val date: Long)
fun personalRecords(logs: List<LoggedSet>): List<PersonalRecord> = logs.groupBy { it.key }.map { (key, sets) ->
    val top = sets.maxBy { it.weightKg }
    PersonalRecord(key, top.entry.exercise.name, top.entry.equipmentProfile()?.label ?: "Unspecified equipment", top.weightKg,
        sets.maxOf { it.reps }, sets.mapNotNull { it.estimatedMaxKg }.maxOrNull(), top.workout.startedAt)
}.sortedByDescending { it.date }

data class SideDifference(val exercise: String, val equipment: String, val date: Long, val left: LoggedSet, val right: LoggedSet) {
    val volumePercent get() = max(left.volumeKg, right.volumeKg).takeIf { it > 0 }?.let { abs(left.volumeKg - right.volumeKg) / it * 100 }
}
fun sideDifferences(logs: List<LoggedSet>): List<SideDifference> = logs.filter { it.set.side != Side.BOTH }
    .groupBy { Triple(it.workout.id, it.entry.id, it.set.groupId) }.values.mapNotNull { pair ->
        val left = pair.singleOrNull { it.set.side == Side.LEFT }; val right = pair.singleOrNull { it.set.side == Side.RIGHT }
        if (left == null || right == null) null else SideDifference(left.entry.exercise.name, left.entry.equipmentProfile()?.label.orEmpty(), left.workout.startedAt, left, right)
    }.sortedByDescending { it.date }

val muscleSuggestions = listOf("Chest", "Upper chest", "Back", "Lats", "Upper back", "Shoulders", "Front delts", "Side delts", "Rear delts", "Biceps", "Triceps", "Forearms", "Quads", "Hamstrings", "Glutes", "Calves", "Abs", "Obliques")
