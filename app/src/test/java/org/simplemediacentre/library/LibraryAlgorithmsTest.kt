package org.simplemediacentre.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.simplemediacentre.model.MediaRecord

class LibraryAlgorithmsTest {
    private fun episode(
        uri: String,
        title: String = "Show",
        season: Int? = 1,
        number: Int? = 1,
        metadataId: Int? = null,
        fileName: String = uri.substringAfterLast('/'),
    ) = MediaRecord(
        uri = uri,
        fileName = fileName,
        title = title,
        kind = MediaRecord.Kind.TV_EPISODE,
        season = season,
        episode = number,
        metadataId = metadataId,
    )

    @Test
    fun tvShowKeyPrefersMetadataId() {
        assertEquals(
            "tmdb:123",
            LibraryAlgorithms.tvShowKey(episode("u1", metadataId = 123)),
        )
    }

    @Test
    fun tvShowKeyFallsBackToCaseInsensitiveTitle() {
        assertEquals(
            "title:the office",
            LibraryAlgorithms.tvShowKey(episode("u1", title = "The Office")),
        )
    }

    @Test
    fun nextEpisodeFollowsSeasonAndEpisodeOrder() {
        val current = episode("u1", season = 1, number = 2)
        val next = episode("u2", season = 2, number = 1)
        val earlier = episode("u0", season = 1, number = 1)

        assertEquals(
            next,
            LibraryAlgorithms.nextEpisode(listOf(next, current, earlier), current),
        )
    }

    @Test
    fun nextEpisodeDoesNotCrossShows() {
        val current = episode("u1", title = "Show A", season = 1, number = 1)
        val other = episode("u2", title = "Show B", season = 1, number = 2)

        assertNull(LibraryAlgorithms.nextEpisode(listOf(current, other), current))
    }

    @Test
    fun topFolderReturnsFirstPathSegment() {
        assertEquals("Family", LibraryAlgorithms.topFolder("/Family/Holidays/"))
        assertNull(LibraryAlgorithms.topFolder(null))
    }

    @Test
    fun searchMatchesGenresFoldersAndFriendlySource() {
        val item = MediaRecord(
            uri = "content://movie",
            fileName = "Alien.1979.mkv",
            title = "Alien",
            kind = MediaRecord.Kind.MOVIE,
            relativePath = "Movies/Sci-Fi",
            genres = listOf("Science Fiction", "Horror"),
        )

        assertTrue(LibraryAlgorithms.matchesSearch(item, "alien"))
        assertTrue(LibraryAlgorithms.matchesSearch(item, "sci-fi"))
        assertTrue(LibraryAlgorithms.matchesSearch(item, "horror"))
        assertTrue(LibraryAlgorithms.matchesSearch(item, "family drive", "Family Drive"))
        assertFalse(LibraryAlgorithms.matchesSearch(item, "romance"))
    }
}
