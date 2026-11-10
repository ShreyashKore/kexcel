package com.gyanoba.kexcel

import kotlin.test.Test

/**
 * Real workbooks from the Apache POI test data, saved by Kexcel **without an edit** and
 * compared with the original by POI.
 *
 * A round trip through Kexcel's own reader cannot see a property Kexcel does not model:
 * if the reader drops it, the writer never knew it was there, and both agree. POI can see
 * it. Each fixture was chosen because it caught a defect (the finding is named beside it,
 * from `AUDIT-FINDINGS.md`); the `known` sets list what a file still loses, and
 * [assertOnlyDiffers] fails when an entry there stops being true, so they can only shrink.
 */
class PoiCorpusRoundTripTest {

    private fun assertLossless(name: String, known: Set<String> = emptySet()) {
        val original = fixture("poi/$name")
        assertOnlyDiffers(original, roundTrip(original), expected = known, what = name)
    }

    // D-01: <x:workbook>, <x:sst>, <x:worksheet> — the main namespace bound to a prefix.
    @Test
    fun aWorkbookWithPrefixedPartsSurvives() = assertLossless(
        "59746_NoRowNums.xlsx",
        known = setOf("row: hidden", "row: outline level"), // F-04
    )

    // D-01: <x:worksheet> with a rich inline string.
    @Test
    fun aPrefixedWorksheetWithInlineStringsSurvives() = assertLossless("InlineString.xlsx")

    // D-01: <ns0:Relationships>, as Python's ElementTree writes it.
    @Test
    fun prefixedRelationshipsSurvive() = assertLossless("58760.xlsx")

    // D-09: header and footer field codes (&C, &P, &"Font,Style", &12).
    @Test
    fun headerAndFooterCodesSurvive() = assertLossless(
        "55640.xlsx",
        known = setOf("row: hidden", "row: outline level", "row dropped", "column: width"), // F-04, F-05
    )

    // D-02: shared formulas — every follower must keep the expression POI derives for it.
    @Test
    fun sharedFormulasSurvive() = assertLossless(
        "shared_formulas.xlsx",
        known = setOf("column: width", "row: style"), // F-04, F-05
    )

    @Test
    fun sharedFormulasFilledAcrossSurvive() = assertLossless("50096.xlsx")

    // D-02: followers whose references are whole columns (`VLOOKUP(A85,D:E,2,0)`).
    @Test
    fun sharedFormulasWithWholeColumnReferencesSurvive() = assertLossless("VLookupFullColumn.xlsx")

    @Test
    fun sharedFormulasNextToShiftedRowsSurvive() = assertLossless("TestShiftRowSharedFormula.xlsx")

    // D-03: a dynamic-array formula (`cm`) and the values it spills.
    @Test
    fun aDynamicArrayFormulaSurvives() = assertLossless(
        "xlookup.xlsx",
        known = setOf("column: width"), // F-05
    )

    // F-01: cell protection, theme fills and fonts that differ only in unmodelled properties.
    @Test
    fun protectionAndThemeColoursSurvive() = assertLossless(
        "FillWithoutColor.xlsx",
        known = setOf("styled blank cell dropped", "row dropped", "column: hidden", "column: style", "column: width", "row: hidden", "row: style", "sheet: default column width"), // F-04, F-05, F-07, F-08
    )

    // D-15: a workbook with a calculation chain.
    @Test
    fun aWorkbookWithACalculationChainSurvives() = assertLossless("56822-Countifs.xlsx")

    @Test
    fun theCalculationChainIsRemovedOnceAValueChanges() {
        // xl/calcChain.xml lists the formula cells. An entry for a cell that is no longer a
        // formula makes Excel offer to repair the workbook; the part is optional, and POI
        // removes entries the same way (XSSFWorkbook.onDeleteFormula).
        val original = fixture("poi/56822-Countifs.xlsx")
        kotlin.test.assertTrue("xl/calcChain.xml" in zipEntryNames(original))

        val saved = roundTrip(original) { excel -> excel[excel.tables.keys.first()].updateCell(com.gyanoba.kexcel.sheet.CellIndex.indexByString("Z99"), com.gyanoba.kexcel.sheet.IntCellValue(1)) }

        kotlin.test.assertFalse("xl/calcChain.xml" in zipEntryNames(saved))
        kotlin.test.assertFalse("calcChain" in readZipEntry(saved, "[Content_Types].xml").decodeToString())
        kotlin.test.assertFalse("calcChain" in readZipEntry(saved, "xl/_rels/workbook.xml.rels").decodeToString())
        PoiOracle.read(saved) { kotlin.test.assertTrue(it.forceFormulaRecalculation) }
    }
}
