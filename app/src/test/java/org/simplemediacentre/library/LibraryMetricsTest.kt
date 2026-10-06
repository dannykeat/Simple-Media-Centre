package org.simplemediacentre.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.simplemediacentre.model.MediaRecord

class LibraryMetricsTest {
    @Test
    fun countsMoviesShowsEpisodesVideosAndMatches() {
        val items = listOf(
            MediaRecord(
                uri = "movie",
                fileName = "Movie.mkv",
                title = "Movie",
                kind = MediaRecord.Kind.MOVIE,
                metadataId = 1,
            ),
            MediaRecord(
                uri = "show1e1",
                fileName = "Show.S01E01.mkv",
                title = "Show",
                kind = MediaRecord.Kind.TV_EPISODE,
                season = 1,
                episode = 1,
                metadataId = 2,
            ),
            MediaRecord(
                uri = "show1e2",
                fileName = "Show.S01E02.mkv",
                title = "Show",
                kind = MediaRecord.Kind.TV_EPISODE,
                season = 1,
                episode = 2,
                metadataId = 2,
            ),
            MediaRecord(
                uri = "video",
                fileName = "Family.mp4",
                title = "Family",
                kind = MediaRecord.Kind.VIDEO,
            ),
        )

        val metrics = LibraryMetricsCalculator.calculate(items)

        assertEquals(1, metrics.movies)
        assertEquals(1, metrics.tvShows)
        assertEquals(2, metrics.tvEpisodes)
        assertEquals(1, metrics.videos)
        assertEquals(3, metrics.metadataMatched)
    }
}
