package com.jankowski.rafal.jobassistant.profile

/**
 * A profile's optional photograph, as stored.
 *
 * **This is a direct identifier and follows the rule the candidate's name already follows.** It is
 * never interpolated into a prompt, has no field on any AI-service type, and reaches a document
 * only from the renderer, after the model has answered. Nothing in [CandidateProfile] carries it -
 * that type is what prompt builders read, and the profile exposes only [CandidateProfile.hasPortrait]
 * there so a builder cannot pick up the bytes by accident.
 *
 * Not a data class: a [ByteArray] gives generated `equals` reference semantics, which would make
 * `==` quietly mean something other than what it reads as.
 */
class ProfilePortrait(val mediaType: String, val bytes: ByteArray)
