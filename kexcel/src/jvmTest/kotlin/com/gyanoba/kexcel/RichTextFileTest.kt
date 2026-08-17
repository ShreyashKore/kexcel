package com.gyanoba.kexcel

import com.fleeksoft.ksoup.Ksoup
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.TextCellValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rich-text fixture tests: read `.xlsx` fixtures carrying rich runs and re-save them.
 * In-memory rich-text round-trips are in the multiplatform [RichTextTest].
 */
class RichTextFileTest {

    // Dart: 'Cell Style' group -> 'read file with rich text'
    @Test
    fun readFileWithRichText() {
        val excel = Excel.decodeBytes(fixture("richText.xlsx"))
        val sheetObject = excel.tables["Sheet1"]!!
        val redHex = "FFFF0000"
        val blueHex = "FF2A6099"

        val cellA1 = sheetObject.cell(CellIndex.indexByString("A1")).value as TextCellValue
        assertEquals(12, cellA1.value.children!![0].style!!.fontSize)
        assertEquals(redHex, cellA1.value.children!![0].style!!.fontColor.colorHex)
        assertEquals(10, cellA1.value.children!![1].style!!.fontSize)
        assertEquals(blueHex, cellA1.value.children!![1].style!!.fontColor.colorHex)

        val cellA2 = sheetObject.cell(CellIndex.indexByString("A2")).value as TextCellValue
        assertEquals(true, cellA2.value.children!![0].style!!.isBold)
        assertEquals(false, cellA2.value.children!![0].style!!.isItalic)
        assertEquals(false, cellA2.value.children!![1].style!!.isBold)
        assertEquals(true, cellA2.value.children!![1].style!!.isItalic)

        val cellA3 = sheetObject.cell(CellIndex.indexByString("A3")).value as TextCellValue
        assertEquals("Skia", cellA3.value.children!![0].style!!.fontFamily)
        assertEquals("Arial", cellA3.value.children!![1].style!!.fontFamily)
    }

    // Saving must re-emit the <r>/<rPr> runs rather than flattening them to plain text.
    @Test
    fun savingXlsxWithRichText() {
        val original = Excel.decodeBytes(fixture("richText.xlsx"))
        val saved = original.encode()!!
        val reread = Excel.decodeBytes(saved)

        listOf("A1", "A2", "A3").forEach { id ->
            val before = original.tables["Sheet1"]!!.cell(CellIndex.indexByString(id)).value as TextCellValue
            val after = reread.tables["Sheet1"]!!.cell(CellIndex.indexByString(id)).value as TextCellValue
            assertEquals(before.toString(), after.toString(), "text of $id")
            assertEquals(
                before.value.children?.map { it.text },
                after.value.children?.map { it.text },
                "runs of $id",
            )
            assertEquals(
                before.value.children?.map { it.style },
                after.value.children?.map { it.style },
                "run styles of $id",
            )
        }

        // The written part really does carry rich runs, not one flattened <t>.
        val sst = Ksoup.parseXml(readZipEntry(saved, "xl/sharedStrings.xml").decodeToString())
        val richItems = sst.getElementsByTag("si").filter { it.getElementsByTag("r").isNotEmpty() }
        assertTrue(richItems.isNotEmpty(), "expected <si> entries containing <r> runs")
        assertTrue(
            richItems.all { it.getElementsByTag("rPr").isNotEmpty() },
            "every rich run should carry its <rPr> properties",
        )
    }
}