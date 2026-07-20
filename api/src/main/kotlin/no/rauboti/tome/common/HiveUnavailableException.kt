package no.rauboti.tome.common

/**
 * 502 — an upstream dependency (Hive) is unreachable or returned an unusable response.
 * Thrown by the Hive token client on the "sign-in service unavailable" path; for a token
 * refresh it also signals the session can no longer be renewed silently (fall back to login).
 *
 * Kept in its own file so the RFC-7807 exception handler and the remaining domain exceptions
 * (StaleVersionException, etc.) added in T012 don't collide with this one.
 */
class HiveUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
