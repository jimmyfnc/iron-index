package com.ironindex.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IronIndexViewModel @JvmOverloads constructor(application: Application, private val store: WorkoutStore = WorkoutStore(application)) : AndroidViewModel(application) {
    val workouts = store.workouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val exercises = store.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), starterExercises)
    val equipment = store.equipment.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val partners = store.partners.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val preferences = application.getSharedPreferences("ironindex", 0)
    val unit = MutableStateFlow(preferences.getString("unit", "lb") ?: "lb")
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val opened = MutableStateFlow<String?>(null)
    /** Person chip to select when the next workout opens; null means You. */
    val openedParticipant = MutableStateFlow<String?>(null)
    fun openSession(workoutId: String, participantId: String?) { openedParticipant.value = participantId; opened.value = workoutId }
    private fun run(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error.value = if (e is IllegalArgumentException) e.message else "Couldn’t save your change. Please try again."
        }
    } }
    fun start(source: Workout? = null) = run { opened.value = store.start(source, unit.value) }
    fun setUnit(value: String) { unit.value = value; preferences.edit().putString("unit", value).apply() }
    fun createTemplate(name: String) = run {
        val workout = Workout(name = name.trim(), isTemplate = true, unit = unit.value)
        store.save(workout); opened.value = workout.id
    }
    fun edit(id: String, transform: (Workout) -> Workout) = run { store.edit(id, transform) }
    fun addExercise(id: String, exercise: Exercise, custom: Boolean, equipment: Equipment? = legacyEquipment(exercise), partnerOnly: String? = null) = run {
        val savedEquipment = equipment?.let { store.saveEquipment(it) }
        val normalized = exercise.copy(equipment = "", brand = "", defaultEquipmentId = savedEquipment?.id)
        if (custom) store.saveExercise(normalized)
        store.edit(id) { w -> w.copy(exercises = w.exercises + w.newEntry(normalized, savedEquipment, partnerOnly)) }
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
    fun editDetails(workoutId: String, entryId: String, exercise: Exercise, equipment: Equipment?) = run {
        val saved = equipment?.let { store.saveEquipment(it) }
        val normalized = exercise.copy(equipment = "", brand = "", defaultEquipmentId = saved?.id)
        store.edit(workoutId) { w -> w.copy(exercises = w.exercises.map { e ->
            if (e.id != entryId) e else {
                val changedMode = e.exercise.mode != exercise.mode
                require(!changedMode || e.sets.none { it.complete || it.reps.isNotBlank() || it.weight.isNotBlank() }) {
                    "Add a new exercise entry to change sides after entering sets."
                }
                require(e.equipmentKey() == saved?.id || e.sets.none { it.complete }) {
                    "Add another exercise entry to use different equipment after logging sets."
                }
                e.copy(exercise = normalized, equipmentId = saved?.id, equipmentSnapshot = saved,
                    sets = if (changedMode) w.startingSets(e, exercise.mode) else e.sets)
            }
        }) }
        store.saveExercise(normalized)
    }
    fun move(workoutId: String, entryId: String, beforeId: String?) = edit(workoutId) { it.moveExercise(entryId, beforeId) }
    override fun onCleared() { super.onCleared(); store.close() }
}
