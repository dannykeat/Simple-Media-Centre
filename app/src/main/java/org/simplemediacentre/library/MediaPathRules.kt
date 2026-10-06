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

    fun usefulFolderChoices(relativePaths: Collection<String>): List<String> {
        val normalized = relativePaths.mapNotNull(::normalize)
        if (normalized.isEmpty()) return emptyList()

        val directCounts = normalized.groupingBy { it }.eachCount()
        val unique = normalized.toSet()
        val choices = linkedSetOf<String>()

        unique.forEach { path ->
            val parts = path.split('/').filter { it.isNotBlank() }
            if (parts.isEmpty()) return@forEach

            choices += parts.first()

            if (parts.size >= 2) {
                val secondLevel = parts.take(2).joinToString("/")
                val descendantCount = normalized.count { other ->
                    other.startsWith(secondLevel + "/")
                }
                val directCount = directCounts[secondLevel] ?: 0
                if (descendantCount >= 2 || directCount >= 3) {
                    choices += secondLevel
                }
            }
        }

        return choices.sortedWith(
            compareBy<String> { path -> path.count { it == '/' } }
                .thenBy { it.lowercase() }
        )
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
