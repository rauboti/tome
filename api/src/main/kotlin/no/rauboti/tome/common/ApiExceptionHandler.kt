package no.rauboti.tome.common

import no.rauboti.tome.common.exceptions.BadRequestException
import no.rauboti.tome.common.exceptions.ForbiddenException
import no.rauboti.tome.common.exceptions.NotFoundException
import no.rauboti.tome.common.exceptions.StaleVersionException
import no.rauboti.tome.common.exceptions.UnavailableException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates domain exceptions into RFC-7807 [ProblemDetail]s (`application/problem+json` —
 * {type, title, status, detail}), the shape the openapi `Problem` schema and the frontend expect.
 * Spring's own MVC exceptions render the same way (spring.mvc.problemdetails.enabled), so the error
 * envelope is uniform across framework and domain errors.
 */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Forbidden")

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    /** Optimistic-concurrency conflict → 409, raised by the character service. */
    @ExceptionHandler(StaleVersionException::class)
    fun handleStaleVersion(ex: StaleVersionException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Version conflict")

    /**
     * Framework-level safety net: any Spring Data `@Version` conflict → 409. The character write
     * path translates this to [StaleVersionException] in the service, so it never reaches here for
     * characters; other `@Version` aggregates (campaigns/encounters, US2+) that let it propagate
     * get a clean 409 with a curated detail rather than the driver's message.
     */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLocking(ex: OptimisticLockingFailureException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "The resource was modified by someone else. Reload and try again.",
        )

    @ExceptionHandler(UnavailableException::class)
    fun handleHiveUnavailable(ex: UnavailableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.message ?: "Sign-in service unavailable")
}
