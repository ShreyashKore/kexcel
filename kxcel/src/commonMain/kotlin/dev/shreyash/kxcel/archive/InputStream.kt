package dev.shreyash.kxcel.archive

/**
 * Abstract base for reading data out of an archive.
 *
 * Ported from the Dart `archive` package's `InputStream`. Multi-byte reads
 * that can exceed the signed 32-bit range ([readUint32], [readUint64]) return
 * [Long] so the full unsigned value is preserved.
 */
internal abstract class InputStream(
    /** The current endian order of the stream. */
    var byteOrder: ByteOrder,
) {
    /** The current read position relative to the start of the buffer. */
    abstract var position: Int

    /** How many bytes are left in the stream. */
    abstract val length: Int

    /** Is the current position at the end of the stream? */
    abstract val isEOS: Boolean

    abstract fun open(): Boolean

    /** Closes the input stream. */
    abstract fun close()

    /** Synchronously closes the input stream. */
    abstract fun closeSync()

    /** Reset to the beginning of the stream. */
    abstract fun reset()

    /** Rewind the read head of the stream by the given number of bytes. */
    abstract fun rewind(length: Int = 1)

    /** Move the read position by [length] bytes. */
    abstract fun skip(length: Int)

    /**
     * Read [count] bytes from an [offset] of the current read position, without
     * moving the read position.
     */
    fun peekBytes(count: Int, offset: Int = 0): InputStream =
        subset(position = position + offset, length = count)

    /**
     * Return an [InputStream] to read a subset of this stream. It does not move
     * the read position of this stream. [position] is specified relative to the
     * start of the buffer. If [position] is not specified, the current read
     * position is used. If [length] is not specified, the remainder of this
     * stream is used.
     */
    abstract fun subset(position: Int? = null, length: Int? = null, bufferSize: Int? = null): InputStream

    /** Read a single byte. */
    abstract fun readByte(): Int

    /** Read a single byte. */
    fun readUint8(): Int = readByte()

    /** Read a 16-bit word from the stream. */
    fun readUint16(): Int {
        val b1 = readByte()
        val b2 = readByte()
        return if (byteOrder == ByteOrder.BigEndian) (b1 shl 8) or b2 else (b2 shl 8) or b1
    }

    /** Read a 24-bit word from the stream. */
    fun readUint24(): Int {
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        return if (byteOrder == ByteOrder.BigEndian) {
            b3 or (b2 shl 8) or (b1 shl 16)
        } else {
            b1 or (b2 shl 8) or (b3 shl 16)
        }
    }

    /** Read a 32-bit word from the stream. */
    fun readUint32(): Long {
        val b1 = readByte().toLong()
        val b2 = readByte().toLong()
        val b3 = readByte().toLong()
        val b4 = readByte().toLong()
        return if (byteOrder == ByteOrder.BigEndian) {
            (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        } else {
            (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
        }
    }

    /** Read a 64-bit word from the stream. */
    fun readUint64(): Long {
        val b1 = readByte().toLong()
        val b2 = readByte().toLong()
        val b3 = readByte().toLong()
        val b4 = readByte().toLong()
        val b5 = readByte().toLong()
        val b6 = readByte().toLong()
        val b7 = readByte().toLong()
        val b8 = readByte().toLong()
        return if (byteOrder == ByteOrder.BigEndian) {
            (b1 shl 56) or (b2 shl 48) or (b3 shl 40) or (b4 shl 32) or
                (b5 shl 24) or (b6 shl 16) or (b7 shl 8) or b8
        } else {
            (b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or
                (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
        }
    }

    /** Read [count] bytes from the stream. */
    fun readBytes(count: Int): InputStream {
        val bytes = subset(position = position, length = count)
        position = (position + bytes.length)
        return bytes
    }

    /**
     * Read a null-terminated string, or if [size] is provided, that number of
     * bytes returned as a string.
     */
    fun readString(size: Int? = null, utf8: Boolean = true): String {
        fun latin1(codes: ByteArray): String =
            buildString(codes.size) { for (b in codes) append((b.toInt() and 0xff).toChar()) }

        fun codesToString(codes: ByteArray): String =
            try {
                if (utf8) codes.decodeToString(throwOnInvalidSequence = true) else latin1(codes)
            } catch (err: Throwable) {
                latin1(codes)
            }

        if (size == null) {
            if (isEOS) return ""
            val codes = ArrayList<Byte>()
            while (!isEOS) {
                val c = readByte()
                if (c == 0) return codesToString(codes.toByteArray())
                codes.add(c.toByte())
            }
            return codesToString(codes.toByteArray())
        }

        val s = readBytes(size)
        return codesToString(s.toUint8List())
    }

    /** Convert the remaining bytes to a [ByteArray]. */
    abstract fun toUint8List(): ByteArray
}
