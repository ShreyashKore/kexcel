package com.gyanoba.kexcel

import com.gyanoba.kexcel.utils.shiftFormulaReferences
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [shiftFormulaReferences] is what expands a shared formula (§18.3.1.40) into the
 * expression of each cell that shares it. The expected values are what Excel shows when
 * the formula is filled down or across, and what POI's `XSSFCell.getCellFormula` returns
 * for the same cells (`PoiCorpusRoundTripTest` checks that on real files).
 */
class FormulaShiftTest {

    private fun shift(formula: String, rows: Int = 0, columns: Int = 0) = shiftFormulaReferences(formula, rows, columns)

    @Test
    fun relativeReferencesMoveAndAnchoredOnesStay() {
        assertEquals("A2*2", shift("A1*2", rows = 1))
        assertEquals("B1*2", shift("A1*2", columns = 1))
        assertEquals("C4+\$B\$1+D\$1+\$A4", shift("A1+\$B\$1+B\$1+\$A1", rows = 3, columns = 2))
        assertEquals("SUM(A6:B10)", shift("SUM(A1:B5)", rows = 5))
        assertEquals("\$A59*I\$2", shift("\$A58*H\$2", rows = 1, columns = 1))
    }

    @Test
    fun aZeroShiftReturnsTheFormulaUnchanged() {
        assertEquals("A1 +  B1", shift("A1 +  B1"))
    }

    @Test
    fun wholeColumnAndWholeRowRangesMove() {
        assertEquals("VLOOKUP(A85,D:E,2,0)", shift("VLOOKUP(A2,D:E,2,0)", rows = 83))
        assertEquals("SUM(B:B)", shift("SUM(A:A)", columns = 1))
        assertEquals("SUM(\$A:\$A)", shift("SUM(\$A:\$A)", columns = 1))
        assertEquals("SUM(3:3)", shift("SUM(1:1)", rows = 2))
        assertEquals("SUM(\$1:\$1)", shift("SUM(\$1:\$1)", rows = 2))
    }

    @Test
    fun textInsideStringLiteralsIsNotAReference() {
        assertEquals("IF(A2=\"A1\",\"say \"\"B2\"\"\",C2)", shift("IF(A1=\"A1\",\"say \"\"B2\"\"\",C1)", rows = 1))
    }

    @Test
    fun sheetNamesAreKeptAndTheReferenceAfterThemMoves() {
        assertEquals("Sheet1!A2", shift("Sheet1!A1", rows = 1))
        assertEquals("'My Sheet'!B1", shift("'My Sheet'!A1", columns = 1))
        // A sheet that is named like a cell is still a sheet name.
        assertEquals("A1!B3+'B2'!C4", shift("A1!B2+'B2'!C3", rows = 1))
        assertEquals("'It''s A1'!A2", shift("'It''s A1'!A1", rows = 1))
        assertEquals("Sheet1:Sheet3!A2", shift("Sheet1:Sheet3!A1", rows = 1))
        assertEquals("[1]Sheet1!A2", shift("[1]Sheet1!A1", rows = 1))
    }

    @Test
    fun functionAndDefinedNamesThatLookLikeCellsAreKept() {
        assertEquals("LOG10(A2)+ATAN2(B2,C2)", shift("LOG10(A1)+ATAN2(B1,C1)", rows = 1))
        assertEquals("Q1_total+TAX.RATE1+A2", shift("Q1_total+TAX.RATE1+A1", rows = 1))
        assertEquals("_xlfn.XLOOKUP(B3,B5:B14,C5:D14)", shift("_xlfn.XLOOKUP(B2,B4:B13,C4:D13)", rows = 1))
        assertEquals("TRUE+A2", shift("TRUE+A1", rows = 1))
    }

    @Test
    fun numbersAndErrorLiteralsAreKept() {
        assertEquals("A2*1E5+2.5E-3", shift("A1*1E5+2.5E-3", rows = 1))
        assertEquals("IF(ISERROR(A2),#REF!,#N/A)", shift("IF(ISERROR(A1),#REF!,#N/A)", rows = 1))
    }

    @Test
    fun structuredReferencesAreKept() {
        assertEquals("Table1[[#This Row],[A1]]+B3", shift("Table1[[#This Row],[A1]]+B2", rows = 1))
    }

    @Test
    fun aReferencePushedPastTheEdgeWrapsAround() {
        // POI and Excel both wrap: the row above row 1 is the last row of the sheet.
        assertEquals("A1048576", shift("A1", rows = -1))
        assertEquals("XFD1", shift("A1", columns = -1))
        assertEquals("A1", shift("A1048576", rows = 1))
    }

    @Test
    fun lowerCaseReferencesAreShiftedToo() {
        assertEquals("B3", shift("a2", rows = 1, columns = 1))
    }
}
