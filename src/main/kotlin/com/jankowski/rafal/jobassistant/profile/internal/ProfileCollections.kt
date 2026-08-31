package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * What a profile contains, as a list.
 *
 * This is the inventory: nine ordered collections, each declared once, next to the rules that are
 * its own. Adding a tenth is a descriptor here plus its routes - not another copy of `add`,
 * `update`, `delete` and `reorder`, which live once in [ProfileWriteService].
 *
 * The rules below are deliberately not generalised. `deleteSkill` refusing while a bullet still
 * cites the skill, a bullet's tags having to name declared skills, a consent clause being unique
 * per language: these are behaviour, and a descriptor language able to express all of them would
 * be a worse trade than nine descriptors with eleven interesting hooks between them.
 */
@Component
internal class ProfileCollections(
    private val catalog: SkillCatalog,
    private val skillRows: ProfileSkillRepository,
    private val bulletRows: ExperienceBulletRepository,
    private val projectRows: ProjectRepository,
    linkRows: ProfileLinkRepository,
    experienceRows: WorkExperienceRepository,
    educationRows: EducationRepository,
    credentialRows: CredentialRepository,
    consentClauseRows: ConsentClauseRepository,
    languageRows: LanguageSkillRepository,
) {

    val links = ProfileCollection<ProfileLinkRow, LinkRequest, LinkRequest>(
        entity = "link",
        table = "profile_link",
        repository = linkRows,
        findOne = linkRows::findByIdAndProfileId,
        orderedIds = { owner -> linkRows.findAllOrdered(owner.profileId).map { it.id!! } },
        newRow = { owner, request, order ->
            ProfileLinkRow(
                profileId = owner.profileId,
                label = request.label,
                url = request.url,
                displayOrder = order,
            )
        },
        applyUpdate = { row, request -> row.copy(label = request.label, url = request.url) },
    )

    val skills = ProfileCollection<ProfileSkillRow, SkillRequest, SkillUpdateRequest>(
        entity = "skill",
        table = "profile_skill",
        repository = skillRows,
        findOne = skillRows::findByIdAndProfileId,
        orderedIds = { owner -> skillRows.findAllOrdered(owner.profileId).map { it.id!! } },
        newRow = { owner, request, order ->
            ProfileSkillRow(
                profileId = owner.profileId,
                canonicalSkillId = request.skillId,
                proficiency = request.proficiency.name,
                yearsOfExperience = request.yearsOfExperience,
                lastUsedYear = request.lastUsedYear,
                displayOrder = order,
            )
        },
        applyUpdate = { row, request ->
            row.copy(
                proficiency = request.proficiency.name,
                yearsOfExperience = request.yearsOfExperience,
                lastUsedYear = request.lastUsedYear,
            )
        },
        onAdd = { profileId, request ->
            val skill = catalog.findById(request.skillId)
                ?: throw UnknownProfileEntityException(
                    "No canonical skill ${request.skillId}. Add it to the catalog first."
                )
            skillRows.findByCanonicalSkillId(profileId, skill.id)?.let {
                throw ProfileConflictException(
                    "You already hold ${skill.name}. Edit that entry instead of adding it again."
                )
            }
        },
        onDelete = ::requireNothingCites,
    )

    val experiences = ProfileCollection<WorkExperienceRow, ExperienceRequest, ExperienceRequest>(
        entity = "experience",
        table = "work_experience",
        repository = experienceRows,
        findOne = experienceRows::findByIdAndProfileId,
        orderedIds = { owner -> experienceRows.findAllOrdered(owner.profileId).map { it.id!! } },
        newRow = { owner, request, order ->
            WorkExperienceRow(
                profileId = owner.profileId,
                company = request.company,
                roleTitle = request.roleTitle,
                location = request.location,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                summary = request.summary,
                displayOrder = order,
            )
        },
        /**
         * Bullet ids are untouched by this. That is the whole reason bullets are their own
         * aggregate: while they were an owned collection, Spring Data JDBC reinserted every one of
         * them here, and a CV generated last week would stop matching the profile because a job
         * title was corrected.
         */
        applyUpdate = { row, request ->
            row.copy(
                company = request.company,
                roleTitle = request.roleTitle,
                location = request.location,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                summary = request.summary,
            )
        },
        onAdd = { _, request -> requireDatesOrdered(request) },
        checkUpdate = { _, request -> requireDatesOrdered(request) },
    )

    /**
     * Bullets, under either of their two owners.
     *
     * One descriptor rather than two: a bullet is edited and deleted by id alone, without its owner
     * being named, so a second descriptor would only be a second place for the pair to drift apart.
     * Which owner a new bullet lands under is a property of the call, not of the collection.
     */
    val bullets = ProfileCollection<ExperienceBulletRow, BulletRequest, BulletRequest>(
        entity = "bullet",
        table = "experience_bullet",
        repository = bulletRows,
        findOne = bulletRows::findByIdForProfile,
        orderedIds = { owner ->
            when (owner) {
                is CollectionOwner.Experience -> bulletRows.findByExperience(owner.experienceId)
                is CollectionOwner.Project -> bulletRows.findByProject(owner.projectId)
                is CollectionOwner.Profile -> error("A bullet always belongs to a role or a project.")
            }.map { it.id!! }
        },
        newRow = { owner, request, order ->
            ExperienceBulletRow(
                workExperienceId = (owner as? CollectionOwner.Experience)?.experienceId,
                projectId = (owner as? CollectionOwner.Project)?.projectId,
                text = request.text,
                displayOrder = order,
                skills = request.skillIds.mapTo(mutableSetOf()) { ExperienceBulletSkillRow(it) },
            )
        },
        applyUpdate = { row, request ->
            row.copy(
                text = request.text,
                skills = request.skillIds.mapTo(mutableSetOf()) { ExperienceBulletSkillRow(it) },
            )
        },
        onAdd = { profileId, request -> requireDeclared(profileId, request.skillIds) },
        onUpdate = { profileId, _, request -> requireDeclared(profileId, request.skillIds) },
    )

    val education = ProfileCollection<EducationRow, EducationRequest, EducationRequest>(
        entity = "education entry",
        table = "education",
        repository = educationRows,
        findOne = educationRows::findByIdAndProfileId,
        orderedIds = { owner -> educationRows.findAllOrdered(owner.profileId).map { it.id!! } },
        newRow = { owner, request, order ->
            EducationRow(
                profileId = owner.profileId,
                institution = request.institution,
                degree = request.degree,
                fieldOfStudy = request.fieldOfStudy,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                displayOrder = order,
            )
        },
        applyUpdate = { row, request ->
            row.copy(
                institution = request.institution,
                degree = request.degree,
                fieldOfStudy = request.fieldOfStudy,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
            )
        },
    )

    val credentials = ProfileCollection<CredentialRow, CredentialRequest, CredentialRequest>(
        entity = "credential",
        table = "credential",
        repository = credentialRows,
        findOne = credentialRows::findByIdAndProfileId,
        orderedIds = { owner -> credentialRows.findAllOrdered(owner.profileId).map { it.id!! } },
        newRow = { owner, request, order ->
            CredentialRow(
                profileId = owner.profileId,
                title = request.title,
                issuer = request.issuer,
                kind = request.kind.name,
                url = request.url,
                credentialId = request.credentialId,
                issuedOn = request.issuedOn,
                expiresOn = request.expiresOn,
                displayOrder = order,
            )
        },
        applyUpdate = { row, request ->
            row.copy(
                title = request.title,
                issuer = request.issuer,
                kind = request.kind.name,
                url = request.url,
                credentialId = request.credentialId,
                issuedOn = request.issuedOn,
                expiresOn = request.expiresOn,
            )
        },
        onAdd = { _, request -> requireDatesOrdered(request) },
        checkUpdate = { _, request -> requireDatesOrdered(request) },
    )

    /** Its bullets cascade from the `project` row, same as an experience's do. */
    val projects = ProfileCollection<ProjectRow, ProjectRequest, ProjectRequest>(
        entity = "project",
        table = "project",
        repository = projectRows,
        findOne = projectRows::findByIdAndProfileId,
        orderedIds = { owner -> projectRows.findAllOrdered(owner.profileId).map { it.id!! } },
        newRow = { owner, request, order ->
            ProjectRow(
                profileId = owner.profileId,
                name = request.name,
                url = request.url,
                description = request.description,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                displayOrder = order,
                skills = request.skillIds.mapTo(mutableSetOf()) { ProjectSkillRow(it) },
            )
        },
        applyUpdate = { row, request ->
            row.copy(
                name = request.name,
                url = request.url,
                description = request.description,
                startedOn = request.startedOn,
                endedOn = request.endedOn,
                skills = request.skillIds.mapTo(mutableSetOf()) { ProjectSkillRow(it) },
            )
        },
        onAdd = ::checkProject,
        checkUpdate = ::checkProject,
    )

    val consentClauses = ProfileCollection<ConsentClauseRow, ConsentClauseRequest, ConsentClauseRequest>(
        entity = "consent clause",
        table = "cv_consent_clause",
        repository = consentClauseRows,
        findOne = consentClauseRows::findByIdAndProfileId,
        orderedIds = { owner -> consentClauseRows.findAllOrdered(owner.profileId).map { it.id!! } },
        newRow = { owner, request, order ->
            ConsentClauseRow(
                profileId = owner.profileId,
                language = request.language,
                text = request.text,
                displayOrder = order,
            )
        },
        applyUpdate = { row, request -> row.copy(language = request.language, text = request.text) },
        onAdd = { profileId, request ->
            consentClauseRows.findByLanguageIgnoringCase(profileId, request.language)?.let {
                throw ProfileConflictException(
                    "A consent clause for ${it.language} already exists. Edit that entry instead of adding it again."
                )
            }
        },
        onUpdate = { profileId, row, request ->
            consentClauseRows.findByLanguageIgnoringCase(profileId, request.language)
                ?.takeIf { it.id != row.id }
                ?.let { throw ProfileConflictException("A consent clause for ${it.language} already exists.") }
        },
    )

    val languages = ProfileCollection<LanguageSkillRow, LanguageRequest, LanguageRequest>(
        entity = "language",
        table = "language_skill",
        repository = languageRows,
        findOne = languageRows::findByIdAndProfileId,
        orderedIds = { owner -> languageRows.findAllOrdered(owner.profileId).map { it.id!! } },
        newRow = { owner, request, order ->
            LanguageSkillRow(
                profileId = owner.profileId,
                language = request.language,
                level = request.level.name,
                displayOrder = order,
            )
        },
        applyUpdate = { row, request -> row.copy(language = request.language, level = request.level.name) },
        onAdd = { profileId, request ->
            languageRows.findByLanguageIgnoringCase(profileId, request.language)?.let {
                throw ProfileConflictException(
                    "${it.language} is already on the profile. Edit that entry instead of adding it again."
                )
            }
        },
        onUpdate = { profileId, row, request ->
            languageRows.findByLanguageIgnoringCase(profileId, request.language)
                ?.takeIf { it.id != row.id }
                ?.let { throw ProfileConflictException("${it.language} is already on the profile.") }
        },
    )

    // ------------------------------------------------------------------- rules

    /**
     * Refuses to delete a skill while any bullet or project still cites it.
     *
     * There is no foreign key between `profile_skill` and `experience_bullet_skill`, so nothing in
     * the database stops this - and cascading would quietly delete the evidence linking a claim to
     * the work behind it. Naming what is in the way lets the user decide what to do with it.
     */
    private fun requireNothingCites(profileId: Long, row: ProfileSkillRow) {
        val blockingBullets = bulletRows.findTaggedWithForProfile(row.canonicalSkillId, profileId)
        val blockingProjects = projectRows.findDirectlyTaggedWithForProfile(row.canonicalSkillId, profileId)
        if (blockingBullets.isEmpty() && blockingProjects.isEmpty()) return
        val name = catalog.findById(row.canonicalSkillId)?.name ?: "skill ${row.canonicalSkillId}"
        throw ProfileConflictException(
            "$name is still cited by ${blockingBullets.size} bullet(s) and ${blockingProjects.size} project(s). " +
                "Untag them first, or delete them.",
            blockingBullets.map { BlockingBullet(it.id!!, it.text) },
            blockingProjects.map { BlockingProject(it.id!!, it.name) },
        )
    }

    private fun checkProject(profileId: Long, request: ProjectRequest) {
        requireDatesOrdered(request)
        requireDeclared(profileId, request.skillIds)
    }

    /** Every skill tag must name a skill the profile declares - see [ProfileInvariants]. */
    private fun requireDeclared(profileId: Long, skillIds: Set<Long>) {
        if (skillIds.isEmpty()) return
        val declared = skillRows.findAllOrdered(profileId).mapTo(mutableSetOf()) { it.canonicalSkillId }
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

    private fun requireDatesOrdered(request: CredentialRequest) =
        requireOrdered(request.issuedOn, request.expiresOn) { issued, expires ->
            "A credential cannot expire ($expires) before it was issued ($issued)."
        }

    private fun requireDatesOrdered(request: ProjectRequest) =
        requireOrdered(request.startedOn, request.endedOn) { started, ended ->
            "A project cannot end ($ended) before it starts ($started)."
        }

    private fun requireOrdered(from: LocalDate?, to: LocalDate?, message: (LocalDate, LocalDate) -> String) {
        if (from != null && to != null && to < from) throw ProfileConflictException(message(from, to))
    }
}
