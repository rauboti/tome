package no.rauboti.tome.auth

/**
 * The `GET /api/auth/me` response (openapi: `{ userId, roles, displayName?, locale? }`), derived from
 * the Hive access token's claims. `displayName`/`locale` are null when the token carries no such claim.
 */
data class AuthenticatedUser(
    val userId: String,
    val displayName: String?,
    val roles: List<String>,
    val locale: String?,
)
