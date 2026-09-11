package com.jupiman.workouttracker.data.repository

import androidx.room.withTransaction
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.local.dao.ExerciseDao
import com.jupiman.workouttracker.data.local.dao.ProgramDao
import com.jupiman.workouttracker.data.local.dao.ProgressionStateDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateExerciseDao
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.ProgressionStateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import com.jupiman.workouttracker.data.local.model.WorkoutTemplateExerciseEditorItem

class ProgramRepository(
    private val database: WorkoutTrackerDatabase,
    private val programDao: ProgramDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutTemplateExerciseDao: WorkoutTemplateExerciseDao,
    private val progressionStateDao: ProgressionStateDao,
    private val exerciseDao: ExerciseDao,
) {
    val activeProgram = programDao.observeActive()
    val programs = programDao.observeAllActive()
    val firstActiveProgramTemplate = workoutTemplateDao.observeFirstForActiveProgram()

    fun workoutTemplates(programId: Long) = workoutTemplateDao.observeForProgram(programId)

    fun templateExercises(workoutTemplateId: Long) =
        workoutTemplateExerciseDao.observeEditorItemsForWorkoutTemplate(workoutTemplateId)

    suspend fun createProgram(name: String): Long {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Program name cannot be empty." }

        return database.withTransaction {
            val shouldActivate = programDao.activeProgramCount() == 0
            programDao.insert(
                ProgramEntity(
                    name = trimmedName,
                    active = shouldActivate,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun renameProgram(programId: Long, name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Program name cannot be empty." }

        val program = programDao.getById(programId) ?: error("Program not found.")
        programDao.update(program.copy(name = trimmedName))
    }

    suspend fun activateProgram(programId: Long) {
        programDao.activate(programId)
    }

    suspend fun archiveProgram(programId: Long) {
        programDao.archive(programId)
    }

    suspend fun createWorkoutTemplate(programId: Long, name: String): Long {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Workout name cannot be empty." }

        return database.withTransaction {
            val sortOrder = workoutTemplateDao.countForProgram(programId)
            workoutTemplateDao.insert(
                WorkoutTemplateEntity(
                    programId = programId,
                    name = trimmedName,
                    sortOrder = sortOrder,
                ),
            )
        }
    }

    suspend fun renameWorkoutTemplate(workoutTemplateId: Long, name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Workout name cannot be empty." }

        val template = workoutTemplateDao.getById(workoutTemplateId) ?: error("Workout not found.")
        workoutTemplateDao.update(template.copy(name = trimmedName))
    }

    suspend fun deleteWorkoutTemplate(workoutTemplateId: Long) {
        database.withTransaction {
            val template = workoutTemplateDao.getById(workoutTemplateId) ?: return@withTransaction
            workoutTemplateDao.deleteById(workoutTemplateId)
            compactTemplateSortOrders(template.programId)
        }
    }

    suspend fun moveWorkoutTemplate(programId: Long, workoutTemplateId: Long, offset: Int) {
        require(offset == -1 || offset == 1) { "Move offset must be -1 or 1." }

        database.withTransaction {
            val templates = workoutTemplateDao.getForProgram(programId)
            val index = templates.indexOfFirst { it.id == workoutTemplateId }
            if (index == -1) return@withTransaction

            val targetIndex = (index + offset).coerceIn(templates.indices)
            if (targetIndex == index) return@withTransaction

            val reordered = templates.toMutableList().apply {
                add(targetIndex, removeAt(index))
            }
            reordered.forEachIndexed { sortOrder, template ->
                workoutTemplateDao.update(template.copy(sortOrder = sortOrder))
            }
        }
    }

    suspend fun addExistingExerciseToWorkout(
        workoutTemplateId: Long,
        exerciseId: Long,
        config: TemplateExerciseConfig,
    ): Long {
        val validConfig = config.validatedForCreate()

        return database.withTransaction {
            val sortOrder = workoutTemplateExerciseDao.countForWorkoutTemplate(workoutTemplateId)
            val templateExerciseId = workoutTemplateExerciseDao.insert(
                WorkoutTemplateExerciseEntity(
                    workoutTemplateId = workoutTemplateId,
                    exerciseId = exerciseId,
                    sortOrder = sortOrder,
                    plannedWorkingSets = validConfig.plannedWorkingSets,
                    repMin = validConfig.repMin,
                    repMax = validConfig.repMax,
                    incrementCentiKg = validConfig.incrementCentiKg,
                    restSeconds = validConfig.restSeconds,
                ),
            )
            progressionStateDao.insert(
                ProgressionStateEntity(
                    workoutTemplateExerciseId = templateExerciseId,
                    currentWeightCentiKg = validConfig.currentWeightCentiKg,
                    currentTargetReps = validConfig.currentTargetReps,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            templateExerciseId
        }
    }

    suspend fun createExerciseAndAddToWorkout(
        workoutTemplateId: Long,
        exerciseName: String,
        config: TemplateExerciseConfig,
    ): Long {
        val trimmedName = exerciseName.trim()
        require(trimmedName.isNotEmpty()) { "Exercise name cannot be empty." }
        val validConfig = config.validatedForCreate()

        return database.withTransaction {
            val existing = exerciseDao.getByName(trimmedName)
            val exerciseId = when {
                existing == null -> exerciseDao.insert(
                    ExerciseEntity(
                        name = trimmedName,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                existing.archived -> {
                    exerciseDao.update(existing.copy(archived = false))
                    existing.id
                }
                else -> existing.id
            }

            val sortOrder = workoutTemplateExerciseDao.countForWorkoutTemplate(workoutTemplateId)
            val templateExerciseId = workoutTemplateExerciseDao.insert(
                WorkoutTemplateExerciseEntity(
                    workoutTemplateId = workoutTemplateId,
                    exerciseId = exerciseId,
                    sortOrder = sortOrder,
                    plannedWorkingSets = validConfig.plannedWorkingSets,
                    repMin = validConfig.repMin,
                    repMax = validConfig.repMax,
                    incrementCentiKg = validConfig.incrementCentiKg,
                    restSeconds = validConfig.restSeconds,
                ),
            )
            progressionStateDao.insert(
                ProgressionStateEntity(
                    workoutTemplateExerciseId = templateExerciseId,
                    currentWeightCentiKg = validConfig.currentWeightCentiKg,
                    currentTargetReps = validConfig.currentTargetReps,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            templateExerciseId
        }
    }

    suspend fun updateTemplateExercise(
        item: WorkoutTemplateExerciseEditorItem,
        config: TemplateExerciseConfig,
    ) {
        val validConfig = config.validatedForUpdate()

        database.withTransaction {
            val templateExercise = workoutTemplateExerciseDao.getById(item.id)
                ?: error("Workout exercise not found.")
            workoutTemplateExerciseDao.update(
                templateExercise.copy(
                    plannedWorkingSets = validConfig.plannedWorkingSets,
                    repMin = validConfig.repMin,
                    repMax = validConfig.repMax,
                    incrementCentiKg = validConfig.incrementCentiKg,
                    restSeconds = validConfig.restSeconds,
                ),
            )

            val existingState = progressionStateDao.getForTemplateExercise(item.id)
            val state = ProgressionStateEntity(
                workoutTemplateExerciseId = item.id,
                currentWeightCentiKg = validConfig.currentWeightCentiKg,
                currentTargetReps = validConfig.currentTargetReps,
                updatedAt = System.currentTimeMillis(),
            )
            if (existingState == null) {
                progressionStateDao.insert(state)
            } else {
                progressionStateDao.update(state)
            }
        }
    }

    suspend fun removeTemplateExercise(workoutTemplateExerciseId: Long) {
        database.withTransaction {
            val templateExercise = workoutTemplateExerciseDao.getById(workoutTemplateExerciseId)
                ?: return@withTransaction
            workoutTemplateExerciseDao.deleteById(workoutTemplateExerciseId)
            compactExerciseSortOrders(templateExercise.workoutTemplateId)
        }
    }

    suspend fun moveTemplateExercise(
        workoutTemplateId: Long,
        workoutTemplateExerciseId: Long,
        offset: Int,
    ) {
        require(offset == -1 || offset == 1) { "Move offset must be -1 or 1." }

        database.withTransaction {
            val exercises = workoutTemplateExerciseDao.getForWorkoutTemplate(workoutTemplateId)
            val index = exercises.indexOfFirst { it.id == workoutTemplateExerciseId }
            if (index == -1) return@withTransaction

            val targetIndex = (index + offset).coerceIn(exercises.indices)
            if (targetIndex == index) return@withTransaction

            val reordered = exercises.toMutableList().apply {
                add(targetIndex, removeAt(index))
            }
            reordered.forEachIndexed { sortOrder, exercise ->
                workoutTemplateExerciseDao.update(exercise.copy(sortOrder = sortOrder))
            }
        }
    }

    private suspend fun compactTemplateSortOrders(programId: Long) {
        workoutTemplateDao.getForProgram(programId).forEachIndexed { sortOrder, template ->
            if (template.sortOrder != sortOrder) {
                workoutTemplateDao.update(template.copy(sortOrder = sortOrder))
            }
        }
    }

    private suspend fun compactExerciseSortOrders(workoutTemplateId: Long) {
        workoutTemplateExerciseDao.getForWorkoutTemplate(workoutTemplateId).forEachIndexed { sortOrder, exercise ->
            if (exercise.sortOrder != sortOrder) {
                workoutTemplateExerciseDao.update(exercise.copy(sortOrder = sortOrder))
            }
        }
    }
}
