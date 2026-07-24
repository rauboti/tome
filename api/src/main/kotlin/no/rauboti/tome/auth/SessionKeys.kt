package no.rauboti.tome.auth

/**
 * Server-side session attribute keys for the Hive login (BFF; research D1/D6). Centralised here
 * because two collaborators share them: [no.rauboti.tome.config.SessionTokenAuthenticationFilter]
 * reads/renews [ACCESS_TOKEN]/[REFRESH_TOKEN] per request, and the AuthController (T011) writes all
 * four across the Authorization-Code + PKCE dance.
 */
object SessionKeys {
    /** CSRF `state` minted at `/auth/login`, verified at `/auth/callback` (one-time). */
    const val STATE = "tome.oauth.state"

    /** PKCE `code_verifier` minted at `/auth/login`, sent in the token exchange (one-time). */
    const val VERIFIER = "tome.oauth.verifier"

    /** The short-lived Hive access token — authenticates API requests via the session filter. */
    const val ACCESS_TOKEN = "tome.hive.accessToken"

    /** The rotating Hive refresh token — renews the access token silently, server-side. */
    const val REFRESH_TOKEN = "tome.hive.refreshToken"
}
