package org.simplemediacentre.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.simplemediacentre.model.MediaRecord
import org.simplemediacentre.model.SourceType

class SourceClassifierTest {
    @Test
    fun movieSourceOverridesFilenameGuess() {
        assertEquals(
            MediaRecord.Kind.MOVIE,
            SourceClassifier.classify(MediaRecord.Kind.TV_EPISODE, SourceType.MOVIES),
        )
    }

    @Test
    fun tvSourceOverridesFilenameGuess() {
        assertEquals(
            MediaRecord.Kind.TV_EPISODE,
            SourceClassifier.classify(MediaRecord.Kind.MOVIE, SourceType.TV_SHOWS),
        )
    }

    @Test
    fun videoSourcePreventsMovieClassification() {
        assertEquals(
            MediaRecord.Kind.VIDEO,
            SourceClassifier.classify(MediaRecord.Kind.MOVIE, SourceType.VIDEOS),
        )
    }

    @Test
    fun mixedSourceKeepsParsedKind() {
        assertEquals(
            MediaRecord.Kind.TV_EPISODE,
            SourceClassifier.classify(MediaRecord.Kind.TV_EPISODE, SourceType.MIXED),
        )
    }
}
