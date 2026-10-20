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

    /**
     * A workbook whose A1 references the first entry of a hand-written sharedStrings.xml,
     * with the given `<si>` items. Lets a test feed run properties Kexcel does not model.
     */
    private fun workbookWithSharedStrings(vararg items: String): ByteArray {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("placeholder"))
        val sst = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${items.size}" uniqueCount="${items.size}">${items.joinToString("")}</sst>"""
        return replaceZipEntry(excel.encode()!!, "xl/sharedStrings.xml", sst.encodeToByteArray())
    }

    private val excelStyleRichItem =
        """<si><r><rPr><b/><sz val="11"/><color theme="4"/><rFont val="Calibri"/><family val="2"/>""" +
            """<scheme val="minor"/></rPr><t>theme</t></r><r><rPr><strike/><vertAlign val="superscript"/>""" +
            """<sz val="11"/><color indexed="10"/><rFont val="Calibri"/></rPr><t xml:space="preserve"> sup</t></r>""" +
            """<rPh sb="0" eb="1"><t>ph</t></rPh><phoneticPr fontId="1"/></si>"""

    @Test
    fun runPropertiesKexcelDoesNotModelSurviveASave() {
        // Excel writes theme colours, family/scheme and the like on every rich run; the
        // model reads none of them, so an untouched cell has to go back as it came in.
        val saved = Excel.decodeBytes(workbookWithSharedStrings(excelStyleRichItem)).encode()!!

        val sst = readZipEntry(saved, "xl/sharedStrings.xml").decodeToString()
        listOf(
            """<color theme="4"""", "<strike", """<vertAlign val="superscript"""",
            """<color indexed="10"""", """<scheme val="minor"""", "<rPh", "<phoneticPr",
        ).forEach { assertTrue(it in sst, "expected $it in the saved sharedStrings.xml:\n$sst") }

        val back = Excel.decodeBytes(saved)["Sheet1"].cell(CellIndex.indexByString("A1")).value
        assertEquals("theme sup", back.toString())
    }

    @Test
    fun anEditedRichCellIsRebuiltFromTheModel() {
        val excel = Excel.decodeBytes(workbookWithSharedStrings(excelStyleRichItem))
        val cell = excel["Sheet1"].cell(CellIndex.indexByString("A1"))
        val original = cell.value as TextCellValue
        // Mutating a run's style in place must not be overridden by the original <si>.
        original.value.children!![0].style!!.isItalic = true
        excel["Sheet1"].updateCell(CellIndex.indexByString("A2"), TextCellValue("new"))

        val saved = excel.encode()!!
        val reread = Excel.decodeBytes(saved)["Sheet1"]
        val a1 = reread.cell(CellIndex.indexByString("A1")).value as TextCellValue
        assertEquals("theme sup", a1.toString())
        assertEquals(true, a1.value.children!![0].style?.isItalic)
        assertEquals(TextCellValue("new"), reread.cell(CellIndex.indexByString("A2")).value)
    }

    @Test
    fun explicitlyOffBoldAndItalicRunsAreNotStyled() {
        // ST_OnOff: a bare <b/> is on, but val="0"/"false" is an explicit off. Reading
        // those as bold turned plain runs bold once rich text started surviving a save.
        val excel = Excel.decodeBytes(
            workbookWithSharedStrings(
                """<si><r><rPr><b val="0"/><i val="1"/></rPr><t>a</t></r>""" +
                    """<r><rPr><b val="false"/><i/></rPr><t>b</t></r><r><rPr><b/><i val="0"/></rPr><t>c</t></r></si>"""
            )
        )

        val runs = (excel["Sheet1"].cell(CellIndex.indexByString("A1")).value as TextCellValue).value.children!!
        assertEquals(listOf(false, false, true), runs.map { it.style?.isBold })
        assertEquals(listOf(true, true, false), runs.map { it.style?.isItalic })
    }
}