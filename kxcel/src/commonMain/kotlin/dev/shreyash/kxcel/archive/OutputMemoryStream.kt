package dev.shreyash.kxcel.archive

/**
 * A byte buffer for writing.
 *
 * Ported from the Dart `archive` package's `OutputMemoryStream`.
 */
internal class OutputMemoryStream(
    size: Int? = DEFAULT_BUFFER_SIZE,
    byteOrder: ByteOrder = ByteOrder.LittleEndian,
) : OutputStream(byteOrder) {

    override var length: Int = 0

    private var _buffer: ByteArray = ByteArray(size ?: DEFAULT_BUFFER_SIZE)

    companion object {
        const val DEFAULT_BUFFER_SIZE = 0x8000 // 32k block-size
    }

    override fun flush() {}

    /** Get the resulting bytes from the buffer. */
    override fun getBytes(): ByteArray = _buffer.copyOfRange(0, length)

    /** Clear the buffer. */
    override fun clear() {
        length = 0
    }

    /** Reset the buffer. */
    fun reset() {
        length = 0
    }

    /** Write a byte to the end of the buffer. */
    override fun writeByte(value: Int) {
        if (length == _buffer.size) {
            expandBuffer()
        }
        _buffer[length++] = value.toByte()
    }

    /** Write a set of bytes to the end of the buffer. */
    override fun writeBytes(bytes: ByteArray, length: Int?) {
        val count = length ?: bytes.size
        while (this.length + count > _buffer.size) {
            expandBuffer((this.length + count) - _buffer.size)
        }
        bytes.copyInto(_buffer, this.length, 0, count)
        this.length += count
    }

    override fun writeStream(stream: InputStream) {
        val bytes = stream.toUint8List()
        val count = bytes.size
        while (length + count > _buffer.size) {
            expandBuffer((length + count) - _buffer.size)
        }
        bytes.copyInto(_buffer, length, 0, count)
        length += count
    }

    /**
     * Return the subset of the buffer in the range [[start]:[end]].
     *
     * If [start] or [end] are < 0 then it is relative to the end of the buffer.
     * If [end] is not specified (or null), then it is the end of the buffer.
     */
    override fun subset(start: Int, end: Int?): ByteArray {
        val s = if (start < 0) length + start else start
        val e = when {
            end == null -> length
            end < 0 -> length + end
            else -> end
        }
        return _buffer.copyOfRange(s, e)
    }

    /** Grow the buffer to accommodate additional data. */
    private fun expandBuffer(required: Int? = null) {
        var blockSize = DEFAULT_BUFFER_SIZE
        if (required != null && required > DEFAULT_BUFFER_SIZE) {
            blockSize = required
        }
        val newLength = (_buffer.size + blockSize) * 2
        val newBuffer = ByteArray(newLength)
        _buffer.copyInto(newBuffer, 0, 0, _buffer.size)
        _buffer = newBuffer
    }
}
