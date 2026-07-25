@file:Suppress("ktlint:standard:class-naming")

package no.rauboti.tome.config.migration

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/**
 * `C002` — create the `campaigns` collection and its lookup indexes (data-model §campaigns):
 *  - `{ dmId: 1 }` — a DM's campaigns;
 *  - `{ "members.playerId": 1 }` — campaigns a player is in;
 *  - `{ "members.characterId": 1 }` — campaigns a character is in.
 *
 * All plain (non-unique) lookups: a character MAY be in several campaigns at once — e.g. side quests
 * alongside a long-running campaign (D6 amended 2026-07-25). "No duplicate member within one campaign"
 * is a service-level check, not an index constraint. Idempotent (safe on crash-retry): the collection
 * is created only when absent and `ensureIndex` no-ops if the index already exists.
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
        indexOps.ensureIndex(Index().on("members.characterId", Sort.Direction.ASC))
    }
}
