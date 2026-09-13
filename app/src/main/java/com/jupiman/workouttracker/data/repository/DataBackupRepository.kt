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

class DataBackupRepository(
    private val database: WorkoutTrackerDatabase,
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
        require(backup.optInt("formatVersion") == BACKUP_FORMAT_VERSION) {
            "Unsupported backup format."
        }
        require(backup.optInt("schemaVersion") == BACKUP_SCHEMA_VERSION) {
            "Backup schema does not match this app version."
        }

        val tables = backup.getJSONObject("tables")
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            BACKUP_TABLES.asReversed().forEach { table ->
                db.execSQL("DELETE FROM ${table.name}")
            }
            BACKUP_TABLES.forEach { table ->
                val rows = tables.optJSONArray(table.name) ?: JSONArray()
                for (index in 0 until rows.length()) {
                    db.insert(
                        table.name,
                        SQLiteDatabase.CONFLICT_ABORT,
                        rows.getJSONObject(index).toContentValues(),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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

    private data class BackupTable(
        val name: String,
        val orderBy: String = "id",
    )

    private companion object {
        const val BACKUP_APP = "Workout Companion"
        const val BACKUP_FORMAT_VERSION = 1
        const val BACKUP_SCHEMA_VERSION = 5

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
