package no.rauboti.tome.catalogs

import no.rauboti.tome.common.NotFoundException
import org.springframework.stereotype.Component

/**
 * Resolves a [Catalog] by (rule set, name). Spring injects every [Catalog] bean on the classpath
 * (v1: just [SpellCatalog]), so adding a catalog is purely additive (FR-023/SC-009). Unknown
 * (ruleSetId, name) pairs are rejected with [NotFoundException] → 404 (RFC-7807, T012).
 */
@Component
class CatalogRegistry(
    catalogs: List<Catalog>,
) {
    private val byKey: Map<String, Catalog> = catalogs.associateBy { key(it.ruleSetId(), it.name()) }

    fun get(
        ruleSetId: String,
        name: String,
    ): Catalog = byKey[key(ruleSetId, name)] ?: throw NotFoundException("Unknown catalog '$name' for rule set '$ruleSetId'.")

    private fun key(
        ruleSetId: String,
        name: String,
    ): String = "$ruleSetId/$name"
}
