package no.rauboti.tome.characters

import no.rauboti.tome.common.BadRequestException
import no.rauboti.tome.common.ForbiddenException
import no.rauboti.tome.common.NotFoundException
import no.rauboti.tome.common.StaleVersionException
import no.rauboti.tome.rulesets.RuleSet
import no.rauboti.tome.rulesets.RuleSetRegistry
import no.rauboti.tome.rulesets.RuleWarning
import no.rauboti.tome.rulesets.SheetChange
import no.rauboti.tome.rulesets.SheetData
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * A stored [Character] paired with the soft [RuleWarning]s from validating its current sheet
 * (FR-005). Warnings are guidance, never persisted and never a block — they ride alongside the row so
 * the controller (T031) can surface them in the `Character` response.
 */
data class CharacterWithWarnings(
    val character: Character,
    val warnings: List<RuleWarning>,
)

/**
 * Application logic for player characters (US1). Sits between the REST controller (T031) and the
 * [CharacterRepository] (T029), and is where the Hybrid engine is applied: every write runs the
 * rule set's [RuleSet.computeDerived] (so stored derived values never drift, FR-004) and
 * [RuleSet.validate] (soft warnings that never block, FR-005), and carries optimistic concurrency —
 * a stale [update] surfaces as [StaleVersionException] → 409 (SC-006).
 *
 * Authorization in v1 is owner-only: a character is readable/writable solely by its owner. DM
 * cross-campaign visibility arrives with the campaign `PermissionService` (US2, T040) and is not
 * modelled here.
 */
@Service
class CharacterService(
    private val repository: CharacterRepository,
    private val ruleSets: RuleSetRegistry,
) {
    /**
     * Create a character for [ownerId] under [ruleSetId]. The rule set must be one the engine knows
     * (else a 400 — the create contract has no 404); derived values are computed before the insert.
     *
     * The promoted [name] column mirrors the sheet's `name` field (data-model.md), so seed it into
     * the sheet [data] when the caller didn't supply one — otherwise the rendered "Name" field would
     * be blank even though the character has a name (the create form collects only the name, not the
     * whole sheet). An explicit `data.name` from the caller is left untouched.
     */
    @Transactional
    fun create(
        ownerId: UUID,
        ruleSetId: String,
        name: String,
        data: SheetData,
    ): CharacterWithWarnings {
        val ruleSet = resolveForWrite(ruleSetId)
        val seeded = if (data.containsKey("name")) data else data + ("name" to name)
        val computed = ruleSet.computeDerived(seeded)
        val warnings = ruleSet.validate(computed, SheetChange(previous = emptyMap(), changedFields = computed.keys))
        return CharacterWithWarnings(repository.insert(ownerId, ruleSetId, name, computed), warnings)
    }

    /** Get a character owned by [callerId] (404 if absent, 403 if owned by someone else). */
    @Transactional(readOnly = true)
    fun get(
        id: UUID,
        callerId: UUID,
    ): CharacterWithWarnings = withWarnings(requireOwned(id, callerId))

    /** The caller's own characters, for the list endpoint (summaries — no warnings needed). */
    @Transactional(readOnly = true)
    fun list(ownerId: UUID): List<Character> = repository.findByUserId(ownerId)

    /**
     * Replace a character's sheet ([data] is the full sheet) and optionally its [name], with
     * optimistic concurrency. Derived values are recomputed; a stale [expectedVersion] (a concurrent
     * edit already landed) becomes a 409 via [StaleVersionException].
     */
    @Transactional
    fun update(
        id: UUID,
        callerId: UUID,
        name: String?,
        data: SheetData,
        expectedVersion: Int,
    ): CharacterWithWarnings {
        val existing = requireOwned(id, callerId)
        // Rule set is fixed for a character's life (FR-002), so the existing one always resolves.
        val ruleSet = ruleSets.get(existing.ruleSetId)
        val computed = ruleSet.computeDerived(data)
        val changed = (computed.keys + existing.data.keys).filter { existing.data[it] != computed[it] }.toSet()
        val warnings = ruleSet.validate(computed, SheetChange(previous = existing.data, changedFields = changed))
        val updated =
            repository.update(id, name ?: existing.name, computed, expectedVersion)
                ?: throw StaleVersionException()
        return CharacterWithWarnings(updated, warnings)
    }

    /** Delete a character owned by [callerId] (404 if absent, 403 if owned by someone else). */
    @Transactional
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

    /** Attach the current sheet's soft warnings (a read, so there is no change to scope them to). */
    private fun withWarnings(character: Character): CharacterWithWarnings {
        val ruleSet = ruleSets.get(character.ruleSetId)
        val warnings =
            ruleSet.validate(character.data, SheetChange(previous = character.data, changedFields = emptySet()))
        return CharacterWithWarnings(character, warnings)
    }

    /** Resolve the rule set for a create; an unrecognized id is a bad request (not a 404). */
    private fun resolveForWrite(ruleSetId: String): RuleSet =
        ruleSets.all().firstOrNull { it.id() == ruleSetId }
            ?: throw BadRequestException("Unknown rule set '$ruleSetId'.")
}
