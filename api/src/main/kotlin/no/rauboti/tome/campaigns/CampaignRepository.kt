package no.rauboti.tome.campaigns

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * `campaigns` collection access via [MongoTemplate] (no JPA). Persistence only; rule-set / permission
 * logic, roster dedup, and `409` mapping live in the service. Optimistic concurrency on the aggregate
 * is Spring Data `@Version` ([save] throws `OptimisticLockingFailureException` on a stale version).
 * Roster changes are atomic array updates (`$push`/`$pull`) so a single member add/remove doesn't
 * version-lock the whole document.
 */
@Repository
class CampaignRepository(
    private val mongo: MongoTemplate,
) {
    /** Insert a new campaign document (caller sets id/timestamps; `@Version` starts at 0). */
    fun insert(campaign: Campaign): Campaign = mongo.insert(campaign)

    /** The campaign with [id], or null if none exists. */
    fun findById(id: UUID): Campaign? = mongo.findById(id, Campaign::class.java)

    /** Every campaign run by [dmId], newest first (backs the `{ dmId: 1 }` index). */
    fun findByDmId(dmId: UUID): List<Campaign> =
        mongo.find(
            Query(Criteria.where("dmId").`is`(dmId)).with(Sort.by(Sort.Direction.DESC, "createdAt")),
            Campaign::class.java,
        )

    /** Every campaign whose roster includes [characterId] (backs the `{ "members.characterId": 1 }` index). */
    fun findByMemberCharacterId(characterId: UUID): List<Campaign> =
        mongo.find(Query(Criteria.where("members.characterId").`is`(characterId)), Campaign::class.java)

    /**
     * Persist an existing campaign with the `@Version` optimistic-concurrency guard: a stale version
     * throws `OptimisticLockingFailureException` (→ 409) instead of overwriting a concurrent edit.
     */
    fun save(campaign: Campaign): Campaign = mongo.save(campaign)

    /**
     * Atomically `$push` [member] onto the roster. No dedup here — "not already in this campaign" is a
     * service-level check (a character may legitimately be in other campaigns). Returns the updated
     * campaign, or null if [campaignId] doesn't exist.
     */
    fun addMember(
        campaignId: UUID,
        member: CampaignMember,
    ): Campaign? =
        mongo.findAndModify(
            Query(Criteria.where("_id").`is`(campaignId)),
            Update().push("members", member).currentDate("updatedAt"),
            FindAndModifyOptions.options().returnNew(true),
            Campaign::class.java,
        )

    /**
     * Atomically `$pull` the roster entry for [characterId] (drops the membership, keeps the character —
     * FR-009). Returns the updated campaign, or null if [campaignId] doesn't exist.
     */
    fun removeMember(
        campaignId: UUID,
        characterId: UUID,
    ): Campaign? =
        mongo.findAndModify(
            Query(Criteria.where("_id").`is`(campaignId)),
            Update()
                .pull("members", Query.query(Criteria.where("characterId").`is`(characterId)))
                .currentDate("updatedAt"),
            FindAndModifyOptions.options().returnNew(true),
            Campaign::class.java,
        )
}
