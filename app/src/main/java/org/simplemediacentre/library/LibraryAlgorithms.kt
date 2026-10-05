package org.simplemediacentre.library

import org.simplemediacentre.model.MediaRecord

object LibraryAlgorithms {
    fun tvShowKey(item: MediaRecord): String =
        item.metadataId?.let { "tmdb:" + it }
            ?: "title:" + item.title.lowercase()

    fun orderedEpisodes(items: Collection<MediaRecord>): List<MediaRecord> =
        items.sortedWith(
            compareBy<MediaRecord> { it.season ?: Int.MAX_VALUE }
                .thenBy { it.episode ?: Int.MAX_VALUE }
                .thenBy { it.fileName.lowercase() }
        )

    fun nextEpisode(
        items: Collection<MediaRecord>,
        current: MediaRecord,
    ): MediaRecord? {
        if (current.kind != MediaRecord.Kind.TV_EPISODE) return null
        val showKey = tvShowKey(current)
        val ordered = orderedEpisodes(
            items.filter {
                it.kind == MediaRecord.Kind.TV_EPISODE &&
                    tvShowKey(it) == showKey
            }
        )
        val index = ordered.indexOfFirst { it.uri == current.uri }
        if (index < 0 || index >= ordered.lastIndex) return null
        return ordered[index + 1]
    }

    fun continueItems(
        items: Collection<MediaRecord>,
        playbackPosition: (String) -> Long,
        isWatched: (String) -> Boolean,
        lastPlayed: (String) -> Long,
        matchesSearch: (MediaRecord) -> Boolean = { true },
    ): List<MediaRecord> =
        items
            .asSequence()
            .filter(matchesSearch)
            .filter { item ->
                PlaybackRules.canResume(
                    playbackPosition(item.uri),
                    isWatched(item.uri),
                )
            }
            .sortedWith(
                compareByDescending<MediaRecord> { lastPlayed(it.uri) }
                    .thenBy { it.displayTitle.lowercase() }
            )
            .toList()

    fun recentItems(
        items: Collection<MediaRecord>,
        limit: Int = 30,
        matchesSearch: (MediaRecord) -> Boolean = { true },
    ): List<MediaRecord> =
        items
            .asSequence()
            .filter { it.addedAt > 0L }
            .filter(matchesSearch)
            .sortedByDescending { it.addedAt }
            .take(limit)
            .toList()

    fun topFolder(relativePath: String?): String? =
        relativePath
            ?.trim('/')
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }

    fun matchesSearch(
        item: MediaRecord,
        rawQuery: String,
        sourceDisplayName: String? = null,
    ): Boolean {
        val query = rawQuery.trim().lowercase()
        if (query.isBlank()) return true

        return item.displayTitle.lowercase().contains(query) ||
            item.fileName.lowercase().contains(query) ||
            item.episodeTitle?.lowercase()?.contains(query) == true ||
            item.relativePath?.lowercase()?.contains(query) == true ||
            item.genres.any { it.lowercase().contains(query) } ||
            sourceDisplayName?.lowercase()?.contains(query) == true
    }
}
