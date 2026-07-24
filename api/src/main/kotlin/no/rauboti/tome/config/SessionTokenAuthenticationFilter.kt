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
 * Authenticates BFF API requests from the session-stored Hive access token, building a per-request
 * `SecurityContext` from its claims with the same [JwtDecoder]/validators and authorities converter a
 * direct resource server would use (so authorities and `hasRole(...)` checks are identical). An expired
 * token is refreshed silently server-side; if that also fails (refresh token expired/revoked, or Hive
 * unreachable), the dead tokens are dropped → 401 → the SPA restarts the Hive login.
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
        // AnonymousAuthenticationFilter runs first, so the context already holds an anonymous token;
        // treat that as "not yet authenticated" and still decode our session token, else every API
        // request stays anonymous → 401.
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
            // Refresh token expired/revoked or Hive unreachable — drop the dead tokens so the request
            // is unauthenticated (401 → SPA re-login).
            session.removeAttribute(SessionKeys.ACCESS_TOKEN)
            session.removeAttribute(SessionKeys.REFRESH_TOKEN)
            null
        }
    }
}
