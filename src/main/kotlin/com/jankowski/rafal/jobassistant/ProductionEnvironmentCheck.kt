package com.jankowski.rafal.jobassistant

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment

/**
 * Refuses to start the production profile with an unresolved configuration placeholder.
 *
 * `application-prod.yaml` gives `DB_PASSWORD` no default, so that a launch which never sourced
 * `.env.prod` cannot fall through to the development credentials. That much works on its own - but
 * not legibly. Spring Boot's `Binder` ignores unresolvable placeholders rather than failing on
 * them, so the literal text `${DB_PASSWORD}` is handed to the driver as a password, and the
 * operator sees a Hikari pool time out after thirty seconds and a buried
 * "password authentication failed". The cause and the symptom look nothing alike.
 *
 * This restates the same guarantee as an assertion that fires before any pool is opened, naming the
 * variable that is missing. It lives in the base package next to [SpaWebConfiguration] for the same
 * reason: `ApplicationModules.of(...)` treats every direct sub-package as a module, so a home of
 * its own would be detected as one.
 *
 * It checks rather than supplies. Adding a default here would be the bug it exists to prevent.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
internal class ProductionEnvironmentCheck {

    internal companion object {
        /**
         * A BeanFactoryPostProcessor runs before ordinary beans are instantiated, which is what
         * puts this ahead of the datasource rather than alongside it. `@JvmStatic` because Spring
         * warns - rightly - when a factory method for one is an instance method: returning it
         * would force the enclosing configuration class to be built before the post-processors
         * that are supposed to see it.
         */
        @Bean
        @JvmStatic
        fun requiredProductionProperties(environment: Environment): BeanFactoryPostProcessor =
            BeanFactoryPostProcessor { _: ConfigurableListableBeanFactory -> verify(environment) }

        /** Properties whose value must come from the environment, and the variable each expects. */
        private val REQUIRED = mapOf("spring.datasource.password" to "DB_PASSWORD")

        fun verify(environment: Environment) {
            REQUIRED.forEach { (property, variable) ->
                val value = try {
                    environment.getProperty(property)
                } catch (unresolved: IllegalArgumentException) {
                    // Environment.getProperty throws on an unresolvable placeholder; Binder does
                    // not. Either path lands here.
                    throw missing(property, variable, unresolved)
                }
                if (value == null || value.contains("\${")) throw missing(property, variable, null)
            }
        }

        private fun missing(property: String, variable: String, cause: Throwable?) =
            IllegalStateException(
                "The prod profile requires $variable to be set: $property has no default and no " +
                    "value. Source .env.prod first, or start the application through " +
                    "scripts/run-prod.sh, which does it for you. See docs/operations.md.",
                cause,
            )
    }
}
