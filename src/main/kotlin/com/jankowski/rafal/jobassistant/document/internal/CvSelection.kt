package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.CandidateProfile

/**
 * The model's tailoring choices, filtered down to what the profile can actually back.
 *
 * Fabrication is prevented here by construction rather than by inspection: a bullet id that is not
 * in the profile has no text to render, and a skill name that is not held is simply not carried
 * through. What the free-text summary and rewritten bullets say is a separate problem, and that is
 * what [CvInvariant] is for.
 */
internal data class CvSelection(
    val summaryLine: String?,
    val skillNames: List<String>,
    val bulletOrder: List<Long>,
    val rewrittenText: Map<Long, String>,
    val droppedBulletIds: List<Long>,
    val droppedSkillNames: List<String>,
) {

    fun toView(profile: CandidateProfile, catalog: SkillCatalog): CvView {
        val positionOf = bulletOrder.withIndex().associate { (index, id) -> id to index }
        val selected = bulletOrder.toSet()

        val roles = profile.experiences.mapNotNull { experience ->
            val bullets = experience.bullets
                .filter { it.id in selected }
                .sortedBy { positionOf[it.id] }
                .map { rewrittenText[it.id] ?: it.text }

            // A role with no surviving bullets still appears: an unexplained employment gap reads
            // worse than a role with only a heading.
            CvRoleView(
                company = experience.company,
                roleTitle = experience.roleTitle,
                period = DocumentViews.period(experience.startedOn, experience.endedOn),
                bullets = bullets,
            )
        }

        return CvView(
            fullName = profile.details.fullName,
            headline = profile.details.headline,
            summaryLine = summaryLine ?: profile.details.summary,
            contacts = DocumentViews.contactsOf(profile),
            links = profile.links,
            skills = skillNames,
            experiences = roles,
            education = profile.education.map {
                CvEducationView(
                    summary = DocumentViews.educationSummary(it.institution, it.degree, it.fieldOfStudy),
                    period = DocumentViews.period(it.startedOn, it.endedOn),
                )
            },
            credentials = profile.credentials.map {
                CvCredentialView(
                    title = it.title,
                    issuer = it.issuer,
                    period = DocumentViews.credentialPeriod(it.issuedOn, it.expiresOn),
                )
            },
            projects = profile.projects.map { project ->
                CvProjectView(
                    name = project.name,
                    url = project.url,
                    period = DocumentViews.period(project.startedOn, project.endedOn),
                    bullets = project.bullets
                        .filter { it.id in selected }
                        .sortedBy { positionOf[it.id] }
                        .map { rewrittenText[it.id] ?: it.text },
                )
            },
            languages = profile.languages.map { "${it.language} (${it.level})" },
        )
    }

    companion object {

        fun from(tailored: TailoredCv, profile: CandidateProfile, catalog: SkillCatalog): CvSelection {
            // Not dead code, however much the compiler protests. LangChain4j deserialises a service
            // return type reflectively, without the Jackson Kotlin module, so it never enforces
            // Kotlin nullability: a model that emits `"bullets": null` produces a null here despite
            // the non-null type. The default values on TailoredCv cover a *missing* key only.
            @Suppress("USELESS_ELVIS")
            val requestedBullets = tailored.bullets ?: emptyList()

            @Suppress("USELESS_ELVIS")
            val requestedSkills = tailored.skillNames ?: emptyList()

            @Suppress("USELESS_ELVIS")
            val summary = tailored.summaryLine ?: ""

            val bulletsById = profile.bullets.associateBy { it.id }

            val requestedIds = requestedBullets.map { it.bulletId }
            val keptIds = requestedIds.filter { it in bulletsById }.distinct()
            val dropped = requestedIds.filterNot { it in bulletsById }.distinct()

            // An empty or wholly invalid selection would produce a CV with no experience at all,
            // which is worse than an untailored one.
            val bulletOrder = keptIds.ifEmpty { profile.bullets.map { it.id } }

            val rewritten = requestedBullets
                .filter { it.bulletId in bulletsById && it.text.isNotBlank() }
                .associate { it.bulletId to it.text.trim() }

            val heldNames = profile.heldSkillIds.mapNotNull { catalog.findById(it)?.name }
            val keptSkills = requestedSkills.mapNotNull { requested ->
                catalog.resolve(requested)?.takeIf { it.id in profile.heldSkillIds }?.name
            }.distinct()
            val droppedSkills = requestedSkills.filter { requested ->
                catalog.resolve(requested)?.takeIf { it.id in profile.heldSkillIds } == null
            }.distinct()

            return CvSelection(
                summaryLine = summary.trim().ifBlank { null },
                skillNames = keptSkills.ifEmpty { heldNames },
                bulletOrder = bulletOrder,
                rewrittenText = rewritten,
                droppedBulletIds = dropped,
                droppedSkillNames = droppedSkills,
            )
        }
    }
}
