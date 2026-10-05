package org.simplemediacentre.library

import org.simplemediacentre.model.MediaRecord
import org.simplemediacentre.model.SourceType

object SourceClassifier {
    fun classify(
        parsedKind: MediaRecord.Kind,
        sourceType: SourceType,
    ): MediaRecord.Kind =
        when (sourceType) {
            SourceType.MOVIES -> MediaRecord.Kind.MOVIE
            SourceType.TV_SHOWS -> MediaRecord.Kind.TV_EPISODE
            SourceType.VIDEOS -> MediaRecord.Kind.VIDEO
            SourceType.MIXED -> parsedKind
        }
}
