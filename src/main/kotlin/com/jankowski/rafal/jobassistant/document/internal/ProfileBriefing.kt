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
        appendLine("Name: ${profile.details.fullName}")
        profile.details.headline?.let { appendLine("Headline: $it") }
        appendLine()

        appendLine("Skills (these are the ONLY skills that exist):")
        profile.skills.forEach { skill ->
            val name = catalog.findById(skill.skillId)?.name ?: return@forEach
            val years = skill.yearsOfExperience?.let { ", $it years" } ?: ""
            appendLine("- $name (${skill.proficiency}$years)")
        }
        appendLine()

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
