package no.rauboti.tome.catalogs

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
 * Contract test for the catalog endpoint (T113): `GET /api/rule-sets/{id}/catalogs/{catalog}?filter=`,
 * which backs a catalog-backed select picker (fetched by a typed sheet component). Exercises the wired security chain + MockMvc:
 *  - requires a Tome role (401 without)
 *  - dnd35 `spells` filtered by class → 200, an array of `{ value, label, meta.level }`
 *  - unknown catalog → 404
 */
@AutoConfigureMockMvc
class CatalogControllerTest : IntegrationTest() {
    @Autowired private lateinit var mvc: MockMvc

    private fun user(vararg roles: String): RequestPostProcessor =
        jwt()
            .jwt { it.subject(UUID.randomUUID().toString()).claim("roles", roles.toList()) }
            .authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    @Test
    fun `the catalog endpoint requires authentication`() {
        mvc.get("/api/rule-sets/dnd35/catalogs/spells?filter=wizard").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `returns the class-filtered spell options with per-class level`() {
        mvc
            .get("/api/rule-sets/dnd35/catalogs/spells?filter=wizard") { with(user("user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$") { isArray() }
                jsonPath("$") { isNotEmpty() }
                jsonPath("$[0].value") { isNotEmpty() }
                jsonPath("$[0].label") { isNotEmpty() }
                // Every wizard option carries its spell level in meta.
                jsonPath("$[0].meta.level") { isNumber() }
                // Fireball is a wizard spell at level 3.
                jsonPath("$[?(@.value=='fireball')].meta.level") { value(3) }
            }
    }

    @Test
    fun `an unknown catalog returns 404`() {
        mvc
            .get("/api/rule-sets/dnd35/catalogs/does-not-exist?filter=wizard") { with(user("user")) }
            .andExpect { status { isNotFound() } }
    }
}
