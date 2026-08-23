package com.jankowski.rafal.jobassistant.llm.internal

import com.jankowski.rafal.jobassistant.llm.LlmCall
import com.jankowski.rafal.jobassistant.llm.LlmCallDetail
import com.jankowski.rafal.jobassistant.llm.LlmCallLog
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Read-only window onto what the models were actually asked and what came back. */
@RestController
@RequestMapping("/api/llm/calls")
internal class LlmCallController(private val calls: LlmCallLog) {

    @GetMapping
    fun recent(@RequestParam(defaultValue = "50") limit: Int): List<LlmCall> = calls.recent(limit)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): ResponseEntity<LlmCallDetail> =
        calls.detail(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}
