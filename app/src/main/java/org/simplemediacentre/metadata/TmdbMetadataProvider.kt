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
    override fun match(item: MediaRecord): MediaMetadata? {
        if (bearerToken.isBlank() || item.kind == MediaRecord.Kind.UNKNOWN) return null

        val endpoint = when (item.kind) {
            MediaRecord.Kind.MOVIE -> "movie"
            MediaRecord.Kind.TV_EPISODE -> "tv"
            MediaRecord.Kind.UNKNOWN -> return null
        }

        val query = URLEncoder.encode(item.title, StandardCharsets.UTF_8.name())
        val yearParameter = when {
            item.year == null -> ""
            item.kind == MediaRecord.Kind.MOVIE -> "&year=" + item.year
            else -> "&first_air_date_year=" + item.year
        }

        val url = URL(
            "https://api.themoviedb.org/3/search/" + endpoint +
                "?query=" + query +
                "&include_adult=false&language=en-AU&page=1" +
                yearParameter
        )

        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken)
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode !in 200..299) return null

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseFirstResult(JSONObject(response), item.kind)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFirstResult(
        response: JSONObject,
        kind: MediaRecord.Kind,
    ): MediaMetadata? {
        val results = response.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        val result = results.optJSONObject(0) ?: return null
        val titleKey = if (kind == MediaRecord.Kind.TV_EPISODE) "name" else "title"
        val title = result.optString(titleKey).takeIf { it.isNotBlank() } ?: return null

        return MediaMetadata(
            id = result.optInt("id"),
            title = title,
            overview = result.optNullableString("overview"),
            posterPath = result.optNullableString("poster_path"),
            backdropPath = result.optNullableString("backdrop_path"),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
