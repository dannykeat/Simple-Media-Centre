package org.simplemediacentre.metadata

import org.simplemediacentre.model.MediaRecord

interface MetadataProvider {
    fun match(item: MediaRecord): MediaMetadata?
    fun search(item: MediaRecord, limit: Int = 5): List<MediaMetadata>
    fun episodeDetails(seriesId: Int, season: Int, episode: Int): EpisodeMetadata?
}

data class MediaMetadata(
    val id: Int,
    val title: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
)

data class EpisodeMetadata(
    val id: Int,
    val title: String,
    val overview: String?,
)
