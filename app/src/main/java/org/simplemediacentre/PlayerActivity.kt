package org.simplemediacentre

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.simplemediacentre.library.LibraryStore

class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
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

        playerView = PlayerView(this).apply {
            useController = true
            isFocusable = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(playerView)

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.requestFocus()

        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(mediaUri)))
        exoPlayer.prepare()

        val savedPosition = store.playbackPosition(mediaUri)
        if (savedPosition > 0L) {
            exoPlayer.seekTo(savedPosition)
        }
        exoPlayer.playWhenReady = true
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
            val positionToSave =
                if (duration > 0 && current >= duration - 60_000L) 0L else current
            store.savePlaybackPosition(mediaUri, positionToSave)
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
