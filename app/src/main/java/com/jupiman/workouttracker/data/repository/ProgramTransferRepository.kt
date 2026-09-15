package com.jupiman.workouttracker.data.repository

import androidx.room.withTransaction
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.ProgressionStateEntity
import com.jupiman.workouttracker.data.local.entity.SupersetGroupEntity
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.entity.WarmupLoadType
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ProgramTransferRepository(
    private val database: WorkoutTrackerDatabase,
) {
    suspend fun exportProgram(programId: Long, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val document = database.withTransaction { createExportDocument(programId) }
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(document.toString(2))
        }
    }

    suspend fun importProgram(inputStream: InputStream): ImportedProgram = withContext(Dispatchers.IO) {
        val text = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        require(text.length <= MAX_DOCUMENT_CHARACTERS) { "Program file is too large." }
        val document = try {
            parseDocument(JSONObject(text))
        } catch (exception: JSONException) {
            throw IllegalArgumentException("Invalid program file.", exception)
        }

        database.withTransaction { insertDocument(document) }
    }

    private suspend fun createExportDocument(programId: Long): JSONObject {
        val program = database.programDao().getById(programId)
            ?: error("Program not found.")
        require(!program.archived) { "Archived programs cannot be exported." }

        val trainingDays = JSONArray()
        database.workoutTemplateDao().getForProgram(programId).forEach { template ->
            val groups = database.supersetGroupDao()
                .getForWorkoutTemplate(template.id)
                .sortedBy { it.id }
            val groupKeys = groups.mapIndexed { index, group -> group.id to "superset-${index + 1}" }.toMap()
            val groupArray = JSONArray()
            groups.forEach { group ->
                groupArray.put(
                    JSONObject()
                        .put("key", groupKeys.getValue(group.id))
                        .put("restSeconds", group.restSeconds),
                )
            }

            val exerciseArray = JSONArray()
            database.workoutTemplateExerciseDao()
                .getEditorItemsForWorkoutTemplate(template.id)
                .forEach { exercise ->
                    val setTargets = JSONArray()
                    database.workoutTemplateSetTargetDao()
                        .getForTemplateExercise(exercise.id)
                        .forEach { target ->
                            setTargets.put(
                                JSONObject()
                                    .put("setOrder", target.setOrder)
                                    .put("prescribedWeightCentiKg", target.prescribedWeightCentiKg)
                                    .put("prescribedReps", target.prescribedReps)
                                    .put("countsForProgression", target.countsForProgression),
                            )
                        }
                    val warmupSets = JSONArray()
                    database.workoutTemplateWarmupSetDao()
                        .getForTemplateExercise(exercise.id)
                        .forEach { warmup ->
                            warmupSets.put(
                                JSONObject()
                                    .put("sortOrder", warmup.sortOrder)
                                    .put("reps", warmup.reps)
                                    .put("loadType", warmup.loadType.name)
                                    .put("percentOfWorkingWeight", warmup.percentOfWorkingWeight ?: JSONObject.NULL)
                                    .put("fixedWeightCentiKg", warmup.fixedWeightCentiKg ?: JSONObject.NULL),
                            )
                        }
                    exerciseArray.put(
                        JSONObject()
                            .put("name", exercise.exerciseName)
                            .put("sortOrder", exercise.sortOrder)
                            .put("trackingMode", exercise.trackingMode.name)
                            .put("plannedWorkingSets", exercise.plannedWorkingSets)
                            .put("repMin", exercise.repMin)
                            .put("repMax", exercise.repMax)
                            .put("targetDurationSeconds", exercise.targetDurationSeconds ?: JSONObject.NULL)
                            .put("incrementCentiKg", exercise.incrementCentiKg)
                            .put("durationIncrementSeconds", exercise.durationIncrementSeconds)
                            .put("warmupRoundingCentiKg", exercise.warmupRoundingCentiKg)
                            .put("restSeconds", exercise.restSeconds)
                            .put("setupNote", exercise.setupNote)
                            .put("supersetKey", exercise.supersetGroupId?.let(groupKeys::get) ?: JSONObject.NULL)
                            .put(
                                "progression",
                                JSONObject()
                                    .put("currentWeightCentiKg", exercise.currentWeightCentiKg)
                                    .put("currentTargetReps", exercise.currentTargetReps),
                            )
                            .put("setTargets", setTargets)
                            .put("warmupSets", warmupSets),
                    )
                }
            trainingDays.put(
                JSONObject()
                    .put("name", template.name)
                    .put("sortOrder", template.sortOrder)
                    .put("supersets", groupArray)
                    .put("exercises", exerciseArray),
            )
        }

        return JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", Instant.now().toString())
            .put(
                "program",
                JSONObject()
                    .put("name", program.name)
                    .put("trainingDays", trainingDays),
            )
    }

    private fun parseDocument(root: JSONObject): ProgramDocument {
        require(root.getString("format") == FORMAT) { "This is not a Workout Companion program file." }
        val formatVersion = root.getInt("formatVersion")
        require(formatVersion in 1..FORMAT_VERSION) { "This program file version is not supported." }
        val program = root.getJSONObject("program")
        val name = program.requiredName("name", "Program name")
        val days = program.getJSONArray("trainingDays").objects().map { dayJson ->
            val groups = dayJson.getJSONArray("supersets").objects().map { groupJson ->
                SupersetDocument(
                    key = groupJson.requiredName("key", "Superset key"),
                    restSeconds = groupJson.nonNegativeInt("restSeconds", "Superset rest"),
                )
            }
            require(groups.map { it.key }.distinct().size == groups.size) { "Superset keys must be unique within a training day." }
            val groupKeys = groups.map { it.key }.toSet()
            val exercises = dayJson.getJSONArray("exercises").objects().map { exerciseJson ->
                val trackingMode = try {
                    TrackingMode.valueOf(exerciseJson.getString("trackingMode"))
                } catch (exception: IllegalArgumentException) {
                    throw IllegalArgumentException("Unknown exercise tracking mode.", exception)
                }
                val plannedWorkingSets = exerciseJson.positiveInt("plannedWorkingSets", "Working sets")
                val repMin = exerciseJson.positiveInt("repMin", "Minimum reps")
                val repMax = exerciseJson.positiveInt("repMax", "Maximum reps")
                require(repMax >= repMin) { "Maximum reps must be at least minimum reps." }
                val targetDurationSeconds = exerciseJson.nullableInt("targetDurationSeconds")
                val incrementCentiKg = exerciseJson.nonNegativeInt("incrementCentiKg", "Weight increment")
                val durationIncrementSeconds = exerciseJson.nonNegativeInt("durationIncrementSeconds", "Duration increment")
                val progressionJson = exerciseJson.getJSONObject("progression")
                val currentWeightCentiKg = progressionJson.nonNegativeInt("currentWeightCentiKg", "Current weight")
                val currentTargetReps = progressionJson.positiveInt("currentTargetReps", "Current target reps")
                when (trackingMode) {
                    TrackingMode.WEIGHT_REPS -> {
                        require(incrementCentiKg > 0) { "Weight increment must be greater than zero." }
                        require(currentTargetReps in repMin..repMax) { "Current target reps must be inside the rep range." }
                    }
                    TrackingMode.REPS -> require(currentTargetReps in repMin..repMax) { "Current target reps must be inside the rep range." }
                    TrackingMode.DURATION -> require(targetDurationSeconds != null && targetDurationSeconds > 0) {
                        "Duration target must be at least one second."
                    }
                }
                val supersetKey = exerciseJson.nullableString("supersetKey")
                require(supersetKey == null || supersetKey in groupKeys) { "Exercise references an unknown superset." }
                val setTargets = exerciseJson.getJSONArray("setTargets").objects().map { targetJson ->
                    SetTargetDocument(
                        setOrder = targetJson.nonNegativeInt("setOrder", "Set order"),
                        prescribedWeightCentiKg = targetJson.nonNegativeInt("prescribedWeightCentiKg", "Set weight"),
                        prescribedReps = targetJson.positiveInt("prescribedReps", "Set reps"),
                        countsForProgression = targetJson.getBoolean("countsForProgression"),
                    )
                }
                require(setTargets.map { it.setOrder }.distinct().size == setTargets.size) { "Set target orders must be unique." }
                require(setTargets.all { it.setOrder < plannedWorkingSets }) { "Set target is outside the planned working sets." }
                require(trackingMode != TrackingMode.DURATION || setTargets.isEmpty()) { "Duration exercises cannot contain rep set targets." }
                val warmupSets = exerciseJson.getJSONArray("warmupSets").objects().map { warmupJson ->
                    val loadType = if (formatVersion == 1) {
                        WarmupLoadType.PERCENTAGE
                    } else {
                        try {
                            WarmupLoadType.valueOf(warmupJson.getString("loadType"))
                        } catch (exception: IllegalArgumentException) {
                            throw IllegalArgumentException("Unknown warm-up load type.", exception)
                        }
                    }
                    val warmup = WarmupSetDocument(
                        sortOrder = warmupJson.nonNegativeInt("sortOrder", "Warm-up order"),
                        reps = warmupJson.positiveInt("reps", "Warm-up reps"),
                        loadType = loadType,
                        percentOfWorkingWeight = if (loadType == WarmupLoadType.PERCENTAGE) {
                            warmupJson.positiveInt("percentOfWorkingWeight", "Warm-up percentage")
                        } else null,
                        fixedWeightCentiKg = if (loadType == WarmupLoadType.FIXED) {
                            warmupJson.nonNegativeInt("fixedWeightCentiKg", "Fixed warm-up weight")
                        } else null,
                    )
                    if (formatVersion >= 2) {
                        require(
                            loadType != WarmupLoadType.PERCENTAGE || warmupJson.isNull("fixedWeightCentiKg"),
                        ) { "Percentage warm-ups cannot contain a fixed weight." }
                        require(
                            loadType != WarmupLoadType.FIXED || warmupJson.isNull("percentOfWorkingWeight"),
                        ) { "Fixed warm-ups cannot contain a percentage." }
                    }
                    require(warmup.percentOfWorkingWeight == null || warmup.percentOfWorkingWeight <= 100) {
                        "Warm-up percentage cannot exceed 100."
                    }
                    warmup
                }
                require(warmupSets.map { it.sortOrder }.distinct().size == warmupSets.size) { "Warm-up orders must be unique." }
                require(trackingMode == TrackingMode.WEIGHT_REPS || warmupSets.isEmpty()) {
                    "Only weight and reps exercises can contain warm-up sets."
                }
                ExerciseDocument(
                    name = exerciseJson.requiredName("name", "Exercise name"),
                    sortOrder = exerciseJson.nonNegativeInt("sortOrder", "Exercise order"),
                    trackingMode = trackingMode,
                    plannedWorkingSets = plannedWorkingSets,
                    repMin = repMin,
                    repMax = repMax,
                    targetDurationSeconds = targetDurationSeconds,
                    incrementCentiKg = incrementCentiKg,
                    durationIncrementSeconds = durationIncrementSeconds,
                    warmupRoundingCentiKg = if (formatVersion == 1) 500 else
                        exerciseJson.nonNegativeInt("warmupRoundingCentiKg", "Warm-up rounding"),
                    restSeconds = exerciseJson.nonNegativeInt("restSeconds", "Rest time"),
                    setupNote = exerciseJson.getString("setupNote").trim(),
                    supersetKey = supersetKey,
                    currentWeightCentiKg = currentWeightCentiKg,
                    currentTargetReps = currentTargetReps,
                    setTargets = setTargets.sortedBy { it.setOrder },
                    warmupSets = warmupSets.sortedBy { it.sortOrder },
                )
            }
            require(exercises.map { it.sortOrder }.distinct().size == exercises.size) { "Exercise orders must be unique within a training day." }
            groups.forEach { group ->
                val members = exercises.filter { it.supersetKey == group.key }
                require(members.size >= 2) { "Every superset must contain at least two exercises." }
                require(members.map { it.plannedWorkingSets }.distinct().size == 1) {
                    "Exercises in a superset must have the same working-set count."
                }
            }
            TrainingDayDocument(
                name = dayJson.requiredName("name", "Training day name"),
                sortOrder = dayJson.nonNegativeInt("sortOrder", "Training day order"),
                supersets = groups,
                exercises = exercises.sortedBy { it.sortOrder },
            )
        }
        require(days.map { it.sortOrder }.distinct().size == days.size) { "Training day orders must be unique." }
        return ProgramDocument(name = name, trainingDays = days.sortedBy { it.sortOrder })
    }

    private suspend fun insertDocument(document: ProgramDocument): ImportedProgram {
        val programName = availableProgramName(document.name, database.programDao().getAll().map { it.name })
        val now = System.currentTimeMillis()
        val programId = database.programDao().insert(
            ProgramEntity(name = programName, active = false, createdAt = now),
        )
        document.trainingDays.forEachIndexed { dayIndex, day ->
            val templateId = database.workoutTemplateDao().insert(
                WorkoutTemplateEntity(programId = programId, name = day.name, sortOrder = dayIndex),
            )
            val groupIds = day.supersets.associate { group ->
                group.key to database.supersetGroupDao().insert(
                    SupersetGroupEntity(workoutTemplateId = templateId, restSeconds = group.restSeconds),
                )
            }
            day.exercises.forEachIndexed { exerciseIndex, exercise ->
                val libraryExercise = database.exerciseDao().getByName(exercise.name)
                val exerciseId = libraryExercise?.id ?: database.exerciseDao().insert(
                    ExerciseEntity(name = exercise.name, createdAt = now),
                )
                val templateExerciseId = database.workoutTemplateExerciseDao().insert(
                    WorkoutTemplateExerciseEntity(
                        workoutTemplateId = templateId,
                        exerciseId = exerciseId,
                        sortOrder = exerciseIndex,
                        plannedWorkingSets = exercise.plannedWorkingSets,
                        repMin = exercise.repMin,
                        repMax = exercise.repMax,
                        incrementCentiKg = exercise.incrementCentiKg,
                        restSeconds = exercise.restSeconds,
                        setupNote = exercise.setupNote,
                        supersetGroupId = exercise.supersetKey?.let(groupIds::get),
                        trackingMode = exercise.trackingMode,
                        targetDurationSeconds = exercise.targetDurationSeconds,
                        durationIncrementSeconds = exercise.durationIncrementSeconds,
                        warmupRoundingCentiKg = exercise.warmupRoundingCentiKg,
                    ),
                )
                database.progressionStateDao().insert(
                    ProgressionStateEntity(
                        workoutTemplateExerciseId = templateExerciseId,
                        currentWeightCentiKg = exercise.currentWeightCentiKg,
                        currentTargetReps = exercise.currentTargetReps,
                        updatedAt = now,
                    ),
                )
                exercise.setTargets.forEach { target ->
                    database.workoutTemplateSetTargetDao().insert(
                        WorkoutTemplateSetTargetEntity(
                            workoutTemplateExerciseId = templateExerciseId,
                            setOrder = target.setOrder,
                            prescribedWeightCentiKg = target.prescribedWeightCentiKg,
                            prescribedReps = target.prescribedReps,
                            countsForProgression = target.countsForProgression,
                        ),
                    )
                }
                if (exercise.warmupSets.isNotEmpty()) {
                    database.workoutTemplateWarmupSetDao().insertAll(
                        exercise.warmupSets.mapIndexed { warmupIndex, warmup ->
                            WorkoutTemplateWarmupSetEntity(
                                workoutTemplateExerciseId = templateExerciseId,
                                sortOrder = warmupIndex,
                                reps = warmup.reps,
                                loadType = warmup.loadType,
                                percentOfWorkingWeight = warmup.percentOfWorkingWeight,
                                fixedWeightCentiKg = warmup.fixedWeightCentiKg,
                            )
                        },
                    )
                }
            }
        }
        return ImportedProgram(id = programId, name = programName)
    }

    private fun availableProgramName(sourceName: String, existingNames: List<String>): String {
        fun isUsed(candidate: String) = existingNames.any { it.equals(candidate, ignoreCase = true) }
        if (!isUsed(sourceName)) return sourceName
        val importedName = "$sourceName imported"
        if (!isUsed(importedName)) return importedName
        var suffix = 2
        while (isUsed("$importedName $suffix")) suffix += 1
        return "$importedName $suffix"
    }

    private fun JSONObject.requiredName(key: String, label: String): String =
        getString(key).trim().also { require(it.isNotEmpty()) { "$label cannot be empty." } }

    private fun JSONObject.nonNegativeInt(key: String, label: String): Int =
        getInt(key).also { require(it >= 0) { "$label cannot be negative." } }

    private fun JSONObject.positiveInt(key: String, label: String): Int =
        getInt(key).also { require(it >= 1) { "$label must be at least one." } }

    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key).trim().ifEmpty { null }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { index -> getJSONObject(index) }

    data class ImportedProgram(val id: Long, val name: String)

    private data class ProgramDocument(val name: String, val trainingDays: List<TrainingDayDocument>)
    private data class TrainingDayDocument(
        val name: String,
        val sortOrder: Int,
        val supersets: List<SupersetDocument>,
        val exercises: List<ExerciseDocument>,
    )
    private data class SupersetDocument(val key: String, val restSeconds: Int)
    private data class ExerciseDocument(
        val name: String,
        val sortOrder: Int,
        val trackingMode: TrackingMode,
        val plannedWorkingSets: Int,
        val repMin: Int,
        val repMax: Int,
        val targetDurationSeconds: Int?,
        val incrementCentiKg: Int,
        val durationIncrementSeconds: Int,
        val warmupRoundingCentiKg: Int,
        val restSeconds: Int,
        val setupNote: String,
        val supersetKey: String?,
        val currentWeightCentiKg: Int,
        val currentTargetReps: Int,
        val setTargets: List<SetTargetDocument>,
        val warmupSets: List<WarmupSetDocument>,
    )
    private data class SetTargetDocument(
        val setOrder: Int,
        val prescribedWeightCentiKg: Int,
        val prescribedReps: Int,
        val countsForProgression: Boolean,
    )
    private data class WarmupSetDocument(
        val sortOrder: Int,
        val reps: Int,
        val loadType: WarmupLoadType,
        val percentOfWorkingWeight: Int?,
        val fixedWeightCentiKg: Int?,
    )

    private companion object {
        const val FORMAT = "workout-companion-program"
        const val FORMAT_VERSION = 2
        const val MAX_DOCUMENT_CHARACTERS = 5_000_000
    }
}
