package no.rauboti.tome.rulesets.domain

/**
 * A soft validation finding: guidance, never a hard block — the DM can always override. [field] is
 * the offending field id, or null for a sheet-wide warning. Serialized to the openapi `RuleWarning`
 * schema.
 */
data class RuleWarning(
    val code: String,
    val message: String,
    val field: String? = null,
)
