package org.simplemediacentre.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.simplemediacentre.model.MediaRecord

class MovieQueryTest {
    private fun movie(
        uri: String,
        title: String,
        year: Int? = null,
        genres: List<String> = emptyList(),
        addedAt: Long = 0L,
        metadataId: Int? = null,
    ) = MediaRecord(
        uri = uri,
        fileName = title + ".mkv",
        title = title,
        kind = MediaRecord.Kind.MOVIE,
        year = year,
        genres = genres,
        addedAt = addedAt,
        metadataId = metadataId,
    )

    private val watched = setOf("watched")

    @Test
    fun filtersByWatchedState() {
        val items = listOf(
            movie("watched", "A"),
            movie("new", "B"),
        )

        assertEquals(
            listOf("B"),
            MovieQueryEngine.apply(
                items,
                MovieQuery(filter = MovieFilter.UNWATCHED),
                watched::contains,
            ).map { it.title },
        )
    }

    @Test
    fun filtersUnmatchedMetadata() {
        val items = listOf(
            movie("matched", "A", metadataId = 1),
            movie("unmatched", "B"),
        )

        assertEquals(
            listOf("B"),
            MovieQueryEngine.apply(
                items,
                MovieQuery(filter = MovieFilter.UNMATCHED),
                watched::contains,
            ).map { it.title },
        )
    }

    @Test
    fun filtersByDecadeAndGenre() {
        val items = listOf(
            movie("a", "Alien", 1979, listOf("Science Fiction")),
            movie("b", "Aliens", 1986, listOf("Science Fiction")),
            movie("c", "Heat", 1995, listOf("Crime")),
        )

        assertEquals(
            listOf("Aliens"),
            MovieQueryEngine.apply(
                items,
                MovieQuery(decade = 1980, genre = "Science Fiction"),
                watched::contains,
            ).map { it.title },
        )
    }

    @Test
    fun unknownYearFilterOnlyReturnsMissingYears() {
        val items = listOf(
            movie("known", "Known", 2000),
            movie("unknown", "Unknown"),
        )

        assertEquals(
            listOf("Unknown"),
            MovieQueryEngine.apply(
                items,
                MovieQuery(decade = MovieQueryEngine.UNKNOWN_YEAR),
                watched::contains,
            ).map { it.title },
        )
    }

    @Test
    fun recentSortUsesAddedTimestamp() {
        val items = listOf(
            movie("older", "Older", addedAt = 10L),
            movie("newer", "Newer", addedAt = 20L),
        )

        assertEquals(
            listOf("Newer", "Older"),
            MovieQueryEngine.apply(
                items,
                MovieQuery(sort = MovieSort.RECENT),
                watched::contains,
            ).map { it.title },
        )
    }

    @Test
    fun availableFiltersAreStableAndSorted() {
        val items = listOf(
            movie("a", "A", 1985, listOf("Thriller", "Crime")),
            movie("b", "B", 1979, listOf("Science Fiction", "Crime")),
        )

        assertEquals(listOf(1980, 1970), MovieQueryEngine.availableDecades(items))
        assertEquals(
            listOf("Crime", "Science Fiction", "Thriller"),
            MovieQueryEngine.availableGenres(items),
        )
    }
}
