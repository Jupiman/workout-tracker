package com.jupiman.workouttracker.selfhosted

import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

data class SelfHostedWorkoutPayload(
    val syncId: String,
    val json: JSONObject,
)

fun WorkoutSessionWithDetails.toSelfHostedPayload(): SelfHostedWorkoutPayload {
    require(session.status == WorkoutSessionStatus.COMPLETED || session.status == WorkoutSessionStatus.PARTIAL) {
        "Only finalized workouts can be synchronized."
    }
    val completedAt = requireNotNull(session.completedAt) { "Finalized workout is missing completedAt." }
    val exerciseJson = exercises.sortedBy { it.exercise.sortOrderSnapshot }.map { details ->
        val exercise = details.exercise
        val duration = exercise.trackingModeSnapshot == TrackingMode.DURATION
        val weightReps = exercise.trackingModeSnapshot == TrackingMode.WEIGHT_REPS
        JSONObject()
            .put("syncId", exercise.syncId)
            .putNullable("sourceProgressionTrackSyncId", exercise.sourceProgressionTrackSyncId)
            .put("exerciseName", exercise.exerciseNameSnapshot)
            .put("sortOrder", exercise.sortOrderSnapshot)
            .put("trackingMode", exercise.trackingModeSnapshot.name)
            .putNullable("repMin", if (duration) null else exercise.repMinSnapshot)
            .putNullable("repMax", if (duration) null else exercise.repMaxSnapshot)
            .putNullable("targetReps", if (duration) null else exercise.targetRepsSnapshot)
            .putNullable("prescribedWeightCentiKg", if (weightReps) exercise.prescribedWeightCentiKgSnapshot else null)
            .putNullable("targetDurationSeconds", if (duration) exercise.targetDurationSecondsSnapshot else null)
            .putNullable("resultingProgressionWeightCentiKg", if (weightReps) exercise.resultingProgressionWeightCentiKg else null)
            .putNullable("resultingProgressionTargetReps", if (duration) null else exercise.resultingProgressionTargetReps)
            .putNullable("resultingProgressionDurationSeconds", if (duration) exercise.resultingProgressionDurationSeconds else null)
            .put("sets", JSONArray(details.sets.sortedBy { it.setOrder }.map { set ->
                JSONObject()
                    .put("syncId", set.syncId)
                    .put("setOrder", set.setOrder)
                    .put("setType", set.setType.name)
                    .put("status", set.status.name)
                    .put("isPlanned", set.isPlanned)
                    .put("countsForProgression", set.countsForProgression)
                    .putNullable("prescribedWeightCentiKg", if (weightReps) set.prescribedWeightCentiKg else null)
                    .putNullable("prescribedReps", if (duration) null else set.prescribedReps)
                    .putNullable("prescribedDurationSeconds", if (duration) set.prescribedDurationSeconds else null)
                    .putNullable("actualWeightCentiKg", if (weightReps) set.actualWeightCentiKg else null)
                    .putNullable("actualReps", if (duration) null else set.actualReps)
                    .putNullable("actualDurationSeconds", if (duration) set.actualDurationSeconds else null)
                    .putNullable("completedAt", set.completedAt?.let(::isoInstant))
            }))
    }
    val json = JSONObject()
        .put("schemaVersion", PAYLOAD_SCHEMA_VERSION)
        .put("syncId", session.syncId)
        .put("programName", session.programNameSnapshot)
        .put("workoutName", session.workoutNameSnapshot)
        .put("startedAt", isoInstant(session.startedAt))
        .put("completedAt", isoInstant(completedAt))
        .put("status", session.status.name)
        .put("exercises", JSONArray(exerciseJson))
    return SelfHostedWorkoutPayload(session.syncId, json)
}

private fun isoInstant(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
