package no.rauboti.tome.rulesets

import no.rauboti.tome.rulesets.domain.RuleSetSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Serves the bundled rule sets to the web as summaries — the list (for a picker) and a single lookup.
 * Per the sheet is a typed schema known to the client, so `/{id}` returns [RuleSetSummary], not a sheet
 * definition. Read-only; behind the `/api` role gate (SecurityConfig).
 */
@RestController
@RequestMapping("/api/rule-sets")
class RuleSetController(
    private val registry: RuleSetRegistry,
) {
    @GetMapping
    fun list(): List<RuleSetSummary> = registry.all().map { RuleSetSummary(it.id(), it.name()) }

    @GetMapping("/{ruleSetId}")
    fun get(
        @PathVariable ruleSetId: String,
    ): RuleSetSummary = registry.get(ruleSetId).let { RuleSetSummary(it.id(), it.name()) }
}
