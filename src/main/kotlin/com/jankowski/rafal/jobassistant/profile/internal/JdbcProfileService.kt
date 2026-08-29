package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.Credential
import com.jankowski.rafal.jobassistant.profile.CredentialKind
import com.jankowski.rafal.jobassistant.profile.Education
import com.jankowski.rafal.jobassistant.profile.ExperienceBullet
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.LanguageSkill
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileIdentity
import com.jankowski.rafal.jobassistant.profile.ProfileImportException
import com.jankowski.rafal.jobassistant.profile.ProfileLink
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.ProfileSkill
import com.jankowski.rafal.jobassistant.profile.Project
import com.jankowski.rafal.jobassistant.profile.WorkExperience
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class JdbcProfileService(
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
) : ProfileService {

    @Transactional(readOnly = true)
    override fun current(profileId: Long): CandidateProfile? {
        val details = readDetails(profileId) ?: return null
        // Bullets no longer hang off the experience aggregate, so they are read once for the whole
        // profile and grouped here rather than queried per role.
        val bulletsByExperience = bullets.findAllOrderedForProfile(profileId).groupBy { it.workExperienceId }
        val bulletsByProject = bullets.findAllOrderedForProjects(profileId).groupBy { it.projectId }
        return CandidateProfile(
            details = details,
            links = links.findAllOrdered(profileId).map { ProfileLink(it.id!!, it.label, it.url) },
            skills = skills.findAllOrdered(profileId).map { it.toDomain() },
            experiences = experiences.findAllOrdered(profileId).map { row ->
                row.toDomain(bulletsByExperience[row.id].orEmpty().map { it.toDomain() })
            },
            education = education.findAllOrdered(profileId).map { it.toDomain() },
            credentials = credentials.findAllOrdered(profileId).map { it.toDomain() },
            projects = projects.findAllOrdered(profileId).map { row ->
                row.toDomain(bulletsByProject[row.id].orEmpty().map { it.toDomain() })
            },
            languages = languages.findAllOrdered(profileId).map { it.toDomain() },
            revision = readRevision(profileId),
        )
    }

    override fun require(profileId: Long): CandidateProfile =
        current(profileId) ?: throw IllegalStateException(
            "No profile $profileId, or it has no details yet. PUT /api/profiles/$profileId/details, " +
                "or POST a document to /api/profiles/$profileId/import."
        )

    @Transactional(readOnly = true)
    override fun revision(profileId: Long): Long = readRevision(profileId)

    /**
     * Two small indexed reads rather than loading whole profiles. Not cached on purpose: a model
     * call costs seconds and these cost microseconds, so a cache would buy nothing and would need
     * invalidating on every profile write - a staleness bug here means the guard checks against a
     * name the user has already changed.
     */
    @Transactional(readOnly = true)
    override fun identities(): List<ProfileIdentity> {
        // A project URL is a direct identifier exactly like a profile link - github.com/AnielskieOczko
        // names the candidate as surely as an email does - so it feeds the same linkUrls list.
        val urlsByProfile = jdbc.sql(
            "select profile_id, url from profile_link " +
                "union all select profile_id, url from project where url is not null"
        )
            .query { rs, _ -> rs.getLong("profile_id") to rs.getString("url") }
            .list()
            .groupBy({ it.first }, { it.second })

        return jdbc.sql("select profile_id, full_name, email, phone from profile_details")
            .query { rs, _ ->
                val profileId = rs.getLong("profile_id")
                ProfileIdentity(
                    profileId = profileId,
                    fullName = rs.getString("full_name"),
                    email = rs.getString("email"),
                    phone = rs.getString("phone"),
                    linkUrls = urlsByProfile[profileId].orEmpty(),
                )
            }
            .list()
    }

    @Transactional(readOnly = true)
    override fun defaultProfileId(): Long =
        jdbc.sql("select id from profile where is_default")
            .query(Long::class.java)
            .optional()
            .orElseThrow { IllegalStateException("No profile exists yet. POST /api/profiles to create one.") }

    @Transactional
    override fun replace(profileId: Long, import: ProfileImport): CandidateProfile {
        requireProfileExists(profileId)
        val skillIdsByName = resolveSkillNames(import)

        deleteEverything(profileId)
        writeDetails(profileId, import.details)

        links.saveAll(
            import.links.mapIndexed { i, link ->
                ProfileLinkRow(profileId = profileId, label = link.label, url = link.url, displayOrder = i)
            }
        )
        skills.saveAll(
            import.skills.mapIndexed { i, it ->
                ProfileSkillRow(
                    profileId = profileId,
                    canonicalSkillId = skillIdsByName.getValue(it.skill.lowercase()),
                    proficiency = it.proficiency.name,
                    yearsOfExperience = it.yearsOfExperience,
                    lastUsedYear = it.lastUsedYear,
                    displayOrder = i,
                )
            }
        )
        import.experiences.forEachIndexed { i, exp ->
            val saved = experiences.save(
                WorkExperienceRow(
                    profileId = profileId,
                    company = exp.company,
                    roleTitle = exp.roleTitle,
                    location = exp.location,
                    startedOn = exp.startedOn,
                    endedOn = exp.endedOn,
                    summary = exp.summary,
                    displayOrder = i,
                )
            )
            bullets.saveAll(
                exp.bullets.mapIndexed { j, bullet ->
                    ExperienceBulletRow(
                        workExperienceId = saved.id!!,
                        projectId = null,
                        text = bullet.text,
                        displayOrder = j,
                        skills = bullet.skills.mapTo(mutableSetOf()) {
                            ExperienceBulletSkillRow(skillIdsByName.getValue(it.lowercase()))
                        },
                    )
                }
            )
        }
        education.saveAll(
            import.education.mapIndexed { i, e ->
                EducationRow(
                    profileId = profileId,
                    institution = e.institution,
                    degree = e.degree,
                    fieldOfStudy = e.fieldOfStudy,
                    startedOn = e.startedOn,
                    endedOn = e.endedOn,
                    displayOrder = i,
                )
            }
        )
        credentials.saveAll(
            import.credentials.mapIndexed { i, c ->
                CredentialRow(
                    profileId = profileId,
                    title = c.title,
                    issuer = c.issuer,
                    kind = c.kind.name,
                    url = c.url,
                    credentialId = c.credentialId,
                    issuedOn = c.issuedOn,
                    expiresOn = c.expiresOn,
                    displayOrder = i,
                )
            }
        )
        import.projects.forEachIndexed { i, proj ->
            val saved = projects.save(
                ProjectRow(
                    profileId = profileId,
                    name = proj.name,
                    url = proj.url,
                    description = proj.description,
                    startedOn = proj.startedOn,
                    endedOn = proj.endedOn,
                    displayOrder = i,
                    skills = proj.skills.mapTo(mutableSetOf()) {
                        ProjectSkillRow(skillIdsByName.getValue(it.lowercase()))
                    },
                )
            )
            bullets.saveAll(
                proj.bullets.mapIndexed { j, bullet ->
                    ExperienceBulletRow(
                        workExperienceId = null,
                        projectId = saved.id!!,
                        text = bullet.text,
                        displayOrder = j,
                        skills = bullet.skills.mapTo(mutableSetOf()) {
                            ExperienceBulletSkillRow(skillIdsByName.getValue(it.lowercase()))
                        },
                    )
                }
            )
        }
        languages.saveAll(
            import.languages.mapIndexed { i, it ->
                LanguageSkillRow(profileId = profileId, language = it.language, level = it.level.name, displayOrder = i)
            }
        )

        bumpRevision(profileId)
        return require(profileId)
    }

    /**
     * Resolves every skill name the document mentions in one pass, and refuses the whole import
     * if anything is unresolvable or if a bullet claims a skill the profile never declares.
     * Rejecting up front keeps a half-written profile from ever reaching the database.
     */
    private fun resolveSkillNames(import: ProfileImport): Map<String, Long> {
        val declared = import.skills.map { it.skill }
        val onExperienceBullets = import.experiences.flatMap { it.bullets }.flatMap { it.skills }
        val onProjects = import.projects.flatMap { it.skills }
        val onProjectBullets = import.projects.flatMap { it.bullets }.flatMap { it.skills }
        val tagged = onExperienceBullets + onProjects + onProjectBullets
        val resolved = catalog.resolveAll((declared + tagged).toSet())

        val unresolved = resolved.filterValues { it == null }.keys.sorted()

        val declaredIds = declared.mapNotNull { resolved[it]?.id }.toSet()
        val taggedIds = tagged.mapNotNull { resolved[it]?.id }.toSet()
        val undeclaredIds = ProfileInvariants.undeclaredTags(declaredIds, taggedIds)
        // Reported back as names: the document was written in names, so that is what the author
        // has to go and correct.
        val undeclared = tagged.distinct()
            .filter { name -> resolved[name]?.id?.let { it in undeclaredIds } == true }
            .sorted()

        if (unresolved.isNotEmpty() || undeclared.isNotEmpty()) {
            throw ProfileImportException(unresolved, undeclared)
        }
        return resolved.entries.associate { (name, skill) -> name.lowercase() to skill!!.id }
    }

    private fun requireProfileExists(profileId: Long) {
        val exists = jdbc.sql("select 1 from profile where id = :id")
            .param("id", profileId)
            .query(Int::class.java)
            .optional()
            .isPresent
        if (!exists) throw UnknownProfileEntityException("No profile $profileId. POST /api/profiles first.")
    }

    private fun readDetails(profileId: Long): ProfileDetails? =
        jdbc.sql(
            "select full_name, headline, email, phone, location, summary " +
                "from profile_details where profile_id = :profileId"
        )
            .param("profileId", profileId)
            .query { rs, _ ->
                ProfileDetails(
                    fullName = rs.getString("full_name"),
                    headline = rs.getString("headline"),
                    email = rs.getString("email"),
                    phone = rs.getString("phone"),
                    location = rs.getString("location"),
                    summary = rs.getString("summary"),
                )
            }
            .optional()
            .orElse(null)

    private fun writeDetails(profileId: Long, details: ProfileDetails) {
        jdbc.sql(
            """
            insert into profile_details (profile_id, full_name, headline, email, phone, location, summary)
            values (:profileId, :fullName, :headline, :email, :phone, :location, :summary)
            on conflict (profile_id) do update set
                full_name = excluded.full_name,
                headline  = excluded.headline,
                email     = excluded.email,
                phone     = excluded.phone,
                location  = excluded.location,
                summary   = excluded.summary
            """
        )
            .param("profileId", profileId)
            .param("fullName", details.fullName)
            .param("headline", details.headline)
            .param("email", details.email)
            .param("phone", details.phone)
            .param("location", details.location)
            .param("summary", details.summary)
            .update()
    }

    private fun readRevision(profileId: Long): Long =
        jdbc.sql("select revision from profile where id = :profileId")
            .param("profileId", profileId)
            .query(Long::class.java)
            .optional()
            .orElse(0L)

    private fun bumpRevision(profileId: Long) {
        jdbc.sql("update profile set revision = revision + 1 where id = :profileId")
            .param("profileId", profileId)
            .update()
    }

    private fun deleteEverything(profileId: Long) {
        // experience_bullet and experience_bullet_skill cascade from work_experience or project.
        experiences.deleteByProfileId(profileId)
        skills.deleteByProfileId(profileId)
        links.deleteByProfileId(profileId)
        education.deleteByProfileId(profileId)
        credentials.deleteByProfileId(profileId)
        projects.deleteByProfileId(profileId)
        languages.deleteByProfileId(profileId)
    }
}

internal fun ProfileSkillRow.toDomain() = ProfileSkill(
    id = id!!,
    skillId = canonicalSkillId,
    proficiency = Proficiency.valueOf(proficiency),
    yearsOfExperience = yearsOfExperience,
    lastUsedYear = lastUsedYear,
)

internal fun WorkExperienceRow.toDomain(bullets: List<ExperienceBullet>) = WorkExperience(
    id = id!!,
    company = company,
    roleTitle = roleTitle,
    location = location,
    startedOn = startedOn,
    endedOn = endedOn,
    summary = summary,
    bullets = bullets,
)

internal fun ExperienceBulletRow.toDomain() = ExperienceBullet(
    id = id!!,
    text = text,
    skillIds = skills.mapTo(mutableSetOf()) { it.canonicalSkillId },
)

internal fun EducationRow.toDomain() =
    Education(id!!, institution, degree, fieldOfStudy, startedOn, endedOn)

internal fun CredentialRow.toDomain() =
    Credential(id!!, title, issuer, CredentialKind.valueOf(kind), url, credentialId, issuedOn, expiresOn)

internal fun ProjectRow.toDomain(bullets: List<ExperienceBullet>) = Project(
    id = id!!,
    name = name,
    url = url,
    description = description,
    startedOn = startedOn,
    endedOn = endedOn,
    skillIds = skills.mapTo(mutableSetOf()) { it.canonicalSkillId },
    bullets = bullets,
)

internal fun LanguageSkillRow.toDomain() =
    LanguageSkill(id!!, language, LanguageLevel.valueOf(level))
