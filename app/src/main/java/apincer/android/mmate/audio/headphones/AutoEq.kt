package apincer.android.mmate.audio.headphones

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One AutoEq result. [path] is relative to the results folder and already URL-encoded, as the index writes it. */
data class AutoEqEntry(val name: String, val source: String, val rig: String?, val path: String) {
    /** Shown as the imported profile's source. */
    val credit: String get() = "AutoEq, by $source" + (rig?.let { " on $it" } ?: "")

    // Decoding and re-encoding would turn '+' into a space, so the index's own encoding is used as is
    val profileUrl: String get() = "${AutoEqIndex.RESULTS_URL}/$path/${path.substringAfterLast('/')}%20ParametricEQ.txt"
}

/** AutoEq's results carry no data license, so the index is fetched on request and never bundled. */
object AutoEqIndex {
    const val RESULTS_URL = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
    const val INDEX_URL = "$RESULTS_URL/INDEX.md"

    fun parse(markdown: String): List<AutoEqEntry> = markdown.lineSequence().mapNotNull { entry(it.trim()) }.toList()

    // Names and paths can contain parentheses, so the path ends at the last ") by "
    private fun entry(line: String): AutoEqEntry? {
        if (!line.startsWith("- [")) return null
        val nameEnd = line.indexOf("](./")
        val pathEnd = line.lastIndexOf(") by ")
        if (nameEnd < 0 || pathEnd < nameEnd + 4) return null
        val name = line.substring(3, nameEnd)
        val path = line.substring(nameEnd + 4, pathEnd)
        val credit = line.substring(pathEnd + 5)
        val source = credit.substringBefore(" on ")
        if (name.isBlank() || source.isBlank() || path.isEmpty() || path.any { it == '#' || it == '?' || it.isWhitespace() }) return null
        return AutoEqEntry(name, source, if (" on " in credit) credit.substringAfter(" on ") else null, path)
    }

    fun search(entries: List<AutoEqEntry>, query: String, limit: Int = 50): List<AutoEqEntry> {
        val tokens = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val phrase = tokens.joinToString(" ")
        // oratory1990 is AutoEq's recommended source, so it leads among equally ranked results
        return entries.filter { entry -> entry.name.lowercase(Locale.ROOT).let { name -> tokens.all { it in name } } }
            .sortedWith(compareBy({ !it.name.lowercase(Locale.ROOT).startsWith(phrase) }, { it.name.length }, { it.source != "oratory1990" }))
            .take(limit)
    }
}

/** [get] is injectable so tests run without the network. */
class AutoEqClient(
    private val cacheFile: File,
    private val get: (url: String, maxBytes: Int) -> String = ::httpGet,
    private val now: () -> Long = System::currentTimeMillis
) {
    private var entries: List<AutoEqEntry>? = null

    @Synchronized
    fun entries(): List<AutoEqEntry> {
        entries?.let { return it }
        val fresh = cacheFile.exists() && now() - cacheFile.lastModified() < CACHE_MAX_AGE_MS
        val downloaded = if (fresh) null else try {
            // A Wi-Fi sign-in page answers 200 with HTML; caching it would hide every headphone for 30 days
            get(AutoEqIndex.INDEX_URL, INDEX_MAX_BYTES).also { if (AutoEqIndex.parse(it).isEmpty()) throw IOException("The AutoEq index lists no headphones") }
        } catch (failure: IOException) {
            if (!cacheFile.exists()) throw IOException("Could not reach AutoEq. Check the internet connection and try again.", failure)
            null
        }
        downloaded?.let { save(it) }
        return AutoEqIndex.parse(downloaded ?: cacheFile.readText()).also { entries = it }
    }

    fun profile(entry: AutoEqEntry): String = try {
        get(entry.profileUrl, HeadphoneProfileParser.MAX_BYTES)
    } catch (failure: IOException) {
        throw IOException("Could not download this profile. Try again, or import it from autoeq.app.", failure)
    }

    private fun save(text: String) {
        val temp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), cacheFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val INDEX_MAX_BYTES = 4 * 1024 * 1024
    }
}

// HttpURLConnection waits out the connect timeout on each unreachable IPv6 address (60 s on a home network
// that drops IPv6 to GitHub); OkHttp races IPv4 and IPv6 instead
private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build() }

internal fun httpGet(url: String, maxBytes: Int): String = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
    if (response.code != 200) throw IOException("HTTP ${response.code} for $url")
    val bytes = response.body.byteStream().readNBytes(maxBytes + 1)
    if (bytes.size > maxBytes) throw IOException("Response from $url is larger than $maxBytes bytes")
    String(bytes, Charsets.UTF_8)
}
