package no.rauboti.tome.config

/**
 * Hive's OAuth/OIDC endpoint paths — fixed by Hive's contract, so they live here as constants
 * rather than as configuration (only the base URLs are configured). Combined with the two bases
 * they form the full endpoints:
 *  - authorize: `{external-url}` + [AUTHORIZE_PATH] — the browser is redirected here
 *  - token:     `{internal-url}` + [TOKEN_PATH]     — server-side exchange from the api container
 *  - JWKS:      `{internal-url}` + [JWKS_PATH]       — server-side key fetch for JWT validation
 */
object HiveEndpoints {
    const val AUTHORIZE_PATH = "/oauth2/authorize"
    const val TOKEN_PATH = "/oauth2/token"
    const val JWKS_PATH = "/.well-known/jwks.json"
}
