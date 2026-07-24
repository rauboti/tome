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
 * 409 — optimistic-concurrency conflict. Thrown when a write carries a `version` that no
 * longer matches the stored aggregate (a concurrent edit landed first); the client re-reads and
 * retries, so no write is silently overwritten.
 */
class StaleVersionException(
    message: String = "The resource was modified by someone else. Reload and try again.",
) : RuntimeException(message)
