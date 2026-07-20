package no.rauboti.tome.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates the app's domain exceptions into RFC-7807 problem details. Spring serializes
 * [ProblemDetail] as `application/problem+json` ({type, title, status, detail}) — the shape the
 * openapi `Problem` schema and the frontend expect. Spring's own MVC exceptions also render as
 * problem details (spring.mvc.problemdetails.enabled), so the error envelope is uniform across
 * framework and domain errors.
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

    /** Optimistic-concurrency conflict → 409 (SC-006). */
    @ExceptionHandler(StaleVersionException::class)
    fun handleStaleVersion(ex: StaleVersionException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Version conflict")

    @ExceptionHandler(HiveUnavailableException::class)
    fun handleHiveUnavailable(ex: HiveUnavailableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.message ?: "Sign-in service unavailable")
}
