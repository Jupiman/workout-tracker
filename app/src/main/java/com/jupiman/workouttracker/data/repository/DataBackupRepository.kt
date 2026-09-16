package com.jupiman.workouttracker.data.repository

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DataBackupRepository(
    private val database: WorkoutTrackerDatabase,
    private val onRestoreCompleted: suspend () -> Unit = {},
) {
    suspend fun exportBackup(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        val backup = JSONObject()
            .put("app", BACKUP_APP)
            .put("formatVersion", BACKUP_FORMAT_VERSION)
            .put("schemaVersion", BACKUP_SCHEMA_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("tables", exportTables(db))

        outputStream.bufferedWriter().use { writer ->
            writer.write(backup.toString(2))
        }
    }

    suspend fun restoreBackup(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val text = inputStream.bufferedReader().use { it.readText() }
        val backup = JSONObject(text)
        require(backup.optString("app") == BACKUP_APP) { "This is not a Workout Companion backup." }
        val legacy = backup.optInt("formatVersion") == 1 && backup.optInt("schemaVersion") == 5
        val initialTracking = backup.optInt("formatVersion") == 2 && backup.optInt("schemaVersion") == 6
        val preDurationTimer = backup.optInt("formatVersion") == 3 && backup.optInt("schemaVersion") == 7
        val preAdvancedWarmups = backup.optInt("formatVersion") == 4 && backup.optInt("schemaVersion") == 8
        val preSelfHostedSync = backup.optInt("formatVersion") == 5 && backup.optInt("schemaVersion") == 9
        val current = backup.optInt("formatVersion") == BACKUP_FORMAT_VERSION &&
            backup.optInt("schemaVersion") == BACKUP_SCHEMA_VERSION
        require(legacy || initialTracking || preDurationTimer || preAdvancedWarmups || preSelfHostedSync || current) {
            "Unsupported backup format."
        }

        val tables = backup.getJSONObject("tables")
        val needsSyncIdentity = !current
        if (needsSyncIdentity) addSyncIdentityFields(tables)
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            BACKUP_TABLES.asReversed().forEach { table ->
                db.execSQL("DELETE FROM ${table.name}")
            }
            BACKUP_TABLES.forEach { table ->
                val rows = tables.optJSONArray(table.name) ?: JSONArray()
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    if (legacy) {
                        if (table.name == "workout_template_exercises") row.put("trackingMode", "WEIGHT_REPS").put("durationIncrementSeconds", 0)
                        if (table.name == "session_exercises") row.put("trackingModeSnapshot", "WEIGHT_REPS").put("durationIncrementSecondsSnapshot", 0)
                    }
                    if (initialTracking) {
                        if (table.name == "workout_template_exercises") row.put("durationIncrementSeconds", 0)
                        if (table.name == "session_exercises") row.put("durationIncrementSecondsSnapshot", 0)
                    }
                    if ((legacy || initialTracking || preDurationTimer) && table.name == "workout_sessions") {
                        row.put("activeDurationSetId", JSONObject.NULL)
                            .put("durationStartsAt", JSONObject.NULL)
                            .put("durationEndsAt", JSONObject.NULL)
                    }
                    if ((legacy || initialTracking || preDurationTimer || preAdvancedWarmups) &&
                        table.name == "workout_template_exercises"
                    ) {
                        row.put("warmupRoundingCentiKg", 500)
                    }
                    if ((legacy || initialTracking || preDurationTimer || preAdvancedWarmups) &&
                        table.name == "workout_template_warmup_sets"
                    ) {
                        row.put("loadType", "PERCENTAGE")
                            .put("fixedWeightCentiKg", JSONObject.NULL)
                    }
                    db.insert(
                        table.name,
                        SQLiteDatabase.CONFLICT_ABORT,
                        row.toContentValues(),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        runCatching { onRestoreCompleted() }
    }

    private fun exportTables(db: SupportSQLiteDatabase): JSONObject {
        val tables = JSONObject()
        BACKUP_TABLES.forEach { table ->
            tables.put(table.name, exportTable(db, table))
        }
        return tables
    }

    private fun exportTable(
        db: SupportSQLiteDatabase,
        table: BackupTable,
    ): JSONArray {
        val rows = JSONArray()
        db.query("SELECT * FROM ${table.name} ORDER BY ${table.orderBy}").use { cursor ->
            while (cursor.moveToNext()) {
                rows.put(cursor.toJsonObject())
            }
        }
        return rows
    }

    private fun Cursor.toJsonObject(): JSONObject {
        val row = JSONObject()
        for (index in 0 until columnCount) {
            val column = getColumnName(index)
            when (getType(index)) {
                Cursor.FIELD_TYPE_NULL -> row.put(column, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> row.put(column, getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> row.put(column, getDouble(index))
                Cursor.FIELD_TYPE_STRING -> row.put(column, getString(index))
                else -> error("Unsupported backup field type in $column.")
            }
        }
        return row
    }

    private fun JSONObject.toContentValues(): ContentValues {
        val values = ContentValues()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isNull(key)) {
                values.putNull(key)
                continue
            }
            when (val value = get(key)) {
                is Int -> values.put(key, value)
                is Long -> values.put(key, value)
                is Number -> values.put(key, value.toLong())
                is Boolean -> values.put(key, if (value) 1 else 0)
                is String -> values.put(key, value)
                else -> error("Unsupported backup value for $key.")
            }
        }
        return values
    }

    private fun addSyncIdentityFields(tables: JSONObject) {
        val progressionIds = mutableMapOf<Long, String>()
        tables.optJSONArray("workout_template_exercises")?.forEachObject { row ->
            val syncId = UUID.randomUUID().toString()
            progressionIds[row.getLong("id")] = syncId
            row.put("syncId", syncId)
        }
        tables.optJSONArray("workout_sessions")?.forEachObject { row ->
            row.put("syncId", UUID.randomUUID().toString())
                .put("selfHostedSyncState", "PENDING")
                .put("selfHostedLastAttemptAt", JSONObject.NULL)
                .put("selfHostedLastError", JSONObject.NULL)
        }
        val missingProgressionIds = mutableMapOf<Long, String>()
        tables.optJSONArray("session_exercises")?.forEachObject { row ->
            val sourceId = row.optLong("sourceWorkoutTemplateExerciseId").takeUnless {
                row.isNull("sourceWorkoutTemplateExerciseId")
            }
            val sourceSyncId = sourceId?.let { id ->
                progressionIds[id] ?: missingProgressionIds.getOrPut(id) { UUID.randomUUID().toString() }
            }
            row.put("syncId", UUID.randomUUID().toString())
                .put("sourceProgressionTrackSyncId", sourceSyncId ?: JSONObject.NULL)
                .put("resultingProgressionWeightCentiKg", JSONObject.NULL)
                .put("resultingProgressionTargetReps", JSONObject.NULL)
                .put("resultingProgressionDurationSeconds", JSONObject.NULL)
        }
        tables.optJSONArray("session_sets")?.forEachObject { row ->
            row.put("syncId", UUID.randomUUID().toString())
        }
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) block(getJSONObject(index))
    }

    private data class BackupTable(
        val name: String,
        val orderBy: String = "id",
    )

    private companion object {
        const val BACKUP_APP = "Workout Companion"
        const val BACKUP_FORMAT_VERSION = 6
        const val BACKUP_SCHEMA_VERSION = 10

        val BACKUP_TABLES = listOf(
            BackupTable("exercises"),
            BackupTable("programs"),
            BackupTable("workout_templates"),
            BackupTable("superset_groups"),
            BackupTable("workout_template_exercises"),
            BackupTable("progression_states", orderBy = "workoutTemplateExerciseId"),
            BackupTable("workout_template_set_targets"),
            BackupTable("workout_template_warmup_sets"),
            BackupTable("workout_sessions"),
            BackupTable("session_exercises"),
            BackupTable("session_sets"),
        )
    }
}
