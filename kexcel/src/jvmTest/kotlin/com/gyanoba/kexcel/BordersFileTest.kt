package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.utils.toExcelColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Border tests from the Dart `excel` port that read `.xlsx` fixtures from disk.
 */
class BordersFileTest {

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
}