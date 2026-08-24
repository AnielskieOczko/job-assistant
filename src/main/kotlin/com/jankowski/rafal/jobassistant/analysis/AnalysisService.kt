package com.jankowski.rafal.jobassistant.analysis

interface AnalysisService {

    /**
     * Queues an analysis and returns immediately with its id. The work runs on a background
     * executor; poll [findReport] until [AnalysisState.isTerminal].
     *
     * Asynchronous because two model calls plus a database cold start routinely take longer than
     * a proxy will hold an HTTP request open.
     */
    fun start(offerId: Long): Long

    fun findReport(analysisId: Long): AnalysisReport?

    fun latestForOffer(offerId: Long): AnalysisReport?

    /**
     * Demand and gap counts across every completed analysis. A single offer tells you what you
     * lack for that job; this tells you what to learn.
     */
    fun aggregateGaps(): AggregateGapReport
}
