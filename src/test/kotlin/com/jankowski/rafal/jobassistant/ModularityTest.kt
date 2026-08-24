package com.jankowski.rafal.jobassistant

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * Enforces the module boundaries described in the architecture plan. This test fails the build
 * when one feature slice reaches into another's `internal` package, which is the whole point of
 * choosing Spring Modulith over plain packages.
 *
 * Runs without Docker or Spring context startup - it is static bytecode analysis only.
 */
class ModularityTest {

    private val modules = ApplicationModules.of(JobAssistantApplication::class.java)

    @Test
    fun `module boundaries are respected`() {
        modules.verify()
    }

    @Test
    fun `write module documentation`() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
    }
}
