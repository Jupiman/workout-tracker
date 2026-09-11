package com.jupiman.workouttracker.data.local

import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTypeConvertersTest {
    private val converters = WorkoutTypeConverters()

    @Test
    fun workoutSessionStatusRoundTrips() {
        assertEquals(
            WorkoutSessionStatus.ACTIVE,
            converters.toWorkoutSessionStatus(converters.fromWorkoutSessionStatus(WorkoutSessionStatus.ACTIVE)),
        )
    }

    @Test
    fun sessionSetStatusRoundTrips() {
        assertEquals(
            SessionSetStatus.SKIPPED,
            converters.toSessionSetStatus(converters.fromSessionSetStatus(SessionSetStatus.SKIPPED)),
        )
    }

    @Test
    fun setTypeRoundTrips() {
        assertEquals(
            SetType.AMRAP,
            converters.toSetType(converters.fromSetType(SetType.AMRAP)),
        )
    }
}

