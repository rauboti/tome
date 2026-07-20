package no.rauboti.tome.common

import no.rauboti.tome.rulesets.SheetData
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Converts a [SheetData] sheet object to and from a Postgres `jsonb` column for JdbcTemplate
 * (research D3). Serialization goes through the Boot-autoconfigured Jackson 3 [ObjectMapper]
 * (`tools.jackson`), which already has the Kotlin module registered — the same mapper the web layer
 * uses for request/response bodies, so a sheet round-trips identically over the wire and into storage.
 *
 * The value is a plain JSON **String**: repositories (T029 onward) bind it into a `jsonb` column with
 * an explicit SQL cast, e.g. `insert ... values (cast(:data as jsonb))`. We deliberately do not wrap it
 * in a `PGobject` — the Postgres driver is a runtime-only dependency (pom scope), so the app doesn't
 * compile against driver types, and the cast keeps the `jsonb`-ness explicit at each query.
 */
@Component
class JsonbSupport(
    private val objectMapper: ObjectMapper,
) {
    /** Serialize a sheet to a JSON string, to be bound with `cast(? as jsonb)`. */
    fun toJson(data: SheetData): String = objectMapper.writeValueAsString(data)

    /** Parse a `jsonb` column value (read as text) back into a [SheetData]; null/blank → empty sheet. */
    fun fromJson(json: String?): SheetData = if (json.isNullOrBlank()) emptyMap() else objectMapper.readValue<SheetData>(json)
}
