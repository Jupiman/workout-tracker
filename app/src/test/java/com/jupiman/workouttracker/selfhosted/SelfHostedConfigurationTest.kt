package com.jupiman.workouttracker.selfhosted

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SelfHostedConfigurationTest {
    @Test
    fun normalizesHttpsAndRejectsUnsafeProductionUrls() {
        assertEquals(
            ServerUrlValidation.Valid("https://workout.example.com/base"),
            validateServerUrl("  https://workout.example.com/base///  ", allowLocalHttp = false),
        )
        assertTrue(validateServerUrl("http://workout.example.com", allowLocalHttp = true) is ServerUrlValidation.Invalid)
        assertTrue(validateServerUrl("http://localhost:8080", allowLocalHttp = false) is ServerUrlValidation.Invalid)
        assertEquals(
            ServerUrlValidation.Valid("http://10.0.2.2:8080"),
            validateServerUrl("http://10.0.2.2:8080/", allowLocalHttp = true),
        )
    }

    @Test
    fun compatibilityRequiresPayloadSchemaInsideAdvertisedRange() {
        assertTrue(compatiblePayloadSchema(1, 1))
        assertTrue(compatiblePayloadSchema(0, 2))
        assertFalse(compatiblePayloadSchema(2, 3))
        assertFalse(compatiblePayloadSchema(0, 0))
    }

    @Test
    fun statusFormattingIsDeterministicAndHumanReadable() {
        assertEquals("Never", formatSelfHostedLastSync(null, Locale.US))
        assertTrue(formatSelfHostedLastSync(0L, Locale.US).contains("1970"))
        assertEquals("Authentication failed.", ConnectionTestResult.Unauthorized.displayMessage())
        assertEquals("Connected to server 1.0.0.", ConnectionTestResult.Success("1.0.0").displayMessage())
    }
}
