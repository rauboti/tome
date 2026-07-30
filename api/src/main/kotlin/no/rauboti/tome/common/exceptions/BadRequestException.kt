package no.rauboti.tome.common.exceptions

/** 400 — the request is invalid (e.g. a rule-set mismatch when adding a character to a campaign). */
class BadRequestException(
    message: String,
) : RuntimeException(message)
