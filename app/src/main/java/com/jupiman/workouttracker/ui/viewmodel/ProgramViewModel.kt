package com.jupiman.workouttracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.model.WorkoutTemplateExerciseEditorItem
import com.jupiman.workouttracker.data.repository.ExerciseRepository
import com.jupiman.workouttracker.data.repository.ProgramRepository
import com.jupiman.workouttracker.data.repository.TemplateExerciseConfig
import com.jupiman.workouttracker.data.repository.parseCentiKg
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgramViewModel(
    private val programRepository: ProgramRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    val programs: StateFlow<List<ProgramEntity>> = programRepository.programs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val activeProgram: StateFlow<ProgramEntity?> = programRepository.activeProgram.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val exercises: StateFlow<List<ExerciseEntity>> = exerciseRepository.exercises.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun workoutTemplates(programId: Long): Flow<List<WorkoutTemplateEntity>> =
        programRepository.workoutTemplates(programId)

    fun templateExercises(workoutTemplateId: Long): Flow<List<WorkoutTemplateExerciseEditorItem>> =
        programRepository.templateExercises(workoutTemplateId)

    fun clearMessage() {
        _message.value = null
    }

    fun createExercise(name: String) = launchOperation("Exercise added.") {
        exerciseRepository.createExercise(name)
    }

    fun renameExercise(id: Long, name: String) = launchOperation("Exercise renamed.") {
        exerciseRepository.renameExercise(id, name)
    }

    fun archiveExercise(id: Long) = launchOperation("Exercise archived.") {
        exerciseRepository.archiveExercise(id)
    }

    fun createProgram(name: String) = launchOperation("Program created.") {
        programRepository.createProgram(name)
    }

    fun renameProgram(id: Long, name: String) = launchOperation("Program renamed.") {
        programRepository.renameProgram(id, name)
    }

    fun activateProgram(id: Long) = launchOperation("Program activated.") {
        programRepository.activateProgram(id)
    }

    fun archiveProgram(id: Long) = launchOperation("Program archived.") {
        programRepository.archiveProgram(id)
    }

    fun createWorkoutTemplate(programId: Long, name: String) = launchOperation("Workout added.") {
        programRepository.createWorkoutTemplate(programId, name)
    }

    fun renameWorkoutTemplate(id: Long, name: String) = launchOperation("Workout renamed.") {
        programRepository.renameWorkoutTemplate(id, name)
    }

    fun deleteWorkoutTemplate(id: Long) = launchOperation("Workout removed.") {
        programRepository.deleteWorkoutTemplate(id)
    }

    fun moveWorkoutTemplate(programId: Long, id: Long, offset: Int) = launchOperation("Workout reordered.") {
        programRepository.moveWorkoutTemplate(programId, id, offset)
    }

    fun addExistingExerciseToWorkout(
        workoutTemplateId: Long,
        exerciseId: Long?,
        sets: String,
        repMin: String,
        repMax: String,
        currentWeight: String,
        currentTargetReps: String,
        increment: String,
        restSeconds: String,
    ) = launchOperation("Exercise added to workout.") {
        require(exerciseId != null) { "Choose an exercise first." }
        programRepository.addExistingExerciseToWorkout(
            workoutTemplateId = workoutTemplateId,
            exerciseId = exerciseId,
            config = templateExerciseConfig(
                sets = sets,
                repMin = repMin,
                repMax = repMax,
                currentWeight = currentWeight,
                currentTargetReps = currentTargetReps,
                increment = increment,
                restSeconds = restSeconds,
            ),
        )
    }

    fun createExerciseAndAddToWorkout(
        workoutTemplateId: Long,
        exerciseName: String,
        sets: String,
        repMin: String,
        repMax: String,
        currentWeight: String,
        currentTargetReps: String,
        increment: String,
        restSeconds: String,
    ) = launchOperation("Exercise created and added.") {
        programRepository.createExerciseAndAddToWorkout(
            workoutTemplateId = workoutTemplateId,
            exerciseName = exerciseName,
            config = templateExerciseConfig(
                sets = sets,
                repMin = repMin,
                repMax = repMax,
                currentWeight = currentWeight,
                currentTargetReps = currentTargetReps,
                increment = increment,
                restSeconds = restSeconds,
            ),
        )
    }

    fun updateTemplateExercise(
        item: WorkoutTemplateExerciseEditorItem,
        sets: String,
        repMin: String,
        repMax: String,
        currentWeight: String,
        currentTargetReps: String,
        increment: String,
        restSeconds: String,
    ) = launchOperation("Exercise configuration saved.") {
        programRepository.updateTemplateExercise(
            item = item,
            config = templateExerciseConfig(
                sets = sets,
                repMin = repMin,
                repMax = repMax,
                currentWeight = currentWeight,
                currentTargetReps = currentTargetReps,
                increment = increment,
                restSeconds = restSeconds,
            ),
        )
    }

    fun removeTemplateExercise(id: Long) = launchOperation("Exercise removed from workout.") {
        programRepository.removeTemplateExercise(id)
    }

    fun moveTemplateExercise(workoutTemplateId: Long, id: Long, offset: Int) =
        launchOperation("Exercise reordered.") {
            programRepository.moveTemplateExercise(workoutTemplateId, id, offset)
        }

    fun supersetWithPrevious(workoutTemplateId: Long, id: Long) = launchOperation("Superset updated.") {
        programRepository.supersetWithPrevious(workoutTemplateId, id)
    }

    fun removeFromSuperset(id: Long) = launchOperation("Superset updated.") {
        programRepository.removeFromSuperset(id)
    }

    private fun templateExerciseConfig(
        sets: String,
        repMin: String,
        repMax: String,
        currentWeight: String,
        currentTargetReps: String,
        increment: String,
        restSeconds: String,
    ) = TemplateExerciseConfig(
        plannedWorkingSets = sets.toPositiveInt("Working sets"),
        repMin = repMin.toPositiveInt("Minimum reps"),
        repMax = repMax.toPositiveInt("Maximum reps"),
        currentWeightCentiKg = parseCentiKg(currentWeight),
        currentTargetReps = currentTargetReps.toPositiveInt("Current target reps"),
        incrementCentiKg = parseCentiKg(increment),
        restSeconds = restSeconds.toNonNegativeInt("Rest seconds"),
    )

    private fun String.toPositiveInt(label: String): Int {
        val value = trim().toIntOrNull()
            ?: throw IllegalArgumentException("$label must be a whole number.")
        require(value >= 1) { "$label must be at least 1." }
        return value
    }

    private fun String.toNonNegativeInt(label: String): Int {
        val value = trim().toIntOrNull()
            ?: throw IllegalArgumentException("$label must be a whole number.")
        require(value >= 0) { "$label cannot be negative." }
        return value
    }

    private fun launchOperation(
        successMessage: String,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _message.value = successMessage }
                .onFailure { throwable ->
                    _message.value = throwable.message ?: "Something went wrong."
                }
        }
    }
}
