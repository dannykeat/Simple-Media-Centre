package org.simplemediacentre.library

object MediaPathRules {
    fun normalize(path: String?): String? =
        path?.trim()?.trim('/')?.takeIf { it.isNotBlank() }

    fun selectedFolder(
        relativePath: String?,
        selectedFolders: Collection<String>,
    ): String? {
        val path = normalize(relativePath) ?: return null
        return selectedFolders
            .mapNotNull(::normalize)
            .filter { folder -> path == folder || path.startsWith(folder + "/") }
            .maxByOrNull(String::length)
    }

    fun sourceId(volumeName: String, folder: String): String =
        volumeName + "|" + normalize(folder).orEmpty()

    fun relativeWithinFolder(relativePath: String?, selectedFolder: String?): String? {
        val path = normalize(relativePath) ?: return null
        val folder = normalize(selectedFolder) ?: return path
        if (path != folder && !path.startsWith(folder + "/")) return path
        return path.removePrefix(folder).trim('/').takeIf { it.isNotBlank() }
    }
}
