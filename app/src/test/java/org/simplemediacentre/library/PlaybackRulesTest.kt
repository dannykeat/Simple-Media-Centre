package org.simplemediacentre.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRulesTest {
    @Test
    fun resumeRequiresMoreThanThirtySecondsAndNotWatched() {
        assertFalse(PlaybackRules.canResume(30_000L, false))
        assertTrue(PlaybackRules.canResume(30_001L, false))
        assertFalse(PlaybackRules.canResume(120_000L, true))
    }

    @Test
    fun shortVideoUsesNinetyPercentThreshold() {
        val duration = 60_000L

        assertFalse(PlaybackRules.isFinished(30_000L, duration))
        assertFalse(PlaybackRules.isFinished(53_999L, duration))
        assertTrue(PlaybackRules.isFinished(54_000L, duration))
    }

    @Test
    fun longVideoUsesFinalMinuteThreshold() {
        val duration = 120 * 60_000L

        assertFalse(PlaybackRules.isFinished(duration - 60_001L, duration))
        assertTrue(PlaybackRules.isFinished(duration - 60_000L, duration))
    }

    @Test
    fun unknownDurationIsNeverFinished() {
        assertFalse(PlaybackRules.isFinished(10_000L, 0L))
    }
}
