package com.jupiman.workouttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.repository.DataBackupRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultExerciseSeederTest {
    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "default-exercise-seeder-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun freshDatabaseSeedsCuratedExerciseLibrary() = runTest {
        withSeededDatabase { database ->
            val exercises = database.exerciseDao().getAll()
            val names = exercises.map { it.name }

            assertEquals(50, exercises.size)
            assertEquals(50, names.distinctBy { it.lowercase() }.size)
            assertTrue(names.contains("Bench Press"))
            assertTrue(names.contains("Lat Pulldown"))
            assertTrue(names.contains("Cable Lateral Raise"))
            assertTrue(names.contains("Triceps Pushdown"))
            assertTrue(names.contains("Hack Squat"))
            assertTrue(names.contains("Romanian Deadlift"))
            assertTrue(names.contains("Cable Crunch"))
        }
    }

    @Test
    fun restartDoesNotDuplicateSeededExercises() = runTest {
        withSeededDatabase { database ->
            assertEquals(50, database.exerciseDao().getAll().size)
        }

        withSeededDatabase { database ->
            assertEquals(50, database.exerciseDao().getAll().size)
        }
    }

    @Test
    fun renamedSeededExerciseIsNotRecreatedAfterRestart() = runTest {
        withSeededDatabase { database ->
            val latPulldown = database.exerciseDao().getByName("Lat Pulldown")!!
            database.exerciseDao().update(latPulldown.copy(name = "Neutral Grip Lat Pulldown"))
        }

        withSeededDatabase { database ->
            val names = database.exerciseDao().getAll().map { it.name }
            assertTrue(names.contains("Neutral Grip Lat Pulldown"))
            assertEquals(false, names.contains("Lat Pulldown"))
            assertEquals(50, names.size)
        }
    }

    @Test
    fun archivedSeededExerciseIsNotRecreatedAfterRestart() = runTest {
        withSeededDatabase { database ->
            val benchPress = database.exerciseDao().getByName("Bench Press")!!
            database.exerciseDao().archive(benchPress.id)
        }

        withSeededDatabase { database ->
            val benchPressRows = database.exerciseDao()
                .getAll()
                .filter { it.name.equals("Bench Press", ignoreCase = true) }

            assertEquals(1, benchPressRows.size)
            assertEquals(true, benchPressRows.single().archived)
            assertEquals(50, database.exerciseDao().getAll().size)
        }
    }

    @Test
    fun restoreReplacesSeededExercisesAndDoesNotReseedAfterRestart() = runTest {
        val backupBytes = createBackupWithExercises(listOf("Custom Backup Lift"))

        withSeededDatabase { database ->
            assertEquals(50, database.exerciseDao().getAll().size)
            DataBackupRepository(database).restoreBackup(ByteArrayInputStream(backupBytes))
            assertEquals(listOf("Custom Backup Lift"), database.exerciseDao().getAll().map { it.name })
        }

        withSeededDatabase { database ->
            assertEquals(listOf("Custom Backup Lift"), database.exerciseDao().getAll().map { it.name })
        }
    }

    @Test
    fun intentionallyEmptyExistingLibraryDoesNotReseedAfterRestart() = runTest {
        withSeededDatabase { database ->
            database.openHelper.writableDatabase.execSQL("DELETE FROM exercises")
            assertEquals(emptyList<ExerciseEntity>(), database.exerciseDao().getAll())
        }

        withSeededDatabase { database ->
            assertEquals(emptyList<ExerciseEntity>(), database.exerciseDao().getAll())
        }
    }

    private suspend fun withSeededDatabase(block: suspend (WorkoutTrackerDatabase) -> Unit) {
        val database = openSeededDatabase()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun openSeededDatabase(): WorkoutTrackerDatabase =
        Room.databaseBuilder(context, WorkoutTrackerDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addCallback(WorkoutTrackerDatabase.SEED_DEFAULT_EXERCISES_ON_CREATE)
            .build()

    private suspend fun createBackupWithExercises(names: List<String>): ByteArray {
        val sourceName = "default-exercise-seeder-source-${System.nanoTime()}.db"
        context.deleteDatabase(sourceName)
        val output = ByteArrayOutputStream()
        try {
            val database = Room.databaseBuilder(context, WorkoutTrackerDatabase::class.java, sourceName)
                .allowMainThreadQueries()
                .build()
            try {
                names.forEach { name ->
                    database.exerciseDao().insert(
                        ExerciseEntity(
                            name = name,
                            createdAt = 1_000L,
                        ),
                    )
                }
                DataBackupRepository(database).exportBackup(output)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(sourceName)
        }
        return output.toByteArray()
    }
}
