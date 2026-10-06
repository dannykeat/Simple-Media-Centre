package org.simplemediacentre.library

import org.simplemediacentre.model.MediaRecord

data class LibraryMetrics(
    val movies: Int,
    val tvShows: Int,
    val tvEpisodes: Int,
    val videos: Int,
    val metadataMatched: Int,
)

object LibraryMetricsCalculator {
    fun calculate(items: Collection<MediaRecord>): LibraryMetrics {
        val tvItems = items.filter { it.kind == MediaRecord.Kind.TV_EPISODE }
        return LibraryMetrics(
            movies = items.count { it.kind == MediaRecord.Kind.MOVIE },
            tvShows = tvItems.groupBy(LibraryAlgorithms::tvShowKey).size,
            tvEpisodes = tvItems.size,
            videos = items.count {
                it.kind == MediaRecord.Kind.VIDEO || it.kind == MediaRecord.Kind.UNKNOWN
            },
            metadataMatched = items.count { it.metadataId != null },
        )
    }
}
