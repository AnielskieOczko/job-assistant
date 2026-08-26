package com.jankowski.rafal.jobassistant.support

import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Collects eval results and renders them to disk.
 *
 * The eval tier exists to be *comparable* - change a prompt or swap a model, re-run, see whether
 * the numbers moved. Printing to stdout does not support that: by the time you want to compare,
 * the previous run's output has scrolled away.
 *
 * Kept as an ordinary class rather than folded into [EvalScorecard] so it can be tested without
 * touching process-wide state or arming a shutdown hook.
 */
internal class Scorecard {

    /** One fixture's worth of results. [notes] carries diagnosis, [metrics] carries the numbers. */
    private data class Case(
        val suite: String,
        val fixture: String,
        val metrics: Map<String, Double>,
        val notes: Map<String, String>,
    )

    /** Keyed so a suite can report one fixture's metrics from more than one test method. */
    private val cases = ConcurrentHashMap<String, Case>()
    private val context = ConcurrentHashMap<String, String>()
    private val json = JsonMapper.builder().build()

    /** Run metadata - which model produced these numbers, without which they mean nothing. */
    fun describe(key: String, value: String) {
        context[key] = value
    }

    fun record(suite: String, fixture: String, metrics: Map<String, Double>, notes: Map<String, String>) {
        cases.merge("$suite/$fixture", Case(suite, fixture, metrics, notes)) { existing, added ->
            existing.copy(metrics = existing.metrics + added.metrics, notes = existing.notes + added.notes)
        }
    }

    val isEmpty: Boolean get() = cases.isEmpty()

    /** Writes `eval-report.json` for diffing and `eval-report.md` for reading. No-op when empty. */
    fun write(directory: Path) {
        val snapshot = cases.values.toList()
        if (snapshot.isEmpty()) return

        Files.createDirectories(directory)
        Files.writeString(directory.resolve("eval-report.json"), json.writeValueAsString(asMap(snapshot)))
        Files.writeString(directory.resolve("eval-report.md"), asMarkdown(snapshot))
    }

    private fun asMap(snapshot: List<Case>): Map<String, Any> = mapOf(
        "generatedAt" to Instant.now().toString(),
        "context" to context.toSortedMap(),
        "suites" to snapshot.groupBy { it.suite }.toSortedMap().mapValues { (_, suiteCases) ->
            mapOf(
                "means" to means(suiteCases),
                "cases" to suiteCases.sortedBy { it.fixture }.map {
                    mapOf("fixture" to it.fixture, "metrics" to it.metrics, "notes" to it.notes)
                },
            )
        },
    )

    /**
     * Averaged per metric rather than across all of them: suites do not share metric names, and a
     * fixture that never reported a metric must not drag its mean toward zero.
     */
    private fun means(suiteCases: List<Case>): Map<String, Double> =
        suiteCases.flatMap { it.metrics.keys }.distinct().sorted().associateWith { metric ->
            val values = suiteCases.mapNotNull { it.metrics[metric] }
            values.sum() / values.size
        }

    private fun asMarkdown(snapshot: List<Case>): String = buildString {
        appendLine("# Eval scorecard")
        appendLine()
        appendLine("Generated ${Instant.now()}. Regenerate with `./mvnw test -Peval`.")
        appendLine()

        if (context.isNotEmpty()) {
            appendLine("| Run context | |")
            appendLine("|---|---|")
            context.toSortedMap().forEach { (key, value) -> appendLine("| $key | $value |") }
            appendLine()
        }

        snapshot.groupBy { it.suite }.toSortedMap().forEach { (suite, suiteCases) ->
            val metrics = suiteCases.flatMap { it.metrics.keys }.distinct().sorted()
            val mean = means(suiteCases)

            appendLine("## $suite")
            appendLine()
            appendLine("| fixture | ${metrics.joinToString(" | ")} |")
            appendLine("|---".repeat(metrics.size + 1) + "|")
            suiteCases.sortedBy { it.fixture }.forEach { case ->
                appendLine("| ${case.fixture} | " + metrics.joinToString(" | ") { format(case.metrics[it]) } + " |")
            }
            appendLine("| **mean** | " + metrics.joinToString(" | ") { "**${format(mean[it])}**" } + " |")
            appendLine()

            val noted = suiteCases.filter { it.notes.isNotEmpty() }.sortedBy { it.fixture }
            if (noted.isNotEmpty()) {
                noted.forEach { case ->
                    appendLine("- **${case.fixture}** - " + case.notes.entries.joinToString("; ") { "${it.key}: ${it.value}" })
                }
                appendLine()
            }
        }
    }

    private fun format(value: Double?) = value?.let { "%.2f".format(it) } ?: "-"
}

/**
 * The scorecard every eval-tier suite writes into.
 *
 * Results arrive from several test classes, so the write happens on a JVM shutdown hook rather
 * than in an `@AfterAll` - no single class can know it went last. The hook is armed on first use,
 * so a run that touches no eval leaves no report behind.
 */
object EvalScorecard {

    private val scorecard = Scorecard()
    private val armed = AtomicBoolean(false)

    fun describe(key: String, value: String) {
        arm()
        scorecard.describe(key, value)
    }

    fun record(
        suite: String,
        fixture: String,
        metrics: Map<String, Double>,
        notes: Map<String, String> = emptyMap(),
    ) {
        arm()
        scorecard.record(suite, fixture, metrics, notes)
    }

    private fun arm() {
        if (armed.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(
                Thread({ scorecard.write(Path.of("target")) }, "eval-scorecard")
            )
        }
    }
}
