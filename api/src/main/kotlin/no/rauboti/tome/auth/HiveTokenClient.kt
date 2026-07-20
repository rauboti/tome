package no.rauboti.tome.auth

import no.rauboti.tome.common.HiveUnavailableException
import no.rauboti.tome.config.HiveEndpoints
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/** A Hive token pair: the short-lived access token and the rotating refresh token. */
data class HiveTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Talks to Hive's `POST {internal-url}/oauth2/token` (`client_secret_post`; research D1):
 * [exchange] redeems an authorization code (Authorization-Code + PKCE), [refresh] renews
 * a session with the rotating refresh token. Both return the new [HiveTokens]. Any
 * transport failure or unusable response is surfaced as [HiveUnavailableException] — the
 * "Hive unreachable" path; for a refresh it also signals the session can no longer be
 * renewed silently (fall back to login).
 */
interface HiveTokenClient {
    fun exchange(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): HiveTokens

    fun refresh(refreshToken: String): HiveTokens
}

@Component
class RestClientHiveTokenClient(
    @param:Value("\${tome.hive.internal-url}") internalUrl: String,
    @param:Value("\${tome.hive.client-id}") private val clientId: String,
    @param:Value("\${tome.hive.client-secret}") private val clientSecret: String,
) : HiveTokenClient {
    private val restClient = RestClient.builder().baseUrl(internalUrl).build()

    override fun exchange(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): HiveTokens =
        postToken(
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", code)
                add("redirect_uri", redirectUri)
                add("code_verifier", codeVerifier)
            },
        )

    override fun refresh(refreshToken: String): HiveTokens =
        postToken(
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
            },
        )

    private fun postToken(form: MultiValueMap<String, String>): HiveTokens {
        form.add("client_id", clientId)
        form.add("client_secret", clientSecret)
        val body: Map<*, *> =
            try {
                restClient
                    .post()
                    .uri(HiveEndpoints.TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map::class.java)
            } catch (e: RestClientException) {
                // Transport failure (ResourceAccessException) or non-2xx (RestClientResponseException,
                // e.g. an invalid_grant on a dead refresh token) — Hive is unusable for this call.
                throw HiveUnavailableException("Hive token request failed", e)
            } ?: throw HiveUnavailableException("Empty response from Hive token endpoint")

        val access =
            body["access_token"] as? String
                ?: throw HiveUnavailableException("Hive token response missing access_token")
        val refresh =
            body["refresh_token"] as? String
                ?: throw HiveUnavailableException("Hive token response missing refresh_token")
        return HiveTokens(access, refresh)
    }
}
