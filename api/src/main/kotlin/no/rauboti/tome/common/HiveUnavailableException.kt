package no.rauboti.tome.common

/**
 * 502 — an upstream dependency (Hive) is unreachable or returned an unusable response. Thrown by the
 * Hive token client; for a token refresh it also signals the session can't be renewed silently (fall
 * back to login).
 */
class HiveUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
