package org.simplemediacentre.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.simplemediacentre.model.MediaRecord

class FilenameParserTest {
    @Test
    fun parsesTvEpisode() {
        val parsed = FilenameParser.parse("Severance.S02E03.1080p.WEB-DL.mkv")

        assertEquals("Severance", parsed.title)
        assertEquals(MediaRecord.Kind.TV_EPISODE, parsed.kind)
        assertEquals(2, parsed.season)
        assertEquals(3, parsed.episode)
    }

    @Test
    fun parsesMovieWithYear() {
        val parsed = FilenameParser.parse("Alien (1979).1080p.BluRay.mkv")

        assertEquals("Alien", parsed.title)
        assertEquals(1979, parsed.year)
        assertEquals(MediaRecord.Kind.MOVIE, parsed.kind)
    }

    @Test
    fun stripsCommonReleaseNoise() {
        val parsed = FilenameParser.parse("The.Matrix.2160p.UHD.BluRay.x265.mkv")

        assertEquals("The Matrix", parsed.title)
        assertNull(parsed.year)
    }

    @Test
    fun keepsReadablePlainFilename() {
        val parsed = FilenameParser.parse("My Holiday Video.mp4")

        assertEquals("My Holiday Video", parsed.title)
        assertEquals(MediaRecord.Kind.MOVIE, parsed.kind)
    }
}
