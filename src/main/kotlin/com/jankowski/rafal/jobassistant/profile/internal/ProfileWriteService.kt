package com.jankowski.rafal.jobassistant.profile.internal

import com.jankowski.rafal.jobassistant.profile.CandidateProfile
import com.jankowski.rafal.jobassistant.profile.ProfileService
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Per-entity editing of the profile, written once for all nine collections.
 *
 * The concept the nine are instances of is *an ordered, profile-owned collection whose writes bump
 * the profile revision and answer with the whole profile*. That is what this class implements;
 * [ProfileCollections] says which nine there are and what rules each has of its own. The properties
 * that hold for every collection - a reorder names its ids exactly once, a new row lands at the end
 * of its own owner's rows, every write bumps the revision - are asserted here or nowhere, which is
 * the point: there is no longer a copy that can quietly get one of them wrong.
 *
 * Internal on purpose: no other module has any business writing a single skill or bullet, and the
 * cross-module contract stays "read it, or replace it wholesale". Every mutation returns the whole
 * profile, so a caller never has to reassemble one from a patch response and the UI has a single
 * query to invalidate.
 */
@Service
internal class ProfileWriteService(
    private val profiles: ProfileService,
    private val experiences: WorkExperienceRepository,
    private val projects: ProjectRepository,
    private val jdbc: JdbcClient,
) {

    // ------------------------------------------------------------ collections

    @Transactional
    fun <Row : Any, Add : Any, Update : Any> add(
        collection: ProfileCollection<Row, Add, Update>,
        owner: CollectionOwner,
        request: Add,
    ): CandidateProfile {
        requireOwnerExists(owner)
        collection.onAdd(owner.profileId, request)
        collection.repository.save(collection.newRow(owner, request, nextOrder(collection, owner)))
        return commit(owner.profileId)
    }

    @Transactional
    fun <Row : Any, Add : Any, Update : Any> update(
        collection: ProfileCollection<Row, Add, Update>,
        profileId: Long,
        id: Long,
        request: Update,
    ): CandidateProfile {
        collection.checkUpdate(profileId, request)
        val row = collection.findOne(id, profileId) ?: throw unknown(collection.entity, id)
        collection.onUpdate(profileId, row, request)
        collection.repository.save(collection.applyUpdate(row, request))
        return commit(profileId)
    }

    @Transactional
    fun <Row : Any, Add : Any, Update : Any> delete(
        collection: ProfileCollection<Row, Add, Update>,
        profileId: Long,
        id: Long,
    ): CandidateProfile {
        val row = collection.findOne(id, profileId) ?: throw unknown(collection.entity, id)
        collection.onDelete(profileId, row)
        collection.repository.delete(row)
        return commit(profileId)
    }

    /**
     * A reorder must name the collection exactly. Accepting a subset would silently leave the
     * omitted rows wherever they were, which looks like a bug rather than a partial reorder.
     */
    @Transactional
    fun <Row : Any, Add : Any, Update : Any> reorder(
        collection: ProfileCollection<Row, Add, Update>,
        owner: CollectionOwner,
        ids: List<Long>,
    ): CandidateProfile {
        requireOwnerInProfile(owner)
        val current = collection.orderedIds(owner)
        if (ids.size != ids.toSet().size || ids.toSet() != current.toSet()) {
            throw ProfileConflictException(
                "A reorder must list every id in the collection exactly once. " +
                    "Expected ${current.sorted()}, got ${ids.sorted()}."
            )
        }
        ids.forEachIndexed { position, id ->
            // The table name is interpolated, but only ever from the literal on the descriptor.
            jdbc.sql("update ${collection.table} set display_order = :position where id = :id")
                .param("position", position)
                .param("id", id)
                .update()
        }
        return commit(owner.profileId)
    }

    // ---------------------------------------------------------------- details

    /**
     * Creates the details for an already-existing profile. This is the only endpoint that can bring
     * a profile's contents into existence without a document - the profile itself (the `profile` root
     * row) must already exist, via `POST /api/profiles`.
     *
     * Deliberately outside the collection mechanism: it is an upsert of a single row rather than an
     * ordered collection, and there is nothing to reorder, nothing to name in a 404 and no id.
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

    // --------------------------------------------------------------- internals

    /**
     * An insert needs its owner to exist, or the foreign key answers with a constraint violation
     * where the caller should have been told which id was wrong.
     */
    private fun requireOwnerExists(owner: CollectionOwner) {
        if (owner is CollectionOwner.Profile) requireProfileExists(owner.profileId)
        requireOwnerInProfile(owner)
    }

    /**
     * A parent-scoped collection has to prove its parent belongs to this profile: its ordering
     * query is scoped by the parent alone, so without this one profile could read - and reorder -
     * another's bullets. A profile-scoped collection needs no such check, because its own query
     * already carries the scope.
     */
    private fun requireOwnerInProfile(owner: CollectionOwner) {
        when (owner) {
            is CollectionOwner.Profile -> Unit
            is CollectionOwner.Experience ->
                experiences.findByIdAndProfileId(owner.experienceId, owner.profileId)
                    ?: throw unknown("experience", owner.experienceId)
            is CollectionOwner.Project ->
                projects.findByIdAndProfileId(owner.projectId, owner.profileId)
                    ?: throw unknown("project", owner.projectId)
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
     * New rows land at the end of whatever they are joining, counted within their own owner.
     *
     * The table and the owner column are interpolated, but only ever from the literals declared on
     * the descriptor - never from request input, which supplies the bound id. Scoping is not
     * optional: without it two personas' display orders would interleave.
     */
    private fun nextOrder(collection: ProfileCollection<*, *, *>, owner: CollectionOwner): Int =
        jdbc.sql(
            "select coalesce(max(display_order) + 1, 0) from ${collection.table} " +
                "where ${owner.column} = :ownerId"
        )
            .param("ownerId", owner.id)
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
