package no.rauboti.tome.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mongodb.MongoDBContainer

/**
 * Shared Testcontainers wiring for integration tests.
 *
 * The container is a JVM-wide singleton reused across test classes; it is never stopped explicitly —
 * Ryuk tears it down at JVM exit — giving one MongoDB for the whole `verify` run.
 *
 * [MongoDBContainer] initiates a single-node replica set, required for multi-document transactions
 * and `@Version` optimistic concurrency (research §D5). [@ServiceConnection][ServiceConnection] wires
 * it from the container's replica-set URL, so tests bypass the `spring.mongodb.uri` property entirely
 * — which is why they can't catch a misconfigured URI; the compose boot check covers that path.
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
