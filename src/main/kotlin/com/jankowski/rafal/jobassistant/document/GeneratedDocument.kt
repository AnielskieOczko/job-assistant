package com.jankowski.rafal.jobassistant.document

import java.time.Instant

enum class DocumentType { CV, COVER_LETTER }

data class GeneratedDocument(
    val id: Long,
    val offerId: Long,
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
