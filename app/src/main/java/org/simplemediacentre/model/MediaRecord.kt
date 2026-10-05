package org.simplemediacentre.model

data class MediaRecord(
    val uri: String,
    val fileName: String,
    val title: String,
    val kind: Kind,
    val sourceId: String? = null,
    val relativePath: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val modifiedAt: Long = 0L,
    val addedAt: Long = 0L,
    val metadataId: Int? = null,
    val metadataTitle: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val episodeMetadataId: Int? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
) {
    enum class Kind {
        MOVIE,
        TV_EPISODE,
        VIDEO,
        UNKNOWN,
    }

    val displayTitle: String
        get() {
            val base = metadataTitle?.takeIf { it.isNotBlank() } ?: title
            return if (kind == Kind.TV_EPISODE && season != null && episode != null) {
                base + "  •  S" + season.toString().padStart(2, '0') +
                    "E" + episode.toString().padStart(2, '0')
            } else {
                base
            }
        }

    val episodeDisplayTitle: String
        get() = if (kind == Kind.TV_EPISODE && season != null && episode != null) {
            "S" + season.toString().padStart(2, '0') +
                "E" + episode.toString().padStart(2, '0') +
                (episodeTitle?.takeIf { it.isNotBlank() }?.let { " • " + it } ?: "")
        } else {
            displayTitle
        }
}
