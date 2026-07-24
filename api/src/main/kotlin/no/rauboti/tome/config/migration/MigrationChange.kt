package no.rauboti.tome.config.migration

import org.springframework.data.mongodb.core.MongoTemplate

/**
 * One ordered, idempotent MongoDB migration ("change unit") — the Spring Data-native replacement for a
 * framework changelog (research §Migrations; no Mongock/Flamingock). Each change is a Spring bean;
 * [MigrationRunner] runs the not-yet-applied ones in [id] order on boot and records each in the
 * `_migrations` ledger so it runs at most once.
 *
 * Implementations MUST be idempotent: the ledger is written *after* [apply], so a crash between the two
 * re-runs the change. [id] is the stable identifier (e.g. `C001`); ordered lexically, so keep it
 * zero-padded and never change one once shipped.
 */
interface MigrationChange {
    val id: String

    fun apply(mongo: MongoTemplate)
}
