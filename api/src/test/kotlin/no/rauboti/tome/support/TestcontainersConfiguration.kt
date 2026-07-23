package no.rauboti.tome.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mongodb.MongoDBContainer

/**
 * Shared Testcontainers wiring for integration tests.
 *
 * The container is a JVM-wide singleton: started once on first access and reused across every
 * test class via [IntegrationTest]. `start()` is idempotent, and the instance is never stopped
 * explicitly — Testcontainers' Ryuk reaper tears it down at JVM exit. That gives one MongoDB
 * replica set for the whole `verify` run.
 *
 * [MongoDBContainer] initiates a single-node replica set automatically, so multi-document
 * transactions and `@Version` optimistic concurrency work (research §D5). Exposed to Spring via
 * [@ServiceConnection][ServiceConnection], which wires `spring.data.mongodb.uri` from the container's
 * replica-set URL — no `@DynamicPropertySource` plumbing — overriding the placeholder in application.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun mongoContainer(): MongoDBContainer = SHARED_MONGO

    companion object {
        // Pinned to match docker-compose.yml so tests run the production engine + major version.
        private const val MONGO_IMAGE = "mongo:8"

        private val SHARED_MONGO: MongoDBContainer =
            MongoDBContainer(MONGO_IMAGE).apply { start() }
    }
}
