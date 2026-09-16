package com.jupiman.workouttracker.selfhosted

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun ConnectionTestResult.displayMessage(): String = when (this) {
    is ConnectionTestResult.Success -> "Connected to server $serverVersion."
    is ConnectionTestResult.InvalidUrl -> message
    is ConnectionTestResult.Unreachable -> message
    is ConnectionTestResult.TlsFailure -> message
    ConnectionTestResult.Unauthorized -> "Authentication failed."
    ConnectionTestResult.Incompatible -> "Server API is incompatible."
    is ConnectionTestResult.Unexpected -> message
}

fun formatSelfHostedLastSync(timestamp: Long?, locale: Locale = Locale.getDefault()): String =
    timestamp?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(it))
    } ?: "Never"
