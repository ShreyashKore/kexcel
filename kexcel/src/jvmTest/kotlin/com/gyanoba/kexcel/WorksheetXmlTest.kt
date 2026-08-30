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

    // `rightToLeft` is the only thing the model owns inside <sheetView>. Everything else
    // there — <pane>, <selection>, tabSelected, zoomScale, showGridLines — is unmodelled
    // and must survive. The writer used to rebuild the element from scratch, and because
    // the parser puts *every* sheet on the RTL change list, that ran on ordinary saves.
    @Test
    fun sheetViewKeepsUnmodelledStateAcrossASave() {
        val sheetViewXml =
            """<sheetViews><sheetView tabSelected="1" zoomScale="125" showGridLines="0" workbookViewId="0">""" +
                """<pane xSplit="2" ySplit="1" topLeftCell="C2" activePane="bottomRight" state="frozen"/>""" +
                """<selection pane="bottomRight" activeCell="C2" sqref="C2"/>""" +
                """</sheetView></sheetViews>"""
        val base = Excel.createExcel().encode()!!
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$sheetViewXml<sheetData><row r="1"><c r="A1"><v>1</v></c></row></sheetData></worksheet>"""
        val original = replaceZipEntry(base, "xl/worksheets/sheet1.xml", xml.encodeToByteArray())

        val saved = Excel.decodeBytes(original).encode()!!
        val view = Ksoup.parseXml(readZipEntry(saved, "xl/worksheets/sheet1.xml").decodeToString())
            .getElementsByTag("sheetView").first()
            ?: error("<sheetView> was dropped on save")

        assertEquals("1", view.attr("tabSelected"))
        assertEquals("125", view.attr("zoomScale"))
        assertEquals("0", view.attr("showGridLines"))

        val pane = view.getElementsByTag("pane").first() ?: error("<pane> was dropped on save")
        assertEquals("2", pane.attr("xSplit"))
        assertEquals("frozen", pane.attr("state"))
        assertEquals("C2", view.getElementsByTag("selection").first()?.attr("activeCell"))
    }

    // Turning RTL on and back off must patch just the one attribute, not replace the element.
    @Test
    fun togglingRightToLeftLeavesTheRestOfTheSheetViewAlone() {
        val base = Excel.createExcel().encode()!!
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView zoomScale="90" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" state="frozen"/></sheetView></sheetViews><sheetData/></worksheet>"""
        val original = replaceZipEntry(base, "xl/worksheets/sheet1.xml", xml.encodeToByteArray())

        val on = Excel.decodeBytes(original).also { it["Sheet1"].isRTL = true }.encode()!!
        assertEquals(true, Excel.decodeBytes(on)["Sheet1"].isRTL)

        val off = Excel.decodeBytes(on).also { it["Sheet1"].isRTL = false }.encode()!!
        assertEquals(false, Excel.decodeBytes(off)["Sheet1"].isRTL)

        val view = Ksoup.parseXml(readZipEntry(off, "xl/worksheets/sheet1.xml").decodeToString())
            .getElementsByTag("sheetView").first()!!
        assertEquals("90", view.attr("zoomScale"), "unrelated view state was lost")
        assertTrue(view.getElementsByTag("pane").isNotEmpty(), "<pane> was lost")
        assertTrue(!view.hasAttr("rightToLeft"), "rightToLeft should be gone once unset")
    }
}