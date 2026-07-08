package sample.app

// File dialogs are not wired up on Android in this minimal sample.
// A real implementation would use ActivityResultContracts.OpenDocument /
// CreateDocument together with the current Activity's ContentResolver.

actual val fileDialogsSupported: Boolean = false

actual suspend fun openXlsxFile(): PickedFile? = null

actual suspend fun saveXlsxFile(suggestedName: String, bytes: ByteArray): String? = null
