package com.nuvio.app.features.simkl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SimklPinResponse(
    val result: String? = null,
    val message: String? = null,
    @SerialName("device_code") val deviceCode: String? = null,
    @SerialName("user_code") val userCode: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("verification_url") val verificationUrl: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val interval: Int? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

internal data class SimklPendingPinAuthorization(
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresAtEpochMs: Long,
    val deviceCode: String? = null,
)

internal sealed interface SimklPinPollResult {
    data class Authorized(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresInSeconds: Long? = null,
    ) : SimklPinPollResult

    data object Pending : SimklPinPollResult
    data object Gone : SimklPinPollResult
    data object Failed : SimklPinPollResult
}

/** The Simkl PIN flow (older clients): the server requires result=OK. */
internal fun SimklPinResponse.toPendingAuthorization(
    nowEpochMs: Long,
): SimklPendingPinAuthorization? {
    if (!result.equals("OK", ignoreCase = true)) return null
    val code = userCode?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val url = verificationUri?.trim()?.takeIf(String::isNotEmpty)
        ?: verificationUrl?.trim()?.takeIf(String::isNotEmpty)
        ?: return null
    val lifetimeSeconds = expiresIn?.coerceAtLeast(1L) ?: 900L
    return SimklPendingPinAuthorization(
        userCode = code,
        verificationUrl = url,
        intervalSeconds = interval?.coerceAtLeast(1) ?: 5,
        expiresAtEpochMs = nowEpochMs + lifetimeSeconds * 1_000L,
    )
}

/**
 * The OAuth 2 device flow (current Simkl clients): the response has no result field, and
 * verification_uri_complete already contains the code.
 */
internal fun SimklPinResponse.toDeviceAuthorization(
    nowEpochMs: Long,
): SimklPendingPinAuthorization? {
    val code = userCode?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val url = verificationUriComplete?.trim()?.takeIf(String::isNotEmpty)
        ?: verificationUri?.trim()?.takeIf(String::isNotEmpty)
        ?: verificationUrl?.trim()?.takeIf(String::isNotEmpty)
        ?: return null
    val deviceCode = deviceCode?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val lifetimeSeconds = expiresIn?.coerceAtLeast(1L) ?: 900L
    return SimklPendingPinAuthorization(
        userCode = code,
        verificationUrl = url,
        intervalSeconds = interval?.coerceAtLeast(1) ?: 5,
        expiresAtEpochMs = nowEpochMs + lifetimeSeconds * 1_000L,
        deviceCode = deviceCode,
    )
}

internal fun SimklPinResponse.toPollResult(): SimklPinPollResult = when {
    !deviceCode.isNullOrBlank() && result.equals("OK", ignoreCase = true) -> SimklPinPollResult.Gone
    result.equals("OK", ignoreCase = true) && !accessToken.isNullOrBlank() ->
        SimklPinPollResult.Authorized(
            accessToken = accessToken.trim(),
            refreshToken = refreshToken?.trim()?.takeIf(String::isNotEmpty),
            expiresInSeconds = expiresIn,
        )
    result.equals("KO", ignoreCase = true) -> SimklPinPollResult.Pending
    else -> SimklPinPollResult.Failed
}

/** OAuth 2 token and device responses do not carry the legacy result field. */
internal fun SimklPinResponse.toDevicePollResult(): SimklPinPollResult {
    val token = accessToken?.trim()?.takeIf(String::isNotEmpty)
        ?: return SimklPinPollResult.Failed
    return SimklPinPollResult.Authorized(
        accessToken = token,
        refreshToken = refreshToken?.trim()?.takeIf(String::isNotEmpty),
        expiresInSeconds = expiresIn,
    )
}

internal fun isSimklPinAuthorizationExpired(
    expiresAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean = expiresAtEpochMs == null || nowEpochMs >= expiresAtEpochMs
