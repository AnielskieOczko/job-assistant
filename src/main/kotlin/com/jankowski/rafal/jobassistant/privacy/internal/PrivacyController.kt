package com.jankowski.rafal.jobassistant.privacy.internal

import com.jankowski.rafal.jobassistant.privacy.PrivacyManifest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The one privacy endpoint: what leaves this machine, and what does not.
 *
 * Read-only and profile-independent, so it needs no profile id and answers the same manifest
 * whether or not a persona exists yet.
 */
@RestController
@RequestMapping("/api/privacy")
internal class PrivacyController {

    @GetMapping("/manifest")
    fun manifest(): PrivacyManifest = PrivacyManifests.MANIFEST
}
