package org.simplemediacentre.library

import org.simplemediacentre.model.MediaRecord
import java.util.Locale

object FilenameParser {
    private val episodePattern = Regex(
        """(?i)^(.*?)[ ._\-]+s(\d{1,2})e(\d{1,3})(?:[ ._\-]+|$).*"""
    )
    private val yearPattern = Regex("""^(.*?)[ ._\-]*[\[(](19\d{2}|20\d{2})[\])]""")
    private val trailingNoise = Regex(
        """(?i)[ ._\-]+(?:2160p|1080p|720p|480p|uhd|bluray|blu-ray|brrip|webrip|web-dl|hdtv|dvdrip|x26[45]|h26[45]|hevc|av1|hdr|remux).*$"""
    )

    data class Parsed(
        val title: String,
        val kind: MediaRecord.Kind,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
    )

    fun parse(fileName: String): Parsed {
        val stem = fileName.substringBeforeLast('.', fileName).trim()

        episodePattern.matchEntire(stem)?.let { match ->
            val title = cleanTitle(match.groupValues[1])
            return Parsed(
                title = title.ifBlank { stem },
                kind = MediaRecord.Kind.TV_EPISODE,
                season = match.groupValues[2].toInt(),
                episode = match.groupValues[3].toInt(),
            )
        }

        val withoutNoise = stem.replace(trailingNoise, "")
        val movieMatch = yearPattern.find(withoutNoise)
        val title = cleanTitle(movieMatch?.groupValues?.get(1) ?: withoutNoise)

        return Parsed(
            title = title.ifBlank { stem },
            kind = if (title.isBlank()) MediaRecord.Kind.UNKNOWN else MediaRecord.Kind.MOVIE,
            year = movieMatch?.groupValues?.get(2)?.toIntOrNull(),
        )
    }

    private fun cleanTitle(value: String): String =
        value
            .replace('.', ' ')
            .replace('_', ' ')
            .replace(Regex("""\s+-\s+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '.', '_')
            .split(' ')
            .joinToString(" ") { token ->
                if (token.any(Char::isUpperCase)) token
                else token.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
}
