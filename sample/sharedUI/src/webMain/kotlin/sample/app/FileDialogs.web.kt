package sample.app

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download

actual suspend fun saveXlsxFile(
    suggestedName: String,
    bytes: ByteArray
): String? {
    FileKit.download(bytes = bytes, fileName = suggestedName)
    return null
}