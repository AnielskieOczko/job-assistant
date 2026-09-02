package com.jankowski.rafal.jobassistant.profile

import com.jankowski.rafal.jobassistant.profile.internal.ProfileManagementService
import com.jankowski.rafal.jobassistant.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The portrait endpoints, asserted over HTTP for the reason [ProfileCrudHttpTest] gives: the status
 * codes are what the SPA branches on, and the one for a rejected upload is the whole point of the
 * feature having a validation step at all.
 */
@IntegrationTest
internal class ProfilePortraitHttpTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val profiles: ProfileService,
    @Autowired private val management: ProfileManagementService,
    @Autowired private val jdbc: JdbcClient,
) {

    private lateinit var mvc: MockMvc
    private var profileId: Long = 0

    /** A one-pixel PNG. Real image bytes, because the media type is sniffed rather than declared. */
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00,
        0x1F, 0x15.toByte(), 0xC4.toByte(), 0x89.toByte(),
    )

    @BeforeEach
    fun reset() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        jdbc.sql("delete from profile").update()
        profileId = management.create("Test").id
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/profiles/$profileId/details")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Rafal Jankowski"}""")
        ).andExpect(status().isOk)
    }

    private fun upload(bytes: ByteArray, declaredType: String = MediaType.IMAGE_PNG_VALUE) =
        mvc.perform(
            multipart("/api/profiles/$profileId/portrait")
                .file(MockMultipartFile("file", "photo.png", declaredType, bytes))
                .with { it.method = "PUT"; it }
        )

    @Test
    fun `uploading a portrait answers with the profile carrying it`() {
        upload(png)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hasPortrait").value(true))

        // The bytes never travel in the profile document - only the fact of them.
        assertEquals(true, profiles.require(profileId).hasPortrait)
        assertContentEquals(png, profiles.portrait(profileId)!!.bytes)
    }

    @Test
    fun `a profile with no portrait says so, and serving one 404s`() {
        mvc.perform(get("/api/profiles/$profileId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hasPortrait").value(false))

        mvc.perform(get("/api/profiles/$profileId/portrait")).andExpect(status().isNotFound)
    }

    @Test
    fun `the stored media type is sniffed from the bytes, not taken from the request`() {
        // The client claims JPEG; the bytes are a PNG. What is served back is what was actually sent.
        upload(png, declaredType = MediaType.IMAGE_JPEG_VALUE).andExpect(status().isOk)

        mvc.perform(get("/api/profiles/$profileId/portrait"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
    }

    @Test
    fun `a file that is not an image is refused with 415`() {
        upload("this is a PDF, honestly".toByteArray())
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.title").value("Portrait rejected"))

        assertEquals(false, profiles.require(profileId).hasPortrait)
    }

    @Test
    fun `replacing a portrait overwrites rather than accumulating`() {
        upload(png).andExpect(status().isOk)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        upload(jpeg).andExpect(status().isOk)

        assertEquals(1, jdbc.sql("select count(*) from profile_portrait").query(Int::class.java).single())
        assertEquals("image/jpeg", profiles.portrait(profileId)!!.mediaType)
    }

    @Test
    fun `deleting a portrait removes it, and deleting it twice is a 404`() {
        upload(png).andExpect(status().isOk)

        mvc.perform(delete("/api/profiles/$profileId/portrait"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hasPortrait").value(false))

        mvc.perform(delete("/api/profiles/$profileId/portrait")).andExpect(status().isNotFound)
    }

    @Test
    fun `uploading to an unknown profile is a 404, not a foreign key violation`() {
        mvc.perform(
            multipart("/api/profiles/999999/portrait")
                .file(MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, png))
                .with { it.method = "PUT"; it }
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `a portrait bumps the profile revision, so documents built before it read as stale`() {
        val before = profiles.revision(profileId)

        upload(png).andExpect(status().isOk)
        val afterUpload = profiles.revision(profileId)

        mvc.perform(delete("/api/profiles/$profileId/portrait")).andExpect(status().isOk)

        assertEquals(before + 1, afterUpload)
        assertEquals(before + 2, profiles.revision(profileId))
    }

    @Test
    fun `deleting the profile deletes the portrait with it`() {
        upload(png).andExpect(status().isOk)

        jdbc.sql("delete from profile where id = :id").param("id", profileId).update()

        // The cascade is the erasure guarantee for a direct identifier, so it is asserted against
        // the table rather than through an API that would report the same thing either way.
        assertEquals(
            0,
            jdbc.sql("select count(*) from profile_portrait where profile_id = :id")
                .param("id", profileId)
                .query(Int::class.java)
                .single(),
        )
    }
}
