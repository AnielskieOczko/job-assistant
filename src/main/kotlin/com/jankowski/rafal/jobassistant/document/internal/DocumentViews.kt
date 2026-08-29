package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileLink
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class CvView(
    val fullName: String,
    val headline: String?,
    val summaryLine: String?,
    val contacts: List<String>,
    val links: List<ProfileLink>,
    val skills: List<String>,
    val experiences: List<CvRoleView>,
    val education: List<CvEducationView>,
    val credentials: List<CvCredentialView>,
    val languages: List<String>,
)

internal data class CvRoleView(
    val company: String,
    val roleTitle: String,
    val period: String,
    val bullets: List<String>,
)

internal data class CvEducationView(val summary: String, val period: String)

internal data class CvCredentialView(val title: String, val issuer: String, val period: String)

internal data class CoverLetterView(
    val fullName: String,
    val contacts: List<String>,
    val links: List<ProfileLink>,
    val roleLine: String?,
    val paragraphs: List<String>,
)

internal object DocumentViews {

    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy")

    fun contactsOf(profile: CandidateProfile): List<String> = listOfNotNull(
        profile.details.email,
        profile.details.phone,
        profile.details.location,
    )

    fun period(from: LocalDate?, to: LocalDate?, presentLabel: String = "present"): String = when {
        from == null && to == null -> ""
        from == null -> to!!.format(MONTH_YEAR)
        else -> "${from.format(MONTH_YEAR)} — ${to?.format(MONTH_YEAR) ?: presentLabel}"
    }

    fun educationSummary(institution: String, degree: String, fieldOfStudy: String?): String =
        listOfNotNull(degree, fieldOfStudy).joinToString(" in ") + ", " + institution

    /**
     * Unlike [period], a missing expiry is never rendered as "present" - most credentials never
     * expire, and borrowing the employment-history idiom here would misrepresent that.
     */
    fun credentialPeriod(issuedOn: LocalDate?, expiresOn: LocalDate?): String = when {
        issuedOn == null && expiresOn == null -> ""
        expiresOn == null -> "Issued ${issuedOn!!.format(MONTH_YEAR)}"
        issuedOn == null -> "Expires ${expiresOn.format(MONTH_YEAR)}"
        else -> "Issued ${issuedOn.format(MONTH_YEAR)} · Expires ${expiresOn.format(MONTH_YEAR)}"
    }
}
