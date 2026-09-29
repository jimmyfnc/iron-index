package com.ironindex.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

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
fun EquipmentManager(equipment: List<Equipment>, workouts: List<Workout>, save: (Equipment) -> Unit, update: (Equipment, Boolean) -> Unit, back: () -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
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
                    e.baseLabel?.let { Text(it.replaceFirstChar { c -> c.uppercase() }, color = Lime, fontSize = 13.sp) }
                    Row {
                        TextButton(onClick = { editingId = e.id }) { Text("Edit") }
                        TextButton(onClick = { update(e.copy(archived = !e.archived), false) }) { Text(if (e.archived) "Restore" else "Archive") }
                    }
                }
            }
        }
        item { Text("To use a different machine, create a new equipment profile. Archiving keeps existing sessions and comparisons intact. Name fixes show everywhere, past sessions included; a new starting weight applies to new sessions unless you say past weights were plates only.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (adding) EquipmentEditor(null, { adding = false }) { save(it); adding = false }
    editingId?.let { id -> equipment.find { it.id == id }?.let { e -> EquipmentEditFlow(e, equipment, { equipmentUses(workouts, it) }, { editingId = null }, update) } }
}

/** Edits a catalog machine, first asking how past weights were typed when the change would reinterpret logged numbers. */
@Composable
fun EquipmentEditFlow(initial: Equipment, catalog: List<Equipment>, uses: (String) -> Int, dismiss: () -> Unit, update: (Equipment, Boolean) -> Unit) {
    // An edit that changes what typed numbers mean, waiting for "how were past weights typed?".
    var pending by remember(initial.id) { mutableStateOf<Equipment?>(null) }
    val edited = pending
    // Checked here so a clashing name keeps the form open with the typing intact.
    val clash = { value: Equipment -> "Another machine already has this type, brand, model and gym.".takeIf { catalog.any { it.id != value.id && it.identity == value.identity } } }
    if (edited == null) EquipmentEditor(initial, dismiss, validate = clash) { value ->
        if (loadMeaningChanged(initial, value) && uses(initial.id) > 0) pending = value else { update(value, false); dismiss() }
    } else {
        val base = edited.baseIn(edited.baseUnit)
        val used = uses(edited.id)
        AlertDialog(onDismissRequest = dismiss, title = { Text("How were past weights typed?") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${count(used, "session or routine")} already ${if (used == 1) "has" else "have"} weights on ${edited.type}.")
                if (edited.loadMode == LoadMode.ADDED && base != null)
                    Text("Plates only: count the ${weightText(base)} ${edited.baseUnit} ${baseName(edited.type)} in them too, so past totals and PRs match from now on.", color = Muted, fontSize = 13.sp)
                else Text("As totals: the numbers you typed were already the full weight.", color = Muted, fontSize = 13.sp)
                Text("Keep totals: past sessions stay exactly as logged; routines and today's workout adjust their numbers to keep the same totals.", color = Muted, fontSize = 13.sp)
            }
        }, confirmButton = { TextButton(onClick = { update(edited, true); dismiss() }) { Text(if (edited.loadMode == LoadMode.ADDED) "Plates only" else "As totals") } },
            dismissButton = { TextButton(onClick = { update(edited, false); dismiss() }) { Text("Keep totals") } })
    }
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
fun EquipmentEditor(initial: Equipment?, dismiss: () -> Unit, validate: (Equipment) -> String? = { null }, save: (Equipment) -> Unit) {
    val key = initial?.id ?: "new"
    var type by rememberSaveable(key) { mutableStateOf(initial?.type.orEmpty()) }
    var brand by rememberSaveable(key) { mutableStateOf(initial?.brand.orEmpty()) }
    var model by rememberSaveable(key) { mutableStateOf(initial?.model.orEmpty()) }
    var location by rememberSaveable(key) { mutableStateOf(initial?.location.orEmpty()) }
    var base by rememberSaveable(key) { mutableStateOf(initial?.baseWeight?.let(::weightText).orEmpty()) }
    var baseUnit by rememberSaveable(key) { mutableStateOf(initial?.baseUnit ?: "lb") }
    // Null until chosen, so the suggestion follows the type (leg press → plates only) until the lifter picks.
    var mode by rememberSaveable(key) { mutableStateOf(initial?.takeIf { it.baseWeight != null }?.loadMode?.name) }
    val effectiveMode = mode?.let(LoadMode::valueOf) ?: suggestedLoadMode(type)
    val baseValue = base.toWeightOrNull()?.takeIf { it > 0 }
    val template = remember(key) { initial ?: Equipment(type = "") }
    val result = template.copy(type = type.trim(), brand = brand.trim(), model = model.trim(), location = location.trim(),
        baseWeight = baseValue, baseUnit = baseUnit, loadMode = if (baseValue == null) LoadMode.TOTAL else effectiveMode)
    val problem = validate(result)
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (initial == null) "Add equipment" else "Edit equipment") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(type, { type = it }, label = { Text("Equipment type *") }, placeholder = { Text("Incline press machine") })
            OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, placeholder = { Text("Hammer Strength") })
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, placeholder = { Text("ISO-Lateral Incline Press") })
            OutlinedTextField(location, { location = it }, label = { Text("Gym / nickname (optional)") })
            problem?.let { Text(it, color = Coral, style = MaterialTheme.typography.bodySmall) }
            if (initial != null) Text("Name changes show everywhere this machine is used, past sessions included.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("Starting weight", fontWeight = FontWeight.Bold)
            Text("The bar, sled or carriage before plates. Leave blank for dumbbells and cables.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(45.0 to "lb", 55.0 to "lb", 35.0 to "lb", 25.0 to "lb", 20.0 to "kg", 15.0 to "kg").forEach { (w, u) ->
                    FilterChip(baseValue == w && baseUnit == u, { base = weightText(w); baseUnit = u }, label = { Text("${weightText(w)} $u") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(base, { base = it.replace(',', '.') }, label = { Text("Starting weight") }, singleLine = true, modifier = Modifier.weight(1f),
                    isError = base.isNotBlank() && base.toWeightOrNull() == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                listOf("lb", "kg").forEach { u -> FilterChip(baseUnit == u, { baseUnit = u }, label = { Text(u) }, modifier = Modifier.semantics { contentDescription = "Starting weight in $u" }) }
            }
            if (baseValue != null) Column(Modifier.selectableGroup()) {
                Text("How do you log it?", fontWeight = FontWeight.Bold)
                listOf(LoadMode.TOTAL to "Total weight · type 225 for a bar with two plates a side",
                    LoadMode.ADDED to "Plates only · type what you load; the ${weightText(baseValue)} $baseUnit ${baseName(type)} is added for totals").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(effectiveMode == value, role = Role.RadioButton) { mode = value.name }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(effectiveMode == value, onClick = null); Spacer(Modifier.width(8.dp)); Text(label)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = type.isNotBlank() && (base.isBlank() || baseValue != null) && problem == null, onClick = { save(result) }) { Text("Save equipment") } },
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
                        e.baseLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Lime) }
                        if (e.id in usedIds) Text("Used with this exercise", style = MaterialTheme.typography.labelSmall)
                    } }
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    if (adding) EquipmentEditor(null, { adding = false }) { pick(it) }
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
                   modeLocked: Boolean = false,
                   /** Checked sets per person on this entry ("you: 2, Alex: 3"); they move with a machine change. Null when none. */
                   loggedSets: String? = null, askOnRename: Boolean = false,
                   /** Edits the chosen machine in the catalog right away (name fixes, starting weight); null hides the Edit button. */
                   editEquipment: ((Equipment, Boolean) -> Unit)? = null, equipmentUses: (String) -> Int = { 0 },
                   dismiss: () -> Unit, save: (Exercise, Equipment?) -> Unit) {
    // "" until chosen, then "rename" (same exercise and history) or "separate" (new exercise, new history).
    var identity by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var primary by rememberSaveable { mutableStateOf(initial.primaryTargets().joinToString(", ")) }
    var secondary by rememberSaveable { mutableStateOf(initial.secondaryMuscles.joinToString(", ")) }
    var target by rememberSaveable { mutableStateOf(initial.target) }
    var mode by rememberSaveable { mutableStateOf(initial.mode.name) }
    var chosen by rememberSaveable(stateSaver = equipmentSaver) { mutableStateOf(initialEquipment) }
    // Follow catalog edits (such as a name fixed with Edit) for the machine on screen.
    val equipment = chosen?.let { c -> catalog.find { it.id == c.id } ?: c }
    var picking by rememberSaveable { mutableStateOf(false) }
    var editingEquipment by rememberSaveable { mutableStateOf(false) }
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
            // This session's own load settings for its machine; the catalog's for a newly picked one.
            (if (equipment?.id == initialEquipment?.id) initialEquipment else equipment)?.baseLabel?.let { Text(it.replaceFirstChar { c -> c.uppercase() }, color = Lime, fontSize = 13.sp) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text("Choose equipment") }
                // Only machines already in the catalog; a brand-new one is saved with the exercise.
                if (editEquipment != null && equipment != null && catalog.any { it.id == equipment.id }) OutlinedButton(onClick = { editingEquipment = true },
                    modifier = Modifier.semantics { contentDescription = "Edit ${equipment.label}" }) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(4.dp)); Text("Edit") }
            }
            if (loggedSets != null) Text(if (equipment?.id != initialEquipment?.id) "Logged sets ($loggedSets) will move to ${equipment?.label ?: "unspecified equipment"} with weights as typed."
                else "Picked the wrong machine? Choose the right one and the logged sets ($loggedSets) move with it. Switched machines partway? Add another entry instead.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
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
    if (picking) EquipmentPicker(catalog, { picking = false }) { chosen = it; picking = false }
    if (editingEquipment && editEquipment != null && equipment != null) EquipmentEditFlow(equipment, catalog, equipmentUses, { editingEquipment = false }, editEquipment)
}

/** Keeps a picked (possibly not yet saved) machine across rotation and process death. */
private val equipmentSaver = androidx.compose.runtime.saveable.Saver<Equipment?, String>(
    save = { value -> value?.let { storageJson.encodeToString(Equipment.serializer(), it) } ?: "" },
    restore = { text -> text.takeIf { it.isNotEmpty() }?.let { runCatching { storageJson.decodeFromString(Equipment.serializer(), it) }.getOrNull() } },
)

fun ExerciseMode.displayName(): String = when (this) { ExerciseMode.BILATERAL -> "Both sides together"; ExerciseMode.SINGLE_ARM -> "Single-arm · Left / Right"; ExerciseMode.SINGLE_LEG -> "Single-leg · Left / Right" }

@Composable
fun BackupScreen(workouts: List<Workout>, partners: List<Partner>, vm: IronIndexViewModel, back: () -> Unit) {
    val lastExport by vm.lastExport.collectAsStateWithLifecycle()
    val preview by vm.importPreview.collectAsStateWithLifecycle()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportTo) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readImport) }
    val sessions = workouts.count { !it.isTemplate && it.finishedAt != null }
    val routines = workouts.count { it.isTemplate }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ManagerHeading("Backup & restore", "Your training lives only on this phone. Export a backup file and keep it somewhere safe: Drive, email or a computer.", back) }
        item {
            Surface(color = Panel, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${count(sessions, "session")} · ${count(routines, "routine")} · ${count(partners.size, "partner")}", fontWeight = FontWeight.Bold)
                    Text(lastExport?.let { "Last exported ${dateLabel(it)}" } ?: "Never exported", color = if (lastExport == null) Coral else Muted)
                }
            }
        }
        item {
            Button(onClick = { exporter.launch(backupFileName()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Icon(Icons.Rounded.Upload, null); Spacer(Modifier.width(8.dp)); Text("Export backup")
            }
        }
        item {
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp)); Text("Import backup")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Importing adds what's missing and never deletes anything. Importing the same file twice changes nothing. A workout edited more recently in the file replaces the older copy.", color = Muted, fontSize = 13.sp)
                Text("Android also backs up IronIndex to your Google account and moves it to a new phone when device backup is on.", color = Muted, fontSize = 13.sp)
            }
        }
    }
    preview?.let { (backup, plan) ->
        AlertDialog(onDismissRequest = { vm.importPreview.value = null }, title = { Text(if (plan.isEmpty) "Already up to date" else "Import this backup?") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exported ${dateLabel(backup.exportedAt)}${backup.appVersion.takeIf { it.isNotBlank() }?.let { " from IronIndex $it" }.orEmpty()}.", color = Muted)
                Text(plan.summary())
                if (plan.skippedDrafts > 0) Text("The backup's unfinished workout is skipped because this phone already has workouts.", color = Coral, fontSize = 13.sp)
                if (plan.phoneHadWorkouts && plan.newSessions + plan.newRoutines > 0) Text("Sessions or routines you deleted on this phone after this backup was made will come back.", color = Muted, fontSize = 13.sp)
            }
        }, confirmButton = {
            if (plan.isEmpty) TextButton(onClick = { vm.importPreview.value = null }) { Text("Done") }
            else TextButton(onClick = vm::confirmImport) { Text("Import") }
        }, dismissButton = { if (!plan.isEmpty) TextButton(onClick = { vm.importPreview.value = null }) { Text("Cancel") } })
    }
}
