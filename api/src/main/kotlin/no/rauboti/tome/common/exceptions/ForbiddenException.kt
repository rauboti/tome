package no.rauboti.tome.common.exceptions

/** 403 — the caller is authenticated but not permitted to perform the action. */
class ForbiddenException(
    message: String,
) : RuntimeException(message)
