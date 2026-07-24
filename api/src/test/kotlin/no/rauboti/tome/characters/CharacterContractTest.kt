package no.rauboti.tome.characters

import no.rauboti.tome.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

/**
 * Contract test for the character endpoints (T025, written **before** the model/service/controller
 * in T029–T031 — the 201/200/204 cases fail until they exist; the `401` cases already pass on the
 * wired security chain from T009). Exercises the real chain + MockMvc against the openapi
 * `/characters` paths:
 *  - `GET  /api/characters`        → 200, an array of `CharacterSummary { id, name, ruleSetId }`
 *  - `POST /api/characters`        → 201, a `Character` (owner = the caller's Hive subject); 400 on
 *                                    a body missing the required `ruleSetId`/`name`
 *  - `GET  /api/characters/{id}`   → 200, the full `Character` (sheet `data` + `warnings`); 404 unknown
 *  - `PUT  /api/characters/{id}`   → 200 with the bumped `version` (optimistic concurrency); 409 stale
 *  - `DELETE /api/characters/{id}` → 204, then the resource is gone (404)
 *
 * All are data-API routes, so they require a Tome role (a `jwt()` caller with `ROLE_User`); the
 * caller's `sub` is the owning user. Each authenticated case runs against the real MongoDB from
 * [IntegrationTest], so created documents persist across the request chain within a test. The openapi
 * response shapes are unchanged by the compute-on-read re-platform (the `Character`/`CharacterSummary`
 * contracts stay identical); this test pins that.
 */
@AutoConfigureMockMvc
class CharacterContractTest : IntegrationTest() {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var objectMapper: ObjectMapper

    /** An authenticated caller: `sub` = [sub], a `roles` claim, and matching `ROLE_*` authorities. */
    private fun user(
        sub: UUID = UUID.randomUUID(),
        vararg roles: String,
    ): RequestPostProcessor =
        jwt()
            .jwt { it.subject(sub.toString()).claim("roles", roles.toList()) }
            .authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    /** Create a character as [owner] and return its generated id (parsed from the 201 body). */
    private fun createCharacter(
        owner: UUID,
        name: String = "Alaric",
    ): String {
        val body =
            mvc
                .post("/api/characters") {
                    with(user(owner, "user"))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"$name","data":{"ruleSetId":"dnd35","abilities":{"strength":16}}}"""
                }.andReturn()
                .response.contentAsString
        val created: Map<String, Any?> = objectMapper.readValue(body)
        return created["id"] as String
    }

    @Test
    fun `creating a character requires authentication`() {
        mvc
            .post("/api/characters") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"ruleSetId":"dnd35","name":"Alaric"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `listing characters requires authentication`() {
        mvc.get("/api/characters").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `creating a character returns 201 with the character owned by the caller`() {
        val sub = UUID.randomUUID()
        mvc
            .post("/api/characters") {
                with(user(sub, "user"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Alaric","data":{"ruleSetId":"dnd35","abilities":{"strength":16}}}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.name") { value("Alaric") }
                jsonPath("$.ruleSetId") { value("dnd35") }
                jsonPath("$.ownerId") { value(sub.toString()) }
                jsonPath("$.version") { isNumber() }
            }
    }

    @Test
    fun `creating a character without the required fields returns 400`() {
        mvc
            .post("/api/characters") {
                with(user(roles = arrayOf("user")))
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `creating a character with an unknown rule set returns 400`() {
        mvc
            .post("/api/characters") {
                with(user(roles = arrayOf("user")))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Alaric","data":{"ruleSetId":"pathfinder","abilities":{"strength":16}}}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `listing returns the caller's own characters`() {
        val sub = UUID.randomUUID()
        val id = createCharacter(sub, name = "Brenna")
        mvc
            .get("/api/characters") { with(user(sub, "user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$[?(@.id == '$id')].name") { value("Brenna") }
                jsonPath("$[?(@.id == '$id')].ruleSetId") { value("dnd35") }
            }
    }

    @Test
    fun `getting a character returns its full sheet with computed values and warnings`() {
        val sub = UUID.randomUUID()
        val id = createCharacter(sub)
        mvc
            .get("/api/characters/$id") { with(user(sub, "user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(id) }
                jsonPath("$.data") { isMap() }
                jsonPath("$.data.abilities.strMod") { value(3) } // enriched derived (str 16 → +3)
                jsonPath("$.warnings") { isArray() }
                jsonPath("$.version") { isNumber() }
            }
    }

    @Test
    fun `getting an unknown character returns 404`() {
        mvc
            .get("/api/characters/${UUID.randomUUID()}") { with(user(roles = arrayOf("user"))) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `updating a character with the read version returns 200 and bumps the version`() {
        val sub = UUID.randomUUID()
        val id = createCharacter(sub)
        mvc
            .put("/api/characters/$id") {
                with(user(sub, "user"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Alaric the Bold","data":{"ruleSetId":"dnd35","abilities":{"strength":18}},"version":0}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Alaric the Bold") }
                jsonPath("$.version") { value(1) }
            }
    }

    @Test
    fun `a stale version update returns 409`() {
        val sub = UUID.randomUUID()
        val id = createCharacter(sub)
        mvc
            .put("/api/characters/$id") {
                with(user(sub, "user"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"data":{"ruleSetId":"dnd35","abilities":{"strength":18}},"version":99}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `deleting a character returns 204 and then it is gone`() {
        val sub = UUID.randomUUID()
        val id = createCharacter(sub)
        mvc
            .delete("/api/characters/$id") { with(user(sub, "user")) }
            .andExpect { status { isNoContent() } }
        mvc
            .get("/api/characters/$id") { with(user(sub, "user")) }
            .andExpect { status { isNotFound() } }
    }
}
