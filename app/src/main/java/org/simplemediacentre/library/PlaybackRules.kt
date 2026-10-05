package org.simplemediacentre.library

object PlaybackRules {
    private const val RESUME_THRESHOLD_MS = 30_000L
    private const val SHORT_VIDEO_LIMIT_MS = 5 * 60_000L
    private const val LONG_VIDEO_END_MARGIN_MS = 60_000L

    fun canResume(positionMs: Long, watched: Boolean): Boolean =
        positionMs > RESUME_THRESHOLD_MS && !watched

    fun isFinished(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L || positionMs < 0L) return false

        val threshold = if (durationMs <= SHORT_VIDEO_LIMIT_MS) {
            (durationMs * 9L) / 10L
        } else {
            durationMs - LONG_VIDEO_END_MARGIN_MS
        }

        return positionMs >= threshold.coerceAtLeast(0L)
    }
}
