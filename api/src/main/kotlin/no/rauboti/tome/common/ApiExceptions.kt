package no.rauboti.tome.common

/** 404 — the requested resource does not exist. */
class NotFoundException(
    message: String,
) : RuntimeException(message)

/** 403 — the caller is authenticated but not permitted to perform the action. */
class ForbiddenException(
    message: String,
) : RuntimeException(message)

/** 400 — the request is invalid (e.g. a rule-set mismatch when adding a character to a campaign). */
class BadRequestException(
    message: String,
) : RuntimeException(message)

/**
 * 409 — optimistic-concurrency conflict (SC-006). Thrown when a write carries a `version` that no
 * longer matches the stored aggregate (a concurrent edit landed first). The client re-reads and
 * retries; no write is silently overwritten.
 *
 * [HiveUnavailableException] (502) lives in its own file and is handled by the same advice.
 */
class StaleVersionException(
    message: String = "The resource was modified by someone else. Reload and try again.",
) : RuntimeException(message)
