package com.gyanoba.kexcel

import com.fleeksoft.ksoup.Ksoup
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.sheet.TextCellValue
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Helpers shared by the JVM fixture-driven tests that were split out of
 * [ExcelFileTest]: locating fixtures, peeking at zip entries / shared strings, and
 * the assertion helpers used by several test classes.
 */

internal fun fixture(name: String): ByteArray {
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

internal fun readZipEntry(bytes: ByteArray, entryName: String): ByteArray {
    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == entryName) return zis.readBytes()
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    error("zip entry not found: $entryName")
}

internal data class Sst(val count: String, val uniqueCount: String)

internal fun parseSst(bytes: ByteArray): Sst {
    val xml = readZipEntry(bytes, "xl/sharedStrings.xml").decodeToString()
    val sst = Ksoup.parse(xml).getElementsByTag("sst").first()
        ?: error("no <sst> element in xl/sharedStrings.xml")
    return Sst(count = sst.attr("count"), uniqueCount = sst.attr("uniqueCount"))
}

internal val allBorderStyles: List<BorderStyle> = listOf(
    BorderStyle.None, BorderStyle.DashDot, BorderStyle.DashDotDot, BorderStyle.Dashed,
    BorderStyle.Dotted, BorderStyle.Double, BorderStyle.Hair, BorderStyle.Medium,
    BorderStyle.MediumDashDot, BorderStyle.MediumDashDotDot, BorderStyle.MediumDashed,
    BorderStyle.SlantDashDot, BorderStyle.Thick, BorderStyle.Thin,
)

internal fun assertSpannedItemsList(sheet: Sheet) {
    val spannedItems = sheet.spannedItems
    assertEquals("A1:B1", spannedItems[0])
    assertEquals("A2:A3", spannedItems[1])
    assertEquals("A4:B5", spannedItems[2])
}

internal fun assertSpannedItemsSheetValues(sheet: Sheet) {
    val cells = sheet.rows.flatMap { row -> row.filterNotNull() }

    assertEquals(TextCellValue("spanned item A1:B1"), cells[0].value)
    assertEquals(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 0), cells[0].cellIndex)

    assertEquals(TextCellValue("spanned item A2:A3"), cells[1].value)
    assertEquals(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 1), cells[1].cellIndex)

    assertEquals(TextCellValue("spanned item A4:B5"), cells[2].value)
    assertEquals(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 3), cells[2].cellIndex)
}

internal fun assertMergeCellsCount(bytes: ByteArray, expected: Int) {
    val xml = readZipEntry(bytes, "xl/worksheets/sheet1.xml").decodeToString()
    val mergeCells = Ksoup.parseXml(xml).getElementsByTag("mergeCells").first()
        ?: error("no <mergeCells> element in xl/worksheets/sheet1.xml")
    assertEquals(expected.toString(), mergeCells.attr("count"))
    assertEquals(expected, mergeCells.getElementsByTag("mergeCell").size)
    assertFalse(mergeCells.hasAttr("value"), "unexpected `value` attribute on <mergeCells>")
}
/**
 * Rewrites a single entry of an `.xlsx` archive, leaving every other entry byte-identical.
 *
 * Lets the reader tests hand Kexcel worksheet XML that Kexcel itself would never emit —
 * the shapes other producers (Excel, LibreOffice, Google Sheets, POI, exporters) do emit.
 */
internal fun replaceZipEntry(bytes: ByteArray, entryName: String, content: ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    java.util.zip.ZipOutputStream(out).use { zos ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                if (entry.name == entryName) zos.write(content) else zos.write(zis.readBytes())
                zos.closeEntry()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
    return out.toByteArray()
}

/** Every entry name in an `.xlsx` archive. */
internal fun zipEntryNames(bytes: ByteArray): List<String> = buildList {
    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            add(entry.name)
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}

/**
 * Builds a workbook whose `xl/worksheets/sheet1.xml` is [sheetDataXml] wrapped in a
 * minimal `<worksheet>`, then reads `Sheet1` back out of it.
 */
internal fun sheetFromRawXml(sheetDataXml: String, beforeSheetData: String = ""): Sheet {
    val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$beforeSheetData<sheetData>$sheetDataXml</sheetData></worksheet>"""
    val base = Excel.createExcel().encode()!!
    val patched = replaceZipEntry(base, "xl/worksheets/sheet1.xml", xml.encodeToByteArray())
    return Excel.decodeBytes(patched)["Sheet1"]
}
