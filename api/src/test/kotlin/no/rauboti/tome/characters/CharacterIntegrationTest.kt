package no.rauboti.tome.characters

import com.mongodb.client.model.Filters
import no.rauboti.tome.support.IntegrationTest
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.mongodb.core.MongoTemplate
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
 * Behavioural integration test for the character write path (US1), driving the wired HTTP stack via
 * MockMvc against the real MongoDB from [IntegrationTest] (a single-node replica-set Testcontainer).
 * Pins the **compute-on-read** behaviour (D8):
 *  - a created sheet's **base inputs persist** and reload exactly (US1 independent test);
 *  - derived values (ability mods, saves, initiative) are **recomputed on read** and returned in the
 *    response, but the **raw stored document holds base inputs only — no derived fields** (D8/SC-021);
 *  - a soft rule violation returns a **warning without blocking** the save (FR-005);
 *  - optimistic concurrency: a stale `@Version` write is refused with **409** and does **not**
 *    overwrite the winning edit (SC-006).
 *
 * All routes require a Tome role, so callers authenticate with a `jwt()` carrying `ROLE_User`; the
 * caller's `sub` owns the character.
 */
@AutoConfigureMockMvc
class CharacterIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var mongo: MongoTemplate

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

    /** The raw stored `data` sub-document for [id], read straight from MongoDB (bypassing the resolver). */
    private fun storedData(id: String): Document {
        val doc = mongo.getCollection("characters").find(Filters.eq("_id", UUID.fromString(id))).first()
        assertNotNull(doc, "character '$id' should be persisted")
        return doc!!["data"] as Document
    }

    @Test
    fun `a created character persists and reloads exactly`() {
        val owner = UUID.randomUUID()
        // `feats` is a structured table (T109): a row is an object, not a bare string.
        val id = postCharacter(owner, "Aria", """{"level":3,"notes":"scout","feats":[{"name":"Dodge","type":"general"}]}""")["id"]

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
                jsonPath("$.data.feats[0].name") { value("Dodge") }
            }
    }

    @Test
    fun `derived values are computed on read and returned, but never stored`() {
        val owner = UUID.randomUUID()
        val id =
            postCharacter(
                owner,
                "Bardo",
                """{"strength":16,"dexterity":14,"constitution":13,"wisdom":8,"fortBase":2,"willBase":1}""",
            )["id"] as String

        // The GET response carries the resolved derived values (computed on read).
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

        // …but the raw stored document holds base inputs only — no derived fields (D8/SC-021).
        val stored = storedData(id)
        assertTrue(stored.containsKey("strength"), "base inputs must be stored")
        for (derived in listOf("strMod", "dexMod", "conMod", "wisMod", "fortitude", "reflex", "will", "initiative")) {
            assertFalse(stored.containsKey(derived), "derived '$derived' must not be stored")
        }
    }

    @Test
    fun `editing recomputes derived on read and persists the base inputs across a reload`() {
        val owner = UUID.randomUUID()
        val created = postCharacter(owner, "Corvus", """{"strength":10}""")
        val id = created["id"] as String

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

        // The edit persisted the base input; the recomputed derived is still not stored.
        val stored = storedData(id)
        assertTrue(stored.containsKey("strength"), "edited base input persists")
        assertFalse(stored.containsKey("strMod"), "derived must not be stored after an edit")
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
        val id = created["id"] as String
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
