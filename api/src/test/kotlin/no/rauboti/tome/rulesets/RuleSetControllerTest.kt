package no.rauboti.tome.rulesets

import no.rauboti.tome.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.util.UUID

/**
 * Contract test for the rule-set endpoints (T018, written before the controller in T019 — the 200
 * cases fail until it exists). Exercises the wired security chain + MockMvc against the openapi:
 *  - `GET /api/rule-sets`        → 200, an array of `RuleSetSummary { id, name }` (v1: just dnd35)
 *  - `GET /api/rule-sets/{id}`   → 200, the `SheetDefinition` for that rule set
 *  - unknown id                  → 404
 * All are data-API routes, so they require a Tome role (a `jwt()` caller with `ROLE_User`).
 */
@AutoConfigureMockMvc
class RuleSetControllerTest : IntegrationTest() {
    @Autowired private lateinit var mvc: MockMvc

    private fun user(vararg roles: String): RequestPostProcessor =
        jwt()
            .jwt { it.subject(UUID.randomUUID().toString()).claim("roles", roles.toList()) }
            .authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    @Test
    fun `listing rule sets requires authentication`() {
        mvc.get("/api/rule-sets").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `lists the bundled dnd35 rule set as a summary`() {
        mvc
            .get("/api/rule-sets") { with(user("User")) }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value("dnd35") }
                jsonPath("$[0].name") { isNotEmpty() }
            }
    }

    @Test
    fun `returns the full dnd35 sheet definition`() {
        mvc
            .get("/api/rule-sets/dnd35") { with(user("User")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.ruleSetId") { value("dnd35") }
                jsonPath("$.sections") { isNotEmpty() }
            }
    }

    @Test
    fun `an unknown rule set id returns 404`() {
        mvc
            .get("/api/rule-sets/does-not-exist") { with(user("User")) }
            .andExpect { status { isNotFound() } }
    }
}
