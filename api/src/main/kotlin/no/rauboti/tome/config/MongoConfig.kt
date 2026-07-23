package no.rauboti.tome.config

import com.mongodb.MongoClientSettings
import org.bson.UuidRepresentation
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager

/**
 * MongoDB wiring. The Mongo client and [org.springframework.data.mongodb.core.MongoTemplate] are
 * auto-configured by Spring Boot from `spring.data.mongodb.uri` (application.yml, T087) — mirroring
 * the platform's low-level, no-JPA template convention — so this class only adds the two pieces Boot
 * does not provide on its own:
 *
 *  - a [MongoClientSettingsBuilderCustomizer] pinning the UUID representation to
 *    [UuidRepresentation.STANDARD]. Aggregate ids are [java.util.UUID] `@Id`s and the driver's default
 *    `UNSPECIFIED` refuses to read/write UUIDs; setting it on the client settings covers every client
 *    Boot builds, including the Testcontainers one wired via `@ServiceConnection` in tests.
 *  - a [MongoTransactionManager] enabling `@Transactional` against the single-node replica set
 *    (multi-document transactions, research §D5); Boot does not register one automatically.
 */
@Configuration
class MongoConfig {
    @Bean
    fun uuidRepresentationCustomizer(): MongoClientSettingsBuilderCustomizer =
        MongoClientSettingsBuilderCustomizer { builder: MongoClientSettings.Builder ->
            builder.uuidRepresentation(UuidRepresentation.STANDARD)
        }

    @Bean
    fun mongoTransactionManager(databaseFactory: MongoDatabaseFactory): MongoTransactionManager = MongoTransactionManager(databaseFactory)
}
