package org.simplemediacentre.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.simplemediacentre.model.MediaRecord
import org.simplemediacentre.model.SourceType

class LibraryStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("simple_media_centre", Context.MODE_PRIVATE)

    fun roots(): Set<String> =
        preferences.getStringSet(KEY_ROOTS, emptySet())?.toSet().orEmpty()

    fun addRoot(uri: String) {
        val updated = roots().toMutableSet().apply { add(uri) }
        preferences.edit().putStringSet(KEY_ROOTS, updated).apply()
    }

    fun removeRoot(uri: String) {
        val updated = roots().toMutableSet().apply { remove(uri) }
        preferences.edit()
            .putStringSet(KEY_ROOTS, updated)
            .remove(KEY_SOURCE_TYPE_PREFIX + uri)
            .apply()
    }

    fun mediaStoreVolumes(): Set<String> =
        preferences.getStringSet(KEY_MEDIASTORE_VOLUMES, emptySet())?.toSet().orEmpty()

    fun addMediaStoreVolume(volumeName: String) {
        val updated = mediaStoreVolumes().toMutableSet().apply { add(volumeName) }
        preferences.edit().putStringSet(KEY_MEDIASTORE_VOLUMES, updated).apply()
    }

    fun removeMediaStoreVolume(volumeName: String) {
        val updated = mediaStoreVolumes().toMutableSet().apply { remove(volumeName) }
        val editor = preferences.edit()
            .putStringSet(KEY_MEDIASTORE_VOLUMES, updated)
            .remove(KEY_SOURCE_TYPE_PREFIX + volumeName)
            .remove(KEY_MEDIASTORE_FOLDERS_PREFIX + volumeName)
        mediaStoreFolders(volumeName).forEach { folder ->
            editor.remove(KEY_SOURCE_TYPE_PREFIX + mediaStoreFolderSourceId(volumeName, folder))
        }
        editor.apply()
    }

    fun mediaStoreFolders(volumeName: String): Set<String> =
        preferences.getStringSet(KEY_MEDIASTORE_FOLDERS_PREFIX + volumeName, emptySet())
            ?.mapNotNull(::normalizeFolderPath)
            ?.toSet()
            .orEmpty()

    fun setMediaStoreFolders(volumeName: String, folders: Collection<String>) {
        val normalized = folders.mapNotNull(::normalizeFolderPath).toSet()
        preferences.edit()
            .putStringSet(KEY_MEDIASTORE_FOLDERS_PREFIX + volumeName, normalized)
            .apply()
    }

    fun mediaStoreFolderSourceId(volumeName: String, folder: String): String =
        volumeName + "|" + normalizeFolderPath(folder).orEmpty()

    private fun normalizeFolderPath(path: String): String? =
        path.trim().trim('/').takeIf { it.isNotBlank() }

    fun sourceType(sourceId: String): SourceType {
        val raw = preferences.getString(KEY_SOURCE_TYPE_PREFIX + sourceId, null)
        return runCatching { raw?.let(SourceType::valueOf) }.getOrNull() ?: SourceType.MIXED
    }

    fun setSourceType(sourceId: String, type: SourceType) {
        preferences.edit()
            .putString(KEY_SOURCE_TYPE_PREFIX + sourceId, type.name)
            .apply()
    }

    fun tmdbToken(): String =
        preferences.getString(KEY_TMDB_TOKEN, "").orEmpty()

    fun saveTmdbToken(token: String) {
        preferences.edit().putString(KEY_TMDB_TOKEN, token.trim()).apply()
    }

    fun isWatched(uri: String): Boolean =
        preferences.getBoolean(KEY_WATCHED_PREFIX + uri, false)

    fun setWatched(uri: String, watched: Boolean) {
        preferences.edit()
            .putBoolean(KEY_WATCHED_PREFIX + uri, watched)
            .apply()

        if (watched) {
            savePlaybackPosition(uri, 0L)
        }
    }

    fun loadLibrary(): List<MediaRecord> {
        val raw = preferences.getString(KEY_LIBRARY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        MediaRecord(
                            uri = item.getString("uri"),
                            fileName = item.getString("fileName"),
                            title = item.getString("title"),
                            kind = MediaRecord.Kind.valueOf(item.getString("kind")),
                            sourceId = item.optStringOrNull("sourceId"),
                            relativePath = item.optStringOrNull("relativePath"),
                            year = item.optIntOrNull("year"),
                            season = item.optIntOrNull("season"),
                            episode = item.optIntOrNull("episode"),
                            modifiedAt = item.optLong("modifiedAt", 0L),
                            addedAt = item.optLong("addedAt", 0L),
                            metadataId = item.optIntOrNull("metadataId"),
                            metadataTitle = item.optStringOrNull("metadataTitle"),
                            overview = item.optStringOrNull("overview"),
                            posterPath = item.optStringOrNull("posterPath"),
                            backdropPath = item.optStringOrNull("backdropPath"),
                            episodeMetadataId = item.optIntOrNull("episodeMetadataId"),
                            episodeTitle = item.optStringOrNull("episodeTitle"),
                            episodeOverview = item.optStringOrNull("episodeOverview"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveLibrary(items: List<MediaRecord>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("uri", item.uri)
                    .put("fileName", item.fileName)
                    .put("title", item.title)
                    .put("kind", item.kind.name)
                    .putNullable("sourceId", item.sourceId)
                    .putNullable("relativePath", item.relativePath)
                    .putNullable("year", item.year)
                    .putNullable("season", item.season)
                    .putNullable("episode", item.episode)
                    .put("modifiedAt", item.modifiedAt)
                    .put("addedAt", item.addedAt)
                    .putNullable("metadataId", item.metadataId)
                    .putNullable("metadataTitle", item.metadataTitle)
                    .putNullable("overview", item.overview)
                    .putNullable("posterPath", item.posterPath)
                    .putNullable("backdropPath", item.backdropPath)
                    .putNullable("episodeMetadataId", item.episodeMetadataId)
                    .putNullable("episodeTitle", item.episodeTitle)
                    .putNullable("episodeOverview", item.episodeOverview)
            )
        }
        preferences.edit().putString(KEY_LIBRARY, array.toString()).apply()
    }

    fun playbackPosition(uri: String): Long =
        preferences.getLong(KEY_POSITION_PREFIX + uri, 0L)

    fun savePlaybackPosition(uri: String, positionMs: Long) {
        preferences.edit()
            .putLong(KEY_POSITION_PREFIX + uri, positionMs.coerceAtLeast(0L))
            .apply()
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private companion object {
        const val KEY_ROOTS = "roots"
        const val KEY_LIBRARY = "library"
        const val KEY_TMDB_TOKEN = "tmdb_token"
        const val KEY_MEDIASTORE_VOLUMES = "mediastore_volumes"
        const val KEY_POSITION_PREFIX = "position:"
        const val KEY_WATCHED_PREFIX = "watched:"
        const val KEY_SOURCE_TYPE_PREFIX = "source_type:"
        const val KEY_MEDIASTORE_FOLDERS_PREFIX = "mediastore_folders:"
    }
}
