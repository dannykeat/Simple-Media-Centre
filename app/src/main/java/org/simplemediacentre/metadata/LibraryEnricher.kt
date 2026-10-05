package org.simplemediacentre.metadata

import org.simplemediacentre.model.MediaRecord

class LibraryEnricher(
    private val provider: MetadataProvider,
) {
    fun enrich(items: List<MediaRecord>): List<MediaRecord> =
        items.map { item ->
            val matched = if (item.metadataId != null) {
                item
            } else {
                provider.match(item)?.let { metadata ->
                    item.copy(
                        metadataId = metadata.id,
                        metadataTitle = metadata.title,
                        overview = metadata.overview,
                        posterPath = metadata.posterPath,
                        backdropPath = metadata.backdropPath,
                        year = item.year ?: metadata.year,
                        genres = metadata.genres,
                    )
                } ?: item
            }

            enrichEpisode(matched)
        }

    private fun enrichEpisode(item: MediaRecord): MediaRecord {
        if (item.kind != MediaRecord.Kind.TV_EPISODE ||
            item.metadataId == null ||
            item.season == null ||
            item.episode == null ||
            item.episodeMetadataId != null
        ) {
            return item
        }

        val details = provider.episodeDetails(
            seriesId = item.metadataId,
            season = item.season,
            episode = item.episode,
        ) ?: return item

        return item.copy(
            episodeMetadataId = details.id,
            episodeTitle = details.title,
            episodeOverview = details.overview,
        )
    }
}
