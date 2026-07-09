package sample.app

/**
 * Show a "save file" dialog, write [bytes] to the chosen location and return its
 * path, or `null` if cancelled/unsupported.
 */
expect suspend fun saveXlsxFile(suggestedName: String, bytes: ByteArray): String?
