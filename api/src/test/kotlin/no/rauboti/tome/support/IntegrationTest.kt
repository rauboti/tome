package no.rauboti.tome.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Base class for integration tests that need the full application context backed by a real MongoDB
 * (a single-node replica set). Subclasses just `: IntegrationTest()` and inherit the shared singleton
 * container from [TestcontainersConfiguration] plus a Spring context that the test framework caches
 * and reuses across classes. The base is storage-agnostic by design — the backing store lives in
 * [TestcontainersConfiguration] (re-platformed Postgres → MongoDB in T090).
 *
 * Runs under the `test` profile (application-test.yml). Migrations run Spring Data-natively on the
 * `ApplicationReadyEvent` fired for this context (T089/T091); no demo seed — tests insert only what
 * they need.
 *
 * First introduced here for the auth contract test (T010); reused by the DB-backed integration tests
 * from T026 onward.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest
