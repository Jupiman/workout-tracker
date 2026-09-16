package com.jupiman.workouttracker.selfhosted

import java.net.URI

data class SelfHostedTransportPolicy(val allowsHttp: Boolean) {
    fun effectiveUseHttps(requestedUseHttps: Boolean): Boolean = !allowsHttp || requestedUseHttps

    companion object {
        val HTTP_ALLOWED = SelfHostedTransportPolicy(allowsHttp = true)
        val HTTPS_ONLY = SelfHostedTransportPolicy(allowsHttp = false)
    }
}

data class SelfHostedConfiguration(
    val serverAddress: String,
    val useHttps: Boolean,
    val apiToken: String,
) {
    val baseUrl: String
        get() = buildSelfHostedBaseUrl(serverAddress, useHttps)
}

sealed interface ServerAddressValidation {
    data class Valid(
        val serverAddress: String,
        val useHttps: Boolean,
    ) : ServerAddressValidation

    data class Invalid(val message: String) : ServerAddressValidation
}

/**
 * Normalizes an address and, when a full URL was pasted, treats its explicit scheme as authoritative.
 */
fun validateServerAddress(
    raw: String,
    useHttps: Boolean,
    transportPolicy: SelfHostedTransportPolicy = SelfHostedTransportPolicy.HTTP_ALLOWED,
): ServerAddressValidation {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ServerAddressValidation.Invalid("Enter a server address.")

    val explicitScheme = SCHEME_PREFIX.find(trimmed)?.value?.dropLast(3)?.lowercase()
    if (explicitScheme != null && explicitScheme !in setOf("http", "https")) {
        return ServerAddressValidation.Invalid("The server address is invalid.")
    }
    val requestedUseHttps = explicitScheme?.let { it == "https" } ?: useHttps
    val candidate = if (explicitScheme == null) {
        buildSelfHostedBaseUrl(trimmed, requestedUseHttps)
    } else {
        trimmed
    }
    val uri = runCatching { URI(candidate) }.getOrNull()
        ?: return ServerAddressValidation.Invalid("The server address is invalid.")
    val host = uri.host
    if (
        host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null ||
        uri.port !in -1..65535 ||
        uri.scheme?.lowercase() !in setOf("http", "https")
    ) {
        return ServerAddressValidation.Invalid("The server address is invalid.")
    }

    val authority = uri.rawAuthority?.lowercase()
        ?: return ServerAddressValidation.Invalid("The server address is invalid.")
    val path = uri.rawPath.orEmpty().trimEnd('/')
    return ServerAddressValidation.Valid(
        authority + path,
        transportPolicy.effectiveUseHttps(requestedUseHttps),
    )
}

fun buildSelfHostedBaseUrl(serverAddress: String, useHttps: Boolean): String =
    "${if (useHttps) "https" else "http"}://${serverAddress.trim().trimEnd('/')}"

fun compatiblePayloadSchema(minimum: Int, maximum: Int, schema: Int = PAYLOAD_SCHEMA_VERSION): Boolean =
    minimum <= schema && schema <= maximum

const val PAYLOAD_SCHEMA_VERSION = 1
const val SELF_HOSTED_BATCH_SIZE = 50

private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
