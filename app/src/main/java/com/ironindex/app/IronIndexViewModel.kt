package com.ironindex.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pounds only where they're the everyday unit; everyone else starts in kilograms. */
fun defaultUnit(country: String = java.util.Locale.getDefault().country) = if (country in setOf("US", "LR", "MM")) "lb" else "kg"

@OptIn(kotlinx.coroutines.FlowPreview::class)
class IronIndexViewModel @JvmOverloads constructor(
    application: Application,
    private val store: WorkoutStore = WorkoutStore(application),
    /** Null in tests, so a test run can never write fake sessions into a real Health Connect. */
    private val health: HealthSync? = HealthSync(application),
) : AndroidViewModel(application) {
    val workouts = store.workouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Finished sessions only; emits only when history changes, not on every keystroke in a draft. */
    val history = workouts.map { all -> all.filter { !it.isTemplate && it.finishedAt != null } }.distinctUntilChanged()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val exercises = store.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), starterExercises)
    val equipment = store.equipment.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val partners = store.partners.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val preferences = application.getSharedPreferences("ironindex", 0)
    val unit = MutableStateFlow(preferences.getString("unit", null) ?: defaultUnit())
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)

    /** Sharing finished sessions with Health Connect: off until the lifter turns it on and allows writing. */
    val healthConnect = MutableStateFlow(preferences.getBoolean("healthConnect", false))
    /** Last outcome, shown on the card: "Shared · 12 sessions · 10:25 AM" or why it couldn't share. */
    val healthStatus = MutableStateFlow<String?>(null)
    val healthAvailability: HealthSync.Availability get() = health?.let { runCatching { it.availability }.getOrNull() } ?: HealthSync.Availability.UNAVAILABLE
    val healthPermissions: Set<String> get() = health?.permissions.orEmpty()
    fun healthSettingsIntent() = health?.settingsIntent()
    fun healthInstallIntent() = health?.installIntent()
    suspend fun healthGranted(): Boolean = health?.let { runCatching { it.granted() }.getOrDefault(false) } ?: false
    fun setHealthConnect(on: Boolean) {
        healthConnect.value = on
        preferences.edit().putBoolean("healthConnect", on).apply()
        notice.value = if (on) "Sharing finished sessions with Health Connect." else "Stopped sharing. Sessions already shared stay in Health Connect."
    }
    init {
        // Finishing, editing, moving, deleting or importing a session changes history; mirror it to Health
        // Connect. Debounced so typing while editing a finished session doesn't write on every keystroke.
        val health = health
        if (health != null) viewModelScope.launch {
            combine(history, healthConnect) { sessions, on -> sessions to on }.debounce(1_500).collectLatest { (sessions, on) ->
                if (!on) return@collectLatest
                try {
                    if (!health.granted()) { healthStatus.value = "Allow Health Connect again to keep sharing."; return@collectLatest }
                    val count = health.share(sessions)
                    healthStatus.value = "Shared · ${count(count, "session")} · ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())}"
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    runCatching { android.util.Log.w("IronIndex", "Health Connect sync failed", e) }
                    healthStatus.value = "Couldn’t share with Health Connect. It will try again after your next change."
                }
            }
        }
    }
    val opened = MutableStateFlow<String?>(null)
    /** Person chip to select when the next workout opens; null means You. */
    val openedParticipant = MutableStateFlow<String?>(null)
    fun openSession(workoutId: String, participantId: String?) { openedParticipant.value = participantId; opened.value = workoutId }

    /** The finished session being edited, exactly as it was before editing began (used by Discard). */
    val editingOriginal = MutableStateFlow<Workout?>(null)
    fun beginEdit(workout: Workout) {
        if (workout.finishedAt == null || workout.isTemplate) return
        editingOriginal.value = workout
        // Kept on disk too, so an edit interrupted by the app being killed is still wrapped up safely.
        preferences.edit().putString("editingOriginal", storageJson.encodeToString(Workout.serializer(), workout)).apply()
    }
    private fun clearEdit() { editingOriginal.value = null; preferences.edit().remove("editingOriginal").apply() }
    fun doneEditing() = run { finishEdit() }
    fun discardEdit() = run {
        val original = editingOriginal.value ?: return@run
        // A fresh updatedAt so a backup exported mid-edit can't bring the abandoned changes back.
        store.save(original.copy(updatedAt = System.currentTimeMillis()))
        clearEdit()
        notice.value = "Changes discarded."
    }
    private suspend fun finishEdit() {
        val original = editingOriginal.value ?: return
        var keptOriginal = false
        store.edit(original.id) { edited ->
            when {
                // Never let editing empty a session out of history by accident.
                edited.allCompletedSets == 0 -> { keptOriginal = true; original }
                edited.sameContentAs(original) -> edited
                else -> edited.copy(editedAt = System.currentTimeMillis())
            }
        }
        clearEdit()
        if (keptOriginal) notice.value = "No sets were checked, so the original session was kept."
    }
    init {
        preferences.getString("editingOriginal", null)
            ?.let { runCatching { storageJson.decodeFromString(Workout.serializer(), it) }.getOrNull() }
            ?.let { interrupted -> editingOriginal.value = interrupted; run { finishEdit() } }
        // Leaving the session (Back, another screen) counts as Done.
        viewModelScope.launch { opened.collect { id -> if (editingOriginal.value?.let { it.id != id } == true) run { finishEdit() } } }
    }
    private fun run(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error.value = when (e) {
                is IllegalArgumentException -> e.message
                is java.io.IOException, is SecurityException -> "Couldn’t read or write that file. Try another location."
                else -> "Couldn’t save your change. Please try again."
            }
        }
    } }
    /** Routine or session waiting for the user to resolve an in-progress draft. */
    val pendingStart = MutableStateFlow<Workout?>(null)
    fun start(source: Workout? = null, replaceDraft: Boolean = false) = run {
        if (source != null && !replaceDraft && store.activeDraft()?.exercises?.isNotEmpty() == true) {
            pendingStart.value = source
            return@run
        }
        pendingStart.value = null
        openedParticipant.value = null
        opened.value = store.start(source, unit.value, replaceDraft)
    }
    /** Time of the last successful export, or null if this phone has never exported. */
    val lastExport = MutableStateFlow(preferences.getLong("lastExport", 0L).takeIf { it > 0 })
    /** Sessions finished before this time are in some backup: the last export, or the newest backup imported here. */
    val backedUpUntil = MutableStateFlow(maxOf(preferences.getLong("lastExport", 0L), preferences.getLong("importedBackupAt", 0L)))
    /** A parsed backup and what importing it would change, waiting for the user to confirm. */
    val importPreview = MutableStateFlow<Pair<Backup, ImportPlan>?>(null)
    private val resolver get() = getApplication<Application>().contentResolver
    private val appVersion get() = runCatching {
        getApplication<Application>().let { it.packageManager.getPackageInfo(it.packageName, 0).versionName }
    }.getOrNull().orEmpty()

    fun exportTo(uri: android.net.Uri) = run {
        val backup = store.exportBackup(appVersion)
        val bytes = withContext(Dispatchers.Default) { backup.encode().toByteArray(Charsets.UTF_8) }
        withContext(Dispatchers.IO) {
            try {
                // CreateDocument gives a new empty file; some providers reject "wt", and plain "w" is then safe.
                val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri, "w")
                    ?: throw java.io.IOException("No output stream")
                out.use { it.write(bytes) }
            } catch (e: Exception) {
                runCatching { android.provider.DocumentsContract.deleteDocument(resolver, uri) }
                throw if (e is java.io.IOException) e else java.io.IOException(e)
            }
        }
        lastExport.value = backup.exportedAt
        backedUpUntil.value = maxOf(backedUpUntil.value, backup.exportedAt)
        preferences.edit().putLong("lastExport", backup.exportedAt).apply()
        notice.value = "Backup saved · ${count(backup.workouts.count { !it.isTemplate && it.finishedAt != null }, "session")}"
    }

    fun readImport(uri: android.net.Uri) = run {
        val text = withContext(Dispatchers.IO) {
            val input = resolver.openInputStream(uri) ?: throw java.io.IOException("No input stream")
            input.use { readLimited(it, BACKUP_MAX_BYTES) }
        }
        val backup = withContext(Dispatchers.Default) {
            try { parseBackup(text) } catch (e: OutOfMemoryError) { throw BackupException("This file is too large to be an IronIndex backup.") }
        }
        importPreview.value = backup to store.planImport(backup)
    }

    fun confirmImport() = run {
        val backup = importPreview.value?.first ?: return@run
        importPreview.value = null
        val plan = store.applyImport(backup)
        if (backup.exportedAt > backedUpUntil.value) {
            backedUpUntil.value = backup.exportedAt
            preferences.edit().putLong("importedBackupAt", backup.exportedAt).apply()
        }
        notice.value = if (plan.isEmpty) "Nothing new to import." else "Imported · ${plan.summary()}"
    }

    fun setUnit(value: String) { unit.value = value; preferences.edit().putString("unit", value).apply() }
    fun createTemplate(name: String) = run {
        val workout = Workout(name = name.trim(), isTemplate = true, unit = unit.value)
        store.save(workout); opened.value = workout.id
    }
    fun edit(id: String, transform: (Workout) -> Workout) = run { store.edit(id, transform) }
    fun addExercise(id: String, exercise: Exercise, custom: Boolean, equipment: Equipment? = legacyEquipment(exercise), partnerOnly: String? = null) = run {
        val savedEquipment = equipment?.let { store.ensureEquipment(it) }
        val normalized = exercise.copy(equipment = "", brand = "", defaultEquipmentId = savedEquipment?.id)
        if (custom) store.saveExercise(normalized)
        // Routines keep their own targets; live workouts and finished-session edits start from last time's sets.
        // Read from the store, not the history StateFlow, so a session finished moments ago is included.
        val past = store.workouts.first()
        store.edit(id) { w ->
            val entry = w.newEntry(normalized, savedEquipment, partnerOnly)
            w.copy(exercises = w.exercises + if (w.isTemplate) entry else entry.prefilledFrom(past, w))
        }
    }
    fun skipExercise(workoutId: String, entryId: String, participantId: String?) = edit(workoutId) { it.skipExercise(entryId, participantId) }
    fun includeExercise(workoutId: String, entryId: String, participantId: String?) = edit(workoutId) { it.includeExercise(entryId, participantId) }
    fun saveRoutine(workout: Workout, name: String) = run {
        store.save(workout.freshCopy(true, name))
        notice.value = "Routine saved. Find it in Routines."
    }
    fun finish(id: String) = run {
        store.edit(id) { workout ->
            require(workout.allCompletedSets > 0) { "Log at least one set before finishing." }
            workout.copy(finishedAt = System.currentTimeMillis())
        }
        opened.value = null
    }
    // A deleted session leaves history, and the next Health Connect sync removes its record.
    fun delete(id: String) = run { store.delete(id); if (opened.value == id) opened.value = null }
    fun savePartner(partner: Partner) = run {
        require(partner.name.isNotBlank()) { "Enter a partner name." }
        store.savePartner(partner.copy(name = partner.name.trim(), goal = partner.goal.trim(), notes = partner.notes.trim()))
    }
    fun saveEquipment(equipment: Equipment, onSaved: (Equipment) -> Unit = {}) = run {
        require(equipment.type.isNotBlank()) { "Enter the equipment type." }
        onSaved(store.saveEquipment(equipment))
    }
    fun attachPartner(workoutId: String, partner: Partner, track: Boolean) = edit(workoutId) { it.attachPartner(partner, track) }
    fun detachPartner(workoutId: String, partnerId: String) = edit(workoutId) { it.detachPartner(partnerId) }
    /** Saves an edited catalog machine and carries it into routines, the workout in progress and (if asked) past logs. */
    fun updateEquipment(equipment: Equipment, reinterpretPast: Boolean) = run {
        require(equipment.type.isNotBlank()) { "Enter the equipment type." }
        val saved = store.applyEquipment(equipment, reinterpretPast)
        // Equipment edits stay when a session edit is discarded, so the copy Discard restores takes them too.
        editingOriginal.value?.let { original ->
            val updated = original.withEquipmentEdit(saved, reinterpretPast)
            if (updated != original) {
                editingOriginal.value = updated
                preferences.edit().putString("editingOriginal", storageJson.encodeToString(Workout.serializer(), updated)).apply()
            }
        }
    }
    /**
     * Saves an entry's details. Picking a different machine after logging sets fixes a wrong choice: the sets
     * move to the new machine with their weights as typed, and totals follow the new machine's starting weight.
     */
    fun editDetails(workoutId: String, entryId: String, exercise: Exercise, equipment: Equipment?) = run {
        val entry = workouts.value.find { it.id == workoutId }?.exercises?.find { it.id == entryId }
        // Only a newly picked machine goes through the catalog; the current one is never rewritten from a snapshot.
        val picked = if (equipment != null && equipment.id != entry?.equipmentKey()) store.ensureEquipment(equipment) else equipment
        val normalized = exercise.copy(equipment = "", brand = "", defaultEquipmentId = picked?.id)
        var retotalled = false
        store.edit(workoutId) { w -> w.copy(exercises = w.exercises.map { e ->
            if (e.id != entryId) e else {
                val changedMode = e.exercise.mode != exercise.mode
                require(!changedMode || e.sets.none { it.complete || it.reps.isNotBlank() || it.weight.isNotBlank() }) {
                    "Add a new exercise entry to change sides after entering sets."
                }
                // Unchanged equipment keeps this session's own (current, locked) snapshot.
                val snapshot = if (picked != null && picked.id == e.equipmentKey()) e.equipmentSnapshot ?: picked else picked
                retotalled = snapshot?.id != e.equipmentKey() && e.sets.any { it.weight.isNotBlank() } && loadMeaningChanged(e.equipmentProfile(), snapshot)
                e.copy(exercise = normalized, equipmentId = snapshot?.id, equipmentSnapshot = snapshot,
                    sets = if (changedMode) w.startingSets(e, exercise.mode) else e.sets)
            }
        }) }
        store.saveExercise(normalized)
        if (retotalled) notice.value = "Weights kept as typed. Totals now use this machine’s starting weight."
    }
    /** Records one person's machine setup on an entry; names new to the machine are added to its settings for next time. */
    fun saveSetup(workoutId: String, entryId: String, participantId: String?, values: Map<String, String>, machineId: String?, newNames: List<String>) = run {
        store.edit(workoutId) { w -> w.copy(exercises = w.exercises.map { e -> if (e.id == entryId) e.withSetup(participantId, values) else e }) }
        if (machineId != null && newNames.isNotEmpty()) store.addEquipmentSettings(machineId, newNames)
    }
    fun move(workoutId: String, entryId: String, beforeId: String?) = edit(workoutId) { it.moveExercise(entryId, beforeId) }
    override fun onCleared() { super.onCleared(); store.close() }
}
