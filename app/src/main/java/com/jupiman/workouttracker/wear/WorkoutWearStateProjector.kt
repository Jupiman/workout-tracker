package com.jupiman.workouttracker.wear

import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.wearprotocol.WearSessionStatus
import com.jupiman.workouttracker.wearprotocol.WearTrackingMode
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState
import kotlin.math.abs

object WorkoutWearStateProjector {
    fun stateFor(
        activeWorkout: WorkoutSessionWithDetails?,
        now: Long = System.currentTimeMillis(),
    ): WorkoutWearState {
        val workout = activeWorkout ?: return WorkoutWearState.noActive(now)
        val currentSet = workout.findNextActionableSet()
            ?: return WorkoutWearState.workoutComplete(
                sessionId = workout.session.id,
                now = now,
            )

        return WorkoutWearState(
            sessionId = workout.session.id,
            sessionStatus = WearSessionStatus.ACTIVE,
            currentSetId = currentSet.set.id,
            exerciseName = currentSet.exercise.exercise.exerciseNameSnapshot,
            weightCentiKg = if (currentSet.exercise.exercise.trackingModeSnapshot == TrackingMode.WEIGHT_REPS)
                currentSet.set.prescribedWeightCentiKg ?: currentSet.exercise.exercise.prescribedWeightCentiKgSnapshot else null,
            targetReps = if (currentSet.exercise.exercise.trackingModeSnapshot != TrackingMode.DURATION)
                currentSet.set.prescribedReps ?: currentSet.exercise.exercise.targetRepsSnapshot else null,
            trackingMode = when (currentSet.exercise.exercise.trackingModeSnapshot) {
                TrackingMode.WEIGHT_REPS -> WearTrackingMode.WEIGHT_REPS
                TrackingMode.REPS -> WearTrackingMode.REPS
                TrackingMode.DURATION -> WearTrackingMode.DURATION
            },
            targetDurationSeconds = currentSet.set.prescribedDurationSeconds
                ?: currentSet.exercise.exercise.targetDurationSecondsSnapshot,
            setLabel = setLabel(currentSet.set, currentSet.exercise),
            setNumber = setNumber(currentSet.set),
            totalSets = totalSets(currentSet.set, currentSet.exercise),
            restEndsAt = workout.session.restEndsAt,
            durationStartsAt = workout.session.durationStartsAt,
            durationEndsAt = workout.session.durationEndsAt,
            supersetPosition = currentSet.supersetPosition,
            supersetSize = currentSet.supersetSize,
            stateVersion = now,
            updatedAt = now,
        )
    }

    private fun WorkoutSessionWithDetails.findNextActionableSet(): CurrentWearSet? =
        exercises
            .sortedBy { it.exercise.sortOrderSnapshot }
            .toDisplayBlocks()
            .asSequence()
            .mapNotNull { block ->
                when (block) {
                    is DisplayBlock.SingleExercise -> block.exercise.firstPendingActionableSet(
                        supersetPosition = null,
                        supersetSize = null,
                    )
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
                            exercises = sessionExercises
                                .filter { it.exercise.supersetGroupSnapshot == groupId }
                                .sortedBy { it.exercise.sortOrderSnapshot },
                        ),
                    )
                }
            }
        }
    }

    private fun SessionExerciseWithSets.firstPendingActionableSet(
        supersetPosition: Int?,
        supersetSize: Int?,
    ): CurrentWearSet? =
        sets
            .sortedBy { it.setOrder }
            .firstOrNull { it.status == SessionSetStatus.PENDING }
            ?.let { set ->
                CurrentWearSet(
                    exercise = this,
                    set = set,
                    supersetPosition = supersetPosition,
                    supersetSize = supersetSize,
                )
            }

    private fun DisplayBlock.Superset.nextSupersetSet(): CurrentWearSet? {
        val setOrders = exercises
            .flatMap { exercise -> exercise.sets.map { it.setOrder } }
            .distinct()
            .sorted()
        setOrders.forEach { setOrder ->
            exercises.forEachIndexed { index, exercise ->
                val set = exercise.sets
                    .firstOrNull { it.setOrder == setOrder && it.status == SessionSetStatus.PENDING }
                if (set != null) {
                    return CurrentWearSet(
                        exercise = exercise,
                        set = set,
                        supersetPosition = index + 1,
                        supersetSize = exercises.size,
                    )
                }
            }
        }
        return null
    }

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

    private fun setNumber(set: SessionSetEntity): Int? =
        when (set.setType) {
            SetType.WORKING -> set.setOrder + 1
            SetType.WARMUP -> abs(set.setOrder)
            SetType.EXTRA,
            SetType.AMRAP,
            SetType.DROP -> null
        }

    private fun totalSets(
        set: SessionSetEntity,
        exercise: SessionExerciseWithSets,
    ): Int? =
        when (set.setType) {
            SetType.WORKING -> exercise.exercise.plannedSetCountSnapshot
            SetType.WARMUP,
            SetType.EXTRA,
            SetType.AMRAP,
            SetType.DROP -> null
        }

    private sealed interface DisplayBlock {
        data class SingleExercise(val exercise: SessionExerciseWithSets) : DisplayBlock
        data class Superset(val exercises: List<SessionExerciseWithSets>) : DisplayBlock
    }

    private data class CurrentWearSet(
        val exercise: SessionExerciseWithSets,
        val set: SessionSetEntity,
        val supersetPosition: Int?,
        val supersetSize: Int?,
    )
}
