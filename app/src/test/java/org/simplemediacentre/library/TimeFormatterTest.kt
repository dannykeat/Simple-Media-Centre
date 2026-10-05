package org.simplemediacentre.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {
    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:00", TimeFormatter.format(0L))
        assertEquals("1:05", TimeFormatter.format(65_000L))
    }

    @Test
    fun formatsHours() {
        assertEquals("1:02:03", TimeFormatter.format(3_723_000L))
    }

    @Test
    fun negativeValuesClampToZero() {
        assertEquals("0:00", TimeFormatter.format(-1L))
    }
}
