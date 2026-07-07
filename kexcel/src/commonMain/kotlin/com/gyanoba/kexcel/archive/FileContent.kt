package com.gyanoba.kexcel.archive

/**
 * Used by [ArchiveFile] to abstract the content of a file within an archive,
 * either in memory or as a stream.
 *
 * Ported from the Dart `archive` package's `FileContent`.
 */
internal abstract class FileContent {
    /** The size of the file content in bytes. */
    abstract val length: Int

    /** Get the [InputStream] for reading the file content. */
    abstract fun getStream(decompress: Boolean = true): InputStream

    /** Write the contents of the file to the given [output]. */
    abstract fun write(output: OutputStream)

    /** Close the file content. */
    abstract fun close()

    /** Close the file content synchronously. */
    abstract fun closeSync()

    /** Read the file content into memory and return the read bytes. */
    open fun readBytes(): ByteArray = getStream().toUint8List()

    /**
     * Decompress the file content and write out the decompressed bytes to the
     * [output] stream.
     */
    open fun decompress(output: OutputStream) {
        output.writeStream(getStream())
    }

    /** True if the file content is compressed. */
    open val isCompressed: Boolean get() = false
}

/** A [FileContent] that is resident in memory. */
internal class FileContentMemory(data: ByteArray) : FileContent() {
    var bytes: ByteArray? = data

    override val length: Int get() = bytes?.size ?: 0

    override fun getStream(decompress: Boolean): InputStream =
        InputMemoryStream(bytes ?: ByteArray(0))

    override fun write(output: OutputStream) {
        bytes?.let { output.writeBytes(it) }
    }

    override fun close() {
        bytes = null
    }

    override fun closeSync() {
        bytes = null
    }
}

/** A [FileContent] that is backed by a stream. */
internal class FileContentStream(val stream: InputStream) : FileContent() {
    override val length: Int get() = stream.length

    override fun getStream(decompress: Boolean): InputStream = stream

    override fun write(output: OutputStream) = output.writeStream(stream)

    override fun close() = stream.close()

    override fun closeSync() = stream.closeSync()
}
