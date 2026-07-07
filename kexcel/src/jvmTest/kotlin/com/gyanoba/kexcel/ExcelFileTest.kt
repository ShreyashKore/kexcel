package com.gyanoba.kexcel

import com.fleeksoft.ksoup.Ksoup
import com.gyanoba.kexcel.number_format.CustomNumericNumFormat
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import com.gyanoba.kexcel.utils.toExcelColor
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM-only port of the Dart `excel` tests that read `.xlsx` fixtures from disk.
 *
 * Fixtures live under `src/commonTest/kotlin/com/gyanoba/kexcel/test_resources/`.
 * In-memory (fixture-less) tests are in the multiplatform [ExcelInMemoryTest].
 *
 * Disk round-trips in the Dart originals (write to ./tmp, read back) are replaced by
 * in-memory round-trips: `Excel.decodeBytes(excel.encode()!!)` — functionally identical,
 * no temp files.
 */
class ExcelFileTest {

    // region --- fixture / zip helpers ---

    private fun fixture(name: String): ByteArray {
        val candidates = listOf(
            "src/commonTest/kotlin/com/gyanoba/kexcel/test_resources/$name",
            "kexcel/src/commonTest/kotlin/com/gyanoba/kexcel/test_resources/$name",
        )
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) return f.readBytes()
        }
        error("Test fixture not found: $name (looked in $candidates, cwd=${File(".").absolutePath})")
    }

    private data class Sst(val count: String, val uniqueCount: String)

    private fun parseSst(bytes: ByteArray): Sst {
        val xml = readZipEntry(bytes, "xl/sharedStrings.xml").decodeToString()
        val sst = Ksoup.parse(xml).getElementsByTag("sst").first()
            ?: error("no <sst> element in xl/sharedStrings.xml")
        return Sst(count = sst.attr("count"), uniqueCount = sst.attr("uniqueCount"))
    }

    private fun readZipEntry(bytes: ByteArray, entryName: String): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == entryName) return zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        error("zip entry not found: $entryName")
    }

    // endregion

    // Dart: 'Read XLSX File'
    @Test
    fun readXlsxFile() {
        val excel = Excel.decodeBytes(fixture("example.xlsx"))
        assertEquals(3, excel.tables["Sheet1"]!!.maxColumns)
        assertEquals("Washington", excel.tables["Sheet1"]!!.rows[1][1]!!.value.toString())
    }

    // Dart: 'Cell Data-Types from Microsoft Excel 365 Destkop'
    @Test
    fun cellDataTypesMsExcel365() {
        val t = Excel.decodeBytes(fixture("dataTypesUsingMsExcel365Desktop.xlsx")).tables["Tabelle1"]!!
        assertEquals(TextCellValue("Some text"), t.rows[2][1]?.value)
        assertEquals(IntCellValue(42), t.rows[3][1]?.value)
        assertEquals(DoubleCellValue(12.3), t.rows[4][1]?.value)
        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), t.rows[5][1]?.value)
        assertEquals(
            DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13),
            t.rows[6][1]?.value,
        )
        assertEquals(BoolCellValue(true), t.rows[7][1]?.value)
        assertEquals(BoolCellValue(false), t.rows[8][1]?.value)
        assertEquals(DoubleCellValue(15.99), t.rows[9][1]?.value)
        assertEquals(DoubleCellValue(0.05), t.rows[10][1]?.value)
        assertEquals(TimeCellValue(hour = 2, minute = 20, second = 10), t.rows[11][1]?.value)
    }

    // Dart: 'Cell Data-Types from Google Spreadsheet'
    @Test
    fun cellDataTypesGoogleSpreadsheet() {
        val t = Excel.decodeBytes(fixture("dataTypesUsingGoogleSpreadsheet.xlsx")).tables["Sheet1"]!!
        assertEquals(TextCellValue("Some text"), t.rows[2][1]?.value)
        assertEquals(IntCellValue(42), t.rows[3][1]?.value)
        assertEquals(DoubleCellValue(12.3), t.rows[4][1]?.value)
        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), t.rows[5][1]?.value)
        assertEquals(
            DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13),
            t.rows[6][1]?.value,
        )
        assertEquals(BoolCellValue(true), t.rows[7][1]?.value)
        assertEquals(BoolCellValue(false), t.rows[8][1]?.value)
        assertEquals(DoubleCellValue(15.99), t.rows[9][1]?.value)
        assertEquals(DoubleCellValue(0.05), t.rows[10][1]?.value)
    }

    // Dart: 'Cell Data-Types from LibreOffice'
    @Test
    fun cellDataTypesLibreOffice() {
        val t = Excel.decodeBytes(fixture("dataTypesUsingLibreoffice.xlsx")).tables["Sheet1"]!!
        assertEquals(TextCellValue("Some text"), t.rows[2][1]?.value)
        assertEquals(IntCellValue(42), t.rows[3][1]?.value)
        assertEquals(DoubleCellValue(12.3), t.rows[4][1]?.value)
        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), t.rows[5][1]?.value)
        assertEquals(
            DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13),
            t.rows[6][1]?.value,
        )
        assertEquals(BoolCellValue(true), t.rows[7][1]?.value)
        assertEquals(BoolCellValue(false), t.rows[8][1]?.value)
        assertEquals(DoubleCellValue(15.99), t.rows[9][1]?.value)
        assertEquals(DoubleCellValue(0.05), t.rows[10][1]?.value)
    }

    // Dart: 'Read/Write various data types'
    @Test
    fun readWriteVariousDataTypes() {
        val excel = Excel.decodeBytes(fixture("dataTypesUsingMsExcel365Desktop.xlsx"))
        run {
            val sheet = excel.tables["Tabelle1"]!!
            sheet.updateCell(CellIndex.indexByString("B4"), DoubleCellValue(13.37))
            sheet.updateCell(CellIndex.indexByString("B5"), DateCellValue(year = 2025, month = 11, day = 28))
            sheet.updateCell(CellIndex.indexByString("B6"), null)
            sheet.updateCell(CellIndex.indexByString("B7"), TimeCellValue(hour = 20, minute = 15))
            sheet.updateCell(
                CellIndex.indexByString("B8"),
                DoubleCellValue(42.0),
                cellStyle = CellStyle(numberFormat = NumFormat.standard_11),
            )
            val b10 = sheet.cell(CellIndex.indexByString("B10"))
            b10.cellStyle = (b10.cellStyle ?: CellStyle())
                .copyWith(numberFormat = CustomNumericNumFormat(formatCode = """0\m\²"""))
        }

        val excelAgain = Excel.decodeBytes(excel.encode()!!)
        run {
            val sheet = excelAgain.tables["Tabelle1"]!!

            val b3 = sheet.cell(CellIndex.indexByString("B3"))
            assertEquals(TextCellValue("Some text"), b3.value)
            assertEquals(NumFormat.standard_0, b3.cellStyle?.numberFormat ?: NumFormat.standard_0)

            val b4 = sheet.cell(CellIndex.indexByString("B4"))
            assertEquals(DoubleCellValue(13.37), b4.value)
            assertEquals(NumFormat.defaultFloat, b4.cellStyle?.numberFormat ?: NumFormat.defaultFloat)

            val b5 = sheet.cell(CellIndex.indexByString("B5"))
            assertEquals(DateCellValue(year = 2025, month = 11, day = 28), b5.value)
            assertEquals(NumFormat.defaultDate, b5.cellStyle?.numberFormat)

            val b6 = sheet.cell(CellIndex.indexByString("B6"))
            assertNull(b6.value)
            assertEquals(NumFormat.standard_0, b6.cellStyle?.numberFormat)

            val b7 = sheet.cell(CellIndex.indexByString("B7"))
            assertEquals(TimeCellValue(hour = 20, minute = 15), b7.value)
            assertEquals(NumFormat.defaultTime, b7.cellStyle?.numberFormat)

            val b8 = sheet.cell(CellIndex.indexByString("B8"))
            assertEquals(IntCellValue(42), b8.value)
            assertEquals(NumFormat.standard_11, b8.cellStyle?.numberFormat)

            val b10 = sheet.cell(CellIndex.indexByString("B10"))
            assertEquals(DoubleCellValue(15.99), b10.value)
            assertEquals(CustomNumericNumFormat(formatCode = """0\m\²"""), b10.cellStyle?.numberFormat)
        }
    }

    // Dart: 'Sheet Operations' group (create/copy/rename/delete share one workbook, so combined).
    @Test
    fun sheetOperations() {
        val excel = Excel.decodeBytes(fixture("example.xlsx"))

        // create Sheet
        val sheetObject = excel["SheetTmp"]
        sheetObject.insertRowIterables(
            listOf(TextCellValue("Country"), TextCellValue("Capital"), TextCellValue("Head")), 0,
        )
        sheetObject.insertRowIterables(
            listOf(TextCellValue("Russia"), TextCellValue("Moscow"), TextCellValue("Putin")), 1,
        )
        assertEquals(2, excel.tables.size)
        assertEquals("Washington", excel.tables["Sheet1"]!!.rows[1][1]!!.value.toString())
        assertEquals(3, excel.tables["SheetTmp"]!!.maxColumns)
        assertEquals("Putin", excel.tables["SheetTmp"]!!.rows[1][2]!!.value.toString())

        // copy Sheet
        excel.copy("SheetTmp", "SheetTmp2")
        assertEquals(3, excel.tables.size)
        assertEquals("Putin", excel.tables["SheetTmp2"]!!.rows[1][2]!!.value.toString())

        // rename Sheet
        excel.rename("SheetTmp2", "SheetTmp3")
        assertEquals(3, excel.tables.size)
        assertNull(excel.tables["Sheettmp2"])
        assertEquals("Putin", excel.tables["SheetTmp3"]!!.rows[1][2]!!.value.toString())

        // delete Sheet
        excel.delete("SheetTmp3")
        excel.delete("SheetTmp")
        assertEquals(1, excel.tables.size)
        assertEquals("Washington", excel.tables["Sheet1"]!!.rows[1][1]!!.value.toString())
    }

    // Dart: 'Saving XLSX File'
    @Test
    fun savingXlsxFile() {
        val excel = Excel.decodeBytes(fixture("example.xlsx"))
        excel.tables["Sheet1"]!!.insertRowIterables(
            listOf(TextCellValue("Russia"), TextCellValue("Moscow"), TextCellValue("Putin")), 4,
        )
        val newExcel = Excel.decodeBytes(excel.encode()!!)
        assertEquals(1, newExcel.tables.size)
        assertEquals("Washington", newExcel.tables["Sheet1"]!!.rows[1][1]!!.value.toString())
        assertEquals(3, newExcel.tables["Sheet1"]!!.maxColumns)
        assertEquals("Moscow", newExcel.tables["Sheet1"]!!.rows[4][1]!!.value.toString())
    }

    // Dart: 'Saving XLSX File with superscript' (present twice in the Dart source; kept once).
    @Test
    fun savingXlsxWithSuperscript() {
        val excel = Excel.decodeBytes(fixture("superscriptExample.xlsx"))
        val newExcel = Excel.decodeBytes(excel.encode()!!)
        assertEquals(1, newExcel.tables.size)
        assertEquals("Text and superscript text", newExcel.tables["Sheet1"]!!.rows[0][0]!!.value.toString())
        assertEquals("Text and superscript text", newExcel.tables["Sheet1"]!!.rows[1][0]!!.value.toString())
        assertEquals("Text in A3", newExcel.tables["Sheet1"]!!.rows[2][0]!!.value.toString())
    }

    // Dart: 'Add already shared strings ... increased usage count but equal unique count'
    @Test
    fun sharedStringsAreReused() {
        val bytes = fixture("example.xlsx")
        val oldSst = parseSst(bytes)

        val excel = Excel.decodeBytes(bytes)
        excel.tables["Sheet1"]!!.insertRowIterables(
            listOf(
                TextCellValue("ISRAEL"),
                TextCellValue("Jerusalem"),
                TextCellValue("Benjamin Netanyahu"),
            ), 4,
        )
        val fileBytes = excel.encode()!!

        // Re-decoding the written bytes must not throw (Dart: returnsNormally).
        Excel.decodeBytes(fileBytes)

        val newSst = parseSst(fileBytes)
        assertEquals(oldSst.uniqueCount, newSst.uniqueCount)
        assertEquals("12", oldSst.count)
        assertEquals("15", newSst.count)
    }

    // region --- Header/Footer group ---

    // Dart: 'Update header/footer'.
    @Test
    fun updateHeaderFooter() {
        val bytes = fixture("example.xlsx")
        val excel = Excel.decodeBytes(bytes)
        val sheetObject = excel.tables["Sheet1"]!!

        sheetObject.headerFooter!!.oddHeader = "Foo"
        sheetObject.headerFooter!!.oddFooter = "Bar"

        excel.copy("Sheet1", "test_sheet")
        val newSheet = excel.tables["test_sheet"]!!
        assertEquals(
            newSheet.headerFooter!!.oddHeader!!, "Foo")
        assertEquals(
            newSheet.headerFooter!!.oddFooter!!, "Bar")
    }

    // Dart: 'Clone header/footer of existing Workbook'.
    // KMP port: same immutability limitation as updateHeaderFooter.
    @Test
    fun cloneHeaderFooter() {
        val bytes = fixture("example.xlsx")
        val excel = Excel.decodeBytes(bytes)
        val sheetObject = excel.tables["Sheet1"]

        sheetObject!!.headerFooter!!.oddHeader = "Foo"
        sheetObject!!.headerFooter!!.oddFooter = "Bar"

        excel.copy("Sheet1", "test_sheet");

        val testSheet = excel.tables["test_sheet"];

        assertEquals(testSheet!!.headerFooter!!.oddHeader!!, "Foo")
        assertEquals(testSheet.headerFooter!!.oddFooter!!, "Bar")
    }

    // Dart: 'Remove header/footer from Workbook' — the Dart test body is empty.
    @Test
    fun removeHeaderFooterFromWorkbook() = Unit

    // Dart: 'Reader headerFooter attributes'
    @Test
    fun readerHeaderFooterAttributes() {
        val excel = Excel.decodeBytes(fixture("headerFooter.xlsx"))
        val headerFooter = excel.tables["Sheet1"]!!.headerFooter!!
        assertEquals(false, headerFooter.alignWithMargins)
        assertEquals(true, headerFooter.differentFirst)
        assertEquals(true, headerFooter.differentOddEven)
        assertEquals(false, headerFooter.scaleWithDoc)
    }

    // endregion

    // region --- Borders group ---

    private val allBorderStyles = listOf(
        BorderStyle.None, BorderStyle.DashDot, BorderStyle.DashDotDot, BorderStyle.Dashed,
        BorderStyle.Dotted, BorderStyle.Double, BorderStyle.Hair, BorderStyle.Medium,
        BorderStyle.MediumDashDot, BorderStyle.MediumDashDotDot, BorderStyle.MediumDashed,
        BorderStyle.SlantDashDot, BorderStyle.Thick, BorderStyle.Thin,
    )

    // Dart: 'read file with borders'
    @Test
    fun readFileWithBorders() {
        val excel = Excel.decodeBytes(fixture("borders.xlsx"))
        val sheetObject = excel.tables["Sheet1"]!!

        val borderEmpty = Border()
        val borderMedium = Border(borderStyle = BorderStyle.Medium)
        val borderMediumRed = Border(borderStyle = BorderStyle.Medium, borderColorHex = "FFFF0000".toExcelColor())
        val borderHair = Border(borderStyle = BorderStyle.Hair)
        val borderDouble = Border(borderStyle = BorderStyle.Double)

        val a1 = sheetObject.cell(CellIndex.indexByString("A1")).cellStyle
        assertEquals(borderMedium, a1?.leftBorder)
        assertEquals(borderMedium, a1?.rightBorder)
        val a1Top = a1?.topBorder
        assertTrue(a1Top == null || a1Top == borderEmpty)
        assertEquals(borderMediumRed, a1?.bottomBorder)
        val a1Diagonal = a1?.diagonalBorder
        assertTrue(a1Diagonal == null || a1Diagonal == borderEmpty)
        assertEquals(false, a1?.diagonalBorderUp)
        assertEquals(false, a1?.diagonalBorderDown)

        val b3 = sheetObject.cell(CellIndex.indexByString("B3")).cellStyle
        assertEquals(borderMedium, b3?.leftBorder)
        assertEquals(borderMedium, b3?.rightBorder)
        assertEquals(borderHair, b3?.topBorder)
        assertEquals(borderHair, b3?.bottomBorder)

        val a5 = sheetObject.cell(CellIndex.indexByString("A5")).cellStyle
        assertEquals(borderDouble, a5?.diagonalBorder)
        assertEquals(false, a5?.diagonalBorderUp)
        assertEquals(true, a5?.diagonalBorderDown)

        val c5 = sheetObject.cell(CellIndex.indexByString("C5")).cellStyle
        assertEquals(borderDouble, c5?.diagonalBorder)
        assertEquals(true, c5?.diagonalBorderUp)
        assertEquals(false, c5?.diagonalBorderDown)
    }

    // Dart: 'test support all border styles'
    @Test
    fun supportAllBorderStyles() {
        val excel = Excel.decodeBytes(fixture("borders2.xlsx"))
        val sheetObject = excel.tables["Sheet1"]!!

        for (i in 1 until allBorderStyles.size) {
            // Loop from i = 1, as Excel does not set None type.
            val border = Border(borderStyle = allBorderStyles[i])
            val cellStyle = sheetObject.cell(CellIndex.indexByString("B${2 * (i + 1)}")).cellStyle
            assertEquals(border, cellStyle?.leftBorder)
            assertEquals(border, cellStyle?.rightBorder)
            assertEquals(border, cellStyle?.topBorder)
            assertEquals(border, cellStyle?.bottomBorder)
        }
    }

    // Dart: 'test support for merged cells with borders'
    @Test
    fun mergedCellsWithBorders() {
        val excel = Excel.decodeBytes(fixture("mergedBorders.xlsx"))
        val sheetObject = excel.tables["Sheet1"]!!

        sheetObject.merge(CellIndex.indexByString("B2"), CellIndex.indexByString("D4"))

        for (i in 1 until allBorderStyles.size) {
            val border = Border(borderStyle = allBorderStyles[i], borderColorHex = "FF000000".toExcelColor())
            val start = CellIndex.indexByString("B${4 * i + 2}")
            val end = CellIndex.indexByString("D${4 * i + 4}")

            sheetObject.merge(start, end)
            sheetObject.setMergedCellStyle(
                start,
                CellStyle(leftBorder = border, rightBorder = border, topBorder = border, bottomBorder = border),
            )
        }

        for (i in 1 until allBorderStyles.size) {
            val cellIndexStart = CellIndex.indexByString("B${4 * i + 2}")
            val cellIndexEnd = CellIndex.indexByString("D${4 * i + 4}")

            for (j in cellIndexStart.rowIndex..cellIndexEnd.rowIndex) {
                for (k in cellIndexStart.columnIndex..cellIndexEnd.columnIndex) {
                    val cellStyle = sheetObject
                        .cell(CellIndex.indexByColumnRow(columnIndex = k, rowIndex = j))
                        .cellStyle
                    val borderStyle = Border(borderStyle = allBorderStyles[i], borderColorHex = "FF000000".toExcelColor())

                    if (j == cellIndexStart.rowIndex) assertEquals(borderStyle, cellStyle?.topBorder)
                    if (j == cellIndexEnd.rowIndex) assertEquals(borderStyle, cellStyle?.bottomBorder)
                    if (k == cellIndexStart.columnIndex) assertEquals(borderStyle, cellStyle?.leftBorder)
                    if (k == cellIndexEnd.columnIndex) assertEquals(borderStyle, cellStyle?.rightBorder)
                }
            }
        }
    }

    // Dart: 'saving XLSX File with borders'
    @Test
    fun savingXlsxWithBorders() {
        val excel = Excel.decodeBytes(fixture("borders.xlsx"))
        val newExcel = Excel.decodeBytes(excel.encode()!!)
        assertEquals(1, newExcel.tables.size)

        val borderEmpty = Border()
        val borderMedium = Border(borderStyle = BorderStyle.Medium)
        val borderMediumRed = Border(borderStyle = BorderStyle.Medium, borderColorHex = "FFFF0000".toExcelColor())

        val b1 = newExcel.tables["Sheet1"]!!.cell(CellIndex.indexByString("B1")).cellStyle
        assertEquals(borderMedium, b1?.leftBorder)
        assertEquals(borderMedium, b1?.rightBorder)
        assertEquals(borderEmpty, b1?.topBorder)
        assertEquals(borderMediumRed, b1?.bottomBorder)
    }

    // endregion

    // Dart: 'Cell Style' group -> 'read file with rich text'
    @Test
    fun readFileWithRichText() {
        val excel = Excel.decodeBytes(fixture("richText.xlsx"))
        val sheetObject = excel.tables["Sheet1"]!!
        val redHex = "FFFF0000"
        val blueHex = "FF2A6099"

        val cellA1 = sheetObject.cell(CellIndex.indexByString("A1")).value as TextCellValue
        assertEquals(12, cellA1.value.children!![0].style!!.fontSize)
        assertEquals(redHex, cellA1.value.children!![0].style!!.fontColor.colorHex)
        assertEquals(10, cellA1.value.children!![1].style!!.fontSize)
        assertEquals(blueHex, cellA1.value.children!![1].style!!.fontColor.colorHex)

        val cellA2 = sheetObject.cell(CellIndex.indexByString("A2")).value as TextCellValue
        assertEquals(true, cellA2.value.children!![0].style!!.isBold)
        assertEquals(false, cellA2.value.children!![0].style!!.isItalic)
        assertEquals(false, cellA2.value.children!![1].style!!.isBold)
        assertEquals(true, cellA2.value.children!![1].style!!.isItalic)

        val cellA3 = sheetObject.cell(CellIndex.indexByString("A3")).value as TextCellValue
        assertEquals("Skia", cellA3.value.children!![0].style!!.fontFamily)
        assertEquals("Arial", cellA3.value.children!![1].style!!.fontFamily)
    }

    // region --- rPh tag group ---

    // Dart: 'Read Cell shared text without rPh elements'
    @Test
    fun readCellSharedTextWithoutRph() {
        val t = Excel.decodeBytes(fixture("rphSample.xlsx")).tables["Sheet1"]!!
        assertEquals("plainText", t.rows[1][0]!!.value.toString())
        assertEquals("Hellow world", t.rows[1][1]!!.value.toString())
        assertEquals("世界よこんにちは", t.rows[1][2]!!.value.toString())
        assertEquals("ようこそユーザー", t.rows[2][2]!!.value.toString())
        assertEquals("ロケール選択", t.rows[3][2]!!.value.toString())
        assertEquals("ロケール選択", t.rows[4][2]!!.value.toString())
    }

    // Dart: 'saving XLSX File without rPh elements'
    @Test
    fun savingXlsxWithoutRph() {
        val excel = Excel.decodeBytes(fixture("rphSample.xlsx"))
        excel.tables["Sheet1"]!!.rows[3][2]!!.value = TextCellValue("ロケール選択")
        val newExcel = Excel.decodeBytes(excel.encode()!!)
        assertEquals("ロケール選択", newExcel.tables["Sheet1"]!!.rows[3][2]!!.value.toString())
    }

    // endregion

    // region --- .xls file handling ---

    // Dart: 'Exception when opening old .xls file'
    @Test
    fun exceptionWhenOpeningOldXls() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            Excel.decodeBytes(fixture("oldXLSFile.xls"))
        }
        assertEquals(ex.message?.contains("Only .xlsx files are supported"), true)
    }

    // Dart: 'Exception when opening new .xls file'
    @Test
    fun exceptionWhenOpeningNewXls() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            Excel.decodeBytes(fixture("newXLSFile.xls"))
        }
        assertEquals(ex.message?.contains("Only .xlsx files are supported"), true)
    }

    // endregion

    // region --- Spanned Items ---

    private fun assertSpannedItemsList(sheet: Sheet) {
        val spannedItems = sheet.spannedItems
        assertEquals("A1:B1", spannedItems[0])
        assertEquals("A2:A3", spannedItems[1])
        assertEquals("A4:B5", spannedItems[2])
    }

    private fun assertSpannedItemsSheetValues(sheet: Sheet) {
        val cells = sheet.rows.flatMap { row -> row.filterNotNull() }

        assertEquals(TextCellValue("spanned item A1:B1"), cells[0].value)
        assertEquals(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 0), cells[0].cellIndex)

        assertEquals(TextCellValue("spanned item A2:A3"), cells[1].value)
        assertEquals(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 1), cells[1].cellIndex)

        assertEquals(TextCellValue("spanned item A4:B5"), cells[2].value)
        assertEquals(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 3), cells[2].cellIndex)
    }

    // Dart: 'Spanned Items' group -> 'read spanned items'
    @Test
    fun readSpannedItems() {
        val excel = Excel.decodeBytes(fixture("spannedItemExample.xlsx"))
        val sheet = excel.tables["Spanned Items"]!!
        assertSpannedItemsList(sheet)
        assertSpannedItemsSheetValues(sheet)

        val newExcel = Excel.decodeBytes(excel.encode()!!)
        val newSheet = newExcel.tables["Spanned Items"]!!
        assertSpannedItemsList(newSheet)
        assertSpannedItemsSheetValues(newSheet)
    }

    // endregion

    // Dart: 'Parse column width row height'
    @Test
    fun parseColumnWidthRowHeight() {
        val sheetObject = Excel.decodeBytes(fixture("columnWidthRowHeight.xlsx")).tables["Sheet1"]!!

        // ~20 with a little tolerance.
        assertTrue(sheetObject.defaultColumnWidth!! > 18)
        assertTrue(sheetObject.defaultColumnWidth!! < 22)
        assertTrue(sheetObject.defaultRowHeight!! > 18)
        assertTrue(sheetObject.defaultRowHeight!! < 22)

        // ~40 with a little tolerance.
        assertTrue(sheetObject.getColumnWidth(1) > 38)
        assertTrue(sheetObject.getColumnWidth(1) < 42)
        assertTrue(sheetObject.getRowHeight(1) > 38)
        assertTrue(sheetObject.getRowHeight(1) < 42)
    }

    // Dart: 'Decode customNumFmtIdBelow164.xlsx without throwing exception'
    @Test
    fun decodeCustomNumFmtIdBelow164() {
        // Must not throw (Dart: returnsNormally).
        Excel.decodeBytes(fixture("customNumFmtIdBelow164.xlsx"))
    }
}
