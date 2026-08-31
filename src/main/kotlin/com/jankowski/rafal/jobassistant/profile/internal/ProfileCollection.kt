package com.jankowski.rafal.jobassistant.profile.internal

import org.springframework.data.repository.CrudRepository

/**
 * What one profile collection needs to say about itself that is not true of all of them.
 *
 * The profile holds nine collections and they share a shape: *an ordered, profile-owned collection
 * whose writes bump the profile revision and answer with the whole profile.* That shape is written
 * once, in [ProfileWriteService]. A descriptor supplies only the parts that genuinely differ - which
 * repository holds the rows, which table carries the `display_order`, how a request becomes a row,
 * and what rules this entity has that the shape does not.
 *
 * The rules are the interesting half. A collection with none of them (links, education) is four
 * lambdas; the ones that refuse something - a skill still cited by a bullet, a second consent clause
 * in a language already covered - say so in a hook, where it stands out instead of being buried in
 * boilerplate that looks just like it.
 *
 * [Add] and [Update] are separate type parameters because a skill is added by naming a catalog id
 * and updated without one: swapping the identity under a stored row would strand every bullet
 * tagged with the old skill.
 */
internal class ProfileCollection<Row : Any, Add : Any, Update : Any>(
    /** Names this collection's entity in a 404, in the singular: "link", "education entry". */
    val entity: String,

    /**
     * The table carrying `display_order`.
     *
     * Interpolated into SQL, so it must stay what it is here - a fixed literal declared alongside
     * the collection, never a value that can originate from a request.
     */
    val table: String,

    val repository: CrudRepository<Row, Long>,

    /** The row with this id *on this profile*, or null. Scoping is the caller's whole defence here. */
    val findOne: (id: Long, profileId: Long) -> Row?,

    /** Every id in the collection, in its current order, scoped to [owner]. */
    val orderedIds: (owner: CollectionOwner) -> List<Long>,

    val newRow: (owner: CollectionOwner, request: Add, displayOrder: Int) -> Row,

    val applyUpdate: (row: Row, request: Update) -> Row,

    /** Rules to check before a row is added. Runs after the owner is known to exist. */
    val onAdd: (profileId: Long, request: Add) -> Unit = { _, _ -> },

    /**
     * Rules that judge the request alone, checked before the row is looked up.
     *
     * Which of 404 and 409 wins when an unknown id carries an invalid body depends on this: a rule
     * here answers 409, one in [onUpdate] answers 404. Each collection keeps the answer it has
     * always given; unifying them is a behaviour change and belongs in its own commit.
     */
    val checkUpdate: (profileId: Long, request: Update) -> Unit = { _, _ -> },

    /** Rules that need the stored row - a duplicate check that must not fire against itself. */
    val onUpdate: (profileId: Long, row: Row, request: Update) -> Unit = { _, _, _ -> },

    /** Rules that can refuse a delete, such as a skill still cited by a bullet. */
    val onDelete: (profileId: Long, row: Row) -> Unit = { _, _ -> },
)

/**
 * The row a collection's entries hang from, and the scope their `display_order` counts within.
 *
 * Eight of the nine collections belong to the profile directly. Bullets belong to a work experience
 * or to a project, which is why this is not simply a profile id: `display_order` restarts inside
 * each role, and two roles' bullets must not be numbered against each other.
 *
 * [column] is a fixed literal from this file and is interpolated into SQL; [id] comes from the
 * request path and is always bound.
 */
internal sealed interface CollectionOwner {

    val profileId: Long
    val column: String
    val id: Long

    data class Profile(override val profileId: Long) : CollectionOwner {
        override val column = "profile_id"
        override val id = profileId
    }

    data class Experience(override val profileId: Long, val experienceId: Long) : CollectionOwner {
        override val column = "work_experience_id"
        override val id = experienceId
    }

    data class Project(override val profileId: Long, val projectId: Long) : CollectionOwner {
        override val column = "project_id"
        override val id = projectId
    }
}
