package com.ironindex.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChoiceField(label: String, selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(choices.find { it.first == selected }?.second ?: "Choose", modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
            }
        }
    }
}

@Composable
fun PartnerManager(partners: List<Partner>, workouts: List<Workout>, save: (Partner) -> Unit, open: (String) -> Unit, back: () -> Unit) {
    // Saveable id so the editor survives rotation: "" = new partner, otherwise the profile being edited.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val newPartner = rememberSaveable { newId() }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ManagerHeading("Partner Manager", "Local profiles. Each person keeps their own sets and history.", back) }
        item { Button(onClick = { editingId = "" }, modifier = Modifier.fillMaxWidth()) { Text("Add partner") } }
        item { FilterChip(showArchived, { showArchived = !showArchived }, label = { Text("Show archived") }) }
        if (partners.isEmpty()) item { Text("Add someone you train with. Attach them from inside a workout.") }
        items(partners.filter { showArchived || !it.archived }, key = { it.id }) { p ->
            val summary = remember(workouts, p.id) { personSummary(completedHistory(workouts, p.id)) }
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (p.goal.isNotBlank()) Text("Goal · ${p.goal}", color = Lime)
                    Text(summary.lastSession?.let { "${count(summary.sessions, "session")} · ${count(summary.sets, "set")} · last ${dateLabel(it)}" } ?: "No finished sessions yet", color = Muted, fontSize = 13.sp)
                    if (p.archived) Text("Archived · history retained")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { open(p.id) }) { Text("History & stats") }
                        TextButton(onClick = { editingId = p.id }) { Text("Edit") }
                        TextButton(onClick = { save(p.copy(archived = !p.archived)) }) { Text(if (p.archived) "Restore" else "Archive") }
                    }
                }
            }
        }
    }
    editingId?.let { id -> (if (id.isEmpty()) Partner(id = newPartner, name = "") else partners.find { it.id == id })?.let { p ->
        PartnerEditor(p, { editingId = null }) { save(it); editingId = null }
    } }
}

@Composable
fun PartnerEditor(initial: Partner, dismiss: () -> Unit, save: (Partner) -> Unit) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var goal by rememberSaveable(initial.id) { mutableStateOf(initial.goal) }
    var notes by rememberSaveable(initial.id) { mutableStateOf(initial.notes) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (initial.name.isBlank()) "Add partner" else "Edit partner") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Partner name *") }, singleLine = true)
            OutlinedTextField(goal, { goal = it }, label = { Text("Goal / focus") }, placeholder = { Text("Bench 225, hypertrophy, rehab…") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, placeholder = { Text("Injuries, limits, cues, preferred machines") }, minLines = 3)
            Text("Goal and notes show above the sets while you log for this person.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }, confirmButton = { TextButton(onClick = { save(initial.copy(name = name.trim(), goal = goal.trim(), notes = notes.trim())) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun PartnerProfile(partner: Partner, workouts: List<Workout>, unit: String, save: (Partner) -> Unit, openSession: (String) -> Unit, openProgress: () -> Unit, back: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val logs = remember(workouts, partner.id) { completedHistory(workouts, partner.id) }
    val summary = remember(logs) { personSummary(logs) }
    val records = remember(logs) { personalRecords(logs) }
    val sessions = remember(logs) { logs.groupBy { it.workout.id }.values.sortedByDescending { it.first().workout.startedAt } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ManagerHeading(partner.name, partner.goal.ifBlank { "Training partner" }, back) }
        if (partner.notes.isNotBlank()) item { Surface(color = Panel, shape = MaterialTheme.shapes.large) { Text(partner.notes, Modifier.fillMaxWidth().padding(16.dp)) } }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, null); Text("Edit details") }
                TextButton(onClick = openProgress, enabled = logs.isNotEmpty()) { Icon(Icons.Rounded.Insights, null); Text("Progress charts") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStat("${summary.sessions}", if (summary.sessions == 1) "session" else "sessions", Modifier.weight(1f))
                ProfileStat("${summary.sets}", if (summary.sets == 1) "set" else "sets", Modifier.weight(1f))
                ProfileStat(numberLabel(displayWeight(summary.volumeKg, unit)), "$unit × reps", Modifier.weight(1f))
            }
            summary.lastSession?.let { Text("Last trained ${dateLabel(it)}", color = Muted, modifier = Modifier.padding(top = 8.dp)) }
        }
        if (logs.isEmpty()) item { Text("Finish a workout with ${partner.name}’s sets tracked to build their history here.", color = Lavender) }
        if (records.isNotEmpty()) {
            item { Text("Recent records", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            items(records.take(5), key = { "pr-${it.key}" }) { r ->
                Column {
                    Text("${r.exercise}${if (r.key.side == Side.BOTH) "" else " · ${r.key.side.name.lowercase()}"}", fontWeight = FontWeight.Bold)
                    Text("${numberLabel(displayWeight(r.weightKg, unit))} $unit top weight · ${r.reps} best reps · ${r.equipment}", color = Muted, fontSize = 13.sp)
                }
            }
        }
        if (sessions.isNotEmpty()) item { Text("Sessions", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        items(sessions, key = { it.first().workout.id }) { sets ->
            val w = sets.first().workout
            Surface(onClick = { openSession(w.id) }, color = Panel, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${dateLabel(w.startedAt)} · ${w.name}", fontWeight = FontWeight.Bold)
                    Text("${count(sets.size, "set")} · ${sets.map { it.entry.exercise.name }.distinct().joinToString(" / ")}", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
    if (editing) PartnerEditor(partner, { editing = false }) { save(it); editing = false }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier) {
    Surface(modifier, color = Panel, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = Lime, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun EquipmentManager(equipment: List<Equipment>, save: (Equipment) -> Unit, back: () -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ManagerHeading("Equipment library", "Brand + model + optional gym or nickname. Your workout history remembers the equipment you used.", back) }
        item { Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add equipment") } }
        item { FilterChip(showArchived, { showArchived = !showArchived }, label = { Text("Show archived") }) }
        items(equipment.filter { showArchived || !it.archived }, key = { it.id }) { e ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e.type.ifBlank { "Equipment" }, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(e.brand, e.model, e.location).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Generic equipment" })
                    TextButton(onClick = { save(e.copy(archived = !e.archived)) }) { Text(if (e.archived) "Restore" else "Archive") }
                }
            }
        }
        item { Text("To use a different machine, create a new equipment profile. Archiving keeps existing sessions and comparisons intact.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (adding) EquipmentEditor({ adding = false }) { save(it); adding = false }
}

@Composable
private fun ManagerHeading(title: String, body: String, back: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EquipmentEditor(dismiss: () -> Unit, save: (Equipment) -> Unit) {
    var type by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add equipment") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(type, { type = it }, label = { Text("Equipment type *") }, placeholder = { Text("Incline press machine") })
            OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, placeholder = { Text("Hammer Strength") })
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, placeholder = { Text("ISO-Lateral Incline Press") })
            OutlinedTextField(location, { location = it }, label = { Text("Gym / nickname (optional)") })
        }
    }, confirmButton = { TextButton(onClick = { save(Equipment(type = type.trim(), brand = brand.trim(), model = model.trim(), location = location.trim())) }, enabled = type.isNotBlank()) { Text("Save equipment") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun EquipmentPicker(equipment: List<Equipment>, dismiss: () -> Unit, exerciseName: String? = null, usedIds: Set<String> = emptySet(), pick: (Equipment?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(exerciseName?.let { "Equipment for $it" } ?: "Choose equipment") }, text = {
        Column {
            if (exerciseName != null) Text("One exercise, any equipment. Each equipment choice keeps its own history.")
            OutlinedTextField(query, { query = it }, label = { Text("Search type, brand, model or gym") })
            TextButton(onClick = { adding = true }) { Text("Add new equipment") }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item { TextButton(onClick = { pick(null) }) { Text("Unspecified equipment") } }
                items(equipment.filter { !it.archived && it.label.contains(query, true) }.sortedBy { it.id !in usedIds }, key = { it.id }) { e ->
                    TextButton(onClick = { pick(e) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) {
                        Text(e.label)
                        if (e.id in usedIds) Text("Used with this exercise", style = MaterialTheme.typography.labelSmall)
                    } }
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    if (adding) EquipmentEditor({ adding = false }) { pick(it) }
}

@Composable
fun WorkoutPartnersDialog(workout: Workout, partners: List<Partner>, vm: IronIndexViewModel, dismiss: () -> Unit) {
    var newPartner by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Workout partners") }, text = {
        Column {
            Text("Attach partners, then choose whether to log their sets on this phone.")
            TextButton(onClick = { newPartner = true }) { Text("Create partner") }
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                val profiles = (partners + workout.partners.map { Partner(it.id, it.name) }).distinctBy { it.id }
                items(profiles.filter { !it.archived || workout.partners.any { wp -> wp.id == it.id } }, key = { it.id }) { p ->
                    val attached = workout.partners.find { it.id == p.id }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(attached != null, { if (it) vm.attachPartner(workout.id, p, false) else vm.detachPartner(workout.id, p.id) }, modifier = Modifier.semantics { contentDescription = "Attach ${p.name}" })
                            Text(p.name, Modifier.weight(1f))
                        }
                        if (attached != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Track sets", Modifier.weight(1f))
                            Switch(attached.trackSets, { vm.attachPartner(workout.id, p, it) }, modifier = Modifier.semantics { contentDescription = "Track sets for ${p.name}" })
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
    if (newPartner) NameDialog("Create partner", "", { newPartner = false }, label = "Partner name") { vm.savePartner(Partner(name = it)); newPartner = false }
}

@Composable
fun ExerciseEditor(initial: Exercise, initialEquipment: Equipment?, catalog: List<Equipment>, title: String,
                   modeLocked: Boolean = false, equipmentLocked: Boolean = false, askOnRename: Boolean = false, dismiss: () -> Unit, save: (Exercise, Equipment?) -> Unit) {
    // "" until chosen, then "rename" (same exercise and history) or "separate" (new exercise, new history).
    var identity by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var primary by rememberSaveable { mutableStateOf(initial.primaryTargets().joinToString(", ")) }
    var secondary by rememberSaveable { mutableStateOf(initial.secondaryMuscles.joinToString(", ")) }
    var target by rememberSaveable { mutableStateOf(initial.target) }
    var mode by rememberSaveable { mutableStateOf(initial.mode.name) }
    var equipment by remember { mutableStateOf(initialEquipment) }
    var picking by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Exercise name *") })
            val renaming = askOnRename && initial.renamedTo(name)
            if (renaming) Column(Modifier.selectableGroup()) {
                Text("Is this the same movement?", fontWeight = FontWeight.Bold)
                listOf("rename" to "Same exercise, new name · keeps one history everywhere",
                    "separate" to "Different exercise · starts its own history").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(identity == value, role = Role.RadioButton) { identity = value }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(identity == value, onClick = null); Spacer(Modifier.width(8.dp)); Text(label)
                    }
                }
            }
            Text("Equipment for this entry", fontWeight = FontWeight.Bold)
            Text(equipment?.label ?: "Unspecified equipment")
            OutlinedButton(onClick = { picking = true }, enabled = !equipmentLocked) { Text("Choose equipment") }
            if (equipmentLocked) Text("Equipment is locked after a completed set. Add another entry to switch machines.")
            if (!modeLocked) ChoiceField("How is this performed?", mode, ExerciseMode.entries.map { it.name to it.displayName() }) { mode = it }
            else Text("${initial.mode.displayName()} · add a new entry to change sides after entering values.")
            OutlinedTextField(primary, { primary = it }, label = { Text("Primary muscles (comma separated)") }, placeholder = { Text("Chest, upper chest") })
            OutlinedTextField(secondary, { secondary = it }, label = { Text("Secondary muscles (comma separated)") }, placeholder = { Text("Triceps, front delts") })
            Text("Examples: ${muscleSuggestions.take(12).joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(target, { target = it }, label = { Text("Specific target / isolation notes") }, placeholder = { Text("e.g. Upper chest emphasis") })
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && (!askOnRename || !initial.renamedTo(name) || identity.isNotEmpty()), onClick = {
        fun muscles(text: String) = text.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        val first = muscles(primary)
        val base = if (askOnRename && initial.renamedTo(name) && identity == "separate") initial.copy(id = newId()) else initial
        save(base.copy(name = name.trim(), equipment = "", brand = "", muscleGroup = "", primaryMuscles = first,
            secondaryMuscles = muscles(secondary).filter { value -> first.none { it.equals(value, true) } },
            target = target.trim(), mode = ExerciseMode.valueOf(mode), defaultEquipmentId = equipment?.id), equipment)
    }) { Text("Save exercise") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    if (picking) EquipmentPicker(catalog, { picking = false }) { equipment = it; picking = false }
}

fun ExerciseMode.displayName(): String = when (this) { ExerciseMode.BILATERAL -> "Both sides together"; ExerciseMode.SINGLE_ARM -> "Single-arm · Left / Right"; ExerciseMode.SINGLE_LEG -> "Single-leg · Left / Right" }
