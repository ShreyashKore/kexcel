package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.utils.readZipArchive
import com.gyanoba.kexcel.utils.resolveWorkbookRelTarget
import no.synth.kmpzip.io.ByteArrayInputStream
import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Workbook relationship targets may be relative to `xl/` or package-absolute (OPC §9.3).
 * Fixture-based coverage, including a real openpyxl file, is in the JVM-only
 * `AbsoluteRelationshipTargetsTest`; this runs on every KMP target.
 */
class RelationshipTargetTest {

    @Test
    fun relativeTargetsResolveAgainstXl() {
        assertEquals("xl/worksheets/sheet1.xml", resolveWorkbookRelTarget("worksheets/sheet1.xml"))
        assertEquals("xl/styles.xml", resolveWorkbookRelTarget("styles.xml"))
    }

    @Test
    fun absoluteTargetsResolveAgainstPackageRoot() {
        assertEquals("xl/worksheets/sheet1.xml", resolveWorkbookRelTarget("/xl/worksheets/sheet1.xml"))
        assertEquals("xl/sharedStrings.xml", resolveWorkbookRelTarget("/xl/sharedStrings.xml"))
        assertEquals("other/part.xml", resolveWorkbookRelTarget("/other/part.xml"))
    }

    @Test
    fun dotSegmentsAreResolved() {
        assertEquals("xl/worksheets/sheet1.xml", resolveWorkbookRelTarget("./worksheets/sheet1.xml"))
        assertEquals("xl/styles.xml", resolveWorkbookRelTarget("../xl/styles.xml"))
        assertEquals("xl/styles.xml", resolveWorkbookRelTarget("worksheets/../styles.xml"))
        assertEquals("xl/worksheets/sheet1.xml", resolveWorkbookRelTarget("/xl/./worksheets//sheet1.xml"))
        // `..` cannot climb above the package root.
        assertEquals("styles.xml", resolveWorkbookRelTarget("../../styles.xml"))
    }

    /** Re-zips [bytes] with every `xl/_rels/workbook.xml.rels` target made package-absolute. */
    private fun withAbsoluteTargets(bytes: ByteArray): ByteArray {
        val targetAttr = Regex("""Target="([^"/][^"]*)"""")
        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zos ->
            readZipArchive(ByteArrayInputStream(bytes)).files.forEach { file ->
                val content = if (file.name == "xl/_rels/workbook.xml.rels") {
                    targetAttr.replace(file.content.decodeToString()) { "Target=\"/xl/${it.groupValues[1]}\"" }
                        .encodeToByteArray()
                } else {
                    file.content
                }
                zos.putNextEntry(ZipEntry(file.name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return buf.toByteArray()
    }

    @Test
    fun absoluteAndRelativeTargetsSaveTheSameParts() {
        val created = Excel.createExcel()
        val sheetName = created.getDefaultSheet()!!
        created[sheetName].updateCell(CellIndex.indexByString("A1"), TextCellValue("before"))
        val relative = created.encode()!!
        val absolute = withAbsoluteTargets(relative)

        fun edit(input: ByteArray): List<Pair<String, String>> {
            val excel = Excel.decodeBytes(input)
            excel[sheetName].updateCell(CellIndex.indexByString("A1"), TextCellValue("after"))
            excel[sheetName].updateCell(CellIndex.indexByString("B2"), IntCellValue(7))
            excel["Added"].updateCell(CellIndex.indexByString("A1"), TextCellValue("new"))
            return readZipArchive(ByteArrayInputStream(excel.encode()!!)).files
                .filter { it.name != "xl/_rels/workbook.xml.rels" }
                .map { it.name to it.content.decodeToString() }
        }

        // Every part except the relationships themselves comes out byte-for-byte identical.
        assertEquals(edit(relative), edit(absolute))
    }

    private fun entry(bytes: ByteArray, name: String): String =
        readZipArchive(ByteArrayInputStream(bytes)).findFile(name)!!.content.decodeToString()

    @Test
    fun absoluteTargetsReadEditAndSave() {
        val created = Excel.createExcel()
        val sheetName = created.getDefaultSheet()!!
        created[sheetName].updateCell(CellIndex.indexByString("A1"), TextCellValue("before"))
        created[sheetName].updateCell(CellIndex.indexByString("B1"), IntCellValue(1))
        val input = withAbsoluteTargets(created.encode()!!)
        assertTrue("Target=\"/xl/worksheets/" in entry(input, "xl/_rels/workbook.xml.rels"))

        val excel = Excel.decodeBytes(input)
        assertEquals(TextCellValue("before"), excel[sheetName].cell(CellIndex.indexByString("A1")).value)
        excel[sheetName].updateCell(CellIndex.indexByString("A1"), TextCellValue("after"))
        excel[sheetName].updateCell(CellIndex.indexByString("B1"), IntCellValue(2))

        val saved = excel.encode()!!
        val again = Excel.decodeBytes(saved)
        assertEquals(TextCellValue("after"), again[sheetName].cell(CellIndex.indexByString("A1")).value)
        assertEquals(IntCellValue(2), again[sheetName].cell(CellIndex.indexByString("B1")).value)

        val names = readZipArchive(ByteArrayInputStream(saved)).files.map { it.name }
        assertTrue(names.none { "//" in it }, "entry names containing '//': $names")
        assertEquals(names.distinct(), names, "duplicate entries")
        assertTrue("<v>2</v>" in entry(saved, "xl/worksheets/sheet1.xml"), "B1 edit missing from the sheet part")
        assertTrue("after" in entry(saved, "xl/sharedStrings.xml"), "A1 edit missing from the shared strings part")
    }
}
