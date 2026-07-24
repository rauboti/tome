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
 * Security for the BFF. Tome validates Hive-issued RS256 JWTs offline via Hive's JWKS; the token
 * lives server-side in the session (browser holds only a cookie), so requests are authenticated by
 * [SessionTokenAuthenticationFilter] and the `SecurityContext` is stateless. The URL model and role
 * gating are documented in api/README.md; the inline comments below mark the why.
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
     * Decoder pointed at Hive's JWKS URI (`internal-url` + JWKS path; keys fetched lazily, no startup
     * network call), with Tome's validators — `iss` is checked against the external URL Hive stamps.
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
                // Everything else under /api (incl. /api/auth/me) needs a Tome role; a signed-in Hive
                // user without an admin/user grant gets 403 (FR-024). Keys are Hive's lowercase role
                // keys from the `roles` claim, not display names.
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
