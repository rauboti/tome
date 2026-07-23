@file:Suppress("ktlint:standard:class-naming")

package no.rauboti.tome.config.migration

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/**
 * `C001` — create the `characters` collection and its owner-lookup index `{ userId: 1 }` (backs
 * `GET /api/characters`; data-model §Character Indexes). Applied once on boot by [MigrationRunner] and
 * recorded in the `_migrations` ledger.
 *
 * Idempotent, so a crash-retry (ledger recorded after [apply]) is safe: the collection is created only
 * when absent, and `ensureIndex` is a no-op when the identical index already exists.
 *
 * Naming: `C<order>__<Name>` mirrors Flyway/Flamingock migration files for readability; the underscore
 * needs the file-level ktlint `class-naming` suppress above. Execution **order comes from [id]**, not
 * the class name (the ledger key is `C001`), so the name is purely descriptive.
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
