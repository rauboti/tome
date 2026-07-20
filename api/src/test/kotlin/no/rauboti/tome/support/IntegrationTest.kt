package no.rauboti.tome.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Base class for integration tests that need the full application context backed by a real Postgres
 * (Flyway-migrated on startup). Subclasses just `: IntegrationTest()` and inherit the shared
 * singleton container from [TestcontainersConfiguration] plus a Spring context that the test
 * framework caches and reuses across classes.
 *
 * Runs under the `test` profile (application-test.yml): Flyway applies the schema migrations against
 * the throwaway container, with no demo seed. Tests insert only the rows they need.
 *
 * First introduced here for the auth contract test (T010); reused by the DB-backed integration tests
 * from T026 onward.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest
