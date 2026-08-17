package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.sheet.TextCellValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for the row/column structure of a [Sheet]: reading cells and ranges, and the
 * insert/remove/clear operations that shift the grid around.
 */
class SheetStructureTest {

    /** A 3x3 sheet whose cell A1 holds "r0c0", B1 "r0c1", A2 "r1c0" and so on. */
    private fun grid(rows: Int = 3, columns: Int = 3): Sheet {
        val sheet = Excel.createExcel()["Sheet1"]
        for (r in 0 until rows) {
            sheet.insertRowIterables(List(columns) { c -> TextCellValue("r${r}c$c") }, r)
        }
        return sheet
    }

    private fun Sheet.text(cellId: String): String? =
        cell(CellIndex.indexByString(cellId)).value?.toString()

    private fun Sheet.textGrid(): List<List<String?>> =
        rows.map { row -> row.map { it?.value?.toString() } }

    // region --- Reading ---

    @Test
    fun emptySheetHasNoRowsOrColumns() {
        val sheet = Excel.createExcel()["Empty"]
        assertEquals(0, sheet.maxRows)
        assertEquals(0, sheet.maxColumns)
        assertEquals(emptyList(), sheet.rows)
        assertEquals(emptyList(), sheet.row(0))
        assertEquals(emptyList(), sheet.selectRangeWithString("A1:B2"))
    }

    @Test
    fun rowsExposeADenseGrid() {
        val sheet = grid()
        assertEquals(3, sheet.maxRows)
        assertEquals(3, sheet.maxColumns)
        assertEquals(
            listOf(
                listOf("r0c0", "r0c1", "r0c2"),
                listOf("r1c0", "r1c1", "r1c2"),
                listOf("r2c0", "r2c1", "r2c2"),
            ),
            sheet.textGrid(),
        )
    }

    @Test
    fun sparseCellsArePaddedWithNulls() {
        val sheet = Excel.createExcel()["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("C3"), TextCellValue("only"))
        assertEquals(3, sheet.maxRows)
        assertEquals(3, sheet.maxColumns)
        assertEquals(
            listOf(
                listOf(null, null, null),
                listOf(null, null, null),
                listOf(null, null, "only"),
            ),
            sheet.textGrid(),
        )
    }

    @Test
    fun rowReturnsASingleRowAndEmptyListPastTheEnd() {
        val sheet = grid()
        assertEquals(listOf("r1c0", "r1c1", "r1c2"), sheet.row(1).map { it?.value?.toString() })
        assertEquals(emptyList(), sheet.row(3))
        assertEquals(emptyList(), sheet.row(-1))
    }

    @Test
    fun cellCreatesMissingCellsAndGrowsTheSheet() {
        val sheet = Excel.createExcel()["Sheet1"]
        val cell = sheet.cell(CellIndex.indexByString("B4"))
        assertNull(cell.value)
        assertEquals(1, cell.columnIndex)
        assertEquals(3, cell.rowIndex)
        assertEquals(CellIndex.indexByString("B4"), cell.cellIndex)
        assertEquals("Sheet1", cell.sheetName)
        // Reading a cell materialises it, so the sheet now spans up to B4.
        assertEquals(4, sheet.maxRows)
        assertEquals(2, sheet.maxColumns)
        // The same cell object comes back on the next read.
        assertSame(cell, sheet.cell(CellIndex.indexByString("B4")))
    }

    @Test
    fun negativeAndOversizedIndicesAreRejected() {
        val sheet = grid()
        assertFailsWith<IllegalArgumentException> {
            sheet.cell(CellIndex.indexByColumnRow(columnIndex = -1, rowIndex = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            sheet.cell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = -1))
        }
        // Excel's hard limits: 16384 columns (XFD) and 1048576 rows.
        assertFailsWith<IllegalArgumentException> {
            sheet.cell(CellIndex.indexByColumnRow(columnIndex = 16384, rowIndex = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            sheet.cell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 1048576))
        }
    }

    // endregion

    // region --- Ranges ---

    @Test
    fun selectRangeReturnsTheRequestedRectangle() {
        val sheet = grid()
        assertEquals(
            listOf(listOf("r0c0", "r0c1"), listOf("r1c0", "r1c1")),
            sheet.selectRangeWithString("A1:B2").map { row -> row!!.map { it?.value?.toString() } },
        )
        assertEquals(
            listOf(listOf("r1c1", "r1c2"), listOf("r2c1", "r2c2")),
            sheet.selectRange(CellIndex.indexByString("B2"), CellIndex.indexByString("C3"))
                .map { row -> row!!.map { it?.value?.toString() } },
        )
    }

    @Test
    fun selectRangeNormalisesReversedCorners() {
        val sheet = grid()
        assertEquals(
            sheet.selectRangeValuesWithString("A1:B2").map { r -> r.map { it?.toString() } },
            sheet.selectRangeValuesWithString("B2:A1").map { r -> r.map { it?.toString() } },
        )
    }

    @Test
    fun selectRangeWithoutAnEndRunsToTheLastUsedCell() {
        val sheet = grid()
        assertEquals(
            listOf(
                listOf("r1c1", "r1c2"),
                listOf("r2c1", "r2c2"),
            ),
            sheet.selectRangeValuesWithString("B2").map { r -> r.map { it?.toString() } },
        )
    }

    @Test
    fun selectRangeValuesUnwrapsTheCellValues() {
        val sheet = grid()
        val values = sheet.selectRangeValuesWithString("A1:B1")
        assertEquals(1, values.size)
        assertEquals(listOf(TextCellValue("r0c0"), TextCellValue("r0c1")), values[0])
    }

    @Test
    fun selectRangeYieldsNullForRowsThatHoldNoData() {
        val sheet = Excel.createExcel()["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("top"))
        sheet.updateCell(CellIndex.indexByString("A3"), TextCellValue("bottom"))

        val range = sheet.selectRangeWithString("A1:A3")
        assertEquals(3, range.size)
        assertNotNull(range[0])
        assertNull(range[1], "row 2 was never written, so it has no backing row at all")
        assertNotNull(range[2])
        // selectRangeValues flattens those absent rows into empty lists.
        assertEquals(emptyList(), sheet.selectRangeValuesWithString("A1:A3")[1])
    }

    // endregion

    // region --- Inserting and removing rows ---

    @Test
    fun insertRowShiftsLaterRowsDown() {
        val sheet = grid()
        sheet.insertRow(1)
        assertEquals(4, sheet.maxRows)
        assertEquals(
            listOf(
                listOf("r0c0", "r0c1", "r0c2"),
                listOf(null, null, null),
                listOf("r1c0", "r1c1", "r1c2"),
                listOf("r2c0", "r2c1", "r2c2"),
            ),
            sheet.textGrid(),
        )
        // The shifted cells know their new row.
        assertEquals(2, sheet.cell(CellIndex.indexByString("A3")).rowIndex)
    }

    @Test
    fun insertRowPastTheEndExtendsTheSheet() {
        val sheet = grid()
        sheet.insertRow(5)
        assertEquals(6, sheet.maxRows)
        assertEquals("r2c0", sheet.text("A3"))
        assertNull(sheet.text("A6"))
    }

    @Test
    fun insertRowIgnoresNegativeIndices() {
        val sheet = grid()
        sheet.insertRow(-1)
        assertEquals(3, sheet.maxRows)
        assertEquals("r0c0", sheet.text("A1"))
    }

    @Test
    fun removeRowShiftsLaterRowsUp() {
        val sheet = grid()
        sheet.removeRow(1)
        assertEquals(2, sheet.maxRows)
        assertEquals(
            listOf(
                listOf("r0c0", "r0c1", "r0c2"),
                listOf("r2c0", "r2c1", "r2c2"),
            ),
            sheet.textGrid(),
        )
    }

    @Test
    fun removeLastRowJustShrinksTheSheet() {
        val sheet = grid()
        sheet.removeRow(2)
        assertEquals(2, sheet.maxRows)
        assertEquals(
            listOf(
                listOf("r0c0", "r0c1", "r0c2"),
                listOf("r1c0", "r1c1", "r1c2"),
            ),
            sheet.textGrid(),
        )
    }

    @Test
    fun removeRowIgnoresOutOfRangeIndices() {
        val sheet = grid()
        sheet.removeRow(-1)
        sheet.removeRow(3)
        assertEquals(3, sheet.maxRows)
        assertEquals("r2c2", sheet.text("C3"))
    }

    // endregion

    // region --- Inserting and removing columns ---

    @Test
    fun insertColumnShiftsLaterColumnsRight() {
        val sheet = grid()
        sheet.insertColumn(1)
        assertEquals(4, sheet.maxColumns)
        assertEquals(
            listOf(
                listOf("r0c0", null, "r0c1", "r0c2"),
                listOf("r1c0", null, "r1c1", "r1c2"),
                listOf("r2c0", null, "r2c1", "r2c2"),
            ),
            sheet.textGrid(),
        )
    }

    @Test
    fun insertColumnPastTheEndExtendsTheSheet() {
        val sheet = grid()
        sheet.insertColumn(5)
        assertEquals(6, sheet.maxColumns)
        assertEquals("r0c2", sheet.text("C1"))
        assertNull(sheet.text("F1"))
    }

    @Test
    fun insertColumnIgnoresNegativeIndices() {
        val sheet = grid()
        sheet.insertColumn(-1)
        assertEquals(3, sheet.maxColumns)
        assertEquals("r0c0", sheet.text("A1"))
    }

    @Test
    fun removeColumnShiftsLaterColumnsLeft() {
        val sheet = grid()
        sheet.removeColumn(1)
        assertEquals(2, sheet.maxColumns)
        assertEquals(
            listOf(
                listOf("r0c0", "r0c2"),
                listOf("r1c0", "r1c2"),
                listOf("r2c0", "r2c2"),
            ),
            sheet.textGrid(),
        )
    }

    @Test
    fun removeLastColumnJustShrinksTheSheet() {
        val sheet = grid()
        sheet.removeColumn(2)
        assertEquals(2, sheet.maxColumns)
        assertEquals(
            listOf(
                listOf("r0c0", "r0c1"),
                listOf("r1c0", "r1c1"),
                listOf("r2c0", "r2c1"),
            ),
            sheet.textGrid(),
        )
    }

    @Test
    fun removeColumnIgnoresOutOfRangeIndices() {
        val sheet = grid()
        sheet.removeColumn(3)
        assertEquals(3, sheet.maxColumns)
        assertEquals("r2c2", sheet.text("C3"))
    }

    // endregion

    // region --- Writing rows ---

    @Test
    fun appendRowAddsAfterTheLastUsedRow() {
        val sheet = grid()
        sheet.appendRow(listOf(IntCellValue(1), IntCellValue(2)))
        assertEquals(4, sheet.maxRows)
        assertEquals(IntCellValue(1), sheet.cell(CellIndex.indexByString("A4")).value)
        assertEquals(IntCellValue(2), sheet.cell(CellIndex.indexByString("B4")).value)
        assertNull(sheet.cell(CellIndex.indexByString("C4")).value)
    }

    @Test
    fun insertRowIterablesOverwritesTheTargetRow() {
        val sheet = grid()
        sheet.insertRowIterables(listOf(TextCellValue("a"), TextCellValue("b")), 1)
        assertEquals(listOf("a", "b", "r1c2"), sheet.textGrid()[1])
        assertEquals(3, sheet.maxRows, "the row is replaced in place, not inserted")
    }

    @Test
    fun insertRowIterablesHonoursStartingColumn() {
        val sheet = grid()
        sheet.insertRowIterables(listOf(TextCellValue("p"), TextCellValue("q")), 0, startingColumn = 2)
        assertEquals(listOf("r0c0", "r0c1", "p", "q"), sheet.textGrid()[0])
        assertEquals(4, sheet.maxColumns)
    }

    @Test
    fun insertRowIterablesIgnoresEmptyRowsAndNegativeIndices() {
        val sheet = grid()
        sheet.insertRowIterables(emptyList(), 0)
        sheet.insertRowIterables(listOf(TextCellValue("x")), -1)
        assertEquals("r0c0", sheet.text("A1"))
        assertEquals(3, sheet.maxRows)
    }

    @Test
    fun insertRowIterablesCanSkipOverMergedCells() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B1"))
        sheet.insertRowIterables(
            listOf(TextCellValue("x"), TextCellValue("y"), TextCellValue("z")),
            0,
            overwriteMergedCells = false,
        )
        // A1:B1 is one visual cell, so it takes a single value ("x" lands on its last
        // column) and the remaining values continue past the merged region.
        assertEquals(listOf("r0c0", "x", "y", "z"), sheet.textGrid()[0])
    }

    // endregion

    // region --- clearRow ---

    @Test
    fun clearRowEmptiesTheCellsButKeepsTheRow() {
        val sheet = grid()
        assertTrue(sheet.clearRow(1))
        assertEquals(3, sheet.maxRows)
        assertEquals(listOf(null, null, null), sheet.textGrid()[1])
        assertEquals(listOf("r2c0", "r2c1", "r2c2"), sheet.textGrid()[2])
    }

    @Test
    fun clearRowRefusesRowsInsideAMergedRegion() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A2"), CellIndex.indexByString("B3"))
        assertEquals(false, sheet.clearRow(2))
        assertEquals("r2c2", sheet.text("C3"))
        // Rows outside the merged region can still be cleared.
        assertTrue(sheet.clearRow(0))
    }

    @Test
    fun clearRowRejectsNegativeIndices() {
        assertEquals(false, grid().clearRow(-1))
    }

    // endregion
}
