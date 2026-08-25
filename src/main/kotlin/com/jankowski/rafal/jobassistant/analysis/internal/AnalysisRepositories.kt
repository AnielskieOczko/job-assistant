package com.jankowski.rafal.jobassistant.analysis.internal

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

internal interface AnalysisRepository : CrudRepository<AnalysisRow, Long> {

    @Query(
        "select * from analysis where job_offer_id = :offerId and profile_id = :profileId " +
            "order by created_at desc limit 1"
    )
    fun findLatestForOfferAndProfile(offerId: Long, profileId: Long): AnalysisRow?

    @Query("select * from analysis where state not in ('DONE', 'FAILED')")
    fun findUnfinished(): List<AnalysisRow>

    /** Distinct offers with a completed analysis - re-running one offer must not count twice. */
    @Query("select count(distinct job_offer_id) from analysis where state = 'DONE' and profile_id = :profileId")
    fun countAnalysedOffers(profileId: Long): Int
}

internal interface OfferRequirementRepository : CrudRepository<OfferRequirementRow, Long> {
    @Query("select * from offer_requirement where analysis_id = :analysisId order by display_order")
    fun findForAnalysis(analysisId: Long): List<OfferRequirementRow>
}

internal interface LanguageRequirementRepository : CrudRepository<LanguageRequirementRow, Long> {
    @Query("select * from language_requirement where analysis_id = :analysisId order by language")
    fun findForAnalysis(analysisId: Long): List<LanguageRequirementRow>
}

internal interface LearningPlanItemRepository : CrudRepository<LearningPlanItemRow, Long> {
    @Query("select * from learning_plan_item where analysis_id = :analysisId order by priority")
    fun findForAnalysis(analysisId: Long): List<LearningPlanItemRow>
}
