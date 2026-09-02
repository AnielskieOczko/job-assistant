package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillCategory
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ExperienceBullet
import com.jankowski.rafal.jobassistant.profile.ProfileLink
import com.jankowski.rafal.jobassistant.profile.ProfilePortrait
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

internal data class CvView(
    val fullName: String,
    val headline: String?,
    val summaryLine: String?,
    val contacts: List<String>,
    val links: List<ProfileLink>,
    /**
     * The candidate's skills sorted into the catalog's own categories, because a recruiter
     * scanning for a language should not have to read an undifferentiated run of thirty chips.
     */
    val skillGroups: List<CvSkillGroupView>,
    val experiences: List<CvRoleView>,
    val education: List<CvEducationView>,
    val credentials: List<CvCredentialView>,
    val projects: List<CvProjectView>,
    val languages: List<String>,
    /**
     * The candidate's photograph as a `data:` URI, or null when there is none.
     *
     * A URI rather than a URL, and inlined rather than linked, for the reason the fonts are:
     * `PlaywrightDocumentRenderer` calls `setContent` with no base URL, so `/api/profiles/1/portrait`
     * has no origin to be relative to and would render as a broken image.
     *
     * It is added here by the renderer, after the model has answered - a portrait is a direct
     * identifier and follows the rule the candidate's name already follows.
     *
     * Deliberately without a default: nullable *and* defaulted is how a caller silently forgets to
     * pass it, which is exactly what happened while this was being written.
     */
    val portrait: String?,
)

internal data class CvSkillGroupView(val category: String, val names: List<String>)

internal data class CvRoleView(
    val company: String,
    val roleTitle: String,
    val period: String,
    val bullets: List<String>,
    /** @see DocumentViews.badgeSkills - the union over [bullets], never over the role's full list. */
    val skills: List<String>,
)

internal data class CvEducationView(val summary: String, val period: String)

internal data class CvCredentialView(val title: String, val issuer: String, val period: String)

/**
 * The URL is rendered here even though [com.jankowski.rafal.jobassistant.document.internal.ProfileBriefing]
 * never sends it to a model - the same treatment the candidate's name and portrait already get. It
 * reaches the finished document straight from the profile, never through a prompt.
 */
internal data class CvProjectView(
    val name: String,
    val url: String?,
    val period: String,
    val bullets: List<String>,
    val skills: List<String>,
)

internal data class CoverLetterView(
    val fullName: String,
    val contacts: List<String>,
    val links: List<ProfileLink>,
    val roleLine: String?,
    val paragraphs: List<String>,
)

internal object DocumentViews {

    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy")

    /**
     * The order skill categories appear in on a CV, most concrete first.
     *
     * Declared here rather than taken from `SkillCategory.ordinal`: the enum's declaration order
     * happens to be close to this, which is exactly what makes depending on it a trap - reordering
     * it for readability would silently reshuffle every CV. A category missing from this list is
     * appended rather than dropped, so adding one to the catalog cannot make a held skill vanish
     * from the document.
     */
    private val CATEGORY_ORDER = listOf(
        SkillCategory.LANGUAGE,
        SkillCategory.FRAMEWORK,
        SkillCategory.DATABASE,
        SkillCategory.MESSAGING,
        SkillCategory.CLOUD,
        SkillCategory.DEVOPS,
        SkillCategory.TESTING,
        SkillCategory.FRONTEND,
        SkillCategory.AI,
        SkillCategory.TOOL,
        SkillCategory.PRACTICE,
        SkillCategory.SOFT,
        SkillCategory.OTHER,
    )

    /**
     * Groups already-selected skill *names* into their catalog categories.
     *
     * Takes names rather than ids because that is what survives selection: [CvSelection] has
     * already dropped anything the profile cannot back, and re-resolving here only adds the
     * category the layout needs. A name the catalog no longer knows is kept under
     * [SkillCategory.OTHER] rather than discarded - it passed the fabrication guard on the way in,
     * and silently removing a held skill from a CV is a worse failure than an odd heading.
     */
    fun skillGroups(names: List<String>, catalog: SkillCatalog): List<CvSkillGroupView> {
        val byCategory = names.groupBy { catalog.resolve(it)?.category ?: SkillCategory.OTHER }
        return CATEGORY_ORDER
            .plus(byCategory.keys.filterNot { it in CATEGORY_ORDER })
            .distinct()
            .mapNotNull { category ->
                byCategory[category]?.let { CvSkillGroupView(category.name, it) }
            }
    }

    /**
     * The skills a role's badge row shows: the union of what its bullets evidence, in
     * first-appearance order, so the technology the leading bullet rests on leads.
     *
     * **The union is taken over the bullets that actually render.** A skill whose only evidence
     * was dropped during tailoring must not survive into the badge row, or the CV displays a claim
     * that nothing on the page backs - the same rule [CvSelection] applies to bullets, restated
     * for presentation.
     */
    fun badgeSkills(bullets: List<ExperienceBullet>, catalog: SkillCatalog): List<String> =
        bullets.flatMap { it.skillIds }
            .distinct()
            .mapNotNull { catalog.findById(it)?.name }

    /** `data:image/jpeg;base64,...` - see [CvView.portrait] for why this is not a URL. */
    fun portraitDataUri(portrait: ProfilePortrait?): String? = portrait?.let {
        "data:${it.mediaType};base64," + Base64.getEncoder().encodeToString(it.bytes)
    }

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
