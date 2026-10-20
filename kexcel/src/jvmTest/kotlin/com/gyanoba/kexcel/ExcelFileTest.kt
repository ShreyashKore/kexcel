package com.gyanoba.kexcel

import com.gyanoba.kexcel.number_format.CustomNumericNumFormat
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import kotlin.test.Test
import kotlin.test.assertEquals
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
 *
 * Fixture/zip helpers are shared across the JVM tests in [TestFixtures]. The
 * header/footer, border, rich-text, rPh, `.xls`, spanned-item and writer-XML tests
 * have been split out into [HeaderFooterFileTest], [BordersFileTest],
 * [RichTextFileTest], [RphTest], [XlsFileTest], [SpannedItemsTest] and
 * [WorksheetXmlTest].
 */
class ExcelFileTest {

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