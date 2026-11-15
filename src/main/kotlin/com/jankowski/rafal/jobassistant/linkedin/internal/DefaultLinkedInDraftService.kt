package com.jankowski.rafal.jobassistant.linkedin.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.catalog.SkillMentions
import com.jankowski.rafal.jobassistant.linkedin.LinkedInDraft
import com.jankowski.rafal.jobassistant.linkedin.LinkedInDraftService
import com.jankowski.rafal.jobassistant.linkedin.LinkedInSection
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.ChatModelRegistry
import com.jankowski.rafal.jobassistant.llm.LlmCallScope
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.springframework.stereotype.Service

@Service
internal class DefaultLinkedInDraftService(
    private val profiles: ProfileService,
    private val catalog: SkillCatalog,
    private val aiServices: AiServiceFactory,
    private val models: ChatModelRegistry,
    private val repository: LinkedInDraftRepository,
) : LinkedInDraftService {

    override fun draft(
        profileId: Long,
        section: LinkedInSection,
        subjectId: Long?,
        notes: String?,
    ): LinkedInDraft {
        val profile = profiles.require(profileId)
        val allSkills = catalog.findAll()
        val skillNamesById = allSkills.associate { it.id to it.name }

        val context = buildPiiFreeContext(profile, section, subjectId, skillNamesById)
        val guidance = guidanceFor(section)
        val cleanNotes = notes?.trim().orEmpty().ifEmpty { "None" }

        val content = LlmCallScope.forProfile(profileId) {
            aiServices.create(LinkedInDrafter::class.java, LlmTask.LINKEDIN)
                .draft(
                    section = section.name,
                    guidance = guidance,
                    context = context,
                    notes = cleanNotes,
                )
                .safeContent()
        }

        check(content.isNotBlank()) { "Model returned empty LinkedIn draft for $section." }

        val unheld = SkillMentions.unheld(content, allSkills, profile.heldSkillIds)

        return repository.save(
            profileId = profileId,
            section = section,
            subjectId = subjectId,
            profileRevision = profile.revision,
            content = content,
            unheldSkills = unheld,
            modelProfile = models.profileNameFor(LlmTask.LINKEDIN),
        )
    }

    override fun list(profileId: Long): List<LinkedInDraft> {
        val revision = profiles.revision(profileId)
        return repository.listForProfile(profileId, revision)
    }

    private fun buildPiiFreeContext(
        profile: CandidateProfile,
        section: LinkedInSection,
        subjectId: Long?,
        skillNamesById: Map<Long, String>,
    ): String {
        val heldSkills = profile.heldSkillIds.mapNotNull { skillNamesById[it] }.sorted()

        return when (section) {
            LinkedInSection.HEADLINE, LinkedInSection.ABOUT, LinkedInSection.SKILL_OR_CREDENTIAL_POST -> {
                buildString {
                    profile.details.headline?.let { appendLine("Headline: $it") }
                    profile.details.summary?.let { appendLine("Summary: $it") }
                    profile.details.careerGoal?.let { appendLine("Career goal: $it") }
                    appendLine("Skills: ${heldSkills.joinToString(", ")}")
                    if (profile.experiences.isNotEmpty()) {
                        appendLine("Experience:")
                        profile.experiences.forEach { exp ->
                            appendLine("- ${exp.roleTitle} at ${exp.company} (${exp.startedOn}..${exp.endedOn ?: "present"})")
                            exp.bullets.forEach { appendLine("  * ${it.text}") }
                        }
                    }
                    if (profile.projects.isNotEmpty()) {
                        appendLine("Projects:")
                        profile.projects.forEach { proj ->
                            appendLine("- ${proj.name}: ${proj.description.orEmpty()}")
                            proj.bullets.forEach { appendLine("  * ${it.text}") }
                        }
                    }
                    if (profile.credentials.isNotEmpty()) {
                        appendLine("Credentials:")
                        profile.credentials.forEach { cred ->
                            appendLine("- ${cred.title} (${cred.issuer}, ${cred.kind})")
                        }
                    }
                }.trim()
            }

            LinkedInSection.EXPERIENCE_ENTRY -> {
                requireNotNull(subjectId) { "EXPERIENCE_ENTRY requires subjectId." }
                val exp = profile.experiences.firstOrNull { it.id == subjectId }
                    ?: throw IllegalArgumentException("No experience $subjectId on profile.")
                buildString {
                    appendLine("Role: ${exp.roleTitle} at ${exp.company} (${exp.startedOn}..${exp.endedOn ?: "present"})")
                    exp.summary?.let { appendLine("Summary: $it") }
                    exp.bullets.forEach { appendLine("- ${it.text}") }
                    appendLine("Held skills: ${heldSkills.joinToString(", ")}")
                }.trim()
            }

            LinkedInSection.PROJECT_ENTRY -> {
                requireNotNull(subjectId) { "PROJECT_ENTRY requires subjectId." }
                val proj = profile.projects.firstOrNull { it.id == subjectId }
                    ?: throw IllegalArgumentException("No project $subjectId on profile.")
                val projSkills = proj.skillIds.mapNotNull { skillNamesById[it] }.sorted()
                buildString {
                    appendLine("Project: ${proj.name}")
                    proj.description?.let { appendLine("Description: $it") }
                    if (projSkills.isNotEmpty()) appendLine("Project skills: ${projSkills.joinToString(", ")}")
                    proj.bullets.forEach { appendLine("- ${it.text}") }
                }.trim()
            }
        }
    }

    private fun guidanceFor(section: LinkedInSection): String = when (section) {
        LinkedInSection.HEADLINE -> "Write a sharp, searchable LinkedIn headline (max 220 characters)."
        LinkedInSection.ABOUT -> "Write a first-person LinkedIn About section (2-3 short paragraphs + core stack)."
        LinkedInSection.EXPERIENCE_ENTRY -> "Write a concise summary and 3-5 impact-oriented bullet points for this LinkedIn experience entry."
        LinkedInSection.PROJECT_ENTRY -> "Write a clear LinkedIn Projects description explaining what was built and with which technologies."
        LinkedInSection.SKILL_OR_CREDENTIAL_POST -> "Write a brief, authentic LinkedIn update post sharing recent learning, certification, or project progress."
    }
}
