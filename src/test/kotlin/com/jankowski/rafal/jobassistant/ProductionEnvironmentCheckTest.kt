package com.jankowski.rafal.jobassistant

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

/**
 * The interesting case is the *unresolved placeholder*, not the missing key. Boot's `Binder`
 * ignores a placeholder it cannot resolve and passes the literal `${DB_PASSWORD}` through as a
 * value, which is why checking for null alone would let the thirty-second pool timeout happen
 * anyway.
 */
class ProductionEnvironmentCheckTest {

    @Test
    fun `a real password passes`() {
        val environment = MockEnvironment().withProperty("spring.datasource.password", "s3cret")
        assertThatCode { ProductionEnvironmentCheck.verify(environment) }.doesNotThrowAnyException()
    }

    @Test
    fun `an unresolved placeholder is rejected`() {
        val environment = MockEnvironment().withProperty("spring.datasource.password", "\${DB_PASSWORD}")
        assertThatThrownBy { ProductionEnvironmentCheck.verify(environment) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("DB_PASSWORD")
            .hasMessageContaining("scripts/run-prod.sh")
    }

    @Test
    fun `an absent property is rejected`() {
        assertThatThrownBy { ProductionEnvironmentCheck.verify(MockEnvironment()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("DB_PASSWORD")
    }

    @Test
    fun `the message names the variable rather than any value`() {
        val environment = MockEnvironment().withProperty("spring.datasource.password", "\${DB_PASSWORD}")
        val message = runCatching { ProductionEnvironmentCheck.verify(environment) }
            .exceptionOrNull()!!.message!!
        // Same rule as SensitiveDataInPromptException: this message reaches logs, so it reports
        // which setting is missing and never what any setting contains.
        assertThat(message).doesNotContain("s3cret")
    }
}
