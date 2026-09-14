package com.jupiman.workouttracker.wearprotocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object WorkoutWearPaths {
    const val STATE = "/workout_tracker/wear/state"
    const val REQUEST_STATE = "/workout_tracker/wear/request_state"
    const val COMPLETE_SET = "/workout_tracker/wear/complete_set"
    const val FINISH_WORKOUT = "/workout_tracker/wear/finish_workout"
    const val COMMAND_ACK = "/workout_tracker/wear/command_ack"
}

enum class WearSessionStatus {
    NO_ACTIVE,
    ACTIVE,
    WORKOUT_COMPLETE,
    UNAVAILABLE,
}

data class WorkoutWearState(
    val sessionId: Long?,
    val sessionStatus: WearSessionStatus,
    val currentSetId: Long?,
    val exerciseName: String?,
    val weightCentiKg: Int?,
    val targetReps: Int?,
    val setLabel: String?,
    val setNumber: Int?,
    val totalSets: Int?,
    val restEndsAt: Long?,
    val supersetPosition: Int?,
    val supersetSize: Int?,
    val stateVersion: Long,
    val updatedAt: Long,
) {
    val hasActionableSet: Boolean
        get() = sessionStatus == WearSessionStatus.ACTIVE && currentSetId != null

    companion object {
        fun noActive(now: Long): WorkoutWearState =
            WorkoutWearState(
                sessionId = null,
                sessionStatus = WearSessionStatus.NO_ACTIVE,
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
                stateVersion = now,
                updatedAt = now,
            )

        fun workoutComplete(sessionId: Long, now: Long): WorkoutWearState =
            WorkoutWearState(
                sessionId = sessionId,
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
                stateVersion = now,
                updatedAt = now,
            )
    }
}

data class CompleteSetCommand(
    val sessionId: Long,
    val sessionSetId: Long,
    val commandId: String,
    val observedStateVersion: Long,
    val createdAt: Long,
)

data class CommandAck(
    val commandId: String,
    val accepted: Boolean,
    val stateVersion: Long,
    val message: String?,
)

data class FinishWorkoutCommand(val sessionId: Long, val commandId: String)

object WorkoutWearCodecs {
    private const val VERSION = 1

    fun encodeFinishWorkoutCommand(command: FinishWorkoutCommand): ByteArray = writeBytes {
        writeInt(VERSION)
        writeLong(command.sessionId)
        writeUTF(command.commandId)
    }

    fun decodeFinishWorkoutCommand(bytes: ByteArray): FinishWorkoutCommand =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.requireVersion()
            FinishWorkoutCommand(input.readLong(), input.readUTF())
        }

    fun encodeState(state: WorkoutWearState): ByteArray =
        writeBytes {
            writeInt(VERSION)
            writeNullableLong(state.sessionId)
            writeInt(state.sessionStatus.ordinal)
            writeNullableLong(state.currentSetId)
            writeNullableString(state.exerciseName)
            writeNullableInt(state.weightCentiKg)
            writeNullableInt(state.targetReps)
            writeNullableString(state.setLabel)
            writeNullableInt(state.setNumber)
            writeNullableInt(state.totalSets)
            writeNullableLong(state.restEndsAt)
            writeNullableInt(state.supersetPosition)
            writeNullableInt(state.supersetSize)
            writeLong(state.stateVersion)
            writeLong(state.updatedAt)
        }

    fun decodeState(bytes: ByteArray): WorkoutWearState =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.requireVersion()
            WorkoutWearState(
                sessionId = input.readNullableLong(),
                sessionStatus = WearSessionStatus.entries[input.readInt()],
                currentSetId = input.readNullableLong(),
                exerciseName = input.readNullableString(),
                weightCentiKg = input.readNullableInt(),
                targetReps = input.readNullableInt(),
                setLabel = input.readNullableString(),
                setNumber = input.readNullableInt(),
                totalSets = input.readNullableInt(),
                restEndsAt = input.readNullableLong(),
                supersetPosition = input.readNullableInt(),
                supersetSize = input.readNullableInt(),
                stateVersion = input.readLong(),
                updatedAt = input.readLong(),
            )
        }

    fun encodeCompleteSetCommand(command: CompleteSetCommand): ByteArray =
        writeBytes {
            writeInt(VERSION)
            writeLong(command.sessionId)
            writeLong(command.sessionSetId)
            writeUTF(command.commandId)
            writeLong(command.observedStateVersion)
            writeLong(command.createdAt)
        }

    fun decodeCompleteSetCommand(bytes: ByteArray): CompleteSetCommand =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.requireVersion()
            CompleteSetCommand(
                sessionId = input.readLong(),
                sessionSetId = input.readLong(),
                commandId = input.readUTF(),
                observedStateVersion = input.readLong(),
                createdAt = input.readLong(),
            )
        }

    fun encodeCommandAck(ack: CommandAck): ByteArray =
        writeBytes {
            writeInt(VERSION)
            writeUTF(ack.commandId)
            writeBoolean(ack.accepted)
            writeLong(ack.stateVersion)
            writeNullableString(ack.message)
        }

    fun decodeCommandAck(bytes: ByteArray): CommandAck =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.requireVersion()
            CommandAck(
                commandId = input.readUTF(),
                accepted = input.readBoolean(),
                stateVersion = input.readLong(),
                message = input.readNullableString(),
            )
        }

    private fun writeBytes(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output -> output.block() }
            bytes.toByteArray()
        }

    private fun DataInputStream.requireVersion() {
        val version = readInt()
        require(version == VERSION) { "Unsupported Wear protocol version $version." }
    }

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }

    private fun DataInputStream.readNullableInt(): Int? =
        if (readBoolean()) readInt() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullableLong(): Long? =
        if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readUTF() else null
}
