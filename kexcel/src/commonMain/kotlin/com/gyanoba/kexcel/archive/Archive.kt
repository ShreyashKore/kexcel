package com.gyanoba.kexcel.archive

/**
 * A collection of files.
 *
 * Ported from the Dart `archive` package's `Archive`. This is a pure in-memory
 * collection of [ArchiveFile]s; decoding a zip/tar into an [Archive] is a
 * separate concern (see the zip reader in `com.gyanoba.kexcel.utils`).
 */
internal class Archive : Iterable<ArchiveFile> {
    private val _files = mutableListOf<ArchiveFile>()
    private val _fileMap = mutableMapOf<String, Int>()

    /** A global comment for the archive. */
    var comment: String? = null

    /** Read-only view of the files in the archive. */
    val files: List<ArchiveFile> get() = _files

    /**
     * Add a file or directory to the archive. Adding a file with the same path
     * as one that's already in the archive will replace the previous file.
     */
    fun add(file: ArchiveFile) {
        val index = _fileMap[file.name]
        if (index != null) {
            _files[index] = file
            return
        }
        _files.add(file)
        _fileMap[file.name] = _files.size - 1
    }

    fun modifyAtIndex(index: Int, file: ArchiveFile) {
        _files[index] = file
    }

    /** Alias for [add] for backwards compatibility. */
    fun addFile(file: ArchiveFile) = add(file)

    fun removeFile(file: ArchiveFile) {
        val index = _fileMap[file.name] ?: return
        _files.removeAt(index)
        _fileMap.remove(file.name)
        // Indexes have changed, update the file map.
        updateFileMap()
    }

    fun removeAt(index: Int) {
        if (index < 0 || index >= _files.size) return
        _fileMap.remove(_files[index].name)
        _files.removeAt(index)
        // Indexes have changed, update the file map.
        updateFileMap()
    }

    fun clear() {
        for (fp in _files) fp.close()
        _files.clear()
        _fileMap.clear()
        comment = null
    }

    fun clearSync() {
        for (fp in _files) fp.closeSync()
        _files.clear()
        _fileMap.clear()
        comment = null
    }

    /** The number of files in the archive. */
    val length: Int get() = _files.size

    /** Get a file from the archive. */
    operator fun get(index: Int): ArchiveFile = _files[index]

    /** Set a file in the archive. */
    operator fun set(index: Int, file: ArchiveFile) {
        if (index < 0 || index >= _files.size) return
        _fileMap.remove(_files[index].name)
        _files[index] = file
        _fileMap[file.name] = index
    }

    /**
     * Find a file with the given [name] in the archive. If the file isn't found,
     * null will be returned.
     */
    fun find(name: String): ArchiveFile? {
        val index = _fileMap[name] ?: return null
        return _files[index]
    }

    /** Alias for [find], for backwards compatibility. */
    fun findFile(name: String): ArchiveFile? = find(name)

    /** The number of files in the archive. */
    fun numberOfFiles(): Int = _files.size

    /** The name of the file at the given [index]. */
    fun fileName(index: Int): String = _files[index].name

    /** The decompressed size of the file at the given [index]. */
    fun fileSize(index: Int): Int = _files[index].size

    /** The decompressed data of the file at the given [index]. */
    fun fileData(index: Int): ByteArray = _files[index].content

    val first: ArchiveFile get() = _files.first()

    val last: ArchiveFile get() = _files.last()

    val isEmpty: Boolean get() = _files.isEmpty()

    /** Returns true if there is at least one element in this collection. */
    val isNotEmpty: Boolean get() = _files.isNotEmpty()

    override fun iterator(): Iterator<ArchiveFile> = _files.iterator()

    private fun updateFileMap() {
        _fileMap.clear()
        for (i in _files.indices) {
            _fileMap[_files[i].name] = i
        }
    }
}
