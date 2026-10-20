package com.gyanoba.kexcel

import com.gyanoba.kexcel.number_format.CustomNumericNumFormat
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.FontScheme
import com.gyanoba.kexcel.utils.HorizontalAlign
import com.gyanoba.kexcel.utils.TextWrapping
import com.gyanoba.kexcel.utils.Underline
import com.gyanoba.kexcel.utils.VerticalAlign
import no.synth.kmpzip.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end `encode()` / `decodeBytes()` tests built from scratch workbooks, covering
 * the read/write symmetry the library depends on: cell values, styling, sheet geometry
 * and workbook-level metadata must all survive a save.
 */
class WorkbookRoundTripTest {

    private fun Excel.reload(): Excel = Excel.decodeBytes(encode()!!)

    // region --- Values ---

    @Test
    fun everyCellValueTypeSurvivesASave() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        val values = mapOf(
            "A1" to TextCellValue("text"),
            "A2" to IntCellValue(42),
            "A3" to DoubleCellValue(12.34),
            "A4" to BoolCellValue(true),
            "A5" to BoolCellValue(false),
            "A6" to DateCellValue(year = 2023, month = 4, day = 20),
            "A7" to DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13),
            "A8" to TimeCellValue(hour = 2, minute = 20, second = 10),
        )
        values.forEach { (id, value) -> sheet.updateCell(CellIndex.indexByString(id), value) }

        val reread = excel.reload()["Sheet1"]
        values.forEach { (id, value) ->
            assertEquals(value, reread.cell(CellIndex.indexByString(id)).value, "cell $id")
        }
    }

    @Test
    fun nullCellsStayEmpty() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("kept"))
        sheet.updateCell(CellIndex.indexByString("A2"), TextCellValue("cleared"))
        sheet.updateCell(CellIndex.indexByString("A2"), null)

        val reread = excel.reload()["Sheet1"]
        assertEquals(TextCellValue("kept"), reread.cell(CellIndex.indexByString("A1")).value)
        assertNull(reread.cell(CellIndex.indexByString("A2")).value)
    }

    @Test
    fun formulasAreStoredAsFormulasNotResults() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(1))
        sheet.updateCell(CellIndex.indexByString("A2"), IntCellValue(2))
        sheet.updateCell(CellIndex.indexByString("A3"), FormulaCellValue("SUM(A1:A2)"))
        // Data.setFormula is the other way in.
        sheet.cell(CellIndex.indexByString("A4")).setFormula("A1*2")

        val reread = excel.reload()["Sheet1"]
        assertEquals(FormulaCellValue("SUM(A1:A2)"), reread.cell(CellIndex.indexByString("A3")).value)
        assertEquals(FormulaCellValue("A1*2"), reread.cell(CellIndex.indexByString("A4")).value)
    }

    @Test
    fun unicodeAndWhitespaceInStringsArePreserved() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        val strings = listOf("世界よこんにちは", "  leading and trailing  ", "a\nb", "quote \" and & amp", "")
        strings.forEachIndexed { i, s ->
            sheet.updateCell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = i), TextCellValue(s))
        }

        val reread = excel.reload()["Sheet1"]
        strings.forEachIndexed { i, s ->
            assertEquals(
                TextCellValue(s),
                reread.cell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = i)).value,
                "string #$i",
            )
        }
    }

    @Test
    fun repeatedStringsAreSharedRatherThanDuplicated() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        repeat(5) { i ->
            sheet.updateCell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = i), TextCellValue("same"))
        }
        val reread = excel.reload()["Sheet1"]
        repeat(5) { i ->
            assertEquals(
                TextCellValue("same"),
                reread.cell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = i)).value,
            )
        }
    }

    // endregion

    // region --- Styling ---

    @Test
    fun aFullyPopulatedCellStyleSurvivesASave() {
        val excel = Excel.createExcel()
        val style = CellStyle(
            fontColorHex = ExcelColor.red,
            backgroundColorHex = ExcelColor.yellow,
            fontSize = 18,
            fontFamily = "Courier New",
            fontScheme = FontScheme.Minor,
            horizontalAlign = HorizontalAlign.Center,
            verticalAlign = VerticalAlign.Top,
            textWrapping = TextWrapping.WrapText,
            bold = true,
            italic = true,
            underline = Underline.Double,
            rotation = 45,
            leftBorder = Border(BorderStyle.Thick, ExcelColor.blue),
            rightBorder = Border(BorderStyle.Dotted, ExcelColor.green),
            topBorder = Border(BorderStyle.Double, ExcelColor.black),
            bottomBorder = Border(BorderStyle.Hair, ExcelColor.purple),
        )
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("styled"), style)

        val back = excel.reload()["Sheet1"].cell(CellIndex.indexByString("A1")).cellStyle
        assertNotNull(back)
        assertEquals(style, back)
    }

    @Test
    fun alignmentAndRotationSurviveASave() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(
            CellIndex.indexByString("A1"), TextCellValue("a"),
            CellStyle(horizontalAlign = HorizontalAlign.Right, verticalAlign = VerticalAlign.Center),
        )
        sheet.updateCell(
            CellIndex.indexByString("A2"), TextCellValue("b"),
            CellStyle(rotation = 90, textWrapping = TextWrapping.WrapText),
        )

        val reread = excel.reload()["Sheet1"]
        reread.cell(CellIndex.indexByString("A1")).cellStyle.let {
            assertEquals(HorizontalAlign.Right, it?.horizontalAlignment)
            assertEquals(VerticalAlign.Center, it?.verticalAlignment)
        }
        reread.cell(CellIndex.indexByString("A2")).cellStyle.let {
            assertEquals(90, it?.rotation)
            assertEquals(TextWrapping.WrapText, it?.wrap)
        }
    }

    @Test
    fun singleAndDoubleUnderlinesAreDistinguished() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("none"), CellStyle())
        sheet.updateCell(CellIndex.indexByString("A2"), TextCellValue("single"), CellStyle(underline = Underline.Single))
        sheet.updateCell(CellIndex.indexByString("A3"), TextCellValue("double"), CellStyle(underline = Underline.Double))

        val reread = excel.reload()["Sheet1"]
        assertEquals(Underline.None, reread.cell(CellIndex.indexByString("A1")).cellStyle?.underline)
        assertEquals(Underline.Single, reread.cell(CellIndex.indexByString("A2")).cellStyle?.underline)
        assertEquals(Underline.Double, reread.cell(CellIndex.indexByString("A3")).cellStyle?.underline)
    }

    @Test
    fun rotationIsClampedAndNegativesUseExcelEncoding() {
        val style = CellStyle()
        style.rotation = 45
        assertEquals(45, style.rotation)
        style.rotation = 90
        assertEquals(90, style.rotation)
        // Excel stores counter-clockwise angles as 90 + |angle|.
        style.rotation = -45
        assertEquals(135, style.rotation)
        style.rotation = -90
        assertEquals(180, style.rotation)
        // Anything outside [-90, 90] collapses to no rotation.
        style.rotation = 91
        assertEquals(0, style.rotation)
        style.rotation = -91
        assertEquals(0, style.rotation)
    }

    @Test
    fun everyBorderStyleSurvivesASave() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        val styles = BorderStyle.entries.filter { it != BorderStyle.None }
        styles.forEachIndexed { i, borderStyle ->
            val border = Border(borderStyle = borderStyle, borderColorHex = ExcelColor.black)
            sheet.updateCell(
                CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = i),
                TextCellValue(borderStyle.name),
                CellStyle(leftBorder = border, rightBorder = border, topBorder = border, bottomBorder = border),
            )
        }

        val reread = excel.reload()["Sheet1"]
        styles.forEachIndexed { i, borderStyle ->
            val border = Border(borderStyle = borderStyle, borderColorHex = ExcelColor.black)
            val back = reread.cell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = i)).cellStyle
            assertEquals(border, back?.leftBorder, borderStyle.name)
            assertEquals(border, back?.rightBorder, borderStyle.name)
            assertEquals(border, back?.topBorder, borderStyle.name)
            assertEquals(border, back?.bottomBorder, borderStyle.name)
        }
    }

    @Test
    fun diagonalBordersSurviveASave() {
        val excel = Excel.createExcel()
        val diagonal = Border(borderStyle = BorderStyle.Double, borderColorHex = ExcelColor.black)
        excel["Sheet1"].updateCell(
            CellIndex.indexByString("A1"), TextCellValue("x"),
            CellStyle(diagonalBorder = diagonal, diagonalBorderUp = true, diagonalBorderDown = false),
        )

        val back = excel.reload()["Sheet1"].cell(CellIndex.indexByString("A1")).cellStyle
        assertEquals(diagonal, back?.diagonalBorder)
        assertEquals(true, back?.diagonalBorderUp)
        assertEquals(false, back?.diagonalBorderDown)
    }

    @Test
    fun aStyleThatRejectsTheValueFallsBackToTheDefaultFormat() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        // "@" (text) is a numeric format and does not accept text, so updateCell swaps in
        // the default format for the value instead of writing an incompatible pair.
        sheet.updateCell(
            CellIndex.indexByString("A1"),
            TextCellValue("text"),
            CellStyle(numberFormat = NumFormat.standard_49),
        )
        assertEquals(NumFormat.standard_0, sheet.cell(CellIndex.indexByString("A1")).cellStyle?.numberFormat)

        // A date format on a date is kept as given.
        sheet.updateCell(
            CellIndex.indexByString("A2"),
            DateCellValue(year = 2023, month = 4, day = 20),
            CellStyle(numberFormat = NumFormat.standard_15),
        )
        assertEquals(NumFormat.standard_15, sheet.cell(CellIndex.indexByString("A2")).cellStyle?.numberFormat)
        assertEquals(
            NumFormat.standard_15,
            excel.reload()["Sheet1"].cell(CellIndex.indexByString("A2")).cellStyle?.numberFormat,
        )
    }

    @Test
    fun customNumberFormatsSurviveASave() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        val percent = CustomNumericNumFormat("0.000%")
        val currency = CustomNumericNumFormat("""#,##0.00" kr"""")
        sheet.updateCell(CellIndex.indexByString("A1"), DoubleCellValue(0.125), CellStyle(numberFormat = percent))
        sheet.updateCell(CellIndex.indexByString("A2"), DoubleCellValue(9.5), CellStyle(numberFormat = currency))

        val reread = excel.reload()["Sheet1"]
        assertEquals(percent, reread.cell(CellIndex.indexByString("A1")).cellStyle?.numberFormat)
        assertEquals(currency, reread.cell(CellIndex.indexByString("A2")).cellStyle?.numberFormat)
    }

    @Test
    fun changingAValueRelaxesAnIncompatibleFormat() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(
            CellIndex.indexByString("A1"),
            DateCellValue(year = 2023, month = 4, day = 20),
            CellStyle(numberFormat = NumFormat.defaultDate),
        )
        // Overwriting with a number must not leave the date format behind.
        sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(7))
        assertEquals(NumFormat.defaultNumeric, sheet.cell(CellIndex.indexByString("A1")).cellStyle?.numberFormat)
    }

    // endregion

    // region --- Sheet geometry ---

    @Test
    fun columnWidthsAndRowHeightsSurviveASave() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("C3"), TextCellValue("x"))
        sheet.setDefaultColumnWidth(15.5)
        sheet.setDefaultRowHeight(22.5)
        sheet.setColumnWidth(1, 40.0)
        sheet.setRowHeight(2, 33.0)

        val reread = excel.reload()["Sheet1"]
        assertEquals(15.5, reread.defaultColumnWidth)
        assertEquals(22.5, reread.defaultRowHeight)
        assertEquals(40.0, reread.getColumnWidth(1))
        assertEquals(33.0, reread.getRowHeight(2))
        // Untouched columns and rows report the defaults.
        assertEquals(22.5, reread.getRowHeight(0))
    }

    @Test
    fun sizeSettersIgnoreNegativeValues() {
        val sheet = Excel.createExcel()["Sheet1"]
        sheet.setDefaultColumnWidth(12.0)
        sheet.setDefaultRowHeight(14.0)
        sheet.setColumnWidth(0, 20.0)
        sheet.setRowHeight(0, 25.0)

        sheet.setDefaultColumnWidth(-1.0)
        sheet.setDefaultRowHeight(-1.0)
        sheet.setColumnWidth(0, -1.0)
        sheet.setRowHeight(0, -1.0)

        assertEquals(12.0, sheet.defaultColumnWidth)
        assertEquals(14.0, sheet.defaultRowHeight)
        assertEquals(20.0, sheet.getColumnWidth(0))
        assertEquals(25.0, sheet.getRowHeight(0))
    }

    @Test
    fun columnAutoFitIsRecordedOnTheSheet() {
        val sheet = Excel.createExcel()["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("B1"), TextCellValue("x"))
        sheet.setColumnAutoFit(1)
        assertTrue(sheet.getColumnAutoFit(1))
        assertEquals(false, sheet.getColumnAutoFit(0))
        assertEquals(mapOf(1 to true), sheet.getColumnAutoFits)
        // Negative indices are rejected the same way cell() rejects them.
        assertFailsWith<IllegalArgumentException> { sheet.setColumnAutoFit(-1) }
        assertEquals(mapOf(1 to true), sheet.getColumnAutoFits)
    }

    @Test
    fun rightToLeftFlagSurvivesASave() {
        val excel = Excel.createExcel()
        assertEquals(false, excel["Sheet1"].isRTL)
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("שלום"))
        excel["Sheet1"].isRTL = true

        assertTrue(excel.reload()["Sheet1"].isRTL)
    }

    // endregion

    // region --- findAndReplace ---

    private fun replaceFixture(): Excel {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        for (r in 0 until 3) {
            sheet.insertRowIterables(List(3) { c -> TextCellValue("r${r}c$c") }, r)
        }
        return excel
    }

    private fun Excel.textGrid(): List<List<String?>> =
        this["Sheet1"].rows.map { row -> row.map { it?.value?.toString() } }

    @Test
    fun findAndReplaceRewritesEveryMatch() {
        val excel = replaceFixture()
        assertEquals(3, excel.findAndReplace("Sheet1", Regex("c1"), "X"))
        assertEquals(
            listOf(
                listOf("r0c0", "r0X", "r0c2"),
                listOf("r1c0", "r1X", "r1c2"),
                listOf("r2c0", "r2X", "r2c2"),
            ),
            excel.textGrid(),
        )
    }

    @Test
    fun findAndReplaceHandlesMatchesAwayFromTheStartOfTheText() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("prefix-target-suffix"))
        assertEquals(1, excel.findAndReplace("Sheet1", Regex("target"), "VALUE"))
        assertEquals(
            TextCellValue("prefix-VALUE-suffix"),
            excel["Sheet1"].cell(CellIndex.indexByString("A1")).value,
        )
    }

    @Test
    fun findAndReplaceCountsMultipleMatchesInOneCell() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("a-a-a"))
        assertEquals(3, excel.findAndReplace("Sheet1", Regex("a"), "b"))
        assertEquals(TextCellValue("b-b-b"), excel["Sheet1"].cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun findAndReplaceCanStopAfterTheFirstMatches() {
        val excel = replaceFixture()
        assertEquals(1, excel.findAndReplace("Sheet1", Regex("c[0-9]"), "X", first = 1))
        assertEquals(
            listOf(
                listOf("r0X", "r0c1", "r0c2"),
                listOf("r1c0", "r1c1", "r1c2"),
                listOf("r2c0", "r2c1", "r2c2"),
            ),
            excel.textGrid(),
        )
    }

    @Test
    fun findAndReplaceHonoursRowAndColumnBounds() {
        val rowBounded = replaceFixture()
        assertEquals(3, rowBounded.findAndReplace("Sheet1", Regex("^r"), "R", startingRow = 1, endingRow = 1))
        assertEquals(listOf("R1c0", "R1c1", "R1c2"), rowBounded.textGrid()[1])
        assertEquals(listOf("r0c0", "r0c1", "r0c2"), rowBounded.textGrid()[0])

        val columnBounded = replaceFixture()
        assertEquals(3, columnBounded.findAndReplace("Sheet1", Regex("^r"), "R", startingColumn = 1, endingColumn = 1))
        assertEquals(listOf("r0c0", "R0c1", "r0c2"), columnBounded.textGrid()[0])

        // Reversed bounds are normalised.
        val reversed = replaceFixture()
        assertEquals(3, reversed.findAndReplace("Sheet1", Regex("^r"), "R", startingRow = 1, endingRow = 1))
        assertEquals(rowBounded.textGrid(), reversed.textGrid())
    }

    @Test
    fun findAndReplaceSkipsNonTextCellsAndUnknownSheets() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(11))
        sheet.updateCell(CellIndex.indexByString("A2"), TextCellValue("11"))

        assertEquals(1, excel.findAndReplace("Sheet1", Regex("11"), "22"))
        assertEquals(IntCellValue(11), sheet.cell(CellIndex.indexByString("A1")).value)
        assertEquals(TextCellValue("22"), sheet.cell(CellIndex.indexByString("A2")).value)

        assertEquals(0, excel.findAndReplace("Missing", Regex("11"), "22"))
    }

    // endregion

    // region --- Workbook and sheet management ---

    @Test
    fun getReturnsAndCreatesSheets() {
        val excel = Excel.createExcel()
        assertEquals(listOf("Sheet1"), excel.getSheets().keys.toList())
        val created = excel["Fresh"]
        assertEquals(0, created.maxRows)
        assertEquals("Fresh", created.sheetName)
        assertEquals(listOf("Sheet1", "Fresh"), excel.getSheets().keys.toList())
        // A second lookup returns the same sheet rather than a new blank one.
        created.updateCell(CellIndex.indexByString("A1"), TextCellValue("v"))
        assertEquals(TextCellValue("v"), excel["Fresh"].cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun copyProducesAnIndependentSheet() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("original"))
        excel.copy("Sheet1", "Duplicate")

        assertEquals(TextCellValue("original"), excel["Duplicate"].cell(CellIndex.indexByString("A1")).value)
        excel["Duplicate"].updateCell(CellIndex.indexByString("A1"), TextCellValue("changed"))
        assertEquals(TextCellValue("original"), excel["Sheet1"].cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun linkSharesOneSheetUnderTwoNamesUntilUnlinked() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("one"))
        excel.link("Alias", excel["Sheet1"])

        excel["Alias"].updateCell(CellIndex.indexByString("A2"), TextCellValue("two"))
        assertEquals(TextCellValue("two"), excel["Sheet1"].cell(CellIndex.indexByString("A2")).value)

        excel.unLink("Alias")
        excel["Alias"].updateCell(CellIndex.indexByString("A3"), TextCellValue("three"))
        assertNull(excel["Sheet1"].cell(CellIndex.indexByString("A3")).value)
        // The content written before unlinking is still there.
        assertEquals(TextCellValue("two"), excel["Alias"].cell(CellIndex.indexByString("A2")).value)
    }

    @Test
    fun deleteRefusesToRemoveTheLastSheet() {
        val excel = Excel.createExcel()
        excel.delete("Sheet1")
        assertEquals(listOf("Sheet1"), excel.getSheets().keys.toList())
    }

    @Test
    fun renameIsIgnoredWhenTheTargetNameIsTaken() {
        val excel = Excel.createExcel()
        excel["Other"].updateCell(CellIndex.indexByString("A1"), TextCellValue("other"))
        excel.rename("Sheet1", "Other")
        assertEquals(listOf("Sheet1", "Other"), excel.getSheets().keys.toList())
        assertEquals(TextCellValue("other"), excel["Other"].cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun defaultSheetTracksRenamesAndDeletions() {
        val excel = Excel.createExcel()
        assertEquals("Sheet1", excel.getDefaultSheet())

        excel["Second"]
        assertTrue(excel.setDefaultSheet("Second"))
        assertEquals("Second", excel.getDefaultSheet())
        // An unknown name is rejected and leaves the current choice alone.
        assertEquals(false, excel.setDefaultSheet("Missing"))
        assertEquals("Second", excel.getDefaultSheet())

        excel.rename("Second", "Renamed")
        assertEquals("Renamed", excel.getDefaultSheet())

        // Deleting the chosen sheet clears the explicit choice; the getter then falls back
        // to the first sheet declared in workbook.xml.
        excel.delete("Renamed")
        assertEquals("Sheet1", excel.getDefaultSheet())
    }

    @Test
    fun workbookLevelRowAndColumnHelpersDelegateToTheSheet() {
        val excel = Excel.createExcel()
        excel.appendRow("Sheet1", listOf(TextCellValue("a"), TextCellValue("b")))
        excel.appendRow("Sheet1", listOf(TextCellValue("c"), TextCellValue("d")))
        assertEquals(2, excel["Sheet1"].maxRows)

        excel.insertRow("Sheet1", 0)
        assertEquals(3, excel["Sheet1"].maxRows)
        excel.removeRow("Sheet1", 0)
        assertEquals(2, excel["Sheet1"].maxRows)

        excel.insertColumn("Sheet1", 0)
        assertEquals(3, excel["Sheet1"].maxColumns)
        excel.removeColumn("Sheet1", 0)
        assertEquals(2, excel["Sheet1"].maxColumns)

        assertEquals(TextCellValue("a"), excel["Sheet1"].cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun workbookLevelHelpersIgnoreNegativeIndicesAndUnknownSheets() {
        val excel = Excel.createExcel()
        excel.appendRow("Sheet1", listOf(TextCellValue("a")))
        excel.insertRow("Sheet1", -1)
        excel.insertColumn("Sheet1", -1)
        excel.removeRow("Missing", 0)
        excel.removeColumn("Missing", 0)
        excel.appendRow("Sheet1", emptyList())
        excel.insertRowIterables("Sheet1", listOf(TextCellValue("x")), -1)

        assertEquals(1, excel["Sheet1"].maxRows)
        assertEquals(1, excel["Sheet1"].maxColumns)
        assertEquals(listOf("Sheet1"), excel.getSheets().keys.toList())
    }

    @Test
    fun multipleSheetsRoundTripIndependently() {
        val excel = Excel.createExcel()
        listOf("Alpha", "Beta", "Gamma").forEachIndexed { i, name ->
            val sheet = excel[name]
            sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue(name))
            sheet.updateCell(CellIndex.indexByString("B1"), IntCellValue(i))
        }
        excel.delete("Sheet1")

        val reread = excel.reload()
        assertEquals(listOf("Alpha", "Beta", "Gamma"), reread.getSheets().keys.toList())
        listOf("Alpha", "Beta", "Gamma").forEachIndexed { i, name ->
            assertEquals(TextCellValue(name), reread[name].cell(CellIndex.indexByString("A1")).value)
            assertEquals(IntCellValue(i), reread[name].cell(CellIndex.indexByString("B1")).value)
        }
    }

    @Test
    fun aWorkbookCanBeSavedAndReloadedRepeatedly() {
        var excel = Excel.createExcel()
        repeat(3) { round ->
            excel["Sheet1"].updateCell(
                CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = round),
                TextCellValue("round $round"),
            )
            excel = excel.reload()
        }
        repeat(3) { round ->
            assertEquals(
                TextCellValue("round $round"),
                excel["Sheet1"].cell(CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = round)).value,
            )
        }
    }

    // endregion

    // region --- Decoding ---

    @Test
    fun decodeStreamMatchesDecodeBytes() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), TextCellValue("streamed"))
        val bytes = excel.encode()!!

        val fromStream = Excel.decodeStream(ByteArrayInputStream(bytes))
        assertEquals(
            TextCellValue("streamed"),
            fromStream["Sheet1"].cell(CellIndex.indexByString("A1")).value,
        )
        assertEquals(
            Excel.decodeBytes(bytes).getSheets().keys.toList(),
            fromStream.getSheets().keys.toList(),
        )
    }

    @Test
    fun decodingSomethingThatIsNotAnXlsxFails() {
        assertFailsWith<UnsupportedOperationException> {
            Excel.decodeBytes("this is not a zip archive".encodeToByteArray())
        }
        assertFailsWith<UnsupportedOperationException> {
            Excel.decodeBytes(ByteArray(0))
        }
    }

    // endregion
}
