package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.HeaderFooter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * In-memory ports of the Dart `excel` tests that are too generic to belong to a single
 * feature (creating a workbook, saving an empty one) or have no other commonTest
 * companion to group with (header/footer).
 *
 * Feature-specific in-memory tests live in their own files (e.g. [SheetOperationsTest],
 * [NumberFormatTest]), and fixture-based tests in the JVM-only `ExcelFileTest`.
 */
class ExcelInMemoryTest {

    // Dart: 'Create New XLSX File'
    @Test
    fun createNewXlsxFile() {
        val excel = Excel.createExcel()
        assertEquals(1, excel.tables.size)
        assertEquals("Sheet1", excel.tables.keys.first())
    }

    // Dart: Header/Footer group -> 'Save empty Workbook'
    @Test
    fun saveEmptyWorkbook() {
        val excel = Excel.createExcel()
        assertNotNull(excel.encode())
    }

    @Test
    fun setAndRemoveHeaderFooterInMemory() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.headerFooter = HeaderFooter(
            alignWithMargins = true,
            differentFirst = false,
            differentOddEven = true,
            scaleWithDoc = false,
            evenFooter = "EvenFooter",
            evenHeader = "EvenHeader",
            firstFooter = "FirstFooter",
            firstHeader = "FirstHeader",
            oddFooter = "OddFooter",
            oddHeader = "OddHeader",
        )

        val bytes = excel.encode()
        assertNotNull(bytes)

        val reread = Excel.decodeBytes(bytes)
        val rereadSheet = reread["Sheet1"]
        assertNotNull(rereadSheet.headerFooter)
        assertEquals(true, rereadSheet.headerFooter!!.alignWithMargins)
        assertEquals(false, rereadSheet.headerFooter!!.differentFirst)
        assertEquals(true, rereadSheet.headerFooter!!.differentOddEven)
        assertEquals(false, rereadSheet.headerFooter!!.scaleWithDoc)
        assertEquals("EvenFooter", rereadSheet.headerFooter!!.evenFooter)
        assertEquals("EvenHeader", rereadSheet.headerFooter!!.evenHeader)
        assertEquals("FirstFooter", rereadSheet.headerFooter!!.firstFooter)
        assertEquals("FirstHeader", rereadSheet.headerFooter!!.firstHeader)
        assertEquals("OddFooter", rereadSheet.headerFooter!!.oddFooter)
        assertEquals("OddHeader", rereadSheet.headerFooter!!.oddHeader)

        // Remove headerFooter and roundtrip again
        rereadSheet.headerFooter = null
        val bytesWithoutHf = reread.encode()
        assertNotNull(bytesWithoutHf)

        val finalExcel = Excel.decodeBytes(bytesWithoutHf)
        assertNull(finalExcel["Sheet1"].headerFooter)
    }
}