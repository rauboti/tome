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
 * Create a character: `name` and the typed `data` are required — declared non-null so a body missing
 * either fails deserialization → 400. `data` is the typed base sheet ([CharacterBaseData]); its
 * `ruleSetId` selects the rule set (an unknown one fails to bind → 400), so no separate top-level
 * `ruleSetId` is sent (ADR-001). A partial sheet is fine — every base field defaults.
 */
data class CreateCharacterRequest(
    val name: String,
    val data: CharacterBaseData,
)

/**
 * Update a character sheet: the typed `data` (full base sheet) and `version` are required (non-null →
 * 400 if absent); `name` is optional (null keeps the current name). `version` carries optimistic
 * concurrency — a stale value comes back as 409 (SC-006). `data.ruleSetId` must match the character's
 * (the rule set is fixed for life, FR-002) or the service answers 400.
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
 * Full character projection (openapi `Character`): the promoted columns, the **enriched** sheet `data`
 * ([CharacterData] — base inputs plus derived values computed on read), the soft `warnings` from the
 * last validate, and the `version` to send back on the next write. HP lives inside `data` (in the
 * DnD35 sheet's `hitPoints` group), not as a promoted top-level field in v1.
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
 * REST surface for player characters (US1, openapi `/characters`). Behind the `/api` Tome-role gate
 * (SecurityConfig, T009); the caller's Hive subject (from the session-authenticated [Jwt]) is the
 * owner for every operation. All business rules — rule-set resolution, derived-value recompute, soft
 * warnings, and optimistic concurrency — live in [CharacterService]; this class only maps HTTP to it
 * and projects the result. Domain exceptions become RFC-7807 responses via the shared advice (T012):
 * `NotFoundException` → 404, `ForbiddenException` → 403, `BadRequestException` → 400,
 * `StaleVersionException` → 409.
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
            // Enrich the stored base into the served sheet (base inputs + derived) on read (ADR-001).
            data = character.data.enrich(),
            warnings = warnings,
            // A persisted character always carries a @Version (0 on insert, bumped on save).
            version = requireNotNull(character.version) { "a persisted character must have a version" },
        )
}
