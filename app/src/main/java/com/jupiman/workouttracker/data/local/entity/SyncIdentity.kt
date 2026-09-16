package com.jupiman.workouttracker.data.local.entity

import java.util.UUID

fun newSyncId(): String = UUID.randomUUID().toString()

enum class SelfHostedSyncState {
    PENDING,
    SYNCED,
    FAILED_PERMANENT,
}
