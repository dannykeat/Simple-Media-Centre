package org.simplemediacentre.metadata

import org.simplemediacentre.model.MediaRecord

interface MetadataProvider {
    fun match(item: MediaRecord): MediaMetadata?
}

data class MediaMetadata(
    val id: Int,
    val title: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
)
