package com.jankowski.rafal.jobassistant.document.internal

import com.jankowski.rafal.jobassistant.analysis.AnalysisReport
import com.jankowski.rafal.jobassistant.analysis.AnalysisService
import com.jankowski.rafal.jobassistant.analysis.AnalysisState
import com.jankowski.rafal.jobassistant.catalog.SkillCatalog
import com.jankowski.rafal.jobassistant.document.DocumentService
import com.jankowski.rafal.jobassistant.document.DocumentType
import com.jankowski.rafal.jobassistant.document.FabricatedClaimException
import com.jankowski.rafal.jobassistant.document.GeneratedDocument
import com.jankowski.rafal.jobassistant.llm.AiServiceFactory
import com.jankowski.rafal.jobassistant.llm.LlmCallScope
import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.offer.OfferService
import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import tools.jackson.databind.json.JsonMapper
import java.util.Locale

@Service
internal class JdbcDocumentService(
    private val documents: GeneratedDocumentRepository,
    private val offers: OfferService,
    private val analyses: AnalysisService,
    private val profiles: ProfileService,
    private val catalog: SkillCatalog,
    private val aiServices: AiServiceFactory,
    private val renderer: DocumentRenderer,
    private val templates: TemplateEngine,
    private val json: JsonMapper,
) : DocumentService {

    private val log = LoggerFactory.getLogger(JdbcDocumentService::class.java)

    @Transactional
    override fun generate(offerId: Long, profileId: Long, type: DocumentType, language: String): GeneratedDocument {
        val offer = offers.findById(offerId) ?: throw NoSuchElementException("No job offer $offerId")
        val profile = profiles.require(profileId)

        // Pairing this with the profile above guarantees the analysis being tailored against and
        // the profile CvInvariant/ProfileBriefing/CvSelection see are always the same profile -
        // there is no code path where they can diverge.
        val report = analyses.latestForOffer(offerId, profileId)
            ?: throw IllegalStateException("Offer $offerId has no analysis of profile $profileId yet; run one first.")
        check(report.state == AnalysisState.DONE) {
            "Analysis ${report.id} is ${report.state}; a document can only be tailored to a completed analysis."
        }

        val roleTitle = offer.title ?: offer.displayTitle
        val company = offer.company ?: "the company"

        // Scoped so the model call's audit row names the profile it was about and is erased with it.
        val built = LlmCallScope.forProfile(profileId) {
            when (type) {
                DocumentType.CV -> buildCv(profile, report, roleTitle, company, language, offer.company)
                DocumentType.COVER_LETTER -> buildCoverLetter(profile, report, roleTitle, company, language)
            }
        }

        enforceNoFabrication(built.html, profile)
        recordDrops(offerId, type, built)

        val saved = documents.save(
            GeneratedDocumentRow(
                jobOfferId = offerId,
                profileId = profileId,
                analysisId = report.id,
                type = type.name,
                language = language,
                html = built.html,
                selectionJson = json.writeValueAsString(built.selection),
                droppedBulletCount = built.droppedBulletCount,
                droppedSkillCount = built.droppedSkillCount,
                profileRevision = profile.revision,
                consentClauseLanguage = built.consentClauseLanguage,
            )
        )
        return saved.toDomain()
    }

    /**
     * The document is sound either way - selection dropped these before they could be rendered.
     * Logged because the rate is the thing worth noticing, and a rate nobody ever sees is a rate
     * nobody acts on. The counts persist alongside the row so the trend outlives the log buffer.
     */
    private fun recordDrops(offerId: Long, type: DocumentType, built: Built) {
        if (built.droppedBulletCount == 0 && built.droppedSkillCount == 0) return
        log.warn(
            "Tailoring for offer {} ({}) cited {} bullet id(s) and {} skill(s) absent from the profile; dropped: {}",
            offerId, type, built.droppedBulletCount, built.droppedSkillCount, built.droppedNames,
        )
    }

    /** A rendered document plus what the model asked for that the profile could not back. */
    private data class Built(
        val html: String,
        val selection: Any,
        val droppedBulletCount: Int = 0,
        val droppedSkillCount: Int = 0,
        val droppedNames: List<String> = emptyList(),
        val consentClauseLanguage: String? = null,
    )

    private fun buildCv(
        profile: CandidateProfile,
        report: AnalysisReport,
        roleTitle: String,
        company: String,
        language: String,
        rawCompany: String?,
    ): Built {
        val tailored = aiServices.create(CvTailor::class.java, LlmTask.DOCUMENT).tailor(
            roleTitle = roleTitle,
            company = company,
            requirements = ProfileBriefing.requirements(report),
            profile = ProfileBriefing.profile(profile, catalog),
            language = language,
        )

        val selection = CvSelection.from(tailored, profile, catalog)
        val clause = profile.consentClauses.firstOrNull { it.language.equals(language, ignoreCase = true) }
        // Deterministic string substitution, never a model - see ConsentClause. An offer with no
        // company name leaves the placeholder visible rather than substituting a made-up employer.
        val consentText = clause?.let { rawCompany?.let { name -> it.text.replace("{{company}}", name) } ?: it.text }
        return Built(
            html = render(
                "cv",
                mapOf(
                    "cv" to selection.toView(profile, catalog),
                    "langCode" to langCode(language),
                    "consentClause" to (consentText ?: ""),
                )
            ),
            selection = selection,
            droppedBulletCount = selection.droppedBulletIds.size,
            droppedSkillCount = selection.droppedSkillNames.size,
            droppedNames = selection.droppedSkillNames + selection.droppedBulletIds.map { "bullet#$it" },
            consentClauseLanguage = clause?.language,
        )
    }

    private fun buildCoverLetter(
        profile: CandidateProfile,
        report: AnalysisReport,
        roleTitle: String,
        company: String,
        language: String,
    ): Built {
        val letter = aiServices.create(CoverLetterWriter::class.java, LlmTask.DOCUMENT).write(
            roleTitle = roleTitle,
            company = company,
            requirements = ProfileBriefing.requirements(report),
            profile = ProfileBriefing.profile(profile, catalog),
            language = language,
        )

        val paragraphs = letter.paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        val view = CoverLetterView(
            fullName = profile.details.fullName,
            contacts = DocumentViews.contactsOf(profile),
            links = profile.links,
            roleLine = "Re: $roleTitle at $company",
            paragraphs = paragraphs,
        )
        // A letter selects nothing by id, so it has no drop count of its own. CvInvariant over the
        // rendered text remains its only guard, and that runs on the way out of generate().
        return Built(
            html = render("cover-letter", mapOf("letter" to view, "langCode" to langCode(language))),
            selection = mapOf("paragraphs" to paragraphs),
        )
    }

    /**
     * The last line of defence. Selection already restricts bullets and skills to profile records,
     * but the summary line and any rewritten bullet are free text, and free text is where a model
     * can still slip a technology in.
     *
     * Scans the visible text rather than the markup: the page's own doctype and stylesheet contain
     * "HTML" and "CSS", which are themselves catalog skills.
     */
    private fun enforceNoFabrication(html: String, profile: CandidateProfile) {
        val readable = HtmlText.visibleText(html)
        val violations = CvInvariant.violations(readable, catalog.findAll(), profile.heldSkillIds)
        if (violations.isNotEmpty()) {
            log.warn("Discarding generated document claiming absent skills: {}", violations)
            throw FabricatedClaimException(violations)
        }
    }

    private fun render(template: String, variables: Map<String, Any>): String =
        templates.process(template, Context(Locale.ENGLISH, variables))

    private fun langCode(language: String): String = when (language.lowercase()) {
        "polish", "polski", "pl" -> "pl"
        "german", "deutsch", "de" -> "de"
        else -> "en"
    }

    @Transactional(readOnly = true)
    override fun findById(documentId: Long): GeneratedDocument? =
        documents.findById(documentId).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun latest(offerId: Long, type: DocumentType, profileId: Long?): GeneratedDocument? =
        documents.findLatest(offerId, profileId ?: profiles.defaultProfileId(), type.name)?.toDomain()

    override fun renderPdf(documentId: Long): ByteArray {
        val document = findById(documentId) ?: throw NoSuchElementException("No document $documentId")
        return renderer.toPdf(document.html)
    }
}

private fun GeneratedDocumentRow.toDomain() = GeneratedDocument(
    id = requireNotNull(id),
    offerId = jobOfferId,
    profileId = profileId,
    analysisId = analysisId,
    type = DocumentType.valueOf(type),
    language = language,
    html = html,
    createdAt = createdAt,
    profileRevision = profileRevision,
    droppedBulletCount = droppedBulletCount,
    droppedSkillCount = droppedSkillCount,
    consentClauseLanguage = consentClauseLanguage,
)
