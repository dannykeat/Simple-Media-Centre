package org.simplemediacentre

import android.app.Activity
import android.app.AlertDialog
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.simplemediacentre.library.LibraryStore
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
    private lateinit var store: LibraryStore
    private lateinit var mediaUri: String

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

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.requestFocus()

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    updateTrackButtons(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    val detail = error.cause?.message?.takeIf { it.isNotBlank() }
                        ?: error.message
                        ?: "Unknown playback error"
                    Toast.makeText(
                        this@PlayerActivity,
                        "Cannot play this video: " + detail,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        )

        val uri = Uri.parse(mediaUri)
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

        val trackControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            addView(audioButton)
            addView(subtitleButton)
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
