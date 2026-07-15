package com.freefcc.app

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Checks for app updates by querying the GitHub Releases API.
 *
 * GitHub API endpoint:
 *   GET https://api.github.com/repos/doesthings/FreeFCC/releases/latest
 *
 * Returns JSON with tag_name, name, body (changelog), and assets[] (download URLs).
 *
 * The download flow is fail-closed: any of the following rejects the
 * update and deletes the partial file rather than falling back to an
 * unverified install.
 *   1. Refuse if GitHub reported no SHA-256 digest for the asset
 *   2. Follow redirects manually, allowlisting the target host of every
 *      hop (GitHub release assets are served from a CDN, not api/github.com)
 *   3. Download the APK to app cache dir
 *   4. Verify the downloaded size against the GitHub asset size
 *   5. Verify the SHA-256 digest against the GitHub asset digest
 *   6. Verify the package ID and signing certificates match the
 *      currently installed app — the real trust anchor, since the
 *      digest above only proves transit integrity against metadata
 *      from the same release, not that the release itself is genuine
 */
data class UpdateInfo(
    val version: String,       // e.g. "1.4"
    val title: String,         // e.g. "v1.4 — Altitude Unlock"
    val changelog: String,    // release body (markdown)
    val downloadUrl: String,  // direct APK URL
    val apkSize: Long,        // bytes
    val publishedAt: String,  // ISO date
    val sha256: String?       // expected hex digest from GitHub, or null if absent
) {
    fun isNewerThan(currentVersion: String): Boolean {
        val cur = parseVersion(currentVersion)
        val new = parseVersion(version)
        val maxLen = maxOf(cur.size, new.size)
        for (i in 0 until maxLen) {
            val c = cur.getOrElse(i) { 0 }
            val n = new.getOrElse(i) { 0 }
            if (n != c) return n > c
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> {
        return v.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    }
}

/**
 * Verifies a downloaded APK's package ID and signing certificates match
 * the currently installed app. Behind an interface so the gate logic in
 * [UpdateChecker.downloadApk] stays testable without a real PackageManager.
 */
interface ApkAuthenticityChecker {
    fun matchesInstalledApp(context: Context, apkFile: File): Boolean
}

object DefaultApkAuthenticityChecker : ApkAuthenticityChecker {
    override fun matchesInstalledApp(context: Context, apkFile: File): Boolean {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
        if (archiveInfo.packageName != context.packageName) return false

        val installedInfo = pm.getPackageInfo(context.packageName, flags)
        val archiveSigners = archiveInfo.signingInfo?.apkContentsSigners
            ?.map { it.toByteArray().toList() }?.toSet()
        val installedSigners = installedInfo.signingInfo?.apkContentsSigners
            ?.map { it.toByteArray().toList() }?.toSet()
        if (archiveSigners.isNullOrEmpty() || installedSigners.isNullOrEmpty()) return false

        return archiveSigners == installedSigners
    }
}

object UpdateChecker {

    private const val REPO = "doesthings/FreeFCC"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    // GitHub release assets redirect from api.github.com/github.com through
    // this CDN before the actual bytes are served — every hop must be
    // re-checked against this allowlist before the next request is made.
    private val ALLOWED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )
    private const val MAX_REDIRECTS = 5

    /** Pure — no I/O — so the allowlist decision itself is unit-testable. */
    internal fun isAllowedDownloadUrl(url: String): Boolean {
        val parsed = try {
            URL(url)
        } catch (_: Exception) {
            return false
        }
        return parsed.protocol == "https" && parsed.host in ALLOWED_DOWNLOAD_HOSTS
    }

    private fun openAllowlistedConnection(
        startUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection? {
        var currentUrl = startUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowedDownloadUrl(currentUrl)) return null
            val parsed = URL(currentUrl)

            val conn = (parsed.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", "FreeFCC-App")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) return null
                currentUrl = location
            } else if (code == 200) {
                return conn
            } else {
                conn.disconnect()
                return null
            }
        }
        return null
    }

    /**
     * Fetches the latest release info from GitHub.
     * Returns null on any error (network, parse, etc).
     */
    fun fetchLatest(): UpdateInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FreeFCC-App")
            }

            if (conn.responseCode != 200) return null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").removePrefix("v")
            val name = json.optString("name", "v$tagName")
            val changelog = json.optString("body", "").trim()
            val publishedAt = json.optString("published_at", "")

            // Find the first APK asset
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var apkSize = 0L
            var sha256: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val nameField = asset.optString("name", "")
                if (nameField.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0)
                    // GitHub returns "sha256:<hex>" in the digest field.
                    sha256 = asset.optString("digest", "").removePrefix("sha256:").ifEmpty { null }
                    break
                }
            }

            if (apkUrl == null) return null

            UpdateInfo(
                version = tagName,
                title = name,
                changelog = changelog,
                downloadUrl = apkUrl,
                apkSize = apkSize,
                publishedAt = publishedAt,
                sha256 = sha256
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Pure — no I/O — combines the post-download gates so the reject logic
     * is unit-testable without a real file, network, or PackageManager.
     */
    internal fun evaluateDownloadGates(
        expectedSha256: String?,
        actualDigestHex: String,
        expectedSize: Long,
        actualSize: Long,
        authenticityMatch: Boolean
    ): Boolean {
        if (expectedSha256 == null) return false
        if (expectedSize <= 0 || actualSize != expectedSize) return false
        if (!actualDigestHex.equals(expectedSha256, ignoreCase = true)) return false
        return authenticityMatch
    }

    /**
     * Downloads the APK file to the app cache directory.
     * Calls onProgress with bytes downloaded / total bytes.
     * Fail-closed: rejects (deletes the partial file, returns null) on a
     * missing digest, a size mismatch, a redirect to a disallowed host, a
     * digest mismatch, or a package/signer mismatch against the installed app.
     */
    fun downloadApk(
        context: Context,
        info: UpdateInfo,
        authenticityChecker: ApkAuthenticityChecker = DefaultApkAuthenticityChecker,
        onProgress: (Float) -> Unit
    ): File? {
        val expectedSha256 = info.sha256 ?: return null // no digest, no install

        var conn: HttpURLConnection? = null
        var outputFile: File? = null
        return try {
            conn = openAllowlistedConnection(info.downloadUrl, connectTimeoutMs = 10000, readTimeoutMs = 30000)
                ?: return null

            val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
            val outputDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(outputDir, "freefcc_update.apk")
            outputFile = file
            val md = MessageDigest.getInstance("SHA-256")

            var downloaded = 0L
            FileOutputStream(file).use { fos ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                        md.update(buffer, 0, read)
                        downloaded += read
                        onProgress((downloaded.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }

            val actualDigestHex = md.digest().joinToString("") { "%02x".format(it) }
            // The signer check is the real trust anchor: package ID + signing
            // certs must match the installed app, so a compromised
            // release/CDN can't swap in an APK signed by a different key
            // even if it passes the digest check (which only proves transit
            // integrity against metadata from the same release).
            val authenticityMatch = authenticityChecker.matchesInstalledApp(context, file)

            if (!evaluateDownloadGates(expectedSha256, actualDigestHex, info.apkSize, downloaded, authenticityMatch)) {
                file.delete()
                return null
            }

            file
        } catch (_: Exception) {
            outputFile?.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }
}