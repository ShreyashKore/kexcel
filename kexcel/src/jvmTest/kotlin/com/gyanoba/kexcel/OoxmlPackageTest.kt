package com.gyanoba.kexcel

import com.fleeksoft.ksoup.Ksoup
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.utils.ExcelColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Structural invariants of the `.xlsx` package Kexcel writes, checked against the file
 * bytes rather than through a read-back.
 *
 * A reload-based assertion only proves the writer and the reader are consistent with each
 * other. These assertions are about the artifact: parts are declared in
 * `[Content_Types].xml`, relationships resolve, index-based cross-references
 * (`s` → `cellXfs`, `t="s"` → `sharedStrings.xml`) stay in range, and the elements inside a
 * worksheet appear in the order the schema's sequence demands. Excel is far stricter about
 * these than a permissive parser is, and getting them wrong shows up as "we found a
 * problem with some content" rather than as a wrong value.
 */
class OoxmlPackageTest {

    private fun ByteArray.part(name: String) =
        Ksoup.parseXml(readZipEntry(this, name).decodeToString())

    /** Element order required inside `<worksheet>` by the CT_Worksheet sequence. */
    private val worksheetChildOrder = listOf(
        "sheetPr", "dimension", "sheetViews", "sheetFormatPr", "cols", "sheetData",
        "sheetCalcPr", "sheetProtection", "protectedRanges", "scenarios", "autoFilter",
        "sortState", "dataConsolidate", "customSheetViews", "mergeCells", "phoneticPr",
        "conditionalFormatting", "dataValidations", "hyperlinks", "printOptions",
        "pageMargins", "pageSetup", "headerFooter", "rowBreaks", "colBreaks",
        "customProperties", "cellWatches", "ignoredErrors", "smartTags", "drawing",
        "legacyDrawing", "legacyDrawingHF", "picture", "oleObjects", "controls",
        "webPublishItems", "tableParts", "extLst",
    )

    private fun assertWorksheetChildrenAreInSchemaOrder(bytes: ByteArray, part: String) {
        val worksheet = assertNotNull(bytes.part(part).getElementsByTag("worksheet").first())
        val positions = worksheet.children()
            .map { it.tagName() }
            .filter { it in worksheetChildOrder }
            .map { it to worksheetChildOrder.indexOf(it) }

        assertEquals(
            positions.sortedBy { it.second },
            positions,
            "<worksheet> children are out of schema order in $part: ${positions.map { it.first }}",
        )
    }

    // region --- Package layout ---

    @Test
    fun aNewWorkbookDeclaresEveryPartItShips() {
        val bytes = Excel.createExcel().encode()!!
        val entries = zipEntryNames(bytes).filterNot { it.endsWith("/") }

        val types = bytes.part("[Content_Types].xml")
        val defaults = types.getElementsByTag("Default").map { it.attr("Extension").lowercase() }.toSet()
        val overrides = types.getElementsByTag("Override").map { it.attr("PartName") }.toSet()

        entries.filterNot { it == "[Content_Types].xml" }.forEach { entry ->
            val declared = "/$entry" in overrides || entry.substringAfterLast('.').lowercase() in defaults
            assertTrue(declared, "part `$entry` has no content type (no Override, no Default extension)")
        }
    }

    @Test
    fun theZipHasNoDuplicateEntries() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("x"))
        excel["Extra"].updateCell(CellIndex.indexByString("A1"), TextCellValue("y"))

        val names = zipEntryNames(excel.encode()!!)
        assertEquals(names.distinct(), names, "duplicate zip entries: ${names.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }

    @Test
    fun everyWorkbookRelationshipTargetExists() {
        val excel = Excel.createExcel()
        excel["Second"].updateCell(CellIndex.indexByString("A1"), TextCellValue("y"))
        val bytes = excel.encode()!!
        val entries = zipEntryNames(bytes).toSet()

        bytes.part("xl/_rels/workbook.xml.rels").getElementsByTag("Relationship").forEach { rel ->
            if (rel.attr("TargetMode") == "External") return@forEach
            val target = rel.attr("Target").removePrefix("/")
            val resolved = if (target.startsWith("xl/")) target else "xl/$target"
            assertTrue(resolved in entries, "relationship ${rel.attr("Id")} points at missing part `$resolved`")
        }
    }

    @Test
    fun addingASheetAddsItsPartItsOverrideAndItsRelationship() {
        val excel = Excel.createExcel()
        excel["Added"].updateCell(CellIndex.indexByString("A1"), TextCellValue("v"))
        val bytes = excel.encode()!!

        val sheetEl = assertNotNull(
            bytes.part("xl/workbook.xml").getElementsByTag("sheet").firstOrNull { it.attr("name") == "Added" },
            "new sheet is missing from workbook.xml",
        )
        val relId = sheetEl.attr("r:id").ifEmpty { sheetEl.attr("id") }

        val rel = assertNotNull(
            bytes.part("xl/_rels/workbook.xml.rels").getElementsByTag("Relationship")
                .firstOrNull { it.attr("Id") == relId },
            "new sheet has no workbook relationship",
        )
        val target = rel.attr("Target").removePrefix("/").let { if (it.startsWith("xl/")) it else "xl/$it" }

        assertTrue(target in zipEntryNames(bytes), "new sheet part `$target` is not in the archive")
        assertTrue(
            bytes.part("[Content_Types].xml").getElementsByTag("Override")
                .any { it.attr("PartName") == "/$target" },
            "new sheet part `$target` has no content type override",
        )
    }

    @Test
    fun deletingASheetRemovesItFromTheWorkbookAndTheContentTypes() {
        val excel = Excel.createExcel()
        excel["Keep"].updateCell(CellIndex.indexByString("A1"), TextCellValue("k"))
        excel["Drop"].updateCell(CellIndex.indexByString("A1"), TextCellValue("d"))

        val before = excel.encode()!!
        val droppedTarget = before.part("xl/workbook.xml").getElementsByTag("sheet")
            .first { it.attr("name") == "Drop" }
            .let { el -> el.attr("r:id").ifEmpty { el.attr("id") } }
            .let { relId ->
                before.part("xl/_rels/workbook.xml.rels").getElementsByTag("Relationship")
                    .first { it.attr("Id") == relId }.attr("Target")
            }
            .removePrefix("/").let { if (it.startsWith("xl/")) it else "xl/$it" }

        val reopened = Excel.decodeBytes(before)
        reopened.delete("Drop")
        val after = reopened.encode()!!

        assertTrue(
            after.part("xl/workbook.xml").getElementsByTag("sheet").none { it.attr("name") == "Drop" },
            "deleted sheet is still listed in workbook.xml",
        )
        assertTrue(droppedTarget !in zipEntryNames(after), "deleted sheet part `$droppedTarget` is still in the archive")
        assertTrue(
            after.part("[Content_Types].xml").getElementsByTag("Override")
                .none { it.attr("PartName") == "/$droppedTarget" },
            "deleted sheet still has a content type override",
        )
        assertEquals(setOf("Sheet1", "Keep"), Excel.decodeBytes(after).tables.keys)
    }

    // endregion

    // region --- Cross-references stay in range ---

    @Test
    fun everyCellStyleIndexPointsAtAnExistingCellXf() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("plain"))
        sheet.updateCell(
            CellIndex.indexByString("A2"), TextCellValue("bold"),
            cellStyle = CellStyle(bold = true, fontColorHex = ExcelColor.red),
        )
        sheet.updateCell(
            CellIndex.indexByString("A3"), DoubleCellValue(1.5),
            cellStyle = CellStyle(numberFormat = com.gyanoba.kexcel.number_format.CustomNumericNumFormat("0.000")),
        )
        val bytes = excel.encode()!!

        val cellXfCount = bytes.part("xl/styles.xml")
            .getElementsByTag("cellXfs").first()!!.getElementsByTag("xf").size
        assertTrue(cellXfCount > 0, "styles.xml has no <cellXfs> entries")

        bytes.part("xl/worksheets/sheet1.xml").getElementsByTag("c").forEach { c ->
            val s = c.attr("s")
            if (s.isEmpty()) return@forEach
            val index = assertNotNull(s.toIntOrNull(), "cell ${c.attr("r")} has a non-numeric s=`$s`")
            assertTrue(
                index in 0 until cellXfCount,
                "cell ${c.attr("r")} references cellXf $index, but only $cellXfCount exist",
            )
        }
    }

    @Test
    fun everySharedStringIndexPointsAtAnExistingItem() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        listOf("alpha", "beta", "alpha", "gamma").forEachIndexed { i, s ->
            sheet.updateCell(CellIndex.indexByColumnRow(0, i), TextCellValue(s))
        }
        val bytes = excel.encode()!!

        val itemCount = bytes.part("xl/sharedStrings.xml").getElementsByTag("si").size

        bytes.part("xl/worksheets/sheet1.xml").getElementsByTag("c")
            .filter { it.attr("t") == "s" }
            .forEach { c ->
                val raw = c.getElementsByTag("v").first()?.text().orEmpty()
                val index = assertNotNull(raw.toIntOrNull(), "cell ${c.attr("r")} has a non-numeric shared index")
                assertTrue(
                    index in 0 until itemCount,
                    "cell ${c.attr("r")} references shared string $index, but only $itemCount exist",
                )
            }
    }

    @Test
    fun theSharedStringHeaderCountsMatchTheTableAndItsUses() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        // Three string cells over two distinct strings.
        listOf("alpha", "beta", "alpha").forEachIndexed { i, s ->
            sheet.updateCell(CellIndex.indexByColumnRow(0, i), TextCellValue(s))
        }
        val bytes = excel.encode()!!

        val sst = parseSst(bytes)
        val items = bytes.part("xl/sharedStrings.xml").getElementsByTag("si").size
        val uses = bytes.part("xl/worksheets/sheet1.xml").getElementsByTag("c").count { it.attr("t") == "s" }

        // uniqueCount must equal the number of <si> children; count must equal the number
        // of cells referencing them. Excel repairs the file when they disagree.
        assertEquals(items, sst.uniqueCount.toInt(), "uniqueCount does not match the number of <si> items")
        assertEquals(2, items)
        assertEquals(uses, sst.count.toInt(), "count does not match the number of referencing cells")
        assertEquals(3, uses)
    }

    // endregion

    // region --- Worksheet element order ---

    @Test
    fun aPlainWorksheetKeepsItsChildrenInSchemaOrder() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("x"))

        assertWorksheetChildrenAreInSchemaOrder(excel.encode()!!, "xl/worksheets/sheet1.xml")
    }

    @Test
    fun mergesColumnWidthsAndHeaderFootersDoNotDisturbSchemaOrder() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("x"))
        sheet.merge(CellIndex.indexByString("B1"), CellIndex.indexByString("C2"))
        sheet.setColumnWidth(0, 30.0)
        sheet.setRowHeight(0, 25.0)
        sheet.setDefaultColumnWidth(18.0)
        sheet.headerFooter = com.gyanoba.kexcel.sheet.HeaderFooter(
            alignWithMargins = true,
            differentFirst = false,
            differentOddEven = false,
            scaleWithDoc = true,
            evenFooter = null,
            evenHeader = null,
            firstFooter = null,
            firstHeader = null,
            oddFooter = "&CPage &P",
            oddHeader = "&CTitle",
        )
        sheet.isRTL = true

        val bytes = excel.encode()!!
        assertWorksheetChildrenAreInSchemaOrder(bytes, "xl/worksheets/sheet1.xml")

        // And again after a reload-and-save, which takes the "element already exists" paths.
        assertWorksheetChildrenAreInSchemaOrder(
            Excel.decodeBytes(bytes).encode()!!,
            "xl/worksheets/sheet1.xml",
        )
    }

    @Test
    fun rowsAndCellsAreWrittenInAscendingOrder() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        // Written back-to-front on purpose: Excel requires ascending `r` on both axes.
        listOf("D9", "A1", "C3", "B2").forEach {
            sheet.updateCell(CellIndex.indexByString(it), TextCellValue(it))
        }
        sheet.updateCell(CellIndex.indexByString("A9"), IntCellValue(1))

        val sheetData = assertNotNull(
            excel.encode()!!.part("xl/worksheets/sheet1.xml").getElementsByTag("sheetData").first()
        )

        val rowNumbers = sheetData.getElementsByTag("row").map { it.attr("r").toInt() }
        assertEquals(rowNumbers.sorted(), rowNumbers, "<row> elements are not in ascending order")

        sheetData.getElementsByTag("row").forEach { row ->
            val columns = row.getElementsByTag("c").map { CellIndex.indexByString(it.attr("r")).columnIndex }
            assertEquals(columns.sorted(), columns, "<c> elements in row ${row.attr("r")} are not in ascending order")
        }
    }

    // endregion
}
