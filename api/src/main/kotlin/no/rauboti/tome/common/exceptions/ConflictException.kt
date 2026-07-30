package no.rauboti.tome.common.exceptions

/**
 * 409 — the request conflicts with the target's current state, as opposed to being malformed (400).
 * Raised by the campaign service for a roster add that clashes with the campaign (a rule-set mismatch,
 * or a character already on that roster) and for archiving an already-archived campaign — the
 * `409`s the openapi contract documents for those paths.
 *
 * Distinct from [StaleVersionException], which is specifically an optimistic-concurrency `version`
 * clash: that one means "re-read and retry", this one means "the request will never succeed as-is".
 */
class ConflictException(
    message: String,
) : RuntimeException(message)
