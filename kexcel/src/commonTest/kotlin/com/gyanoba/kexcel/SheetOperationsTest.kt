package com.gyanoba.kexcel

import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import com.gyanoba.kexcel.sheet.FormulaCellValue
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
                // The Dart original expects null here: its rename is a copy followed by a
                // delete, and deleting the default sheet clears the default. Renaming a
                // sheet does not change which sheet opens first in Excel, nor in POI
                // (XSSFWorkbook.setSheetName leaves the active sheet alone).
                assertEquals(newName, element.getDefaultSheet())
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


    @Test
    fun sheetNamesMustFollowExcelsRules() {
        // The rules POI enforces in WorkbookUtil.validateSheetName. A workbook that breaks
        // them is one Excel refuses to open or silently repairs.
        val excel = Excel.createExcel()
        for (bad in listOf("", "x".repeat(32), "a/b", "a\\b", "a?b", "a*b", "a[b", "a]b", "a:b", "'quoted'")) {
            assertFailsWith<IllegalArgumentException>("`$bad` should be refused") { excel[bad] }
            assertFailsWith<IllegalArgumentException>("`$bad` should be refused") { excel.rename("Sheet1", bad) }
        }
        // Names that differ only by case are the same name to Excel.
        assertFailsWith<IllegalArgumentException> { excel["sheet1"] }
        excel["Other"]
        assertFailsWith<IllegalArgumentException> { excel.rename("Other", "SHEET1") }
        assertEquals(setOf("Sheet1", "Other"), excel.tables.keys)

        for (good in listOf("x".repeat(31), "A&B <1> \"q\"", "It's fine", " leading space", "Données")) {
            excel[good].updateCell(CellIndex.indexByString("A1"), TextCellValue(good))
        }
        val reread = Excel.decodeBytes(excel.encode()!!)
        assertEquals(TextCellValue("It's fine"), reread["It's fine"].cell(CellIndex.indexByString("A1")).value)
        // Renaming a sheet to a different spelling of its own name is allowed.
        excel.rename("Sheet1", "SHEET1")
        assertTrue("SHEET1" in excel.tables.keys)
    }

    @Test
    fun renamingASheetKeepsItsPlaceAndItsCells() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("first"))
        excel["Second"].updateCell(CellIndex.indexByString("A1"), FormulaCellValue("Sheet1!A1&\"!\""))
        excel["Third"].updateCell(CellIndex.indexByString("A1"), TextCellValue("third"))

        excel.rename("Sheet1", "Start Here")

        assertEquals(listOf("Start Here", "Second", "Third"), excel.tables.keys.toList())
        assertEquals("Start Here", excel["Start Here"].sheetName)
        assertEquals("Start Here", excel["Start Here"].cell(CellIndex.indexByString("A1")).sheetName)
        assertEquals(FormulaCellValue("'Start Here'!A1&\"!\""), excel["Second"].cell(CellIndex.indexByString("A1")).value)

        val reread = Excel.decodeBytes(excel.encode()!!)
        assertEquals(listOf("Start Here", "Second", "Third"), reread.tables.keys.toList())
        assertEquals(TextCellValue("first"), reread["Start Here"].cell(CellIndex.indexByString("A1")).value)
    }
}
