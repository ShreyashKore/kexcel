package com.gyanoba.kexcel

import com.gyanoba.kexcel.number_format.CustomNumericNumFormat
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.utils.ExcelColor
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-validation against **Apache POI**, used here purely as an independent OOXML
 * oracle. POI is a `jvmTest`-only dependency and is never part of the published artifact.
 *
 * The rest of the suite checks Kexcel against itself: write bytes, read them back, compare.
 * That cannot catch a defect where the reader and the writer agree on something no other
 * implementation accepts. These tests close that loop in both directions:
 *
 *  - **Kexcel writes → POI reads.** What we emit is real, mainstream-readable OOXML.
 *  - **POI writes → Kexcel reads.** We can ingest files produced by a different toolchain,
 *    with conventions of its own (styles table layout, inline vs. shared strings,
 *    `dimension`, pane and view state).
 *  - **POI writes → Kexcel edits and re-saves → POI reads.** The parts Kexcel does not
 *    model still survive the trip.
 *
 * This is the property that has to hold before the library grows a second spreadsheet
 * format: correctness is defined by what other implementations accept, not by our own
 * round trip.
 */
class PoiInteropTest {

    private fun poiWorkbook(build: (Workbook) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            build(wb)
            wb.write(out)
        }
        return out.toByteArray()
    }

    private fun <T> readWithPoi(bytes: ByteArray, block: (Workbook) -> T): T =
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use(block)

    // region --- Kexcel writes, POI reads ---

    @Test
    fun poiReadsEveryScalarValueKexcelWrites() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("some text"))
        sheet.updateCell(CellIndex.indexByString("A2"), IntCellValue(42))
        sheet.updateCell(CellIndex.indexByString("A3"), DoubleCellValue(12.5))
        sheet.updateCell(CellIndex.indexByString("A4"), BoolCellValue(true))
        sheet.updateCell(CellIndex.indexByString("A5"), BoolCellValue(false))
        sheet.updateCell(CellIndex.indexByString("A6"), DateCellValue(2023, 4, 20))
        sheet.updateCell(
            CellIndex.indexByString("A7"),
            DateTimeCellValue(2023, 4, 20, hour = 15, minute = 44, second = 13),
        )

        readWithPoi(excel.encode()!!) { wb ->
            val s = wb.getSheetAt(0)
            assertEquals("some text", s.getRow(0).getCell(0).stringCellValue)
            assertEquals(42.0, s.getRow(1).getCell(0).numericCellValue)
            assertEquals(12.5, s.getRow(2).getCell(0).numericCellValue)
            assertEquals(true, s.getRow(3).getCell(0).booleanCellValue)
            assertEquals(false, s.getRow(4).getCell(0).booleanCellValue)
            assertEquals(LocalDate.of(2023, 4, 20), s.getRow(5).getCell(0).localDateTimeCellValue.toLocalDate())
            assertEquals(
                LocalDateTime.of(2023, 4, 20, 15, 44, 13),
                s.getRow(6).getCell(0).localDateTimeCellValue,
            )
        }
    }

    @Test
    fun poiSeesADateCellAsADateNotAsARawSerialNumber() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(CellIndex.indexByString("A1"), DateCellValue(2024, 2, 29))

        readWithPoi(excel.encode()!!) { wb ->
            val cell = wb.getSheetAt(0).getRow(0).getCell(0)
            // POI applies its own serial->date conversion. Agreement here means Kexcel
            // writes the serial Excel's own epoch implies, leap-year quirk included.
            assertTrue(
                org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell),
                "date cell must carry a date number format, otherwise Excel shows a number",
            )
            assertEquals(LocalDate.of(2024, 2, 29), cell.localDateTimeCellValue.toLocalDate())
        }
    }

    @Test
    fun poiReadsKexcelFormulasAsFormulas() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(2))
        sheet.updateCell(CellIndex.indexByString("A2"), IntCellValue(3))
        sheet.updateCell(CellIndex.indexByString("B1"), FormulaCellValue("SUM(A1:A2)"))
        sheet.updateCell(CellIndex.indexByString("B2"), FormulaCellValue("""IF(A1<5,"low & slow","high")"""))

        readWithPoi(excel.encode()!!) { wb ->
            val s = wb.getSheetAt(0)
            assertEquals(CellType.FORMULA, s.getRow(0).getCell(1).cellType)
            assertEquals("SUM(A1:A2)", s.getRow(0).getCell(1).cellFormula)
            // `<` and `&` must reach POI unescaped, i.e. they were escaped correctly on write.
            assertEquals("""IF(A1<5,"low & slow","high")""", s.getRow(1).getCell(1).cellFormula)
        }
    }

    @Test
    fun poiCanEvaluateFormulasKexcelWrote() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(2))
        sheet.updateCell(CellIndex.indexByString("A2"), IntCellValue(3))
        sheet.updateCell(CellIndex.indexByString("B1"), FormulaCellValue("SUM(A1:A2)"))

        readWithPoi(excel.encode()!!) { wb ->
            val evaluated = wb.creationHelper.createFormulaEvaluator()
                .evaluate(wb.getSheetAt(0).getRow(0).getCell(1))
            // A formula that a real engine can evaluate proves the operands, the references
            // and the surrounding cells were all written coherently.
            assertEquals(5.0, evaluated.numberValue)
        }
    }

    @Test
    fun poiReadsKexcelStyling() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(
            CellIndex.indexByString("A1"),
            TextCellValue("styled"),
            cellStyle = CellStyle(
                fontColorHex = ExcelColor.red,
                fontSize = 20,
                bold = true,
                italic = true,
            ),
        )

        readWithPoi(excel.encode()!!) { wb ->
            wb as XSSFWorkbook
            val font = wb.getFontAt(wb.getSheetAt(0).getRow(0).getCell(0).cellStyle.fontIndex)
            assertEquals(true, font.bold)
            assertEquals(true, font.italic)
            assertEquals(20, font.fontHeightInPoints.toInt())
            assertEquals("FFF44336", font.xssfColor?.argbHex)
        }
    }

    @Test
    fun poiReadsKexcelNumberFormatCodes() {
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(
            CellIndex.indexByString("A1"),
            DoubleCellValue(1234.5),
            cellStyle = CellStyle(numberFormat = CustomNumericNumFormat("#,##0.00")),
        )

        readWithPoi(excel.encode()!!) { wb ->
            assertEquals("#,##0.00", wb.getSheetAt(0).getRow(0).getCell(0).cellStyle.dataFormatString)
        }
    }

    @Test
    fun poiSeesKexcelMergedRegions() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.merge(CellIndex.indexByString("A1"), CellIndex.indexByString("C3"))
        sheet.merge(CellIndex.indexByString("E1"), CellIndex.indexByString("F2"))

        readWithPoi(excel.encode()!!) { wb ->
            assertEquals(
                listOf("A1:C3", "E1:F2"),
                wb.getSheetAt(0).mergedRegions.map { it.formatAsString() },
            )
        }
    }

    @Test
    fun poiReadsEveryKexcelSheetByName() {
        val excel = Excel.createExcel()
        listOf("Alpha", "Beta & Co", "Gamma 3").forEach {
            excel[it].updateCell(CellIndex.indexByString("A1"), TextCellValue(it))
        }

        readWithPoi(excel.encode()!!) { wb ->
            listOf("Alpha", "Beta & Co", "Gamma 3").forEach { name ->
                val sheet = assertNotNull(wb.getSheet(name), "POI could not find sheet `$name`")
                assertEquals(name, sheet.getRow(0).getCell(0).stringCellValue)
            }
        }
    }

    @Test
    fun anEmptyKexcelWorkbookIsAValidPackage() {
        val bytes = Excel.createExcel().encode()!!
        // OPCPackage validates the Open Packaging Conventions layer on open: content types
        // cover every part, and relationship targets all resolve.
        OPCPackage.open(ByteArrayInputStream(bytes)).use { pkg ->
            assertTrue(pkg.parts.isNotEmpty())
        }
        readWithPoi(bytes) { wb ->
            assertEquals(1, wb.numberOfSheets)
            assertEquals("Sheet1", wb.getSheetAt(0).sheetName)
        }
    }

    @Test
    fun aPopulatedKexcelWorkbookIsAValidPackage() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]
        sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("x"))
        sheet.updateCell(CellIndex.indexByString("B2"), DoubleCellValue(1.5))
        sheet.merge(CellIndex.indexByString("D1"), CellIndex.indexByString("E2"))
        excel["Second"].updateCell(CellIndex.indexByString("A1"), IntCellValue(7))

        OPCPackage.open(ByteArrayInputStream(excel.encode()!!)).use { pkg ->
            assertTrue(pkg.parts.isNotEmpty())
        }
    }

    // endregion

    // region --- POI writes, Kexcel reads ---

    @Test
    fun kexcelReadsScalarValuesPoiWrote() {
        val bytes = poiWorkbook { wb ->
            val s = wb.createSheet("Data")
            val row = s.createRow(0)
            row.createCell(0).setCellValue("some text")
            row.createCell(1).setCellValue(42.0)
            row.createCell(2).setCellValue(12.5)
            row.createCell(3).setCellValue(true)
            row.createCell(4).setCellFormula("B1*2")
        }

        val sheet = assertNotNull(Excel.decodeBytes(bytes).tables["Data"])
        assertEquals(TextCellValue("some text"), sheet.rows[0][0]?.value)
        assertEquals(IntCellValue(42), sheet.rows[0][1]?.value)
        assertEquals(DoubleCellValue(12.5), sheet.rows[0][2]?.value)
        assertEquals(BoolCellValue(true), sheet.rows[0][3]?.value)
        assertEquals(FormulaCellValue("B1*2"), sheet.rows[0][4]?.value)
    }

    @Test
    fun kexcelReadsDatesPoiWrote() {
        val bytes = poiWorkbook { wb ->
            val dateStyle = wb.createCellStyle().apply {
                dataFormat = wb.createDataFormat().getFormat("yyyy-mm-dd")
            }
            val row = wb.createSheet("D").createRow(0)
            row.createCell(0).apply {
                setCellValue(LocalDate.of(2023, 4, 20))
                cellStyle = dateStyle
            }
        }

        assertEquals(
            DateCellValue(2023, 4, 20),
            Excel.decodeBytes(bytes).tables["D"]!!.rows[0][0]?.value,
        )
    }

    @Test
    fun kexcelReadsMergedRegionsPoiWrote() {
        val bytes = poiWorkbook { wb ->
            val s = wb.createSheet("M")
            s.createRow(0).createCell(0).setCellValue("anchor")
            s.addMergedRegion(CellRangeAddress(0, 2, 0, 2))
            s.addMergedRegion(CellRangeAddress(0, 1, 4, 5))
        }

        val excel = Excel.decodeBytes(bytes)
        assertEquals(listOf("A1:C3", "E1:F2"), excel.getMergedCells("M"))
        assertEquals(TextCellValue("anchor"), excel["M"].cell(CellIndex.indexByString("A1")).value)
    }

    @Test
    fun kexcelReadsAnErrorCellPoiWrote() {
        val bytes = poiWorkbook { wb ->
            wb.createSheet("E").createRow(0).createCell(0)
                .setCellErrorValue(FormulaError.DIV0.code)
        }

        // Kexcel has no dedicated error value type, so an error cell surfaces as a
        // FormulaCellValue holding the error literal. Pinned here so a future
        // ErrorCellValue is a deliberate, visible change rather than a silent one.
        assertEquals(
            FormulaCellValue("#DIV/0!"),
            Excel.decodeBytes(bytes).tables["E"]!!.rows[0][0]?.value,
        )
    }

    // endregion

    // region --- POI writes, Kexcel edits, POI reads back ---

    @Test
    fun editingAPoiWorkbookKeepsTheSheetItDidNotTouch() {
        val bytes = poiWorkbook { wb ->
            wb.createSheet("Keep").createRow(0).createCell(0).setCellValue("untouched")
            wb.createSheet("Edit").createRow(0).createCell(0).setCellValue("before")
        }

        val excel = Excel.decodeBytes(bytes)
        excel["Edit"].updateCell(CellIndex.indexByString("A1"), TextCellValue("after"))

        readWithPoi(excel.encode()!!) { wb ->
            assertEquals("untouched", wb.getSheet("Keep").getRow(0).getCell(0).stringCellValue)
            assertEquals("after", wb.getSheet("Edit").getRow(0).getCell(0).stringCellValue)
        }
    }

    @Test
    fun frozenPanesSurviveAKexcelRoundTrip() {
        val bytes = poiWorkbook { wb ->
            val s = wb.createSheet("S")
            s.createRow(0).createCell(0).setCellValue("header")
            s.createFreezePane(1, 1)
        }

        val saved = Excel.decodeBytes(bytes).encode()!!

        // `<pane>` lives inside `<sheetView>`, which the writer rewrites to keep the
        // `rightToLeft` flag in sync. Rebuilding that element instead of patching it used
        // to drop the freeze, the selection and every view attribute on every save.
        readWithPoi(saved) { wb ->
            val pane = assertNotNull(wb.getSheetAt(0).paneInformation, "freeze pane was dropped")
            assertEquals(1, pane.horizontalSplitPosition.toInt())
            assertEquals(1, pane.verticalSplitPosition.toInt())
            assertTrue(pane.isFreezePane)
        }
    }

    @Test
    fun autoFilterSurvivesAKexcelRoundTrip() {
        val bytes = poiWorkbook { wb ->
            val s = wb.createSheet("S")
            s.createRow(0).createCell(0).setCellValue("header")
            s.setAutoFilter(CellRangeAddress(0, 0, 0, 2))
        }

        val saved = Excel.decodeBytes(bytes).encode()!!
        val xml = readZipEntry(saved, "xl/worksheets/sheet1.xml").decodeToString()
        assertTrue(xml.contains("""<autoFilter ref="A1:C1"""), "autoFilter was dropped:\n$xml")
    }

    @Test
    fun hiddenSheetsStayHiddenAcrossAKexcelRoundTrip() {
        val bytes = poiWorkbook { wb ->
            wb.createSheet("Visible").createRow(0).createCell(0).setCellValue("a")
            wb.createSheet("Hidden").createRow(0).createCell(0).setCellValue("b")
            wb.setSheetHidden(1, true)
        }

        val saved = Excel.decodeBytes(bytes).encode()!!
        readWithPoi(saved) { wb ->
            assertTrue(wb.isSheetHidden(wb.getSheetIndex("Hidden")))
            assertTrue(!wb.isSheetHidden(wb.getSheetIndex("Visible")))
        }
    }

    @Test
    fun definedNamesSurviveAKexcelRoundTrip() {
        val bytes = poiWorkbook { wb ->
            wb.createSheet("S").createRow(0).createCell(0).setCellValue(1.0)
            wb.createName().apply {
                nameName = "MyRange"
                refersToFormula = "S!\$A\$1"
            }
        }

        val saved = Excel.decodeBytes(bytes).encode()!!
        readWithPoi(saved) { wb ->
            assertEquals(
                listOf("MyRange" to "S!\$A\$1"),
                wb.allNames.map { it.nameName to it.refersToFormula },
            )
        }
    }

    @Test
    fun defaultRowHeightSurvivesEvenWithoutADefaultColumnWidth() {
        // POI writes <sheetFormatPr defaultRowHeight="15.0"/> with no defaultColWidth.
        // Reading the two attributes as an all-or-nothing pair dropped both, and the
        // writer then deleted the now-empty element.
        val bytes = poiWorkbook { wb ->
            wb.createSheet("S").createRow(0).createCell(0).setCellValue("a")
        }

        val excel = Excel.decodeBytes(bytes)
        assertEquals(15.0, excel["S"].defaultRowHeight)

        readWithPoi(excel.encode()!!) { wb ->
            assertEquals(15.0f, wb.getSheetAt(0).defaultRowHeightInPoints)
        }
    }

    @Test
    fun aKexcelEditedPoiWorkbookIsStillAValidPackage() {
        val bytes = poiWorkbook { wb ->
            val s = wb.createSheet("S")
            s.createRow(0).createCell(0).setCellValue("a")
            s.addMergedRegion(CellRangeAddress(1, 2, 0, 1))
        }

        val excel = Excel.decodeBytes(bytes)
        excel["S"].updateCell(CellIndex.indexByString("D4"), TextCellValue("added"))
        excel["Extra"].updateCell(CellIndex.indexByString("A1"), IntCellValue(1))

        OPCPackage.open(ByteArrayInputStream(excel.encode()!!)).use { pkg ->
            assertTrue(pkg.parts.isNotEmpty())
        }
    }

    // endregion
}
