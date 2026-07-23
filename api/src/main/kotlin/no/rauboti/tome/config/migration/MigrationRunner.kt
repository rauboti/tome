package no.rauboti.tome.config.migration

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Applies the [MigrationChange] beans on startup — the Spring Data-native migration mechanism (no
 * framework; research §Migrations). On [ApplicationReadyEvent] it reads the already-applied change ids
 * from the `_migrations` ledger and runs each not-yet-applied change in [MigrationChange.id] order,
 * recording it afterwards so it runs at most once.
 *
 * v1 is single-instance, so no distributed lock is taken; concurrent starts are tolerated because the
 * changes are idempotent and the ledger `_id` is the change id — a losing racer's record insert hits a
 * duplicate key, treated here as "already recorded". (Multi-instance coordination is a Flamingock
 * revisit trigger — research §Migrations.)
 *
 * With no [MigrationChange] beans present this is a harmless no-op; the first change (`C001`) lands in
 * T091.
 */
@Component
class MigrationRunner(
    private val mongo: MongoTemplate,
    private val changes: List<MigrationChange>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun applyPending() {
        val applied = mongo.findAll(AppliedChange::class.java).mapTo(mutableSetOf()) { it.id }
        changes.sortedBy { it.id }.forEach { change ->
            if (change.id in applied) return@forEach
            log.info("Applying migration {}", change.id)
            change.apply(mongo)
            try {
                mongo.insert(AppliedChange(change.id, Instant.now()))
            } catch (e: DuplicateKeyException) {
                log.info("Migration {} recorded concurrently by another instance; skipping ledger write", change.id)
            }
        }
    }
}

/** Ledger row: one per applied [MigrationChange], keyed by its id, in the `_migrations` collection. */
@Document(collection = "_migrations")
data class AppliedChange(
    @Id val id: String,
    val appliedAt: Instant,
)
