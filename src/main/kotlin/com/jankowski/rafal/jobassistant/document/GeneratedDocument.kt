package com.jankowski.rafal.jobassistant.document

import java.time.Instant

enum class DocumentType { CV, COVER_LETTER }

data class GeneratedDocument(
    val id: Long,
    val offerId: Long,
    /** Which profile this document was tailored to. */
    val profileId: Long = 0,
    val analysisId: Long?,
    val type: DocumentType,
    val language: String,
    val html: String,
    val createdAt: Instant,
    /**
     * Profile revision this document was built from. The stored HTML was true when it was written,
     * so a trailing revision does not make the document wrong - it makes it out of date.
     */
    val profileRevision: Long? = null,
    /**
     * How many of the model's choices had nothing behind them and were discarded.
     *
     * Not a warning about this document - selection drops them, so what was rendered is backed by
     * the profile either way. It is the fabrication rate, measured on real offers rather than on
     * fixtures, and worth watching: a number that climbs after a prompt or model change is the
     * earliest signal that tailoring has started guessing. Always zero for a cover letter, which
     * selects nothing.
     */
    val droppedBulletCount: Int = 0,
    val droppedSkillCount: Int = 0,
)

/**
 * Raised when a generated document would claim a skill the profile does not contain.
 *
 * This is a hard failure rather than a warning: the entire value of the tool depends on never
 * putting a technology you have not used in front of an employer.
 */
class FabricatedClaimException(val claims: List<String>) : RuntimeException(
    "Refusing to produce a document claiming skills absent from the profile: ${claims.joinToString()}"
)
