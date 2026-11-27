package com.example.myweibo.data

import java.net.URI
import java.util.Locale

internal object ProfileCoverUrls {
    // These are the same four sinaimg CDN hosts used for PID image URLs by the parser.
    private val cdnHosts = (1..4).map { "wx$it.sinaimg.cn" }.toSet()
    private val separator = Regex(""";\s*(?=(?:https?:)?//)""", RegexOption.IGNORE_CASE)
    private val imageExtension = Regex("""\.(?:jpe?g|png|gif|webp)$""", RegexOption.IGNORE_CASE)
    private val renditionPath = Regex(
        """/(?:crop\.\d+\.\d+\.\d+\.\d+|mw2000|woriginal|large|orj960|bmiddle)/([A-Za-z0-9_-]+\.(?:jpe?g|png|gif|webp))""",
        RegexOption.IGNORE_CASE,
    )

    fun parseField(value: String): List<String> = normalize(value.split(separator).flatMap { url ->
        if ('?' in url || '#' in url) listOf(url) else url.split(';')
    })

    fun isAllowed(url: String): Boolean = normalizeUrl(url) == url

    fun normalize(urls: List<String>): List<String> = urls.mapNotNull(::normalizeUrl).distinct()

    private fun normalizeUrl(value: String): String? {
        val trimmed = value.trim()
        val url = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://", ignoreCase = true) -> "https:${trimmed.substringAfter(':')}"
            else -> trimmed
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || host !in cdnHosts ||
            !uri.rawAuthority.equals(host, ignoreCase = true)
        ) return null
        if (uri.rawPath?.let(imageExtension::containsMatchIn) != true) return null
        return url
    }

    fun downloadCandidates(url: String): List<String> {
        val original = normalizeUrl(url) ?: return emptyList()
        val uri = URI(original)
        // A query may sign the exact path. Never rewrite it or try a queryless version.
        if (uri.rawQuery != null || uri.rawFragment != null) return listOf(original)
        val fileName = renditionPath.matchEntire(uri.rawPath)?.groupValues?.get(1)
            ?: return listOf(original)
        return (listOf(original) + listOf("mw2000", "large").map { "https://${uri.host}/$it/$fileName" })
            .distinct()
    }
}
