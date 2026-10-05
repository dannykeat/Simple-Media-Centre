package org.simplemediacentre.metadata

import org.json.JSONObject
import org.simplemediacentre.model.MediaRecord
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TmdbMetadataProvider(
    private val bearerToken: String,
) : MetadataProvider {
    override fun match(item: MediaRecord): MediaMetadata? =
        search(item, limit = 1).firstOrNull()

    override fun search(item: MediaRecord, limit: Int): List<MediaMetadata> {
        if (bearerToken.isBlank() || item.kind == MediaRecord.Kind.UNKNOWN || item.kind == MediaRecord.Kind.VIDEO || limit <= 0) {
            return emptyList()
        }

        val endpoint = when (item.kind) {
            MediaRecord.Kind.MOVIE -> "movie"
            MediaRecord.Kind.TV_EPISODE -> "tv"
            MediaRecord.Kind.VIDEO,
            MediaRecord.Kind.UNKNOWN -> return emptyList()
        }

        val query = URLEncoder.encode(item.title, StandardCharsets.UTF_8.name())
        val yearParameter = when {
            item.year == null -> ""
            item.kind == MediaRecord.Kind.MOVIE -> "&year=" + item.year
            else -> "&first_air_date_year=" + item.year
        }

        val response = getJson(
            "https://api.themoviedb.org/3/search/" + endpoint +
                "?query=" + query +
                "&include_adult=false&language=en-AU&page=1" +
                yearParameter
        ) ?: return emptyList()

        val results = response.optJSONArray("results") ?: return emptyList()
        val titleKey = if (item.kind == MediaRecord.Kind.TV_EPISODE) "name" else "title"

        return buildList {
            for (index in 0 until minOf(results.length(), limit)) {
                val result = results.optJSONObject(index) ?: continue
                val title = result.optString(titleKey).takeIf { it.isNotBlank() } ?: continue
                val id = result.optInt("id")
                if (id <= 0) continue

                add(
                    MediaMetadata(
                        id = id,
                        title = title,
                        overview = result.optNullableString("overview"),
                        posterPath = result.optNullableString("poster_path"),
                        backdropPath = result.optNullableString("backdrop_path"),
                    )
                )
            }
        }
    }

    override fun episodeDetails(
        seriesId: Int,
        season: Int,
        episode: Int,
    ): EpisodeMetadata? {
        if (bearerToken.isBlank() || seriesId <= 0 || season < 0 || episode <= 0) return null

        val result = getJson(
            "https://api.themoviedb.org/3/tv/" + seriesId +
                "/season/" + season +
                "/episode/" + episode +
                "?language=en-AU"
        ) ?: return null

        val id = result.optInt("id")
        val title = result.optString("name").takeIf { it.isNotBlank() } ?: return null
        if (id <= 0) return null

        return EpisodeMetadata(
            id = id,
            title = title,
            overview = result.optNullableString("overview"),
        )
    }

    private fun getJson(rawUrl: String): JSONObject? {
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken)
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode !in 200..299) return null

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
