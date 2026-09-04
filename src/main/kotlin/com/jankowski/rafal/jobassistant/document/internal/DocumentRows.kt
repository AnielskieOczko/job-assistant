package com.jankowski.rafal.jobassistant.document.internal

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import java.time.Instant

@Table("generated_document")
internal data class GeneratedDocumentRow(
    @Id val id: Long? = null,
    val jobOfferId: Long,
    val profileId: Long,
    val analysisId: Long?,
    val type: String,
    val language: String,
    val html: String,
    val selectionJson: String,
    /** Bullet ids the model cited that the profile does not contain. See V12. */
    val droppedBulletCount: Int = 0,
    /** Skill names the model claimed that the profile does not hold. See V12. */
    val droppedSkillCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    /** Profile revision this document was built from - null for rows that predate the counter. */
    val profileRevision: Long? = null,
    /** The consent clause language rendered onto this document, or null if none matched. See V23. */
    val consentClauseLanguage: String? = null,
    /** The document this was reused from, or null when this row was freshly tailored. See V29. */
    val sourceDocumentId: Long? = null,
)

internal interface GeneratedDocumentRepository : CrudRepository<GeneratedDocumentRow, Long> {

    @Query(
        """
        select * from generated_document
        where job_offer_id = :offerId and profile_id = :profileId and type = :type
        order by created_at desc limit 1
        """
    )
    fun findLatest(offerId: Long, profileId: Long, type: String): GeneratedDocumentRow?

    @Query("select * from generated_document where profile_id = :profileId order by created_at desc")
    fun findByProfileId(profileId: Long): List<GeneratedDocumentRow>
}
