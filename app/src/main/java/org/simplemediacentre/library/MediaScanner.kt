package org.simplemediacentre.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import org.simplemediacentre.model.MediaRecord
import org.simplemediacentre.model.SourceType

class MediaScanner(private val context: Context) {
    private val videoExtensions = setOf(
        "mkv", "mp4", "m4v", "avi", "mov", "webm", "mpeg", "mpg", "ts", "m2ts"
    )

    fun scan(
        rootUris: Collection<String>,
        mediaStoreVolumes: Collection<String> = emptyList(),
        sourceTypes: Map<String, SourceType> = emptyMap(),
        mediaStoreFolders: Map<String, Set<String>> = emptyMap(),
    ): List<MediaRecord> {
        val results = mutableListOf<MediaRecord>()

        rootUris.forEach { rawUri ->
            val root = DocumentFile.fromTreeUri(context, Uri.parse(rawUri)) ?: return@forEach
            scanDirectory(
                directory = root,
                results = results,
                sourceType = sourceTypes[rawUri] ?: SourceType.MIXED,
                sourceId = rawUri,
                relativePath = "",
            )
        }

        mediaStoreVolumes.forEach { volumeName ->
            scanMediaStoreVolume(
                volumeName = volumeName,
                results = results,
                volumeSourceType = sourceTypes[volumeName] ?: SourceType.MIXED,
                selectedFolders = mediaStoreFolders[volumeName].orEmpty(),
                sourceTypes = sourceTypes,
            )
        }

        return results
            .distinctBy(MediaRecord::uri)
            .sortedWith(
                compareBy<MediaRecord> { it.title.lowercase() }
                    .thenBy { it.season ?: -1 }
                    .thenBy { it.episode ?: -1 }
            )
    }

    private fun scanDirectory(
        directory: DocumentFile,
        results: MutableList<MediaRecord>,
        sourceType: SourceType,
        sourceId: String,
        relativePath: String,
    ) {
        val children = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            return
        }

        children.forEach { file ->
            when {
                file.isDirectory -> {
                    val childPath = if (relativePath.isBlank()) {
                        file.name.orEmpty()
                    } else {
                        relativePath + "/" + file.name.orEmpty()
                    }
                    scanDirectory(
                        directory = file,
                        results = results,
                        sourceType = sourceType,
                        sourceId = sourceId,
                        relativePath = childPath,
                    )
                }

                file.isFile && isVideo(file) -> {
                    val name = file.name ?: return@forEach
                    val parsed = FilenameParser.parse(name)
                    results += MediaRecord(
                        uri = file.uri.toString(),
                        fileName = name,
                        title = parsed.title,
                        kind = kindFor(parsed.kind, sourceType),
                        sourceId = sourceId,
                        relativePath = relativePath.takeIf { it.isNotBlank() },
                        year = parsed.year,
                        season = parsed.season,
                        episode = parsed.episode,
                        modifiedAt = file.lastModified(),
                    )
                }
            }
        }
    }

    fun discoverMediaStoreFolders(volumeName: String): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val collection = MediaStore.Video.Media.getContentUri(volumeName)
        val folders = linkedSetOf<String>()

        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Video.Media.RELATIVE_PATH),
                null,
                null,
                null,
            )?.use { cursor ->
                val pathColumn = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                if (pathColumn < 0) return emptyList()

                while (cursor.moveToNext()) {
                    cursor.getString(pathColumn)
                        ?.trim('/')
                        ?.takeIf { it.isNotBlank() }
                        ?.let(folders::add)
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }

        return folders.sortedWith(
            compareBy<String> { path -> path.count { it == '/' } }
                .thenBy { it.lowercase() }
        )
    }

    private fun scanMediaStoreVolume(
        volumeName: String,
        results: MutableList<MediaRecord>,
        volumeSourceType: SourceType,
        selectedFolders: Set<String>,
        sourceTypes: Map<String, SourceType>,
    ) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(volumeName)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val modifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    if (!hasSupportedExtension(name)) continue

                    val relativePath = if (relativePathColumn >= 0) {
                        cursor.getString(relativePathColumn)
                            ?.trim('/')
                            ?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }

                    val selectedFolder =
                        MediaPathRules.selectedFolder(relativePath, selectedFolders)

                    if (selectedFolders.isNotEmpty() && selectedFolder == null) continue

                    val sourceId = selectedFolder
                        ?.let { MediaPathRules.sourceId(volumeName, it) }
                        ?: volumeName
                    val sourceType = sourceTypes[sourceId] ?: volumeSourceType
                    val displayRelativePath =
                        MediaPathRules.relativeWithinFolder(relativePath, selectedFolder)

                    val parsed = FilenameParser.parse(name)
                    results += MediaRecord(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        fileName = name,
                        title = parsed.title,
                        kind = kindFor(parsed.kind, sourceType),
                        sourceId = sourceId,
                        relativePath = displayRelativePath,
                        year = parsed.year,
                        season = parsed.season,
                        episode = parsed.episode,
                        modifiedAt = cursor.getLong(modifiedColumn) * 1000L,
                    )
                }
            }
        } catch (_: SecurityException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
    }

    private fun kindFor(
        parsed: MediaRecord.Kind,
        sourceType: SourceType,
    ): MediaRecord.Kind =
        when (sourceType) {
            SourceType.MOVIES -> MediaRecord.Kind.MOVIE
            SourceType.TV_SHOWS -> MediaRecord.Kind.TV_EPISODE
            SourceType.VIDEOS -> MediaRecord.Kind.VIDEO
            SourceType.MIXED -> parsed
        }

    private fun isVideo(file: DocumentFile): Boolean {
        if (file.type?.startsWith("video/") == true) return true
        return hasSupportedExtension(file.name.orEmpty())
    }

    private fun hasSupportedExtension(fileName: String): Boolean {
        val extension = fileName
            .substringAfterLast('.', "")
            .lowercase()
        return extension in videoExtensions
    }
}
