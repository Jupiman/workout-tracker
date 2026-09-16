package com.jupiman.workouttracker.selfhosted

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SelfHostedConfigurationTest {
    @Test
    fun constructsHttpAndHttpsUrlsFromNormalizedAddresses() {
        assertEquals(
            "http://192.168.1.140:5544",
            buildSelfHostedBaseUrl("192.168.1.140:5544", useHttps = false),
        )
        assertEquals(
            "https://192.168.1.140:5544",
            buildSelfHostedBaseUrl("192.168.1.140:5544", useHttps = true),
        )
        assertEquals("https://workout.example.com", buildSelfHostedBaseUrl("workout.example.com", true))
    }

    @Test
    fun normalizesAddressAndUsesPastedScheme() {
        assertEquals(
            ServerAddressValidation.Valid("192.168.1.140:5544", false),
            validateServerAddress(" 192.168.1.140:5544/ ", useHttps = false),
        )
        assertEquals(
            ServerAddressValidation.Valid("192.168.1.140:5544", false),
            validateServerAddress("http://192.168.1.140:5544/", useHttps = true),
        )
        assertEquals(
            ServerAddressValidation.Valid("workout.example.com", true),
            validateServerAddress("https://workout.example.com/", useHttps = false),
        )
        assertTrue(validateServerAddress("https://user@example.com", true) is ServerAddressValidation.Invalid)
        assertTrue(validateServerAddress("not a host", true) is ServerAddressValidation.Invalid)
        assertTrue(validateServerAddress("workout.example.com:70000", true) is ServerAddressValidation.Invalid)
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
