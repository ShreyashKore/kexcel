package dev.shreyash.kxcel.archive

/**
 * Abstract base for writing data into an archive.
 *
 * Ported from the Dart `archive` package's `OutputStream`. Multi-byte writes
 * that can exceed the signed 32-bit range ([writeUint32], [writeUint64]) take
 * [Long] so the full unsigned value can be written.
 */
internal abstract class OutputStream(var byteOrder: ByteOrder) {

    abstract val length: Int

    open fun open() {}

    open fun close() {}

    open fun closeSync() {}

    open val isOpen: Boolean get() = true

    abstract fun clear()

    /** Write any pending data writes to the output. */
    abstract fun flush()

    /** Write a byte to the output stream. */
    abstract fun writeByte(value: Int)

    /** Write a set of bytes to the output stream. */
    abstract fun writeBytes(bytes: ByteArray, length: Int? = null)

    /** Write an [InputStream] to the output stream. */
    abstract fun writeStream(stream: InputStream)

    /** Write a 16-bit word to the output stream. */
    fun writeUint16(value: Int) {
        if (byteOrder == ByteOrder.BigEndian) {
            writeByte((value ushr 8) and 0xff)
            writeByte(value and 0xff)
        } else {
            writeByte(value and 0xff)
            writeByte((value ushr 8) and 0xff)
        }
    }

    /** Write a 32-bit word to the end of the buffer. */
    fun writeUint32(value: Long) {
        if (byteOrder == ByteOrder.BigEndian) {
            writeByte(((value ushr 24) and 0xff).toInt())
            writeByte(((value ushr 16) and 0xff).toInt())
            writeByte(((value ushr 8) and 0xff).toInt())
            writeByte((value and 0xff).toInt())
        } else {
            writeByte((value and 0xff).toInt())
            writeByte(((value ushr 8) and 0xff).toInt())
            writeByte(((value ushr 16) and 0xff).toInt())
            writeByte(((value ushr 24) and 0xff).toInt())
        }
    }

    /** Write a 64-bit word to the end of the buffer. */
    fun writeUint64(value: Long) {
        if (byteOrder == ByteOrder.BigEndian) {
            writeByte(((value ushr 56) and 0xff).toInt())
            writeByte(((value ushr 48) and 0xff).toInt())
            writeByte(((value ushr 40) and 0xff).toInt())
            writeByte(((value ushr 32) and 0xff).toInt())
            writeByte(((value ushr 24) and 0xff).toInt())
            writeByte(((value ushr 16) and 0xff).toInt())
            writeByte(((value ushr 8) and 0xff).toInt())
            writeByte((value and 0xff).toInt())
        } else {
            writeByte((value and 0xff).toInt())
            writeByte(((value ushr 8) and 0xff).toInt())
            writeByte(((value ushr 16) and 0xff).toInt())
            writeByte(((value ushr 24) and 0xff).toInt())
            writeByte(((value ushr 32) and 0xff).toInt())
            writeByte(((value ushr 40) and 0xff).toInt())
            writeByte(((value ushr 48) and 0xff).toInt())
            writeByte(((value ushr 56) and 0xff).toInt())
        }
    }

    abstract fun subset(start: Int, end: Int? = null): ByteArray

    open fun getBytes(): ByteArray = subset(0, length)
}
