package com.jankowski.rafal.jobassistant.analysis.internal

/**
 * Thrown when extraction returned no requirements at all.
 *
 * The gap report is the product, so an empty one is not a quiet result - it reads as "this offer
 * asks for nothing" or "you match everything", and the application has no basis for either. That is
 * the same rule the rest of the design serves from the other side: asserting an *absence* of gaps
 * is as unfounded as inventing experience.
 *
 * `AnalysisRunner` catches every exception and records it on the analysis row, so throwing here is
 * what turns a confidently empty answer into a FAILED run with a reason a human can read.
 */
internal class EmptyExtractionException(offerId: Long) : RuntimeException(
    "Extraction returned no requirements for offer $offerId. The offer text may be too short to " +
        "carry any, but far more often the model answered with an empty or unrelated object - " +
        "check the response on the most recent llm_call row for this analysis."
)
