package org.simplemediacentre.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.simplemediacentre.model.MediaRecord

class MediaScanner(private val context: Context) {
    private val videoExtensions = setOf(
        "mkv", "mp4", "m4v", "avi", "mov", "webm", "mpeg", "mpg", "ts", "m2ts"
    )

    fun scan(rootUris: Collection<String>): List<MediaRecord> {
        val results = mutableListOf<MediaRecord>()

        rootUris.forEach { rawUri ->
            val root = DocumentFile.fromTreeUri(context, Uri.parse(rawUri)) ?: return@forEach
            scanDirectory(root, results)
        }

        return results
            .distinctBy(MediaRecord::uri)
            .sortedWith(compareBy<MediaRecord> { it.title.lowercase() }
                .thenBy { it.season ?: -1 }
                .thenBy { it.episode ?: -1 })
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
                        season = parsed.season,
                        episode = parsed.episode,
                        modifiedAt = file.lastModified(),
                    )
                }
            }
        }
    }

    private fun isVideo(file: DocumentFile): Boolean {
        if (file.type?.startsWith("video/") == true) return true
        val extension = file.name
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        return extension in videoExtensions
    }
}
