package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.utils.ExcelColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for merged regions: which cells survive a merge, how overlapping merges are
 * folded together, how merges follow row/column shifts, and un-merging.
 */
class SheetMergeTest {

    private fun grid(rows: Int = 4, columns: Int = 4): Sheet {
        val sheet = Excel.createExcel()["Sheet1"]
        for (r in 0 until rows) {
            sheet.insertRowIterables(List(columns) { c -> TextCellValue("r${r}c$c") }, r)
        }
        return sheet
    }

    private fun Sheet.text(cellId: String): String? =
        cell(CellIndex.indexByString(cellId)).value?.toString()

    // region --- Merging ---

    @Test
    fun mergeKeepsTheFirstValueAndClearsTheRest() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))

        assertEquals(listOf("A1:B2"), sheet.spannedItems)
        assertEquals("r0c0", sheet.text("A1"))
        assertNull(sheet.text("B1"))
        assertNull(sheet.text("A2"))
        assertNull(sheet.text("B2"))
        // Cells outside the region are untouched.
        assertEquals("r0c2", sheet.text("C1"))
    }

    @Test
    fun mergePicksUpTheFirstNonEmptyValueInTheRegion() {
        val sheet = Excel.createExcel()["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("B2"), TextCellValue("found me"))
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("C3"))
        assertEquals("found me", sheet.text("A1"))
    }

    @Test
    fun mergeCanBeGivenAnExplicitValue() {
        val sheet = grid()
        sheet.merge(
            CellIndex.indexByString("A1"),
            CellIndex.indexByString("B2"),
            customValue = TextCellValue("custom"),
        )
        assertEquals("custom", sheet.text("A1"))
    }

    @Test
    fun mergeNormalisesReversedCorners() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("C3"), CellIndex.indexByString("A1"))
        assertEquals(listOf("A1:C3"), sheet.spannedItems)
    }

    @Test
    fun mergeIsIgnoredForDegenerateOrDuplicateRegions() {
        val sheet = grid()
        // A single cell is not a region.
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("A1"))
        assertEquals(emptyList(), sheet.spannedItems)

        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))
        assertEquals(listOf("A1:B2"), sheet.spannedItems, "the same region must not be merged twice")
    }

    @Test
    fun overlappingMergesAreFoldedIntoTheirBoundingBox() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))
        sheet.merge(CellIndex.indexByString("B2"), CellIndex.indexByString("C3"))
        assertEquals(listOf("A1:C3"), sheet.spannedItems)
    }

    @Test
    fun disjointMergesCoexist() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B1"))
        sheet.merge(CellIndex.indexByString("C3"), CellIndex.indexByString("D4"))
        assertEquals(listOf("A1:B1", "C3:D4"), sheet.spannedItems)
    }

    @Test
    fun writingInsideAMergedRegionRedirectsToItsAnchor() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))
        sheet.updateCell(CellIndex.indexByString("B2"), TextCellValue("late"))
        assertEquals("late", sheet.text("A1"))
        assertNull(sheet.text("B2"))
    }

    // endregion

    // region --- Un-merging ---

    @Test
    fun unMergeDropsTheRegion() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))
        sheet.merge(CellIndex.indexByString("C3"), CellIndex.indexByString("D4"))

        sheet.unMerge("A1:B2")
        assertEquals(listOf("C3:D4"), sheet.spannedItems)

        sheet.unMerge("C3:D4")
        assertEquals(emptyList(), sheet.spannedItems)
    }

    @Test
    fun unMergeIgnoresRegionsThatWereNeverMerged() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))
        sheet.unMerge("C1:D2")
        sheet.unMerge("nonsense")
        assertEquals(listOf("A1:B2"), sheet.spannedItems)
    }

    @Test
    fun unMergedCellsStayEmptyAndTheAnchorKeepsItsValue() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))
        sheet.unMerge("A1:B2")
        assertEquals("r0c0", sheet.text("A1"))
        assertNull(sheet.text("B2"), "merging discarded the covered values; un-merging does not restore them")
        // Writing to a freed cell now stays where it was put.
        sheet.updateCell(CellIndex.indexByString("B2"), TextCellValue("back"))
        assertEquals("back", sheet.text("B2"))
    }

    // endregion

    // region --- Merges under row/column edits ---

    @Test
    fun insertingARowAboveAMergePushesItDown() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("B2"), CellIndex.indexByString("C3"))
        sheet.insertRow(0)
        assertEquals(listOf("B3:C4"), sheet.spannedItems)
    }

    @Test
    fun insertingAColumnBeforeAMergePushesItRight() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("B2"), CellIndex.indexByString("C3"))
        sheet.insertColumn(0)
        assertEquals(listOf("C2:D3"), sheet.spannedItems)
    }

    @Test
    fun removingARowAboveAMergePullsItUp() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("B2"), CellIndex.indexByString("C3"))
        sheet.removeRow(0)
        assertEquals(listOf("B1:C2"), sheet.spannedItems)
    }

    @Test
    fun removingAColumnBeforeAMergePullsItLeft() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("B2"), CellIndex.indexByString("C3"))
        sheet.removeColumn(0)
        assertEquals(listOf("A2:B3"), sheet.spannedItems)
    }

    // endregion

    // region --- setMergedCellStyle ---

    @Test
    fun setMergedCellStylePutsBordersOnlyOnTheOutsideEdges() {
        val sheet = grid()
        val start = CellIndex.indexByString("A1")
        val end = CellIndex.indexByString("C3")
        sheet.merge(start, end)

        val border = Border(borderStyle = BorderStyle.Medium, borderColorHex = ExcelColor.black)
        sheet.setMergedCellStyle(
            start,
            CellStyle(leftBorder = border, rightBorder = border, topBorder = border, bottomBorder = border),
        )

        for (row in 0..2) {
            for (column in 0..2) {
                val style = sheet.cell(CellIndex.indexByColumnRow(columnIndex = column, rowIndex = row)).cellStyle
                assertEquals(if (row == 0) border else Border(), style?.topBorder)
                assertEquals(if (row == 2) border else Border(), style?.bottomBorder)
                assertEquals(if (column == 0) border else Border(), style?.leftBorder)
                assertEquals(if (column == 2) border else Border(), style?.rightBorder)
            }
        }
    }

    @Test
    fun setMergedCellStyleIgnoresCellsThatDoNotStartAMerge() {
        val sheet = grid()
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("C3"))
        val border = Border(borderStyle = BorderStyle.Medium)

        // B2 is inside the region but is not its anchor, so nothing is applied.
        sheet.setMergedCellStyle(CellIndex.indexByString("B2"), CellStyle(topBorder = border))
        assertTrue(sheet.cell(CellIndex.indexByString("B2")).cellStyle.let { it == null || it.topBorder == Border() })
    }

    // endregion

    // region --- Workbook-level wrappers ---

    @Test
    fun workbookMergeApiMirrorsTheSheetApi() {
        val excel = Excel.createExcel()
        excel.merge("Sheet1", CellIndex.indexByString("A1"), CellIndex.indexByString("B2"))
        assertEquals(listOf("A1:B2"), excel.getMergedCells("Sheet1"))

        excel.unMerge("Sheet1", "A1:B2")
        assertEquals(emptyList(), excel.getMergedCells("Sheet1"))

        // Unknown sheets answer with an empty list rather than throwing.
        assertEquals(emptyList(), excel.getMergedCells("Missing"))
    }

    @Test
    fun mergedRegionsSurviveASaveAndReload() {
        val excel = Excel.createExcel()
        excel.merge(
            "Sheet1",
            CellIndex.indexByString("A1"),
            CellIndex.indexByString("C2"),
            customValue = TextCellValue("header"),
        )
        excel.merge("Sheet1", CellIndex.indexByString("A4"), CellIndex.indexByString("B5"))

        val reread = Excel.decodeBytes(excel.encode()!!)
        assertEquals(listOf("A1:C2", "A4:B5"), reread.getMergedCells("Sheet1"))
        assertEquals("header", reread["Sheet1"].text("A1"))
    }

    // endregion
}
