package no.rauboti.tome.auth

import no.rauboti.tome.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.util.UUID

/**
 * Contract test for the BFF auth endpoints (T010). Written **before** the AuthController (T011),
 * so the 200/204 cases fail until it exists — the 401/403 cases already pass on the wired security
 * chain alone (SecurityConfig, T009). Exercises that real chain end-to-end via MockMvc; authenticated
 * callers use spring-security-test's `jwt()` post-processor, which pre-populates the SecurityContext
 * that [no.rauboti.tome.config.SessionTokenAuthenticationFilter] leaves intact (it only fills an
 * empty context), so authorities and `hasAnyRole(...)` are exercised exactly as in production.
 *
 * Contract (openapi `/auth/me`): 200 → `{ userId, roles, displayName?, locale? }`; `/auth/logout`
 * → 204. FR-024: a signed-in Hive user *without* a Tome role (Admin/User) is denied — `/api/auth/me`
 * returns **403**, not 200.
 */
@AutoConfigureMockMvc
class AuthControllerTest : IntegrationTest() {
    @Autowired private lateinit var mvc: MockMvc

    /** An authenticated caller: `sub` = [sub], a `roles` claim, and matching `ROLE_*` authorities. */
    private fun user(
        sub: UUID = UUID.randomUUID(),
        vararg roles: String,
    ): RequestPostProcessor =
        jwt()
            .jwt { it.subject(sub.toString()).claim("roles", roles.toList()) }
            .authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    @Test
    fun `me returns 401 when unauthenticated`() {
        mvc.get("/api/auth/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `me returns 403 when authenticated without a Tome role (FR-024)`() {
        mvc.get("/api/auth/me") { with(user()) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `me returns userId and roles for a User-role caller`() {
        val sub = UUID.randomUUID()
        mvc
            .get("/api/auth/me") { with(user(sub, "user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(sub.toString()) }
                jsonPath("$.roles[0]") { value("user") }
            }
    }

    @Test
    fun `me is accessible to an Admin-role caller (gate accepts Admin too)`() {
        mvc
            .get("/api/auth/me") { with(user(roles = arrayOf("admin"))) }
            .andExpect {
                status { isOk() }
                jsonPath("$.roles[0]") { value("admin") }
            }
    }

    @Test
    fun `logout returns 204 for an authenticated caller`() {
        mvc
            .post("/api/auth/logout") { with(user(roles = arrayOf("user"))) }
            .andExpect { status { isNoContent() } }
    }
}
