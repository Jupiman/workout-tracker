package com.jupiman.workouttracker.selfhosted

import com.jupiman.workouttracker.data.local.dao.WorkoutSessionDao
import com.jupiman.workouttracker.data.local.entity.SelfHostedSyncState
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails

interface SelfHostedWorkoutStore {
    suspend fun pending(limit: Int): List<WorkoutSessionWithDetails>
    suspend fun finalized(limit: Int, offset: Int): List<WorkoutSessionWithDetails>
    suspend fun pendingCount(): Int
    suspend fun permanentFailureCount(): Int
    suspend fun updateState(id: Long, state: SelfHostedSyncState, attemptAt: Long?, error: String?)
}

class RoomSelfHostedWorkoutStore(private val dao: WorkoutSessionDao) : SelfHostedWorkoutStore {
    override suspend fun pending(limit: Int) = dao.getPendingForSelfHostedSync(limit)
    override suspend fun finalized(limit: Int, offset: Int) = dao.getFinalizedForSelfHostedSync(limit, offset)
    override suspend fun pendingCount() = dao.countPendingSelfHostedSync()
    override suspend fun permanentFailureCount() = dao.countPermanentSelfHostedFailures()
    override suspend fun updateState(id: Long, state: SelfHostedSyncState, attemptAt: Long?, error: String?) =
        dao.updateSelfHostedSyncState(id, state, attemptAt, error)
}
