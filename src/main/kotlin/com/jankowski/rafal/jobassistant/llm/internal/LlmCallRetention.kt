package com.jankowski.rafal.jobassistant.llm.internal

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * Ages prompt copies out of the audit log.
 *
 * The guard upstream keeps direct identifiers out of a prompt, but what remains is still a record
 * of who worked where and what they did, kept indefinitely for the sake of debugging a call that
 * happened months ago. Keeping it forever is not a debugging requirement, it is just what happens
 * when nothing deletes anything.
 *
 * Set `job-assistant.llm.audit.retention` to `PT0S` to keep rows forever.
 */
@Component
internal class LlmCallRetention(
    private val jdbc: JdbcClient,
    private val properties: LlmProperties,
) {

    private val log = LoggerFactory.getLogger(LlmCallRetention::class.java)

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT6H")
    @Transactional
    fun purgeExpired() {
        val retention = properties.audit.retention
        if (retention <= Duration.ZERO) return

        val deleted = jdbc.sql("delete from llm_call where created_at < now() - cast(:age as interval)")
            .param("age", "${retention.toSeconds()} seconds")
            .update()

        if (deleted > 0) log.info("Purged {} model-call audit row(s) older than {}", deleted, retention)
    }
}
