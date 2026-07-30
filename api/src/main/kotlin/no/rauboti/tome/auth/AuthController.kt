package no.rauboti.tome.auth

import jakarta.servlet.http.HttpSession
import no.rauboti.tome.common.exceptions.UnavailableException
import no.rauboti.tome.config.HiveEndpoints
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.security.MessageDigest

/**
 * The BFF's Hive login + identity endpoints. `/auth/login` starts the Authorization-Code + PKCE
 * handshake; `/auth/callback` verifies `state`, exchanges the code, and stores the token pair on the
 * session. A failed exchange (Hive unreachable) redirects to the SPA with `?error=signin_unavailable`
 * rather than a raw problem+json page mid-redirect. `/auth/login`/`/auth/callback` are public; `me`
 * requires a Tome role (roleless Hive user gets 403, FR-024) and `logout` only a session.
 */
@RestController
class AuthController(
    private val hiveTokenClient: HiveTokenClient,
    @param:Value("\${tome.hive.external-url}") private val externalUrl: String,
    @param:Value("\${tome.hive.client-id}") private val clientId: String,
    @param:Value("\${tome.web.base-url}") private val webBaseUrl: String,
) {
    /**
     * OAuth redirect URI = web origin + [CALLBACK_PATH], derived (not separately configured) so it
     * can't drift from [webBaseUrl] or the callback mapping. Must match the URI registered in Hive.
     */
    private val redirectUri = "$webBaseUrl$CALLBACK_PATH"

    @GetMapping("/auth/login")
    fun login(session: HttpSession): ResponseEntity<Void> {
        val verifier = Pkce.randomToken()
        val state = Pkce.randomToken()
        session.setAttribute(SessionKeys.STATE, state)
        session.setAttribute(SessionKeys.VERIFIER, verifier)

        val authorize =
            UriComponentsBuilder
                .fromUriString("$externalUrl${HiveEndpoints.AUTHORIZE_PATH}")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .queryParam("code_challenge", Pkce.challenge(verifier))
                .queryParam("code_challenge_method", "S256")
                .encode()
                .toUriString()
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authorize)).build()
    }

    @GetMapping(CALLBACK_PATH)
    fun callback(
        @RequestParam code: String,
        @RequestParam state: String,
        session: HttpSession,
    ): ResponseEntity<Void> {
        val expectedState = session.getAttribute(SessionKeys.STATE) as? String
        val verifier = session.getAttribute(SessionKeys.VERIFIER) as? String
        if (expectedState == null || verifier == null || !constantTimeEquals(state, expectedState)) {
            // Controller-local handshake guard → problem+json 400 (spring.mvc.problemdetails), not
            // the domain exception hierarchy (T012).
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or missing OAuth state.")
        }
        // One-time use: drop the challenge material before the exchange.
        session.removeAttribute(SessionKeys.STATE)
        session.removeAttribute(SessionKeys.VERIFIER)

        // Both tokens stay server-side; the access token authenticates API calls, the refresh renews it.
        val tokens =
            try {
                hiveTokenClient.exchange(code, verifier, redirectUri)
            } catch (_: UnavailableException) {
                // Browser redirect, so a 502 problem+json would land as a raw error page; bounce to
                // the SPA with a marker it renders as "sign-in unavailable, try again".
                return redirectToSpa(mapOf("error" to SIGNIN_UNAVAILABLE))
            }
        session.setAttribute(SessionKeys.ACCESS_TOKEN, tokens.accessToken)
        session.setAttribute(SessionKeys.REFRESH_TOKEN, tokens.refreshToken)

        return redirectToSpa()
    }

    /**
     * Invalidates the session, dropping the server-side tokens; the SPA then treats the user as logged
     * out. (Hive has no consumer token-revoke endpoint yet, so the refresh token stays valid there
     * until it expires — but it's discarded here and never reachable again.)
     */
    @PostMapping("/api/auth/logout")
    fun logout(session: HttpSession): ResponseEntity<Void> {
        session.invalidate()
        return ResponseEntity.noContent().build()
    }

    /**
     * The current authenticated user, from the session-authenticated Hive token (principal set by
     * [no.rauboti.tome.config.SessionTokenAuthenticationFilter]). Unauthenticated callers get 401 from
     * the security chain first; a roleless Hive user is stopped with 403 by the `/api` gate (FR-024).
     */
    @GetMapping("/api/auth/me")
    fun me(
        @AuthenticationPrincipal jwt: Jwt,
    ): AuthenticatedUser =
        AuthenticatedUser(
            userId = requireNotNull(jwt.subject) { "Hive token is missing the subject claim." },
            displayName = jwt.getClaimAsString("name"),
            roles = jwt.getClaimAsStringList("roles") ?: emptyList(),
            // Optional UI locale; only the openapi enum values (nb/en) are surfaced.
            locale = jwt.getClaimAsString("locale")?.takeIf { it == "nb" || it == "en" },
        )

    /** 302 to the SPA, optionally with query params (e.g. the sign-in-unavailable marker). */
    private fun redirectToSpa(query: Map<String, String> = emptyMap()): ResponseEntity<Void> {
        val target =
            UriComponentsBuilder
                .fromUriString(webBaseUrl)
                .apply { query.forEach { (k, v) -> queryParam(k, v) } }
                .encode()
                .toUriString()
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build()
    }

    /** Timing-safe compare so a mismatched `state` can't be probed byte-by-byte. */
    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        /** The BFF's OAuth callback path. Shared by the [callback] mapping and the derived
         *  [redirectUri] so the two can't diverge. */
        const val CALLBACK_PATH = "/auth/callback"

        /** Marker the SPA login screen renders as a "sign-in unavailable" message. */
        const val SIGNIN_UNAVAILABLE = "signin_unavailable"
    }
}
