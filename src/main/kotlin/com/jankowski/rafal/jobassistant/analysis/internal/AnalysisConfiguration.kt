package com.jankowski.rafal.jobassistant.analysis.internal

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration(proxyBeanMethods = false)
@EnableAsync
internal class AnalysisConfiguration {

    /**
     * Deliberately small. Each analysis makes two model calls, so unbounded concurrency would
     * mean unbounded spend and rate-limit errors; a short queue with caller-runs backpressure is
     * the right shape for a single-user tool.
     */
    @Bean
    fun analysisExecutor(): TaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 2
        queueCapacity = 20
        setThreadNamePrefix("analysis-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(30)
        initialize()
    }

    /**
     * A job that was mid-flight when the process died can never resume, and leaving it PENDING
     * would make a client poll forever. Fail it explicitly at startup so the state is honest.
     */
    @Bean
    fun failOrphanedAnalyses(analyses: AnalysisRepository): ApplicationRunner = ApplicationRunner {
        val log = LoggerFactory.getLogger(AnalysisConfiguration::class.java)
        val orphaned = analyses.findUnfinished()
        if (orphaned.isNotEmpty()) {
            log.warn("Failing {} analysis job(s) orphaned by a previous shutdown", orphaned.size)
            analyses.saveAll(
                orphaned.map {
                    it.copy(
                        state = "FAILED",
                        error = "Interrupted by application shutdown; start a new analysis.",
                        completedAt = java.time.Instant.now(),
                    )
                }
            )
        }
    }
}
