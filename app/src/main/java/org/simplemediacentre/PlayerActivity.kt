package org.simplemediacentre

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.simplemediacentre.library.LibraryStore
import org.simplemediacentre.model.MediaRecord
import java.util.Locale

class PlayerActivity : Activity() {
    private data class TrackChoice(
        val group: Tracks.Group,
        val trackIndex: Int,
        val label: String,
        val selected: Boolean,
    )

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var audioButton: Button
    private lateinit var subtitleButton: Button
    private lateinit var infoButton: Button
    private lateinit var store: LibraryStore
    private lateinit var mediaUri: String
    private var upNextShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaUri = intent.getStringExtra(EXTRA_URI).orEmpty()
        if (mediaUri.isBlank()) {
            Toast.makeText(this, "The video location is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        title = intent.getStringExtra(EXTRA_TITLE) ?: "Simple Media Centre"
        store = LibraryStore(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(buildPlayerUi())

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory).build()
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.requestFocus()

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    updateTrackButtons(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    showPlaybackError(error)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && !upNextShown) {
                        upNextShown = true
                        store.setWatched(mediaUri, true)
                        nextEpisode()?.let(::showUpNext)
                    }
                }
            }
        )

        val uri = Uri.parse(mediaUri)
        if (!canReadMedia(uri)) {
            showMediaAccessError(uri)
            return
        }

        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(mediaUri)
                .build()
        )
        exoPlayer.prepare()

        val savedPosition = store.playbackPosition(mediaUri)
        if (savedPosition > 0L) {
            exoPlayer.seekTo(savedPosition)
        }
        exoPlayer.playWhenReady = true
    }

    private fun nextEpisode(): MediaRecord? {
        val items = store.loadLibrary()
        val current = items.firstOrNull { it.uri == mediaUri } ?: return null
        if (current.kind != MediaRecord.Kind.TV_EPISODE) return null

        val sameShow = items
            .filter { item ->
                item.kind == MediaRecord.Kind.TV_EPISODE &&
                    if (current.metadataId != null) {
                        item.metadataId == current.metadataId
                    } else {
                        item.title.equals(current.title, ignoreCase = true)
                    }
            }
            .sortedWith(
                compareBy<MediaRecord> { it.season ?: Int.MAX_VALUE }
                    .thenBy { it.episode ?: Int.MAX_VALUE }
                    .thenBy { it.fileName.lowercase() }
            )

        val currentIndex = sameShow.indexOfFirst { it.uri == mediaUri }
        if (currentIndex < 0 || currentIndex >= sameShow.lastIndex) return null
        return sameShow[currentIndex + 1]
    }

    private fun showUpNext(next: MediaRecord) {
        AlertDialog.Builder(this)
            .setTitle("Up next")
            .setMessage(next.episodeDisplayTitle)
            .setPositiveButton("Play next") { _, _ ->
                store.savePlaybackPosition(mediaUri, 0L)
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra(EXTRA_URI, next.uri)
                        .putExtra(EXTRA_TITLE, next.displayTitle)
                )
                finish()
            }
            .setNegativeButton("Done") { _, _ -> finish() }
            .show()
    }

    private fun canReadMedia(uri: Uri): Boolean {
        if (uri.scheme != "content") return true
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: java.io.FileNotFoundException) {
            false
        }
    }

    private fun showMediaAccessError(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Cannot access this video")
            .setMessage(
                "Android no longer allows Simple Media Centre to read this file. " +
                    "The source may have been disconnected or its permission may have changed."
            )
            .setPositiveButton("Other player") { _, _ -> openExternalPlayer() }
            .setNegativeButton("Close") { _, _ -> finish() }
            .show()
    }

    private fun showPlaybackError(error: PlaybackException) {
        val detail = error.cause?.message?.takeIf { it.isNotBlank() }
            ?: error.message
            ?: "Unknown playback error"
        val message = error.errorCodeName + "\n\n" + detail

        AlertDialog.Builder(this)
            .setTitle("Cannot play this video")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ ->
                player?.prepare()
                player?.playWhenReady = true
                playerView.requestFocus()
            }
            .setNeutralButton("Other player") { _, _ -> openExternalPlayer() }
            .setNegativeButton("Close") { _, _ -> finish() }
            .show()
    }

    private fun openExternalPlayer() {
        val uri = Uri.parse(mediaUri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(intent, "Open video with"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No other video player is installed.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun buildPlayerUi(): View {
        val density = resources.displayMetrics.density
        val margin = (16 * density).toInt()

        playerView = PlayerView(this).apply {
            useController = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        audioButton = Button(this).apply {
            text = "Audio"
            isEnabled = false
            setOnClickListener { showAudioTracks() }
        }

        subtitleButton = Button(this).apply {
            text = "Subtitles"
            isEnabled = false
            setOnClickListener { showSubtitleTracks() }
        }

        infoButton = Button(this).apply {
            text = "Info"
            isEnabled = false
            setOnClickListener { showPlaybackInfo() }
        }

        val trackControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            addView(audioButton)
            addView(subtitleButton)
            addView(infoButton)
        }

        return FrameLayout(this).apply {
            addView(playerView)
            addView(
                trackControls,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END,
                ).apply {
                    setMargins(margin, margin, margin, margin)
                }
            )
        }
    }

    private fun updateTrackButtons(tracks: Tracks) {
        audioButton.isEnabled = trackChoices(tracks, C.TRACK_TYPE_AUDIO).isNotEmpty()
        subtitleButton.isEnabled = trackChoices(tracks, C.TRACK_TYPE_TEXT).isNotEmpty()
        infoButton.isEnabled = tracks.groups.isNotEmpty()
    }

    private fun showPlaybackInfo() {
        val exoPlayer = player ?: return
        val lines = mutableListOf<String>()

        exoPlayer.currentTracks.groups.forEach { group ->
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSelected(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                when (group.type) {
                    C.TRACK_TYPE_VIDEO -> {
                        val resolution = if (format.width > 0 && format.height > 0) {
                            format.width.toString() + "×" + format.height
                        } else {
                            null
                        }
                        val codec = format.codecs
                            ?: format.sampleMimeType
                            ?: "unknown codec"
                        lines += listOfNotNull(
                            "Video: " + codec,
                            resolution,
                        ).joinToString(" • ")
                    }
                    C.TRACK_TYPE_AUDIO -> {
                        val codec = format.codecs
                            ?: format.sampleMimeType
                            ?: "unknown codec"
                        val channels = if (format.channelCount > 0) {
                            format.channelCount.toString() + " ch"
                        } else {
                            null
                        }
                        val rate = if (format.sampleRate > 0) {
                            format.sampleRate.toString() + " Hz"
                        } else {
                            null
                        }
                        lines += listOfNotNull(
                            "Audio: " + codec,
                            channels,
                            rate,
                        ).joinToString(" • ")
                    }
                    C.TRACK_TYPE_TEXT -> {
                        lines += "Subtitles: " + trackLabel(format, trackIndex)
                    }
                }
            }
        }

        val duration = exoPlayer.duration
        if (duration > 0L) {
            lines += "Duration: " + formatDuration(duration)
        }

        AlertDialog.Builder(this)
            .setTitle(title ?: "Playback info")
            .setMessage(lines.ifEmpty { listOf("Track information is not available yet.") }.joinToString("\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            hours.toString() + ":" +
                minutes.toString().padStart(2, '0') + ":" +
                seconds.toString().padStart(2, '0')
        } else {
            minutes.toString() + ":" + seconds.toString().padStart(2, '0')
        }
    }

    private fun showAudioTracks() {
        val exoPlayer = player ?: return
        val choices = trackChoices(exoPlayer.currentTracks, C.TRACK_TYPE_AUDIO)
        if (choices.isEmpty()) {
            Toast.makeText(this, "No alternate audio tracks are available.", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = buildList {
            add("Auto")
            choices.forEach { add(it.label) }
        }.toTypedArray()

        val selectedIndex = choices.indexOfFirst { it.selected }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0

        AlertDialog.Builder(this)
            .setTitle("Audio track")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                if (which == 0) {
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .build()
                } else {
                    selectTrack(C.TRACK_TYPE_AUDIO, choices[which - 1])
                }
                dialog.dismiss()
                playerView.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubtitleTracks() {
        val exoPlayer = player ?: return
        val choices = trackChoices(exoPlayer.currentTracks, C.TRACK_TYPE_TEXT)
        if (choices.isEmpty()) {
            Toast.makeText(this, "No subtitle tracks are available.", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = buildList {
            add("Off")
            choices.forEach { add(it.label) }
        }.toTypedArray()

        val textDisabled =
            exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val selectedIndex = if (textDisabled) {
            0
        } else {
            choices.indexOfFirst { it.selected }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: 0
        }

        AlertDialog.Builder(this)
            .setTitle("Subtitles")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                if (which == 0) {
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                } else {
                    selectTrack(C.TRACK_TYPE_TEXT, choices[which - 1])
                }
                dialog.dismiss()
                playerView.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectTrack(trackType: Int, choice: TrackChoice) {
        val exoPlayer = player ?: return
        exoPlayer.trackSelectionParameters =
            exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(trackType, false)
                .setOverrideForType(
                    TrackSelectionOverride(
                        choice.group.mediaTrackGroup,
                        choice.trackIndex,
                    )
                )
                .build()
    }

    private fun trackChoices(
        tracks: Tracks,
        trackType: Int,
    ): List<TrackChoice> =
        buildList {
            tracks.groups
                .filter { it.type == trackType }
                .forEach { group ->
                    for (trackIndex in 0 until group.length) {
                        if (!group.isTrackSupported(trackIndex)) continue
                        val format = group.getTrackFormat(trackIndex)
                        add(
                            TrackChoice(
                                group = group,
                                trackIndex = trackIndex,
                                label = trackLabel(format, trackIndex),
                                selected = group.isTrackSelected(trackIndex),
                            )
                        )
                    }
                }
        }

    private fun trackLabel(format: Format, trackIndex: Int): String {
        val parts = mutableListOf<String>()

        format.label?.takeIf { it.isNotBlank() }?.let(parts::add)

        format.language
            ?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { language ->
                val display = runCatching {
                    Locale.forLanguageTag(language).displayLanguage
                }.getOrNull()
                parts += display?.takeIf { it.isNotBlank() } ?: language
            }

        if (format.channelCount > 0) {
            parts += when (format.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> format.channelCount.toString() + " channels"
            }
        }

        format.codecs?.takeIf { it.isNotBlank() }?.let(parts::add)

        return parts.distinct().joinToString(" • ")
            .ifBlank { "Track " + (trackIndex + 1) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (::playerView.isInitialized && playerView.dispatchKeyEvent(event)) {
            true
        } else {
            super.dispatchKeyEvent(event)
        }

    override fun onStop() {
        player?.let { currentPlayer ->
            val duration = currentPlayer.duration
            val current = currentPlayer.currentPosition
            val finished = duration > 0 && current >= duration - 60_000L
            val positionToSave = if (finished) 0L else current
            store.savePlaybackPosition(mediaUri, positionToSave)
            if (current > 0L) {
                store.markPlayed(mediaUri)
            }
            if (finished) {
                store.setWatched(mediaUri, true)
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "media_uri"
        const val EXTRA_TITLE = "media_title"
    }
}
