package com.ironindex.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

@Composable
fun AnalyticsScreen(workouts: List<Workout>, partners: List<Partner>, unit: String, person: String, choosePerson: (String) -> Unit) {
    var periodName by rememberSaveable { mutableStateOf(Period.WEEK.name) }
    var exerciseChoice by rememberSaveable { mutableStateOf("") }
    var metricName by rememberSaveable { mutableStateOf(Metric.WEIGHT.name) }
    val period = Period.valueOf(periodName)
    val metric = Metric.valueOf(metricName)
    val logs = remember(workouts, person) { completedHistory(workouts, person.ifBlank { null }) }
    val variants = remember(logs) { logs.groupBy { it.key }.map { (key, values) ->
        key to "${values.last().entry.exercise.name} · ${values.last().entry.equipmentProfile()?.label ?: "Unspecified equipment"} · ${if (key.side == Side.BOTH) "Both sides" else key.side.name.lowercase()}"
    } }
    fun keyId(key: PerformanceKey) = "${key.exerciseId}/${key.equipmentId}/${key.mode}/${key.side}"
    val selected = variants.find { keyId(it.first) == exerciseChoice } ?: variants.firstOrNull()
    val points = remember(logs, selected?.first, metric, unit) { selected?.let { performanceSeries(logs, it.first, metric, unit) }.orEmpty() }
    val buckets = remember(logs, period, unit) { volumeBuckets(logs, period, unit) }
    val today = LocalDate.now()
    val rangeStart = periodStart(today, period)
    val rangeEnd = if (period == Period.WEEK) rangeStart.plusWeeks(1) else rangeStart.plusMonths(1)
    val recent = remember(logs, rangeStart, rangeEnd) { logs.filter {
        val date = Instant.ofEpochMilli(it.workout.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        !date.isBefore(rangeStart) && date.isBefore(rangeEnd)
    } }
    val muscles = remember(recent) { muscleWork(recent) }
    val records = remember(logs) { personalRecords(logs) }
    val differences = remember(logs) { sideDifferences(logs) }
    val profiles = (partners + workouts.flatMap { it.partners }.map { Partner(it.id, it.name) }).distinctBy { it.id }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Your work, in focus.", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("Completed sets only. Each person and machine has its own story.", color = Muted)
        }
        item { ChoiceField("Person", person, listOf("" to "You") + profiles.map { it.id to (it.name + if (it.archived) " (archived)" else "") }) { choosePerson(it); exerciseChoice = "" } }
        if (logs.isEmpty()) item { Text("Finish a workout with this person’s sets to see their history, charts and records here.", color = Lavender) }
        else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Period.entries.forEach { p -> FilterChip(period == p, { periodName = p.name }, label = { Text(p.label) }) } }
                Text("This ${if (period == Period.WEEK) "week" else "month"} · ${count(recent.map { it.workout.id }.distinct().size, "session")} · ${count(recent.size, "logged set")}", fontWeight = FontWeight.Bold)
                Text("${numberLabel(displayWeight(recent.sumOf { it.volumeKg }, unit))} $unit × reps", color = Lime, fontSize = 24.sp)
            }
            item { TrendChart("${period.label} volume · last 12 periods", buckets, "$unit × reps") }
            item {
                SectionTitle("Sets by muscle")
                Text("Current ${if (period == Period.WEEK) "week" else "month"}. Primary and secondary counts are separate. Each logged side counts as one set; one set can target multiple muscles.", color = Muted, fontSize = 12.sp)
                if (muscles.isEmpty()) Text("No muscle targets recorded in this period.")
                muscles.forEach { muscle ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(muscle.name.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                        Text("${muscle.primarySets} primary / ${muscle.secondarySets} secondary", color = Lavender, modifier = Modifier.weight(1.3f))
                    }
                }
            }
            item {
                SectionTitle("Exercise progression")
                ChoiceField("Exercise · equipment · side", selected?.let { keyId(it.first) }.orEmpty(), variants.map { keyId(it.first) to it.second }) { exerciseChoice = it }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric.entries.forEach { m -> FilterChip(metric == m, { metricName = m.name }, label = { Text(m.label) }) } }
            }
            item {
                val suffix = when (metric) { Metric.REPS -> "reps"; Metric.VOLUME -> "$unit × reps"; Metric.PERFORMANCE -> "% of baseline"; else -> unit }
                TrendChart(metric.label, points, suffix)
                Text(when (metric) {
                    Metric.WEIGHT -> "Heaviest completed set in each session."
                    Metric.REPS -> "Highest reps in any completed set; the load can differ."
                    Metric.VOLUME -> "Sum of weight × reps for the selected exercise, machine, person and side."
                    Metric.ESTIMATED_MAX -> "Epley estimate: weight × (1 + reps / 30). Uses 1–10 reps and positive weight; a single rep uses its actual weight. An estimate, not a tested max."
                    Metric.PERFORMANCE -> "Best estimated 1RM relative to the first eligible session (100%). Same machine, person and side."
                }, color = Muted, fontSize = 12.sp)
                if (points.size > 1) {
                    val delta = points.last().value - points.first().value
                    Text("First → latest: ${if (delta >= 0) "+" else ""}${numberLabel(delta)} $suffix", color = Lavender)
                }
            }
            item {
                SectionTitle("Exercise frequency")
                Text("Current ${if (period == Period.WEEK) "week" else "month"}, grouped by exercise and equipment.", color = Muted, fontSize = 12.sp)
                recent.groupBy { it.entry.exercise.id to it.entry.equipmentKey() }.values.sortedByDescending { it.size }.forEach { values ->
                    Text("${values.first().entry.exercise.name} · ${values.map { it.workout.id }.distinct().size} sessions / ${values.size} sets", modifier = Modifier.padding(top = 12.dp))
                    Text(values.first().entry.equipmentProfile()?.label ?: "Unspecified equipment", color = Muted, fontSize = 12.sp)
                }
            }
            item { SectionTitle("Personal records"); Text("All time. Best weight, reps and estimated 1RM can come from different sets. Records stay separate by equipment and side.", color = Muted, fontSize = 12.sp) }
            items(records, key = { "pr-${keyId(it.key)}" }) { record ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${record.exercise} · ${if (record.key.side == Side.BOTH) "Both sides" else record.key.side.name.lowercase()}", fontWeight = FontWeight.Bold)
                        Text(record.equipment, color = Lavender)
                        Text("Weight ${numberLabel(displayWeight(record.weightKg, unit))} $unit · Reps ${record.reps}")
                        Text("Estimated 1RM: ${record.estimatedMaxKg?.let { "${numberLabel(displayWeight(it, unit))} $unit" } ?: "No eligible sets"}")
                    }
                }
            }
            item { SectionTitle("Left / right comparison"); Text("Matched sets from the same session and round. Missing or unchecked sides are excluded. Difference = absolute volume gap ÷ larger side volume.", color = Muted, fontSize = 12.sp) }
            if (differences.isEmpty()) item { Text("Complete both sides of a unilateral set to compare them.") }
            items(differences.take(20), key = { "lr-${it.left.workout.id}-${it.left.entry.id}-${it.left.set.groupId}" }) { d ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${d.exercise} · ${dateLabel(d.date)}", fontWeight = FontWeight.Bold)
                        Text(d.equipment, color = Muted)
                        Text("Left: ${numberLabel(displayWeight(d.left.weightKg, unit))} $unit × ${d.left.reps}", color = Lavender)
                        Text("Right: ${numberLabel(displayWeight(d.right.weightKg, unit))} $unit × ${d.right.reps}", color = Coral)
                        Text(d.volumePercent?.let { "${numberLabel(it)}% volume difference" } ?: "No external-load volume to compare")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) { Text(text, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }

@Composable
fun TrendChart(title: String, points: List<ChartPoint>, unit: String) {
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    var table by rememberSaveable { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (points.isEmpty()) Text("No eligible data yet.", color = Muted)
            else {
                val chosen = points[selected.coerceIn(points.indices)]
                Text("${dateLabel(chosen.date)} · ${numberLabel(chosen.value)} $unit", color = Lime)
                val maximum = max(points.maxOf { it.value }, 1.0)
                Text("${numberLabel(maximum)} $unit", style = MaterialTheme.typography.labelSmall, color = Muted)
                Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "$title. ${points.size} data points. Values available using Show chart values." }
                    .pointerInput(points) { detectTapGestures { tap ->
                        val usable = max(size.width - 24f, 1f)
                        selected = points.indices.minBy { i -> abs((if (points.size == 1) size.width / 2f else 12f + usable * i / points.lastIndex) - tap.x) }
                    } }) {
                    val width = max(size.width - 24f, 1f)
                    val height = max(size.height - 20f, 1f)
                    repeat(4) { row -> val y = 10 + height * row / 3; drawLine(grid, Offset(12f, y), Offset(size.width - 12f, y), 1f) }
                    val coordinates = points.mapIndexed { index, point -> Offset(if (points.size == 1) size.width / 2 else 12 + width * index / points.lastIndex, 10 + height * (1 - point.value / maximum).toFloat()) }
                    val path = Path().apply { coordinates.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
                    coordinates.forEachIndexed { i, p -> drawCircle(color, if (i == selected) 6.dp.toPx() else 3.dp.toPx(), p) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0", color = Muted); Text("${dateLabel(points.first().date)} → ${dateLabel(points.last().date)}", style = MaterialTheme.typography.labelSmall, color = Muted) }
                Text("Points are spaced by session / period. Tap a point to inspect it.", style = MaterialTheme.typography.bodySmall, color = Muted)
                TextButton(onClick = { table = !table }) { Text(if (table) "Hide chart values" else "Show chart values") }
                if (table) points.forEachIndexed { index, point -> TextButton(onClick = { selected = index }) { Text("${dateLabel(point.date)} · ${numberLabel(point.value)} $unit") } }
            }
        }
    }
}

@Composable
fun ExerciseHistoryDialog(logs: List<LoggedSet>, dismiss: () -> Unit) {
    val sessions = logs.groupBy { it.workout.id }.values.sortedByDescending { it.first().workout.startedAt }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Exercise history") }, text = {
        LazyColumn(Modifier.heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (sessions.isEmpty()) item { Text("No earlier completed sessions for this person and equipment.") }
            items(sessions, key = { it.first().workout.id }) { values ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${dateLabel(values.first().workout.startedAt)} · ${values.first().workout.name}", fontWeight = FontWeight.Bold)
                    values.forEach { log -> Text("${if (log.set.side == Side.BOTH) "" else log.set.side.name.lowercase() + " · "}${log.set.weight} ${log.workout.unit} × ${log.set.reps}") }
                    HorizontalDivider()
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}
