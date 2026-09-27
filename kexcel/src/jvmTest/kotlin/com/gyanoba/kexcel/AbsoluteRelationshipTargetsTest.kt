package com.gyanoba.kexcel

import com.fleeksoft.ksoup.Ksoup
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Relationship targets in `xl/_rels/workbook.xml.rels` may be relative to `xl/`
 * (`worksheets/sheet1.xml`, what Excel writes) or absolute package paths
 * (`/xl/worksheets/sheet1.xml`, what openpyxl — and therefore pandas — writes), per
 * ECMA-376 Part 2 (OPC) §9.3. Both forms must load, and must save back to the real part.
 *
 * Most tests take an existing fixture and rewrite its workbook relationships to the absolute
 * form, so every assertion can be checked against what the untouched, relative-target
 * original decodes to. `openpyxlAbsoluteTargets.xlsx` is a real openpyxl 3.1.5 file.
 */
class AbsoluteRelationshipTargetsTest {

    // region --- fixture / zip helpers ---

    private fun fixture(name: String): ByteArray {
        val candidates = listOf(
            "src/commonTest/kotlin/com/gyanoba/kexcel/test_resources/$name",
            "kexcel/src/commonTest/kotlin/com/gyanoba/kexcel/test_resources/$name",
        )
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) return f.readBytes()
        }
        error("Test fixture not found: $name (looked in $candidates, cwd=${File(".").absolutePath})")
    }

    /** Every zip entry in order, duplicates included. */
    private fun zipEntries(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries += entry.name to zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    private fun readZipEntry(bytes: ByteArray, entryName: String): String =
        zipEntries(bytes).firstOrNull { it.first == entryName }?.second?.decodeToString()
            ?: error("zip entry not found: $entryName")

    /** Re-zips [bytes], passing every `Target` in `xl/_rels/workbook.xml.rels` through [transform]. */
    private fun withWorkbookRelTargets(bytes: ByteArray, transform: (String) -> String): ByteArray {
        val targetAttr = Regex("""Target="([^"]*)"""")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zipEntries(bytes).forEach { (name, content) ->
                val rewritten = if (name == WORKBOOK_RELS) {
                    targetAttr.replace(content.decodeToString()) { m ->
                        "Target=\"${transform(m.groupValues[1])}\""
                    }.encodeToByteArray()
                } else {
                    content
                }
                zos.putNextEntry(ZipEntry(name))
                zos.write(rewritten)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** `worksheets/sheet1.xml` → `/xl/worksheets/sheet1.xml`, as openpyxl writes it. */
    private fun withAbsoluteTargets(bytes: ByteArray): ByteArray =
        withWorkbookRelTargets(bytes) { if (it.startsWith("/")) it else "/xl/$it" }

    private fun workbookRelTargets(bytes: ByteArray): List<String> =
        Ksoup.parseXml(readZipEntry(bytes, WORKBOOK_RELS))
            .getElementsByTag("Relationship")
            .filter { it.attr("TargetMode") != "External" }
            .map { it.attr("Target") }

    /**
     * Independent oracle for OPC §9.3 part-name resolution against the source part's folder
     * (`xl/`): a leading `/` is package-absolute, otherwise `.` and `..` segments are resolved.
     */
    private fun resolveAgainstXl(target: String): String {
        val segments = if (target.startsWith("/")) mutableListOf() else mutableListOf("xl")
        target.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    // endregion

    // region --- assertions ---

    /** Sheet names, every cell (value + style), shared strings and the style tables. */
    private fun assertSameWorkbook(expected: Excel, actual: Excel, label: String) {
        assertEquals(expected.tables.keys.toList(), actual.tables.keys.toList(), "$label: sheet names")
        expected.tables.forEach { (name, sheet) ->
            val other = actual.tables.getValue(name)
            assertEquals(sheet.maxRows, other.maxRows, "$label/$name: maxRows")
            assertEquals(sheet.maxColumns, other.maxColumns, "$label/$name: maxColumns")
            assertEquals(sheet.rows, other.rows, "$label/$name: cells")
        }
        assertEquals(
            expected.sharedStrings.map.keys.map { it.stringValue },
            actual.sharedStrings.map.keys.map { it.stringValue },
            "$label: shared strings",
        )
        assertEquals(expected.cellStyleList, actual.cellStyleList, "$label: cellXfs")
        assertEquals(expected.fontStyleList, actual.fontStyleList, "$label: fonts")
        assertEquals(expected.numFmtIds, actual.numFmtIds, "$label: numFmtIds")
    }

    /** No `//` entry names, no duplicates, and every workbook relationship resolves to an entry. */
    private fun assertPackageIntegrity(bytes: ByteArray, label: String) {
        val names = zipEntries(bytes).map { it.first }
        assertTrue(names.none { "//" in it }, "$label: entry names containing '//': ${names.filter { "//" in it }}")
        assertEquals(
            emptyList(),
            names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList(),
            "$label: duplicate zip entries",
        )
        workbookRelTargets(bytes).forEach { target ->
            assertTrue(
                resolveAgainstXl(target) in names,
                "$label: relationship Target=\"$target\" resolves to missing part " +
                    "${resolveAgainstXl(target)}; entries=$names",
            )
        }
    }

    // endregion

    // region --- READ ---

    @Test
    fun rewrittenFixtureReallyHasAbsoluteTargets() {
        val targets = workbookRelTargets(withAbsoluteTargets(fixture("example.xlsx")))
        assertEquals(listOf("/xl/styles.xml", "/xl/worksheets/sheet1.xml", "/xl/sharedStrings.xml"), targets)
    }

    @Test
    fun absoluteTargetsDecodeLikeRelativeTargets() {
        for (name in FIXTURES) {
            val original = fixture(name)
            assertSameWorkbook(
                expected = Excel.decodeBytes(original),
                actual = Excel.decodeBytes(withAbsoluteTargets(original)),
                label = name,
            )
        }
    }

    @Test
    fun absoluteTargetsDecodeEveryCellType() {
        val t = Excel.decodeBytes(withAbsoluteTargets(fixture("dataTypesUsingMsExcel365Desktop.xlsx")))
            .tables["Tabelle1"]!!
        assertEquals(TextCellValue("Some text"), t.rows[2][1]?.value)
        assertEquals(IntCellValue(42), t.rows[3][1]?.value)
        assertEquals(DoubleCellValue(12.3), t.rows[4][1]?.value)
        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), t.rows[5][1]?.value)
        assertEquals(
            DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13),
            t.rows[6][1]?.value,
        )
        assertEquals(BoolCellValue(true), t.rows[7][1]?.value)
        assertEquals(BoolCellValue(false), t.rows[8][1]?.value)
    }

    @Test
    fun dotSegmentTargetsDecodeLikeRelativeTargets() {
        // `./worksheets/sheet1.xml`, `../xl/styles.xml`: unusual, but valid OPC relative references.
        val forms = listOf<(String) -> String>({ "./$it" }, { "../xl/$it" }, { "/xl/./$it" })
        for (name in FIXTURES) {
            val original = fixture(name)
            forms.forEachIndexed { i, form ->
                val input = withWorkbookRelTargets(original, form)
                assertSameWorkbook(Excel.decodeBytes(original), Excel.decodeBytes(input), "$name form #$i")
                assertPackageIntegrity(Excel.decodeBytes(input).encode()!!, "$name form #$i")
            }
        }
    }

    @Test
    fun openpyxlFileDecodes() {
        // openpyxl 3.1.5: worksheets absolute (`/xl/worksheets/sheetN.xml`), styles relative,
        // no sharedStrings part at all (it writes inlineStr cells).
        val excel = Excel.decodeBytes(fixture("openpyxlAbsoluteTargets.xlsx"))
        assertEquals(listOf("Data", "Second"), excel.tables.keys.toList())

        val data = excel.tables["Data"]!!
        assertEquals(TextCellValue("Name"), data.cell(CellIndex.indexByString("A1")).value)
        assertTrue(data.cell(CellIndex.indexByString("A1")).cellStyle!!.isBold, "A1 is bold")
        assertEquals(TextCellValue("Alice"), data.cell(CellIndex.indexByString("A2")).value)
        assertEquals(IntCellValue(42), data.cell(CellIndex.indexByString("B2")).value)
        assertEquals(DoubleCellValue(12.5), data.cell(CellIndex.indexByString("C2")).value)
        assertEquals(BoolCellValue(true), data.cell(CellIndex.indexByString("D2")).value)
        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), data.cell(CellIndex.indexByString("E2")).value)
        assertEquals(BoolCellValue(false), data.cell(CellIndex.indexByString("D3")).value)
        assertEquals(
            DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13),
            data.cell(CellIndex.indexByString("E3")).value,
        )

        val second = excel.tables["Second"]!!
        assertEquals(TextCellValue("second sheet"), second.cell(CellIndex.indexByString("A1")).value)
        assertEquals(DoubleCellValue(3.25), second.cell(CellIndex.indexByString("B2")).value)
    }

    // endregion

    // region --- EDIT + SAVE ---

    /** Edits a shared-string cell and a numeric cell, saves, and checks the rest is untouched. */
    private fun assertEditSurvivesSave(input: ByteArray, label: String) {
        val before = Excel.decodeBytes(input)
        val excel = Excel.decodeBytes(input)
        val sheet = excel.tables["Sheet1"]!!
        sheet.updateCell(CellIndex.indexByString("B2"), TextCellValue("Edited by Kexcel"))
        sheet.updateCell(CellIndex.indexByString("A6"), IntCellValue(2026))

        val saved = excel.encode()!!
        val after = Excel.decodeBytes(saved).tables["Sheet1"]!!

        assertEquals(TextCellValue("Edited by Kexcel"), after.cell(CellIndex.indexByString("B2")).value, label)
        assertEquals(IntCellValue(2026), after.cell(CellIndex.indexByString("A6")).value, label)

        val original = before.tables["Sheet1"]!!
        for (row in 0 until original.maxRows) {
            for (col in 0 until original.maxColumns) {
                val id = CellIndex.indexByColumnRow(columnIndex = col, rowIndex = row)
                if (id.cellId == "B2") continue
                assertEquals(original.cell(id).value, after.cell(id).value, "$label: ${id.cellId} changed")
            }
        }

        // The edit must be in the real parts, not just in whatever Kexcel decodes back.
        val sheetXml = readZipEntry(saved, "xl/worksheets/sheet1.xml")
        assertTrue("""<c r="A6"""" in sheetXml && "<v>2026</v>" in sheetXml, "$label: A6 missing from sheet1.xml")
        assertTrue(
            "Edited by Kexcel" in readZipEntry(saved, "xl/sharedStrings.xml"),
            "$label: new string missing from xl/sharedStrings.xml",
        )
        assertPackageIntegrity(saved, label)
    }

    @Test
    fun editAndSaveWithAbsoluteTargets() {
        assertEditSurvivesSave(withAbsoluteTargets(fixture("example.xlsx")), "absolute")
    }

    @Test
    fun savePreservesAbsoluteTargets() {
        // Saving writes each part back to the path it was read from, so the producer's absolute
        // targets stay valid and are left alone rather than rewritten.
        val input = withAbsoluteTargets(fixture("example.xlsx"))
        val excel = Excel.decodeBytes(input)
        excel.tables["Sheet1"]!!.updateCell(CellIndex.indexByString("A1"), TextCellValue("x"))
        assertEquals(workbookRelTargets(input), workbookRelTargets(excel.encode()!!))
    }

    @Test
    fun editAndSaveOpenpyxlFile() {
        val excel = Excel.decodeBytes(fixture("openpyxlAbsoluteTargets.xlsx"))
        excel.tables["Data"]!!.updateCell(CellIndex.indexByString("B2"), IntCellValue(43))
        excel.tables["Second"]!!.updateCell(CellIndex.indexByString("A1"), TextCellValue("edited"))

        val saved = excel.encode()!!
        val again = Excel.decodeBytes(saved)
        assertEquals(IntCellValue(43), again.tables["Data"]!!.cell(CellIndex.indexByString("B2")).value)
        assertEquals(TextCellValue("Alice"), again.tables["Data"]!!.cell(CellIndex.indexByString("A2")).value)
        assertEquals(TextCellValue("edited"), again.tables["Second"]!!.cell(CellIndex.indexByString("A1")).value)
        assertEquals(DoubleCellValue(3.25), again.tables["Second"]!!.cell(CellIndex.indexByString("B2")).value)
        assertTrue("<v>43</v>" in readZipEntry(saved, "xl/worksheets/sheet1.xml"), "B2 missing from sheet1.xml")
        assertPackageIntegrity(saved, "openpyxl")
    }

    // endregion

    // region --- OUTPUT INTEGRITY ---

    @Test
    fun unmodifiedAbsoluteTargetRoundTripMatchesRelative() {
        for (name in FIXTURES) {
            val saved = Excel.decodeBytes(withAbsoluteTargets(fixture(name))).encode()!!
            assertPackageIntegrity(saved, name)
            // Compared with the relative-target round trip rather than the input: a save is not
            // lossless for every fixture (see unmodifiedRelativeTargetRoundTripKeepsPackageIntact),
            // but the form of the relationship targets must make no difference to it.
            assertSameWorkbook(
                expected = Excel.decodeBytes(Excel.decodeBytes(fixture(name)).encode()!!),
                actual = Excel.decodeBytes(saved),
                label = "$name round trip",
            )
        }
    }

    @Test
    fun deletingSheetWithAbsoluteTargetLeavesNoDanglingRelationship() {
        val excel = Excel.decodeBytes(withAbsoluteTargets(fixture("columnWidthRowHeight.xlsx")))
        excel.delete("Sheet2")

        val saved = excel.encode()!!
        assertPackageIntegrity(saved, "after delete")
        assertEquals(listOf("Sheet1"), Excel.decodeBytes(saved).tables.keys.toList())
    }

    // endregion

    // region --- REGRESSION GUARD (relative targets) ---

    @Test
    fun editAndSaveWithRelativeTargets() {
        assertEditSurvivesSave(fixture("example.xlsx"), "relative")
    }

    @Test
    fun unmodifiedRelativeTargetRoundTripKeepsPackageIntact() {
        // Package structure only. Cell-level fidelity of an unmodified save is not asserted
        // here: on this code line it drops unchanged cell styles, rich-text runs and phonetic
        // runs for some fixtures, which is unrelated to relationship targets.
        for (name in FIXTURES) {
            assertPackageIntegrity(Excel.decodeBytes(fixture(name)).encode()!!, name)
        }
    }

    @Test
    fun deletingSheetWithRelativeTargetLeavesNoDanglingRelationship() {
        val excel = Excel.decodeBytes(fixture("columnWidthRowHeight.xlsx"))
        excel.delete("Sheet2")

        val saved = excel.encode()!!
        assertPackageIntegrity(saved, "after delete")
        assertEquals(listOf("Sheet1"), Excel.decodeBytes(saved).tables.keys.toList())
    }

    // endregion

    private companion object {
        const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"

        /** Relative-target fixtures covering one/many sheets, typed cells, styles and rich text. */
        val FIXTURES = listOf(
            "example.xlsx",
            "dataTypesUsingMsExcel365Desktop.xlsx",
            "columnWidthRowHeight.xlsx",
            "customNumFmtIdBelow164.xlsx",
            "borders.xlsx",
            "richText.xlsx",
        )
    }
}
