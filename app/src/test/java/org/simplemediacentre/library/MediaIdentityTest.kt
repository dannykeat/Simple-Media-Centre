package org.simplemediacentre.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.simplemediacentre.model.MediaRecord

class MediaIdentityTest {
    private fun item(
        uri: String,
        sourceId: String = "drive|Movies",
        sizeBytes: Long = 1_000L,
        modifiedAt: Long = 2_000L,
    ) = MediaRecord(
        uri = uri,
        fileName = uri.substringAfterLast('/'),
        title = "Title",
        kind = MediaRecord.Kind.MOVIE,
        sourceId = sourceId,
        sizeBytes = sizeBytes,
        modifiedAt = modifiedAt,
    )

    @Test
    fun signatureUsesSourceSizeAndModifiedTime() {
        assertEquals(
            "drive|Movies|1000|2000",
            MediaIdentity.signature(item("content://old")),
        )
    }

    @Test
    fun missingSizeDoesNotProduceRenameSignature() {
        assertNull(MediaIdentity.signature(item("content://old", sizeBytes = 0L)))
    }

    @Test
    fun uniqueSignatureCanBeUsedForRenameMatching() {
        val old = item("content://old")
        val lookup = MediaIdentity.uniquePreviousBySignature(listOf(old))

        assertEquals(old, lookup[MediaIdentity.signature(old)])
    }

    @Test
    fun ambiguousSignatureIsExcluded() {
        val first = item("content://one")
        val second = item("content://two")
        val lookup = MediaIdentity.uniquePreviousBySignature(listOf(first, second))

        assertNull(lookup[MediaIdentity.signature(first)])
    }
}
