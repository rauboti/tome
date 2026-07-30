package no.rauboti.tome.characters

import no.rauboti.tome.characters.data.enrich
import no.rauboti.tome.characters.dto.CharacterDto
import no.rauboti.tome.characters.dto.CompactCharacterDto
import no.rauboti.tome.characters.dto.CreateCharacterDto
import no.rauboti.tome.characters.dto.UpdateCharacterDto
import no.rauboti.tome.common.exceptions.BadRequestException
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
    ): List<CompactCharacterDto> = service.list(callerId(jwt)).map { CompactCharacterDto(it.id, it.name, it.ruleSetId) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody body: CreateCharacterDto,
    ): CharacterDto {
        if (body.name.isBlank()) throw BadRequestException("Character name must not be blank.")
        return service.create(callerId(jwt), body.name, body.data).toResponse()
    }

    @GetMapping("/{characterId}")
    fun get(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable characterId: UUID,
    ): CharacterDto = service.get(characterId, callerId(jwt)).toResponse()

    @PutMapping("/{characterId}")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable characterId: UUID,
        @RequestBody body: UpdateCharacterDto,
    ): CharacterDto {
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

    private fun CharacterWithWarnings.toResponse(): CharacterDto =
        CharacterDto(
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
