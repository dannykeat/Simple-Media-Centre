package org.simplemediacentre

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.graphics.Color
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
import org.simplemediacentre.model.SourceType

class MainActivity : Activity() {
    private enum class Section {
        HOME,
        MOVIES,
        TV,
        VIDEOS,
        CONTINUE,
        RECENT,
    }

    private lateinit var store: LibraryStore
    private lateinit var gridView: GridView
    private lateinit var statusView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var adapter: MediaLibraryAdapter
    private val sectionButtons = mutableMapOf<Section, Button>()

    private var library: List<MediaRecord> = emptyList()
    private var visibleCards: List<LibraryCard> = emptyList()
    private var section = Section.HOME
    private var searchQuery = ""
    private var movieSort = MovieSort.TITLE

    private enum class MovieSort {
        TITLE,
        RECENT,
        YEAR,
        UNWATCHED,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LibraryStore(this)
        setContentView(buildUi())
        library = store.loadLibrary()
        updateSectionButtons()
        renderLibrary()

        if (library.isEmpty() && (store.roots().isNotEmpty() || store.mediaStoreVolumes().isNotEmpty())) {
            scanLibrary()
        }
    }

    private fun buildUi(): View {
        val density = resources.displayMetrics.density
        val tvScale = resources.configuration.smallestScreenWidthDp >= 600
        val padding = ((if (tvScale) 20 else 16) * density).toInt()
        val gridColumnWidth = ((if (tvScale) 200 else 170) * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 8, 8))
            setPadding(padding, padding, padding, padding)

            addView(TextView(context).apply {
                text = "Simple Media Centre"
                textSize = if (tvScale) 30f else 26f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, padding / 2)
            })

            val sections = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(sectionButton("Home", Section.HOME))
                addView(sectionButton("Movies", Section.MOVIES))
                addView(sectionButton("TV Shows", Section.TV))
                addView(sectionButton("Videos", Section.VIDEOS))
                addView(sectionButton("Continue", Section.CONTINUE))
                addView(sectionButton("Recent", Section.RECENT))
            }
            addView(sections)

            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                addView(Button(context).apply {
                    text = "Search"
                    setOnClickListener { showSearch() }
                })

                addView(Button(context).apply {
                    text = "Browse"
                    setOnClickListener { showBrowseOptions() }
                })

                addView(Button(context).apply {
                    text = "Settings"
                    setOnClickListener { showSettings() }
                })
            }
            addView(actions)

            progressView = ProgressBar(context).apply {
                visibility = View.GONE
            }
            addView(progressView)

            statusView = TextView(context).apply {
                textSize = if (tvScale) 16f else 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, padding / 2, 0, padding / 2)
            }
            addView(statusView)

            adapter = MediaLibraryAdapter(context, emptyList())

            gridView = GridView(context).apply {
                numColumns = GridView.AUTO_FIT
                columnWidth = gridColumnWidth
                horizontalSpacing = ((if (tvScale) 18 else 12) * density).toInt()
                verticalSpacing = ((if (tvScale) 18 else 12) * density).toInt()
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
            sectionButtons[target] = this
            setOnClickListener {
                section = target
                updateSectionButtons()
                renderLibrary()
                gridView.requestFocus()
            }
        }

    private fun updateSectionButtons() {
        sectionButtons.forEach { (target, button) ->
            button.alpha = if (target == section) 1f else 0.62f
            button.isSelected = target == section
        }
    }

    private fun showSearch() {
        val input = EditText(this).apply {
            setText(searchQuery)
            selectAll()
            hint = "Title or filename"
            isSingleLine = true
        }

        AlertDialog.Builder(this)
            .setTitle("Search library")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                searchQuery = input.text.toString().trim()
                renderLibrary()
                gridView.requestFocus()
            }
            .setNeutralButton("Clear") { _, _ ->
                searchQuery = ""
                renderLibrary()
                gridView.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun chooseMediaFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

        if (intent.resolveActivity(packageManager) != null) {
            try {
                startActivityForResult(intent, REQUEST_MEDIA_FOLDER)
                return
            } catch (_: ActivityNotFoundException) {
                // Some TV firmware reports a picker handler that cannot actually launch.
            }
        }

        chooseMediaStoreVolume()
    }

    private fun chooseMediaStoreVolume() {
        if (!hasVideoReadPermission()) {
            requestPermissions(
                arrayOf(requiredVideoReadPermission()),
                REQUEST_VIDEO_PERMISSION,
            )
            return
        }

        showMediaStoreVolumes()
    }

    private fun hasVideoReadPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            checkSelfPermission(requiredVideoReadPermission()) == PackageManager.PERMISSION_GRANTED

    private fun requiredVideoReadPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun showMediaStoreVolumes() {
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(this).sorted()
        } else {
            listOf(MEDIASTORE_LEGACY_EXTERNAL)
        }

        if (volumes.isEmpty()) {
            Toast.makeText(
                this,
                "No shared media storage is currently available.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val labels = volumes.map { volume ->
            when (volume) {
                MediaStore.VOLUME_EXTERNAL_PRIMARY -> "Internal shared storage"
                MEDIASTORE_LEGACY_EXTERNAL -> "Shared storage"
                else -> "External storage • " + volume
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose media storage")
            .setMessage(
                "This device has no folder picker. Choose a storage volume and " +
                    "Simple Media Centre will index the videos Android exposes from it."
            )
            .setItems(labels) { _, which ->
                val volume = volumes[which]
                store.addMediaStoreVolume(volume)
                chooseSourceType(volume)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_VIDEO_PERMISSION) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            showMediaStoreVolumes()
        } else {
            Toast.makeText(
                this,
                "Video access is required to scan media on this device.",
                Toast.LENGTH_LONG,
            ).show()
        }
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
            val source = uri.toString()
            store.addRoot(source)
            chooseSourceType(source)
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                "Android did not grant permanent access to that folder.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun chooseSourceType(sourceId: String) {
        val types = SourceType.entries.toTypedArray()
        val labels = types.map(SourceType::label).toTypedArray()
        val current = types.indexOf(store.sourceType(sourceId)).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("What is in this source?")
            .setMessage("Choose how videos in this folder or storage source should be organised.")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                store.setSourceType(sourceId, types[which])
                dialog.dismiss()
                scanLibrary()
            }
            .setNegativeButton("Keep automatic") { _, _ ->
                store.setSourceType(sourceId, SourceType.MIXED)
                scanLibrary()
            }
            .show()
    }

    private fun showSettings() {
        val labels = arrayOf(
            "Add media source",
            "Manage sources",
            "Rescan library",
            "Metadata",
            "About",
        )

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> chooseMediaFolder()
                    1 -> showSources()
                    2 -> scanLibrary()
                    3 -> showMetadataSettings()
                    4 -> showAbout()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSources() {
        val roots = store.roots().sorted()
        val volumes = store.mediaStoreVolumes().sorted()
        val sources = roots.map { Triple("folder", it, sourceLabel(it)) } +
            volumes.map { Triple("volume", it, it) }

        if (sources.isEmpty()) {
            Toast.makeText(this, "No media sources configured.", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = sources.map { (_, id, label) ->
            label + " • " + store.sourceType(id).label
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Media sources")
            .setItems(labels) { _, which ->
                val (kind, id, label) = sources[which]
                val actions = arrayOf("Change type", "Remove source")
                AlertDialog.Builder(this)
                    .setTitle(label)
                    .setItems(actions) { _, action ->
                        if (action == 0) {
                            chooseSourceType(id)
                        } else {
                            confirmRemoveSource(kind, id, label)
                        }
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
            .setPositiveButton("Add source") { _, _ -> chooseMediaFolder() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun sourceLabel(sourceId: String): String =
        runCatching {
            android.net.Uri.parse(sourceId).lastPathSegment
                ?.substringAfterLast(':')
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "Media folder"

    private fun confirmRemoveSource(kind: String, id: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove source?")
            .setMessage("Stop scanning " + label + "? Files are not deleted.")
            .setPositiveButton("Remove") { _, _ ->
                if (kind == "folder") store.removeRoot(id)
                else store.removeMediaStoreVolume(id)
                scanLibrary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMetadataSettings() {
        val configured = store.tmdbToken().isNotBlank()
        val labels = if (configured) {
            arrayOf("TMDB: configured", "Clear TMDB token", "Rescan metadata")
        } else {
            arrayOf("TMDB: not configured", "Configure TMDB")
        }

        AlertDialog.Builder(this)
            .setTitle("Metadata")
            .setMessage(
                if (configured) {
                    "Online metadata is optional. Local titles and video thumbnails remain available without it."
                } else {
                    "Local titles, thumbnails and playback work without an online metadata account."
                }
            )
            .setItems(labels) { _, which ->
                if (configured) {
                    when (which) {
                        0 -> showTmdbSetup()
                        1 -> {
                            store.saveTmdbToken("")
                            Toast.makeText(this, "TMDB disabled.", Toast.LENGTH_SHORT).show()
                        }
                        2 -> scanLibrary()
                    }
                } else if (which == 1) {
                    showTmdbSetup()
                }
            }
            .setNegativeButton("Close", null)
            .show()
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
        val mediaStoreVolumes = store.mediaStoreVolumes()
        val sourceCount = roots.size + mediaStoreVolumes.size
        if (sourceCount == 0) {
            library = emptyList()
            store.saveLibrary(emptyList())
            renderLibrary()
            return
        }

        progressView.visibility = View.VISIBLE
        val suffix = if (sourceCount == 1) "" else "s"
        statusView.text = "Scanning " + sourceCount + " media source" + suffix + "…"

        Thread {
            val previous = store.loadLibrary().associateBy { it.uri }
            val now = System.currentTimeMillis()
            val sourceTypes = (roots + mediaStoreVolumes).associateWith(store::sourceType)
            var scanned = MediaScanner(this).scan(
                roots,
                mediaStoreVolumes,
                sourceTypes,
            ).map { item ->
                carryCachedMetadata(item, previous[item.uri], now)
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
        now: Long,
    ): MediaRecord {
        if (previous == null) {
            return scanned.copy(addedAt = now)
        }

        if (previous.fileName != scanned.fileName || previous.modifiedAt != scanned.modifiedAt) {
            return scanned.copy(addedAt = previous.addedAt.takeIf { it > 0L } ?: now)
        }

        return scanned.copy(
            addedAt = previous.addedAt.takeIf { it > 0L } ?: now,
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
            Section.HOME -> homeCards()
            Section.MOVIES -> movieCards()
            Section.TV -> tvCards()
            Section.VIDEOS -> videoCards()
            Section.CONTINUE -> continueCards()
            Section.RECENT -> recentCards()
        }
        adapter.submitItems(visibleCards)

        statusView.text = when {
            store.roots().isEmpty() && store.mediaStoreVolumes().isEmpty() ->
                "No media sources configured. Choose a folder or storage volume to begin."
            library.isEmpty() ->
                "No supported video files found."
            visibleCards.isEmpty() ->
                when (section) {
                    Section.HOME -> "Your library is empty."
                    Section.MOVIES -> "No movies found."
                    Section.TV -> "No TV episodes found."
                    Section.VIDEOS -> "No ordinary videos found."
                    Section.CONTINUE -> "Nothing to continue watching."
                    Section.RECENT -> "No recently added videos yet."
                }
            else -> {
                val matched = library.count { it.metadataId != null }
                val label = when (section) {
                    Section.HOME -> "home item"
                    Section.MOVIES -> "movie"
                    Section.TV -> "show"
                    Section.VIDEOS -> "video"
                    Section.CONTINUE -> "video"
                    Section.RECENT -> "recent item"
                }
                val suffix = if (visibleCards.size == 1) "" else "s"
                visibleCards.size.toString() + " " + label + suffix +
                    (if (searchQuery.isNotBlank()) " • Search: " + searchQuery else "") +
                    (if (store.tmdbToken().isNotBlank()) " • " + matched + " files matched" else "")
            }
        }
    }

    private fun homeCards(): List<LibraryCard> {
        val continueItems = continueCards().take(12)
        val recentItems = recentCards().take(12)
        val unwatchedMovies = movieCards().filter { card ->
            card.items.firstOrNull()?.let { !store.isWatched(it.uri) } == true
        }.take(12)

        return (continueItems + recentItems + unwatchedMovies)
            .distinctBy { it.items.firstOrNull()?.uri ?: it.key }
    }

    private fun movieCards(): List<LibraryCard> {
        val matching = library
            .filter { it.kind == MediaRecord.Kind.MOVIE }
            .filter { matchesSearch(it) }

        val sorted = when (movieSort) {
            MovieSort.TITLE -> matching.sortedBy { it.displayTitle.lowercase() }
            MovieSort.RECENT -> matching.sortedByDescending { it.addedAt }
            MovieSort.YEAR -> matching.sortedWith(
                compareByDescending<MediaRecord> { it.year ?: Int.MIN_VALUE }
                    .thenBy { it.displayTitle.lowercase() }
            )
            MovieSort.UNWATCHED -> matching.sortedWith(
                compareBy<MediaRecord> { store.isWatched(it.uri) }
                    .thenBy { it.displayTitle.lowercase() }
            )
        }

        return sorted.map { item ->
                LibraryCard(
                    key = item.uri,
                    title = item.displayTitle,
                    subtitle = listOfNotNull(
                        item.year?.toString(),
                        if (store.isWatched(item.uri)) "Watched" else null,
                    ).joinToString(" • "),
                    posterPath = item.posterPath,
                    items = listOf(item),
                )
            }
    }

    private fun matchesSearch(item: MediaRecord): Boolean {
        if (searchQuery.isBlank()) return true
        val query = searchQuery.lowercase()
        return item.displayTitle.lowercase().contains(query) ||
            item.fileName.lowercase().contains(query) ||
            item.episodeTitle?.lowercase()?.contains(query) == true
    }

    private fun showBrowseOptions() {
        val labels = when (section) {
            Section.MOVIES -> arrayOf("Sort movies", "Jump A–Z")
            Section.TV, Section.VIDEOS -> arrayOf("Jump A–Z")
            else -> emptyArray()
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "No additional browsing options here.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Browse")
            .setItems(labels) { _, which ->
                if (section == Section.MOVIES && which == 0) showMovieSort()
                else showAlphabetJump()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showMovieSort() {
        val labels = arrayOf("Title A–Z", "Recently added", "Year", "Unwatched first")
        AlertDialog.Builder(this)
            .setTitle("Sort movies")
            .setSingleChoiceItems(labels, movieSort.ordinal) { dialog, which ->
                movieSort = MovieSort.entries[which]
                renderLibrary()
                dialog.dismiss()
                gridView.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAlphabetJump() {
        val labels = (listOf("#") + ('A'..'Z').map { it.toString() }).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Jump to title")
            .setItems(labels) { _, which ->
                val target = labels[which]
                val index = visibleCards.indexOfFirst { card ->
                    val first = card.title.trim().firstOrNull()?.uppercaseChar()
                    if (target == "#") first == null || first !in 'A'..'Z'
                    else first?.toString() == target
                }
                if (index >= 0) {
                    gridView.setSelection(index)
                    gridView.requestFocus()
                } else {
                    Toast.makeText(this, "No titles under " + target, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun tvCards(): List<LibraryCard> =
        library
            .filter { it.kind == MediaRecord.Kind.TV_EPISODE }
            .filter { matchesSearch(it) }
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

                val watchedCount = ordered.count { store.isWatched(it.uri) }
                val watchedText = if (watchedCount > 0) {
                    " • " + watchedCount + "/" + ordered.size + " watched"
                } else {
                    ""
                }

                LibraryCard(
                    key = representative.metadataId?.toString() ?: representative.title,
                    title = representative.metadataTitle ?: representative.title,
                    subtitle = ordered.size.toString() + " episode" +
                        (if (ordered.size == 1) "" else "s") + seasonText + watchedText,
                    posterPath = representative.posterPath,
                    items = ordered,
                )
            }
            .sortedBy { it.title.lowercase() }

    private fun videoCards(): List<LibraryCard> =
        library
            .filter { it.kind == MediaRecord.Kind.VIDEO || it.kind == MediaRecord.Kind.UNKNOWN }
            .filter { matchesSearch(it) }
            .groupBy { item ->
                val folder = item.relativePath
                    ?.trim('/')
                    ?.substringBefore('/')
                    ?.takeIf { it.isNotBlank() }
                item.sourceId.orEmpty() + "|" + (folder ?: "")
            }
            .values
            .map { items ->
                val ordered = items.sortedBy { it.displayTitle.lowercase() }
                val representative = ordered.first()
                val folder = representative.relativePath
                    ?.trim('/')
                    ?.substringBefore('/')
                    ?.takeIf { it.isNotBlank() }
                val label = folder ?: representative.sourceId?.let(::sourceLabel) ?: "Videos"

                if (ordered.size == 1 && folder == null) {
                    LibraryCard(
                        key = "video:" + representative.uri,
                        title = representative.displayTitle,
                        subtitle = if (store.isWatched(representative.uri)) "Watched" else "",
                        posterPath = representative.posterPath,
                        items = ordered,
                    )
                } else {
                    val watched = ordered.count { store.isWatched(it.uri) }
                    LibraryCard(
                        key = "videofolder:" + representative.sourceId.orEmpty() + ":" + label,
                        title = label,
                        subtitle = ordered.size.toString() + " videos" +
                            (if (watched > 0) " • " + watched + " watched" else ""),
                        posterPath = representative.posterPath,
                        items = ordered,
                    )
                }
            }
            .sortedBy { it.title.lowercase() }

    private fun recentCards(): List<LibraryCard> =
        library
            .filter { it.addedAt > 0L }
            .filter { matchesSearch(it) }
            .sortedByDescending { it.addedAt }
            .take(30)
            .map { item ->
                LibraryCard(
                    key = "recent:" + item.uri,
                    title = item.displayTitle,
                    subtitle = recentSubtitle(item),
                    posterPath = item.posterPath,
                    items = listOf(item),
                )
            }

    private fun recentSubtitle(item: MediaRecord): String {
        val watched = if (store.isWatched(item.uri)) "Watched" else "New"
        return if (item.kind == MediaRecord.Kind.TV_EPISODE) {
            item.episodeDisplayTitle + " • " + watched
        } else {
            watched
        }
    }

    private fun continueCards(): List<LibraryCard> =
        library
            .filter { matchesSearch(it) }
            .mapNotNull { item ->
                val position = store.playbackPosition(item.uri)
                if (position <= 30_000L || store.isWatched(item.uri)) {
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
        when {
            card.key.startsWith("videofolder:") -> showVideoFolder(card)
            card.items.size == 1 -> showDetails(card)
            else -> showEpisodePicker(card)
        }
    }

    private fun showVideoFolder(card: LibraryCard) {
        val ordered = card.items.sortedBy { it.displayTitle.lowercase() }
        val labels = ordered.map { item ->
            val position = store.playbackPosition(item.uri)
            val resume = if (position > 30_000L && !store.isWatched(item.uri)) {
                " • Resume " + formatPosition(position)
            } else {
                ""
            }
            val watched = if (store.isWatched(item.uri)) " • Watched" else ""
            item.displayTitle + resume + watched
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(card.title)
            .setItems(labels) { _, which ->
                val item = ordered[which]
                showDetails(
                    LibraryCard(
                        key = "video:" + item.uri,
                        title = item.displayTitle,
                        subtitle = item.relativePath.orEmpty(),
                        posterPath = item.posterPath,
                        items = listOf(item),
                    )
                )
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showEpisodePicker(card: LibraryCard) {
        val seasons = card.items.groupBy { it.season ?: 0 }.toSortedMap()
        if (seasons.size <= 1) {
            showSeasonEpisodes(card.title, seasons.values.firstOrNull().orEmpty())
            return
        }

        val labels = seasons.map { (season, episodes) ->
            val watched = episodes.count { store.isWatched(it.uri) }
            val name = if (season > 0) "Season " + season else "Other episodes"
            name + " • " + episodes.size + " episode" +
                (if (episodes.size == 1) "" else "s") +
                (if (watched > 0) " • " + watched + " watched" else "")
        }.toTypedArray()
        val entries = seasons.entries.toList()

        AlertDialog.Builder(this)
            .setTitle(card.title)
            .setItems(labels) { _, which ->
                val entry = entries[which]
                val seasonName = if (entry.key > 0) {
                    card.title + " — Season " + entry.key
                } else {
                    card.title
                }
                showSeasonEpisodes(seasonName, entry.value)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSeasonEpisodes(title: String, episodes: List<MediaRecord>) {
        val ordered = episodes.sortedWith(
            compareBy<MediaRecord> { it.episode ?: Int.MAX_VALUE }
                .thenBy { it.fileName.lowercase() }
        )
        val labels = ordered.map { episode ->
            val position = store.playbackPosition(episode.uri)
            val resume = if (position > 30_000L && !store.isWatched(episode.uri)) {
                " • Resume " + formatPosition(position)
            } else {
                ""
            }
            val watched = if (store.isWatched(episode.uri)) " • Watched" else ""
            episode.episodeDisplayTitle + resume + watched
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, which -> showEpisodeDetails(ordered[which]) }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showEpisodeDetails(episode: MediaRecord) {
        val summary = episode.episodeOverview?.takeIf { it.isNotBlank() }
            ?: episode.overview?.takeIf { it.isNotBlank() }
            ?: "No online description is available for this episode."

        val watched = store.isWatched(episode.uri)
        AlertDialog.Builder(this)
            .setTitle(episode.episodeDisplayTitle)
            .setMessage(summary)
            .setPositiveButton("Play") { _, _ -> play(episode) }
            .setNeutralButton(if (watched) "Mark unwatched" else "Mark watched") { _, _ ->
                store.setWatched(episode.uri, !watched)
                renderLibrary()
            }
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
            .setPositiveButton(if (card.items.size == 1) {
                val position = store.playbackPosition(card.items.first().uri)
                if (position > 30_000L && !store.isWatched(card.items.first().uri)) "Resume" else "Play"
            } else {
                "Episodes"
            }) { _, _ ->
                if (card.items.size == 1) play(card.items.first()) else showEpisodePicker(card)
            }
            .setNegativeButton("Close", null)

        if (card.items.size == 1) {
            val item = card.items.first()
            val watched = store.isWatched(item.uri)
            if (store.tmdbToken().isNotBlank()) {
                builder.setNeutralButton("Actions") { _, _ ->
                    showSingleItemActions(card, watched)
                }
            } else {
                builder.setNeutralButton(if (watched) "Mark unwatched" else "Mark watched") { _, _ ->
                    store.setWatched(item.uri, !watched)
                    renderLibrary()
                }
            }
        } else if (store.tmdbToken().isNotBlank()) {
            builder.setNeutralButton("Fix match") { _, _ -> promptFixMatch(card) }
        }

        builder.show()
    }

    private fun showSingleItemActions(
        card: LibraryCard,
        watched: Boolean,
    ) {
        val labels = arrayOf(
            if (watched) "Mark unwatched" else "Mark watched",
            "Fix match",
        )

        AlertDialog.Builder(this)
            .setTitle(card.title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> {
                        val item = card.items.first()
                        store.setWatched(item.uri, !watched)
                        renderLibrary()
                    }
                    1 -> promptFixMatch(card)
                }
            }
            .setNegativeButton("Close", null)
            .show()
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
        const val REQUEST_VIDEO_PERMISSION = 1002
        const val MEDIASTORE_LEGACY_EXTERNAL = "external"
    }
}
