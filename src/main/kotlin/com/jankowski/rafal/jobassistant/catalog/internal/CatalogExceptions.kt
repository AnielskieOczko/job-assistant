package com.jankowski.rafal.jobassistant.catalog.internal

/** A canonical skill id the catalog does not have. */
internal class UnknownSkillException(message: String) : RuntimeException(message)

/**
 * A catalog write that cannot be reconciled with what is stored: a rename colliding with another
 * skill's name, or a delete refused because a profile or bullet still cites the skill.
 */
internal class CatalogConflictException(message: String) : RuntimeException(message)
