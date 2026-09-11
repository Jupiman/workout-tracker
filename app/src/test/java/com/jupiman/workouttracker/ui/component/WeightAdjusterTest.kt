package com.jupiman.workouttracker.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightAdjusterTest {
    @Test
    fun upperBoundDefaultsTo150Kg() {
        assertEquals(15_000, upperBoundBucketFor(weightCentiKg = 0))
        assertEquals(15_000, upperBoundBucketFor(weightCentiKg = 10_000))
        assertEquals(15_000, upperBoundBucketFor(weightCentiKg = 15_000))
    }

    @Test
    fun upperBoundExpandsToNextBucket() {
        assertEquals(20_000, upperBoundBucketFor(weightCentiKg = 15_001))
        assertEquals(30_000, upperBoundBucketFor(weightCentiKg = 20_001))
        assertEquals(50_000, upperBoundBucketFor(weightCentiKg = 30_001))
    }

    @Test
    fun upperBoundDoesNotShrinkBelowCurrentBucket() {
        assertEquals(
            30_000,
            upperBoundBucketFor(
                weightCentiKg = 12_500,
                currentUpperBoundCentiKg = 30_000,
            ),
        )
    }

    @Test
    fun upperBoundCapsAtLargestBucket() {
        assertEquals(50_000, upperBoundBucketFor(weightCentiKg = 75_000))
    }
}
