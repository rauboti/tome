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
 * cases fail until it exists). Guards `GET /api/rule-sets` and `GET /api/rule-sets/{id}` returning
 * `RuleSetSummary { id, name }` (v1: just dnd35; ADR-001: the sheet is a typed schema known to the
 * client, not a fetched definition), unknown id → 404, and that all routes require a Tome role.
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
            .get("/api/rule-sets") { with(user("user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value("dnd35") }
                jsonPath("$[0].name") { isNotEmpty() }
            }
    }

    @Test
    fun `returns the dnd35 rule set summary`() {
        mvc
            .get("/api/rule-sets/dnd35") { with(user("user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value("dnd35") }
                jsonPath("$.name") { isNotEmpty() }
            }
    }

    @Test
    fun `an unknown rule set id returns 404`() {
        mvc
            .get("/api/rule-sets/does-not-exist") { with(user("user")) }
            .andExpect { status { isNotFound() } }
    }
}
