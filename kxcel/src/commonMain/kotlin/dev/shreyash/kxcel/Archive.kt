package dev.shreyash.kxcel

import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipInputStream

private const val DEFAULT_BUFFER_SIZE = 8192

class Archive(inputStream: ZipInputStream) {

    private val cache = mutableMapOf<String, ArchiveFile>()

    init {
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry

            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = readAllBytes(zis)
                    cache[entry.name] = ArchiveFile(entry.name, entry.size, bytes)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun listEntries(): List<String> = cache.keys.toList()

    fun getEntry(name: String): ArchiveFile? = cache[name]

    fun findFile(name: String): ArchiveFile? = cache[name]

    val files = cache.values.toList()

    private fun readAllBytes(input: ZipInputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(DEFAULT_BUFFER_SIZE)

        var nRead: Int
        while (input.read(data).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }

        return buffer.toByteArray()
    }
}

class ArchiveFile(
    val name: String,
    val size: Long,
    val content: ByteArray
) {

    val isFile: Boolean
        get() = name.isNotEmpty() && !name.endsWith("/")

    fun getBytes(): ByteArray = content
}
