package com.ironindex.app

import kotlinx.serialization.Serializable
import java.util.Locale

/** How weights are typed for a piece of equipment with a starting weight (bar, sled, carriage). */
@Serializable enum class LoadMode {
    /** Type the full weight, bar included (how most people log a barbell). */
    TOTAL,
    /** Type only the plates added; the starting weight is added for totals (how most people log a leg press). */
    ADDED,
}

private const val KG_PER_LB = 0.45359237

fun convertWeight(value: Double, from: String, to: String): Double = when {
    from == to -> value
    from == "kg" && to == "lb" -> value / KG_PER_LB
    from == "lb" && to == "kg" -> value * KG_PER_LB
    else -> value
}

/** The starting weight in [unit], if this equipment has a usable one (positive, finite, lb or kg). */
fun Equipment.baseIn(unit: String): Double? = baseWeight?.takeIf { it.isFinite() && it > 0 && baseUnit in setOf("lb", "kg") }?.let { convertWeight(it, baseUnit, unit) }

/** What typed numbers sit on top of, in lb: the starting weight for plates-only equipment, otherwise nothing. */
private fun Equipment?.addedBaseLb(): Double = this?.takeIf { it.loadMode == LoadMode.ADDED }?.baseIn("lb") ?: 0.0

/** True when the same typed number would now mean a different total. */
fun loadMeaningChanged(old: Equipment?, new: Equipment?): Boolean = kotlin.math.abs(old.addedBaseLb() - new.addedBaseLb()) > 1e-6

/**
 * Points the entry at [current] equipment and rewrites typed weights so every set keeps the total it had.
 * Used for routines and the workout in progress; finished sessions keep their own snapshot instead.
 * A target that can't be expressed (total below the new starting weight) is cleared.
 */
fun WorkoutExercise.rebasedTo(current: Equipment, unit: String): WorkoutExercise {
    val moved = copy(equipmentId = current.id, equipmentSnapshot = current)
    if (!loadMeaningChanged(equipmentProfile(), current)) return moved
    val newBase = current.takeIf { it.loadMode == LoadMode.ADDED }?.baseIn(unit) ?: 0.0
    return moved.copy(sets = sets.map { s ->
        val typed = s.weight.toWeightOrNull() ?: return@map s
        val retyped = totalLoad(typed, unit) - newBase
        if (retyped < -1e-6) s.copy(weight = "", complete = false) else s.copy(weight = weightText(retyped.coerceAtLeast(0.0)))
    })
}

/** This snapshot relabelled with [current]'s name, keeping the load settings its sets were logged with. */
fun Equipment.renamedLike(current: Equipment): Equipment = copy(type = current.type, brand = current.brand, model = current.model, location = current.location)

/**
 * Carries a saved catalog edit into one workout. Routines and drafts take it (numbers adjusted to keep totals);
 * finished sessions take the new name but keep their load, unless [reinterpretPast] says past numbers were typed on the new basis.
 */
fun Workout.withEquipmentEdit(saved: Equipment, reinterpretPast: Boolean): Workout {
    val finished = finishedAt != null && !isTemplate
    return copy(exercises = exercises.map { e ->
        when {
            e.equipmentKey() != saved.id -> e
            reinterpretPast -> e.copy(equipmentSnapshot = saved)
            finished -> e.equipmentSnapshot?.let { e.copy(equipmentSnapshot = it.renamedLike(saved)) } ?: e
            else -> e.rebasedTo(saved, unit)
        }
    })
}

/** Sessions and routines with a weight typed on this machine. */
fun equipmentUses(workouts: List<Workout>, id: String): Int = workouts.count { w -> w.exercises.any { e -> e.equipmentKey() == id && e.sets.any { it.weight.isNotBlank() } } }

/** "starts at 45 lb", for pickers and the catalog. */
val Equipment.baseLabel: String? get() = baseWeight?.takeIf { it > 0 }?.let {
    "starts at ${weightText(it)} $baseUnit" + if (loadMode == LoadMode.ADDED) " · log plates only" else ""
}

/** Sleds and carriages are usually logged as plates only; bars and everything else as total weight. */
fun suggestedLoadMode(type: String): LoadMode {
    val t = type.lowercase(Locale.ROOT)
    return if (listOf("leg press", "hack", "smith", "sled", "belt squat", "pendulum", "v-squat", "v squat", "t-bar", "t bar", "landmine").any { it in t }) LoadMode.ADDED else LoadMode.TOTAL
}

/** The weight that counts for totals, volume and PRs, in the workout's unit. */
fun WorkoutExercise.totalLoad(entered: Double, unit: String): Double {
    val profile = equipmentProfile() ?: return entered
    val base = profile.baseIn(unit) ?: return entered
    return if (profile.loadMode == LoadMode.ADDED) entered + base else entered
}

/** What to call the starting weight: bar, sled, or base. */
fun baseName(type: String): String {
    val t = type.lowercase(Locale.ROOT)
    return when {
        "bar" in t || "smith" in t -> "bar"
        listOf("press", "sled", "hack", "pendulum", "squat").any { it in t } -> "sled"
        else -> "base"
    }
}

/** Standard plates, heaviest first. */
fun platesFor(unit: String): List<Double> = if (unit == "kg") listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25) else listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

/** Greedy plate breakdown for one side, or null if it can't be made exactly with standard plates. */
fun plateBreakdown(perSide: Double, unit: String): List<Double>? {
    if (perSide < 0) return null
    var left = perSide
    val plates = mutableListOf<Double>()
    for (p in platesFor(unit)) while (left >= p - 1e-6) { plates += p; left -= p }
    return plates.takeIf { left < 1e-6 }
}

/**
 * The line shown under a weight field, or null when there's nothing useful to add.
 * Plates only: "+167 sled = 347 total". Total on a bar: "45 bar + 90 per side (45 + 45)".
 */
fun loadHint(entry: WorkoutExercise, typed: String, unit: String): String? {
    val profile = entry.equipmentProfile() ?: return null
    val base = profile.baseIn(unit) ?: return null
    val baseText = weightText(base)
    val entered = typed.toWeightOrNull()
    val what = baseName(profile.type)
    if (profile.loadMode == LoadMode.ADDED) {
        return if (entered == null) "+$baseText $unit $what added to totals" else "+$baseText $what = ${weightText(entered + base)} $unit total"
    }
    if (entered == null) return "Includes the $baseText $unit $what"
    if (entered < base - 1e-6) return "Less than the $baseText $unit $what"
    // Plate math only makes sense for two-sided loading (bars, plate-loaded machines).
    if (entry.exercise.mode != ExerciseMode.BILATERAL) return "$baseText $what + ${weightText(entered - base)} $unit"
    val perSide = (entered - base) / 2
    val plates = plateBreakdown(perSide, unit)?.takeIf { it.isNotEmpty() }?.joinToString(" + ") { weightText(it) }
    return "$baseText $what + ${weightText(perSide)} per side" + (plates?.let { " ($it)" } ?: "")
}

/** 45 → "45", 22.5 → "22.5", 167.64 → "167.6". */
fun weightText(value: Double): String =
    if (kotlin.math.abs(value - Math.round(value)) < 1e-6) Math.round(value).toString() else String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
