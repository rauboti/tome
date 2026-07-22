package no.rauboti.tome.characters

import no.rauboti.tome.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

/**
 * Behavioural integration test for the character write path (T026, written **before** the
 * model/service/controller in T029–T031 — every case fails until they exist). Where the contract
 * test (T025) pins the API *shape*, this pins the *behaviour* end-to-end against the real Postgres
 * from [IntegrationTest] (Flyway-migrated Testcontainer), driving the wired HTTP stack via MockMvc:
 *  - a created sheet **persists** and reloads exactly (US1 independent test);
 *  - `RuleSet.computeDerived` runs **on every write** so derived values (ability mods, saves,
 *    initiative) are recomputed and returned/stored (FR-004);
 *  - a soft rule violation returns a **warning without blocking** the save (FR-005);
 *  - optimistic concurrency: a stale `version` write is refused with **409** and does **not**
 *    overwrite the winning edit (SC-006).
 *
 * All routes require a Tome role, so callers authenticate with a `jwt()` carrying `ROLE_User`; the
 * caller's `sub` owns the character.
 */
@AutoConfigureMockMvc
class CharacterIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun user(
        sub: UUID = UUID.randomUUID(),
        vararg roles: String,
    ): RequestPostProcessor =
        jwt()
            .jwt { it.subject(sub.toString()).claim("roles", roles.toList()) }
            .authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    /** POST a character owned by [owner] with the given raw-JSON `data` object; return the parsed body. */
    private fun postCharacter(
        owner: UUID,
        name: String,
        data: String,
    ): Map<String, Any?> {
        val body =
            mvc
                .post("/api/characters") {
                    with(user(owner, "user"))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"ruleSetId":"dnd35","name":"$name","data":$data}"""
                }.andReturn()
                .response.contentAsString
        return objectMapper.readValue(body)
    }

    private fun Map<String, Any?>.int(key: String): Int = (this[key] as Number).toInt()

    @Test
    fun `a created character persists and reloads exactly`() {
        val owner = UUID.randomUUID()
        val id = postCharacter(owner, "Aria", """{"level":3,"notes":"scout","feats":["Dodge"]}""")["id"]

        mvc
            .get("/api/characters/$id") { with(user(owner, "user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Aria") }
                jsonPath("$.ruleSetId") { value("dnd35") }
                // The promoted name is seeded into the sheet, so the rendered "Name" field is not blank.
                jsonPath("$.data.name") { value("Aria") }
                jsonPath("$.data.level") { value(3) }
                jsonPath("$.data.notes") { value("scout") }
                jsonPath("$.data.feats[0]") { value("Dodge") }
            }
    }

    @Test
    fun `derived values are recomputed and stored on create`() {
        val owner = UUID.randomUUID()
        val id =
            postCharacter(
                owner,
                "Bardo",
                """{"strength":16,"dexterity":14,"constitution":13,"wisdom":8,"fortBase":2,"willBase":1}""",
            )["id"]

        // GET proves the derived values were computed on write and round-trip through storage.
        mvc
            .get("/api/characters/$id") { with(user(owner, "user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.strMod") { value(3) } // floor((16-10)/2)
                jsonPath("$.data.dexMod") { value(2) } // floor((14-10)/2)
                jsonPath("$.data.conMod") { value(1) } // floor((13-10)/2)
                jsonPath("$.data.initiative") { value(2) } // = dexMod
                jsonPath("$.data.fortitude") { value(3) } // fortBase 2 + conMod 1
                jsonPath("$.data.will") { value(0) } // willBase 1 + wisMod -1
            }
    }

    @Test
    fun `editing recomputes derived values and persists across a reload`() {
        val owner = UUID.randomUUID()
        val created = postCharacter(owner, "Corvus", """{"strength":10}""")
        val id = created["id"]

        mvc
            .put("/api/characters/$id") {
                with(user(owner, "user"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"data":{"strength":18},"version":${created.int("version")}}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.strMod") { value(4) } // recomputed from the edit
                jsonPath("$.version") { value(created.int("version") + 1) }
            }

        mvc
            .get("/api/characters/$id") { with(user(owner, "user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.strength") { value(18) }
                jsonPath("$.data.strMod") { value(4) }
            }
    }

    @Test
    fun `a soft rule violation returns a warning but still saves`() {
        val owner = UUID.randomUUID()
        // Strength 0 is below the 3.5 minimum → a soft warning, but the write must still succeed.
        mvc
            .post("/api/characters") {
                with(user(owner, "user"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"ruleSetId":"dnd35","name":"Cursed","data":{"strength":0}}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.warnings[0].code") { value("ability.below-minimum") }
                jsonPath("$.warnings[0].field") { value("strength") }
                jsonPath("$.data.strength") { value(0) } // saved despite the warning
            }
    }

    @Test
    fun `a stale version write is refused with 409 and does not overwrite the winning edit`() {
        val owner = UUID.randomUUID()
        val created = postCharacter(owner, "Dahlia", """{"strength":10}""")
        val id = created["id"]
        val stale = created.int("version")

        // First edit wins, bumping the version.
        mvc
            .put("/api/characters/$id") {
                with(user(owner, "user"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"data":{"strength":12},"version":$stale}"""
            }.andExpect { status { isOk() } }

        // Second edit carries the now-stale version → rejected.
        mvc
            .put("/api/characters/$id") {
                with(user(owner, "user"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"data":{"strength":99},"version":$stale}"""
            }.andExpect { status { isConflict() } }

        // The winning edit stands; the stale write left no trace.
        mvc
            .get("/api/characters/$id") { with(user(owner, "user")) }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.strength") { value(12) }
            }
    }
}
