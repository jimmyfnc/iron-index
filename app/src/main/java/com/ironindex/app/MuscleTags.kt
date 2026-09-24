package com.ironindex.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

fun muscleFamily(name: String): String = when (name.trim().lowercase(Locale.ROOT)) {
    "biceps", "triceps", "forearms", "arms" -> "Arms"
    "quads", "hamstrings", "glutes", "calves", "legs" -> "Legs"
    "lats", "upper back", "lower back", "back" -> "Back"
    "upper chest", "lower chest", "chest" -> "Chest"
    "front delts", "side delts", "rear delts", "shoulders" -> "Shoulders"
    "abs", "obliques", "core" -> "Core"
    else -> name.trim()
}

fun Exercise.muscleTargets(): List<String> = (primaryTargets() + secondaryMuscles)
    .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }

fun Exercise.matchesMuscles(query: String, selected: String = ""): Boolean {
    val targets = muscleTargets()
    val names = (targets + targets.map(::muscleFamily)).distinct()
    return (selected.isBlank() || names.any { it.equals(selected, true) }) &&
        (listOf(name, target) + names).any { it.contains(query.trim(), true) }
}

fun muscleFilters(exercises: List<Exercise>): List<String> = exercises.flatMap { it.muscleTargets() }
    .let { it + it.map(::muscleFamily) }.distinctBy { it.lowercase(Locale.ROOT) }.sortedBy { it.lowercase(Locale.ROOT) }

fun muscleColor(name: String): Color = when (muscleFamily(name)) {
    "Chest" -> Coral
    "Back" -> Color(0xFF84D9FF)
    "Arms" -> Lavender
    "Legs" -> Lime
    "Shoulders" -> Color(0xFFFFD580)
    "Core" -> Color(0xFF80E2C0)
    else -> Color(0xFFF5ADE0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleTags(exercise: Exercise) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        exercise.muscleTargets().forEach { muscle ->
            val secondary = exercise.primaryTargets().none { it.equals(muscle, true) }
            Surface(color = muscleColor(muscle), contentColor = Ink, shape = RoundedCornerShape(8.dp)) {
                Text(if (secondary) "$muscle · secondary" else muscle,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
