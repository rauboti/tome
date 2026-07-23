package no.rauboti.tome.characters

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * `characters` collection access via [MongoTemplate] (no JPA — research D3/D5). Persistence only: the
 * rule-set logic, the resolve-on-read projection, and the `409` mapping live in the service (T096).
 *
 * Optimistic concurrency is Spring Data `@Version` (research D5), **not** a hand-rolled `WHERE version`:
 * [save] issues a versioned update and throws `OptimisticLockingFailureException` when the stored
 * version has moved on — the service maps that to `409` (SC-006) rather than overwriting a concurrent
 * edit. [insert] is for new documents (Spring assigns `version` `0`); the caller supplies the
 * id/timestamps on the [Character] (T096).
 */
@Repository
class CharacterRepository(
    private val mongo: MongoTemplate,
) {
    /** Insert a new character document (caller sets id/timestamps; `@Version` starts at 0). */
    fun insert(character: Character): Character = mongo.insert(character)

    /** The character with [id], or null if none exists. */
    fun findById(id: UUID): Character? = mongo.findById(id, Character::class.java)

    /** Every character owned by [userId], newest first (backs `GET /api/characters`). */
    fun findByUserId(userId: UUID): List<Character> =
        mongo.find(
            Query(Criteria.where("userId").`is`(userId)).with(Sort.by(Sort.Direction.DESC, "createdAt")),
            Character::class.java,
        )

    /**
     * Persist an existing character with the `@Version` optimistic-concurrency guard: a stale version
     * throws `OptimisticLockingFailureException` (→ 409) instead of overwriting a concurrent edit.
     */
    fun save(character: Character): Character = mongo.save(character)

    /** Delete the character with [id]; returns true if a document was removed. */
    fun deleteById(id: UUID): Boolean = mongo.remove(Query(Criteria.where("id").`is`(id)), Character::class.java).deletedCount > 0
}
