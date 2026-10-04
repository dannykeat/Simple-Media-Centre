package org.simplemediacentre.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import org.simplemediacentre.model.MediaRecord

class MediaScanner(private val context: Context) {
    private val videoExtensions = setOf(
        "mkv", "mp4", "m4v", "avi", "mov", "webm", "mpeg", "mpg", "ts", "m2ts"
    )

    fun scan(
        rootUris: Collection<String>,
        mediaStoreVolumes: Collection<String> = emptyList(),
    ): List<MediaRecord> {
        val results = mutableListOf<MediaRecord>()

        rootUris.forEach { rawUri ->
            val root = DocumentFile.fromTreeUri(context, Uri.parse(rawUri)) ?: return@forEach
            scanDirectory(root, results)
        }

        mediaStoreVolumes.forEach { volumeName ->
            scanMediaStoreVolume(volumeName, results)
        }

        return results
            .distinctBy(MediaRecord::uri)
            .sortedWith(
                compareBy<MediaRecord> { it.title.lowercase() }
                    .thenBy { it.season ?: -1 }
                    .thenBy { it.episode ?: -1 }
            )
    }

    private fun scanDirectory(directory: DocumentFile, results: MutableList<MediaRecord>) {
        val children = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            return
        }

        children.forEach { file ->
            when {
                file.isDirectory -> scanDirectory(file, results)
                file.isFile && isVideo(file) -> {
                    val name = file.name ?: return@forEach
                    val parsed = FilenameParser.parse(name)
                    results += MediaRecord(
                        uri = file.uri.toString(),
                        fileName = name,
                        title = parsed.title,
                        kind = parsed.kind,
                        year = parsed.year,
                        season = parsed.season,
                        episode = parsed.episode,
                        modifiedAt = file.lastModified(),
                    )
                }
            }
        }
    }

    private fun scanMediaStoreVolume(
        volumeName: String,
        results: MutableList<MediaRecord>,
    ) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(volumeName)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_MODIFIED,
        )

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val modifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    if (!hasSupportedExtension(name)) continue

                    val parsed = FilenameParser.parse(name)
                    results += MediaRecord(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        fileName = name,
                        title = parsed.title,
                        kind = parsed.kind,
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
