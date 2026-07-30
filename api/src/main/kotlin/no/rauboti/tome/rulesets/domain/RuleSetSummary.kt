package no.rauboti.tome.rulesets.domain

/** A rule set as shown in a picker/list (openapi `RuleSetSummary`). */
data class RuleSetSummary(
    val id: String,
    val name: String,
)
