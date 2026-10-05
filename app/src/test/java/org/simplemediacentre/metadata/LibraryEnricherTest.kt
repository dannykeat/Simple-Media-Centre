package org.simplemediacentre.metadata

import org.junit.Assert.assertEquals
import org.junit.Test
import org.simplemediacentre.model.MediaRecord

class LibraryEnricherTest {
    private class FakeProvider : MetadataProvider {
        var matchCalls = 0
        var episodeCalls = 0

        override fun match(item: MediaRecord): MediaMetadata? {
            matchCalls += 1
            return MediaMetadata(
                id = 42,
                title = item.title,
                overview = null,
                posterPath = null,
                backdropPath = null,
            )
        }

        override fun search(item: MediaRecord, limit: Int): List<MediaMetadata> =
            emptyList()

        override fun episodeDetails(
            seriesId: Int,
            season: Int,
            episode: Int,
        ): EpisodeMetadata? {
            episodeCalls += 1
            return EpisodeMetadata(
                id = season * 100 + episode,
                title = "Episode " + episode,
                overview = null,
            )
        }
    }

    @Test
    fun tvEpisodesReuseOneSeriesMatch() {
        val provider = FakeProvider()
        val enricher = LibraryEnricher(provider)

        val episodes = listOf(
            MediaRecord(
                uri = "e1",
                fileName = "Show.S01E01.mkv",
                title = "Show",
                kind = MediaRecord.Kind.TV_EPISODE,
                season = 1,
                episode = 1,
            ),
            MediaRecord(
                uri = "e2",
                fileName = "Show.S01E02.mkv",
                title = "Show",
                kind = MediaRecord.Kind.TV_EPISODE,
                season = 1,
                episode = 2,
            ),
        )

        val enriched = enricher.enrich(episodes)

        assertEquals(1, provider.matchCalls)
        assertEquals(2, provider.episodeCalls)
        assertEquals(listOf(42, 42), enriched.map { it.metadataId })
    }

    @Test
    fun cachePersistsAcrossBatches() {
        val provider = FakeProvider()
        val enricher = LibraryEnricher(provider)

        fun episode(number: Int) = MediaRecord(
            uri = "e" + number,
            fileName = "Show.S01E0" + number + ".mkv",
            title = "Show",
            kind = MediaRecord.Kind.TV_EPISODE,
            season = 1,
            episode = number,
        )

        enricher.enrich(listOf(episode(1)))
        enricher.enrich(listOf(episode(2)))

        assertEquals(1, provider.matchCalls)
    }
}
