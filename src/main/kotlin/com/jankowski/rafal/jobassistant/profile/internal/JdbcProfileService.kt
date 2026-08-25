package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.Education
import com.jankowski.rafal.jobassistant.profile.ExperienceBullet
import com.jankowski.rafal.jobassistant.profile.LanguageLevel
import com.jankowski.rafal.jobassistant.profile.LanguageSkill
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileImportException
import com.jankowski.rafal.jobassistant.profile.ProfileLink
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.ProfileSkill
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
    private val languages: LanguageSkillRepository,
    private val jdbc: JdbcClient,
) : ProfileService {

    @Transactional(readOnly = true)
    override fun current(): CandidateProfile? {
        val details = readDetails() ?: return null
        // Bullets no longer hang off the experience aggregate, so they are read once for the whole
        // profile and grouped here rather than queried per role.
        val bulletsByExperience = bullets.findAllOrdered().groupBy { it.workExperienceId }
        return CandidateProfile(
            details = details,
            links = links.findAllOrdered().map { ProfileLink(it.id!!, it.label, it.url) },
            skills = skills.findAllOrdered().map { it.toDomain() },
            experiences = experiences.findAllOrdered().map { row ->
                row.toDomain(bulletsByExperience[row.id].orEmpty().map { it.toDomain() })
            },
            education = education.findAllOrdered().map { it.toDomain() },
            languages = languages.findAllOrdered().map { it.toDomain() },
            revision = readRevision(),
        )
    }

    override fun require(): CandidateProfile =
        current() ?: throw IllegalStateException(
            "No profile yet. Create one at /api/profile/details, or POST a document to /api/profile/import."
        )

    @Transactional(readOnly = true)
    override fun revision(): Long = readRevision()

    @Transactional
    override fun replace(import: ProfileImport): CandidateProfile {
        val skillIdsByName = resolveSkillNames(import)

        deleteEverything()
        writeDetails(import.details)

        links.saveAll(
            import.links.mapIndexed { i, link -> ProfileLinkRow(label = link.label, url = link.url, displayOrder = i) }
        )
        skills.saveAll(
            import.skills.mapIndexed { i, it ->
                ProfileSkillRow(
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
                    institution = e.institution,
                    degree = e.degree,
                    fieldOfStudy = e.fieldOfStudy,
                    startedOn = e.startedOn,
                    endedOn = e.endedOn,
                    displayOrder = i,
                )
            }
        )
        languages.saveAll(
            import.languages.mapIndexed { i, it ->
                LanguageSkillRow(language = it.language, level = it.level.name, displayOrder = i)
            }
        )

        bumpRevision()
        return require()
    }

    /**
     * Resolves every skill name the document mentions in one pass, and refuses the whole import
     * if anything is unresolvable or if a bullet claims a skill the profile never declares.
     * Rejecting up front keeps a half-written profile from ever reaching the database.
     */
    private fun resolveSkillNames(import: ProfileImport): Map<String, Long> {
        val declared = import.skills.map { it.skill }
        val onBullets = import.experiences.flatMap { it.bullets }.flatMap { it.skills }
        val resolved = catalog.resolveAll((declared + onBullets).toSet())

        val unresolved = resolved.filterValues { it == null }.keys.sorted()

        val declaredIds = declared.mapNotNull { resolved[it]?.id }.toSet()
        val taggedIds = onBullets.mapNotNull { resolved[it]?.id }.toSet()
        val undeclaredIds = ProfileInvariants.undeclaredTags(declaredIds, taggedIds)
        // Reported back as names: the document was written in names, so that is what the author
        // has to go and correct.
        val undeclared = onBullets.distinct()
            .filter { name -> resolved[name]?.id?.let { it in undeclaredIds } == true }
            .sorted()

        if (unresolved.isNotEmpty() || undeclared.isNotEmpty()) {
            throw ProfileImportException(unresolved, undeclared)
        }
        return resolved.entries.associate { (name, skill) -> name.lowercase() to skill!!.id }
    }

    private fun readDetails(): ProfileDetails? =
        jdbc.sql("select full_name, headline, email, phone, location, summary from profile_details where id = 1")
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

    private fun writeDetails(details: ProfileDetails) {
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
            .param("fullName", details.fullName)
            .param("headline", details.headline)
            .param("email", details.email)
            .param("phone", details.phone)
            .param("location", details.location)
            .param("summary", details.summary)
            .update()
    }

    private fun readRevision(): Long =
        jdbc.sql("select revision from profile_details where id = 1")
            .query(Long::class.java)
            .optional()
            .orElse(0L)

    private fun bumpRevision() {
        jdbc.sql("update profile_details set revision = revision + 1 where id = 1").update()
    }

    private fun deleteEverything() {
        // experience_bullet and experience_bullet_skill cascade from work_experience.
        experiences.deleteAll()
        skills.deleteAll()
        links.deleteAll()
        education.deleteAll()
        languages.deleteAll()
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

internal fun LanguageSkillRow.toDomain() =
    LanguageSkill(id!!, language, LanguageLevel.valueOf(level))
