package com.jankowski.rafal.jobassistant.offer.internal

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

internal interface JobOfferRepository : CrudRepository<JobOfferRow, Long> {
    fun findByContentHash(contentHash: String): JobOfferRow?

    @Query("select * from job_offer order by created_at desc")
    fun findAllNewestFirst(): List<JobOfferRow>
}

internal interface ApplicationRepository : CrudRepository<ApplicationRow, Long> {
    fun findByJobOfferId(jobOfferId: Long): ApplicationRow?

    @Query("select * from application")
    fun findAllApplications(): List<ApplicationRow>
}
