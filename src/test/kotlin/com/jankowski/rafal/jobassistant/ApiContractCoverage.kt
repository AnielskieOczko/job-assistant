package com.jankowski.rafal.jobassistant

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberProperties

/**
 * Discovers every DTO type reachable from a `@RestController`'s endpoint return types, by static
 * reflection over the compiled classpath - no Spring context, no HTTP call, no Docker.
 *
 * This is the discovery half of the wire-contract guard. `ApiContractTest` still pins each type's
 * keys by hand against a real fixture - that part does not change - but until now, *which* types
 * had to be pinned was itself a hand-maintained list of imports. A type reachable here that
 * `ApiContractTest` never constructs is exactly the silent gap issue #68 exists to close.
 *
 * Membership is deliberately "reachable from a controller return type", not a package or naming
 * convention: request bodies live under `*.internal` and are correctly excluded, but so is
 * `ProfileSummary`, which is also `internal` yet is a real wire type - naming alone cannot tell
 * the two apart.
 */
object ApiContractCoverage {

    private const val BASE_PACKAGE = "com.jankowski.rafal.jobassistant"

    private val mappingAnnotations: Set<KClass<out Annotation>> = setOf(
        GetMapping::class, PostMapping::class, PutMapping::class, PatchMapping::class,
        DeleteMapping::class, RequestMapping::class,
    )

    /**
     * Generic containers a response may come wrapped in. Unwrapping stops at whatever this does not
     * name, so an unrecognised wrapper is inert rather than misread - extend this set if a
     * controller ever starts returning one (e.g. Spring Data's `Page`, or a `Flow`).
     */
    private val wrapperClasses: Set<KClass<*>> = setOf(
        ResponseEntity::class, List::class, Set::class, Collection::class, Map::class,
    )

    fun discoverWireResponseTypes(): Set<KClass<*>> {
        val visited = mutableSetOf<KClass<*>>()
        findControllers()
            .flatMap(::endpointReturnTypes)
            .forEach { collect(it, visited) }
        return visited
    }

    private fun findControllers(): List<KClass<*>> {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        return scanner.findCandidateComponents(BASE_PACKAGE).map { Class.forName(it.beanClassName).kotlin }
    }

    /** Only mapped endpoints, never `@ExceptionHandler` methods - error shapes aren't this contract. */
    private fun endpointReturnTypes(controller: KClass<*>): List<KType> =
        controller.declaredFunctions
            .filter { function -> function.annotations.any { it.annotationClass in mappingAnnotations } }
            .map { it.returnType }

    private fun collect(type: KType, visited: MutableSet<KClass<*>>) {
        val classifier = type.classifier as? KClass<*> ?: return
        if (classifier in wrapperClasses) {
            type.arguments.mapNotNull { it.type }.forEach { collect(it, visited) }
            return
        }
        if (classifier.qualifiedName?.startsWith(BASE_PACKAGE) != true) return
        if (classifier.java.isEnum) return
        if (!visited.add(classifier)) return
        classifier.memberProperties.forEach { property -> collect(property.returnType, visited) }
    }
}
