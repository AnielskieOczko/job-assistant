package com.jankowski.rafal.jobassistant

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Scheduling is enabled for the market ingestion poll. It lives here rather than in the `market`
 * module because `ApplicationModules.of(...)` treats every direct sub-package as a module, and an
 * `@EnableScheduling` inside one would switch the container-wide feature on from a module that only
 * happens to be its first user.
 */
@SpringBootApplication
@EnableScheduling
class JobAssistantApplication

fun main(args: Array<String>) {
    runApplication<JobAssistantApplication>(*args)
}
