package no.rauboti.tome.campaigns

/**
 * The allowed [Campaign.status] values: `suspended` = paused without archiving. A plain string (not a
 * Kotlin enum) to sidestep Spring Data Mongo's uppercase enum serialization; `status` is not indexed,
 * so the value is only read/compared in the service.
 */
object CampaignStatus {
    const val ACTIVE = "active"
    const val ARCHIVED = "archived"
    const val SUSPENDED = "suspended"
}
