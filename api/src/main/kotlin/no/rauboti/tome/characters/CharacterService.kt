package no.rauboti.tome.characters

import no.rauboti.tome.characters.data.CharacterBaseData
import no.rauboti.tome.common.BadRequestException
import no.rauboti.tome.common.ForbiddenException
import no.rauboti.tome.common.NotFoundException
import no.rauboti.tome.common.StaleVersionException
import no.rauboti.tome.rulesets.RuleSet
import no.rauboti.tome.rulesets.RuleSetRegistry
import no.rauboti.tome.rulesets.RuleWarning
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * A [Character] paired with the soft [RuleWarning]s from validating its sheet. [Character.data]
 * is the stored base inputs; the controller enriches it for the response. Warnings are never persisted.
 */
data class CharacterWithWarnings(
    val character: Character,
    val warnings: List<RuleWarning>,
)

/**
 * Application logic for player characters. Writes store the typed [CharacterBaseData] as-is; reads
 * validate via the [RuleSet] and leave enrichment to the controller. The rule set is fixed for a
 * character's life — an update with a different `data.ruleSetId` is rejected — and a stale write
 * becomes [StaleVersionException] → 409. Authorization is owner-only in v1.
 */
@Service
class CharacterService(
    private val repository: CharacterRepository,
    private val ruleSets: RuleSetRegistry,
) {
    /** Create a character for [ownerId]; [data]'s `ruleSetId` selects the rule set (unknown → 400). */
    fun create(
        ownerId: UUID,
        name: String,
        data: CharacterBaseData,
    ): CharacterWithWarnings {
        val ruleSet = resolveForWrite(data.ruleSetId)
        val now = Instant.now()
        val stored =
            repository.insert(
                Character(
                    id = UUID.randomUUID(),
                    userId = ownerId,
                    ruleSetId = data.ruleSetId,
                    name = name,
                    data = data,
                    version = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        return CharacterWithWarnings(stored, ruleSet.validate(stored.data))
    }

    /** Get a character owned by [callerId] (404 if absent, 403 if owned by someone else). */
    fun get(
        id: UUID,
        callerId: UUID,
    ): CharacterWithWarnings {
        val character = requireOwned(id, callerId)
        val ruleSet = ruleSets.get(character.ruleSetId)
        return CharacterWithWarnings(character, ruleSet.validate(character.data))
    }

    /** The caller's own characters, for the list endpoint (summaries — no enrichment needed). */
    fun list(ownerId: UUID): List<Character> = repository.findByUserId(ownerId)

    /**
     * Replace a character's sheet ([data], full typed base) and optionally its [name], with optimistic
     * concurrency. A differing `data.ruleSetId` is a 400 (rule set fixed for life); a stale
     * [expectedVersion] is a 409.
     */
    fun update(
        id: UUID,
        callerId: UUID,
        name: String?,
        data: CharacterBaseData,
        expectedVersion: Int,
    ): CharacterWithWarnings {
        val existing = requireOwned(id, callerId)
        if (data.ruleSetId != existing.ruleSetId) {
            throw BadRequestException(
                "A character's rule set is fixed; cannot change it from '${existing.ruleSetId}' to '${data.ruleSetId}'.",
            )
        }
        val ruleSet = ruleSets.get(existing.ruleSetId)
        val toSave =
            existing.copy(
                name = name ?: existing.name,
                data = data,
                // Expected version so @Version rejects a stale write (SC-006).
                version = expectedVersion,
                updatedAt = Instant.now(),
            )
        val saved =
            try {
                repository.save(toSave)
            } catch (e: OptimisticLockingFailureException) {
                throw StaleVersionException()
            }
        return CharacterWithWarnings(saved, ruleSet.validate(saved.data))
    }

    /** Delete a character owned by [callerId] (404 if absent, 403 if owned by someone else). */
    fun delete(
        id: UUID,
        callerId: UUID,
    ) {
        requireOwned(id, callerId)
        repository.deleteById(id)
    }

    /** Load [id] and assert [callerId] owns it, else 404 (absent) / 403 (not the owner). */
    private fun requireOwned(
        id: UUID,
        callerId: UUID,
    ): Character {
        val character = repository.findById(id) ?: throw NotFoundException("Character '$id' not found.")
        if (character.userId != callerId) throw ForbiddenException("You do not have access to character '$id'.")
        return character
    }

    /** Resolve the rule set for a write; an unrecognized/unsupported rule set is a bad request (not 404). */
    private fun resolveForWrite(ruleSetId: String): RuleSet =
        ruleSets.all().firstOrNull { it.id() == ruleSetId }
            ?: throw BadRequestException("Unknown or unsupported rule set '$ruleSetId'.")
}
