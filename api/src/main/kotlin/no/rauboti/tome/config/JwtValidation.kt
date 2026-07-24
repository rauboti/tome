package no.rauboti.tome.config

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators

/** Tome's own app slug — the audience its Hive tokens must carry (research D1/D6). */
const val TOME_AUDIENCE = "tome"

/**
 * Validators for a Hive-issued JWT: the Spring defaults (`exp`/`nbf`) plus `iss == issuer` and an
 * `aud` containing `tome`. Signature verification against the JWKS is the decoder's job.
 */
fun tomeJwtValidator(issuer: String): OAuth2TokenValidator<Jwt> =
    DelegatingOAuth2TokenValidator(
        JwtValidators.createDefaultWithIssuer(issuer),
        audienceValidator(),
    )

/** Fails unless the token's `aud` contains Tome's own slug (D6). */
private fun audienceValidator(): OAuth2TokenValidator<Jwt> =
    OAuth2TokenValidator { jwt ->
        if (TOME_AUDIENCE in jwt.audience.orEmpty()) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_token", "Required audience '$TOME_AUDIENCE' is missing", null),
            )
        }
    }

/**
 * Maps the token's `roles` claim to Spring's `ROLE_` convention so endpoints can use `hasRole("admin")`.
 * Values are Hive's lowercase app-role keys (`admin`/`user`) — casing must match what Hive stamps for
 * the `tome` app (display names live in Hive's catalog, not the token). Absent/empty → no
 * authorities, so a signed-in Hive user without a Tome grant is denied.
 */
class TomeJwtAuthoritiesConverter : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(jwt: Jwt): Collection<GrantedAuthority> =
        (jwt.getClaimAsStringList("roles") ?: emptyList()).map { SimpleGrantedAuthority("ROLE_$it") }
}
