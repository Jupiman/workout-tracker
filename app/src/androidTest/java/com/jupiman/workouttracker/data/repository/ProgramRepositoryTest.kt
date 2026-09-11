package com.jupiman.workouttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProgramRepositoryTest {
    private lateinit var database: WorkoutTrackerDatabase
    private lateinit var repository: ProgramRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProgramRepository(
            database = database,
            programDao = database.programDao(),
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
            progressionStateDao = database.progressionStateDao(),
            supersetGroupDao = database.supersetGroupDao(),
            exerciseDao = database.exerciseDao(),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun supersetWithPreviousGroupsAdjacentMatchingExercises() = runTest {
        val seed = seedTwoExerciseTemplate()

        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)

        val first = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        val second = database.workoutTemplateExerciseDao().getById(seed.secondTemplateExerciseId)!!
        val groupId = first.supersetGroupId

        assertNotNull(groupId)
        assertEquals(groupId, second.supersetGroupId)
        assertEquals(180, database.supersetGroupDao().getById(groupId!!)?.restSeconds)
    }

    @Test
    fun removeFromSupersetDeletesTwoExerciseGroup() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)

        repository.removeFromSuperset(seed.secondTemplateExerciseId)

        val first = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        val second = database.workoutTemplateExerciseDao().getById(seed.secondTemplateExerciseId)!!
        assertNull(first.supersetGroupId)
        assertNull(second.supersetGroupId)
        assertEquals(emptyList<Long>(), database.supersetGroupDao().getForWorkoutTemplate(seed.templateId).map { it.id })
    }

    private suspend fun seedTwoExerciseTemplate(): TwoExerciseTemplateSeed {
        val programId = repository.createProgram("Current Program")
        val templateId = repository.createWorkoutTemplate(programId, "Day A")
        val firstTemplateExerciseId = repository.createExerciseAndAddToWorkout(
            workoutTemplateId = templateId,
            exerciseName = "Bench Press",
            config = config(restSeconds = 180),
        )
        val secondTemplateExerciseId = repository.createExerciseAndAddToWorkout(
            workoutTemplateId = templateId,
            exerciseName = "Machine Row",
            config = config(restSeconds = 120),
        )

        return TwoExerciseTemplateSeed(
            templateId = templateId,
            firstTemplateExerciseId = firstTemplateExerciseId,
            secondTemplateExerciseId = secondTemplateExerciseId,
        )
    }

    private fun config(restSeconds: Int) = TemplateExerciseConfig(
        plannedWorkingSets = 3,
        repMin = 8,
        repMax = 12,
        incrementCentiKg = 250,
        restSeconds = restSeconds,
        currentWeightCentiKg = 7000,
        currentTargetReps = 10,
    )

    private data class TwoExerciseTemplateSeed(
        val templateId: Long,
        val firstTemplateExerciseId: Long,
        val secondTemplateExerciseId: Long,
    )
}
