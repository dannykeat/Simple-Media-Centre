package org.simplemediacentre.metadata

import org.simplemediacentre.model.MediaRecord

class LibraryEnricher(
    private val provider: MetadataProvider,
) {
    fun enrich(items: List<MediaRecord>): List<MediaRecord> =
        items.map { item ->
            if (item.metadataId != null) {
                item
            } else {
                provider.match(item)?.let { metadata ->
                    item.copy(
                        metadataId = metadata.id,
                        metadataTitle = metadata.title,
                        overview = metadata.overview,
                        posterPath = metadata.posterPath,
                        backdropPath = metadata.backdropPath,
                    )
                } ?: item
            }
        }
}
