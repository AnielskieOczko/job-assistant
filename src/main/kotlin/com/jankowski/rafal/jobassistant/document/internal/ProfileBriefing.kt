package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.analysis.AnalysisReport
import com.jankowski.rafal.jobassistant.analysis.Importance
import com.jankowski.rafal.jobassistant.analysis.RequirementStatus
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.CandidateProfile

/**
 * Renders the profile and the analysis into the text the tailoring model is shown.
 *
 * The bullet ids are the important part: the model selects and rewrites *by id*, which is what
 * lets every line of the finished CV be traced back to a profile record instead of being taken on
 * trust.
 */
internal object ProfileBriefing {

    fun profile(profile: CandidateProfile, catalog: SkillCatalog): String = buildString {
        // The candidate's name is deliberately absent. No prompt references it, and the model
        // cannot return it - TailoredCv and CoverLetter have no contact fields - so the rendered
        // header is rebuilt from the profile afterwards. Sending it bought nothing and disclosed
        // the strongest identifier there is. PromptPrivacyInvariant enforces that it stays out.
        profile.details.headline?.let { appendLine("Headline: $it") }
        appendLine()

        // An aspiration, not a capability - it must supply direction and motivation only. Naming a
        // technology from this line that is absent from the skills list below still fails
        // CvInvariant, exactly as if it had been invented anywhere else in the document.
        profile.details.careerGoal?.ifBlank { null }?.let {
            appendLine("The candidate's stated goal (an aspiration, not experience - do not name any technology from this line that is not already in the skills list below):")
            appendLine(it)
            appendLine()
        }

        appendLine("Skills (these are the ONLY skills that exist):")
        profile.skills.forEach { skill ->
            val name = catalog.findById(skill.skillId)?.name ?: return@forEach
            val years = skill.yearsOfExperience?.let { ", $it years" } ?: ""
            appendLine("- $name (${skill.proficiency}$years)")
        }
        appendLine()

        if (profile.credentials.isNotEmpty()) {
            appendLine("Credentials:")
            profile.credentials.forEach { credential ->
                val year = credential.issuedOn?.year?.let { " ($it)" } ?: ""
                appendLine("- ${credential.title} — ${credential.issuer}$year")
            }
            appendLine()
        }

        appendLine("Experience:")
        profile.experiences.forEach { experience ->
            val period = DocumentViews.period(experience.startedOn, experience.endedOn)
            appendLine("${experience.roleTitle} at ${experience.company} ($period)")
            experience.bullets.forEach { bullet ->
                val skills = bullet.skillIds.mapNotNull { catalog.findById(it)?.name }
                appendLine("  [id=${bullet.id}] ${bullet.text}  (evidences: ${skills.joinToString().ifBlank { "nothing specific" }})")
            }
            appendLine()
        }

        // A project's URL is never appended here - it never reaches a prompt, the same rule the
        // candidate's name already follows. PromptPrivacyInvariant enforces that it stays out.
        if (profile.projects.isNotEmpty()) {
            appendLine("Projects:")
            profile.projects.forEach { project ->
                val skills = project.skillIds.mapNotNull { catalog.findById(it)?.name }
                val skillsSuffix = if (skills.isNotEmpty()) " (uses: ${skills.joinToString()})" else ""
                appendLine(project.name + skillsSuffix)
                project.bullets.forEach { bullet ->
                    val bulletSkills = bullet.skillIds.mapNotNull { catalog.findById(it)?.name }
                    appendLine("  [id=${bullet.id}] ${bullet.text}  (evidences: ${bulletSkills.joinToString().ifBlank { "nothing specific" }})")
                }
                appendLine()
            }
        }

        if (profile.languages.isNotEmpty()) {
            appendLine("Languages: " + profile.languages.joinToString { "${it.language} (${it.level})" })
        }
    }.trim()

    fun requirements(report: AnalysisReport): String = buildString {
        appendLine("Must-haves:")
        appendRequirements(report, Importance.MUST_HAVE)
        appendLine()
        appendLine("Nice-to-haves:")
        appendRequirements(report, Importance.NICE_TO_HAVE)
    }.trim()

    private fun StringBuilder.appendRequirements(report: AnalysisReport, importance: Importance) {
        val relevant = report.requirements
            .filter { it.importance == importance && it.status != RequirementStatus.UNRESOLVED }

        if (relevant.isEmpty()) {
            appendLine("(none)")
            return
        }
        relevant.forEach {
            val name = it.skillName ?: it.rawText
            append("- ").append(name).append(" [").append(it.status).append(']')
            if (it.status == RequirementStatus.PARTIAL || it.status == RequirementStatus.MET) {
                it.evidence?.let { evidence -> append(" — ").append(evidence) }
            }
            appendLine()
        }
    }
}
