package no.rauboti.tome.common.exceptions

/**
 * 409 — optimistic-concurrency conflict. Thrown when a write carries a `version` that no
 * longer matches the stored aggregate (a concurrent edit landed first); the client re-reads and
 * retries, so no write is silently overwritten.
 */
class StaleVersionException(
    message: String = "The resource was modified by someone else. Reload and try again.",
) : RuntimeException(message)
