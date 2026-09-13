package com.jupiman.workouttracker.wearprotocol

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutWearCodecsTest {
    @Test
    fun stateRoundTripsThroughBytes() {
        val state = WorkoutWearState(
            sessionId = 7,
            sessionStatus = WearSessionStatus.ACTIVE,
            currentSetId = 11,
            exerciseName = "Bench Press",
            weightCentiKg = 7000,
            targetReps = 10,
            setLabel = "Set 2/3",
            setNumber = 2,
            totalSets = 3,
            restEndsAt = 123_456L,
            supersetPosition = 1,
            supersetSize = 2,
            stateVersion = 99,
            updatedAt = 100,
        )

        assertEquals(state, WorkoutWearCodecs.decodeState(WorkoutWearCodecs.encodeState(state)))
    }

    @Test
    fun commandAndAckRoundTripThroughBytes() {
        val command = CompleteSetCommand(
            sessionId = 1,
            sessionSetId = 2,
            commandId = "command-1",
            observedStateVersion = 3,
            createdAt = 4,
        )
        val ack = CommandAck(
            commandId = "command-1",
            accepted = true,
            stateVersion = 5,
            message = null,
        )

        assertEquals(command, WorkoutWearCodecs.decodeCompleteSetCommand(WorkoutWearCodecs.encodeCompleteSetCommand(command)))
        assertEquals(ack, WorkoutWearCodecs.decodeCommandAck(WorkoutWearCodecs.encodeCommandAck(ack)))
    }
}
