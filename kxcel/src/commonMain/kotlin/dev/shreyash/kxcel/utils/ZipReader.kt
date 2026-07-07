package dev.shreyash.kxcel.utils

import dev.shreyash.kxcel.archive.Archive
import dev.shreyash.kxcel.archive.ArchiveFile
import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.io.InputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipInputStream

private const val DEFAULT_BUFFER_SIZE = 8192

/**
 * Reads a zip [inputStream] into an [Archive].
 *
 * This bridges the `kmp-zip` dependency with the ported [Archive] collection.
 * The ported [Archive] is a pure collection and does not decode zips itself
 * (mirroring the Dart `archive` package, where decoding is a separate
 * `ZipDecoder`). Replace this with a native decoder to drop the `kmp-zip`
 * dependency.
 */
internal fun readZipArchive(inputStream: InputStream): Archive {
    val archive = Archive()
    ZipInputStream(inputStream).use { zis ->
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val bytes = readAllBytes(zis)
                archive.addFile(ArchiveFile(entry.name, bytes.size, bytes))
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    return archive
}

private fun readAllBytes(input: ZipInputStream): ByteArray {
    val buffer = ByteArrayOutputStream()
    val data = ByteArray(DEFAULT_BUFFER_SIZE)

    var nRead: Int
    while (input.read(data).also { nRead = it } != -1) {
        buffer.write(data, 0, nRead)
    }

    return buffer.toByteArray()
}
