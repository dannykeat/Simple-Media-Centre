package org.simplemediacentre.library

import org.simplemediacentre.model.MediaRecord

object MediaIdentity {
    fun signature(item: MediaRecord): String? {
        val source = item.sourceId?.takeIf { it.isNotBlank() } ?: return null
        if (item.sizeBytes <= 0L || item.modifiedAt <= 0L) return null
        return source + "|" + item.sizeBytes + "|" + item.modifiedAt
    }

    fun uniquePreviousBySignature(
        items: Collection<MediaRecord>,
    ): Map<String, MediaRecord> =
        items
            .mapNotNull { item -> signature(item)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (signature, matches) ->
                matches.singleOrNull()?.let { signature to it }
            }
            .toMap()
}
