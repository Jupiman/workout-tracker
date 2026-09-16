package com.jupiman.workouttracker.wear

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionalWearOperationTest {
    @Test
    fun apiUnavailableFailureIsContained() = runTest {
        val failure = ApiException(Status(17, "Wearable.API is not available"))
        var reportedFailure: Throwable? = null

        val succeeded = runOptionalWearOperation(
            onFailure = { reportedFailure = it },
        ) {
            throw failure
        }

        assertFalse(succeeded)
        assertSame(failure, reportedFailure)
    }

    @Test
    fun successfulWearOperationIsPreserved() = runTest {
        var completed = false

        val succeeded = runOptionalWearOperation(onFailure = { error("Unexpected failure") }) {
            completed = true
        }

        assertTrue(succeeded)
        assertTrue(completed)
    }

    @Test(expected = CancellationException::class)
    fun coroutineCancellationIsNotSwallowed() = runTest {
        runOptionalWearOperation(onFailure = { error("Cancellation must not be reported as a Wear failure") }) {
            throw CancellationException("cancelled")
        }
    }
}
