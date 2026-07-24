package no.rauboti.tome.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Base for integration tests needing the full context against a real MongoDB. Subclasses extend
 * `: IntegrationTest()` and inherit the shared singleton container from [TestcontainersConfiguration].
 *
 * Under the `test` profile, migrations run Spring Data-natively on `ApplicationReadyEvent`; there is
 * no demo seed, so tests insert only what they need.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest
