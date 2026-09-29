package com.ironindex.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The exercise library: likely duplicates first, then everything, with "Merge into…" for custom exercises. */
@Composable
fun ExerciseManager(library: List<Exercise>, workouts: List<Workout>, merge: (Exercise, Exercise) -> Unit, back: () -> Unit) {
    var mergingId by rememberSaveable { mutableStateOf<String?>(null) }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    val usage = remember(workouts) {
        workouts.flatMap { w -> w.exercises.map { it.exercise.id to w } }.groupBy({ it.first }, { it.second })
            .mapValues { (_, ws) -> ws.distinctBy { it.id }.let { list -> list.count { !it.isTemplate && it.finishedAt != null } to list.count { it.isTemplate } } }
    }
    fun usageText(e: Exercise): String {
        val (sessions, routines) = usage[e.id] ?: (0 to 0)
        return listOfNotNull(if (e.isBuiltIn) "Built-in" else "Custom", count(sessions, "session"), routines.takeIf { it > 0 }?.let { count(it, "routine") },
            e.mode.takeIf { it != ExerciseMode.BILATERAL }?.displayName()).joinToString(" · ")
    }
    val duplicates = remember(library) { duplicateGroups(library) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Exercises", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Merge duplicates so one movement keeps one history. Each machine still keeps its own.", color = Muted)
            }
        }
        if (duplicates.isNotEmpty()) {
            item { Text("Look like duplicates", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Coral, modifier = Modifier.semantics { heading() }) }
            items(duplicates, key = { group -> "dup-" + group.joinToString { it.id } }) { group ->
                Surface(color = Panel, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { e -> ExerciseRow(e, usageText(e)) { mergingId = e.id } }
                    }
                }
            }
            item { Text("All exercises", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) }
        }
        items(library, key = { it.id }) { e ->
            Surface(color = Panel, shape = MaterialTheme.shapes.large) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { ExerciseRow(e, usageText(e)) { mergingId = e.id } }
            }
        }
    }
    val from = library.find { it.id == mergingId }
    val into = library.find { it.id == targetId }
    if (from != null && into == null) AlertDialog(onDismissRequest = { mergingId = null }, title = { Text("Merge ${from.name} into…") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val targets = mergeTargets(from, library)
            if (targets.isEmpty()) Text("No other exercise is logged ${from.mode.displayName().lowercase()}, so there's nothing to merge it into yet.", color = Muted)
            else Text("Its sessions and routines will use the exercise you pick.", color = Muted, fontSize = 13.sp)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(targets, key = { it.id }) { t ->
                    TextButton(onClick = { targetId = t.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(t.name, fontWeight = FontWeight.Bold)
                            Text(usageText(t), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { mergingId = null }) { Text("Cancel") } })
    if (from != null && into != null) {
        val (sessions, routines) = usage[from.id] ?: (0 to 0)
        val inProgress = workouts.any { w -> !w.isTemplate && w.finishedAt == null && w.exercises.any { it.exercise.id == from.id } }
        val logged = listOfNotNull(sessions.takeIf { it > 0 }?.let { count(it, "session") }, routines.takeIf { it > 0 }?.let { count(it, "routine") },
            "your workout in progress".takeIf { inProgress })
        AlertDialog(onDismissRequest = { targetId = null }, title = { Text("Merge into ${into.name}?") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (logged.isEmpty()) "Nothing is logged as ${from.name} yet."
                    else "${logged.joinToString(", ").replaceFirstChar { it.uppercase() }} will use ${into.name}, including its name and muscles. Sets and machines stay as logged.")
                Text("${from.name} is removed from your library.", color = Muted, fontSize = 13.sp)
            }
        }, confirmButton = { TextButton(onClick = { merge(from, into); targetId = null; mergingId = null }) { Text("Merge") } },
            dismissButton = { TextButton(onClick = { targetId = null }) { Text("Back") } })
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, detail: String, onMerge: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(exercise.name, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
        // Built-ins can be merged into, but not merged away.
        if (!exercise.isBuiltIn) TextButton(onClick = onMerge, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Merge ${exercise.name} into another exercise" }) {
            Icon(Icons.Rounded.MergeType, null); Spacer(Modifier.width(4.dp)); Text("Merge")
        }
    }
}
