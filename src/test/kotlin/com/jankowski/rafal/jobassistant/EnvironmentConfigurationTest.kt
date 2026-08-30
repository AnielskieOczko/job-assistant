package com.jankowski.rafal.jobassistant

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.origin.OriginTrackedValue
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource

/**
 * Pins the handful of properties that decide *which database a process writes to*.
 *
 * These are the ones whose drift is silent and expensive. Every other misconfiguration announces
 * itself: a wrong model name fails a call, a wrong port fails to bind. But a prod profile that
 * inherits dev's connection details starts cleanly, serves happily, and writes the real profile
 * into the disposable database - or worse, the other way round. Nothing at runtime would say so.
 *
 * The guarantees, and why each one:
 *
 *  - Dev and prod differ in **both** port and database name, so a half-applied change misses rather
 *    than lands on the wrong data.
 *  - The shared `application.yaml` supplies no datasource fallback at all, so a profile that forgot
 *    to declare one fails instead of quietly inheriting.
 *  - The prod password has no default. It is the one property that cannot be wrong-but-working, so
 *    a launch that never sourced .env.prod stops at start-up.
 *  - Market ingestion is stated explicitly in both profiles, because MarketIngestionScheduler is
 *    `@ConditionalOnProperty(matchIfMissing = true)`: silence there means polling.
 *
 * Pure file parsing - no Spring context, no container. See docs/operations.md.
 */
class EnvironmentConfigurationTest {

    private val shared = load("application.yaml")
    private val dev = load("application-dev.yaml")
    private val prod = load("application-prod.yaml")

    @Test
    fun `dev and prod point at different databases`() {
        assertThat(dev["spring.datasource.url"] as String)
            .contains("5432")
            .contains("jobassistant_dev")
        assertThat(prod["spring.datasource.url"] as String)
            .contains("5433")
            .endsWith("/jobassistant}")
    }

    @Test
    fun `the shared configuration names no database of its own`() {
        assertThat(shared.keys.filter { it.startsWith("spring.datasource") })
            .noneMatch { it.endsWith(".url") || it.endsWith(".username") || it.endsWith(".password") }
        assertThat(shared).doesNotContainKey("server.port")
        assertThat(shared).doesNotContainKey("job-assistant.market.enabled")
    }

    @Test
    fun `the production password has no default`() {
        // "${DB_PASSWORD}" and not "${DB_PASSWORD:something}" - the colon is what would turn a
        // missing environment variable into a silent fallback.
        assertThat(prod["spring.datasource.password"] as String).isEqualTo("\${DB_PASSWORD}")
    }

    @Test
    fun `each environment states whether it polls the market`() {
        assertThat(dev["job-assistant.market.enabled"]).isEqualTo(false)
        assertThat(prod["job-assistant.market.enabled"]).isEqualTo(true)
    }

    @Test
    fun `dev keeps the port the vite proxy expects and prod does not share it`() {
        assertThat(dev["server.port"]).isEqualTo(8080)
        assertThat(prod["server.port"]).isNotEqualTo(8080)
    }

    /**
     * The loader wraps every value in an [OriginTrackedValue] so Boot can report which file a
     * property came from. Unwrapped here, because these assertions are about the value.
     */
    private fun load(resource: String): Map<String, Any?> =
        YamlPropertySourceLoader()
            .load(resource, ClassPathResource(resource))
            .flatMap { source: PropertySource<*> ->
                @Suppress("UNCHECKED_CAST")
                (source.source as Map<String, Any?>).entries
            }
            .associate { it.key to ((it.value as? OriginTrackedValue)?.value ?: it.value) }
}
