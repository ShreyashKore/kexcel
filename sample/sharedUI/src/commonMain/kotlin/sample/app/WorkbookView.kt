package sample.app

import com.gyanoba.kexcel.Excel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes

/** One sheet reduced to plain strings, ready to render as text. */
data class SheetView(
    val name: String,
    val totalRows: Int,
    val totalCols: Int,
    val rows: List<List<String>>,
    val truncated: Boolean,
)

/**
 * A decoded workbook together with its display model. Keeps the live [Excel]
 * instance so it can be re-encoded (round-tripped through the library) on save.
 */
class OpenedWorkbook(
    val fileName: String,
    val excel: Excel,
    val sheets: List<SheetView>,
)

/**
 * Decode [picked] with Kexcel and build a plain-text view of every sheet.
 *
 * Large sheets are capped at [maxRows] x [maxCols] cells for display only —
 * the returned [OpenedWorkbook.excel] still holds the full, unmodified workbook.
 */
suspend fun openWorkbook(picked: PlatformFile, maxRows: Int = 100, maxCols: Int = 26): OpenedWorkbook {
    // Decode the raw .xlsx bytes into Kexcel's in-memory model.
    val bytes = picked.readBytes()
    val excel = Excel.decodeBytes(bytes)

    val sheets = excel.getSheets().map { (name, sheet) ->
        val rows = sheet.rows.take(maxRows).map { row ->
            row.take(maxCols).map { data -> data?.value?.toString() ?: "" }
        }
        SheetView(
            name = name,
            totalRows = sheet.maxRows,
            totalCols = sheet.maxColumns,
            rows = rows,
            truncated = sheet.maxRows > maxRows || sheet.maxColumns > maxCols,
        )
    }
    return OpenedWorkbook(picked.name, excel, sheets)
}

/**
 * Re-encode an opened workbook to `.xlsx` bytes. This runs the data back
 * through Kexcel's writer, so the output is produced by the library rather
 * than being a copy of the original file.
 */
fun OpenedWorkbook.reEncode(): ByteArray? = excel.encode()
