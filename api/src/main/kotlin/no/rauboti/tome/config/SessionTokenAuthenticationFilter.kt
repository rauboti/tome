package no.rauboti.tome.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import no.rauboti.tome.auth.HiveTokenClient
import no.rauboti.tome.auth.SessionKeys
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates BFF API requests from the **session-stored** Hive access token — the browser
 * holds only the session cookie, so the token never leaves the server (BFF; research D1/D6).
 * Each request decodes the stored access token and builds a per-request `SecurityContext` from
 * its claims, reusing the very same [JwtDecoder]/validators and authorities converter a direct
 * resource server would (so authorities and `hasRole(...)` checks are identical).
 *
 * If the access token has expired, it is refreshed **silently, server-side** with the stored
 * refresh token — the user's work survives the access-token TTL with no browser round-trip.
 * If the refresh also fails (refresh token expired/revoked, or Hive unreachable), the dead
 * tokens are dropped and the request stays unauthenticated → 401 → the SPA restarts the Hive
 * login.
 */
@Component
class SessionTokenAuthenticationFilter(
    private val jwtDecoder: JwtDecoder,
    private val jwtAuthenticationConverter: JwtAuthenticationConverter,
    private val hiveTokenClient: HiveTokenClient,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val session = request.getSession(false)
        val accessToken = session?.getAttribute(SessionKeys.ACCESS_TOKEN) as? String
        // Spring Security's AnonymousAuthenticationFilter runs before this one, so the context
        // already holds a (non-null) anonymous token by now. Treat that as "not yet authenticated"
        // and still decode our session token — otherwise every API request stays anonymous → 401.
        val existing = SecurityContextHolder.getContext().authentication
        if (accessToken != null && (existing == null || existing is AnonymousAuthenticationToken)) {
            validAccessJwt(accessToken, session)?.let { jwt ->
                SecurityContextHolder.getContext().authentication = jwtAuthenticationConverter.convert(jwt)
            }
        }
        filterChain.doFilter(request, response)
    }

    /** Decode the stored access token, silently refreshing it once if it no longer validates. */
    private fun validAccessJwt(
        accessToken: String,
        session: HttpSession,
    ): Jwt? =
        try {
            jwtDecoder.decode(accessToken)
        } catch (expiredOrInvalid: JwtException) {
            refreshAndDecode(session)
        }

    private fun refreshAndDecode(session: HttpSession): Jwt? {
        val refreshToken = session.getAttribute(SessionKeys.REFRESH_TOKEN) as? String ?: return null
        return try {
            val tokens = hiveTokenClient.refresh(refreshToken)
            session.setAttribute(SessionKeys.ACCESS_TOKEN, tokens.accessToken)
            session.setAttribute(SessionKeys.REFRESH_TOKEN, tokens.refreshToken)
            jwtDecoder.decode(tokens.accessToken)
        } catch (refreshFailed: RuntimeException) {
            // Refresh token expired/revoked or Hive unreachable — the session can't be renewed.
            // Drop the dead tokens so the request is unauthenticated (401 → SPA re-login).
            session.removeAttribute(SessionKeys.ACCESS_TOKEN)
            session.removeAttribute(SessionKeys.REFRESH_TOKEN)
            null
        }
    }
}
