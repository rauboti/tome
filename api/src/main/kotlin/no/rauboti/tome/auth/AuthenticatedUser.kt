package no.rauboti.tome.auth

/**
 * The authenticated user as the SPA needs it — the `GET /api/auth/me` response (openapi schema:
 * `{ userId, roles, displayName?, locale? }`). Derived entirely from the Hive access token's claims.
 * `displayName` and `locale` are optional in the contract; they're null when the token carries no
 * such claim.
 */
data class AuthenticatedUser(
    val userId: String,
    val displayName: String?,
    val roles: List<String>,
    val locale: String?,
)
