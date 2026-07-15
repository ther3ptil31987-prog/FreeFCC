package com.freefcc.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the fail-closed gates S-011 added to UpdateChecker: a missing
 * digest, a size mismatch, a redirect to a disallowed host, and a signer
 * mismatch must each independently reject the update. Both pure functions
 * are exercised directly (no network/PackageManager needed) so the reject
 * logic is verified without depending on Android test infrastructure this
 * project doesn't have yet (no Robolectric/Mockito/androidTest source set).
 */
class UpdateCheckerSecurityTest {

    private val validDigest = "a".repeat(64)

    // --- evaluateDownloadGates ---

    @Test
    fun rejectsWhenDigestMissing() {
        assertFalse(
            UpdateChecker.evaluateDownloadGates(
                expectedSha256 = null,
                actualDigestHex = validDigest,
                expectedSize = 100,
                actualSize = 100,
                authenticityMatch = true
            )
        )
    }

    @Test
    fun rejectsOnSizeMismatch() {
        assertFalse(
            UpdateChecker.evaluateDownloadGates(
                expectedSha256 = validDigest,
                actualDigestHex = validDigest,
                expectedSize = 100,
                actualSize = 99,
                authenticityMatch = true
            )
        )
    }

    @Test
    fun rejectsWhenExpectedSizeIsMissing() {
        assertFalse(
            UpdateChecker.evaluateDownloadGates(
                expectedSha256 = validDigest,
                actualDigestHex = validDigest,
                expectedSize = 0,
                actualSize = 0,
                authenticityMatch = true
            )
        )
    }

    @Test
    fun rejectsOnDigestMismatch() {
        assertFalse(
            UpdateChecker.evaluateDownloadGates(
                expectedSha256 = validDigest,
                actualDigestHex = "b".repeat(64),
                expectedSize = 100,
                actualSize = 100,
                authenticityMatch = true
            )
        )
    }

    @Test
    fun rejectsOnSignerMismatch() {
        assertFalse(
            UpdateChecker.evaluateDownloadGates(
                expectedSha256 = validDigest,
                actualDigestHex = validDigest,
                expectedSize = 100,
                actualSize = 100,
                authenticityMatch = false
            )
        )
    }

    @Test
    fun acceptsWhenAllGatesPass() {
        assertTrue(
            UpdateChecker.evaluateDownloadGates(
                expectedSha256 = validDigest,
                actualDigestHex = validDigest.uppercase(),
                expectedSize = 100,
                actualSize = 100,
                authenticityMatch = true
            )
        )
    }

    // --- isAllowedDownloadUrl ---

    @Test
    fun allowsKnownGitHubReleaseHosts() {
        assertTrue(UpdateChecker.isAllowedDownloadUrl("https://github.com/doesthings/FreeFCC/releases/download/v1/app.apk"))
        assertTrue(UpdateChecker.isAllowedDownloadUrl("https://objects.githubusercontent.com/some/asset"))
        assertTrue(UpdateChecker.isAllowedDownloadUrl("https://release-assets.githubusercontent.com/some/asset"))
    }

    @Test
    fun rejectsUnknownHost() {
        assertFalse(UpdateChecker.isAllowedDownloadUrl("https://evil.example.com/app.apk"))
    }

    @Test
    fun rejectsNonHttpsScheme() {
        assertFalse(UpdateChecker.isAllowedDownloadUrl("http://github.com/doesthings/FreeFCC/releases/download/v1/app.apk"))
    }

    @Test
    fun rejectsMalformedUrl() {
        assertFalse(UpdateChecker.isAllowedDownloadUrl("not a url"))
    }
}
