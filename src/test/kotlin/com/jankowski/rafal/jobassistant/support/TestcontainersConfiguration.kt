package com.jankowski.rafal.jobassistant.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Real Postgres for every integration test. H2 is deliberately not an option: the Flyway
 * migrations are Postgres-specific and the production target (Neon) is Postgres.
 *
 * The container is a singleton bean, so Spring's context cache reuses one database across
 * all tests that import this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer =
        PostgreSQLContainer("postgres:18-alpine")
}
