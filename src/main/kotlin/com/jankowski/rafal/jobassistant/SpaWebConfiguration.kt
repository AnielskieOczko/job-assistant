package com.jankowski.rafal.jobassistant

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Serves the Vite build from `classpath:/static` and sends any unknown, non-API path back to
 * `index.html`, so a client-side route survives a refresh or a pasted deep link.
 *
 * This lives in the base package rather than a feature slice on purpose. `ModularityTest` builds
 * `ApplicationModules.of(JobAssistantApplication::class)`, which treats every *direct sub-package*
 * as an application module - a `…jobassistant.web` package would be detected as a seventh module
 * and stand alongside `analysis` and `document` in the generated documentation. A type sitting
 * directly in the base package belongs to no module and is visible to all, which is what a
 * cross-cutting infrastructure concern should be.
 *
 * A [PathResourceResolver] is used rather than the more familiar forwarding controller. The usual
 * idiom maps a dot-free path segment followed by a catch-all and forwards it to `index.html`, but
 * that also matches `/assets/index-a1b2c3.js`: the first segment carries no dot, and the dot sits
 * in a later segment swallowed by the catch-all. Annotated handler mappings take precedence over
 * resource handlers, so such a controller would intercept the application's own bundle. A
 * resolver only runs once no real file has matched, so it can never shadow a static asset.
 *
 * When `src/main/resources/static/` does not exist - a fresh clone, or any build that did not run
 * `-Pfrontend` - every lookup simply misses and 404s. That is fine: development is served by the
 * Vite dev server on :5173, which proxies `/api` here.
 */
@Configuration
class SpaWebConfiguration : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? {
                    val requested = location.createRelative(resourcePath)
                    if (requested.exists() && requested.isReadable) return requested

                    // A mistyped API path must 404 rather than quietly return the SPA shell -
                    // otherwise fetch() receives HTML and JSON.parse fails somewhere far away.
                    if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                        return null
                    }

                    return location.createRelative(INDEX).takeIf { it.exists() }
                }
            })
    }

    private companion object {
        const val INDEX = "index.html"
    }
}
