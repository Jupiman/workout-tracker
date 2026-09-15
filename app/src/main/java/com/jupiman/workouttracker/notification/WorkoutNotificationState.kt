package com.jupiman.workouttracker.notification

import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.formatCentiKg
import kotlin.math.abs
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.repository.trackingText
import com.jupiman.workouttracker.preferences.WeightUnit

sealed interface WorkoutNotificationState {
    data class SetAction(
        val sessionId: Long,
        val setId: Long,
        val title: String,
        val text: String,
        val trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
    ) : WorkoutNotificationState

    data class Resting(
        val restEndsAt: Long,
        val title: String,
        val text: String,
    ) : WorkoutNotificationState

    data class RestFinished(
        val title: String,
        val text: String,
        val setAction: SetAction?,
    ) : WorkoutNotificationState

    data class WaitingToFinish(
        val title: String,
        val text: String,
    ) : WorkoutNotificationState
}

object WorkoutNotificationProjector {
    fun stateFor(
        activeWorkout: WorkoutSessionWithDetails?,
        now: Long = System.currentTimeMillis(),
        weightUnit: WeightUnit = WeightUnit.KG,
    ): WorkoutNotificationState? {
        val workout = activeWorkout ?: return null
        val nextSet = workout.findNextActionableSet()
        val restEndsAt = workout.session.restEndsAt
        if (restEndsAt != null) {
            val nextText = nextSet?.let { "Next: ${it.exerciseName} • ${it.targetText(weightUnit)}" }
                ?: "No pending sets"
            return if (restEndsAt > now) {
                WorkoutNotificationState.Resting(
                    restEndsAt = restEndsAt,
                    title = "Rest",
                    text = nextText,
                )
            } else {
                WorkoutNotificationState.RestFinished(
                    title = "Rest finished",
                    text = nextText.removePrefix("Next: "),
                    setAction = nextSet?.toSetAction(workout.session.id, weightUnit),
                )
            }
        }

        return nextSet?.toSetAction(workout.session.id, weightUnit)
            ?: WorkoutNotificationState.WaitingToFinish(
                title = "Workout complete",
                text = "Finish workout in the app",
            )
    }

    fun nextActionableSet(
        activeWorkout: WorkoutSessionWithDetails,
    ): ActionableSet? = activeWorkout.findNextActionableSet()

    private fun WorkoutSessionWithDetails.findNextActionableSet(): ActionableSet? =
        exercises
            .sortedBy { it.exercise.sortOrderSnapshot }
            .toDisplayBlocks()
            .asSequence()
            .mapNotNull { block ->
                when (block) {
                    is DisplayBlock.SingleExercise -> block.exercise.firstPendingActionableSet()
                    is DisplayBlock.Superset -> block.nextSupersetSet()
                }
            }
            .firstOrNull()

    private fun List<SessionExerciseWithSets>.toDisplayBlocks(): List<DisplayBlock> {
        val seenSupersetGroups = mutableSetOf<Long>()
        val sessionExercises = this
        return buildList {
            sessionExercises.forEach { exercise ->
                val groupId = exercise.exercise.supersetGroupSnapshot
                if (groupId == null) {
                    add(DisplayBlock.SingleExercise(exercise))
                } else if (seenSupersetGroups.add(groupId)) {
                    add(
                        DisplayBlock.Superset(
                            exercises = sessionExercises.filter { it.exercise.supersetGroupSnapshot == groupId }
                                .sortedBy { it.exercise.sortOrderSnapshot },
                        ),
                    )
                }
            }
        }
    }

    private fun SessionExerciseWithSets.firstPendingActionableSet(): ActionableSet? =
        sets
            .sortedBy { it.setOrder }
            .firstOrNull { it.status == SessionSetStatus.PENDING }
            ?.toActionableSet(this)

    private fun DisplayBlock.Superset.nextSupersetSet(): ActionableSet? {
        val setOrders = exercises
            .flatMap { exercise -> exercise.sets.map { it.setOrder } }
            .distinct()
            .sorted()
        setOrders.forEach { setOrder ->
            exercises.forEach { exercise ->
                val set = exercise.sets
                    .firstOrNull { it.setOrder == setOrder && it.status == SessionSetStatus.PENDING }
                if (set != null) return set.toActionableSet(exercise)
            }
        }
        return null
    }

    private fun SessionSetEntity.toActionableSet(
        exercise: SessionExerciseWithSets,
    ): ActionableSet =
        ActionableSet(
            setId = id,
            exerciseName = exercise.exercise.exerciseNameSnapshot,
            trackingMode = exercise.exercise.trackingModeSnapshot,
            durationSeconds = prescribedDurationSeconds ?: exercise.exercise.targetDurationSecondsSnapshot,
            label = setLabel(this, exercise),
            prescribedWeightCentiKg = prescribedWeightCentiKg
                ?: exercise.exercise.prescribedWeightCentiKgSnapshot,
            prescribedReps = prescribedReps
                ?: exercise.exercise.targetRepsSnapshot,
        )

    private fun setLabel(
        set: SessionSetEntity,
        exercise: SessionExerciseWithSets,
    ): String = when (set.setType) {
        SetType.WORKING -> "Set ${set.setOrder + 1}/${exercise.exercise.plannedSetCountSnapshot}"
        SetType.WARMUP -> "Warm-up ${abs(set.setOrder)}"
        SetType.EXTRA -> "Extra"
        SetType.AMRAP -> "AMRAP"
        SetType.DROP -> "Drop"
    }

    private sealed interface DisplayBlock {
        data class SingleExercise(val exercise: SessionExerciseWithSets) : DisplayBlock
        data class Superset(val exercises: List<SessionExerciseWithSets>) : DisplayBlock
    }
}

data class ActionableSet(
    val setId: Long,
    val exerciseName: String,
    val label: String,
    val prescribedWeightCentiKg: Int,
    val prescribedReps: Int,
    val trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
    val durationSeconds: Int? = null,
) {
    val targetText: String
        get() = targetText(WeightUnit.KG)

    fun targetText(weightUnit: WeightUnit = WeightUnit.KG): String =
        trackingText(trackingMode, prescribedWeightCentiKg, prescribedReps, durationSeconds, weightUnit).replace("×", "x")

    fun toSetAction(sessionId: Long, weightUnit: WeightUnit = WeightUnit.KG): WorkoutNotificationState.SetAction =
        WorkoutNotificationState.SetAction(
            sessionId = sessionId,
            setId = setId,
            title = exerciseName,
            text = "$label • ${targetText(weightUnit)}",
            trackingMode = trackingMode,
        )
}
