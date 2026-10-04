package org.simplemediacentre.model

data class MediaRecord(
    val uri: String,
    val fileName: String,
    val title: String,
    val kind: Kind,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val modifiedAt: Long = 0L,
    val metadataId: Int? = null,
    val metadataTitle: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
) {
    enum class Kind {
        MOVIE,
        TV_EPISODE,
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
}
