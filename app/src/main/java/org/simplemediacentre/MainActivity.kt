package org.simplemediacentre

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.simplemediacentre.library.LibraryStore
import org.simplemediacentre.library.MediaScanner
import org.simplemediacentre.model.MediaRecord

class MainActivity : Activity() {
    private lateinit var store: LibraryStore
    private lateinit var listView: ListView
    private lateinit var statusView: TextView
    private lateinit var progressView: ProgressBar

    private var library: List<MediaRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LibraryStore(this)
        setContentView(buildUi())
        library = store.loadLibrary()
        renderLibrary()

        if (library.isEmpty() && store.roots().isNotEmpty()) {
            scanLibrary()
        }
    }

    private fun buildUi(): View {
        val padding = (16 * resources.displayMetrics.density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)

            addView(TextView(context).apply {
                text = "Simple Media Centre"
                textSize = 26f
            })

            addView(TextView(context).apply {
                text = "Local videos, without the Kodi overhead"
                textSize = 14f
            })

            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(Button(context).apply {
                    text = "Add media folder"
                    setOnClickListener { chooseMediaFolder() }
                })

                addView(Button(context).apply {
                    text = "Rescan"
                    setOnClickListener { scanLibrary() }
                })
            }
            addView(actions)

            progressView = ProgressBar(context).apply {
                visibility = View.GONE
            }
            addView(progressView)

            statusView = TextView(context).apply {
                textSize = 14f
                setPadding(0, padding / 2, 0, padding / 2)
            }
            addView(statusView)

            listView = ListView(context).apply {
                isFocusable = true
                setOnItemClickListener { _, _, position, _ ->
                    play(library[position])
                }
            }
            addView(
                listView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun chooseMediaFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQUEST_MEDIA_FOLDER)
    }

    @Deprecated("Uses the platform activity-result API to keep the MVP dependency-light.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_MEDIA_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            store.addRoot(uri.toString())
            scanLibrary()
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                "Android did not grant permanent access to that folder.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun scanLibrary() {
        val roots = store.roots()
        if (roots.isEmpty()) {
            statusView.text = "Add a media folder first."
            return
        }

        progressView.visibility = View.VISIBLE
        val suffix = if (roots.size == 1) "" else "s"
        statusView.text = "Scanning " + roots.size + " media source" + suffix + "…"

        Thread {
            val scanned = MediaScanner(this).scan(roots)
            store.saveLibrary(scanned)

            runOnUiThread {
                library = scanned
                progressView.visibility = View.GONE
                renderLibrary()
            }
        }.start()
    }

    private fun renderLibrary() {
        statusView.text = when {
            store.roots().isEmpty() ->
                "No media folders configured. Choose a folder or external drive to begin."
            library.isEmpty() ->
                "No supported video files found."
            else -> {
                val suffix = if (library.size == 1) "" else "s"
                library.size.toString() + " video" + suffix + " indexed."
            }
        }

        val labels = library.map { item ->
            val position = store.playbackPosition(item.uri)
            if (position > 30_000L) {
                item.displayTitle + "  •  Resume " + formatPosition(position)
            } else {
                item.displayTitle
            }
        }

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            labels,
        )
    }

    private fun play(item: MediaRecord) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URI, item.uri)
                .putExtra(PlayerActivity.EXTRA_TITLE, item.displayTitle)
        )
    }

    override fun onResume() {
        super.onResume()
        if (::listView.isInitialized) renderLibrary()
    }

    private fun formatPosition(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return minutes.toString() + ":" + seconds.toString().padStart(2, '0')
    }

    private companion object {
        const val REQUEST_MEDIA_FOLDER = 1001
    }
}
