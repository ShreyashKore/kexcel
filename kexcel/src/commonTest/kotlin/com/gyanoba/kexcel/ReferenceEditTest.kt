package com.gyanoba.kexcel

import com.gyanoba.kexcel.utils.Axis
import com.gyanoba.kexcel.utils.StructuralEdit
import com.gyanoba.kexcel.utils.adjustFormulaForEdit
import com.gyanoba.kexcel.utils.adjustReferenceForEdit
import com.gyanoba.kexcel.utils.adjustReferenceListForEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a formula or a range becomes when a row or column is inserted or deleted. The
 * expected values are Excel's behaviour; `PoiInteropTest` checks the same edits against
 * POI's `shiftRows` / `shiftColumns` on whole workbooks.
 *
 * Row and column numbers passed to [StructuralEdit] are zero-based: row index 1 is row 2.
 */
class ReferenceEditTest {

    private fun insertRow(index: Int) = StructuralEdit(Axis.Row, index, removed = false)
    private fun removeRow(index: Int) = StructuralEdit(Axis.Row, index, removed = true)
    private fun insertColumn(index: Int) = StructuralEdit(Axis.Column, index, removed = false)
    private fun removeColumn(index: Int) = StructuralEdit(Axis.Column, index, removed = true)

    private fun adjust(formula: String, edit: StructuralEdit, own: Boolean = true, sheets: Set<String> = setOf("Data")) =
        adjustFormulaForEdit(formula, edit, sheets, own)

    @Test
    fun insertingARowMovesReferencesAtOrBelowIt() {
        assertEquals("A1+A3+A4", adjust("A1+A2+A3", insertRow(1)))
        // Unlike a fill, a structural edit moves anchored references too.
        assertEquals("\$A\$3+A\$3+\$A3", adjust("\$A\$2+A\$2+\$A2", insertRow(1)))
        assertEquals("B1*2", adjust("B1*2", insertRow(5)))
    }

    @Test
    fun aRangeGrowsWhenARowIsInsertedInsideIt() {
        assertEquals("SUM(A2:A5)", adjust("SUM(A1:A4)", insertRow(0)))   // above: moves
        assertEquals("SUM(A1:A5)", adjust("SUM(A1:A4)", insertRow(1)))   // inside: grows
        assertEquals("SUM(A1:A5)", adjust("SUM(A1:A4)", insertRow(3)))   // at its last row: grows
        assertEquals("SUM(A1:A4)", adjust("SUM(A1:A4)", insertRow(4)))   // just below: untouched
    }

    @Test
    fun removingARowShrinksRangesAndBreaksReferencesToIt() {
        assertEquals("A1+#REF!+A2", adjust("A1+A2+A3", removeRow(1)))
        assertEquals("SUM(A1:A3)", adjust("SUM(A1:A4)", removeRow(1)))
        assertEquals("SUM(A1:A3)", adjust("SUM(A1:A4)", removeRow(0)))
        assertEquals("SUM(A1:A3)", adjust("SUM(A1:A4)", removeRow(3)))
        assertEquals("SUM(A1:A3)", adjust("SUM(A2:A4)", removeRow(0)))
        assertEquals("SUM(#REF!)", adjust("SUM(A2:C2)", removeRow(1)))
        assertEquals("SUM(A1:A4)", adjust("SUM(A1:A4)", removeRow(9)))
    }

    @Test
    fun columnsBehaveLikeRows() {
        assertEquals("A1+C1+D1", adjust("A1+B1+C1", insertColumn(1)))
        assertEquals("SUM(A1:E1)", adjust("SUM(A1:D1)", insertColumn(2)))
        assertEquals("A1+#REF!+B1", adjust("A1+B1+C1", removeColumn(1)))
        assertEquals("SUM(A1:C1)", adjust("SUM(A1:D1)", removeColumn(1)))
        assertEquals("AA1", adjust("Z1", insertColumn(3)))
        assertEquals("Z1", adjust("AA1", removeColumn(3)))
    }

    @Test
    fun wholeRowAndWholeColumnReferencesFollowTheirOwnAxisOnly() {
        assertEquals("SUM(B:D)", adjust("SUM(B:C)", insertColumn(2)))
        assertEquals("SUM(B:C)", adjust("SUM(B:C)", insertRow(2)))
        assertEquals("SUM(#REF!)", adjust("SUM(B:B)", removeColumn(1)))
        assertEquals("SUM(3:5)", adjust("SUM(2:4)", insertRow(0)))
        assertEquals("SUM(2:4)", adjust("SUM(2:4)", insertColumn(0)))
        assertEquals("SUM(\$A:\$B)", adjust("SUM(\$A:\$C)", removeColumn(1)))
    }

    @Test
    fun onlyReferencesIntoTheEditedSheetMove() {
        // A formula on another sheet: unqualified references are its own.
        assertEquals("A2+Data!A3+'Data'!\$B\$3", adjust("A2+Data!A2+'Data'!\$B\$2", insertRow(0), own = false))
        // A formula on the edited sheet: references into other sheets stay.
        assertEquals("A3+Other!A2", adjust("A2+Other!A2", insertRow(0)))
        assertEquals("'My Data'!A3", adjust("'My Data'!A2", insertRow(0), own = false, sheets = setOf("My Data")))
        // Sheet names are compared without regard to case.
        assertEquals("DATA!A3", adjust("DATA!A2", insertRow(0), own = false))
        // 3-D references and other workbooks are left alone.
        assertEquals("SUM(Data:Other!A2)+[1]Data!A2+'[2]Data'!A2", adjust("SUM(Data:Other!A2)+[1]Data!A2+'[2]Data'!A2", insertRow(0), own = false))
        assertEquals("Data!#REF!", adjust("Data!A2", removeRow(1), own = false))
    }

    @Test
    fun whatOnlyLooksLikeAReferenceIsKept() {
        assertEquals("IF(A3=\"A2\",LOG10(A3),Q1_total)", adjust("IF(A2=\"A2\",LOG10(A2),Q1_total)", insertRow(0)))
        assertEquals("A3*1E5+#REF!+#N/A", adjust("A2*1E5+#REF!+#N/A", insertRow(0)))
        assertEquals("Table1[[#This Row],[A2]]+B3", adjust("Table1[[#This Row],[A2]]+B2", insertRow(0)))
        assertEquals("A1048576", adjust("A1048576", insertColumn(5)))
    }

    @Test
    fun aReferencePushedOffTheSheetIsBroken() {
        assertEquals("#REF!", adjust("A1048576", insertRow(0)))
        assertEquals("#REF!", adjust("XFD1", insertColumn(0)))
    }

    @Test
    fun rangesHeldInAttributesFollowTheSameRules() {
        assertEquals("A3", adjustReferenceForEdit("A2", insertRow(0)))
        assertEquals("A1:C6", adjustReferenceForEdit("A1:C5", insertRow(2)))
        assertNull(adjustReferenceForEdit("A2", removeRow(1)))
        assertNull(adjustReferenceForEdit("A2:C2", removeRow(1)))
        assertEquals("not a range", adjustReferenceForEdit("not a range", insertRow(0)))

        assertEquals("A1:A4 C7", adjustReferenceListForEdit("A1:A5 B2 C8", removeRow(1)))
        assertEquals("", adjustReferenceListForEdit("B2", removeRow(1)))
    }
}
