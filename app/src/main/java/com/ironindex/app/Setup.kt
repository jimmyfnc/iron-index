package com.ironindex.app

import kotlinx.serialization.Serializable
import java.util.Locale

/** One person's value for one machine setting in a workout entry, such as Seat height = 4. Null participant is you. */
@Serializable data class SetupValue(val participantId: String? = null, val name: String, val value: String)

/** Setting names to offer for a machine type: cables usually change attachments, seated machines a seat. */
fun suggestedSettings(type: String): List<String> {
    val t = type.lowercase(Locale.ROOT)
    return when {
        "cable" in t || "pulley" in t || "crossover" in t || "functional trainer" in t -> listOf("Attachment", "Pulley height")
        // Bars on rails or a pivot: nothing to set beyond the load.
        "smith" in t || "t-bar" in t || "t bar" in t || "landmine" in t -> emptyList()
        "preacher" in t -> listOf("Seat height")
        "bench" in t -> listOf("Bench angle")
        "leg press" in t || "hack" in t -> listOf("Back pad", "Foot position")
        listOf("machine", "press", "row", "curl", "extension", "pulldown", "fly", "pec deck", "abductor", "adductor").any { it in t } -> listOf("Seat height")
        else -> emptyList()
    }
}

/** Common cable attachments, offered when a setting looks like one. */
val commonAttachments = listOf("Rope", "Straight bar", "V-bar", "Wide bar", "D-handle", "Ankle strap")

/** Trimmed names with case-insensitive duplicates and blanks removed, first spelling kept. */
fun cleanSettingNames(names: List<String>): List<String> = names.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }

fun WorkoutExercise.setupFor(participantId: String?): Map<String, String> =
    setup.filter { it.participantId == participantId }.associate { it.name to it.value }

/** Replaces one person's setup on this entry; blank values are dropped. Unchanged values leave the entry as it was. */
fun WorkoutExercise.withSetup(participantId: String?, values: Map<String, String>): WorkoutExercise {
    val mine = values.map { (k, v) -> SetupValue(participantId, k.trim(), v.trim()) }.filter { it.name.isNotBlank() && it.value.isNotBlank() }.distinctBy { it.name.lowercase(Locale.ROOT) }
    if (mine == setup.filter { it.participantId == participantId }) return this
    return copy(setup = setup.filterNot { it.participantId == participantId } + mine)
}

/** Re-keys recorded values to the spelling in [names] ("attachment" → "Attachment"), so a value is never hidden by case. */
fun Map<String, String>.keyedBy(names: List<String>): Map<String, String> =
    entries.associate { (k, v) -> (names.firstOrNull { it.equals(k, ignoreCase = true) } ?: k) to v }

/** The most recent setup this person recorded for the same exercise on the same machine, with that session's date. */
fun lastSetup(earlier: List<LoggedSet>, participantId: String?): Pair<Long, Map<String, String>>? = earlier
    .sortedByDescending { it.workout.startedAt }
    .firstNotNullOfOrNull { log -> log.entry.setupFor(participantId).takeIf { it.isNotEmpty() }?.let { log.workout.startedAt to it } }

/** Setting names to show for an entry: the machine's own, then any recorded here that it no longer lists. */
fun settingNames(machine: Equipment?, vararg recorded: Map<String, String>): List<String> =
    cleanSettingNames(machine?.settings.orEmpty() + recorded.flatMap { it.keys })

/** "Seat height 4 · Attachment Rope" in the machine's setting order. */
fun setupLabel(names: List<String>, values: Map<String, String>): String {
    val keyed = values.keyedBy(names)
    return names.mapNotNull { name -> keyed[name]?.takeIf { it.isNotBlank() }?.let { "$name $it" } }.joinToString(" · ")
}
