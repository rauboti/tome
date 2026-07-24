package no.rauboti.tome.characters

import no.rauboti.tome.characters.data.CharacterBaseData
import no.rauboti.tome.characters.data.CharacterData
import no.rauboti.tome.characters.data.enrich
import no.rauboti.tome.common.BadRequestException
import no.rauboti.tome.rulesets.RuleWarning
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Create a character. `name` and the typed base `data` are non-null, so a body missing either fails
 * deserialization → 400; `data.ruleSetId` selects the rule set (unknown → 400). A partial sheet is
 * fine — every base field defaults.
 */
data class CreateCharacterRequest(
    val name: String,
    val data: CharacterBaseData,
)

/**
 * Update a character sheet. `data` (full base sheet) and `version` are required; `name` is optional
 * (null keeps the current). `version` carries optimistic concurrency (stale → 409); `data.ruleSetId`
 * must match the character's or the service answers 400 (FR-002).
 */
data class UpdateCharacterRequest(
    val name: String? = null,
    val data: CharacterBaseData,
    val version: Int,
)

/** List/roster projection of a character (openapi `CharacterSummary`). */
data class CharacterSummaryResponse(
    val id: UUID,
    val name: String,
    val ruleSetId: String,
)

/**
 * Full character projection (openapi `Character`): the enriched sheet `data` ([CharacterData] — base
 * inputs plus derived), the soft `warnings` from the last validate, and the `version` to send on the
 * next write. HP lives inside `data` (the DnD35 `hitPoints` group), not as a top-level field in v1.
 */
data class CharacterResponse(
    val id: UUID,
    val name: String,
    val ruleSetId: String,
    val ownerId: UUID,
    val data: CharacterData,
    val warnings: List<RuleWarning>,
    val version: Int,
)

/**
 * REST surface for player characters (US1, openapi `/characters`). Behind the `/api` Tome-role gate;
 * the caller's Hive subject (from the session-authenticated [Jwt]) owns every operation. Business
 * rules live in [CharacterService] — this class maps HTTP and projects the result; domain exceptions
 * become RFC-7807 responses via the shared advice.
 */
@RestController
@RequestMapping("/api/characters")
class CharacterController(
    private val service: CharacterService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
    ): List<CharacterSummaryResponse> = service.list(callerId(jwt)).map { CharacterSummaryResponse(it.id, it.name, it.ruleSetId) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody body: CreateCharacterRequest,
    ): CharacterResponse {
        if (body.name.isBlank()) throw BadRequestException("Character name must not be blank.")
        return service.create(callerId(jwt), body.name, body.data).toResponse()
    }

    @GetMapping("/{characterId}")
    fun get(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable characterId: UUID,
    ): CharacterResponse = service.get(characterId, callerId(jwt)).toResponse()

    @PutMapping("/{characterId}")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable characterId: UUID,
        @RequestBody body: UpdateCharacterRequest,
    ): CharacterResponse {
        if (body.name != null && body.name.isBlank()) throw BadRequestException("Character name must not be blank.")
        return service.update(characterId, callerId(jwt), body.name, body.data, body.version).toResponse()
    }

    @DeleteMapping("/{characterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable characterId: UUID,
    ) = service.delete(characterId, callerId(jwt))

    /** The caller's Hive subject as a UUID (the `user_id`/owner of every character in v1). */
    private fun callerId(jwt: Jwt): UUID = UUID.fromString(requireNotNull(jwt.subject) { "Hive token is missing the subject claim." })

    private fun CharacterWithWarnings.toResponse(): CharacterResponse =
        CharacterResponse(
            id = character.id,
            name = character.name,
            ruleSetId = character.ruleSetId,
            ownerId = character.userId,
            // Enrich base → served sheet (base + derived) on read (ADR-001).
            data = character.data.enrich(),
            warnings = warnings,
            version = requireNotNull(character.version) { "a persisted character must have a version" },
        )
}
