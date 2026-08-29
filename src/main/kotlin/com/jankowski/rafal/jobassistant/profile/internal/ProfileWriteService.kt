package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileService
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
    private val credentials: CredentialRepository,
    private val projects: ProjectRepository,
    private val languages: LanguageSkillRepository,
    private val jdbc: JdbcClient,
) {

    // ---------------------------------------------------------------- details

    /**
     * Creates the details for an already-existing profile. This is the only endpoint that can bring
     * a profile's contents into existence without a document - the profile itself (the `profile` root
     * row) must already exist, via `POST /api/profiles`.
     */
    @Transactional
    fun putDetails(profileId: Long, request: DetailsRequest): CandidateProfile {
        requireProfileExists(profileId)
        jdbc.sql(
            """
            insert into profile_details
                (profile_id, full_name, headline, email, phone, location, summary, career_goal)
            values
                (:profileId, :fullName, :headline, :email, :phone, :location, :summary, :careerGoal)
            on conflict (profile_id) do update set
                full_name   = excluded.full_name,
                headline    = excluded.headline,
                email       = excluded.email,
                phone       = excluded.phone,
                location    = excluded.location,
                summary     = excluded.summary,
                career_goal = excluded.career_goal
            """
        )
            .param("profileId", profileId)
            .param("fullName", request.fullName)
            .param("headline", request.headline)
            .param("email", request.email)
            .param("phone", request.phone)
            .param("location", request.location)
            .param("summary", request.summary)
            .param("careerGoal", request.careerGoal)
            .update()
        return commit(profileId)
    }

    // ------------------------------------------------------------------ links

    @Transactional
    fun addLink(profileId: Long, request: LinkRequest): CandidateProfile {
        requireProfileExists(profileId)
        links.save(
            ProfileLinkRow(
                profileId = profileId,
                label = request.label,
                url = request.url,
                displayOrder = nextOrder("profile_link", profileId),
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun updateLink(profileId: Long, id: Long, request: LinkRequest): CandidateProfile {
        val row = links.findByIdAndProfileId(id, profileId) ?: throw unknown("link", id)
        links.save(row.copy(label = request.label, url = request.url))
        return commit(profileId)
    }

    @Transactional
    fun deleteLink(profileId: Long, id: Long): CandidateProfile = deleteLinkRow(profileId, id)

    @Transactional
    fun reorderLinks(profileId: Long, ids: List<Long>): CandidateProfile =
        reorder(profileId, "profile_link", ids, links.findAllOrdered(profileId).map { it.id!! })

    // ----------------------------------------------------------------- skills

    @Transactional
    fun addSkill(profileId: Long, request: SkillRequest): CandidateProfile {
        requireProfileExists(profileId)
        val skill = catalog.findById(request.skillId)
            ?: throw UnknownProfileEntityException(
                "No canonical skill ${request.skillId}. Add it to the catalog first."
            )
        skills.findByCanonicalSkillId(profileId, skill.id)?.let {
            throw ProfileConflictException("You already hold ${skill.name}. Edit that entry instead of adding it again.")
        }
        skills.save(
            ProfileSkillRow(
                profileId = profileId,
                canonicalSkillId = skill.id,
                proficiency = request.proficiency.name,
                yearsOfExperience = request.yearsOfExperience,
                lastUsedYear = request.lastUsedYear,
                displayOrder = nextOrder("profile_skill", profileId),
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun updateSkill(profileId: Long, id: Long, request: SkillUpdateRequest): CandidateProfile {
        val row = skills.findByIdAndProfileId(id, profileId) ?: throw unknown("skill", id)
        skills.save(
            row.copy(
                proficiency = request.proficiency.name,
                yearsOfExperience = request.yearsOfExperience,
                lastUsedYear = request.lastUsedYear,
            )
        )
        return commit(profileId)
    }

    /**
     * Refuses while any bullet still cites the skill.
     *
     * There is no foreign key between `profile_skill` and `experience_bullet_skill`, so nothing in
     * the database stops this - and cascading would quietly delete the evidence linking a claim to
     * the work behind it. Naming the bullets lets the user decide what to do with them.
     */
    @Transactional
    fun deleteSkill(profileId: Long, id: Long): CandidateProfile {
        val row = skills.findByIdAndProfileId(id, profileId) ?: throw unknown("skill", id)
        val blockingBullets = bullets.findTaggedWithForProfile(row.canonicalSkillId, profileId)
        val blockingProjects = projects.findDirectlyTaggedWithForProfile(row.canonicalSkillId, profileId)
        if (blockingBullets.isNotEmpty() || blockingProjects.isNotEmpty()) {
            val name = catalog.findById(row.canonicalSkillId)?.name ?: "skill ${row.canonicalSkillId}"
            throw ProfileConflictException(
                "$name is still cited by ${blockingBullets.size} bullet(s) and ${blockingProjects.size} project(s). " +
                    "Untag them first, or delete them.",
                blockingBullets.map { BlockingBullet(it.id!!, it.text) },
                blockingProjects.map { BlockingProject(it.id!!, it.name) },
            )
        }
        skills.delete(row)
        return commit(profileId)
    }

    @Transactional
    fun reorderSkills(profileId: Long, ids: List<Long>): CandidateProfile =
        reorder(profileId, "profile_skill", ids, skills.findAllOrdered(profileId).map { it.id!! })

    // ------------------------------------------------------------ experiences

    @Transactional
    fun addExperience(profileId: Long, request: ExperienceRequest): CandidateProfile {
        requireProfileExists(profileId)
        requireDatesOrdered(request)
        experiences.save(
            WorkExperienceRow(
                profileId = profileId,
                company = request.company,
                roleTitle = request.roleTitle,
                location = request.location,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                summary = request.summary,
                displayOrder = nextOrder("work_experience", profileId),
            )
        )
        return commit(profileId)
    }

    /**
     * Bullet ids are untouched by this. That is the whole reason bullets are their own aggregate:
     * while they were an owned collection, Spring Data JDBC reinserted every one of them here, and
     * a CV generated last week would stop matching the profile because a job title was corrected.
     */
    @Transactional
    fun updateExperience(profileId: Long, id: Long, request: ExperienceRequest): CandidateProfile {
        requireDatesOrdered(request)
        val row = experiences.findByIdAndProfileId(id, profileId) ?: throw unknown("experience", id)
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
        return commit(profileId)
    }

    /** Bullets and their tags cascade from the `work_experience` row. */
    @Transactional
    fun deleteExperience(profileId: Long, id: Long): CandidateProfile {
        val row = experiences.findByIdAndProfileId(id, profileId) ?: throw unknown("experience", id)
        experiences.delete(row)
        return commit(profileId)
    }

    @Transactional
    fun reorderExperiences(profileId: Long, ids: List<Long>): CandidateProfile =
        reorder(profileId, "work_experience", ids, experiences.findAllOrdered(profileId).map { it.id!! })

    // ---------------------------------------------------------------- bullets

    @Transactional
    fun addBullet(profileId: Long, experienceId: Long, request: BulletRequest): CandidateProfile {
        experiences.findByIdAndProfileId(experienceId, profileId) ?: throw unknown("experience", experienceId)
        requireDeclared(profileId, request.skillIds)
        bullets.save(
            ExperienceBulletRow(
                workExperienceId = experienceId,
                projectId = null,
                text = request.text,
                displayOrder = nextOrder("experience_bullet", "work_experience_id = $experienceId"),
                skills = request.skillIds.mapTo(mutableSetOf()) { ExperienceBulletSkillRow(it) },
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun updateBullet(profileId: Long, id: Long, request: BulletRequest): CandidateProfile {
        val row = bullets.findByIdForProfile(id, profileId) ?: throw unknown("bullet", id)
        requireDeclared(profileId, request.skillIds)
        bullets.save(
            row.copy(
                text = request.text,
                skills = request.skillIds.mapTo(mutableSetOf()) { ExperienceBulletSkillRow(it) },
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun deleteBullet(profileId: Long, id: Long): CandidateProfile {
        val row = bullets.findByIdForProfile(id, profileId) ?: throw unknown("bullet", id)
        bullets.delete(row)
        return commit(profileId)
    }

    @Transactional
    fun reorderBullets(profileId: Long, experienceId: Long, ids: List<Long>): CandidateProfile {
        experiences.findByIdAndProfileId(experienceId, profileId) ?: throw unknown("experience", experienceId)
        return reorder(profileId, "experience_bullet", ids, bullets.findByExperience(experienceId).map { it.id!! })
    }

    // -------------------------------------------------------------- education

    @Transactional
    fun addEducation(profileId: Long, request: EducationRequest): CandidateProfile {
        requireProfileExists(profileId)
        education.save(
            EducationRow(
                profileId = profileId,
                institution = request.institution,
                degree = request.degree,
                fieldOfStudy = request.fieldOfStudy,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                displayOrder = nextOrder("education", profileId),
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun updateEducation(profileId: Long, id: Long, request: EducationRequest): CandidateProfile {
        val row = education.findByIdAndProfileId(id, profileId) ?: throw unknown("education entry", id)
        education.save(
            row.copy(
                institution = request.institution,
                degree = request.degree,
                fieldOfStudy = request.fieldOfStudy,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun deleteEducation(profileId: Long, id: Long): CandidateProfile {
        val row = education.findByIdAndProfileId(id, profileId) ?: throw unknown("education entry", id)
        education.delete(row)
        return commit(profileId)
    }

    @Transactional
    fun reorderEducation(profileId: Long, ids: List<Long>): CandidateProfile =
        reorder(profileId, "education", ids, education.findAllOrdered(profileId).map { it.id!! })

    // ------------------------------------------------------------ credentials

    @Transactional
    fun addCredential(profileId: Long, request: CredentialRequest): CandidateProfile {
        requireProfileExists(profileId)
        requireDatesOrdered(request)
        credentials.save(
            CredentialRow(
                profileId = profileId,
                title = request.title,
                issuer = request.issuer,
                kind = request.kind.name,
                url = request.url,
                credentialId = request.credentialId,
                issuedOn = request.issuedOn,
                expiresOn = request.expiresOn,
                displayOrder = nextOrder("credential", profileId),
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun updateCredential(profileId: Long, id: Long, request: CredentialRequest): CandidateProfile {
        requireDatesOrdered(request)
        val row = credentials.findByIdAndProfileId(id, profileId) ?: throw unknown("credential", id)
        credentials.save(
            row.copy(
                title = request.title,
                issuer = request.issuer,
                kind = request.kind.name,
                url = request.url,
                credentialId = request.credentialId,
                issuedOn = request.issuedOn,
                expiresOn = request.expiresOn,
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun deleteCredential(profileId: Long, id: Long): CandidateProfile {
        val row = credentials.findByIdAndProfileId(id, profileId) ?: throw unknown("credential", id)
        credentials.delete(row)
        return commit(profileId)
    }

    @Transactional
    fun reorderCredentials(profileId: Long, ids: List<Long>): CandidateProfile =
        reorder(profileId, "credential", ids, credentials.findAllOrdered(profileId).map { it.id!! })

    // --------------------------------------------------------------- projects

    @Transactional
    fun addProject(profileId: Long, request: ProjectRequest): CandidateProfile {
        requireProfileExists(profileId)
        requireDatesOrdered(request)
        requireDeclared(profileId, request.skillIds)
        projects.save(
            ProjectRow(
                profileId = profileId,
                name = request.name,
                url = request.url,
                description = request.description,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                displayOrder = nextOrder("project", profileId),
                skills = request.skillIds.mapTo(mutableSetOf()) { ProjectSkillRow(it) },
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun updateProject(profileId: Long, id: Long, request: ProjectRequest): CandidateProfile {
        requireDatesOrdered(request)
        requireDeclared(profileId, request.skillIds)
        val row = projects.findByIdAndProfileId(id, profileId) ?: throw unknown("project", id)
        projects.save(
            row.copy(
                name = request.name,
                url = request.url,
                description = request.description,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                skills = request.skillIds.mapTo(mutableSetOf()) { ProjectSkillRow(it) },
            )
        )
        return commit(profileId)
    }

    /** Its bullets cascade from the `project` row, same as an experience's do. */
    @Transactional
    fun deleteProject(profileId: Long, id: Long): CandidateProfile {
        val row = projects.findByIdAndProfileId(id, profileId) ?: throw unknown("project", id)
        projects.delete(row)
        return commit(profileId)
    }

    @Transactional
    fun reorderProjects(profileId: Long, ids: List<Long>): CandidateProfile =
        reorder(profileId, "project", ids, projects.findAllOrdered(profileId).map { it.id!! })

    @Transactional
    fun addProjectBullet(profileId: Long, projectId: Long, request: BulletRequest): CandidateProfile {
        projects.findByIdAndProfileId(projectId, profileId) ?: throw unknown("project", projectId)
        requireDeclared(profileId, request.skillIds)
        bullets.save(
            ExperienceBulletRow(
                workExperienceId = null,
                projectId = projectId,
                text = request.text,
                displayOrder = nextOrder("experience_bullet", "project_id = $projectId"),
                skills = request.skillIds.mapTo(mutableSetOf()) { ExperienceBulletSkillRow(it) },
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun reorderProjectBullets(profileId: Long, projectId: Long, ids: List<Long>): CandidateProfile {
        projects.findByIdAndProfileId(projectId, profileId) ?: throw unknown("project", projectId)
        return reorder(profileId, "experience_bullet", ids, bullets.findByProject(projectId).map { it.id!! })
    }

    // -------------------------------------------------------------- languages

    @Transactional
    fun addLanguage(profileId: Long, request: LanguageRequest): CandidateProfile {
        requireProfileExists(profileId)
        languages.findByLanguageIgnoringCase(profileId, request.language)?.let {
            throw ProfileConflictException(
                "${it.language} is already on the profile. Edit that entry instead of adding it again."
            )
        }
        languages.save(
            LanguageSkillRow(
                profileId = profileId,
                language = request.language,
                level = request.level.name,
                displayOrder = nextOrder("language_skill", profileId),
            )
        )
        return commit(profileId)
    }

    @Transactional
    fun updateLanguage(profileId: Long, id: Long, request: LanguageRequest): CandidateProfile {
        val row = languages.findByIdAndProfileId(id, profileId) ?: throw unknown("language", id)
        languages.findByLanguageIgnoringCase(profileId, request.language)
            ?.takeIf { it.id != id }
            ?.let {
                throw ProfileConflictException("${it.language} is already on the profile.")
            }
        languages.save(row.copy(language = request.language, level = request.level.name))
        return commit(profileId)
    }

    @Transactional
    fun deleteLanguage(profileId: Long, id: Long): CandidateProfile {
        val row = languages.findByIdAndProfileId(id, profileId) ?: throw unknown("language", id)
        languages.delete(row)
        return commit(profileId)
    }

    @Transactional
    fun reorderLanguages(profileId: Long, ids: List<Long>): CandidateProfile =
        reorder(profileId, "language_skill", ids, languages.findAllOrdered(profileId).map { it.id!! })

    // --------------------------------------------------------------- internals

    private fun deleteLinkRow(profileId: Long, id: Long): CandidateProfile {
        val row = links.findByIdAndProfileId(id, profileId) ?: throw unknown("link", id)
        links.delete(row)
        return commit(profileId)
    }

    /** Every bullet tag must name a skill the profile declares - see [ProfileInvariants]. */
    private fun requireDeclared(profileId: Long, skillIds: Set<Long>) {
        if (skillIds.isEmpty()) return
        val declared = skills.findAllOrdered(profileId).mapTo(mutableSetOf()) { it.canonicalSkillId }
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

    private fun requireDatesOrdered(request: CredentialRequest) {
        val issued = request.issuedOn
        val expires = request.expiresOn
        if (issued != null && expires != null && expires < issued) {
            throw ProfileConflictException("A credential cannot expire ($expires) before it was issued ($issued).")
        }
    }

    private fun requireDatesOrdered(request: ProjectRequest) {
        val started = request.startedOn
        val ended = request.endedOn
        if (started != null && ended != null && ended < started) {
            throw ProfileConflictException("A project cannot end ($ended) before it starts ($started).")
        }
    }

    private fun requireProfileExists(profileId: Long) {
        val exists = jdbc.sql("select 1 from profile where id = :id")
            .param("id", profileId)
            .query(Int::class.java)
            .optional()
            .isPresent
        if (!exists) throw UnknownProfileEntityException("No profile $profileId. POST /api/profiles first.")
    }

    /**
     * A reorder must name the collection exactly. Accepting a subset would silently leave the
     * omitted rows wherever they were, which looks like a bug rather than a partial reorder.
     *
     * The table name is interpolated, but only ever from the fixed literals above - the request
     * supplies ids, which are bound.
     */
    private fun reorder(profileId: Long, table: String, ids: List<Long>, current: List<Long>): CandidateProfile {
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
        return commit(profileId)
    }

    /** New rows land at the end of whatever they are joining, scoped to the profile. */
    private fun nextOrder(table: String, profileId: Long): Int = nextOrder(table, "profile_id = $profileId")

    /**
     * New rows land at the end of whatever they are joining.
     *
     * The where-clause is interpolated, but only ever from the fixed literals and numeric ids above
     * - never from request input - so there is no new injection surface.
     */
    private fun nextOrder(table: String, where: String): Int =
        jdbc.sql("select coalesce(max(display_order) + 1, 0) from $table where $where")
            .query(Int::class.java)
            .single()

    private fun unknown(what: String, id: Long) = UnknownProfileEntityException("No $what $id on this profile.")

    private fun commit(profileId: Long): CandidateProfile {
        jdbc.sql("update profile set revision = revision + 1 where id = :profileId")
            .param("profileId", profileId)
            .update()
        return profiles.require(profileId)
    }
}
