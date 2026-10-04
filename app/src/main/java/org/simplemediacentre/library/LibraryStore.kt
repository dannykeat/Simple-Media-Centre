package org.simplemediacentre.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.simplemediacentre.model.MediaRecord

class LibraryStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("simple_media_centre", Context.MODE_PRIVATE)

    fun roots(): Set<String> =
        preferences.getStringSet(KEY_ROOTS, emptySet())?.toSet().orEmpty()

    fun addRoot(uri: String) {
        val updated = roots().toMutableSet().apply { add(uri) }
        preferences.edit().putStringSet(KEY_ROOTS, updated).apply()
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
                            season = item.optIntOrNull("season"),
                            episode = item.optIntOrNull("episode"),
                            modifiedAt = item.optLong("modifiedAt", 0L),
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
                    .put("season", item.season ?: JSONObject.NULL)
                    .put("episode", item.episode ?: JSONObject.NULL)
                    .put("modifiedAt", item.modifiedAt)
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

    private companion object {
        const val KEY_ROOTS = "roots"
        const val KEY_LIBRARY = "library"
        const val KEY_POSITION_PREFIX = "position:"
    }
}
