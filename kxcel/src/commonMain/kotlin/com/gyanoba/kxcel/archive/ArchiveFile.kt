package com.gyanoba.kxcel.archive

import kotlin.time.Clock

/**
 * A callback function called when archive entries are read from or written to
 * archive files like zip or tar.
 */
internal typealias ArchiveCallback = (ArchiveFile) -> Unit

private fun nowSeconds(): Int = (Clock.System.now().toEpochMilliseconds() / 1000).toInt()

/**
 * A file contained in an [Archive].
 *
 * Ported from the Dart `archive` package's `ArchiveFile`. The Dart named
 * constructors are exposed as factory methods on the [companion object]
 * ([bytes], [string], [stream], [file], [symlink], [noData], [directory],
 * [noCompress]). The Dart `close`/`closeSync` async surface is collapsed to
 * synchronous calls since the ported content is always in memory.
 */
internal class ArchiveFile private constructor(
    /** The name of the file or directory. */
    var name: String,
) {
    /** The access mode of the file or directory. */
    var mode: Int = 0x1a4

    /** The owner id of the file or directory. */
    var ownerId: Int = 0

    /** The group id of the file or directory. */
    var groupId: Int = 0

    /** The creation timestamp of the file or directory, in seconds from epoch. */
    var creationTime: Int = nowSeconds()

    /** The timestamp the file or directory was last modified, in seconds from epoch. */
    var lastModTime: Int = nowSeconds()

    /**
     * If not null, the entry is a symbolic link and this is the path to what
     * it's linking to. This should be an archive relative path.
     */
    var symbolicLink: String? = null

    /** True if the entry is a symbolic link, otherwise false. */
    val isSymbolicLink: Boolean get() = symbolicLink?.isNotEmpty() ?: false

    /** The crc32 checksum of the uncompressed content. */
    var crc32: Int? = null

    /** An optional comment for the archive entry. */
    var comment: String? = null

    /**
     * The type of compression to use when encoding an archive. If this is not
     * set, the default compression type will be used. For a zip file, this is
     * deflate compression.
     */
    var compression: CompressionType? = null

    /**
     * The level of compression to use when encoding an archive. If this is not
     * set, the default level of compression will be used.
     */
    var compressionLevel: Int? = null

    private var _rawContent: FileContent? = null
    private var _content: FileContent? = null

    /**
     * The size of the file, in bytes. This is set when decoding an archive, it
     * isn't used for encoding.
     */
    var size: Int = 0

    /** If false, the file represents a directory. */
    var isFile: Boolean = true

    /** If true, the file represents a directory. */
    val isDirectory: Boolean get() = !isFile

    /** The unix permission flags of the file. */
    val unixPermissions: Int get() = mode and 0x1ff

    /** A file storing the given [data]. Alias for [bytes] for backwards compatibility. */
    constructor(name: String, size: Int, data: ByteArray) : this(name) {
        this.size = size
        _content = FileContentMemory(data)
        _rawContent = FileContentMemory(data)
    }

    companion object {
        private val emptyData = ByteArray(0)

        /** A file storing the given [data]. */
        fun bytes(name: String, data: ByteArray): ArchiveFile = ArchiveFile(name).apply {
            _content = FileContentMemory(data)
            _rawContent = FileContentMemory(data)
            size = data.size
        }

        /** A file storing the given string [content]. */
        fun string(name: String, content: String): ArchiveFile = ArchiveFile(name).apply {
            val encoded = content.encodeToByteArray()
            size = encoded.size
            _content = FileContentMemory(encoded)
            _rawContent = FileContentMemory(encoded)
        }

        /** A file that gets its content from the given [stream]. */
        fun stream(name: String, stream: InputStream): ArchiveFile = ArchiveFile(name).apply {
            size = stream.length
            _rawContent = FileContentStream(stream)
        }

        /** A file that gets its content from the given [file]. */
        fun file(name: String, size: Int, file: FileContent): ArchiveFile = ArchiveFile(name).apply {
            this.size = size
            _rawContent = file
        }

        /** A file that's a symlink to another file. */
        fun symlink(name: String, symbolicLink: String): ArchiveFile = ArchiveFile(name).apply {
            this.symbolicLink = symbolicLink
        }

        /** An empty file. */
        fun noData(name: String): ArchiveFile = ArchiveFile(name)

        /** A directory, usually representing an empty directory in an archive. */
        fun directory(name: String): ArchiveFile = ArchiveFile(name).apply {
            isFile = false
        }

        /**
         * Helper factory to define a file storing the given [data], which remains
         * uncompressed in the archive.
         */
        fun noCompress(name: String, size: Int, data: ByteArray): ArchiveFile = ArchiveFile(name).apply {
            this.size = size
            _content = FileContentMemory(data)
            _rawContent = FileContentMemory(data)
            compression = CompressionType.NONE
        }
    }

    /**
     * Write the contents of the file to the given [output]. If [freeMemory] is
     * true, then any storage of decompressed data will be freed after the write
     * has completed.
     */
    fun writeContent(output: OutputStream, freeMemory: Boolean = true) {
        if (_content == null) {
            if (_rawContent == null) return
            decompress(output)
        }

        _content?.write(output)

        if (freeMemory && _content != null) {
            _content!!.closeSync()
            _content = null
        }
    }

    /** Get the content without decompressing it first. */
    val rawContent: FileContent? get() = _rawContent

    /** Get the content of the file, decompressing on demand as necessary. */
    fun getContent(): InputStream? {
        if (_content == null) {
            decompress()
        }
        return _content?.getStream()
    }

    /** Get the decompressed bytes of the file. */
    fun readBytes(): ByteArray? = getContent()?.toUint8List()

    /** The decompressed bytes of the file. */
    val content: ByteArray get() = readBytes() ?: emptyData

    /** Clear the used memory and close the underlying content. */
    fun close() {
        _content?.close()
        if (_rawContent != null) {
            _rawContent!!.close()
            _content = null
        }
    }

    /** Clear the used memory and close the underlying content synchronously. */
    fun closeSync() {
        _content?.closeSync()
        _rawContent?.closeSync()
        _content = null
    }

    /** Clears the memory used without closing the underlying content. */
    fun clear() {
        _rawContent = null
        _content = null
    }

    /**
     * If the file data is compressed, decompress it. Optionally write the
     * decompressed content to [output], otherwise the decompressed content is
     * stored with this [ArchiveFile] in its cached contents.
     */
    fun decompress(output: OutputStream? = null) {
        if (_content != null) {
            if (output != null) {
                output.writeStream(_content!!.getStream())
            }
            return
        }

        if (_rawContent != null) {
            if (output != null) {
                _rawContent!!.decompress(output)
            } else {
                val rawStream = _rawContent!!.getStream()
                val bytes = rawStream.toUint8List()
                _content = FileContentMemory(bytes)
            }
        }
    }

    /** True if the data stored by this file is currently compressed. */
    val isCompressed: Boolean
        get() = _content == null && _rawContent != null && _rawContent!!.isCompressed
}
