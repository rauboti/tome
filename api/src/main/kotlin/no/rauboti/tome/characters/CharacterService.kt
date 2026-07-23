package no.rauboti.tome.characters

import no.rauboti.tome.common.BadRequestException
import no.rauboti.tome.common.ForbiddenException
import no.rauboti.tome.common.NotFoundException
import no.rauboti.tome.common.StaleVersionException
import no.rauboti.tome.rulesets.FieldType
import no.rauboti.tome.rulesets.RuleSet
import no.rauboti.tome.rulesets.RuleSetRegistry
import no.rauboti.tome.rulesets.RuleWarning
import no.rauboti.tome.rulesets.SheetChange
import no.rauboti.tome.rulesets.SheetData
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * A [Character] paired with the soft [RuleWarning]s from validating its sheet (FR-005). The
 * character's [Character.data] here is the **resolved** sheet (base inputs + derived, per
 * [CharacterDataResolver]) ready for the response; warnings are guidance, never persisted, never a block.
 */
data class CharacterWithWarnings(
    val character: Character,
    val warnings: List<RuleWarning>,
)

/**
 * Application logic for player characters (US1), between the REST controller (T097) and the
 * [CharacterRepository]. Applies the Hybrid engine with **compute-on-read** (D8):
 *
 *  - **on write** — fields the rule set's definition marks `derived` are **stripped** before persisting,
 *    so the stored document holds base inputs only; derived values are never stored (FR-004/D8).
 *  - **on read/echo** — [CharacterDataResolver] recomputes the derived values, so GET and the POST/PUT
 *    echo return a fully resolved sheet.
 *  - **concurrency** — a stale write surfaces Spring Data's `OptimisticLockingFailureException`, which
 *    this service maps to [StaleVersionException] → `409` (SC-006).
 *
 * Authorization in v1 is owner-only; DM cross-campaign visibility arrives with the campaign
 * `PermissionService` (US2) and is not modelled here.
 */
@Service
class CharacterService(
    private val repository: CharacterRepository,
    private val ruleSets: RuleSetRegistry,
    private val resolver: CharacterDataResolver,
) {
    /**
     * Create a character for [ownerId] under [ruleSetId] (unknown id → 400; create has no 404). The
     * promoted [name] is seeded into the sheet's `name` field when the caller didn't supply one, so the
     * rendered "Name" field isn't blank; derived fields are stripped before the insert (base inputs only).
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
        val now = Instant.now()
        val stored =
            repository.insert(
                Character(
                    id = UUID.randomUUID(),
                    userId = ownerId,
                    ruleSetId = ruleSetId,
                    name = name,
                    data = stripDerived(seeded, ruleSet),
                    version = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        return toResolved(stored, ruleSet, SheetChange(previous = emptyMap(), changedFields = stored.data.keys))
    }

    /** Get a character owned by [callerId] (404 if absent, 403 if owned by someone else). */
    @Transactional(readOnly = true)
    fun get(
        id: UUID,
        callerId: UUID,
    ): CharacterWithWarnings {
        val character = requireOwned(id, callerId)
        val ruleSet = ruleSets.get(character.ruleSetId)
        return toResolved(character, ruleSet, SheetChange(previous = character.data, changedFields = emptySet()))
    }

    /** The caller's own characters, for the list endpoint (summaries — no sheet resolution needed). */
    @Transactional(readOnly = true)
    fun list(ownerId: UUID): List<Character> = repository.findByUserId(ownerId)

    /**
     * Replace a character's sheet ([data] is the full sheet) and optionally its [name], with optimistic
     * concurrency. Derived fields are stripped before persisting; a stale [expectedVersion] (a concurrent
     * edit already landed) becomes a `409` via [StaleVersionException].
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
        val baseInputs = stripDerived(data, ruleSet)
        val changed = (baseInputs.keys + existing.data.keys).filter { existing.data[it] != baseInputs[it] }.toSet()
        val toSave =
            existing.copy(
                name = name ?: existing.name,
                data = baseInputs,
                // Carry the caller's expected version so @Version rejects a stale write (SC-006).
                version = expectedVersion,
                updatedAt = Instant.now(),
            )
        val saved =
            try {
                repository.save(toSave)
            } catch (e: OptimisticLockingFailureException) {
                throw StaleVersionException()
            }
        return toResolved(saved, ruleSet, SheetChange(previous = existing.data, changedFields = changed))
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

    /**
     * Resolve the sheet on read: return [character] with its [Character.data] replaced by the resolved
     * sheet (base + derived) plus the soft warnings from validating it (scoped by [change]).
     */
    private fun toResolved(
        character: Character,
        ruleSet: RuleSet,
        change: SheetChange,
    ): CharacterWithWarnings {
        val resolved = resolver.resolve(character.data, ruleSet)
        val warnings = ruleSet.validate(resolved, change)
        return CharacterWithWarnings(character.copy(data = resolved), warnings)
    }

    /** Drop every field the [ruleSet] definition marks `derived`, leaving base inputs only (D8). */
    private fun stripDerived(
        data: SheetData,
        ruleSet: RuleSet,
    ): SheetData {
        val derivedIds =
            ruleSet
                .definition()
                .sections
                .flatMap { it.fields }
                .filter { it.type == FieldType.DERIVED }
                .mapTo(mutableSetOf()) { it.id }
        return data.filterKeys { it !in derivedIds }
    }

    /** Resolve the rule set for a create; an unrecognized id is a bad request (not a 404). */
    private fun resolveForWrite(ruleSetId: String): RuleSet =
        ruleSets.all().firstOrNull { it.id() == ruleSetId }
            ?: throw BadRequestException("Unknown rule set '$ruleSetId'.")
}
