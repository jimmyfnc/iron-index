package com.ironindex.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkoutScreen(workout: Workout, library: List<Exercise>, equipment: List<Equipment>, partners: List<Partner>, history: List<Workout>, vm: IronIndexViewModel) {
    var adding by rememberSaveable(workout.id) { mutableStateOf(false) }
    var renaming by rememberSaveable(workout.id) { mutableStateOf(false) }
    var saving by rememberSaveable(workout.id) { mutableStateOf(false) }
    var finishing by rememberSaveable(workout.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(workout.id) { mutableStateOf(false) }
    var ordering by rememberSaveable(workout.id) { mutableStateOf(false) }
    var managingPartners by rememberSaveable(workout.id) { mutableStateOf(false) }
    var athlete by rememberSaveable(workout.id) { mutableStateOf(vm.openedParticipant.value.orEmpty()) }
    val participantId = athlete.ifBlank { null }
    val readOnly = workout.finishedAt != null
    val tracked = workout.partners.filter { it.trackSets }
    val personName = tracked.find { it.id == athlete }?.name
    val athleteProfile = partners.find { it.id == athlete }
    LaunchedEffect(workout.id) { vm.openedParticipant.value = null }
    LaunchedEffect(workout.partners) { if (athlete.isNotBlank() && workout.partners.none { it.id == athlete && it.trackSets }) athlete = "" }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().imePadding()) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { vm.opened.value = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text(if (workout.isTemplate) "ROUTINE BUILDER" else if (readOnly) "SESSION COMPLETE" else "IN YOUR ZONE", color = Lime, fontSize = 12.sp)
                IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename workout") }
            }
            Text(workout.name, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (workout.isTemplate) "Saved automatically · ${workout.unit}" else if (readOnly) "${count(workout.completedSetsFor(participantId), "set")} logged${personName?.let { " by $it" }.orEmpty()} · ${workout.volumeFor(participantId).toInt()} ${workout.unit} total volume" else "${count(workout.completedSetsFor(participantId), "set")} logged${personName?.let { " by $it" }.orEmpty()} · autosaves on this phone", color = Muted)
            if (!readOnly) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { ordering = true }, enabled = workout.exercises.size > 1) { Icon(Icons.Rounded.SwapVert, null); Text("Reorder") }
                if (!workout.isTemplate) TextButton(onClick = { managingPartners = true }) { Icon(Icons.Rounded.People, null); Text("Partners") }
            }
            if (!readOnly && !workout.unitLocked) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weight unit", color = Muted, fontSize = 13.sp)
                listOf("lb", "kg").forEach { value -> FilterChip(workout.unit == value, { vm.edit(workout.id) { it.copy(unit = value) } }, label = { Text(value) },
                    modifier = Modifier.semantics { contentDescription = "Use $value for this workout" }) }
            }
            if (workout.partners.isNotEmpty()) {
                Text("Training with ${workout.partners.joinToString { it.name }}", color = Lavender)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(athlete.isEmpty(), { athlete = "" }, label = { Text("You") })
                    tracked.forEach { p -> FilterChip(athlete == p.id, { athlete = p.id }, label = { Text(p.name) }) }
                }
                if (athleteProfile != null && (athleteProfile.goal.isNotBlank() || athleteProfile.notes.isNotBlank())) Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (athleteProfile.goal.isNotBlank()) Text("Goal · ${athleteProfile.goal}", color = Lime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (athleteProfile.notes.isNotBlank()) Text(athleteProfile.notes, color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        if (workout.exercises.isEmpty()) item {
            EmptyCard("Make it your session", "Pick an exercise or add your own. Equipment and brand are optional.", "Add first exercise", Icons.Rounded.Add) { adding = true }
        }
        items(workout.exercises, key = { it.id }) { entry ->
            if (!entry.includes(participantId)) SkippedExerciseRow(entry, personName, readOnly) { vm.includeExercise(workout.id, entry.id, participantId) }
            else {
                val previous = remember(history, workout.id, entry.exercise.id, entry.equipmentKey(), entry.exercise.mode, participantId) { previousSession(history, workout, entry, participantId) }
                val earlier = remember(history, workout.id, entry.exercise.id, entry.equipmentKey(), entry.exercise.mode, participantId) {
                    completedHistory(history, participantId).filter { it.workout.id != workout.id && it.workout.startedAt < workout.startedAt && it.entry.exercise.id == entry.exercise.id && it.entry.equipmentKey() == entry.equipmentKey() && it.entry.exercise.mode == entry.exercise.mode }
                }
                ExerciseCard(entry, workout, participantId, previous, earlier, equipment,
                    update = { transform -> vm.edit(workout.id) { w -> w.copy(exercises = w.exercises.map { if (it.id == entry.id) transform(it) else it }) } },
                    details = { exercise, profile -> vm.editDetails(workout.id, entry.id, exercise, profile) },
                    remove = { vm.edit(workout.id) { w -> w.copy(exercises = w.exercises.filterNot { it.id == entry.id }) } },
                    skip = if (tracked.isEmpty()) null else ({ vm.skipExercise(workout.id, entry.id, participantId) }), personName = personName)
            }
        }
        if (!readOnly) item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Rounded.Add, null); Text(personName?.let { "Add exercise for $it" } ?: "Add exercise") }
            if (tracked.isNotEmpty()) Text(if (personName == null) "Adds to everyone tracking sets. Skip it per person on the card." else "Adds only to $personName’s workout.", color = Muted, fontSize = 12.sp)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (workout.isTemplate) Button(onClick = { vm.opened.value = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Done editing") }
                else {
                    if (!readOnly) Button(onClick = { finishing = true }, enabled = workout.allCompletedSets > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Rounded.Check, null); Text("Finish workout") }
                    OutlinedButton(onClick = { saving = true }, enabled = workout.exercises.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save as a routine") }
                    if (readOnly) Button(onClick = { vm.start(workout) }, modifier = Modifier.fillMaxWidth()) { Text("Repeat workout") }
                }
                TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text(if (workout.isTemplate) "Delete routine" else if (readOnly) "Delete session" else "Discard workout", color = Coral) }
            }
        }
    }
    if (adding) ExercisePicker(library, equipment, history, { adding = false }) { exercise, custom, profile -> vm.addExercise(workout.id, exercise, custom, profile, partnerOnly = participantId); adding = false }
    if (renaming) NameDialog("Name your workout", workout.name, { renaming = false }) { name -> vm.edit(workout.id) { it.copy(name = name) }; renaming = false }
    if (saving) NameDialog("Save as a routine", workout.name, { saving = false }) { name -> vm.saveRoutine(workout, name); saving = false }
    if (finishing) ConfirmDialog("Nice work. Wrap it up?", "${count(workout.completedSets, "set")} for you${if (workout.partners.any { it.trackSets }) " and ${count(workout.allCompletedSets - workout.completedSets, "partner set")}" else ""} will be saved. Unchecked sets remain for reference.", "Finish workout", { finishing = false }) { vm.finish(workout.id); finishing = false }
    if (deleting) ConfirmDialog("Delete ${if (workout.isTemplate) "routine" else "workout"}?", "This removes this record from your phone. Other sessions and routines stay saved.", "Delete", { deleting = false }) { vm.delete(workout.id); deleting = false }
    if (managingPartners) WorkoutPartnersDialog(workout, partners, vm) { managingPartners = false }
    if (ordering) ReorderExercises(workout.exercises, { ordering = false }) { ids ->
        vm.edit(workout.id) { w -> w.copy(exercises = ids.mapNotNull { id -> w.exercises.find { it.id == id } } + w.exercises.filter { it.id !in ids }) }
    }
}

@Composable
private fun ExerciseCard(entry: WorkoutExercise, workout: Workout, participantId: String?, previous: List<LoggedSet>, earlier: List<LoggedSet>, equipment: List<Equipment>,
                         update: ((WorkoutExercise) -> WorkoutExercise) -> Unit, details: (Exercise, Equipment?) -> Unit, remove: () -> Unit,
                         skip: (() -> Unit)? = null, personName: String? = null) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    val who = personName ?: "you"
    var showPrevious by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val readOnly = workout.finishedAt != null
    val sets = entry.sets.filter { it.participantId == participantId }
    val groups = sets.map { it.groupId }.distinct()
    Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.exercise.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(entry.equipmentProfile()?.label ?: "Unspecified equipment", color = Lavender, fontSize = 13.sp)
                    MuscleTags(entry.exercise)
                    if (entry.exercise.mode != ExerciseMode.BILATERAL) Text(entry.exercise.mode.displayName(), color = Coral, fontSize = 12.sp)
                }
                IconButton(onClick = { editing = true }) {
                    Icon(if (readOnly) Icons.Rounded.Info else Icons.Rounded.Tune, "${if (readOnly) "View" else "Edit"} ${entry.exercise.name} details")
                }
            }
            if (!readOnly && !workout.isTemplate) {
                if (previous.isEmpty()) Text("No previous session on this equipment for this person.", color = Muted, fontSize = 12.sp)
                else {
                    val last = previous.first().workout
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Last time · ${dateLabel(last.startedAt)}", color = Lime, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showHistory = true }) { Text("History") }
                        }
                        Text(previous.take(2).joinToString("  /  ") { log ->
                            val side = if (log.set.side == Side.BOTH) "" else "${log.set.side.name.first()} "
                            "$side${log.set.weight} ${last.unit} × ${log.set.reps}"
                        }, color = Muted, fontSize = 13.sp)
                        if (showPrevious) previous.drop(2).forEach { log -> Text("${log.set.side.name.lowercase()} · ${log.set.weight} ${last.unit} × ${log.set.reps}", color = Muted, fontSize = 13.sp) }
                        if (previous.size > 2) TextButton(onClick = { showPrevious = !showPrevious }) { Text(if (showPrevious) "Show less" else "See all previous sets") }
                    }
                }
            }
            if (!readOnly) Text(if (workout.isTemplate) "Optional targets prefill your next session." else if (entry.exercise.mode != ExerciseMode.BILATERAL) "Load per side · 0 for bodyweight" else "Weight as logged · 0 for bodyweight", color = Muted, fontSize = 12.sp)
            sets.forEach { set ->
                key(set.id) {
                    SetRow(groups.indexOf(set.groupId) + 1, set, workout.unit, workout.isTemplate, readOnly,
                        onChange = { transform -> update { e -> e.copy(sets = e.sets.map { if (it.id == set.id) transform(it) else it }) } },
                        onDelete = { update { e -> e.copy(sets = e.sets.filterNot { it.id == set.id }) } })
                }
            }
            if (!readOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { update { e -> e.copy(sets = e.sets + newSetGroup(e.exercise.mode, participantId, e.sets)) } }) { Icon(Icons.Rounded.Add, null); Text(if (entry.exercise.mode == ExerciseMode.BILATERAL) "Add set" else "Add L / R set") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (skip != null && !workout.isTemplate) TextButton(onClick = skip,
                        modifier = Modifier.semantics { contentDescription = "Skip ${entry.exercise.name} for $who" }) { Text("Skip for $who") }
                    IconButton(onClick = { removing = true }) { Icon(Icons.Rounded.DeleteOutline, "Remove ${entry.exercise.name}", tint = Muted) }
                }
            }
        }
    }
    if (editing && readOnly) AlertDialog(onDismissRequest = { editing = false }, title = { Text(entry.exercise.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.equipmentProfile()?.label ?: "Unspecified equipment")
            Text(entry.exercise.mode.displayName())
            Text("Primary: ${entry.exercise.primaryTargets().joinToString().ifBlank { "Not specified" }}")
            Text("Secondary: ${entry.exercise.secondaryMuscles.joinToString().ifBlank { "Not specified" }}")
            if (entry.exercise.target.isNotBlank()) Text(entry.exercise.target)
        }
    }, confirmButton = { TextButton(onClick = { editing = false }) { Text("Done") } })
    if (editing && !readOnly) ExerciseEditor(entry.exercise, entry.equipmentProfile(), equipment, "Exercise details",
        modeLocked = entry.sets.any { it.complete || it.weight.isNotBlank() || it.reps.isNotBlank() }, equipmentLocked = entry.sets.any { it.complete }, dismiss = { editing = false }) { exercise, profile -> details(exercise, profile); editing = false }
    if (removing) ConfirmDialog("Remove ${entry.exercise.name}?", "This removes its sets for you and all partners from this workout.", "Remove", { removing = false }) { remove(); removing = false }
    if (showHistory) ExerciseHistoryDialog(earlier) { showHistory = false }
}

@Composable
private fun SkippedExerciseRow(entry: WorkoutExercise, personName: String?, readOnly: Boolean, include: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(entry.exercise.name, fontWeight = FontWeight.Bold, color = Muted)
                Text("Not in ${personName?.let { "$it’s" } ?: "your"} workout", color = Muted, fontSize = 12.sp)
            }
            if (!readOnly) TextButton(onClick = include, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add ${entry.exercise.name} for ${personName ?: "you"}" }) {
                Icon(Icons.Rounded.Add, null); Text("Add for ${personName ?: "you"}")
            }
        }
    }
}

@Composable
private fun SetRow(number: Int, set: SetEntry, unit: String, template: Boolean, readOnly: Boolean, onChange: ((SetEntry) -> SetEntry) -> Unit, onDelete: () -> Unit) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var weight by rememberSaveable(set.id) { mutableStateOf(set.weight) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.reps) }
    val suffix = if (set.side == Side.BOTH) "" else " ${set.side.name.lowercase().replaceFirstChar { it.uppercase() }}"
    Column {
        if (suffix.isNotEmpty()) Text("Set $number ·$suffix", color = if (set.side == Side.LEFT) Lavender else Coral, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (set.side == Side.BOTH) Text("$number", color = if (set.complete) Lime else Muted, modifier = Modifier.width(20.dp))
            OutlinedTextField(value = weight, onValueChange = { raw -> val value = raw.replace(',', '.'); weight = value; onChange { it.copy(weight = value, complete = false) } },
                label = { Text(unit) }, singleLine = true, readOnly = readOnly, isError = weight.isNotBlank() && weight.toWeightOrNull() == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = reps, onValueChange = { value -> reps = value; onChange { it.copy(reps = value, complete = false) } },
                label = { Text("reps") }, singleLine = true, readOnly = readOnly, isError = reps.isNotBlank() && reps.toIntOrNull()?.let { it in 1..9999 } != true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            if (!template) Checkbox(checked = set.complete, enabled = !readOnly && (set.complete || set.valid()),
                onCheckedChange = { checked -> onChange { it.copy(complete = checked && it.valid()) } }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Complete set $number$suffix" })
            if (!readOnly) IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Close, "Delete set $number$suffix", tint = Muted) }
        }
        if ((weight.isNotBlank() && weight.toWeightOrNull() == null) || (reps.isNotBlank() && reps.toIntOrNull()?.let { it in 1..9999 } != true)) Text("Use weight 0–99,999 and whole reps 1–9,999.", color = Coral, fontSize = 12.sp)
    }
    if (deleting) ConfirmDialog("Remove set $number$suffix?", "Only this entry is removed. Other sides and people stay saved.", "Remove", { deleting = false }) { onDelete(); deleting = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePicker(library: List<Exercise>, equipment: List<Equipment>, history: List<Workout>, dismiss: () -> Unit, select: (Exercise, Boolean, Equipment?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf(false) }
    var muscleFilter by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ink) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding()) {
            Text("Find your next move", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(query, { query = it }, label = { Text("Search exercises or muscles") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), singleLine = true)
            TextButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text("Create custom exercise") }
            Text("Choose an exercise, then the equipment you’re using today.", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("") + muscleFilters(library)).forEach { muscle ->
                    val tint = if (muscle.isEmpty()) Lime else muscleColor(muscle)
                    FilterChip(selected = muscleFilter.equals(muscle, true), onClick = { muscleFilter = muscle },
                        label = { Text(muscle.ifEmpty { "All muscles" }) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Filter muscles: ${muscle.ifEmpty { "All" }}" },
                        colors = FilterChipDefaults.filterChipColors(labelColor = tint, selectedContainerColor = tint, selectedLabelColor = Ink))
                }
            }
            Text("Includes primary and secondary muscles", color = Muted, fontSize = 12.sp)
            val matches = library.filter { it.matchesMuscles(query, muscleFilter) }
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (matches.isEmpty()) item { Text("No matches. Try All muscles or clear your search.") }
                items(matches, key = { it.id }) { exercise ->
                    Surface(onClick = { selectedId = exercise.id }, color = Ink) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, fontWeight = FontWeight.Bold)
                                MuscleTags(exercise)
                            }
                            Icon(Icons.Rounded.AddCircleOutline, null, tint = Lime)
                        }
                    }
                    HorizontalDivider(color = Panel)
                }
            }
        }
    }
    library.find { it.id == selectedId }?.let { exercise ->
        val usedIds = history.flatMap { it.exercises }.filter { it.exercise.id == exercise.id }
            .mapNotNull { it.equipmentKey() }.toSet()
        EquipmentPicker(equipment, dismiss = { selectedId = null }, exerciseName = exercise.name, usedIds = usedIds) {
            select(exercise, false, it)
        }
    }
    if (custom) ExerciseEditor(Exercise(name = query), null, equipment, "Create exercise", dismiss = { custom = false }) { e, profile -> select(e, true, profile) }
}

fun dateLabel(time: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(time))
fun numberLabel(value: Double): String = String.format(Locale.getDefault(), "%,.1f", value)
