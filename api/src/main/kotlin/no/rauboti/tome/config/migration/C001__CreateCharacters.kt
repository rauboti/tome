@file:Suppress("ktlint:standard:class-naming")

package no.rauboti.tome.config.migration

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/**
 * `C001` — create the `characters` collection and its owner-lookup index `{ userId: 1 }` (backs
 * `GET /api/characters`; data-model §Character Indexes). Idempotent, so a crash-retry is safe: the
 * collection is created only when absent and `ensureIndex` is a no-op if it already exists.
 *
 * Naming `C<order>__<Name>` mirrors Flyway/Flamingock for readability (the underscore needs the
 * file-level ktlint suppress above); execution order comes from [id] (the ledger key `C001`), not the
 * class name.
 */
@Component
class C001__CreateCharacters : MigrationChange {
    override val id = "C001"

    override fun apply(mongo: MongoTemplate) {
        if (!mongo.collectionExists("characters")) {
            mongo.createCollection("characters")
        }
        mongo.indexOps("characters").ensureIndex(Index().on("userId", Sort.Direction.ASC))
    }
}
