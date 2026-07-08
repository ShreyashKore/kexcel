package sample.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.swing.Swing
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual val fileDialogsSupported: Boolean = true

private fun xlsxChooser(title: String): JFileChooser = JFileChooser().apply {
    dialogTitle = title
    fileFilter = FileNameExtensionFilter("Excel workbook (*.xlsx)", "xlsx")
    isAcceptAllFileFilterUsed = true
}

actual suspend fun openXlsxFile(): PickedFile? {
    // Swing dialogs must run on the event-dispatch thread.
    val file = withContext(Dispatchers.Swing) {
        val chooser = xlsxChooser("Open .xlsx file")
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    } ?: return null
    // Read the file off the EDT.
    return withContext(Dispatchers.IO) { PickedFile(file.name, file.readBytes()) }
}

actual suspend fun saveXlsxFile(suggestedName: String, bytes: ByteArray): String? {
    val file = withContext(Dispatchers.Swing) {
        val chooser = xlsxChooser("Save .xlsx file").apply { selectedFile = File(suggestedName) }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val chosen = chooser.selectedFile
            if (chosen.name.endsWith(".xlsx", ignoreCase = true)) chosen
            else File(chosen.parentFile, chosen.name + ".xlsx")
        } else null
    } ?: return null
    return withContext(Dispatchers.IO) {
        file.writeBytes(bytes)
        file.absolutePath
    }
}
