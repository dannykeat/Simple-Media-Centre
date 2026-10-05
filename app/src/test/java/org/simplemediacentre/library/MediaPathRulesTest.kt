package org.simplemediacentre.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPathRulesTest {
    @Test
    fun normalizesFolderPaths() {
        assertEquals("Movies/Action", MediaPathRules.normalize("/Movies/Action/"))
        assertNull(MediaPathRules.normalize(" / "))
    }

    @Test
    fun choosesMostSpecificSelectedFolder() {
        val selected = setOf("Movies", "Movies/Action")

        assertEquals(
            "Movies/Action",
            MediaPathRules.selectedFolder("Movies/Action/Classic", selected),
        )
    }

    @Test
    fun doesNotMatchSiblingWithSamePrefix() {
        val selected = setOf("Movies")

        assertNull(MediaPathRules.selectedFolder("Movies2", selected))
    }

    @Test
    fun calculatesPathWithinSelectedFolder() {
        assertEquals(
            "Classic",
            MediaPathRules.relativeWithinFolder("Movies/Action/Classic", "Movies/Action"),
        )
        assertNull(MediaPathRules.relativeWithinFolder("Movies", "Movies"))
    }

    @Test
    fun createsStableFolderSourceId() {
        assertEquals(
            "ABCD-1234|Movies/Action",
            MediaPathRules.sourceId("ABCD-1234", "/Movies/Action/"),
        )
    }
}
