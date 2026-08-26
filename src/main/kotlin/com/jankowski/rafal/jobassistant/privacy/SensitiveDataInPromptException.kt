package com.jankowski.rafal.jobassistant.privacy

/**
 * Thrown instead of sending a prompt that carries a direct identifier.
 *
 * [fields] names the profile fields that matched - `fullName`, `email` - and never their values.
 * The message ends up in `analysis.error` and in an HTTP problem detail, both of which are readable
 * over the wire, so including the offending value would leak exactly what the refusal prevented.
 */
class SensitiveDataInPromptException(val fields: List<String>) : RuntimeException(
    "Refusing to send a prompt containing personal data: ${fields.joinToString()}. " +
        "A prompt is built from profile records deliberately, so this means a builder started " +
        "including an identifying field - fix the builder rather than the check."
)
