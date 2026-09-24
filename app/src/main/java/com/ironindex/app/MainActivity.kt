package com.ironindex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Ink = Color(0xFF14171C)
internal val Panel = Color(0xFF22262E)
internal val Lime = Color(0xFFD5FA60)
internal val Coral = Color(0xFFFFAF99)
internal val Lavender = Color(0xFFC7BAFF)
internal val Muted = Color(0xFFBBC1CD)
internal val IronIndexColors = darkColorScheme(
    primary = Lime, onPrimary = Ink, secondary = Lavender, onSecondary = Ink,
    tertiary = Coral, onTertiary = Ink, background = Ink, surface = Ink,
    surfaceVariant = Panel, onSurface = Color(0xFFF5F6F8), onSurfaceVariant = Muted,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { MaterialTheme(colorScheme = IronIndexColors) { IronIndexApp() } }
    }
}

@Composable
fun IronIndexApp(vm: IronIndexViewModel = viewModel()) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val opened by vm.opened.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val equipment by vm.equipment.collectAsStateWithLifecycle()
    val partners by vm.partners.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val pendingStart by vm.pendingStart.collectAsStateWithLifecycle()
    var manager by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var newRoutine by rememberSaveable { mutableStateOf(false) }
    var progressPerson by rememberSaveable { mutableStateOf("") }
    // Partner profile to return to when Progress was opened from it.
    var progressReturn by rememberSaveable { mutableStateOf<String?>(null) }
    val current = workouts.find { it.id == opened }
    val profileId = manager?.removePrefix("partner/")?.takeIf { manager?.startsWith("partner/") == true }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.error.value = null } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); vm.notice.value = null } }
    BackHandler(opened != null || manager != null || progressReturn != null) {
        when {
            opened != null -> vm.opened.value = null
            profileId != null -> manager = "partners"
            manager != null -> manager = null
            else -> { manager = "partner/$progressReturn"; progressReturn = null }
        }
    }
    LaunchedEffect(tab, manager) { if (tab != 3 || manager != null) progressReturn = null }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (current == null && manager == null) NavigationBar(containerColor = Ink) {
                listOf("Train" to Icons.Rounded.FitnessCenter, "Routines" to Icons.Rounded.Dashboard,
                    "History" to Icons.Rounded.History, "Progress" to Icons.Rounded.Insights).forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Icon(icon, null) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Lime, selectedIconColor = Ink))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val profile = partners.find { it.id == profileId }
            if (current != null) WorkoutScreen(current, exercises, equipment, partners, history, vm)
            else if (manager == "partners" || (profileId != null && profile == null)) PartnerManager(partners, workouts, vm::savePartner, { manager = "partner/$it" }) { manager = null }
            else if (profile != null) PartnerProfile(profile, workouts, unit, vm::savePartner,
                openSession = { vm.openSession(it, profile.id) },
                openProgress = { progressPerson = profile.id; progressReturn = profile.id; manager = null; tab = 3 }) { manager = "partners" }
            else if (manager == "backup") BackupScreen(workouts, partners, vm) { manager = null }
            else if (manager == "equipment") EquipmentManager(equipment, workouts, { vm.saveEquipment(it) }, vm::updateEquipment) { manager = null }
            else if (tab == 3) AnalyticsScreen(workouts, partners, unit, progressPerson) { progressPerson = it }
            else LazyColumn(
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = Lime, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Rounded.FitnessCenter, null, tint = Ink, modifier = Modifier.padding(10.dp))
                            }
                            Text("IronIndex", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                        }
                        Text("LET’S MOVE", fontSize = 11.sp, color = Muted, letterSpacing = 2.sp)
                    }
                }
                when (tab) {
                    0 -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { manager = "partners" }, modifier = Modifier.weight(1f)) { Text("Partners") }
                                OutlinedButton(onClick = { manager = "equipment" }, modifier = Modifier.weight(1f)) { Text("Equipment") }
                                OutlinedButton(onClick = { manager = "backup" }, modifier = Modifier.weight(1f)) { Text("Backup") }
                            }
                        }
                        item {
                            val backedUpUntil by vm.backedUpUntil.collectAsStateWithLifecycle()
                            // Moved or edited sessions count from their latest change, not their (possibly earlier) date.
                            val unsaved = history.count { maxOf(it.finishedAt ?: it.startedAt, it.editedAt ?: 0L) > backedUpUntil }
                            val stale = if (backedUpUntil > 0) System.currentTimeMillis() - backedUpUntil > 14L * 24 * 60 * 60 * 1000 else unsaved >= 3
                            if (unsaved > 0 && stale) Surface(onClick = { manager = "backup" }, color = Panel, shape = RoundedCornerShape(20.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Rounded.CloudUpload, null, tint = Coral)
                                    Column(Modifier.weight(1f)) {
                                        Text("Back up your training", fontWeight = FontWeight.Bold)
                                        Text("${count(unsaved, "session")} not in a backup yet. Export takes a few seconds.", color = Muted, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()).uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                                Text("A little stronger.\nEvery session.", fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                            }
                        }
                        item {
                            // An empty draft isn't worth resuming; Start reuses it anyway.
                            val active = workouts.firstOrNull { !it.isTemplate && it.finishedAt == null && it.exercises.isNotEmpty() }
                            Surface(color = Lime, shape = RoundedCornerShape(28.dp)) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (active == null) "YOUR TIME. YOUR PACE." else "PICK UP WHERE YOU LEFT OFF", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        Icon(Icons.Rounded.Bolt, null, tint = Ink)
                                    }
                                    Text(active?.name ?: "Show up.\nMake it count.", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
                                    Text(active?.let { "${count(it.allCompletedSets, "set")} logged · saved on this phone" } ?: "No plan needed. Build your workout as you go.", color = Ink)
                                    Button(onClick = { vm.start() }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Lime)) {
                                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp))
                                        Text(if (active == null) "Start a workout" else "Resume workout", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        item {
                            val finished = workouts.filter { !it.isTemplate && it.finishedAt != null }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Stat("${finished.size}", "sessions finished", Lavender, Modifier.weight(1f))
                                Stat("${finished.sumOf { it.completedSets }}", "sets in the bank", Coral, Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Your next session", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { tab = 1 }) { Text("Routines") }
                            }
                        }
                        val routines = workouts.filter { it.isTemplate }
                        if (routines.isEmpty()) item {
                            EmptyCard("Build your go-to workout", "Save a routine now, or turn any live workout into one.", "Create a routine", Icons.Rounded.Add) { newRoutine = true }
                        } else items(routines.take(3), key = { it.id }) { routine ->
                            WorkoutCard(routine, "Start routine", { vm.start(routine) }) { vm.opened.value = routine.id }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("New freestyle workouts", color = Muted, modifier = Modifier.weight(1f))
                                listOf("lb", "kg").forEach { value ->
                                    FilterChip(selected = unit == value, onClick = { vm.setUnit(value) }, label = { Text(value) })
                                }
                            }
                            Text("Routines keep their saved weight unit.", color = Muted, fontSize = 12.sp)
                        }
                    }
                    1 -> {
                        item { PageHeading("Your playbook.", "Less planning. More lifting.") }
                        item { Button(onClick = { newRoutine = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Create a routine")
                        } }
                        val routines = workouts.filter { it.isTemplate }
                        if (routines.isEmpty()) item { Text("Your saved routines will live here. Add exercises and target sets, then start whenever you’re ready.", color = Muted) }
                        items(routines, key = { it.id }) { routine ->
                            WorkoutCard(routine, "Start routine", { vm.start(routine) }) { vm.opened.value = routine.id }
                        }
                    }
                    2 -> {
                        item { PageHeading("Look at you go.", "Every set is a step forward.") }
                        val history = workouts.filter { !it.isTemplate && it.finishedAt != null }
                        if (history.isEmpty()) item {
                            EmptyCard("Your story starts here", "Finish a workout to see your exercises, sets, and weights here.", "Start a workout", Icons.Rounded.PlayArrow) { vm.start() }
                        }
                        items(history, key = { it.id }) { workout ->
                            WorkoutCard(workout, "View session", { vm.opened.value = workout.id }) { vm.opened.value = workout.id }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
    pendingStart?.let { source ->
        val draft = workouts.firstOrNull { !it.isTemplate && it.finishedAt == null }
        AlertDialog(onDismissRequest = { vm.pendingStart.value = null }, title = { Text("Workout in progress") }, text = {
            Text("“${draft?.name ?: "Your workout"}” has ${count(draft?.exercises?.size ?: 0, "exercise")} and ${count(draft?.allCompletedSets ?: 0, "set")} logged. Resume it, or discard it and start “${source.name}”?")
        }, confirmButton = { TextButton(onClick = { vm.pendingStart.value = null; draft?.let { vm.opened.value = it.id } }) { Text("Resume current") } },
            dismissButton = { TextButton(onClick = { vm.start(source, replaceDraft = true) }) { Text("Discard and start", color = Coral) } })
    }
    if (newRoutine) NameDialog("Create a routine", "", { newRoutine = false }) { name -> vm.createTemplate(name); newRoutine = false }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        Text(subtitle, color = Muted)
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Surface(modifier, color = color, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text(label, color = Ink, fontSize = 12.sp)
        }
    }
}

@Composable
fun EmptyCard(title: String, body: String, action: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(body, color = Muted)
            TextButton(onClick = onClick) { Icon(icon, null); Spacer(Modifier.width(8.dp)); Text(action) }
        }
    }
}

@Composable
private fun WorkoutCard(workout: Workout, action: String, start: () -> Unit, open: () -> Unit) {
    Surface(onClick = open, color = Panel, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (workout.isTemplate) "SAVED ROUTINE" else SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(workout.startedAt)), color = Lavender, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(workout.name, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            val partnerSets = workout.allCompletedSets - workout.completedSets
            Text("${count(workout.exercises.size, "exercise")} · ${count(if (workout.isTemplate) workout.exercises.sumOf { it.sets.size } else workout.completedSets, "set")}${if (!workout.isTemplate && partnerSets > 0) " + $partnerSets partner" else ""} · ${workout.unit}", color = Muted)
            if (workout.exercises.isNotEmpty()) Text(workout.exercises.joinToString("  /  ") { it.exercise.name }, color = Muted, fontSize = 13.sp)
            TextButton(onClick = start) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(action) }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, dismiss: () -> Unit, label: String = "Workout name", save: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        OutlinedTextField(name, { name = it }, label = { Text(label) }, singleLine = true)
    }, confirmButton = { TextButton(onClick = { save(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun ConfirmDialog(title: String, body: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = confirm) { Text(action) } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
