package no.rauboti.tome.rulesets

import no.rauboti.tome.common.NotFoundException
import org.springframework.stereotype.Component

/**
 * Resolves a [RuleSet] by id. Spring injects every [RuleSet] bean on the classpath (v1: just
 * `DnD35RuleSet`), so registering a new rule set is purely additive — declare its `@Component` and it
 * appears here with no change to this class or any cross-cutting service (FR-023/SC-009).
 *
 * Unknown ids are rejected with [NotFoundException] → 404 (RFC-7807 via the exception handler, T012).
 */
@Component
class RuleSetRegistry(
    ruleSets: List<RuleSet>,
) {
    private val byId: Map<String, RuleSet> = ruleSets.associateBy { it.id() }

    /** All registered rule sets, for the summary listing. */
    fun all(): List<RuleSet> = byId.values.toList()

    /** The rule set with [id], or [NotFoundException] if none is registered. */
    fun get(id: String): RuleSet = byId[id] ?: throw NotFoundException("Unknown rule set '$id'.")
}
