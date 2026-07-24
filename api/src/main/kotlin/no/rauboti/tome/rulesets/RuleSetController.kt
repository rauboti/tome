package no.rauboti.tome.rulesets

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** A rule set as shown in a picker/list (openapi `RuleSetSummary`). */
data class RuleSetSummary(
    val id: String,
    val name: String,
)

/**
 * Serves the bundled rule sets to the web as summaries — the list (for a picker) and a single lookup.
 * ADR-001: the sheet is a typed schema known to the client (codegen'd from openapi), not a definition
 * fetched to drive a generic renderer, so `/{id}` returns the [RuleSetSummary], not a `SheetDefinition`.
 * Read-only; behind the `/api` role gate (SecurityConfig).
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
