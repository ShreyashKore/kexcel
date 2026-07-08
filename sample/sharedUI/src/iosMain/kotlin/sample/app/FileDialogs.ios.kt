package sample.app

// File dialogs are not wired up on iOS in this minimal sample.
// A real implementation would present a UIDocumentPickerViewController and
// read the security-scoped URL's contents.

actual val fileDialogsSupported: Boolean = false

actual suspend fun openXlsxFile(): PickedFile? = null

actual suspend fun saveXlsxFile(suggestedName: String, bytes: ByteArray): String? = null
