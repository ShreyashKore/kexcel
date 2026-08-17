package com.gyanoba.kexcel

import com.fleeksoft.ksoup.Ksoup
import com.gyanoba.kexcel.sheet.CellIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Writer-level XML details in the worksheet part: the `<mergeCells>` count attribute,
 * dropping `<mergeCells>` entirely once nothing is merged, and preserving unmodelled
 * `<sheetFormatPr>` attributes across a round trip.
 */
class WorksheetXmlTest {

    // The <mergeCells count="..."> attribute must track the number of <mergeCell> children,
    // and no bogus `value` attribute may be emitted alongside it.
    @Test
    fun mergeCellsCountAttribute() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("C3"))

        val firstPass = excel.encode()!!
        assertMergeCellsCount(firstPass, 1)

        // A second round trip goes through the "<mergeCells> already exists" branch.
        val reopened = Excel.decodeBytes(firstPass)
        reopened["Sheet1"].merge(CellIndex.indexByString("E1"), CellIndex.indexByString("F2"))
        assertMergeCellsCount(reopened.encode()!!, 2)
    }

    // Unmerging the last merged range must drop <mergeCells>, otherwise the stale refs
    // resurrect the merge on the next read.
    @Test
    fun unMergingEveryRangeRemovesMergeCells() {
        val excel = Excel.createExcel()
        excel["Sheet1"].merge(CellIndex.indexByString("A1"), CellIndex.indexByString("C3"))
        excel["Sheet1"].merge(CellIndex.indexByString("E1"), CellIndex.indexByString("F2"))

        val reopened = Excel.decodeBytes(excel.encode()!!)
        assertEquals(listOf("A1:C3", "E1:F2"), reopened.getMergedCells("Sheet1"))

        reopened["Sheet1"].unMerge("A1:C3")
        val partial = reopened.encode()!!
        assertMergeCellsCount(partial, 1)
        assertEquals(listOf("E1:F2"), Excel.decodeBytes(partial).getMergedCells("Sheet1"))

        val last = Excel.decodeBytes(partial)
        last["Sheet1"].unMerge("E1:F2")
        val cleared = last.encode()!!

        val xml = readZipEntry(cleared, "xl/worksheets/sheet1.xml").decodeToString()
        assertTrue(
            Ksoup.parseXml(xml).getElementsByTag("mergeCells").isEmpty(),
            "<mergeCells> should be gone once nothing is merged",
        )
        assertEquals(emptyList(), Excel.decodeBytes(cleared).getMergedCells("Sheet1"))
    }

    // <sheetFormatPr> carries attributes the model does not know about (baseColWidth,
    // outlineLevelRow, zeroHeight, …); a round trip must not drop them.
    @Test
    fun sheetFormatPrKeepsUnmodelledAttributes() {
        val original = fixture("example.xlsx")
        val before = Ksoup.parseXml(readZipEntry(original, "xl/worksheets/sheet1.xml").decodeToString())
            .getElementsByTag("sheetFormatPr").first()
            ?: error("fixture has no <sheetFormatPr> to preserve")
        val unmodelled = before.attributes()
            .map { it.key }
            .filter { it != "defaultRowHeight" && it != "defaultColWidth" }
        assertTrue(unmodelled.isNotEmpty(), "fixture should carry unmodelled attributes")

        val saved = Excel.decodeBytes(original).encode()!!
        val after = Ksoup.parseXml(readZipEntry(saved, "xl/worksheets/sheet1.xml").decodeToString())
            .getElementsByTag("sheetFormatPr").first()
            ?: error("<sheetFormatPr> was dropped on save")

        unmodelled.forEach { key ->
            assertEquals(before.attr(key), after.attr(key), "attribute `$key` was not preserved")
        }
        // The two attributes the model does own are still written, exactly once each.
        assertEquals(1, after.attributes().count { it.key == "defaultRowHeight" })
        assertEquals(1, after.attributes().count { it.key == "defaultColWidth" })
    }
}