package com.gyanoba.kexcel

import com.gyanoba.kexcel.number_format.CustomNumericNumFormat
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.utils.ColorType
import com.gyanoba.kexcel.utils.ExcelColor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Ports of the Dart `excel` tests that build workbooks in-memory (no `.xlsx` fixtures),
 * so they run on every KMP target from `commonTest`.
 *
 * Fixture-based tests live in the JVM-only `ExcelFileTest`.
 */
class ExcelInMemoryTest {

    // Dart: 'Create New XLSX File'
    @Test
    fun createNewXlsxFile() {
        val excel = Excel.createExcel()
        assertEquals(1, excel.tables.size)
        assertEquals("Sheet1", excel.tables.keys.first())
    }

    // Dart: 'Testing customNumFormats'
    @Test
    fun customNumFormats() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]

        val format1 = CustomNumericNumFormat(formatCode = "0.00%")
        val format2 = CustomNumericNumFormat(formatCode = "#,##0.00")

        sheet.updateCell(
            CellIndex.indexByString("A1"),
            DoubleCellValue(0.15),
            cellStyle = CellStyle(numberFormat = format1),
        )
        sheet.updateCell(
            CellIndex.indexByString("B1"),
            DoubleCellValue(123456.789),
            cellStyle = CellStyle(numberFormat = format2),
        )

        val bytes = excel.encode()
        assertNotNull(bytes)

        val excel2 = Excel.decodeBytes(bytes)
        val sheet2 = excel2["Sheet1"]
        val a1 = sheet2.cell(CellIndex.indexByString("A1"))
        val b1 = sheet2.cell(CellIndex.indexByString("B1"))

        assertEquals(format1, a1.cellStyle?.numberFormat)
        assertEquals(DoubleCellValue(0.15), a1.value)
        assertEquals(format2, b1.cellStyle?.numberFormat)
        assertEquals(DoubleCellValue(123456.789), b1.value)
    }

    // Dart: 'Saving XLSX File with appendRow'
    @Test
    fun savingXlsxWithAppendRow() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]

        sheet.appendRow(
            listOf(
                IntCellValue(8),
                DoubleCellValue(999.62221),
                DateCellValue(year = 2023, month = 4, day = 20),
                DateTimeCellValue(
                    year = 2023, month = 4, day = 20,
                    hour = 15, minute = 44, second = 13,
                ),
                TextCellValue("value"),
            )
        )

        val fileBytes = excel.save()
        assertNotNull(fileBytes)

        val newExcel = Excel.decodeBytes(fileBytes)
        assertEquals(1, newExcel.tables.size)
        val s = newExcel.tables["Sheet1"]!!
        assertEquals(5, s.maxColumns)

        assertEquals(IntCellValue(8), s.rows[0][0]!!.value)
        assertEquals(
            NumFormat.defaultNumeric.toString(),
            s.rows[0][0]!!.cellStyle?.numberFormat.toString(),
        )

        assertEquals(DoubleCellValue(999.62221), s.rows[0][1]!!.value)
        assertEquals(
            NumFormat.defaultFloat.toString(),
            s.rows[0][1]!!.cellStyle?.numberFormat.toString(),
        )

        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), s.rows[0][2]!!.value)
        assertEquals(
            NumFormat.defaultDate.toString(),
            s.rows[0][2]!!.cellStyle?.numberFormat.toString(),
        )

        assertEquals(
            DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13),
            s.rows[0][3]!!.value,
        )
        assertEquals(
            NumFormat.defaultDateTime.toString(),
            s.rows[0][3]!!.cellStyle?.numberFormat.toString(),
        )

        assertEquals(TextCellValue("value"), s.rows[0][4]!!.value)
        assertEquals(
            NumFormat.standard_0.toString(),
            s.rows[0][4]!!.cellStyle?.numberFormat.toString(),
        )
    }

    // Dart: Header/Footer group -> 'Save empty Workbook'
    @Test
    fun saveEmptyWorkbook() {
        val excel = Excel.createExcel()
        assertNotNull(excel.save())
    }

    // Dart: '.xls file handling' group -> 'Sheet Remove and Rename Operations'
    @Test
    fun sheetRemoveAndRenameOperations() {
        val excelFiles = List(5) { Excel.createExcel() }
        val data = List(5) { x -> List(5) { i -> (x + 1) * (i + 1) } }

        val newName = "Sheet1Replacement"
        val defaultSheetName = "Sheet1"

        val backgroundColor = ExcelColor.values.filter { it.type == ColorType.Material }
        val fontColor = ExcelColor.values.filter { it.type == ColorType.Color }
        val borderColor = ExcelColor.values.filter { it.type == ColorType.MaterialAccent }

        for (element in excelFiles) {
            assertEquals(defaultSheetName, element.getDefaultSheet())

            for (row in data.indices) {
                for (column in data[row].indices) {
                    val border = Border(
                        borderColorHex = borderColor[column],
                        borderStyle = BorderStyle.Thin,
                    )
                    element.updateCell(
                        element.getDefaultSheet()!!,
                        CellIndex.indexByColumnRow(columnIndex = column, rowIndex = row),
                        IntCellValue(data[row][column].toLong()),
                        cellStyle = CellStyle().apply {
                            bottomBorder = border
                            topBorder = border
                            leftBorder = border
                            rightBorder = border
                            this.backgroundColor = backgroundColor[row]
                            this.fontColor = fontColor[column]
                        },
                    )
                }
            }

            if (Random.nextBoolean()) {
                // Rename test
                element.rename(element.getDefaultSheet()!!, newName)
                assertNull(element.getDefaultSheet())
                element.setDefaultSheet(newName)
                assertEquals(newName, element.getDefaultSheet())
            } else {
                // Remove test
                element.copy(element.getDefaultSheet()!!, newName)
                assertEquals(defaultSheetName, element.getDefaultSheet())
                element.delete(element.getDefaultSheet()!!)
                assertNull(element.getDefaultSheet())
                element.setDefaultSheet(newName)
                assertEquals(newName, element.getDefaultSheet())
            }

            assertEquals(1, element.tables.size)

            for (row in data.indices) {
                for (column in data[row].indices) {
                    val cell = element.tables[newName]?.rows?.get(row)?.get(column)
                    assertEquals(backgroundColor[row], cell?.cellStyle?.backgroundColor)
                    assertEquals(fontColor[column], cell?.cellStyle?.fontColor)

                    val hexes = listOf(
                        cell?.cellStyle?.bottomBorder?.borderColorHex,
                        cell?.cellStyle?.topBorder?.borderColorHex,
                        cell?.cellStyle?.leftBorder?.borderColorHex,
                        cell?.cellStyle?.rightBorder?.borderColorHex,
                    )
                    hexes.forEach { assertEquals(borderColor[column].colorHex, it) }
                }
            }
        }
    }
}
