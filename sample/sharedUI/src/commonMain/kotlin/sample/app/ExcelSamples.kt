package sample.app

import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.CellValue
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.HorizontalAlign
import com.gyanoba.kexcel.utils.Underline
import com.gyanoba.kexcel.utils.VerticalAlign

/**
 * A named group of log lines produced by one sample. The UI renders each
 * [Section] as a titled block.
 */
data class Section(val title: String, val lines: List<String>)

/**
 * Runs every Kexcel usage sample and returns their output for display.
 *
 * Each function below is a small, self-contained tour of one part of the API.
 * They are written to double as documentation — read the source top to bottom
 * to learn how the library is used.
 */
fun runAllSamples(): List<Section> = listOf(
    sampleCreateAndWriteValues(),
    sampleReadValues(),
    sampleStyling(),
    sampleFormulas(),
    sampleRowsAndColumns(),
    sampleMergeCells(),
    sampleColumnWidthRowHeight(),
    sampleFindAndReplace(),
    sampleSheetOperations(),
    sampleSaveAndReload(),
)

/**
 * Create a blank workbook and write one cell of every [CellValue] type.
 */
private fun sampleCreateAndWriteValues(): Section {
    val log = mutableListOf<String>()

    // A blank workbook always starts with a single sheet named "Sheet1".
    val excel = Excel.createExcel()
    val sheet = excel["Sheet1"]
    log += "Created workbook with sheets: ${excel.getSheets().keys}"

    // updateCell takes a CellIndex and a CellValue. Indices are 0-based;
    // build them from an "A1" string or from explicit column/row numbers.
    sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Some text"))
    sheet.updateCell(CellIndex.indexByString("A2"), IntCellValue(42))
    sheet.updateCell(CellIndex.indexByString("A3"), DoubleCellValue(3.14))
    sheet.updateCell(CellIndex.indexByString("A4"), BoolCellValue(true))
    sheet.updateCell(CellIndex.indexByString("A5"), DateCellValue(year = 2026, month = 7, day = 9))
    sheet.updateCell(CellIndex.indexByString("A6"), TimeCellValue(hour = 14, minute = 30))
    sheet.updateCell(
        CellIndex.indexByString("A7"),
        DateTimeCellValue(year = 2026, month = 7, day = 9, hour = 14, minute = 30),
    )
    // updateCell on the Excel object itself is equivalent (it takes a sheet name).
    excel.updateCell("Sheet1", CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 7), TextCellValue("row 8, via Excel"))

    log += "Wrote 8 cells to column A (one per data type)."
    log += "A1..A7 = ${(0..6).map { sheet.cell(CellIndex.indexByColumnRow(0, it)).value }}"
    return Section("1. Create workbook & write all value types", log)
}

/**
 * Read cells back individually, by iterating rows, and as a range.
 */
private fun sampleReadValues(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()
    val sheet = excel["Sheet1"]
    sheet.appendRow(listOf(TextCellValue("Country"), TextCellValue("Capital")))
    sheet.appendRow(listOf(TextCellValue("India"), TextCellValue("New Delhi")))
    sheet.appendRow(listOf(TextCellValue("Japan"), TextCellValue("Tokyo")))

    // Single cell.
    val b2 = sheet.cell(CellIndex.indexByString("B2")).value
    log += "B2 = $b2"

    // Iterate rows. Each row is a List<Data?>; a null entry is an empty cell.
    log += "All rows:"
    for (row in sheet.rows) {
        log += "  " + row.joinToString(" | ") { it?.value?.toString() ?: "" }
    }

    // Read a rectangular range as raw values.
    val range = sheet.selectRangeValuesWithString("A1:B3")
    log += "Range A1:B3 -> $range"

    // CellValue is sealed — pattern-match to get the underlying typed value.
    val typed = when (val v = sheet.cell(CellIndex.indexByString("A2")).value) {
        is TextCellValue -> "text: ${v.value}"
        is IntCellValue -> "int: ${v.value}"
        else -> "other: $v"
    }
    log += "A2 typed read -> $typed"
    return Section("2. Read cells, rows & ranges", log)
}

/**
 * Apply a [CellStyle]: colors, font, alignment, borders and number format.
 */
private fun sampleStyling(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()
    val sheet = excel["Sheet1"]

    // A styled header cell.
    val headerStyle = CellStyle(
        fontColorHex = ExcelColor.white,
        backgroundColorHex = ExcelColor.blue,
        bold = true,
        fontSize = 14,
        horizontalAlign = HorizontalAlign.Center,
        verticalAlign = VerticalAlign.Center,
        underline = Underline.Single,
        leftBorder = Border(borderStyle = BorderStyle.Thin, borderColorHex = ExcelColor.black),
        bottomBorder = Border(borderStyle = BorderStyle.Medium, borderColorHex = ExcelColor.black),
    )
    sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Header"), cellStyle = headerStyle)
    log += "A1 styled: white-on-blue, bold, centered, underlined, bordered."

    // Number formats control how numbers are displayed. standard_2 = "0.00".
    sheet.updateCell(
        CellIndex.indexByString("A2"),
        DoubleCellValue(1234.5),
        cellStyle = CellStyle(numberFormat = NumFormat.standard_2),
    )
    log += "A2 = 1234.5 shown with number format '${NumFormat.standard_2.formatCode}'."

    // Styles are immutable; derive variants with copyWith.
    val redHeader = headerStyle.copyWith(backgroundColorHexVal = ExcelColor.red)
    sheet.updateCell(CellIndex.indexByString("B1"), TextCellValue("Alert"), cellStyle = redHeader)
    log += "B1 reuses the header style via copyWith with a red background."

    val a1Style = sheet.cell(CellIndex.indexByString("A1")).cellStyle
    log += "Read back A1 -> bold=${a1Style?.isBold}, bg=${a1Style?.backgroundColor?.colorHex}"
    return Section("3. Cell styling (color, font, border, format)", log)
}

/**
 * Set formulas on cells.
 */
private fun sampleFormulas(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()
    val sheet = excel["Sheet1"]
    sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(10))
    sheet.updateCell(CellIndex.indexByString("A2"), IntCellValue(20))
    sheet.updateCell(CellIndex.indexByString("A3"), IntCellValue(30))

    // Two equivalent ways to add a formula.
    sheet.updateCell(CellIndex.indexByString("A4"), FormulaCellValue("=SUM(A1:A3)"))
    sheet.cell(CellIndex.indexByString("A5")).setFormula("=AVERAGE(A1:A3)")

    log += "A4 formula = ${sheet.cell(CellIndex.indexByString("A4")).value}"
    log += "A5 formula = ${sheet.cell(CellIndex.indexByString("A5")).value}"
    log += "(Formulas are stored, not evaluated — the spreadsheet app computes them.)"
    return Section("4. Formulas", log)
}

/**
 * Insert, append and remove rows and columns.
 */
private fun sampleRowsAndColumns(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()

    // appendRow adds after the last filled row.
    excel.appendRow("Sheet1", listOf(TextCellValue("r0c0"), TextCellValue("r0c1")))
    excel.appendRow("Sheet1", listOf(TextCellValue("r1c0"), TextCellValue("r1c1")))

    // insertRowIterables writes at a specific row, optionally offset by a column.
    excel.insertRowIterables(
        "Sheet1",
        listOf(TextCellValue("offset")),
        rowIndex = 0,
        startingColumn = 2,
    )
    val sheet = excel["Sheet1"]
    log += "After 2 appends + 1 offset insert: ${sheet.maxRows} rows x ${sheet.maxColumns} cols"

    // Insert a blank row at the top, then a blank column at the left.
    excel.insertRow("Sheet1", rowIndex = 0)
    excel.insertColumn("Sheet1", columnIndex = 0)
    log += "After inserting a top row and left column: ${sheet.maxRows} rows x ${sheet.maxColumns} cols"

    // Remove them again.
    excel.removeRow("Sheet1", rowIndex = 0)
    excel.removeColumn("Sheet1", columnIndex = 0)
    log += "After removing them: ${sheet.maxRows} rows x ${sheet.maxColumns} cols"
    return Section("5. Rows & columns", log)
}

/**
 * Merge and unmerge cells.
 */
private fun sampleMergeCells(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()

    // Merge A1:C1 with an explicit value in the merged region.
    excel.merge(
        "Sheet1",
        CellIndex.indexByString("A1"),
        CellIndex.indexByString("C1"),
        customValue = TextCellValue("Merged title"),
    )
    log += "Merged cells: ${excel.getMergedCells("Sheet1")}"
    log += "A1 value = ${excel["Sheet1"].cell(CellIndex.indexByString("A1")).value}"

    // Unmerge by its range string.
    excel.unMerge("Sheet1", "A1:C1")
    log += "After unMerge: ${excel.getMergedCells("Sheet1")}"
    return Section("6. Merge & unmerge cells", log)
}

/**
 * Set column widths, row heights and auto-fit.
 */
private fun sampleColumnWidthRowHeight(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()
    val sheet = excel["Sheet1"]
    sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("wide column"))

    sheet.setColumnWidth(columnIndex = 0, columnWidth = 30.0)
    sheet.setRowHeight(rowIndex = 0, rowHeight = 24.0)
    sheet.setColumnAutoFit(columnIndex = 1)

    log += "Column 0 width = ${sheet.getColumnWidth(0)}"
    log += "Row 0 height = ${sheet.getRowHeight(0)}"
    log += "Column 1 auto-fit = ${sheet.getColumnAutoFit(1)}"
    return Section("7. Column widths & row heights", log)
}

/**
 * Find and replace text across a sheet.
 */
private fun sampleFindAndReplace(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()
    val sheet = excel["Sheet1"]
    sheet.appendRow(listOf(TextCellValue("Widget A"), TextCellValue("Widget B")))
    sheet.appendRow(listOf(TextCellValue("Gizmo"), TextCellValue("Widget C")))

    // source is a Regex; returns the number of replacements made.
    val replaced = excel.findAndReplace("Sheet1", Regex("Widget"), "Gadget")
    log += "Replaced $replaced occurrence(s) of 'Widget' with 'Gadget'."
    log += "Row 0 -> ${sheet.row(0).map { it?.value?.toString() }}"
    log += "Row 1 -> ${sheet.row(1).map { it?.value?.toString() }}"
    return Section("8. Find & replace", log)
}

/**
 * Create, copy, rename, delete sheets and set the default one.
 */
private fun sampleSheetOperations(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()

    // Referencing a new name creates the sheet on demand.
    excel["Data"].appendRow(listOf(TextCellValue("hello")))
    log += "Sheets after creating 'Data': ${excel.getSheets().keys}"

    excel.copy("Data", "Backup")
    log += "After copy Data -> Backup: ${excel.getSheets().keys}"

    excel.rename("Backup", "Archive")
    log += "After rename Backup -> Archive: ${excel.getSheets().keys}"

    excel.setDefaultSheet("Data")
    log += "Default sheet is now: ${excel.getDefaultSheet()}"

    excel.delete("Archive")
    log += "After delete Archive: ${excel.getSheets().keys}"
    return Section("9. Sheet operations", log)
}

/**
 * Serialize to `.xlsx` bytes and load them back — a full round-trip.
 */
private fun sampleSaveAndReload(): Section {
    val log = mutableListOf<String>()

    val excel = Excel.createExcel()
    excel["Sheet1"].appendRow(listOf(TextCellValue("persisted"), IntCellValue(7)))

    // encode() returns the workbook as .xlsx bytes, entirely in memory.
    val bytes: ByteArray? = excel.encode()
    log += "Encoded workbook to ${bytes?.size ?: 0} bytes."

    // Those bytes are a valid .xlsx file: decode them again.
    val reloaded = Excel.decodeBytes(bytes!!)
    val a1 = reloaded["Sheet1"].cell(CellIndex.indexByString("A1")).value
    val b1 = reloaded["Sheet1"].cell(CellIndex.indexByString("B1")).value
    log += "Reloaded A1 = $a1, B1 = $b1"
    log += "(On JVM/native, write `bytes` to a .xlsx file with File(...).writeBytes(bytes).)"
    return Section("10. Save, encode & reload", log)
}
