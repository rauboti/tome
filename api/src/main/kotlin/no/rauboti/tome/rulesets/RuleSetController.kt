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
 * Serves the bundled rule sets to the web: the list (for a picker) and each one's full
 * [SheetDefinition] (which drives the definition-driven sheet renderer). Read-only — rule sets are
 * bundled code + data, not a user-writable resource. Behind the `/api` role gate (SecurityConfig).
 */
@RestController
@RequestMapping("/api/rule-sets")
class RuleSetController(
    private val registry: RuleSetRegistry,
) {
    @GetMapping
    fun list(): List<RuleSetSummary> = registry.all().map { RuleSetSummary(it.id(), it.name()) }

    @GetMapping("/{ruleSetId}")
    fun definition(
        @PathVariable ruleSetId: String,
    ): SheetDefinition = registry.get(ruleSetId).definition()
}
