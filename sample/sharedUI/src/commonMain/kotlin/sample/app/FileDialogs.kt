package sample.app

/** A file the user picked, with its raw bytes read into memory. */
class PickedFile(val name: String, val bytes: ByteArray)

/**
 * Whether interactive open/save file dialogs are implemented on the current platform.
 *
 * This sample implements them for Desktop (JVM) only; on Android and iOS the
 * functions below are no-op stubs. Wiring those up needs platform plumbing
 * (Android `ActivityResultContracts`, iOS `UIDocumentPicker`) that is out of
 * scope for this minimal demo.
 */
expect val fileDialogsSupported: Boolean

/** Show an "open file" dialog and return the chosen `.xlsx`, or `null` if cancelled/unsupported. */
expect suspend fun openXlsxFile(): PickedFile?

/**
 * Show a "save file" dialog, write [bytes] to the chosen location and return its
 * path, or `null` if cancelled/unsupported.
 */
expect suspend fun saveXlsxFile(suggestedName: String, bytes: ByteArray): String?
