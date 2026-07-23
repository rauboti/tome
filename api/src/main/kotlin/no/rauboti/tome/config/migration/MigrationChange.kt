package no.rauboti.tome.config.migration

import org.springframework.data.mongodb.core.MongoTemplate

/**
 * One ordered, idempotent MongoDB migration ("change unit") — the Spring Data-native replacement for
 * a framework changelog (research §Migrations; no Mongock/Flamingock). Each change is a Spring bean;
 * [MigrationRunner] discovers them, runs the not-yet-applied ones in [id] order on boot, and records
 * each in the `_migrations` ledger so it runs at most once.
 *
 * Implementations MUST be idempotent (e.g. create a collection only if absent, `ensureIndex`): the
 * ledger is written *after* [apply], so a crash between the two simply re-runs the (idempotent) change.
 *
 * [id] is the stable change identifier (e.g. `C001`). Ids are ordered lexically, so keep them
 * zero-padded and never change one once shipped.
 */
interface MigrationChange {
    val id: String

    fun apply(mongo: MongoTemplate)
}
