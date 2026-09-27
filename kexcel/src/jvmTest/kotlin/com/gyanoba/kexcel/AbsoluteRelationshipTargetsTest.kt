package com.gyanoba.kexcel

import com.fleeksoft.ksoup.Ksoup
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.utils.ExcelColor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    /** Re-zips [bytes] in the same entry order, passing each `(name, content)` through [transform]. */
    private fun rewriteZip(bytes: ByteArray, transform: (String, ByteArray) -> Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zipEntries(bytes).forEach { (name, content) ->
                val (newName, newContent) = transform(name, content)
                zos.putNextEntry(ZipEntry(newName))
                zos.write(newContent)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Re-zips [bytes], passing every `Target` in `xl/_rels/workbook.xml.rels` through [transform]. */
    private fun withWorkbookRelTargets(bytes: ByteArray, transform: (String) -> String): ByteArray {
        val targetAttr = Regex("""Target="([^"]*)"""")
        return rewriteZip(bytes) { name, content ->
            name to if (name == WORKBOOK_RELS) {
                targetAttr.replace(content.decodeToString()) { m ->
                    "Target=\"${transform(m.groupValues[1])}\""
                }.encodeToByteArray()
            } else {
                content
            }
        }
    }

    private data class Rel(val id: String, val type: String, val target: String)

    private fun workbookRels(bytes: ByteArray): List<Rel> = parseRels(readZipEntry(bytes, WORKBOOK_RELS))

    private fun parseRels(xml: String): List<Rel> =
        Ksoup.parseXml(xml)
            .getElementsByTag("Relationship")
            .map { Rel(it.attr("Id"), it.attr("Type"), it.attr("Target")) }

    private fun contentTypeOverrides(bytes: ByteArray): List<String> =
        Ksoup.parseXml(readZipEntry(bytes, "[Content_Types].xml"))
            .getElementsByTag("Override")
            .map { it.attr("PartName") }

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
        contentTypeOverrides(bytes).forEach { partName ->
            assertTrue(
                partName.removePrefix("/") in names,
                "$label: [Content_Types].xml overrides missing part $partName; entries=$names",
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
    fun everyTargetFormDecodesLikeRelativeTargets() {
        // Dot segments (`./worksheets/sheet1.xml`, `../xl/styles.xml`) are unusual but valid OPC
        // relative references; mixed forms are what openpyxl actually writes.
        for (name in FIXTURES) {
            val original = fixture(name)
            TARGET_FORMS.forEach { (form, transform) ->
                val input = withWorkbookRelTargets(original, transform)
                assertSameWorkbook(Excel.decodeBytes(original), Excel.decodeBytes(input), "$name [$form]")
                assertPackageIntegrity(Excel.decodeBytes(input).encode()!!, "$name [$form]")
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
    fun saveKeepsEveryExistingTargetInItsOriginalForm() {
        // Each part is written back to the path it was read from, so whatever form the producer
        // used stays valid and is left alone: nothing is normalised to relative or absolute.
        for (name in FIXTURES) {
            for ((form, transform) in TARGET_FORMS + ("relative" to { target: String -> target })) {
                val input = withWorkbookRelTargets(fixture(name), transform)
                val inputRels = workbookRels(input).associateBy { it.id }
                for ((op, operation) in OPERATIONS) {
                    val saved = Excel.decodeBytes(input).also(operation).encode()!!
                    workbookRels(saved).filter { it.id in inputRels }.forEach { rel ->
                        assertEquals(inputRels.getValue(rel.id), rel, "$name [$form] after $op")
                    }
                }
            }
        }
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

    // region --- SCHEME EQUIVALENCE ---

    /**
     * The strongest statement of the fix: for every fixture and every kind of edit, saving the
     * absolute-target copy produces exactly the parts that saving the relative original does —
     * same entries, same order, same bytes. The one permitted difference is the `Target` strings
     * of relationships that were already in the input, which keep the form the producer wrote.
     */
    @Test
    fun absoluteAndRelativeTargetsSaveIdenticalParts() {
        for (name in FIXTURES) {
            val relativeInput = fixture(name)
            val absoluteInput = withAbsoluteTargets(relativeInput)
            val inputIds = workbookRels(relativeInput).map { it.id }.toSet()

            for ((op, operation) in OPERATIONS) {
                val label = "$name after $op"
                val fromRelative = zipEntries(Excel.decodeBytes(relativeInput).also(operation).encode()!!)
                val fromAbsolute = zipEntries(Excel.decodeBytes(absoluteInput).also(operation).encode()!!)

                assertEquals(fromRelative.map { it.first }, fromAbsolute.map { it.first }, "$label: entries")
                fromRelative.zip(fromAbsolute).forEach { (rel, abs) ->
                    if (rel.first != WORKBOOK_RELS) {
                        assertTrue(rel.second.contentEquals(abs.second), "$label: ${rel.first} differs")
                    }
                }

                val relRelsXml = fromRelative.first { it.first == WORKBOOK_RELS }.second.decodeToString()
                val absRelsXml = fromAbsolute.first { it.first == WORKBOOK_RELS }.second.decodeToString()
                val relRels = parseRels(relRelsXml)
                val absRels = parseRels(absRelsXml)
                assertEquals(relRels.map { it.id to it.type }, absRels.map { it.id to it.type }, "$label: rels")
                relRels.zip(absRels).forEach { (r, a) ->
                    assertEquals(resolveAgainstXl(r.target), resolveAgainstXl(a.target), "$label: ${r.id}")
                    // Relationships Kexcel adds (a new sheet, a missing sharedStrings part) are
                    // written the same way whichever form the file started with.
                    if (r.id !in inputIds) assertEquals(r.target, a.target, "$label: new ${r.id}")
                }
                val withoutTargets = Regex("""Target="[^"]*"""")
                assertEquals(
                    withoutTargets.replace(relRelsXml, ""),
                    withoutTargets.replace(absRelsXml, ""),
                    "$label: rels differ in more than their targets",
                )
            }
        }
    }

    // endregion

    // region --- SHEET OPERATIONS ON A REAL OPENPYXL FILE ---

    @Test
    fun addSheetToOpenpyxlFile() {
        val input = fixture("openpyxlAbsoluteTargets.xlsx")
        val excel = Excel.decodeBytes(input)
        excel["Added"].updateCell(CellIndex.indexByString("A1"), TextCellValue("new sheet"))

        val saved = excel.encode()!!
        assertPackageIntegrity(saved, "add sheet")

        val again = Excel.decodeBytes(saved)
        assertEquals(listOf("Data", "Second", "Added"), again.tables.keys.toList())
        assertEquals(TextCellValue("new sheet"), again.tables["Added"]!!.cell(CellIndex.indexByString("A1")).value)
        assertEquals(TextCellValue("Alice"), again.tables["Data"]!!.cell(CellIndex.indexByString("A2")).value)
        assertEquals(DoubleCellValue(3.25), again.tables["Second"]!!.cell(CellIndex.indexByString("B2")).value)

        // The new sheet gets a relative target next to openpyxl's absolute ones; OPC allows
        // mixing, and the existing relationships are not touched.
        val rels = workbookRels(saved)
        val worksheetTargets = rels.filter { it.type.endsWith("/worksheet") }.map { it.target }
        assertEquals(listOf("/xl/worksheets/sheet1.xml", "/xl/worksheets/sheet2.xml", "worksheets/sheet3.xml"), worksheetTargets)
        assertContains(contentTypeOverrides(saved), "/xl/worksheets/sheet3.xml")
    }

    @Test
    fun deleteSheetFromOpenpyxlFile() {
        val excel = Excel.decodeBytes(fixture("openpyxlAbsoluteTargets.xlsx"))
        excel.delete("Second")

        val saved = excel.encode()!!
        assertPackageIntegrity(saved, "delete sheet")
        assertFalse(zipEntries(saved).any { it.first == "xl/worksheets/sheet2.xml" }, "part not removed")
        assertFalse(
            workbookRels(saved).any { it.target == "/xl/worksheets/sheet2.xml" },
            "relationship to the deleted sheet not removed",
        )
        assertFalse("/xl/worksheets/sheet2.xml" in contentTypeOverrides(saved), "content type override not removed")
        assertFalse("Second" in readZipEntry(saved, "xl/workbook.xml"), "<sheet> not removed from workbook.xml")

        val again = Excel.decodeBytes(saved)
        assertEquals(listOf("Data"), again.tables.keys.toList())
        assertEquals(IntCellValue(42), again.tables["Data"]!!.cell(CellIndex.indexByString("B2")).value)
    }

    @Test
    fun renameSheetInOpenpyxlFile() {
        val excel = Excel.decodeBytes(fixture("openpyxlAbsoluteTargets.xlsx"))
        excel.rename("Second", "Renamed")

        val saved = excel.encode()!!
        assertPackageIntegrity(saved, "rename sheet")
        val again = Excel.decodeBytes(saved)
        assertEquals(setOf("Data", "Renamed"), again.tables.keys)
        assertEquals(TextCellValue("second sheet"), again.tables["Renamed"]!!.cell(CellIndex.indexByString("A1")).value)
        assertEquals(DoubleCellValue(3.25), again.tables["Renamed"]!!.cell(CellIndex.indexByString("B2")).value)
    }

    @Test
    fun styleCellsInOpenpyxlFile() {
        val excel = Excel.decodeBytes(fixture("openpyxlAbsoluteTargets.xlsx"))
        excel.tables["Data"]!!.updateCell(
            CellIndex.indexByString("A3"),
            TextCellValue("Bob"),
            cellStyle = CellStyle(bold = true, italic = true, backgroundColorHex = ExcelColor.yellowAccent),
        )

        val saved = excel.encode()!!
        assertPackageIntegrity(saved, "style")
        val a3 = Excel.decodeBytes(saved).tables["Data"]!!.cell(CellIndex.indexByString("A3"))
        assertEquals(TextCellValue("Bob"), a3.value)
        assertTrue(a3.cellStyle!!.isBold && a3.cellStyle!!.isItalic, "font style lost")
        assertEquals(ExcelColor.yellowAccent.colorHex, a3.cellStyle!!.backgroundColor.colorHex)
        // openpyxl's untouched header style must survive the styles rewrite.
        assertTrue(Excel.decodeBytes(saved).tables["Data"]!!.cell(CellIndex.indexByString("A1")).cellStyle!!.isBold)
    }

    // endregion

    // region --- PARTS AT NON-STANDARD PATHS ---

    /**
     * Moves the styles, shared strings and first worksheet parts away from their conventional
     * names, updating the relationships and content types to match, so nothing lives where Kexcel
     * might assume it does. The targets stay plainly relative on purpose: this isolates the
     * save path, which ignored the styles relationship even for relative targets.
     */
    private fun withPartsMoved(bytes: ByteArray): ByteArray {
        val moves = MOVED_PARTS
        val targets = mapOf(
            "Target=\"styles.xml\"" to "Target=\"custom/styles-v2.xml\"",
            "Target=\"sharedStrings.xml\"" to "Target=\"strings/sst.xml\"",
            "Target=\"worksheets/sheet1.xml\"" to "Target=\"data/first.xml\"",
        )
        return rewriteZip(bytes) { name, content ->
            val text = when (name) {
                WORKBOOK_RELS -> targets.entries.fold(content.decodeToString()) { s, (a, b) -> s.replace(a, b) }
                "[Content_Types].xml" -> moves.entries.fold(content.decodeToString()) { s, (a, b) ->
                    s.replace("\"/$a\"", "\"/$b\"")
                }
                else -> null
            }
            (moves[name] ?: name) to (text?.encodeToByteArray() ?: content)
        }
    }

    @Test
    fun partsAtNonStandardPathsAreReadFromThere() {
        val original = fixture("example.xlsx")
        val moved = withPartsMoved(original)
        assertPackageIntegrity(moved, "fixture itself")
        assertSameWorkbook(Excel.decodeBytes(original), Excel.decodeBytes(moved), "moved parts")
    }

    @Test
    fun partsAtNonStandardPathsAreWrittenBackInPlace() {
        // Before the fix the styles writer looked up a hardcoded `xl/styles.xml`, so any style
        // change in a workbook whose styles part lives elsewhere crashed in encode().
        fun editAndSave(input: ByteArray): ByteArray {
            val excel = Excel.decodeBytes(input)
            val sheet = excel.tables["Sheet1"]!!
            sheet.updateCell(CellIndex.indexByString("B2"), TextCellValue("moved and edited"))
            sheet.updateCell(CellIndex.indexByString("C6"), IntCellValue(99))
            sheet.updateCell(CellIndex.indexByString("A4"), TextCellValue("ISRAEL"), cellStyle = CellStyle(bold = true))
            return excel.encode()!!
        }

        val original = fixture("example.xlsx")
        val fromOriginal = zipEntries(editAndSave(original)).toMap()
        val saved = editAndSave(withPartsMoved(original))
        assertPackageIntegrity(saved, "moved parts")

        // Each part is saved where it was found, with exactly the content the same edits produce
        // on the untouched fixture: nothing is recreated at its conventional path.
        val fromMoved = zipEntries(saved).toMap()
        MOVED_PARTS.forEach { (conventional, moved) ->
            assertFalse(conventional in fromMoved, "$conventional was recreated at its conventional path")
            assertEquals(
                fromOriginal.getValue(conventional).decodeToString(),
                fromMoved.getValue(moved).decodeToString(),
                "$moved differs from what the same edits write to $conventional",
            )
        }
        assertContains(fromMoved.getValue("xl/strings/sst.xml").decodeToString(), "moved and edited")
        assertContains(fromMoved.getValue("xl/data/first.xml").decodeToString(), "<v>99</v>")
        assertSameWorkbook(
            Excel.decodeBytes(editAndSave(original)),
            Excel.decodeBytes(saved),
            "moved parts after edit",
        )
    }

    // endregion

    // region --- DAMAGED FILES ---

    @Test
    fun missingWorksheetPartIsReportedAsDamagedFile() {
        val input = withWorkbookRelTargets(fixture("example.xlsx")) {
            if (it == "worksheets/sheet1.xml") "/xl/worksheets/missing.xml" else it
        }
        val error = assertFailsWith<IllegalArgumentException> { Excel.decodeBytes(input) }
        assertContains(error.message.orEmpty(), "Sheet1")
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

        /** Alternative spellings of each (relative) fixture target, all naming the same part. */
        val TARGET_FORMS: List<Pair<String, (String) -> String>> = listOf(
            "absolute" to { "/xl/$it" },
            "dot" to { "./$it" },
            "dot-dot" to { "../xl/$it" },
            "absolute with dot" to { "/xl/./$it" },
            // openpyxl's shape: worksheets absolute, everything else relative — and the inverse.
            "worksheets absolute" to { if (it.startsWith("worksheets/")) "/xl/$it" else it },
            "worksheets relative" to { if (it.startsWith("worksheets/")) it else "/xl/$it" },
        )

        /** Each kind of change, chosen so that Kexcel rewrites a different set of parts on save. */
        val OPERATIONS: List<Pair<String, (Excel) -> Unit>> = listOf(
            "no-op" to { },
            "edit" to { e ->
                val sheet = e.tables.values.first()
                sheet.updateCell(CellIndex.indexByString("B2"), TextCellValue("edited"))
                sheet.updateCell(CellIndex.indexByString("C3"), IntCellValue(12345))
                sheet.updateCell(CellIndex.indexByString("D4"), DoubleCellValue(0.25))
                sheet.updateCell(CellIndex.indexByString("E5"), BoolCellValue(true))
                sheet.updateCell(CellIndex.indexByString("F6"), DateCellValue(year = 2026, month = 9, day = 27))
            },
            "style" to { e ->
                e.tables.values.first().updateCell(
                    CellIndex.indexByString("A1"),
                    TextCellValue("styled"),
                    cellStyle = CellStyle(bold = true, backgroundColorHex = ExcelColor.yellowAccent),
                )
            },
            "merge" to { e ->
                e.merge(
                    e.tables.keys.first(),
                    CellIndex.indexByString("A30"),
                    CellIndex.indexByString("C31"),
                    customValue = TextCellValue("merged"),
                )
            },
            "add sheet" to { e -> e["Added"].updateCell(CellIndex.indexByString("A1"), TextCellValue("new")) },
            "delete sheet" to { e -> if (e.tables.size > 1) e.delete(e.tables.keys.last()) },
            "rename sheet" to { e -> e.rename(e.tables.keys.first(), "Renamed") },
        )

        /** Conventional part name → where [withPartsMoved] puts it. */
        val MOVED_PARTS = mapOf(
            "xl/styles.xml" to "xl/custom/styles-v2.xml",
            "xl/sharedStrings.xml" to "xl/strings/sst.xml",
            "xl/worksheets/sheet1.xml" to "xl/data/first.xml",
        )

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
