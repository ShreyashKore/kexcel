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