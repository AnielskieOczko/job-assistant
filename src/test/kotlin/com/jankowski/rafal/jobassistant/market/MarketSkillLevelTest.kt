package com.jankowski.rafal.jobassistant.market

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure logic, so no container: [MarketSkillLevel] is the one place the source's vocabulary is
 * turned into ours, and the mapping is worth pinning.
 */
class MarketSkillLevelTest {

    @Test
    fun `maps every level the source is known to send`() {
        assertThat(MarketSkillLevel.parse("Basic")).isEqualTo(MarketSkillLevel.BASIC)
        assertThat(MarketSkillLevel.parse("Advanced")).isEqualTo(MarketSkillLevel.ADVANCED)
        assertThat(MarketSkillLevel.parse("Expert")).isEqualTo(MarketSkillLevel.EXPERT)
        assertThat(MarketSkillLevel.parse("NiceToHave")).isEqualTo(MarketSkillLevel.NICE_TO_HAVE)
    }

    @Test
    fun `an unseen or absent level does not fail the offer`() {
        assertThat(MarketSkillLevel.parse("Wizard")).isEqualTo(MarketSkillLevel.UNKNOWN)
        assertThat(MarketSkillLevel.parse(null)).isEqualTo(MarketSkillLevel.UNKNOWN)
        assertThat(MarketSkillLevel.parse("")).isEqualTo(MarketSkillLevel.UNKNOWN)
    }

    @Test
    fun `NiceToHave is the only level that is not a requirement`() {
        val notRequired = MarketSkillLevel.entries.filterNot { it.isRequired }
        assertThat(notRequired).containsExactly(MarketSkillLevel.NICE_TO_HAVE)
    }
}
