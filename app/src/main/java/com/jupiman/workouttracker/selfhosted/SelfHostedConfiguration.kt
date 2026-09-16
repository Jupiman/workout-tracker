package com.jupiman.workouttracker.selfhosted

import java.net.URI

data class SelfHostedConfiguration(val serverUrl: String, val apiToken: String)

sealed interface ServerUrlValidation {
    data class Valid(val normalizedUrl: String) : ServerUrlValidation
    data class Invalid(val message: String) : ServerUrlValidation
}

fun validateServerUrl(raw: String, allowLocalHttp: Boolean): ServerUrlValidation {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ServerUrlValidation.Invalid("Enter a server URL.")
    val uri = runCatching { URI(trimmed) }.getOrNull()
        ?: return ServerUrlValidation.Invalid("The server URL is invalid.")
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase()
    if (host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
        return ServerUrlValidation.Invalid("The server URL is invalid.")
    }
    val localHost = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host == "::1"
    if (scheme != "https" && !(allowLocalHttp && scheme == "http" && localHost)) {
        return ServerUrlValidation.Invalid("HTTPS is required for this server URL.")
    }
    val path = uri.rawPath.orEmpty().trimEnd('/')
    val authority = if (uri.port >= 0) "$host:${uri.port}" else host
    return ServerUrlValidation.Valid("$scheme://$authority$path")
}

fun compatiblePayloadSchema(minimum: Int, maximum: Int, schema: Int = PAYLOAD_SCHEMA_VERSION): Boolean =
    minimum <= schema && schema <= maximum

const val PAYLOAD_SCHEMA_VERSION = 1
const val SELF_HOSTED_BATCH_SIZE = 50
