package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.shared_strings.TextSpan
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.Underline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rich text (a [TextSpan] with styled child runs) must survive `encode()`, not collapse
 * into a single unstyled string. These tests drive the `<si>`/`<r>`/`<rPr>` writer in
 * `shared_strings` against the reader that produced this model in the first place.
 */
class RichTextTest {

    private fun roundTrip(span: TextSpan): TextCellValue {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue.span(span))
        val value = Excel.decodeBytes(excel.encode()!!)["Sheet1"]
            .cell(CellIndex.indexByString("A1")).value
        assertTrue(value is TextCellValue, "expected a text cell, got $value")
        return value
    }

    // region --- Runs and their styling ---

    @Test
    fun styledRunsSurviveASave() {
        val span = TextSpan(
            children = listOf(
                TextSpan(text = "bold", style = CellStyle(bold = true)),
                TextSpan(text = "italic", style = CellStyle(italic = true)),
                TextSpan(text = "plain", style = CellStyle()),
            ),
        )
        val back = roundTrip(span)

        assertEquals("bolditalicplain", back.toString())
        val children = back.value.children
        assertNotNull(children)
        assertEquals(3, children.size)
        assertEquals("bold", children[0].text)
        assertEquals(true, children[0].style?.isBold)
        assertEquals(false, children[0].style?.isItalic)
        assertEquals("italic", children[1].text)
        assertEquals(false, children[1].style?.isBold)
        assertEquals(true, children[1].style?.isItalic)
        assertEquals("plain", children[2].text)
        assertEquals(false, children[2].style?.isBold)
        assertEquals(false, children[2].style?.isItalic)
    }

    @Test
    fun everyRunPropertyTheReaderUnderstandsSurvivesASave() {
        val style = CellStyle(
            bold = true,
            italic = true,
            underline = Underline.Double,
            fontSize = 14,
            fontFamily = "Courier New",
            fontColorHex = ExcelColor.red,
        )
        val back = roundTrip(TextSpan(children = listOf(TextSpan(text = "styled", style = style))))

        val run = back.value.children!!.single()
        assertEquals("styled", run.text)
        assertEquals(true, run.style?.isBold)
        assertEquals(true, run.style?.isItalic)
        assertEquals(Underline.Double, run.style?.underline)
        assertEquals(14, run.style?.fontSize)
        assertEquals("Courier New", run.style?.fontFamily)
        assertEquals(ExcelColor.red, run.style?.fontColor)
    }

    @Test
    fun singleAndDoubleUnderlinedRunsStayDistinct() {
        val back = roundTrip(
            TextSpan(
                children = listOf(
                    TextSpan(text = "none", style = CellStyle()),
                    TextSpan(text = "single", style = CellStyle(underline = Underline.Single)),
                    TextSpan(text = "double", style = CellStyle(underline = Underline.Double)),
                ),
            ),
        )
        val children = back.value.children!!
        assertEquals(Underline.None, children[0].style?.underline)
        assertEquals(Underline.Single, children[1].style?.underline)
        assertEquals(Underline.Double, children[2].style?.underline)
    }

    @Test
    fun runsKeepTheirWhitespaceAndUnicode() {
        val back = roundTrip(
            TextSpan(
                children = listOf(
                    TextSpan(text = "Hello ", style = CellStyle(bold = true)),
                    TextSpan(text = " 世界 ", style = CellStyle()),
                    TextSpan(text = "!", style = CellStyle(italic = true)),
                ),
            ),
        )
        assertEquals("Hello  世界 !", back.toString())
        assertEquals(listOf("Hello ", " 世界 ", "!"), back.value.children!!.map { it.text })
    }

    @Test
    fun aRunWithoutAStyleReadsBackWithTheDefaultStyle() {
        val back = roundTrip(TextSpan(children = listOf(TextSpan(text = "bare"))))
        val run = back.value.children!!.single()
        assertEquals("bare", run.text)
        // The reader always attaches a style to a run, so an unstyled run becomes the default.
        assertEquals(CellStyle(), run.style)
    }

    @Test
    fun nestedSpansAreFlattenedIntoRunsThatInheritTheirParentStyle() {
        val span = TextSpan(
            children = listOf(
                TextSpan(
                    style = CellStyle(bold = true),
                    children = listOf(
                        TextSpan(text = "outer-bold"),
                        TextSpan(text = "inner-italic", style = CellStyle(italic = true)),
                    ),
                ),
                TextSpan(text = "tail", style = CellStyle()),
            ),
        )
        val back = roundTrip(span)

        assertEquals("outer-boldinner-italictail", back.toString())
        val children = back.value.children!!
        assertEquals(3, children.size)
        assertEquals(true, children[0].style?.isBold, "the nested run inherits its parent's style")
        assertEquals(false, children[0].style?.isItalic)
        assertEquals(true, children[1].style?.isItalic, "an explicit child style wins over the parent's")
        assertEquals("tail", children[2].text)
    }

    @Test
    fun leadingPlainTextIsKeptAlongsideRuns() {
        val back = roundTrip(
            TextSpan(
                text = "prefix:",
                children = listOf(TextSpan(text = "bold", style = CellStyle(bold = true))),
            ),
        )
        assertEquals("prefix:bold", back.toString())
        assertEquals("prefix:", back.value.text)
        assertEquals(true, back.value.children!!.single().style?.isBold)
    }

    // endregion

    // region --- Interaction with plain strings and the shared-string pool ---

    @Test
    fun plainStringsStayPlain() {
        val back = roundTrip(TextSpan(text = "just text"))
        assertEquals(TextCellValue("just text"), back)
        assertEquals("just text", back.value.text)
        assertNull(back.value.children, "a plain string must not gain runs")
    }

    @Test
    fun sameTextWithDifferentStylingIsNotMergedIntoOneEntry() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Hello"))
        sheet.updateCell(
            CellIndex.indexByString("A2"),
            TextCellValue.span(TextSpan(children = listOf(TextSpan(text = "Hello", style = CellStyle(bold = true))))),
        )

        val reread = Excel.decodeBytes(excel.encode()!!)["Sheet1"]
        val plain = reread.cell(CellIndex.indexByString("A1")).value as TextCellValue
        val bold = reread.cell(CellIndex.indexByString("A2")).value as TextCellValue

        assertEquals("Hello", plain.toString())
        assertEquals("Hello", bold.toString())
        assertNull(plain.value.children, "the plain cell must not pick up the styled cell's runs")
        assertEquals(true, bold.value.children!!.single().style?.isBold)
    }

    @Test
    fun identicalRichSpansShareOneEntry() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        val span = TextSpan(children = listOf(TextSpan(text = "repeated", style = CellStyle(bold = true))))
        repeat(3) { row ->
            sheet.updateCell(
                CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = row),
                TextCellValue.span(span),
            )
        }

        val reread = Excel.decodeBytes(excel.encode()!!)["Sheet1"]
        repeat(3) { row ->
            val value = reread.cell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = row)).value as TextCellValue
            assertEquals("repeated", value.toString())
            assertEquals(true, value.value.children!!.single().style?.isBold)
        }
    }

    @Test
    fun richTextSurvivesRepeatedSaves() {
        var excel = Excel.createExcel()
        excel["Sheet1"].updateCell(
            CellIndex.indexByString("A1"),
            TextCellValue.span(
                TextSpan(
                    children = listOf(
                        TextSpan(text = "a", style = CellStyle(bold = true, fontSize = 16)),
                        TextSpan(text = "b", style = CellStyle(fontColorHex = ExcelColor.blue)),
                    ),
                ),
            ),
        )

        repeat(3) { excel = Excel.decodeBytes(excel.encode()!!) }

        val value = excel["Sheet1"].cell(CellIndex.indexByString("A1")).value as TextCellValue
        assertEquals("ab", value.toString())
        val children = value.value.children!!
        assertEquals(true, children[0].style?.isBold)
        assertEquals(16, children[0].style?.fontSize)
        assertEquals(ExcelColor.blue, children[1].style?.fontColor)
    }

    @Test
    fun findAndReplaceOnRichTextCollapsesItToPlainText() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(
            CellIndex.indexByString("A1"),
            TextCellValue.span(
                TextSpan(children = listOf(TextSpan(text = "target", style = CellStyle(bold = true)))),
            ),
        )
        assertEquals(1, excel.findAndReplace("Sheet1", Regex("target"), "replaced"))

        // Replacement rewrites the cell as a plain string, which is the documented result
        // of matching against the flattened text.
        val value = excel["Sheet1"].cell(CellIndex.indexByString("A1")).value
        assertEquals(TextCellValue("replaced"), value)
    }

    // endregion
}
