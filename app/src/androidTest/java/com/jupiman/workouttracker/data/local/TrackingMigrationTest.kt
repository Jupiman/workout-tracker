package com.jupiman.workouttracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackingMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WorkoutTrackerDatabase::class.java)

    @Test
    fun versionFiveHistorySurvivesThroughCurrentSchemaWithSafeDefaults() {
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
        helper.runMigrationsAndValidate(
            name,
            10,
            true,
            WorkoutTrackerDatabase.MIGRATION_5_6,
            WorkoutTrackerDatabase.MIGRATION_6_7,
            WorkoutTrackerDatabase.MIGRATION_7_8,
            WorkoutTrackerDatabase.MIGRATION_8_9,
            WorkoutTrackerDatabase.MIGRATION_9_10,
        ).use { db ->
            db.query(
                "SELECT trackingMode, durationIncrementSeconds, setupNote, warmupRoundingCentiKg " +
                    "FROM workout_template_exercises",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("WEIGHT_REPS", it.getString(0))
                assertEquals(0, it.getInt(1))
                assertEquals("Keep note", it.getString(2))
                assertEquals(500, it.getInt(3))
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
            db.query("SELECT activeDurationSetId, durationStartsAt, durationEndsAt FROM workout_sessions").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
        }
    }

    @Test
    fun versionNineSyncIdentityMigrationPreservesHistoricalProgressionGrouping() {
        val name = "self-hosted-sync-migration-test"
        helper.createDatabase(name, 9).apply {
            execSQL("INSERT INTO programs VALUES (1, 'Program', 1, 0, 1000)")
            execSQL("INSERT INTO exercises VALUES (1, 'Bench', 0, 1000)")
            execSQL("INSERT INTO workout_templates VALUES (1, 1, 'Day', 0)")
            execSQL(
                "INSERT INTO workout_template_exercises " +
                    "(id, workoutTemplateId, exerciseId, sortOrder, plannedWorkingSets, repMin, repMax, incrementCentiKg, restSeconds, setupNote, supersetGroupId, trackingMode, targetDurationSeconds, durationIncrementSeconds, warmupRoundingCentiKg) " +
                    "VALUES (1, 1, 1, 0, 1, 8, 12, 250, 120, '', NULL, 'WEIGHT_REPS', NULL, 0, 500)",
            )
            execSQL("INSERT INTO workout_sessions VALUES (1, 1, 1, 'Program', 'Day', 1000, 2000, 'COMPLETED', NULL, NULL, NULL, NULL, 1)")
            execSQL("INSERT INTO workout_sessions VALUES (2, 1, 1, 'Program', 'Day', 3000, 4000, 'PARTIAL', NULL, NULL, NULL, NULL, 1)")
            execSQL("INSERT INTO session_exercises VALUES (1, 1, 1, 'Bench', 0, 1, 8, 12, 8, 7000, 250, 120, '', NULL, NULL, 'WEIGHT_REPS', NULL, 0)")
            execSQL("INSERT INTO session_exercises VALUES (2, 1, 99, 'Deleted track', 1, 1, 8, 12, 8, 5000, 250, 120, '', NULL, NULL, 'WEIGHT_REPS', NULL, 0)")
            execSQL("INSERT INTO session_exercises VALUES (3, 2, 99, 'Deleted track', 0, 1, 8, 12, 8, 5000, 250, 120, '', NULL, NULL, 'WEIGHT_REPS', NULL, 0)")
            execSQL("INSERT INTO session_exercises VALUES (4, 2, NULL, 'Session only', 1, 1, 8, 8, 8, 0, 250, 120, '', NULL, NULL, 'REPS', NULL, 0)")
            execSQL("INSERT INTO session_sets VALUES (1, 1, 0, 'WORKING', 1, 1, 7000, 8, 7000, 8, 'COMPLETED', 2000, NULL, NULL)")
            close()
        }

        helper.runMigrationsAndValidate(name, 10, true, WorkoutTrackerDatabase.MIGRATION_9_10).use { db ->
            val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            val templateSyncId = db.query("SELECT syncId FROM workout_template_exercises WHERE id = 1").use {
                assertTrue(it.moveToFirst()); it.getString(0)
            }
            assertTrue(uuid.matches(templateSyncId))
            db.query("SELECT sourceProgressionTrackSyncId FROM session_exercises WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals(templateSyncId, it.getString(0))
            }
            val deletedIds = db.query("SELECT sourceProgressionTrackSyncId FROM session_exercises WHERE id IN (2, 3) ORDER BY id").use {
                buildList { while (it.moveToNext()) add(it.getString(0)) }
            }
            assertEquals(2, deletedIds.size)
            assertEquals(deletedIds[0], deletedIds[1])
            assertTrue(uuid.matches(deletedIds[0]))
            assertTrue(templateSyncId != deletedIds[0])
            db.query("SELECT sourceProgressionTrackSyncId, resultingProgressionWeightCentiKg FROM session_exercises WHERE id = 4").use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertTrue(it.isNull(1))
            }
            db.query("SELECT syncId, selfHostedSyncState, selfHostedLastAttemptAt FROM workout_sessions ORDER BY id").use {
                var count = 0
                while (it.moveToNext()) {
                    assertTrue(uuid.matches(it.getString(0)))
                    assertEquals("PENDING", it.getString(1))
                    assertTrue(it.isNull(2))
                    count++
                }
                assertEquals(2, count)
            }
            db.query("SELECT syncId, exerciseNameSnapshot FROM session_exercises ORDER BY id").use {
                val ids = mutableSetOf<String>()
                val names = mutableListOf<String>()
                while (it.moveToNext()) {
                    assertTrue(uuid.matches(it.getString(0)))
                    assertTrue(ids.add(it.getString(0)))
                    names += it.getString(1)
                }
                assertEquals(listOf("Bench", "Deleted track", "Deleted track", "Session only"), names)
            }
            db.query("SELECT syncId, actualWeightCentiKg, actualReps FROM session_sets WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertTrue(uuid.matches(it.getString(0)))
                assertEquals(7000, it.getInt(1))
                assertEquals(8, it.getInt(2))
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

    @Test
    fun versionEightWarmupsMigrateAsPercentageRowsWithLegacyRounding() {
        val name = "advanced-warmup-migration-test"
        helper.createDatabase(name, 8).apply {
            execSQL("INSERT INTO programs VALUES (1, 'Program', 1, 0, 1000)")
            execSQL("INSERT INTO exercises VALUES (1, 'Bench', 0, 1000)")
            execSQL("INSERT INTO workout_templates VALUES (1, 1, 'Day', 0)")
            execSQL(
                "INSERT INTO workout_template_exercises " +
                    "(id, workoutTemplateId, exerciseId, sortOrder, plannedWorkingSets, repMin, repMax, " +
                    "incrementCentiKg, restSeconds, setupNote, supersetGroupId, trackingMode, " +
                    "targetDurationSeconds, durationIncrementSeconds) " +
                    "VALUES (1, 1, 1, 0, 3, 8, 12, 250, 180, '', NULL, 'WEIGHT_REPS', NULL, 0)",
            )
            execSQL(
                "INSERT INTO workout_template_warmup_sets " +
                    "(id, workoutTemplateExerciseId, sortOrder, reps, percentOfWorkingWeight) VALUES " +
                    "(1, 1, 0, 10, 30), (2, 1, 1, 3, 70), (3, 1, 2, 3, 75)",
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 9, true, WorkoutTrackerDatabase.MIGRATION_8_9).use { db ->
            db.query("SELECT warmupRoundingCentiKg FROM workout_template_exercises WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals(500, it.getInt(0))
            }
            db.query(
                "SELECT reps, loadType, percentOfWorkingWeight, fixedWeightCentiKg " +
                    "FROM workout_template_warmup_sets ORDER BY sortOrder",
            ).use {
                val rows = mutableListOf<Pair<Int, Int>>()
                while (it.moveToNext()) {
                    rows += it.getInt(2) to it.getInt(0)
                    assertEquals("PERCENTAGE", it.getString(1))
                    assertNull(if (it.isNull(3)) null else it.getInt(3))
                }
                assertEquals(listOf(30 to 10, 70 to 3, 75 to 3), rows)
            }
        }
    }
}
