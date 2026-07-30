package no.rauboti.tome.common.exceptions

/** 404 — the requested resource does not exist. */
class NotFoundException(
    message: String,
) : RuntimeException(message)
