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
    fun completedWorkoutRemainsVisibleUntilPhoneFinalizesIt() {
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

        assertEquals(WearSessionStatus.WORKOUT_COMPLETE, state.displayState?.sessionStatus)
    }

    @Test
    fun finishRequiresCompletedWorkoutConnectionAndNoPendingCommand() {
        val state = WearWorkoutUiState(workoutState = WorkoutWearState.workoutComplete(1, 0), connected = true)
        assertEquals(true, state.canFinish)
        assertEquals(false, state.copy(connected = false).canFinish)
        assertEquals(false, state.copy(pendingCommandId = "finish").canFinish)
        assertEquals(false, state.copy(workoutState = WorkoutWearState.noActive(0)).canFinish)
        assertEquals(false, state.copy(workoutState = state.workoutState!!.copy(sessionStatus = WearSessionStatus.ACTIVE)).canFinish)
    }
}
