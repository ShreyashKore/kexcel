package sample.app

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write

actual suspend fun saveXlsxFile(suggestedName: String, bytes: ByteArray): String? {
    val file = FileKit.openFileSaver(suggestedName, defaultExtension = "xlsx")
    file?.write(bytes)
    return file?.path
}
