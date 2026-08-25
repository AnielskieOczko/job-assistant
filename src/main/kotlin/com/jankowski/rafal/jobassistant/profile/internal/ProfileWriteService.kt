package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.springframework.data.repository.CrudRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Per-entity editing of the profile.
 *
 * Internal on purpose: no other module has any business writing a single skill or bullet, and the
 * cross-module contract stays "read it, or replace it wholesale". Every mutation bumps the profile
 * revision and returns the whole profile, so a caller never has to reassemble one from a patch
 * response and the UI has a single query to invalidate.
 */
@Service
internal class ProfileWriteService(
    private val profiles: ProfileService,
    private val catalog: SkillCatalog,
    private val links: ProfileLinkRepository,
    private val skills: ProfileSkillRepository,
    private val experiences: WorkExperienceRepository,
    private val bullets: ExperienceBulletRepository,
    private val education: EducationRepository,
    private val languages: LanguageSkillRepository,
    private val jdbc: JdbcClient,
) {

    // ---------------------------------------------------------------- details

    /**
     * Creates the profile if there is none. This is the only endpoint that can bring a profile into
     * existence without a document, which is what stops a fresh database from forcing the user to
     * hand-write JSON before they can do anything at all.
     */
    @Transactional
    fun putDetails(request: DetailsRequest): CandidateProfile {
        jdbc.sql(
            """
            insert into profile_details (id, full_name, headline, email, phone, location, summary)
            values (1, :fullName, :headline, :email, :phone, :location, :summary)
            on conflict (id) do update set
                full_name = excluded.full_name,
                headline  = excluded.headline,
                email     = excluded.email,
                phone     = excluded.phone,
                location  = excluded.location,
                summary   = excluded.summary
            """
        )
            .param("fullName", request.fullName)
            .param("headline", request.headline)
            .param("email", request.email)
            .param("phone", request.phone)
            .param("location", request.location)
            .param("summary", request.summary)
            .update()
        return commit()
    }

    // ------------------------------------------------------------------ links

    @Transactional
    fun addLink(request: LinkRequest): CandidateProfile {
        links.save(ProfileLinkRow(label = request.label, url = request.url, displayOrder = nextOrder("profile_link")))
        return commit()
    }

    @Transactional
    fun updateLink(id: Long, request: LinkRequest): CandidateProfile {
        val row = links.findById(id).orElseThrow { unknown("link", id) }
        links.save(row.copy(label = request.label, url = request.url))
        return commit()
    }

    @Transactional
    fun deleteLink(id: Long): CandidateProfile = deleteFrom(links, id, "link")

    @Transactional
    fun reorderLinks(ids: List<Long>): CandidateProfile =
        reorder("profile_link", ids, links.findAllOrdered().map { it.id!! })

    // ----------------------------------------------------------------- skills

    @Transactional
    fun addSkill(request: SkillRequest): CandidateProfile {
        val skill = catalog.findById(request.skillId)
            ?: throw UnknownProfileEntityException(
                "No canonical skill ${request.skillId}. Add it to the catalog first."
            )
        skills.findByCanonicalSkillId(skill.id)?.let {
            throw ProfileConflictException("You already hold ${skill.name}. Edit that entry instead of adding it again.")
        }
        skills.save(
            ProfileSkillRow(
                canonicalSkillId = skill.id,
                proficiency = request.proficiency.name,
                yearsOfExperience = request.yearsOfExperience,
                lastUsedYear = request.lastUsedYear,
                displayOrder = nextOrder("profile_skill"),
            )
        )
        return commit()
    }

    @Transactional
    fun updateSkill(id: Long, request: SkillUpdateRequest): CandidateProfile {
        val row = skills.findById(id).orElseThrow { unknown("skill", id) }
        skills.save(
            row.copy(
                proficiency = request.proficiency.name,
                yearsOfExperience = request.yearsOfExperience,
                lastUsedYear = request.lastUsedYear,
            )
        )
        return commit()
    }

    /**
     * Refuses while any bullet still cites the skill.
     *
     * There is no foreign key between `profile_skill` and `experience_bullet_skill`, so nothing in
     * the database stops this - and cascading would quietly delete the evidence linking a claim to
     * the work behind it. Naming the bullets lets the user decide what to do with them.
     */
    @Transactional
    fun deleteSkill(id: Long): CandidateProfile {
        val row = skills.findById(id).orElseThrow { unknown("skill", id) }
        val blocking = bullets.findTaggedWith(row.canonicalSkillId)
        if (blocking.isNotEmpty()) {
            val name = catalog.findById(row.canonicalSkillId)?.name ?: "skill ${row.canonicalSkillId}"
            throw ProfileConflictException(
                "$name is still cited by ${blocking.size} bullet(s). Untag them first, or delete them.",
                blocking.map { BlockingBullet(it.id!!, it.text) },
            )
        }
        skills.delete(row)
        return commit()
    }

    @Transactional
    fun reorderSkills(ids: List<Long>): CandidateProfile =
        reorder("profile_skill", ids, skills.findAllOrdered().map { it.id!! })

    // ------------------------------------------------------------ experiences

    @Transactional
    fun addExperience(request: ExperienceRequest): CandidateProfile {
        requireDatesOrdered(request)
        experiences.save(
            WorkExperienceRow(
                company = request.company,
                roleTitle = request.roleTitle,
                location = request.location,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                summary = request.summary,
                displayOrder = nextOrder("work_experience"),
            )
        )
        return commit()
    }

    /**
     * Bullet ids are untouched by this. That is the whole reason bullets are their own aggregate:
     * while they were an owned collection, Spring Data JDBC reinserted every one of them here, and
     * a CV generated last week would stop matching the profile because a job title was corrected.
     */
    @Transactional
    fun updateExperience(id: Long, request: ExperienceRequest): CandidateProfile {
        requireDatesOrdered(request)
        val row = experiences.findById(id).orElseThrow { unknown("experience", id) }
        experiences.save(
            row.copy(
                company = request.company,
                roleTitle = request.roleTitle,
                location = request.location,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                summary = request.summary,
            )
        )
        return commit()
    }

    /** Bullets and their tags cascade from the `work_experience` row. */
    @Transactional
    fun deleteExperience(id: Long): CandidateProfile = deleteFrom(experiences, id, "experience")

    @Transactional
    fun reorderExperiences(ids: List<Long>): CandidateProfile =
        reorder("work_experience", ids, experiences.findAllOrdered().map { it.id!! })

    // ---------------------------------------------------------------- bullets

    @Transactional
    fun addBullet(experienceId: Long, request: BulletRequest): CandidateProfile {
        if (!experiences.existsById(experienceId)) throw unknown("experience", experienceId)
        requireDeclared(request.skillIds)
        bullets.save(
            ExperienceBulletRow(
                workExperienceId = experienceId,
                text = request.text,
                displayOrder = nextOrder("experience_bullet", "work_experience_id = $experienceId"),
                skills = request.skillIds.mapTo(mutableSetOf()) { ExperienceBulletSkillRow(it) },
            )
        )
        return commit()
    }

    @Transactional
    fun updateBullet(id: Long, request: BulletRequest): CandidateProfile {
        val row = bullets.findById(id).orElseThrow { unknown("bullet", id) }
        requireDeclared(request.skillIds)
        bullets.save(
            row.copy(
                text = request.text,
                skills = request.skillIds.mapTo(mutableSetOf()) { ExperienceBulletSkillRow(it) },
            )
        )
        return commit()
    }

    @Transactional
    fun deleteBullet(id: Long): CandidateProfile = deleteFrom(bullets, id, "bullet")

    @Transactional
    fun reorderBullets(experienceId: Long, ids: List<Long>): CandidateProfile {
        if (!experiences.existsById(experienceId)) throw unknown("experience", experienceId)
        return reorder("experience_bullet", ids, bullets.findByExperience(experienceId).map { it.id!! })
    }

    // -------------------------------------------------------------- education

    @Transactional
    fun addEducation(request: EducationRequest): CandidateProfile {
        education.save(
            EducationRow(
                institution = request.institution,
                degree = request.degree,
                fieldOfStudy = request.fieldOfStudy,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                displayOrder = nextOrder("education"),
            )
        )
        return commit()
    }

    @Transactional
    fun updateEducation(id: Long, request: EducationRequest): CandidateProfile {
        val row = education.findById(id).orElseThrow { unknown("education entry", id) }
        education.save(
            row.copy(
                institution = request.institution,
                degree = request.degree,
                fieldOfStudy = request.fieldOfStudy,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
            )
        )
        return commit()
    }

    @Transactional
    fun deleteEducation(id: Long): CandidateProfile = deleteFrom(education, id, "education entry")

    @Transactional
    fun reorderEducation(ids: List<Long>): CandidateProfile =
        reorder("education", ids, education.findAllOrdered().map { it.id!! })

    // -------------------------------------------------------------- languages

    @Transactional
    fun addLanguage(request: LanguageRequest): CandidateProfile {
        languages.findByLanguageIgnoringCase(request.language)?.let {
            throw ProfileConflictException(
                "${it.language} is already on the profile. Edit that entry instead of adding it again."
            )
        }
        languages.save(
            LanguageSkillRow(
                language = request.language,
                level = request.level.name,
                displayOrder = nextOrder("language_skill"),
            )
        )
        return commit()
    }

    @Transactional
    fun updateLanguage(id: Long, request: LanguageRequest): CandidateProfile {
        val row = languages.findById(id).orElseThrow { unknown("language", id) }
        languages.findByLanguageIgnoringCase(request.language)
            ?.takeIf { it.id != id }
            ?.let {
                throw ProfileConflictException("${it.language} is already on the profile.")
            }
        languages.save(row.copy(language = request.language, level = request.level.name))
        return commit()
    }

    @Transactional
    fun deleteLanguage(id: Long): CandidateProfile = deleteFrom(languages, id, "language")

    @Transactional
    fun reorderLanguages(ids: List<Long>): CandidateProfile =
        reorder("language_skill", ids, languages.findAllOrdered().map { it.id!! })

    // --------------------------------------------------------------- internals

    /** Every bullet tag must name a skill the profile declares - see [ProfileInvariants]. */
    private fun requireDeclared(skillIds: Set<Long>) {
        if (skillIds.isEmpty()) return
        val declared = skills.findAllOrdered().mapTo(mutableSetOf()) { it.canonicalSkillId }
        val undeclared = ProfileInvariants.undeclaredTags(declared, skillIds)
        if (undeclared.isNotEmpty()) {
            val names = catalog.findAllById(undeclared).map { it.name }.ifEmpty { undeclared.map { "#$it" } }
            throw ProfileConflictException(
                "Add ${names.sorted().joinToString(", ")} to your skills before citing it on a bullet."
            )
        }
    }

    private fun requireDatesOrdered(request: ExperienceRequest) {
        val ended = request.endedOn ?: return
        if (ended < request.startedOn) {
            throw ProfileConflictException("A role cannot end (${ended}) before it starts (${request.startedOn}).")
        }
    }

    private fun <T : Any> deleteFrom(repository: CrudRepository<T, Long>, id: Long, what: String): CandidateProfile {
        val row = repository.findById(id).orElseThrow { unknown(what, id) }
        repository.delete(row)
        return commit()
    }

    /**
     * A reorder must name the collection exactly. Accepting a subset would silently leave the
     * omitted rows wherever they were, which looks like a bug rather than a partial reorder.
     *
     * The table name is interpolated, but only ever from the fixed literals above - the request
     * supplies ids, which are bound.
     */
    private fun reorder(table: String, ids: List<Long>, current: List<Long>): CandidateProfile {
        if (ids.size != ids.toSet().size || ids.toSet() != current.toSet()) {
            throw ProfileConflictException(
                "A reorder must list every id in the collection exactly once. " +
                    "Expected ${current.sorted()}, got ${ids.sorted()}."
            )
        }
        ids.forEachIndexed { position, id ->
            jdbc.sql("update $table set display_order = :position where id = :id")
                .param("position", position)
                .param("id", id)
                .update()
        }
        return commit()
    }

    /** New rows land at the end of whatever they are joining. */
    private fun nextOrder(table: String, where: String = "true"): Int =
        jdbc.sql("select coalesce(max(display_order) + 1, 0) from $table where $where")
            .query(Int::class.java)
            .single()

    private fun unknown(what: String, id: Long) = UnknownProfileEntityException("No $what $id on this profile.")

    private fun commit(): CandidateProfile {
        jdbc.sql("update profile_details set revision = revision + 1 where id = 1").update()
        return profiles.require()
    }
}
