package com.jupiman.workouttracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackingMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WorkoutTrackerDatabase::class.java)

    @Test
    fun versionFiveHistorySurvivesWithWeightRepsDefaults() {
        val name = "tracking-migration-test"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO programs VALUES (1, 'Program', 1, 0, 1000)")
            execSQL("INSERT INTO exercises VALUES (1, 'Bench', 0, 1000)")
            execSQL("INSERT INTO workout_templates VALUES (1, 1, 'Day', 0)")
            execSQL("INSERT INTO workout_template_exercises (id, workoutTemplateId, exerciseId, sortOrder, plannedWorkingSets, repMin, repMax, incrementCentiKg, restSeconds, setupNote) VALUES (1, 1, 1, 0, 3, 8, 12, 250, 180, 'Keep note')")
            execSQL("INSERT INTO workout_sessions VALUES (1, 1, 1, 'Program', 'Day', 1000, 2000, 'COMPLETED', NULL, 1)")
            execSQL("INSERT INTO session_exercises VALUES (1, 1, 1, 'Bench', 0, 3, 8, 12, 10, 7000, 250, 180, 'Keep snapshot', NULL, NULL)")
            execSQL("INSERT INTO session_sets VALUES (1, 1, 0, 'WORKING', 1, 1, 7000, 10, 7000, 10, 'COMPLETED', 2000)")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, WorkoutTrackerDatabase.MIGRATION_5_6, WorkoutTrackerDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT trackingMode, durationIncrementSeconds, setupNote FROM workout_template_exercises").use {
                assertTrue(it.moveToFirst())
                assertEquals("WEIGHT_REPS", it.getString(0))
                assertEquals(0, it.getInt(1))
                assertEquals("Keep note", it.getString(2))
            }
            db.query("SELECT trackingModeSnapshot, targetDurationSecondsSnapshot, setupNoteSnapshot FROM session_exercises").use {
                assertTrue(it.moveToFirst())
                assertEquals("WEIGHT_REPS", it.getString(0))
                assertTrue(it.isNull(1))
                assertEquals("Keep snapshot", it.getString(2))
            }
            db.query("SELECT actualWeightCentiKg, actualReps, actualDurationSeconds FROM session_sets").use {
                assertTrue(it.moveToFirst())
                assertEquals(7000, it.getInt(0))
                assertEquals(10, it.getInt(1))
                assertTrue(it.isNull(2))
            }
        }
    }

    @Test
    fun versionSixTrackingDataSurvivesDurationIncrementMigration() {
        val name = "duration-increment-migration-test"
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO programs VALUES (1, 'Program', 1, 0, 1000)")
            execSQL("INSERT INTO exercises VALUES (1, 'Plank', 0, 1000)")
            execSQL("INSERT INTO workout_templates VALUES (1, 1, 'Day', 0)")
            execSQL("INSERT INTO workout_template_exercises (id, workoutTemplateId, exerciseId, sortOrder, plannedWorkingSets, repMin, repMax, incrementCentiKg, restSeconds, setupNote, trackingMode, targetDurationSeconds) VALUES (1, 1, 1, 0, 3, 1, 1, 250, 60, 'Elbows down', 'DURATION', 45)")
            execSQL("INSERT INTO workout_sessions VALUES (1, 1, 1, 'Program', 'Day', 1000, NULL, 'ACTIVE', NULL, 0)")
            execSQL("INSERT INTO session_exercises (id, sessionId, sourceWorkoutTemplateExerciseId, exerciseNameSnapshot, sortOrderSnapshot, plannedSetCountSnapshot, repMinSnapshot, repMaxSnapshot, targetRepsSnapshot, prescribedWeightCentiKgSnapshot, incrementCentiKgSnapshot, restSecondsSnapshot, setupNoteSnapshot, supersetGroupSnapshot, supersetRestSecondsSnapshot, trackingModeSnapshot, targetDurationSecondsSnapshot) VALUES (1, 1, 1, 'Plank', 0, 3, 1, 1, 1, 0, 250, 60, 'Elbows down', NULL, NULL, 'DURATION', 45)")
            execSQL("INSERT INTO session_sets (id, sessionExerciseId, setOrder, setType, isPlanned, countsForProgression, prescribedWeightCentiKg, prescribedReps, actualWeightCentiKg, actualReps, status, completedAt, prescribedDurationSeconds, actualDurationSeconds) VALUES (1, 1, 0, 'WORKING', 1, 0, NULL, NULL, NULL, NULL, 'COMPLETED', 2000, 45, 50)")
            close()
        }

        helper.runMigrationsAndValidate(name, 7, true, WorkoutTrackerDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT trackingMode, targetDurationSeconds, durationIncrementSeconds FROM workout_template_exercises").use {
                assertTrue(it.moveToFirst())
                assertEquals("DURATION", it.getString(0))
                assertEquals(45, it.getInt(1))
                assertEquals(0, it.getInt(2))
            }
            db.query("SELECT trackingModeSnapshot, targetDurationSecondsSnapshot, durationIncrementSecondsSnapshot FROM session_exercises").use {
                assertTrue(it.moveToFirst())
                assertEquals("DURATION", it.getString(0))
                assertEquals(45, it.getInt(1))
                assertEquals(0, it.getInt(2))
            }
            db.query("SELECT prescribedDurationSeconds, actualDurationSeconds FROM session_sets").use {
                assertTrue(it.moveToFirst())
                assertEquals(45, it.getInt(0))
                assertEquals(50, it.getInt(1))
            }
        }
    }

    @Test
    fun versionSevenSessionsGainEmptyDurationTimerState() {
        val name = "duration-timer-migration-test"
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO programs VALUES (1, 'Program', 1, 0, 1000)")
            execSQL("INSERT INTO workout_templates VALUES (1, 1, 'Day', 0)")
            execSQL("INSERT INTO workout_sessions VALUES (1, 1, 1, 'Program', 'Day', 1000, NULL, 'ACTIVE', 5000, 0)")
            close()
        }

        helper.runMigrationsAndValidate(name, 8, true, WorkoutTrackerDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT restEndsAt, activeDurationSetId, durationStartsAt, durationEndsAt FROM workout_sessions").use {
                assertTrue(it.moveToFirst())
                assertEquals(5000L, it.getLong(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
                assertTrue(it.isNull(3))
            }
        }
    }
}
