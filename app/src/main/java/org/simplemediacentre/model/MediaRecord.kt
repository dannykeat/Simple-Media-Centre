package org.simplemediacentre.model

data class MediaRecord(
    val uri: String,
    val fileName: String,
    val title: String,
    val kind: Kind,
    val season: Int? = null,
    val episode: Int? = null,
    val modifiedAt: Long = 0L,
) {
    enum class Kind {
        MOVIE,
        TV_EPISODE,
        UNKNOWN,
    }

    val displayTitle: String
        get() = if (kind == Kind.TV_EPISODE && season != null && episode != null) {
            title + "  •  S" + season.toString().padStart(2, '0') +
                "E" + episode.toString().padStart(2, '0')
        } else {
            title
        }
}
