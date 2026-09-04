package com.jankowski.rafal.jobassistant.analysis

interface AnalysisService {

    /**
     * Queues an analysis of the given offer against the given profile and returns immediately with
     * its id. The work runs on a background executor; poll [findReport] until [AnalysisState.isTerminal].
     *
     * Asynchronous because two model calls plus a database cold start routinely take longer than
     * a proxy will hold an HTTP request open.
     */
    fun start(offerId: Long, profileId: Long): Long

    fun findReport(analysisId: Long): AnalysisReport?

    /** Falls back to the default profile when [profileId] is not given. */
    fun latestForOffer(offerId: Long, profileId: Long? = null): AnalysisReport?

    /**
     * Every saved offer with the score of its latest completed analysis, ranked best-match first.
     * Falls back to the default profile when [profileId] is not given, and to no profile at all -
     * every entry unscored - when none exists.
     *
     * Lives here rather than on `OfferService` because the score does. `offer` depends on nothing,
     * and the edge that would let it read `analysis` is the one `analysis` already occupies in the
     * other direction; joining on this side is a new type on an existing edge, the way
     * `ProfileCoverage` was.
     */
    fun shortlist(profileId: Long? = null): OfferShortlist

    /**
     * Demand and gap counts across every completed analysis of the given profile. A single offer
     * tells you what you lack for that job; this tells you what to learn. Falls back to the default
     * profile when [profileId] is not given.
     */
    fun aggregateGaps(profileId: Long? = null): AggregateGapReport
}
