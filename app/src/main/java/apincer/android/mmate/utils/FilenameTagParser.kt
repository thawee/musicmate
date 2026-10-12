package apincer.android.mmate.utils

import java.util.regex.Pattern

data class ParsedTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val track: String? = null,
    val year: String? = null
) {
    val isEmpty: Boolean
        get() = title.isNullOrBlank() && artist.isNullOrBlank() &&
                album.isNullOrBlank() && albumArtist.isNullOrBlank() &&
                track.isNullOrBlank() && year.isNullOrBlank()
}

/**
 * Utility to parse audio metadata tags from audio file names.
 * Supports standard presets (%artist% - %title%, %track% - %title%, etc.)
 * as well as custom token-based formatting patterns.
 */
object FilenameTagParser {

    val PRESET_PATTERNS = listOf(
        "%artist%/%album%/%track% - %title%",
        "%artist%/%album%/%track%. %title%",
        "%artist%/%album%/%track% - %artist% - %title%",
        "%artist%/%album%/%artist% - %title%",
        "%artist%/%album%/%title%",
        "%track% - %title%",
        "%artist% - %title%",
        "%track% - %artist% - %title%",
        "%artist% - %album% - %track% - %title%",
        "%track%. %artist% - %title%",
        "%track%. %title%",
        "%title%"
    )

    /**
     * Picks the preset for [paths]: among presets that parse at least half of them, the one that
     * extracts the most tags, then the one that parses the most paths, then the earlier preset.
     * Falls back to the first filename-only preset.
     */
    fun detectPattern(paths: List<String>): String {
        return PRESET_PATTERNS
            .map { preset -> Triple(preset, paths.count { parse(it, preset)?.isEmpty == false }, tokenCount(preset)) }
            .filter { it.second > 0 && it.second * 2 >= paths.size }
            .maxWithOrNull(compareBy<Triple<String, Int, Int>>({ it.third }, { it.second })
                .thenByDescending { PRESET_PATTERNS.indexOf(it.first) })
            ?.first
            ?: PRESET_PATTERNS.first { !it.contains('/') }
    }

    private fun tokenCount(pattern: String): Int =
        TOKEN_MAP.keys.sumOf { token -> pattern.windowed(token.length).count { it == token } }

    val AVAILABLE_TOKENS = listOf(
        "%track%",
        "%title%",
        "%artist%",
        "%album%",
        "%year%",
        "%albumartist%"
    )

    private val TOKEN_MAP = mapOf(
        "%title%" to "title",
        "%artist%" to "artist",
        "%album%" to "album",
        "%albumartist%" to "albumartist",
        "%track%" to "track",
        "%year%" to "year"
    )

    /**
     * Parses the filename using the provided pattern.
     * @param filePathOrName full file path or filename (with or without extension)
     * @param pattern token pattern like "%artist% - %title%"
     */
    fun parse(filePathOrName: String, pattern: String): ParsedTags? {
        if (pattern.isBlank()) return null
        val cleanName = sourceName(filePathOrName, pattern.count { it == '/' }) ?: return null

        val (regexPattern, tokenList) = compilePatternToRegex(pattern) ?: return null
        val matcher = regexPattern.matcher(cleanName)
        if (!matcher.matches()) return null

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var track: String? = null
        var year: String? = null

        for (i in tokenList.indices) {
            val token = tokenList[i]
            val value = matcher.group(i + 1)?.trim() ?: continue
            when (token) {
                "title" -> title = value
                "artist" -> artist = value
                "album" -> album = value
                "albumartist" -> albumArtist = value
                "track" -> track = cleanTrackNumber(value)
                "year" -> year = value
            }
        }

        return ParsedTags(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            track = track,
            year = year
        )
    }

    /**
     * Returns the filename without extension, prefixed by up to [parentLevels] parent folders
     * joined with '/', or null when the path has fewer folders than requested.
     */
    fun sourceName(filePathOrName: String, parentLevels: Int): String? {
        val segments = filePathOrName.split('/').filter { it.isNotEmpty() }
        if (segments.size <= parentLevels) return null
        val fileName = segments.last()
        val cleanName = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        if (cleanName.isBlank()) return null
        return (segments.dropLast(1).takeLast(parentLevels) + cleanName).joinToString("/")
    }

    private fun cleanTrackNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotEmpty()) digits else raw
    }

    private fun compilePatternToRegex(pattern: String): Pair<Pattern, List<String>>? {
        val tokenList = mutableListOf<String>()
        val regexBuilder = StringBuilder("^")
        var i = 0

        while (i < pattern.length) {
            val matchedToken = TOKEN_MAP.keys.firstOrNull { pattern.startsWith(it, i) }
            if (matchedToken != null) {
                val groupName = TOKEN_MAP[matchedToken]!!
                tokenList.add(groupName)
                val isLast = (i + matchedToken.length >= pattern.length)
                if (groupName == "track") {
                    regexBuilder.append("(\\d+)")
                } else if (isLast) {
                    regexBuilder.append("([^/]+)")
                } else {
                    regexBuilder.append("([^/]+?)")
                }
                i += matchedToken.length
            } else {
                val ch = pattern[i]
                if (ch == '-') {
                    regexBuilder.append("\\s*-\\s*")
                } else if (ch == '.') {
                    regexBuilder.append("\\.\\s*")
                } else if (ch == '_') {
                    regexBuilder.append("[_\\s]+")
                } else if ("\\^$|?*+()[{".contains(ch)) {
                    regexBuilder.append("\\").append(ch)
                } else if (ch == ' ') {
                    regexBuilder.append("\\s+")
                } else {
                    regexBuilder.append(ch)
                }
                i++
            }
        }
        regexBuilder.append("$")

        return try {
            Pair(Pattern.compile(regexBuilder.toString(), Pattern.CASE_INSENSITIVE), tokenList)
        } catch (e: Exception) {
            null
        }
    }
}
