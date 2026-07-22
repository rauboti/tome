package no.rauboti.tome.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Security for the BFF (research D1/D6). Tome is a *consumer* of Hive: it validates Hive-issued
 * RS256 JWTs offline via Hive's JWKS. The browser holds only a session cookie — the token lives
 * server-side in the session — so API requests are authenticated by [SessionTokenAuthenticationFilter],
 * which decodes that token (same [JwtDecoder]/validators and authorities converter a resource
 * server would use) and silently refreshes it on expiry.
 *
 * The `SecurityContext` itself is stateless (rebuilt per request from the session token, never
 * persisted). URL model:
 *  - `/actuator/health` and the `/auth` login/callback handshake are **public** (the handshake
 *    *starts* a session, so needs none).
 *  - `POST /api/auth/logout` needs only a valid session, so a signed-in Hive user **without** a
 *    Tome grant can still sign out.
 *  - **everything else under `/api` — including `/api/auth/me` — requires a Tome app role
 *    (`Admin` or `User`)**. This is the deliberate divergence from taskmaster (which let any
 *    signed-in user read the `/api/auth` routes): FR-024 requires that a Hive user without a Tome
 *    role is denied, and the auth contract test (T010) asserts `/api/auth/me` returns **403** then.
 *  - unauthenticated calls answer with a plain **401** (no redirect), which the SPA turns into a
 *    Hive login.
 *
 * CORS is driven by `tome.cors.allowed-origins`.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    // Browser-reachable Hive base — also the token issuer (`iss`): Hive stamps its external URL,
    // so this is what the iss validator expects.
    @param:Value("\${tome.hive.external-url}") private val externalUrl: String,
    // Container-reachable Hive base — the JWKS endpoint is derived from it (server-side fetch).
    @param:Value("\${tome.hive.internal-url}") private val internalUrl: String,
    @param:Value("\${tome.cors.allowed-origins}") private val corsAllowedOrigins: List<String>,
) {
    /**
     * Decoder pointed at Hive's JWKS URI (`internal-url` + JWKS path; keys fetched + cached lazily
     * on first use, so no network call at startup), carrying Tome's claim validators — `iss` is
     * validated against the external URL Hive stamps into its tokens.
     */
    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder
            .withJwkSetUri("$internalUrl${HiveEndpoints.JWKS_PATH}")
            .build()
            .apply { setJwtValidator(tomeJwtValidator(externalUrl)) }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(TomeJwtAuthoritiesConverter())
        }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        sessionTokenAuthenticationFilter: SessionTokenAuthenticationFilter,
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Public: the login handshake starts a session, it doesn't require one.
                it.requestMatchers("/auth/login", "/auth/callback").permitAll()
                // Any signed-in Hive user may sign out, even without a Tome grant.
                it.requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                // Everything else under /api (incl. /api/auth/me) is gated on a Tome app role.
                // A signed-in Hive user without an admin/user grant gets a 403 (FR-024). Role keys
                // are Hive's lowercase app-role keys (`admin`/`user`) — the token's `roles` claim
                // carries the keys, not the display names (consistent with the other consumers).
                it.requestMatchers("/api/**").hasAnyRole("admin", "user")
                it.anyRequest().authenticated()
            }.exceptionHandling {
                // Unauthenticated API call → plain 401 (no redirect); the SPA starts a Hive login.
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }.addFilterBefore(sessionTokenAuthenticationFilter, AuthorizationFilter::class.java)
        return http.build()
    }

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = corsAllowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
