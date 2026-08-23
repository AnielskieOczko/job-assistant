package com.jankowski.rafal.jobassistant

import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.Test

@IntegrationTest
class JobAssistantApplicationTests {

    @Test
    fun `context loads and migrations apply`() {
        // Startup exercises Flyway against a real Postgres; failure here means a broken migration.
    }
}
