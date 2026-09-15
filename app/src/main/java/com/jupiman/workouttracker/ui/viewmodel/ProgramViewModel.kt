package com.jupiman.workouttracker.ui.viewmodel

import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.entity.WarmupLoadType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import com.jupiman.workouttracker.data.local.model.WorkoutTemplateExerciseEditorItem
import com.jupiman.workouttracker.data.repository.ExerciseRepository
import com.jupiman.workouttracker.data.repository.ExerciseProgressRepository
import com.jupiman.workouttracker.data.repository.ProgramRepository
import com.jupiman.workouttracker.data.repository.TemplateExerciseConfig
import com.jupiman.workouttracker.data.repository.WarmupSetConfiguration
import com.jupiman.workouttracker.data.repository.parseWeight
import com.jupiman.workouttracker.preferences.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WarmupSetInput(
    val loadType: WarmupLoadType,
    val loadValue: String,
    val reps: String,
)

class ProgramViewModel(
    private val programRepository: ProgramRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseProgressRepository: ExerciseProgressRepository,
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

    fun templateSetTargets(workoutTemplateExerciseId: Long): Flow<List<WorkoutTemplateSetTargetEntity>> =
        programRepository.templateSetTargets(workoutTemplateExerciseId)

    fun templateWarmupSets(workoutTemplateExerciseId: Long): Flow<List<WorkoutTemplateWarmupSetEntity>> =
        programRepository.templateWarmupSets(workoutTemplateExerciseId)

    fun progressTracks(exerciseId: Long) = exerciseProgressRepository.tracksForExercise(exerciseId)

    fun progressSessions(workoutTemplateExerciseId: Long) =
        exerciseProgressRepository.sessionsForTrack(workoutTemplateExerciseId)

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

    fun duplicateWorkoutTemplate(id: Long) = launchOperation("Training day duplicated.") {
        programRepository.duplicateWorkoutTemplate(id)
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
        trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
        durationSeconds: String = "60",
        durationIncrementSeconds: String = "0",
        weightUnit: WeightUnit = WeightUnit.KG,
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
                trackingMode = trackingMode,
                durationSeconds = durationSeconds,
                durationIncrementSeconds = durationIncrementSeconds,
                weightUnit = weightUnit,
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
        trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
        durationSeconds: String = "60",
        durationIncrementSeconds: String = "0",
        weightUnit: WeightUnit = WeightUnit.KG,
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
                trackingMode = trackingMode,
                durationSeconds = durationSeconds,
                durationIncrementSeconds = durationIncrementSeconds,
                weightUnit = weightUnit,
            ),
        )
    }

    fun updateTemplateExercise(
        item: WorkoutTemplateExerciseEditorItem,
        exerciseName: String = item.exerciseName,
        sets: String,
        repMin: String,
        repMax: String,
        currentWeight: String,
        currentTargetReps: String,
        increment: String,
        restSeconds: String,
        trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
        durationSeconds: String = "60",
        durationIncrementSeconds: String = "0",
        setupNote: String,
        weightUnit: WeightUnit = WeightUnit.KG,
    ) = launchOperation("Exercise saved.") {
        programRepository.updateTemplateExercise(
            item = item,
            exerciseName = exerciseName,
            config = templateExerciseConfig(
                sets = sets,
                repMin = repMin,
                repMax = repMax,
                currentWeight = currentWeight,
                currentTargetReps = currentTargetReps,
                increment = increment,
                restSeconds = restSeconds,
                trackingMode = trackingMode,
                durationSeconds = durationSeconds,
                durationIncrementSeconds = durationIncrementSeconds,
                weightUnit = weightUnit,
            ),
            setupNote = setupNote,
        )
    }

    fun removeTemplateExercise(id: Long) = launchOperation("Exercise removed from workout.") {
        programRepository.removeTemplateExercise(id)
    }

    fun duplicateTemplateExercise(id: Long) = launchOperation("Exercise duplicated.") {
        programRepository.duplicateTemplateExercise(id)
    }

    fun updateTemplateSetTarget(
        workoutTemplateExerciseId: Long,
        setOrder: Int,
        prescribedWeight: String,
        prescribedReps: String,
        weightUnit: WeightUnit = WeightUnit.KG,
    ) = launchOperation("Set target saved.") {
        programRepository.updateTemplateSetTarget(
            workoutTemplateExerciseId = workoutTemplateExerciseId,
            setOrder = setOrder,
            prescribedWeightCentiKg = parseWeight(prescribedWeight, weightUnit),
            prescribedReps = prescribedReps.toPositiveInt("Set reps"),
        )
    }

    fun resetTemplateSetTarget(workoutTemplateExerciseId: Long, setOrder: Int) =
        launchOperation("Set target reset.") {
            programRepository.resetTemplateSetTarget(workoutTemplateExerciseId, setOrder)
        }

    fun resetTemplateSetTargets(workoutTemplateExerciseId: Long) =
        launchOperation("Set targets reset.") {
            programRepository.resetTemplateSetTargets(workoutTemplateExerciseId)
        }

    fun saveWarmupScheme(
        workoutTemplateExerciseId: Long,
        roundingCentiKg: Int,
        sets: List<WarmupSetInput>,
        weightUnit: WeightUnit,
    ) = launchOperation("Warm-up scheme saved.") {
        programRepository.saveWarmupScheme(
            workoutTemplateExerciseId = workoutTemplateExerciseId,
            roundingCentiKg = roundingCentiKg,
            sets = sets.map { input ->
                val reps = input.reps.toPositiveInt("Warm-up reps")
                when (input.loadType) {
                    WarmupLoadType.PERCENTAGE -> WarmupSetConfiguration(
                        reps = reps,
                        loadType = input.loadType,
                        percentOfWorkingWeight = input.loadValue.toPositiveInt("Warm-up percentage"),
                    )
                    WarmupLoadType.FIXED -> WarmupSetConfiguration(
                        reps = reps,
                        loadType = input.loadType,
                        fixedWeightCentiKg = parseWeight(input.loadValue, weightUnit),
                    )
                }
            },
        )
    }

    fun clearWarmupScheme(workoutTemplateExerciseId: Long) =
        launchOperation("Warm-up scheme cleared.") {
            programRepository.clearWarmupScheme(workoutTemplateExerciseId)
        }

    fun moveTemplateExercise(workoutTemplateId: Long, id: Long, offset: Int) =
        launchOperation("Exercise reordered.") {
            programRepository.moveTemplateExercise(workoutTemplateId, id, offset)
        }

    fun moveSupersetGroup(workoutTemplateId: Long, supersetGroupId: Long, offset: Int) =
        launchOperation("Superset reordered.") {
            programRepository.moveSupersetGroup(workoutTemplateId, supersetGroupId, offset)
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
        trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
        durationSeconds: String = "60",
        durationIncrementSeconds: String = "0",
        weightUnit: WeightUnit = WeightUnit.KG,
    ) = TemplateExerciseConfig(
        plannedWorkingSets = sets.toPositiveInt("Working sets"),
        trackingMode = trackingMode,
        durationIncrementSeconds = if (trackingMode == TrackingMode.DURATION)
            durationIncrementSeconds.toNonNegativeInt("Increment seconds") else 0,
        targetDurationSeconds = if (trackingMode == TrackingMode.DURATION) durationSeconds.toPositiveInt("Duration seconds") else null,
        repMin = if (trackingMode == TrackingMode.DURATION) 1 else repMin.toPositiveInt("Minimum reps"),
        repMax = if (trackingMode == TrackingMode.DURATION) 1 else repMax.toPositiveInt("Maximum reps"),
        currentWeightCentiKg = if (trackingMode == TrackingMode.WEIGHT_REPS) parseWeight(currentWeight, weightUnit) else 0,
        currentTargetReps = if (trackingMode == TrackingMode.DURATION) 1 else currentTargetReps.toPositiveInt("Current target reps"),
        incrementCentiKg = if (trackingMode == TrackingMode.WEIGHT_REPS) parseWeight(increment, weightUnit) else 250,
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
