package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reader conformance against worksheet XML that **Kexcel itself would never write**.
 *
 * Every other round-trip test in the suite feeds the reader the writer's own output, so
 * the two can agree on a private dialect and still both be wrong. These tests hand the
 * parser the shapes the OOXML spec permits and other producers actually emit — omitted
 * `r` references, inline strings, error cells, cached formula results, empty `<v/>`,
 * unordered rows — and pin what comes back.
 *
 * This is the layer that has to hold up first when the library grows support for another
 * spreadsheet format: a reader that only understands its own writer cannot ingest
 * anybody else's files.
 */
class ForeignWorksheetXmlTest {

    // region --- Cell references (`r`) are optional ---

    @Test
    fun cellsWithoutAnRReferenceFallBackToTheirPositionInTheRow() {
        // §18.3.1.4: `r` is optional on <c>. Compact writers omit it for whole sheets.
        val sheet = sheetFromRawXml("""<row r="1"><c><v>1</v></c><c><v>2</v></c><c><v>3</v></c></row>""")

        assertEquals(
            listOf(IntCellValue(1), IntCellValue(2), IntCellValue(3)),
            sheet.rows[0].map { it?.value },
        )
    }

    @Test
    fun aMissingRReferenceContinuesFromTheLastCellThatHadOne() {
        val sheet = sheetFromRawXml("""<row r="1"><c r="C1"><v>3</v></c><c><v>4</v></c></row>""")

        assertNull(sheet.cell(CellIndex.indexByString("A1")).value)
        assertEquals(IntCellValue(3), sheet.cell(CellIndex.indexByString("C1")).value)
        assertEquals(IntCellValue(4), sheet.cell(CellIndex.indexByString("D1")).value)
    }

    @Test
    fun rowsWithoutAnRReferenceFallBackToDocumentOrder() {
        // §18.3.1.73: `r` is optional on <row> too.
        val sheet = sheetFromRawXml(
            """<row><c r="A1"><v>1</v></c></row><row><c r="A2"><v>2</v></c></row>"""
        )

        assertEquals(IntCellValue(1), sheet.cell(CellIndex.indexByColumnRow(0, 0)).value)
        assertEquals(IntCellValue(2), sheet.cell(CellIndex.indexByColumnRow(0, 1)).value)
    }

    @Test
    fun aMalformedRReferenceDoesNotAbortTheParse() {
        val sheet = sheetFromRawXml("""<row r="1"><c r="???"><v>1</v></c><c r="B1"><v>2</v></c></row>""")

        assertEquals(IntCellValue(2), sheet.cell(CellIndex.indexByString("B1")).value)
    }

    // endregion

    // region --- Value encodings ---

    @Test
    fun inlineStringsAreRead() {
        // t="inlineStr" stores the text in the cell instead of the shared string table.
        // Streaming writers prefer it; Kexcel never emits it.
        val sheet = sheetFromRawXml(
            """<row r="1"><c r="A1" t="inlineStr"><is><t>inline value</t></is></c></row>"""
        )

        assertEquals(TextCellValue("inline value"), sheet.cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun everyRunOfARichInlineStringIsRead() {
        // <is> shares the CT_Rst content model with <si>, so a rich inline string is a
        // list of runs. Reading only the first <t> silently truncated the cell.
        val sheet = sheetFromRawXml(
            """<row r="1"><c r="A1" t="inlineStr"><is>""" +
                """<r><rPr><b/></rPr><t>bold</t></r>""" +
                """<r><t> and plain</t></r>""" +
                """</is></c></row>"""
        )

        val span = (sheet.cell(CellIndex.indexByString("A1")).value as TextCellValue).value
        assertEquals("bold and plain", span.toString())

        val runs = assertNotNull(span.children, "rich runs were flattened away")
        assertEquals(listOf("bold", " and plain"), runs.map { it.text })
        assertEquals(listOf(true, false), runs.map { it.style?.isBold == true })
    }

    @Test
    fun anInlineStringKeepsPreservedWhitespaceAndEscapedCharacters() {
        val sheet = sheetFromRawXml(
            """<row r="1">""" +
                """<c r="A1" t="inlineStr"><is><t xml:space="preserve">  padded  </t></is></c>""" +
                """<c r="B1" t="inlineStr"><is><t>a &amp; b &lt;c&gt; &quot;d&quot;</t></is></c>""" +
                """</row>"""
        )

        assertEquals(TextCellValue("  padded  "), sheet.cell(CellIndex.indexByString("A1")).value)
        assertEquals(TextCellValue("""a & b <c> "d""""), sheet.cell(CellIndex.indexByString("B1")).value)
    }

    @Test
    fun anEmptyInlineStringReadsAsEmptyText() {
        val sheet = sheetFromRawXml("""<row r="1"><c r="A1" t="inlineStr"><is><t></t></is></c></row>""")

        assertEquals(TextCellValue(""), sheet.cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun booleanCellsAreReadFromTheirNumericEncoding() {
        val sheet = sheetFromRawXml(
            """<row r="1"><c r="A1" t="b"><v>1</v></c><c r="B1" t="b"><v>0</v></c></row>"""
        )

        assertEquals(BoolCellValue(true), sheet.cell(CellIndex.indexByString("A1")).value)
        assertEquals(BoolCellValue(false), sheet.cell(CellIndex.indexByString("B1")).value)
    }

    @Test
    fun errorCellsSurfaceTheirErrorLiteral() {
        val sheet = sheetFromRawXml(
            """<row r="1"><c r="A1" t="e"><v>#REF!</v></c><c r="B1" t="e"><v>#N/A</v></c></row>"""
        )

        // No dedicated error type exists yet; the literal is preserved so nothing is lost.
        assertEquals(FormulaCellValue("#REF!"), sheet.cell(CellIndex.indexByString("A1")).value)
        assertEquals(FormulaCellValue("#N/A"), sheet.cell(CellIndex.indexByString("B1")).value)
    }

    @Test
    fun aFormulaWinsOverItsCachedResult() {
        // Real files always carry the last computed value in <v> next to the <f>.
        // Kexcel models the formula, not the cache.
        val sheet = sheetFromRawXml("""<row r="1"><c r="A1"><f>1+1</f><v>2</v></c></row>""")

        assertEquals(FormulaCellValue("1+1"), sheet.cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun aStringResultFormulaIsReadAsAFormula() {
        val sheet = sheetFromRawXml("""<row r="1"><c r="A1" t="str"><f>CONCAT("a","b")</f><v>ab</v></c></row>""")

        assertEquals(FormulaCellValue("ab"), sheet.cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun theHostOfASharedFormulaKeepsItsExpression() {
        // A shared formula gives the expression once on the host cell; followers carry
        // only `si`. Kexcel does not expand them, so a follower comes back empty — pinned
        // here so implementing expansion is a visible change.
        val sheet = sheetFromRawXml(
            """<row r="1"><c r="A1"><f t="shared" ref="A1:A2" si="0">B1*2</f><v>4</v></c></row>""" +
                """<row r="2"><c r="A2"><f t="shared" si="0"/><v>6</v></c></row>"""
        )

        assertEquals(FormulaCellValue("B1*2"), sheet.cell(CellIndex.indexByString("A1")).value)
        assertEquals(FormulaCellValue(""), sheet.cell(CellIndex.indexByString("A2")).value)
    }

    @Test
    fun numbersInScientificNotationAreRead() {
        val sheet = sheetFromRawXml(
            """<row r="1"><c r="A1"><v>1.5E+10</v></c><c r="B1"><v>-2.5e-3</v></c></row>"""
        )

        assertEquals(DoubleCellValue(1.5e10), sheet.cell(CellIndex.indexByString("A1")).value)
        assertEquals(DoubleCellValue(-2.5e-3), sheet.cell(CellIndex.indexByString("B1")).value)
    }

    // endregion

    // region --- Empty and degenerate cells ---

    @Test
    fun anEmptyValueElementReadsAsAnEmptyCellRatherThanThrowing() {
        // A styled-but-valueless cell is written as <c r="A1" s="2"><v/></c>. Handing ""
        // to a number format used to blow up the whole parse.
        val sheet = sheetFromRawXml(
            """<row r="1"><c r="A1"><v></v></c><c r="B1"><v/></c><c r="C1"><v>3</v></c></row>"""
        )

        assertNull(sheet.cell(CellIndex.indexByString("A1")).value)
        assertNull(sheet.cell(CellIndex.indexByString("B1")).value)
        assertEquals(IntCellValue(3), sheet.cell(CellIndex.indexByString("C1")).value)
    }

    @Test
    fun selfClosingCellsAreEmptyButStillOccupyTheirColumn() {
        val sheet = sheetFromRawXml("""<row r="1"><c r="A1"/><c r="B1"><v>2</v></c></row>""")

        assertNull(sheet.cell(CellIndex.indexByString("A1")).value)
        assertEquals(IntCellValue(2), sheet.cell(CellIndex.indexByString("B1")).value)
    }

    @Test
    fun anEmptySheetDataYieldsAnEmptySheet() {
        val sheet = sheetFromRawXml("")

        assertEquals(0, sheet.maxRows)
        assertEquals(0, sheet.maxColumns)
        assertEquals(emptyList(), sheet.rows)
    }

    // endregion

    // region --- Layout ---

    @Test
    fun rowsOutOfDocumentOrderLandAtTheRowTheirReferenceNames() {
        val sheet = sheetFromRawXml(
            """<row r="3"><c r="A3"><v>3</v></c></row><row r="1"><c r="A1"><v>1</v></c></row>"""
        )

        assertEquals(3, sheet.maxRows)
        assertEquals(IntCellValue(1), sheet.cell(CellIndex.indexByString("A1")).value)
        assertNull(sheet.cell(CellIndex.indexByString("A2")).value)
        assertEquals(IntCellValue(3), sheet.cell(CellIndex.indexByString("A3")).value)
    }

    @Test
    fun aSparseSheetDoesNotMaterialiseTheRowsBetween() {
        val sheet = sheetFromRawXml(
            """<row r="1"><c r="A1"><v>1</v></c></row><row r="1000"><c r="A1000"><v>2</v></c></row>"""
        )

        assertEquals(1000, sheet.maxRows)
        assertEquals(IntCellValue(2), sheet.cell(CellIndex.indexByString("A1000")).value)
        assertTrue(sheet.rows.drop(1).dropLast(1).all { row -> row.all { it == null } })
    }

    @Test
    fun aStaleDimensionDoesNotBoundWhatIsRead() {
        // <dimension> is a hint, not a constraint, and producers leave it stale.
        // The data must win.
        val sheet = sheetFromRawXml(
            sheetDataXml = """<row r="1"><c r="A1"><v>1</v></c></row><row r="5"><c r="D5"><v>2</v></c></row>""",
            beforeSheetData = """<dimension ref="A1:A1"/>""",
        )

        assertEquals(5, sheet.maxRows)
        assertEquals(IntCellValue(2), sheet.cell(CellIndex.indexByString("D5")).value)
    }

    // endregion

    // region --- Corrupt input is reported, not crashed on ---

    @Test
    fun aSharedStringIndexThatDoesNotExistIsReportedAsADamagedFile() {
        val error = assertFailsWith<IllegalArgumentException> {
            sheetFromRawXml("""<row r="1"><c r="A1" t="s"><v>999</v></c></row>""")
        }

        // A NullPointerException from deep inside the parser tells a caller nothing.
        assertTrue(
            error.message.orEmpty().contains("Damaged Excel file"),
            "expected a damaged-file diagnostic, got: ${error.message}",
        )
        assertTrue(error.message.orEmpty().contains("999"))
    }

    @Test
    fun aNonNumericSharedStringIndexIsReportedAsADamagedFile() {
        val error = assertFailsWith<IllegalArgumentException> {
            sheetFromRawXml("""<row r="1"><c r="A1" t="s"><v>abc</v></c></row>""")
        }

        assertTrue(
            error.message.orEmpty().contains("Damaged Excel file"),
            "expected a damaged-file diagnostic, got: ${error.message}",
        )
    }

    @Test
    fun decodingSomethingThatIsNotAZipFails() {
        assertFailsWith<Exception> { Excel.decodeBytes("not a spreadsheet".encodeToByteArray()) }
    }

    // endregion
}
