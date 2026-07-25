@file:Suppress("ktlint:standard:class-naming")

package no.rauboti.tome.config.migration

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.PartialIndexFilter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component

/**
 * `C002` — create the `campaigns` collection and its indexes (data-model §campaigns / Invariants):
 *  - `{ dmId: 1 }` — a DM's campaigns;
 *  - `{ "members.playerId": 1 }` — campaigns a player is in;
 *  - **unique partial multikey** `{ "members.characterId": 1 }` filtered to `status: "active"` —
 *    enforces "one active campaign per character" (research §D6) and "no duplicate member in a
 *    campaign". Partial so an *archived* campaign's members don't reserve the character.
 *
 * Idempotent (safe on crash-retry): the collection is created only when absent and `ensureIndex`
 * no-ops if the index already exists. Naming `C<order>__<Name>` and order-from-[id] as in [C001].
 */
@Component
class C002__CreateCampaigns : MigrationChange {
    override val id = "C002"

    override fun apply(mongo: MongoTemplate) {
        if (!mongo.collectionExists("campaigns")) {
            mongo.createCollection("campaigns")
        }
        val indexOps = mongo.indexOps("campaigns")
        indexOps.ensureIndex(Index().on("dmId", Sort.Direction.ASC))
        indexOps.ensureIndex(Index().on("members.playerId", Sort.Direction.ASC))
        indexOps.ensureIndex(
            Index()
                .on("members.characterId", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").`is`("active"))),
        )
    }
}
