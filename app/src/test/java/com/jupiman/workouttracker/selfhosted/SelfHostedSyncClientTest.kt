package com.jupiman.workouttracker.selfhosted

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SelfHostedSyncClientTest {
    @Test
    fun testConnectionUsesTheExplicitProtocolWithoutFallback() = runTest {
        val opened = mutableListOf<URL>()
        val client = HttpSelfHostedSyncClient { url ->
            opened += url
            SuccessfulInfoConnection(url)
        }

        assertEquals(
            ConnectionTestResult.Success("1.0.0"),
            client.testConnection(SelfHostedConfiguration("192.168.1.140:5544", false, "token")),
        )
        assertEquals(
            ConnectionTestResult.Success("1.0.0"),
            client.testConnection(SelfHostedConfiguration("workout.example.com", true, "token")),
        )

        assertEquals(
            listOf(
                "http://192.168.1.140:5544/api/v1/info",
                "https://workout.example.com/api/v1/info",
            ),
            opened.map(URL::toString),
        )
    }

    @Test
    fun tlsErrorsAreReportedOnlyForHttpsConfiguration() = runTest {
        val client = HttpSelfHostedSyncClient(::FailingConnection)

        assertEquals(
            ConnectionTestResult.TlsFailure(),
            client.testConnection(SelfHostedConfiguration("workout.example.com", true, "token")),
        )
        assertEquals(
            ConnectionTestResult.Unreachable(),
            client.testConnection(SelfHostedConfiguration("192.168.1.140:5544", false, "token")),
        )
    }

    private class SuccessfulInfoConnection(url: URL) : HttpURLConnection(url) {
        private val response = """
            {
              "serverVersion":"1.0.0",
              "apiVersion":1,
              "minimumPayloadSchemaVersion":1,
              "maximumPayloadSchemaVersion":1
            }
        """.trimIndent().toByteArray()

        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(response)
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

    private class FailingConnection(url: URL) : HttpURLConnection(url) {
        override fun getResponseCode(): Int = throw SSLException("handshake failed")
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }
}
