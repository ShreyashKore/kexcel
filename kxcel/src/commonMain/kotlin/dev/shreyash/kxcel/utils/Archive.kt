package dev.shreyash.kxcel.utils

import dev.shreyash.kxcel.archive.Archive
import dev.shreyash.kxcel.archive.ArchiveFile
import dev.shreyash.kxcel.archive.CompressionType


internal fun cloneArchive(
    archive: Archive,
    archiveFiles: Map<String, ArchiveFile>,
    excludedFile: String? = null
): Archive {
    val clone = Archive()

    archive.files.forEach { file ->
        if (file.isFile) {
            if (excludedFile != null &&
                file.name.equals(excludedFile, ignoreCase = true)
            ) {
                return@forEach
            }

            val copy: ArchiveFile =
                archiveFiles[file.name]
                    ?: run {
                        val content = file.content
                        val compression =
                            if (NO_COMPRESSION.contains(file.name))
                                CompressionType.NONE
                            else
                                CompressionType.DEFLATE

                        ArchiveFile(file.name, content.size, content).apply {
                            this.compression = compression
                        }
                    }

            clone.addFile(copy)
        }
    }

    return clone
}