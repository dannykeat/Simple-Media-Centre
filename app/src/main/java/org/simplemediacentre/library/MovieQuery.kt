package org.simplemediacentre.library

import org.simplemediacentre.model.MediaRecord

enum class MovieSort {
    TITLE,
    RECENT,
    YEAR,
    UNWATCHED,
}

enum class MovieFilter {
    ALL,
    UNWATCHED,
    WATCHED,
    UNMATCHED,
}

data class MovieQuery(
    val sort: MovieSort = MovieSort.TITLE,
    val filter: MovieFilter = MovieFilter.ALL,
    val decade: Int? = null,
    val genre: String? = null,
)

object MovieQueryEngine {
    const val UNKNOWN_YEAR = Int.MIN_VALUE

    fun apply(
        items: Collection<MediaRecord>,
        query: MovieQuery,
        isWatched: (String) -> Boolean,
        matchesSearch: (MediaRecord) -> Boolean = { true },
    ): List<MediaRecord> {
        val matching = items
            .asSequence()
            .filter { it.kind == MediaRecord.Kind.MOVIE }
            .filter(matchesSearch)
            .filter { item ->
                when (query.filter) {
                    MovieFilter.ALL -> true
                    MovieFilter.UNWATCHED -> !isWatched(item.uri)
                    MovieFilter.WATCHED -> isWatched(item.uri)
                    MovieFilter.UNMATCHED -> item.metadataId == null
                }
            }
            .filter { item ->
                when (val decade = query.decade) {
                    null -> true
                    UNKNOWN_YEAR -> item.year == null
                    else -> item.year?.let { (it / 10) * 10 == decade } == true
                }
            }
            .filter { item ->
                query.genre?.let { genre -> genre in item.genres } ?: true
            }
            .toList()

        return when (query.sort) {
            MovieSort.TITLE -> matching.sortedBy { it.displayTitle.lowercase() }
            MovieSort.RECENT -> matching.sortedByDescending { it.addedAt }
            MovieSort.YEAR -> matching.sortedWith(
                compareByDescending<MediaRecord> { it.year ?: Int.MIN_VALUE }
                    .thenBy { it.displayTitle.lowercase() }
            )
            MovieSort.UNWATCHED -> matching.sortedWith(
                compareBy<MediaRecord> { isWatched(it.uri) }
                    .thenBy { it.displayTitle.lowercase() }
            )
        }
    }

    fun availableDecades(items: Collection<MediaRecord>): List<Int> =
        items
            .asSequence()
            .filter { it.kind == MediaRecord.Kind.MOVIE }
            .mapNotNull { it.year }
            .map { (it / 10) * 10 }
            .distinct()
            .sortedDescending()
            .toList()

    fun availableGenres(items: Collection<MediaRecord>): List<String> =
        items
            .asSequence()
            .filter { it.kind == MediaRecord.Kind.MOVIE }
            .flatMap { it.genres.asSequence() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
}
