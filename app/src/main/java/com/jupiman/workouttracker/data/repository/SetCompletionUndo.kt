package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.entity.SessionSetEntity

/** An identity-bound receipt for one phone completion, never an arbitrary set ID. */
class SetCompletionUndo internal constructor(
    internal val sessionId: Long,
    internal val previous: SessionSetEntity,
    internal val completed: SessionSetEntity,
) {
    val label: String get() = if (completed.setOrder >= 0) {
        "Set ${completed.setOrder + 1} completed"
    } else {
        "Warm-up completed"
    }
}
