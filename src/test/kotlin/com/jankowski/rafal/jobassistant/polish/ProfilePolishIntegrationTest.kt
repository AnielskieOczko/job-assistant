package com.jankowski.rafal.jobassistant.polish

import com.jankowski.rafal.jobassistant.llm.LlmTask
import com.jankowski.rafal.jobassistant.profile.BulletImport
import com.jankowski.rafal.jobassistant.profile.ExperienceImport
import com.jankowski.rafal.jobassistant.profile.Proficiency
import com.jankowski.rafal.jobassistant.profile.ProfileDetails
import com.jankowski.rafal.jobassistant.profile.ProfileImport
import com.jankowski.rafal.jobassistant.profile.ProfileService
import com.jankowski.rafal.jobassistant.profile.ProjectImport
import com.jankowski.rafal.jobassistant.profile.SkillImport
import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import com.jankowski.rafal.jobassistant.support.ScriptedModels
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What polish is allowed to do, and the much longer list of what it is not.
 *
 * The feature's whole claim is that a model can improve the candidate's sentence without being able
 * to change their record, so the assertions that matter are the negative ones: the profile is byte
 * for byte what it was until a human accepts, the accept goes through the ordinary CRUD endpoint
 * that has always been the only way in, and the prompt carries one field's text rather than the
 * entity it came from.
 */
@IntegrationTest
internal class ProfilePolishIntegrationTest(
    @Autowired private val polish: ProsePolishService,
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val models: ScriptedModels,
    @Autowired private val context: WebApplicationContext,
    @Autowired private val jdbc: JdbcClient,
) {

    private lateinit var mvc: MockMvc
    private var profileId = 0L

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        models.resetAll()
        jdbc.sql("delete from profile").update()

        profileId = management.create("Polish").id
        profiles.replace(
            profileId,
            ProfileImport(
                details = ProfileDetails(
                    fullName = "Rafal Jankowski",
                    headline = "Backend Engineer",
                    careerGoal = "want to work more on platform things",
                ),
                skills = listOf(
                    SkillImport("Kotlin", Proficiency.EXPERT),
                    SkillImport("Spring Boot", Proficiency.PROFICIENT),
                ),
                experiences = listOf(
                    ExperienceImport(
                        company = "Acme Corporation",
                        roleTitle = "Senior Backend Engineer",
                        startedOn = java.time.LocalDate.of(2021, 1, 1),
                        bullets = listOf(BulletImport("did the payments service in Kotlin", listOf("Kotlin"))),
                    ),
                ),
                projects = listOf(
                    ProjectImport(
                        name = "Job Assistant",
                        url = "https://github.com/example/job-assistant",
                        description = ORIGINAL_DESCRIPTION,
                        skills = listOf("Kotlin"),
                    ),
                ),
            ),
        )
    }

    private fun project() = profiles.require(profileId).projects.single()

    private fun promptsSent(): String =
        models[LlmTask.POLISH].requests
            .flatMap { it.messages() }
            .joinToString("\n") { message ->
                when (message) {
                    is SystemMessage -> message.text()
                    is UserMessage -> message.singleText()
                    else -> message.toString()
                }
            }

    @Test
    fun `a suggestion comes back and the profile is left exactly as it was`() {
        val revisionBefore = profiles.revision(profileId)
        models[LlmTask.POLISH].enqueue("""{"polished":"$POLISHED_DESCRIPTION"}""")

        val suggestion = polish.polish(profileId, PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION)

        assertEquals(POLISHED_DESCRIPTION, suggestion.suggestion)
        assertEquals(ORIGINAL_DESCRIPTION, suggestion.original, "the original travels back for the diff")
        assertEquals(PolishField.PROJECT_DESCRIPTION, suggestion.field)
        assertEquals("scripted", suggestion.modelProfile)

        assertEquals(ORIGINAL_DESCRIPTION, project().description, "the model wrote to the profile")
        assertEquals(revisionBefore, profiles.revision(profileId), "a suggestion is not a write")
    }

    /**
     * The difference from `CvInvariant`, stated as a test. The same reading runs, and here it
     * reports rather than refuses - the candidate is the next reader, and "I should add Kubernetes
     * to my skills" is a legitimate answer to this flag.
     */
    @Test
    fun `a suggestion naming an unheld skill is flagged and still shown`() {
        models[LlmTask.POLISH]
            .enqueue("""{"polished":"Kotlin service deployed on Kubernetes that tailors a CV to an offer."}""")

        val suggestion = polish.polish(profileId, PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION)

        assertEquals(listOf("Kubernetes"), suggestion.unheldSkills)
        assertTrue(suggestion.suggestion.contains("Kubernetes"), "the suggestion is flagged, not withheld")
        assertEquals(ORIGINAL_DESCRIPTION, project().description, "and nothing was stored either way")
    }

    @Test
    fun `a suggestion using only held skills is flagged with nothing`() {
        models[LlmTask.POLISH].enqueue("""{"polished":"A Kotlin and Spring Boot service that tailors a CV."}""")

        val suggestion = polish.polish(profileId, PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION)

        assertTrue(suggestion.unheldSkills.isEmpty())
    }

    /**
     * The accept, end to end. It is a `PUT` to the same endpoint the edit dialog has always used -
     * there is no polish write path, which is what makes "no model writes to the profile" a
     * structural fact rather than a promise.
     */
    @Test
    fun `accepting writes through the ordinary CRUD endpoint and bumps the revision`() {
        models[LlmTask.POLISH].enqueue("""{"polished":"$POLISHED_DESCRIPTION"}""")
        val suggestion = polish.polish(profileId, PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION)
        val revisionBefore = profiles.revision(profileId)
        val project = project()

        mvc.perform(
            put("/api/profiles/$profileId/projects/${project.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"${project.name}","url":"${project.url}",
                     "description":"${suggestion.suggestion}","skillIds":[${project.skillIds.joinToString(",")}]}
                    """.trimIndent()
                )
        ).andExpect(status().isOk)

        assertEquals(POLISHED_DESCRIPTION, project().description)
        assertTrue(profiles.revision(profileId) > revisionBefore, "the accept is an ordinary profile write")
    }

    /**
     * The prompt is built from the field, not from the entity the field belongs to. A project URL is
     * a direct identifier - `ProfileIdentityInspector` would refuse the whole call over one - and an
     * employer and a set of dates are facts the rewrite has no use for.
     */
    @Test
    fun `the prompt carries the field's text and nothing else from the profile`() {
        models[LlmTask.POLISH].enqueue("""{"polished":"$POLISHED_DESCRIPTION"}""")

        polish.polish(profileId, PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION)

        val sent = promptsSent()
        assertTrue(sent.contains(ORIGINAL_DESCRIPTION), "the field itself was not sent")
        assertTrue(sent.contains("PROJECT_DESCRIPTION"), "the model was not told which field this is")
        assertFalse(sent.contains("github.com/example"), "the project URL reached a model")
        assertFalse(sent.contains("Job Assistant"), "the project name reached a model")
        assertFalse(sent.contains("Acme Corporation"), "an employer reached a model")
        assertFalse(sent.contains("Rafal"), "the candidate's name reached a model")
    }

    @Test
    fun `an empty answer is a failure rather than an empty pane`() {
        models[LlmTask.POLISH].enqueue("""{"polished":""}""")

        assertFailsWith<UnusablePolishException> {
            polish.polish(profileId, PolishField.CAREER_GOAL, "want to work more on platform things")
        }
    }

    /**
     * The `CvSelection.from` rule at this boundary: LangChain4j builds the return type reflectively
     * without calling the constructor, so an explicit `null` lands in a property Kotlin types as
     * non-null. Reading it as a claim rather than a guarantee is what turns this into a refusal
     * instead of a `NullPointerException` from somewhere further down.
     */
    @Test
    fun `an explicit null answer is read as a claim rather than a guarantee`() {
        models[LlmTask.POLISH].enqueue("""{"polished":null}""")

        assertFailsWith<UnusablePolishException> {
            polish.polish(profileId, PolishField.CAREER_GOAL, "want to work more on platform things")
        }
    }

    /**
     * The bug this test exists for, reproduced from the response that caused it.
     *
     * A model that answers with prose instead of an object used to be reprompted, and the reprompt
     * carried no question - so it replied *"I understand you'd like me to respond with a single JSON
     * value. However, I need a question or request to respond to."*, which is well-formed JSON, has a
     * `polished` field, and rendered on screen as a suggested rewrite of the candidate's project
     * description. This surface has no way to tell a rewrite from a reply, so the answer is refused
     * rather than shown.
     */
    @Test
    fun `a model that replies instead of rewriting is refused rather than shown`() {
        models[LlmTask.POLISH].enqueue(
            "I understand you'd like me to respond with a single JSON value. However, I need a " +
                "question or request to respond to. Could you please provide what you'd like me to address?"
        )

        assertFailsWith<UnusablePolishException> {
            polish.polish(profileId, PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION)
        }

        assertEquals(1, models[LlmTask.POLISH].requests.size, "prose must not buy a second question")
        assertEquals(ORIGINAL_DESCRIPTION, project().description)
    }

    /**
     * The other half: a model that says nothing at all is asked the *same question* again, because
     * a reasoning model spending its whole completion on `thinking` is a wasted call rather than a
     * refusal. Two rows in `llm_call`, one suggestion.
     */
    @Test
    fun `a silent model is asked again with the original request`() {
        models[LlmTask.POLISH].enqueue("", """{"polished":"$POLISHED_DESCRIPTION"}""")

        val suggestion = polish.polish(profileId, PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION)

        assertEquals(POLISHED_DESCRIPTION, suggestion.suggestion)
        assertEquals(2, models[LlmTask.POLISH].requests.size)
        assertTrue(
            promptsSent().split(ORIGINAL_DESCRIPTION).size - 1 == 2,
            "the retry has to carry the text being polished, or it is answering nothing",
        )
    }

    @Test
    fun `blank text is refused before a model is called`() {
        assertFailsWith<IllegalArgumentException> {
            polish.polish(profileId, PolishField.CAREER_GOAL, "   ")
        }

        assertTrue(models[LlmTask.POLISH].requests.isEmpty(), "an empty box must not cost a call")
    }

    @Test
    fun `text longer than a field holds is refused before a model is called`() {
        assertFailsWith<IllegalArgumentException> {
            polish.polish(profileId, PolishField.PROJECT_DESCRIPTION, "x".repeat(ProsePolishService.MAX_TEXT_LENGTH + 1))
        }

        assertTrue(models[LlmTask.POLISH].requests.isEmpty(), "a pasted CV must not be priced like an analysis")
    }

    // ------------------------------------------------------------------ HTTP

    private fun polishRequest(field: PolishField, text: String, id: Long = profileId) =
        post("/api/profiles/$id/polish?field=$field")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"text":"$text"}""")

    @Test
    fun `the endpoint answers with the suggestion, the original and what it flagged`() {
        models[LlmTask.POLISH]
            .enqueue("""{"polished":"Kotlin service deployed on Kubernetes that tailors a CV to an offer."}""")

        mvc.perform(polishRequest(PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.field").value("PROJECT_DESCRIPTION"))
            .andExpect(jsonPath("$.original").value(ORIGINAL_DESCRIPTION))
            .andExpect(jsonPath("$.unheldSkills[0]").value("Kubernetes"))
            .andExpect(jsonPath("$.modelProfile").value("scripted"))
    }

    @Test
    fun `an unknown profile is a 404 and never a model call`() {
        mvc.perform(polishRequest(PolishField.CAREER_GOAL, "anything", id = profileId + 9999))
            .andExpect(status().isNotFound)

        assertTrue(models[LlmTask.POLISH].requests.isEmpty())
    }

    @Test
    fun `blank text is a 400`() {
        mvc.perform(polishRequest(PolishField.CAREER_GOAL, " "))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `an unknown field kind is a 400 rather than a fifth thing to polish`() {
        mvc.perform(
            post("/api/profiles/$profileId/polish?field=PROFILE_EMAIL")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"anything"}""")
        ).andExpect(status().isBadRequest)

        assertTrue(models[LlmTask.POLISH].requests.isEmpty())
    }

    @Test
    fun `an empty answer is a 422`() {
        models[LlmTask.POLISH].enqueue("""{"polished":"   "}""")

        mvc.perform(polishRequest(PolishField.PROJECT_DESCRIPTION, ORIGINAL_DESCRIPTION))
            .andExpect(status().isUnprocessableEntity)
    }

    private companion object {
        const val ORIGINAL_DESCRIPTION = "a tool i built that reads a job offer and makes a cv"
        const val POLISHED_DESCRIPTION = "A tool that reads a job offer and tailors a CV to it."
    }
}
