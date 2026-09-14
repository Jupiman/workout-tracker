package com.jupiman.workouttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.ProgressionStateEntity
import com.jupiman.workouttracker.data.local.entity.SessionExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WorkoutTrackerDatabaseTest {
    private lateinit var database: WorkoutTrackerDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun templateExerciseOwnsIndependentProgressionState() = runTest {
        val now = 1_000L
        val programId = database.programDao().insert(ProgramEntity(name = "Current Program", active = true, createdAt = now))
        val dayAId = database.workoutTemplateDao().insert(WorkoutTemplateEntity(programId = programId, name = "Day A", sortOrder = 0))
        val dayCId = database.workoutTemplateDao().insert(WorkoutTemplateEntity(programId = programId, name = "Day C", sortOrder = 1))
        val benchId = database.exerciseDao().insert(ExerciseEntity(name = "Bench Press", createdAt = now))
        val dayABenchId = database.workoutTemplateExerciseDao().insert(
            WorkoutTemplateExerciseEntity(
                workoutTemplateId = dayAId,
                exerciseId = benchId,
                sortOrder = 0,
                plannedWorkingSets = 3,
                repMin = 8,
                repMax = 12,
                incrementCentiKg = 250,
                restSeconds = 180,
            ),
        )
        val dayCBenchId = database.workoutTemplateExerciseDao().insert(
            WorkoutTemplateExerciseEntity(
                workoutTemplateId = dayCId,
                exerciseId = benchId,
                sortOrder = 0,
                plannedWorkingSets = 3,
                repMin = 12,
                repMax = 15,
                incrementCentiKg = 250,
                restSeconds = 180,
            ),
        )

        database.progressionStateDao().insert(
            ProgressionStateEntity(dayABenchId, currentWeightCentiKg = 7000, currentTargetReps = 8, updatedAt = now),
        )
        database.progressionStateDao().insert(
            ProgressionStateEntity(dayCBenchId, currentWeightCentiKg = 5500, currentTargetReps = 12, updatedAt = now),
        )

        assertEquals(7000, database.progressionStateDao().getForTemplateExercise(dayABenchId)?.currentWeightCentiKg)
        assertEquals(5500, database.progressionStateDao().getForTemplateExercise(dayCBenchId)?.currentWeightCentiKg)
    }

    @Test
    fun sessionSnapshotSurvivesSourceRename() = runTest {
        val now = 2_000L
        val exerciseId = database.exerciseDao().insert(ExerciseEntity(name = "Bench Press", createdAt = now))
        val sessionId = database.workoutSessionDao().insert(
            WorkoutSessionEntity(
                sourceWorkoutTemplateId = null,
                sourceProgramId = null,
                programNameSnapshot = "Current Program",
                workoutNameSnapshot = "Day A",
                startedAt = now,
            ),
        )
        val sessionExerciseId = database.sessionExerciseDao().insert(
            SessionExerciseEntity(
                sessionId = sessionId,
                sourceWorkoutTemplateExerciseId = null,
                exerciseNameSnapshot = "Bench Press",
                sortOrderSnapshot = 0,
                plannedSetCountSnapshot = 3,
                repMinSnapshot = 8,
                repMaxSnapshot = 12,
                targetRepsSnapshot = 10,
                prescribedWeightCentiKgSnapshot = 7000,
                incrementCentiKgSnapshot = 250,
                restSecondsSnapshot = 180,
                setupNoteSnapshot = "",
                supersetGroupSnapshot = null,
                supersetRestSecondsSnapshot = null,
            ),
        )
        database.sessionSetDao().insert(
            SessionSetEntity(
                sessionExerciseId = sessionExerciseId,
                setOrder = 0,
                setType = SetType.WORKING,
                isPlanned = true,
                countsForProgression = true,
                prescribedWeightCentiKg = 7000,
                prescribedReps = 10,
                actualWeightCentiKg = 7000,
                actualReps = 10,
            ),
        )

        database.exerciseDao().update(ExerciseEntity(id = exerciseId, name = "Barbell Bench Press", createdAt = now))

        val snapshot = database.sessionExerciseDao().getForSession(sessionId).single()
        assertEquals("Bench Press", snapshot.exerciseNameSnapshot)
    }
}
