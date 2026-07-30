package no.rauboti.tome.characters

import no.rauboti.tome.characters.domain.Character
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * `characters` collection access via [MongoTemplate] (no JPA). Persistence only; the rule-set logic
 *  and `409` mapping live in the service. Optimistic concurrency is Spring Data `@Version`: [save]
 *  issues a versioned update and throws `OptimisticLockingFailureException` on a stale version (→ `409`)
 *  rather than overwriting; [insert] is for new documents (Spring assigns `version` `0`), the caller
 *  supplying id/timestamps.
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
