package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The edges of the sheet grid.
 *
 * Excel's grid is 16384 columns (A..XFD) by 1048576 rows, so the last legal cell is
 * `XFD1048576` — column index 16383, row index 1048575. Both ends matter: the last
 * addressable cell has to work, and the first cell past it has to be refused rather than
 * written into a file Excel will reject.
 *
 * The bound was previously also applied to the sheet's row/column *counts*, which are one
 * greater than the largest index in use. Writing the last column pushed the count to
 * 16384, and every subsequent read of any cell on the sheet then threw.
 */
class SheetLimitsTest {

    private val lastColumnIndex = 16383   // XFD
    private val lastRowIndex = 1048575    // 1048576 in Excel's 1-based numbering

    // region --- The last legal cell is usable ---

    @Test
    fun theLastColumnCanBeWrittenAndReadBack() {
        val sheet = Excel.createExcel()["Sheet1"]
        val xfd1 = CellIndex.indexByColumnRow(columnIndex = lastColumnIndex, rowIndex = 0)

        sheet.updateCell(xfd1, TextCellValue("edge"))

        assertEquals(TextCellValue("edge"), sheet.cell(xfd1).value)
        assertEquals(16384, sheet.maxColumns)
    }

    @Test
    fun theLastRowCanBeWrittenAndReadBack() {
        val sheet = Excel.createExcel()["Sheet1"]
        val a1048576 = CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = lastRowIndex)

        sheet.updateCell(a1048576, TextCellValue("edge"))

        assertEquals(TextCellValue("edge"), sheet.cell(a1048576).value)
        assertEquals(1048576, sheet.maxRows)
    }

    @Test
    fun theLastCellDoesNotLockTheRestOfTheSheet() {
        // The regression this guards: once the count reached the limit, every later access
        // — including to A1 — threw "Reached Max ... value".
        val sheet = Excel.createExcel()["Sheet1"]
        sheet.updateCell(CellIndex.indexByColumnRow(lastColumnIndex, 0), TextCellValue("edge"))

        sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(1))

        assertEquals(IntCellValue(1), sheet.cell(CellIndex.indexByString("A1")).value)
        assertNull(sheet.cell(CellIndex.indexByColumnRow(5, 0)).value)
    }

    @Test
    fun theLastColumnSurvivesASaveAndReload() {
        val excel = Excel.createExcel()
        val xfd1 = CellIndex.indexByColumnRow(columnIndex = lastColumnIndex, rowIndex = 0)
        excel["Sheet1"].updateCell(xfd1, TextCellValue("edge"))

        val reread = Excel.decodeBytes(excel.encode()!!)["Sheet1"]

        assertEquals(TextCellValue("edge"), reread.cell(xfd1).value)
        assertEquals("XFD1", xfd1.cellId)
    }

    @Test
    fun theLastRowSurvivesASaveAndReload() {
        val excel = Excel.createExcel()
        val cell = CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = lastRowIndex)
        excel["Sheet1"].updateCell(cell, TextCellValue("edge"))

        val reread = Excel.decodeBytes(excel.encode()!!)["Sheet1"]

        assertEquals(TextCellValue("edge"), reread.cell(cell).value)
        assertEquals("A1048576", cell.cellId)
    }

    // endregion

    // region --- Past the edge is refused ---

    @Test
    fun writingPastTheLastColumnIsRefused() {
        val sheet = Excel.createExcel()["Sheet1"]

        val error = assertFailsWith<IllegalArgumentException> {
            sheet.updateCell(CellIndex.indexByColumnRow(16384, 0), TextCellValue("too far"))
        }
        assertTrue(error.message.orEmpty().contains("16384"))
    }

    @Test
    fun writingPastTheLastRowIsRefused() {
        val sheet = Excel.createExcel()["Sheet1"]

        val error = assertFailsWith<IllegalArgumentException> {
            sheet.updateCell(CellIndex.indexByColumnRow(0, 1048576), TextCellValue("too far"))
        }
        assertTrue(error.message.orEmpty().contains("1048576"))
    }

    @Test
    fun readingPastTheEdgeIsRefusedToo() {
        val sheet = Excel.createExcel()["Sheet1"]

        assertFailsWith<IllegalArgumentException> { sheet.cell(CellIndex.indexByColumnRow(16384, 0)) }
        assertFailsWith<IllegalArgumentException> { sheet.cell(CellIndex.indexByColumnRow(0, 1048576)) }
    }

    @Test
    fun negativeIndicesAreRefusedAtBothEnds() {
        val sheet = Excel.createExcel()["Sheet1"]

        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                sheet.cell(CellIndex.indexByColumnRow(-1, 0))
            }.message.orEmpty().contains("Negative columnIndex"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                sheet.cell(CellIndex.indexByColumnRow(0, -1))
            }.message.orEmpty().contains("Negative rowIndex"),
        )
    }

    // endregion

    // region --- Column-letter arithmetic holds across the whole range ---

    @Test
    fun columnLettersRoundTripAtTheBoundariesOfEachWidth() {
        // One, two and three letter columns, including every roll-over point.
        val cases = mapOf(
            0 to "A", 25 to "Z", 26 to "AA", 51 to "AZ", 52 to "BA",
            701 to "ZZ", 702 to "AAA", 16383 to "XFD",
        )

        cases.forEach { (index, letters) ->
            val cell = CellIndex.indexByColumnRow(columnIndex = index, rowIndex = 0)
            assertEquals("${letters}1", cell.cellId, "column $index should be $letters")
            assertEquals(
                index,
                CellIndex.indexByString("${letters}1").columnIndex,
                "$letters should parse back to column $index",
            )
        }
    }

    @Test
    fun wideColumnsSurviveASaveAndReload() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        val cells = listOf("Z1", "AA1", "AZ1", "BA1", "ZZ1", "AAA1", "XFD1")
        cells.forEach { sheet.updateCell(CellIndex.indexByString(it), TextCellValue(it)) }

        val reread = Excel.decodeBytes(excel.encode()!!)["Sheet1"]

        cells.forEach {
            assertEquals(TextCellValue(it), reread.cell(CellIndex.indexByString(it)).value, "cell $it")
        }
    }

    // endregion

    // region --- A far-away cell does not materialise the grid before it ---

    @Test
    fun aSingleFarAwayCellLeavesEverythingBeforeItEmpty() {
        val sheet = Excel.createExcel()["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("D5000"), IntCellValue(1))

        assertEquals(5000, sheet.maxRows)
        assertEquals(4, sheet.maxColumns)
        assertNull(sheet.cell(CellIndex.indexByString("A1")).value)
        assertNull(sheet.cell(CellIndex.indexByString("D4999")).value)
        assertEquals(IntCellValue(1), sheet.cell(CellIndex.indexByString("D5000")).value)
    }

    @Test
    fun aSparseSheetOnlyWritesTheRowsThatHoldData() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(1))
        sheet.updateCell(CellIndex.indexByString("A2000"), IntCellValue(2))

        val reread = Excel.decodeBytes(excel.encode()!!)["Sheet1"]

        assertEquals(2000, reread.maxRows)
        assertEquals(IntCellValue(1), reread.cell(CellIndex.indexByString("A1")).value)
        assertEquals(IntCellValue(2), reread.cell(CellIndex.indexByString("A2000")).value)
        assertTrue(
            reread.rows.subList(1, 1999).all { row -> row.all { it == null } },
            "the rows between should have stayed empty",
        )
    }

    // endregion
}
