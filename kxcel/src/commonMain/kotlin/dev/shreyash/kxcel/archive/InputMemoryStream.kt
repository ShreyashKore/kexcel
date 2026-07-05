package dev.shreyash.kxcel.archive

/**
 * Stream in data from an in-memory buffer.
 *
 * Ported from the Dart `archive` package's `InputMemoryStream`. Instead of
 * relying on typed-data buffer views, this keeps a reference to the underlying
 * [ByteArray] plus a [start] offset so that [subset] can share the buffer
 * without copying.
 */
class InputMemoryStream(
    bytes: ByteArray,
    byteOrder: ByteOrder = ByteOrder.littleEndian,
    offset: Int = 0,
    length: Int? = null,
) : InputStream(byteOrder) {

    private var buffer: ByteArray? = bytes
    private val start: Int = offset

    // The length of the window this stream reads from, starting at [start].
    private var _length: Int = 0

    // The read offset relative to [start].
    private var _position: Int = 0

    init {
        var len = length ?: (bytes.size - offset)
        if ((offset + len) > bytes.size) len = bytes.size - offset
        _length = len
    }

    companion object {
        fun empty(): InputMemoryStream = InputMemoryStream(ByteArray(0))

        fun fromList(bytes: ByteArray, byteOrder: ByteOrder = ByteOrder.littleEndian): InputMemoryStream =
            InputMemoryStream(bytes.copyOf(), byteOrder)

        /** Create a copy of [other] that shares the same buffer. */
        fun from(other: InputMemoryStream): InputMemoryStream =
            InputMemoryStream(other.buffer ?: ByteArray(0), other.byteOrder, other.start, other._length)
                .also { it._position = other._position }
    }

    override var position: Int
        get() = _position
        set(value) {
            _position = value
        }

    override val length: Int
        get() = if (buffer == null) 0 else _length - _position

    override val isEOS: Boolean
        get() = _position >= _length

    override fun reset() {
        _position = 0
    }

    override fun open(): Boolean = true

    override fun close() {
        _position = 0
    }

    override fun closeSync() {
        _position = 0
    }

    override fun rewind(length: Int) {
        _position = (_position - length).coerceIn(0, _length)
    }

    override fun skip(length: Int) {
        _position = (_position + length).coerceIn(0, _length)
    }

    /** Access the buffer relative to the current position. */
    operator fun get(index: Int): Int = buffer!![start + _position + index].toInt() and 0xff

    override fun subset(position: Int?, length: Int?, bufferSize: Int?): InputStream {
        val buf = buffer ?: return InputMemoryStream(ByteArray(0))
        val p = position ?: _position
        val len = length ?: (_length - p)
        return InputMemoryStream(buf, byteOrder = byteOrder, offset = start + p, length = len)
    }

    override fun readByte(): Int {
        val b = buffer!![start + _position].toInt() and 0xff
        _position++
        return b
    }

    override fun toUint8List(): ByteArray {
        val buf = buffer ?: return ByteArray(0)
        var len = length
        if ((_position + len) > _length) len = _length - _position
        val from = start + _position
        return buf.copyOfRange(from, from + len)
    }
}
