package no.rauboti.tome.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Shared Testcontainers wiring for integration tests.
 *
 * The container is a JVM-wide singleton: started once on first access and reused across every
 * test class via [IntegrationTest]. `start()` is idempotent, and the instance is never stopped
 * explicitly — Testcontainers' Ryuk reaper tears it down at JVM exit. That gives one Postgres for
 * the whole `verify` run.
 *
 * Exposed to Spring via [@ServiceConnection][ServiceConnection], so the datasource is wired from
 * the running container with no `@DynamicPropertySource` plumbing — overriding the placeholder
 * datasource in application.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = SHARED_POSTGRES

    companion object {
        // Pinned to match docker-compose.yml so tests run the production engine.
        private const val POSTGRES_IMAGE = "postgres:17-alpine"

        private val SHARED_POSTGRES: PostgreSQLContainer =
            PostgreSQLContainer(POSTGRES_IMAGE).apply { start() }
    }
}
