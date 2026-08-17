package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
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
 * In-memory ports of the Dart `excel` sheet-operation tests: copying, renaming and
 * deleting sheets, tracking the default sheet through those changes, and the regressions
 * proving `encode()` stays correct after newly-created, deleted or renamed sheets.
 */
class SheetOperationsTest {

    // region --- Copy, rename and delete ---

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

    // endregion

    // region --- Encoding after structural changes ---

    // Regression: https://github.com/ShreyashKore/kexcel/issues/3
    // Encoding a workbook with a sheet created on the fly used to fail, because the
    // <sheet>/<Relationship>/<Override> nodes never made it into the XML documents.
    @Test
    fun encodeWorkbookWithNewlyCreatedSheets() {
        val excel = Excel.createExcel()

        listOf("Players", "Teams").forEach { name ->
            val sheet = excel[name]
            sheet.updateCell(CellIndex.indexByColumnRow(0, 0), TextCellValue("Lastname-$name"))
            sheet.updateCell(CellIndex.indexByColumnRow(1, 0), TextCellValue("Firstname-$name"))
        }

        val bytes = excel.encode()
        assertNotNull(bytes)

        val reread = Excel.decodeBytes(bytes)
        assertEquals(listOf("Sheet1", "Players", "Teams"), reread.getSheets().keys.toList())

        listOf("Players", "Teams").forEach { name ->
            val sheet = reread[name]
            assertEquals(
                TextCellValue("Lastname-$name"),
                sheet.cell(CellIndex.indexByColumnRow(0, 0)).value,
            )
            assertEquals(
                TextCellValue("Firstname-$name"),
                sheet.cell(CellIndex.indexByColumnRow(1, 0)).value,
            )
        }
    }

    // Regression: https://github.com/ShreyashKore/kexcel/issues/3
    @Test
    fun encodeAfterDeletingTheDefaultSheet() {
        val excel = Excel.createExcel()
        excel["Players"].updateCell(CellIndex.indexByString("A1"), TextCellValue("Lastname"))
        excel.delete("Sheet1")

        val bytes = excel.encode()
        assertNotNull(bytes)

        val reread = Excel.decodeBytes(bytes)
        assertEquals(listOf("Players"), reread.getSheets().keys.toList())
        assertEquals(
            TextCellValue("Lastname"),
            reread["Players"].cell(CellIndex.indexByString("A1")).value,
        )
    }

    // Regression: https://github.com/ShreyashKore/kexcel/issues/3
    @Test
    fun encodeAfterRenamingTheDefaultSheet() {
        val excel = Excel.createExcel()
        excel.rename("Sheet1", "Players")
        excel["Players"].updateCell(CellIndex.indexByString("A1"), TextCellValue("Lastname"))

        val bytes = excel.encode()
        assertNotNull(bytes)

        val reread = Excel.decodeBytes(bytes)
        assertEquals(listOf("Players"), reread.getSheets().keys.toList())
        assertEquals(
            TextCellValue("Lastname"),
            reread["Players"].cell(CellIndex.indexByString("A1")).value,
        )
    }

    // endregion
}
