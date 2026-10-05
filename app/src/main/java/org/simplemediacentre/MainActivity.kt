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
    private var movieFilter = MovieFilter.ALL
    private var movieDecade: Int? = null
    private var tvSort = LibrarySort.TITLE
    private var videoSort = LibrarySort.TITLE

    private enum class MovieSort {
        TITLE,
        RECENT,
        YEAR,
        UNWATCHED,
    }

    private enum class LibrarySort {
        TITLE,
        RECENT,
        UNWATCHED,
    }

    private enum class MovieFilter {
        ALL,
        UNWATCHED,
        WATCHED,
    }

    private data class ConfiguredSource(
        val kind: String,
        val id: String,
        val label: String,
        val volumeName: String? = null,
        val folder: String? = null,
    )

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

    private fun availableMediaStoreVolumes(): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(this)
        } else {
            setOf(MEDIASTORE_LEGACY_EXTERNAL)
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
                chooseMediaStoreFolders(volumes[which])
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

    private fun chooseMediaStoreFolders(volumeName: String) {
        progressView.visibility = View.VISIBLE
        statusView.text = "Finding folders on storage…"

        Thread {
            val folders = MediaScanner(this).discoverMediaStoreFolders(volumeName)
            val selected = store.mediaStoreFolders(volumeName)

            runOnUiThread {
                progressView.visibility = View.GONE
                renderLibrary()

                if (folders.isEmpty()) {
                    store.addMediaStoreVolume(volumeName)
                    chooseSourceType(volumeName)
                    return@runOnUiThread
                }

                val checked = folders.map { it in selected }.toBooleanArray()
                AlertDialog.Builder(this)
                    .setTitle("Choose folders")
                    .setMessage(
                        "Select the folders Simple Media Centre should scan. " +
                            "If none are selected, the whole storage volume is scanned."
                    )
                    .setMultiChoiceItems(folders.toTypedArray(), checked) { _, which, value ->
                        checked[which] = value
                    }
                    .setPositiveButton("Continue") { _, _ ->
                        val chosen = folders.filterIndexed { index, _ -> checked[index] }
                        store.addMediaStoreVolume(volumeName)
                        store.setMediaStoreFolders(volumeName, chosen)
                        if (chosen.isEmpty()) {
                            chooseSourceType(volumeName)
                        } else {
                            chooseFolderTypes(volumeName, chosen, 0)
                        }
                    }
                    .setNeutralButton("Whole volume") { _, _ ->
                        store.addMediaStoreVolume(volumeName)
                        store.setMediaStoreFolders(volumeName, emptySet())
                        chooseSourceType(volumeName)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    private fun chooseFolderTypes(volumeName: String, folders: List<String>, index: Int) {
        if (index >= folders.size) {
            scanLibrary()
            return
        }

        val folder = folders[index]
        val sourceId = store.mediaStoreFolderSourceId(volumeName, folder)
        val types = SourceType.entries.toTypedArray()
        val labels = types.map(SourceType::label).toTypedArray()
        val current = types.indexOf(store.sourceType(sourceId)).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(folder)
            .setMessage("What kind of videos are in this folder?")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                store.setSourceType(sourceId, types[which])
                dialog.dismiss()
                chooseFolderTypes(volumeName, folders, index + 1)
            }
            .setNegativeButton("Automatic") { _, _ ->
                store.setSourceType(sourceId, SourceType.MIXED)
                chooseFolderTypes(volumeName, folders, index + 1)
            }
            .show()
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
        val sources = buildList {
            store.roots().sorted().forEach { root ->
                add(ConfiguredSource("folder", root, sourceLabel(root)))
            }

            store.mediaStoreVolumes().sorted().forEach { volume ->
                val folders = store.mediaStoreFolders(volume).sorted()
                if (folders.isEmpty()) {
                    add(
                        ConfiguredSource(
                            "volume",
                            volume,
                            store.sourceDisplayName(volume) ?: volume,
                            volumeName = volume,
                        )
                    )
                } else {
                    folders.forEach { folder ->
                        add(
                            ConfiguredSource(
                                kind = "mediafolder",
                                id = store.mediaStoreFolderSourceId(volume, folder),
                                label = store.sourceDisplayName(
                                    store.mediaStoreFolderSourceId(volume, folder)
                                ) ?: folder,
                                volumeName = volume,
                                folder = folder,
                            )
                        )
                    }
                }
            }
        }

        if (sources.isEmpty()) {
            Toast.makeText(this, "No media sources configured.", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = sources.map { source ->
            source.label + " • " + store.sourceType(source.id).label
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Media sources")
            .setItems(labels) { _, which ->
                val source = sources[which]
                val actions = if (source.kind == "mediafolder" || source.kind == "volume") {
                    arrayOf("Rename", "Change type", "Choose storage folders", "Remove source")
                } else {
                    arrayOf("Rename", "Change type", "Remove source")
                }

                AlertDialog.Builder(this)
                    .setTitle(source.label)
                    .setItems(actions) { _, action ->
                        when {
                            action == 0 -> renameSource(source)
                            action == 1 -> chooseSourceType(source.id)
                            source.kind in setOf("mediafolder", "volume") && action == 2 ->
                                chooseMediaStoreFolders(source.volumeName ?: source.id)
                            else -> confirmRemoveSource(source)
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
        store.sourceDisplayName(sourceId)
            ?: runCatching {
                android.net.Uri.parse(sourceId).lastPathSegment
                    ?.substringAfterLast(':')
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
            ?: sourceId.substringAfterLast('|').takeIf { it.isNotBlank() }
            ?: "Media folder"

    private fun renameSource(source: ConfiguredSource) {
        val input = EditText(this).apply {
            setText(source.label)
            selectAll()
            hint = "Source name"
            isSingleLine = true
        }

        AlertDialog.Builder(this)
            .setTitle("Rename source")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                store.setSourceDisplayName(source.id, input.text.toString())
                renderLibrary()
            }
            .setNeutralButton("Reset") { _, _ ->
                store.setSourceDisplayName(source.id, "")
                renderLibrary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRemoveSource(source: ConfiguredSource) {
        AlertDialog.Builder(this)
            .setTitle("Remove source?")
            .setMessage("Stop scanning " + source.label + "? Files are not deleted.")
            .setPositiveButton("Remove") { _, _ ->
                when (source.kind) {
                    "folder" -> store.removeRoot(source.id)
                    "volume" -> store.removeMediaStoreVolume(source.id)
                    "mediafolder" -> {
                        val volume = source.volumeName ?: return@setPositiveButton
                        val folder = source.folder ?: return@setPositiveButton
                        val remaining = store.mediaStoreFolders(volume) - folder
                        if (remaining.isEmpty()) {
                            store.removeMediaStoreVolume(volume)
                        } else {
                            store.setMediaStoreFolders(volume, remaining)
                        }
                    }
                }
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
                "Simple Media Centre " + BuildConfig.VERSION_NAME + "\n\n" +
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
            val availableVolumes = availableMediaStoreVolumes()
            val unavailableVolumes = mediaStoreVolumes - availableVolumes
            val scanVolumes = mediaStoreVolumes - unavailableVolumes
            val mediaStoreFolders = mediaStoreVolumes.associateWith(store::mediaStoreFolders)
            val folderSourceIds = mediaStoreFolders.flatMap { (volume, folders) ->
                folders.map { folder -> store.mediaStoreFolderSourceId(volume, folder) }
            }
            val sourceTypes = (roots + mediaStoreVolumes + folderSourceIds)
                .associateWith(store::sourceType)
            val fresh = MediaScanner(this).scan(
                roots,
                scanVolumes,
                sourceTypes,
                mediaStoreFolders,
            ).map { item ->
                carryCachedMetadata(item, previous[item.uri], now)
            }
            val cachedUnavailable = previous.values.filter { item ->
                item.sourceId
                    ?.substringBefore('|')
                    ?.let { it in unavailableVolumes } == true
            }
            var scanned = (fresh + cachedUnavailable)
                .distinctBy(MediaRecord::uri)

            store.saveLibrary(scanned)

            runOnUiThread {
                library = scanned
                progressView.visibility = View.GONE
                renderLibrary()
                if (unavailableVolumes.isNotEmpty()) {
                    statusView.text = statusView.text.toString() +
                        " • " + unavailableVolumes.size + " storage source" +
                        (if (unavailableVolumes.size == 1) "" else "s") +
                        " unavailable; cached items kept"
                }
            }

            val token = store.tmdbToken()
            if (token.isNotBlank()) {
                val provider = TmdbMetadataProvider(token)
                val enricher = LibraryEnricher(provider)
                val working = scanned.toMutableList()
                val indexesByUri = working.indices.associateBy { working[it].uri }
                val candidates = working.filter {
                    it.kind == MediaRecord.Kind.MOVIE || it.kind == MediaRecord.Kind.TV_EPISODE
                }

                runOnUiThread {
                    progressView.visibility = View.VISIBLE
                    statusView.text = "Library ready • matching metadata…"
                }

                candidates.chunked(METADATA_BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                    val enrichedBatch = enricher.enrich(batch)
                    enrichedBatch.forEach { item ->
                        indexesByUri[item.uri]?.let { index -> working[index] = item }
                    }

                    store.saveLibrary(working)

                    val completed = minOf(
                        (batchIndex + 1) * METADATA_BATCH_SIZE,
                        candidates.size,
                    )
                    runOnUiThread {
                        library = working.toList()
                        renderLibrary()
                        statusView.text =
                            "Library ready • metadata " + completed + "/" + candidates.size
                    }
                }

                runOnUiThread {
                    progressView.visibility = View.GONE
                    renderLibrary()
                }
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
                    (if (section == Section.MOVIES && (movieFilter != MovieFilter.ALL || movieDecade != null)) {
                        " • Filtered"
                    } else {
                        ""
                    }) +
                    (if (store.tmdbToken().isNotBlank()) " • " + matched + " files matched" else "")
            }
        }
    }

    private fun homeCards(): List<LibraryCard> {
        val movies = library.filter { it.kind == MediaRecord.Kind.MOVIE }
        val tvEpisodes = library.filter { it.kind == MediaRecord.Kind.TV_EPISODE }
        val videos = library.filter {
            it.kind == MediaRecord.Kind.VIDEO || it.kind == MediaRecord.Kind.UNKNOWN
        }
        val showCount = tvEpisodes
            .groupBy { item ->
                item.metadataId?.let { "tmdb:" + it } ?: "title:" + item.title.lowercase()
            }
            .size

        val destinations = buildList {
            if (movies.isNotEmpty()) {
                add(
                    LibraryCard(
                        key = "nav:movies",
                        title = "Movies",
                        subtitle = movies.size.toString() + " titles",
                        posterPath = movies.firstOrNull { it.posterPath != null }?.posterPath,
                        items = movies.take(1),
                    )
                )
            }
            if (tvEpisodes.isNotEmpty()) {
                add(
                    LibraryCard(
                        key = "nav:tv",
                        title = "TV Shows",
                        subtitle = showCount.toString() + " shows",
                        posterPath = tvEpisodes.firstOrNull { it.posterPath != null }?.posterPath,
                        items = tvEpisodes.take(1),
                    )
                )
            }
            if (videos.isNotEmpty()) {
                add(
                    LibraryCard(
                        key = "nav:videos",
                        title = "Videos",
                        subtitle = videos.size.toString() + " videos",
                        posterPath = videos.firstOrNull { it.posterPath != null }?.posterPath,
                        items = videos.take(1),
                    )
                )
            }
        }

        val continueItems = continueCards().take(8)
        val recentItems = recentCards().take(8)

        return destinations + (continueItems + recentItems)
            .distinctBy { it.items.firstOrNull()?.uri ?: it.key }
    }

    private fun movieCards(): List<LibraryCard> {
        val matching = library
            .filter { it.kind == MediaRecord.Kind.MOVIE }
            .filter { matchesSearch(it) }
            .filter { item ->
                when (movieFilter) {
                    MovieFilter.ALL -> true
                    MovieFilter.UNWATCHED -> !store.isWatched(item.uri)
                    MovieFilter.WATCHED -> store.isWatched(item.uri)
                }
            }
            .filter { item ->
                when (val decade = movieDecade) {
                    null -> true
                    Int.MIN_VALUE -> item.year == null
                    else -> item.year?.let { (it / 10) * 10 == decade } == true
                }
            }

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
            Section.MOVIES -> arrayOf("Sort movies", "Filter movies", "Jump A–Z")
            Section.TV -> arrayOf("Sort TV shows", "Jump A–Z")
            Section.VIDEOS -> arrayOf("Sort videos", "Jump A–Z")
            else -> emptyArray()
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "No additional browsing options here.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Browse")
            .setItems(labels) { _, which ->
                when (section) {
                    Section.MOVIES -> when (which) {
                        0 -> showMovieSort()
                        1 -> showMovieFilter()
                        else -> showAlphabetJump()
                    }
                    Section.TV -> if (which == 0) {
                        showLibrarySort("Sort TV shows", tvSort) { tvSort = it }
                    } else {
                        showAlphabetJump()
                    }
                    Section.VIDEOS -> if (which == 0) {
                        showLibrarySort("Sort videos", videoSort) { videoSort = it }
                    } else {
                        showAlphabetJump()
                    }
                    else -> showAlphabetJump()
                }
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

    private fun showMovieFilter() {
        val decades = library
            .asSequence()
            .filter { it.kind == MediaRecord.Kind.MOVIE }
            .mapNotNull { it.year }
            .map { (it / 10) * 10 }
            .distinct()
            .sortedDescending()
            .toList()

        val labels = buildList {
            add("All movies")
            add("Unwatched")
            add("Watched")
            decades.forEach { add(it.toString() + "s") }
            add("Unknown year")
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Filter movies")
            .setItems(labels) { _, which ->
                when {
                    which == 0 -> {
                        movieFilter = MovieFilter.ALL
                        movieDecade = null
                    }
                    which == 1 -> {
                        movieFilter = MovieFilter.UNWATCHED
                        movieDecade = null
                    }
                    which == 2 -> {
                        movieFilter = MovieFilter.WATCHED
                        movieDecade = null
                    }
                    which in 3 until 3 + decades.size -> {
                        movieFilter = MovieFilter.ALL
                        movieDecade = decades[which - 3]
                    }
                    else -> {
                        movieFilter = MovieFilter.ALL
                        movieDecade = Int.MIN_VALUE
                    }
                }
                renderLibrary()
                gridView.setSelection(0)
                gridView.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLibrarySort(
        title: String,
        current: LibrarySort,
        update: (LibrarySort) -> Unit,
    ) {
        val labels = arrayOf("Title A–Z", "Recently added", "Unwatched first")
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, current.ordinal) { dialog, which ->
                update(LibrarySort.entries[which])
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

    private fun tvCards(): List<LibraryCard> {
        val cards = library
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

        return sortLibraryCards(cards, tvSort)
    }

    private fun videoCards(): List<LibraryCard> {
        val cards =
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
                val label = representative.sourceId
                    ?.let(store::sourceDisplayName)
                    ?: folder
                    ?: representative.sourceId?.let(::sourceLabel)
                    ?: "Videos"

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

        return sortLibraryCards(cards, videoSort)
    }

    private fun sortLibraryCards(
        cards: List<LibraryCard>,
        sort: LibrarySort,
    ): List<LibraryCard> =
        when (sort) {
            LibrarySort.TITLE -> cards.sortedBy { it.title.lowercase() }
            LibrarySort.RECENT -> cards.sortedByDescending { card ->
                card.items.maxOfOrNull { it.addedAt } ?: 0L
            }
            LibrarySort.UNWATCHED -> cards.sortedWith(
                compareBy<LibraryCard> { card -> card.items.all { store.isWatched(it.uri) } }
                    .thenBy { it.title.lowercase() }
            )
        }

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
        when (card.key) {
            "nav:movies" -> openSection(Section.MOVIES)
            "nav:tv" -> openSection(Section.TV)
            "nav:videos" -> openSection(Section.VIDEOS)
            else -> when {
                card.key.startsWith("videofolder:") -> showVideoFolder(card)
                card.items.size == 1 -> showDetails(card)
                else -> showEpisodePicker(card)
            }
        }
    }

    private fun openSection(target: Section) {
        section = target
        searchQuery = ""
        updateSectionButtons()
        renderLibrary()
        gridView.setSelection(0)
        gridView.requestFocus()
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
            ?: localMediaSummary(episode)

        val watched = store.isWatched(episode.uri)
        val position = store.playbackPosition(episode.uri)
        AlertDialog.Builder(this)
            .setTitle(episode.episodeDisplayTitle)
            .setMessage(summary)
            .setPositiveButton(
                if (position > 30_000L && !watched) "Resume" else "Play"
            ) { _, _ -> play(episode) }
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
                    ?: localMediaSummary(representative)
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
            builder.setNeutralButton("Actions") { _, _ ->
                showSingleItemActions(card, watched)
            }
        } else if (store.tmdbToken().isNotBlank()) {
            builder.setNeutralButton("Fix match") { _, _ -> promptFixMatch(card) }
        }

        builder.show()
    }

    private fun localMediaSummary(item: MediaRecord): String {
        val lines = buildList {
            item.year?.let { add("Year: " + it) }
            item.relativePath?.takeIf { it.isNotBlank() }?.let { add("Folder: " + it) }
            item.sourceId?.let { sourceId ->
                val label = sourceLabel(sourceId)
                if (label.isNotBlank()) add("Source: " + label)
            }
            add("File: " + item.fileName)
        }
        return lines.joinToString("\n")
    }

    private fun showSingleItemActions(
        card: LibraryCard,
        watched: Boolean,
    ) {
        val item = card.items.first()
        val canRestart = store.playbackPosition(item.uri) > 30_000L
        val labels = buildList {
            add(if (watched) "Mark unwatched" else "Mark watched")
            if (canRestart) add("Restart from beginning")
            if (store.tmdbToken().isNotBlank() && item.kind != MediaRecord.Kind.VIDEO) {
                add("Fix match")
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(card.title)
            .setItems(labels) { _, which ->
                when (labels[which]) {
                    "Mark watched" -> {
                        store.setWatched(item.uri, true)
                        renderLibrary()
                    }
                    "Mark unwatched" -> {
                        store.setWatched(item.uri, false)
                        renderLibrary()
                    }
                    "Restart from beginning" -> {
                        store.savePlaybackPosition(item.uri, 0L)
                        store.setWatched(item.uri, false)
                        play(item)
                    }
                    "Fix match" -> promptFixMatch(card)
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
        const val METADATA_BATCH_SIZE = 20
    }
}
