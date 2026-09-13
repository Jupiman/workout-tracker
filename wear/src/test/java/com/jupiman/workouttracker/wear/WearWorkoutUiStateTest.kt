package com.jupiman.workouttracker.wear

import com.jupiman.workouttracker.wearprotocol.WearSessionStatus
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState
import org.junit.Assert.assertEquals
import org.junit.Test

class WearWorkoutUiStateTest {
    @Test
    fun phoneNowAppliesEstimatedPhoneClockOffset() {
        val state = WearWorkoutUiState(
            now = 1_000L,
            phoneClockOffsetMillis = 10_000L,
        )

        assertEquals(11_000L, state.phoneNow)
    }

    @Test
    fun completedWorkoutTimeoutUsesPhoneAdjustedTime() {
        val completed = WorkoutWearState(
            sessionId = 1,
            sessionStatus = WearSessionStatus.WORKOUT_COMPLETE,
            currentSetId = null,
            exerciseName = null,
            weightCentiKg = null,
            targetReps = null,
            setLabel = null,
            setNumber = null,
            totalSets = null,
            restEndsAt = null,
            supersetPosition = null,
            supersetSize = null,
            stateVersion = 10_000L,
            updatedAt = 10_000L,
        )
        val state = WearWorkoutUiState(
            workoutState = completed,
            now = 1_000L,
            phoneClockOffsetMillis = 14_000L,
        )

        assertEquals(WearSessionStatus.NO_ACTIVE, state.displayState?.sessionStatus)
    }
}
