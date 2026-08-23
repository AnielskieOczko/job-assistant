package com.jankowski.rafal.jobassistant.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Boots the full application against a Testcontainers Postgres.
 *
 * Requires a running Docker daemon. Tests that only exercise pure logic (the skill diff, the
 * CV invariant) must NOT use this — they belong in the fast tier with no container at all.
 *
 * Every model is a [ScriptedChatModel], so no integration test can reach a real provider whether
 * or not an API key happens to be present in the environment.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@Import(TestcontainersConfiguration::class, StubLlmConfiguration::class)
@ActiveProfiles("test")
annotation class IntegrationTest
