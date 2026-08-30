package com.gyanoba.kexcel

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Header/footer tests from the Dart `excel` port that read `.xlsx` fixtures from disk.
 */
class HeaderFooterFileTest {

    // Dart: 'Update header/footer'.
    @Test
    fun updateHeaderFooter() {
        val bytes = fixture("example.xlsx")
        val excel = Excel.decodeBytes(bytes)
        val sheetObject = excel.tables["Sheet1"]!!

        sheetObject.headerFooter!!.oddHeader = "Foo"
        sheetObject.headerFooter!!.oddFooter = "Bar"

        excel.copy("Sheet1", "test_sheet")
        val newSheet = excel.tables["test_sheet"]!!
        assertEquals(
            newSheet.headerFooter!!.oddHeader!!, "Foo")
        assertEquals(
            newSheet.headerFooter!!.oddFooter!!, "Bar")
    }

    // Dart: 'Clone header/footer of existing Workbook'.
    // KMP port: same immutability limitation as updateHeaderFooter.
    @Test
    fun cloneHeaderFooter() {
        val bytes = fixture("example.xlsx")
        val excel = Excel.decodeBytes(bytes)
        val sheetObject = excel.tables["Sheet1"]

        sheetObject!!.headerFooter!!.oddHeader = "Foo"
        sheetObject!!.headerFooter!!.oddFooter = "Bar"

        excel.copy("Sheet1", "test_sheet");

        val testSheet = excel.tables["test_sheet"];

        assertEquals(testSheet!!.headerFooter!!.oddHeader!!, "Foo")
        assertEquals(testSheet.headerFooter!!.oddFooter!!, "Bar")
    }

    @Test
    fun removeHeaderFooterFromWorkbook() {
        val bytes = fixture("headerFooter.xlsx")
        val excel = Excel.decodeBytes(bytes)
        val sheet = excel.tables["Sheet1"]!!
        assertNotNull(sheet.headerFooter)

        sheet.headerFooter = null
        val encoded = excel.encode()!!

        // Verify XML node was removed and not retained in the worksheet part
        val xml = readZipEntry(encoded, "xl/worksheets/sheet1.xml").decodeToString()
        val doc = Ksoup.parseXml(xml)
        assertEquals(0, doc.getElementsByTag("headerFooter").size, "Expected <headerFooter> to be removed from XML")

        // Verify roundtrip read
        val reread = Excel.decodeBytes(encoded)
        assertNull(reread.tables["Sheet1"]!!.headerFooter)
    }

    @Test
    fun updateHeaderFooterRoundTrip() {
        val bytes = fixture("headerFooter.xlsx")
        val excel = Excel.decodeBytes(bytes)
        val sheet = excel.tables["Sheet1"]!!

        sheet.headerFooter!!.oddHeader = "NewOddHeader"
        sheet.headerFooter!!.oddFooter = "NewOddFooter"
        val encoded = excel.encode()!!

        // Verify XML has exactly one <headerFooter> tag, not duplicated
        val xml = readZipEntry(encoded, "xl/worksheets/sheet1.xml").decodeToString()
        val doc = Ksoup.parseXml(xml)
        val hfTags = doc.getElementsByTag("headerFooter")
        assertEquals(1, hfTags.size, "Expected exactly one <headerFooter> element in XML")
        assertEquals("NewOddHeader", hfTags.first()?.getElementsByTag("oddHeader")?.first()?.text())

        // Verify reread values
        val reread = Excel.decodeBytes(encoded)
        val rereadSheet = reread.tables["Sheet1"]!!
        assertNotNull(rereadSheet.headerFooter)
        assertEquals("NewOddHeader", rereadSheet.headerFooter!!.oddHeader)
        assertEquals("NewOddFooter", rereadSheet.headerFooter!!.oddFooter)
    }

    // Dart: 'Reader headerFooter attributes'
    @Test
    fun readerHeaderFooterAttributes() {
        val excel = Excel.decodeBytes(fixture("headerFooter.xlsx"))
        val headerFooter = excel.tables["Sheet1"]!!.headerFooter!!
        assertEquals(false, headerFooter.alignWithMargins)
        assertEquals(true, headerFooter.differentFirst)
        assertEquals(true, headerFooter.differentOddEven)
        assertEquals(false, headerFooter.scaleWithDoc)
    }
}