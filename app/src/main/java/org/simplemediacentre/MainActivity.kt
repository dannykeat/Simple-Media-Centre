package org.simplemediacentre

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.simplemediacentre.library.LibraryStore
import org.simplemediacentre.library.MediaScanner
import org.simplemediacentre.metadata.LibraryEnricher
import org.simplemediacentre.metadata.MediaMetadata
import org.simplemediacentre.metadata.TmdbMetadataProvider
import org.simplemediacentre.model.MediaRecord

class MainActivity : Activity() {
    private enum class Section {
        MOVIES,
        TV,
        CONTINUE,
    }

    private lateinit var store: LibraryStore
    private lateinit var gridView: GridView
    private lateinit var statusView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var adapter: MediaLibraryAdapter

    private var library: List<MediaRecord> = emptyList()
    private var visibleCards: List<LibraryCard> = emptyList()
    private var section = Section.MOVIES

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
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)

            addView(TextView(context).apply {
                text = "Simple Media Centre"
                textSize = 26f
            })

            val sections = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(sectionButton("Movies", Section.MOVIES))
                addView(sectionButton("TV Shows", Section.TV))
                addView(sectionButton("Continue", Section.CONTINUE))
            }
            addView(sections)

            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(Button(context).apply {
                    text = "Add folder"
                    setOnClickListener { chooseMediaFolder() }
                })

                addView(Button(context).apply {
                    text = "Rescan"
                    setOnClickListener { scanLibrary() }
                })

                addView(Button(context).apply {
                    text = "TMDB"
                    setOnClickListener { showTmdbSetup() }
                })

                addView(Button(context).apply {
                    text = "About"
                    setOnClickListener { showAbout() }
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

            adapter = MediaLibraryAdapter(context, emptyList())

            gridView = GridView(context).apply {
                numColumns = GridView.AUTO_FIT
                columnWidth = (170 * density).toInt()
                horizontalSpacing = (12 * density).toInt()
                verticalSpacing = (12 * density).toInt()
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                gravity = Gravity.CENTER
                clipToPadding = false
                setPadding(0, 0, 0, padding)
                adapter = this@MainActivity.adapter

                setOnItemClickListener { _, _, position, _ ->
                    openCard(visibleCards[position])
                }

                setOnItemLongClickListener { _, _, position, _ ->
                    showDetails(visibleCards[position])
                    true
                }
            }

            addView(
                gridView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            )
        }
    }

    private fun sectionButton(label: String, target: Section): Button =
        Button(this).apply {
            text = label
            setOnClickListener {
                section = target
                renderLibrary()
                gridView.requestFocus()
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

    private fun showTmdbSetup() {
        val input = EditText(this).apply {
            hint = "TMDB API Read Access Token"
            setText(store.tmdbToken())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }

        AlertDialog.Builder(this)
            .setTitle("TMDB metadata")
            .setMessage(
                "Enter your TMDB API Read Access Token. Leave it blank to disable online metadata."
            )
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                store.saveTmdbToken(input.text.toString())
                scanLibrary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Simple Media Centre")
            .setMessage(
                "Local-first video library and player.\n\n" +
                    "This product uses the TMDB API but is not endorsed or certified by TMDB."
            )
            .setPositiveButton("OK", null)
            .show()
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
            val previous = store.loadLibrary().associateBy { it.uri }
            var scanned = MediaScanner(this).scan(roots).map { item ->
                carryCachedMetadata(item, previous[item.uri])
            }

            val token = store.tmdbToken()
            if (token.isNotBlank()) {
                runOnUiThread {
                    statusView.text = "Matching unmatched videos with TMDB…"
                }
                scanned = LibraryEnricher(TmdbMetadataProvider(token)).enrich(scanned)
            }

            store.saveLibrary(scanned)

            runOnUiThread {
                library = scanned
                progressView.visibility = View.GONE
                renderLibrary()
            }
        }.start()
    }

    private fun carryCachedMetadata(
        scanned: MediaRecord,
        previous: MediaRecord?,
    ): MediaRecord {
        if (previous == null ||
            previous.fileName != scanned.fileName ||
            previous.modifiedAt != scanned.modifiedAt
        ) {
            return scanned
        }

        return scanned.copy(
            metadataId = previous.metadataId,
            metadataTitle = previous.metadataTitle,
            overview = previous.overview,
            posterPath = previous.posterPath,
            backdropPath = previous.backdropPath,
            episodeMetadataId = previous.episodeMetadataId,
            episodeTitle = previous.episodeTitle,
            episodeOverview = previous.episodeOverview,
        )
    }

    private fun renderLibrary() {
        visibleCards = when (section) {
            Section.MOVIES -> movieCards()
            Section.TV -> tvCards()
            Section.CONTINUE -> continueCards()
        }
        adapter.submitItems(visibleCards)

        statusView.text = when {
            store.roots().isEmpty() ->
                "No media folders configured. Choose a folder or external drive to begin."
            library.isEmpty() ->
                "No supported video files found."
            visibleCards.isEmpty() ->
                when (section) {
                    Section.MOVIES -> "No movies found."
                    Section.TV -> "No TV episodes found."
                    Section.CONTINUE -> "Nothing to continue watching."
                }
            else -> {
                val matched = library.count { it.metadataId != null }
                val label = when (section) {
                    Section.MOVIES -> "movie"
                    Section.TV -> "show"
                    Section.CONTINUE -> "video"
                }
                val suffix = if (visibleCards.size == 1) "" else "s"
                visibleCards.size.toString() + " " + label + suffix +
                    if (store.tmdbToken().isNotBlank()) " • " + matched + " files matched" else ""
            }
        }
    }

    private fun movieCards(): List<LibraryCard> =
        library
            .filter { it.kind == MediaRecord.Kind.MOVIE }
            .sortedBy { it.displayTitle.lowercase() }
            .map { item ->
                LibraryCard(
                    key = item.uri,
                    title = item.displayTitle,
                    subtitle = item.year?.toString().orEmpty(),
                    posterPath = item.posterPath,
                    items = listOf(item),
                )
            }

    private fun tvCards(): List<LibraryCard> =
        library
            .filter { it.kind == MediaRecord.Kind.TV_EPISODE }
            .groupBy { item ->
                item.metadataId?.let { "tmdb:$it" }
                    ?: "title:" + item.title.lowercase()
            }
            .values
            .map { episodes ->
                val ordered = episodes.sortedWith(
                    compareBy<MediaRecord> { it.season ?: Int.MAX_VALUE }
                        .thenBy { it.episode ?: Int.MAX_VALUE }
                )
                val representative = ordered.first()
                val seasonCount = ordered.mapNotNull { it.season }.distinct().size
                val seasonText = if (seasonCount > 0) {
                    " • " + seasonCount + " season" + if (seasonCount == 1) "" else "s"
                } else {
                    ""
                }

                LibraryCard(
                    key = representative.metadataId?.toString() ?: representative.title,
                    title = representative.metadataTitle ?: representative.title,
                    subtitle = ordered.size.toString() + " episode" +
                        (if (ordered.size == 1) "" else "s") + seasonText,
                    posterPath = representative.posterPath,
                    items = ordered,
                )
            }
            .sortedBy { it.title.lowercase() }

    private fun continueCards(): List<LibraryCard> =
        library
            .mapNotNull { item ->
                val position = store.playbackPosition(item.uri)
                if (position <= 30_000L) {
                    null
                } else {
                    LibraryCard(
                        key = item.uri,
                        title = item.displayTitle,
                        subtitle = "Resume " + formatPosition(position),
                        posterPath = item.posterPath,
                        items = listOf(item),
                    )
                }
            }
            .sortedBy { it.title.lowercase() }

    private fun openCard(card: LibraryCard) {
        if (card.items.size == 1) {
            play(card.items.first())
        } else {
            showEpisodePicker(card)
        }
    }

    private fun showEpisodePicker(card: LibraryCard) {
        val labels = card.items.map { episode ->
            val position = store.playbackPosition(episode.uri)
            val resume = if (position > 30_000L) " • Resume " + formatPosition(position) else ""
            episode.episodeDisplayTitle + resume
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(card.title)
            .setItems(labels) { _, which ->
                showEpisodeDetails(card.items[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showEpisodeDetails(episode: MediaRecord) {
        val summary = episode.episodeOverview?.takeIf { it.isNotBlank() }
            ?: episode.overview?.takeIf { it.isNotBlank() }
            ?: "No online description is available for this episode."

        AlertDialog.Builder(this)
            .setTitle(episode.episodeDisplayTitle)
            .setMessage(summary)
            .setPositiveButton("Play") { _, _ -> play(episode) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDetails(card: LibraryCard) {
        val representative = card.items.first()
        val summary = buildString {
            if (card.subtitle.isNotBlank()) {
                append(card.subtitle)
                append("\n\n")
            }
            append(
                representative.overview?.takeIf { it.isNotBlank() }
                    ?: "No online description is available for this item."
            )
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(card.title)
            .setMessage(summary)
            .setPositiveButton(if (card.items.size == 1) "Play" else "Episodes") { _, _ ->
                openCard(card)
            }
            .setNegativeButton("Close", null)

        if (store.tmdbToken().isNotBlank()) {
            builder.setNeutralButton("Fix match") { _, _ ->
                promptFixMatch(card)
            }
        }

        builder.show()
    }

    private fun promptFixMatch(card: LibraryCard) {
        val representative = card.items.first()
        val input = EditText(this).apply {
            setText(representative.title)
            selectAll()
            hint = "Search title"
            isSingleLine = true
        }

        AlertDialog.Builder(this)
            .setTitle("Fix match")
            .setMessage("Search TMDB and choose the correct title.")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                searchForMatch(card, input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchForMatch(card: LibraryCard, query: String) {
        val token = store.tmdbToken()
        val representative = card.items.first()
        if (token.isBlank() || query.isBlank()) return

        progressView.visibility = View.VISIBLE
        statusView.text = "Searching TMDB…"

        Thread {
            val searchItem = representative.copy(
                title = query.trim(),
                year = null,
                metadataId = null,
                metadataTitle = null,
            )
            val candidates = TmdbMetadataProvider(token).search(searchItem, limit = 8)

            runOnUiThread {
                progressView.visibility = View.GONE
                renderLibrary()

                if (candidates.isEmpty()) {
                    Toast.makeText(this, "No TMDB matches found.", Toast.LENGTH_LONG).show()
                } else {
                    showMatchCandidates(card, candidates)
                }
            }
        }.start()
    }

    private fun showMatchCandidates(
        card: LibraryCard,
        candidates: List<MediaMetadata>,
    ) {
        val labels = candidates.map { candidate ->
            if (candidate.overview.isNullOrBlank()) {
                candidate.title
            } else {
                candidate.title + " — " + candidate.overview.take(90)
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose match")
            .setItems(labels) { _, which ->
                applyManualMatch(card, candidates[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyManualMatch(
        card: LibraryCard,
        candidate: MediaMetadata,
    ) {
        val token = store.tmdbToken()
        val affectedUris = card.items.map { it.uri }.toSet()
        val provider = TmdbMetadataProvider(token)

        progressView.visibility = View.VISIBLE
        statusView.text = "Applying TMDB match…"

        Thread {
            val changed = library.map { item ->
                if (item.uri !in affectedUris) {
                    item
                } else {
                    item.copy(
                        metadataId = candidate.id,
                        metadataTitle = candidate.title,
                        overview = candidate.overview,
                        posterPath = candidate.posterPath,
                        backdropPath = candidate.backdropPath,
                        episodeMetadataId = null,
                        episodeTitle = null,
                        episodeOverview = null,
                    )
                }
            }

            val enriched = LibraryEnricher(provider).enrich(changed)
            store.saveLibrary(enriched)

            runOnUiThread {
                library = enriched
                progressView.visibility = View.GONE
                renderLibrary()
            }
        }.start()
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
        if (::gridView.isInitialized) renderLibrary()
    }

    private fun formatPosition(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
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

    private companion object {
        const val REQUEST_MEDIA_FOLDER = 1001
    }
}
