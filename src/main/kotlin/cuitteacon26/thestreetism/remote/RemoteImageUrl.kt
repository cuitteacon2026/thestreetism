package cuitteacon26.thestreetism.remote

import java.net.URI
import java.util.Locale

/**
 * Shared normalisation and validation for remote graffiti image links.
 *
 * Lives outside the client package so both the command (server side) and the
 * texture loader (client side) agree on exactly which links are accepted.
 */
object RemoteImageUrl {

    const val MAX_URL_LENGTH = 1024

    sealed interface Result {
        data class Valid(val url: String) : Result
        data class Invalid(val translationKey: String) : Result
    }

    /**
     * Clean up user input and rewrite well-known "page" links into direct image
     * links. Returns the canonical URL to store on the spray can.
     */
    fun normalize(raw: String): Result {
        val trimmed = raw.trim().trim('<', '>', '"', '\'', '`')
        if (trimmed.isEmpty()) return Result.Invalid("thestreetism.spray.url.empty")
        if (trimmed.length > MAX_URL_LENGTH) return Result.Invalid("thestreetism.spray.url.too_long")

        // Accept scheme-less input such as "i.imgur.com/abc.png".
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"

        val uri = runCatching { URI(withScheme) }.getOrNull()
            ?: return Result.Invalid("thestreetism.spray.url.malformed")

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return Result.Invalid("thestreetism.spray.url.scheme")
        }
        if (uri.host.isNullOrBlank()) {
            return Result.Invalid("thestreetism.spray.url.host")
        }

        return Result.Valid(rewriteKnownHosts(uri))
    }

    /** True when [raw] normalises cleanly. */
    fun isAcceptable(raw: String): Boolean = normalize(raw) is Result.Valid

    private fun rewriteKnownHosts(uri: URI): String {
        val host = uri.host.lowercase(Locale.ROOT).removePrefix("www.")
        val path = uri.path?.trim('/') ?: ""
        val segments = if (path.isEmpty()) emptyList() else path.split('/')

        return when {
            // github.com/<owner>/<repo>/blob/<ref>/<path> -> raw.githubusercontent.com
            host == "github.com" && segments.size >= 5 && segments[2] == "blob" -> {
                val owner = segments[0]
                val repo = segments[1]
                val rest = segments.drop(3).joinToString("/")
                "https://raw.githubusercontent.com/$owner/$repo/$rest"
            }

            // imgur page/gallery link -> direct i.imgur.com asset
            host == "imgur.com" && segments.isNotEmpty() -> {
                val id = segments.last().substringBefore('.')
                if (id.isEmpty()) uri.toString() else "https://i.imgur.com/$id.png"
            }

            // Dropbox shares need raw=1 to return the file itself.
            host.endsWith("dropbox.com") -> replaceQuery(uri, "raw=1")

            // drive.google.com/file/d/<id>/view -> direct download endpoint
            host == "drive.google.com" && segments.size >= 3 && segments[0] == "file" -> {
                "https://drive.google.com/uc?export=download&id=${segments[2]}"
            }

            else -> uri.toString()
        }
    }

    private fun replaceQuery(uri: URI, query: String): String {
        val base = StringBuilder()
        base.append(uri.scheme).append("://").append(uri.host)
        if (uri.port != -1) base.append(':').append(uri.port)
        base.append(uri.rawPath ?: "")
        base.append('?').append(query)
        return base.toString()
    }
}
