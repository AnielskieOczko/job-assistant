package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.profile.ProfilePortrait
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Storing and reading the one photograph a profile may have.
 *
 * Kept apart from [ProfileWriteService], which implements the nine ordered collections: a portrait
 * has no ordering, no id and no siblings, so putting it through that mechanism would mean a
 * descriptor describing nothing. It is closer to `putDetails`, which is outside the mechanism for
 * the same reason.
 */
internal interface ProfilePortraits {

    fun read(profileId: Long): ProfilePortrait?

    fun exists(profileId: Long): Boolean

    /** @throws UnsupportedPortraitException when [bytes] are not a JPEG, PNG or WebP image. */
    fun save(profileId: Long, bytes: ByteArray)

    /** @return false when there was nothing to remove, which the endpoint answers as a 404. */
    fun delete(profileId: Long): Boolean
}

/** The upload is not an image this application will store. Answered as HTTP 415. */
internal class UnsupportedPortraitException(message: String) : RuntimeException(message)

@Service
internal class JdbcProfilePortraits(private val jdbc: JdbcClient) : ProfilePortraits {

    @Transactional(readOnly = true)
    override fun read(profileId: Long): ProfilePortrait? =
        jdbc.sql("select media_type, bytes from profile_portrait where profile_id = :profileId")
            .param("profileId", profileId)
            .query { rs, _ -> ProfilePortrait(rs.getString("media_type"), rs.getBytes("bytes")) }
            .optional()
            .orElse(null)

    @Transactional(readOnly = true)
    override fun exists(profileId: Long): Boolean =
        jdbc.sql("select exists(select 1 from profile_portrait where profile_id = :profileId)")
            .param("profileId", profileId)
            .query(Boolean::class.java)
            .single()

    @Transactional
    override fun save(profileId: Long, bytes: ByteArray) {
        // The declared content type is whatever the browser felt like sending, so the stored one is
        // read out of the bytes instead. A file that is not an image never reaches the table.
        val mediaType = ImageBytes.mediaTypeOf(bytes)
            ?: throw UnsupportedPortraitException(
                "Only JPEG, PNG and WebP images are accepted; this file is none of them."
            )

        jdbc.sql(
            """
            insert into profile_portrait (profile_id, media_type, bytes, updated_at)
            values (:profileId, :mediaType, :bytes, now())
            on conflict (profile_id) do update
                set media_type = excluded.media_type, bytes = excluded.bytes, updated_at = now()
            """.trimIndent()
        )
            .param("profileId", profileId)
            .param("mediaType", mediaType)
            .param("bytes", bytes)
            .update()

        bumpRevision(profileId)
    }

    @Transactional
    override fun delete(profileId: Long): Boolean {
        val removed = jdbc.sql("delete from profile_portrait where profile_id = :profileId")
            .param("profileId", profileId)
            .update() > 0

        if (removed) bumpRevision(profileId)
        return removed
    }

    /**
     * A portrait change is a profile write like any other: it changes what a CV renders, so a
     * document generated before it has been overtaken and must be shown as stale rather than
     * current. The statement is the same one [JdbcProfileService] runs after every edit.
     */
    private fun bumpRevision(profileId: Long) {
        jdbc.sql("update profile set revision = revision + 1 where id = :profileId")
            .param("profileId", profileId)
            .update()
    }
}

/**
 * What kind of image a byte array actually is.
 *
 * Sniffed rather than trusted: `Content-Type` on a multipart part is supplied by the client, so
 * accepting it would let anything at all into a column the renderer inlines into a document.
 */
internal object ImageBytes {

    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )
    private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)

    fun mediaTypeOf(bytes: ByteArray): String? = when {
        bytes.startsWith(JPEG, 0) -> "image/jpeg"
        bytes.startsWith(PNG, 0) -> "image/png"
        // A WebP file is a RIFF container whose form type sits at offset 8, after the length word.
        bytes.startsWith(RIFF, 0) && bytes.startsWith(WEBP, 8) -> "image/webp"
        else -> null
    }

    private fun ByteArray.startsWith(prefix: ByteArray, offset: Int): Boolean {
        if (size < offset + prefix.size) return false
        return prefix.indices.all { this[offset + it] == prefix[it] }
    }
}
