package com.jankowski.rafal.jobassistant.analysis

/**
 * Which rule produced an analysis's [AnalysisReport.matchScore].
 *
 * Versioned rather than migrated. `matchScore` is read from storage while
 * [AnalysisReport.scoreExplanation] recomputes its denominator from the stored requirements, so a
 * silent change of rule would leave every existing report contradicting its own explanation.
 * Recomputing old scores instead would rewrite a number past decisions were made on, and could not
 * rewrite the model-written summary that already narrates the old percentage.
 *
 * So each analysis says how it was scored, and explains itself in those terms.
 */
enum class ScoringRule {

    /** Every resolvable must-have counted, soft skills included. Everything before V18. */
    V1_ALL_CATEGORIES,

    /**
     * Soft skills reported but excluded from the score.
     *
     * A "Communication" must-have the profile does not declare belongs in the gap report - it is a
     * real thing the offer asked for - but counting it makes the number answer a question it cannot
     * answer. The score says how *technically* qualified the candidate is, and no catalog lookup
     * can tell you whether someone communicates well.
     */
    V2_SOFT_EXCLUDED;

    companion object {
        /** What a new run writes. */
        val CURRENT = V2_SOFT_EXCLUDED
    }
}
