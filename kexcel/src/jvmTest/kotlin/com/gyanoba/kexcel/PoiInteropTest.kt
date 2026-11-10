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
    fun formulasWithNonNumericResultsSurviveAKexcelRoundTrip() {
        // Once evaluated, POI caches these as t="str" / t="b" / t="e" next to the <f>.
        val bytes = poiWorkbook { wb ->
            val row = wb.createSheet("F").createRow(0)
            row.createCell(0).cellFormula = """CONCATENATE("a","b")"""
            row.createCell(1).cellFormula = "1>0"
            row.createCell(2).cellFormula = "1/0"
            wb.creationHelper.createFormulaEvaluator().evaluateAll()
        }

        val excel = Excel.decodeBytes(bytes)
        assertEquals(FormulaCellValue("""CONCATENATE("a","b")"""), excel["F"].rows[0][0]?.value)
        assertEquals(FormulaCellValue("1>0"), excel["F"].rows[0][1]?.value)
        assertEquals(FormulaCellValue("1/0"), excel["F"].rows[0][2]?.value)

        readWithPoi(excel.encode()!!) { wb ->
            val row = wb.getSheetAt(0).getRow(0)
            assertEquals("""CONCATENATE("a","b")""", row.getCell(0).cellFormula)
            assertEquals("1>0", row.getCell(1).cellFormula)
            assertEquals("1/0", row.getCell(2).cellFormula)
        }
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
    fun aFontWithAnExplicitlyOffBoldIsNotReadAsBold() {
        // <b val="false"/> is a valid "not bold"; only a bare <b/> or val="1"/"true" is on.
        val bytes = poiWorkbook { wb ->
            wb as XSSFWorkbook
            val font = wb.createFont()
            font.ctFont.addNewB().setVal(false)
            font.ctFont.addNewI().setVal(true)
            val style = wb.createCellStyle().apply { setFont(font) }
            wb.createSheet("S").createRow(0).createCell(0).apply {
                setCellValue("a")
                cellStyle = style
            }
        }

        val style = Excel.decodeBytes(bytes)["S"].cell(CellIndex.indexByString("A1")).cellStyle!!
        assertEquals(false, style.isBold)
        assertEquals(true, style.isItalic)
    }

    @Test
    fun aNewFontFamilyOnAPoiStyleIsNotOverriddenByTheThemeFont() {
        // POI's default font is <name val="Calibri"/><scheme val="minor"/>. Keeping the
        // scheme after renaming the family makes Excel keep rendering Calibri.
        val bytes = poiWorkbook { wb -> wb.createSheet("S").createRow(0).createCell(0).setCellValue("a") }

        val excel = Excel.decodeBytes(bytes)
        val cell = excel["S"].cell(CellIndex.indexByString("A1"))
        cell.cellStyle = cell.cellStyle!!.copyWith(fontFamilyVal = "Arial")

        readWithPoi(excel.encode()!!) { wb ->
            wb as XSSFWorkbook
            val font = wb.getFontAt(wb.getSheetAt(0).getRow(0).getCell(0).cellStyle.fontIndex)
            assertEquals("Arial", font.fontName)
            assertEquals(null, font.ctFont.schemeList.firstOrNull())
        }
    }

    @Test
    fun changingTheDefaultSheetLeavesOnlyThatSheetSelected() {
        // POI marks the first sheet tabSelected. <sheetView> is patched in place on save,
        // so that flag survives; unless it is cleared, Excel opens the old and the new
        // default sheet grouped together.
        val bytes = poiWorkbook { wb ->
            wb.createSheet("First").createRow(0).createCell(0).setCellValue("a")
            wb.createSheet("Second").createRow(0).createCell(0).setCellValue("b")
            wb.createSheet("Third").createRow(0).createCell(0).setCellValue("c")
            wb.setActiveSheet(2)
            wb.getSheetAt(0).isSelected = true
            wb.getSheetAt(2).isSelected = true
        }

        val excel = Excel.decodeBytes(bytes)
        excel.setDefaultSheet("Second")

        readWithPoi(excel.encode()!!) { wb ->
            assertEquals("Second", wb.getSheetName(wb.activeSheetIndex))
            val selected = (0 until wb.numberOfSheets)
                .filter { wb.getSheetAt(it).isSelected }
                .map { wb.getSheetName(it) }
            assertEquals(listOf("Second"), selected)
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


    // region --- Header and footer ---

    @Test
    fun poiReadsAKexcelHeaderVerbatim() {
        // §18.3.1.39: the header string is a format of its own — &L/&C/&R pick a section,
        // &P is the page number — and `&` must reach the file as a single escaped
        // ampersand. Hand-escaping it before the XML layer did turned every code into text.
        val header = "&LSample Company & Sons\nLine 2 \"quoted\" <tag>&RPage &P of &N"
        val excel = Excel.createExcel()
        excel["Sheet1"].headerFooter = com.gyanoba.kexcel.sheet.HeaderFooter(
            null, null, null, null, null, null, null, null, oddFooter = "Stamped &D", oddHeader = header,
        )
        val bytes = excel.encode()!!

        readWithPoi(bytes) { wb ->
            val headerFooter = (wb.getSheetAt(0) as org.apache.poi.xssf.usermodel.XSSFSheet).ctWorksheet.headerFooter
            assertEquals(header, headerFooter.oddHeader)
            assertEquals("Stamped &D", headerFooter.oddFooter)
        }
        assertEquals(header, Excel.decodeBytes(bytes)["Sheet1"].headerFooter?.oddHeader)
    }

    @Test
    fun aPoiHeaderSurvivesAKexcelRoundTrip() {
        val source = poiWorkbook { wb ->
            val sheet = wb.createSheet("S")
            sheet.createRow(0).createCell(0).setCellValue(1.0)
            sheet.header.left = "Example & Campaign"
            sheet.header.center = "two\nlines"
            sheet.footer.right = "amp stamp &P"
        }

        assertOnlyDiffers(source, roundTrip(source))
        assertEquals("&Ctwo\nlines&LExample & Campaign", Excel.decodeBytes(source)["S"].headerFooter?.oddHeader)
    }

    // endregion


    // region --- An unedited cell is written back as it was stored ---

    private fun formulaWorkbook(): ByteArray = poiWorkbook { wb ->
        val sheet = wb.createSheet("S")
        sheet.createRow(0).createCell(0).setCellValue(2.0)
        sheet.createRow(1).createCell(0).setCellValue(3.0)
        val sum = sheet.createRow(2).createCell(0).apply { cellFormula = "A1+A2" }
        val text = sheet.createRow(3).createCell(0).apply { cellFormula = "\"x\"&\"y\"" }
        val flag = sheet.createRow(4).createCell(0).apply { cellFormula = "A1<A2" }
        wb.creationHelper.createFormulaEvaluator().apply { evaluateFormulaCell(sum); evaluateFormulaCell(text); evaluateFormulaCell(flag) }
    }

    @Test
    fun cachedFormulaResultsSurviveANoEditRoundTrip() {
        // The cached <v> is what every reader that does not calculate shows: pandas,
        // previewers, POI without an evaluator. It used to be emptied on every save.
        val source = formulaWorkbook()
        val saved = roundTrip(source)

        assertOnlyDiffers(source, saved)
        readWithPoi(saved) { wb ->
            val sheet = wb.getSheetAt(0)
            assertEquals(5.0, sheet.getRow(2).getCell(0).numericCellValue)
            assertEquals(CellType.STRING, sheet.getRow(3).getCell(0).cachedFormulaResultType)
            assertEquals("xy", sheet.getRow(3).getCell(0).stringCellValue)
            assertEquals(true, sheet.getRow(4).getCell(0).booleanCellValue)
        }
    }

    @Test
    fun editingAValueDropsTheCachedResultsAndAsksForARecalculation() {
        // Once A1 changes, the cached 5.0 is wrong. A stale result is worse than none, so
        // it is not written, and fullCalcOnLoad (§18.2.2) tells Excel to recalculate —
        // what POI's setForceFormulaRecalculation does.
        val saved = roundTrip(formulaWorkbook()) { it["S"].updateCell(CellIndex.indexByString("A1"), IntCellValue(40)) }

        assertEquals("", rawCell(saved, "A3").getElementsByTag("v").first()!!.text())
        assertEquals("A1+A2", rawCell(saved, "A3").getElementsByTag("f").first()!!.text())
        readWithPoi(saved) { wb ->
            assertTrue(wb.forceFormulaRecalculation)
            val cell = wb.getSheetAt(0).getRow(2).getCell(0)
            assertEquals(43.0, wb.creationHelper.createFormulaEvaluator().evaluate(cell).numberValue)
        }
    }

    @Test
    fun stylePropertiesKexcelDoesNotModelSurviveANoEditRoundTrip() {
        // Each of these differs from the default <xf> only in something CellStyle has no
        // field for. Looked up by equality they all collapsed onto the first "equal" <xf>:
        // fills vanished, unlocked cells became locked, and one cell gained the
        // strike-through of another.
        val source = poiWorkbook { wb ->
            val sheet = wb.createSheet("S")
            var row = 0
            fun cell(value: String, style: (org.apache.poi.xssf.usermodel.XSSFCellStyle) -> Unit) {
                val c = sheet.createRow(row++).createCell(0)
                c.setCellValue(value)
                c.cellStyle = (wb.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle).also(style)
            }
            fun themed(theme: Long, tint: Double = 0.0) = org.apache.poi.xssf.usermodel.XSSFColor.from(
                org.openxmlformats.schemas.spreadsheetml.x2006.main.CTColor.Factory.newInstance().also { it.theme = theme; it.tint = tint }, null,
            )
            cell("strike-through") { it.setFont(wb.createFont().apply { strikeout = true }) }
            cell("theme fill") { it.fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND; it.setFillForegroundColor(themed(4, 0.4)) }
            cell("indexed fill") { it.fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND; it.fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.YELLOW.index }
            cell("pattern fill") { it.fillPattern = org.apache.poi.ss.usermodel.FillPatternType.THIN_HORZ_BANDS; it.fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.RED.index }
            cell("unlocked") { it.locked = false }
            cell("hidden formula") { it.hidden = true }
            cell("indent") { it.indention = 3 }
            cell("justify") { it.alignment = org.apache.poi.ss.usermodel.HorizontalAlignment.JUSTIFY }
            cell("theme font colour") { it.setFont((wb.createFont() as org.apache.poi.xssf.usermodel.XSSFFont).apply { setColor(themed(5)) }) }
            cell("10.5 pt") { it.setFont((wb.createFont() as org.apache.poi.xssf.usermodel.XSSFFont).apply { setFontHeight(10.5) }) }
            sheet.createRow(row++).createCell(0).apply { setCellValue(1234.5); cellStyle = wb.createCellStyle().apply { dataFormat = 44 } }
            sheet.createRow(row).createCell(0).apply { setCellValue(12.0); cellStyle = wb.createCellStyle().apply { dataFormat = 5 } }
        }

        assertOnlyDiffers(source, roundTrip(source))
    }

    @Test
    fun loadingAWorkbookDoesNotChangeAnyNumberFormat() {
        // Any format may sit on any value. Kexcel used to swap in the default format for
        // the value's type whenever it judged the two a poor match — text formatted `@`
        // became General, `hh:mm:ss` became `h:mm` — and, the replacement being a new
        // style, the cell lost its font, fill and border with it.
        val source = poiWorkbook { wb ->
            val sheet = wb.createSheet("S")
            val formats = wb.createDataFormat()
            var row = 0
            fun styled(format: String) = wb.createCellStyle().apply {
                dataFormat = formats.getFormat(format)
                setFont(wb.createFont().apply { bold = true; fontName = "Arial" })
                fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
                fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.LIGHT_GREEN.index
                borderBottom = org.apache.poi.ss.usermodel.BorderStyle.THIN
            }
            sheet.createRow(row++).createCell(0).apply { setCellValue("00123"); cellStyle = styled("@") }
            sheet.createRow(row++).createCell(0).apply { setCellValue("Total"); cellStyle = styled("#,##0") }
            sheet.createRow(row++).createCell(0).apply { setCellValue(0.5); cellStyle = styled("hh:mm:ss") }
            sheet.createRow(row++).createCell(0).apply { setCellValue(1.25); cellStyle = styled("[h]:mm:ss") }
            sheet.createRow(row++).createCell(0).apply { setCellValue(0.25); cellStyle = styled("m/d/yy") }
            sheet.createRow(row).createCell(0).apply { setCellValue(true); cellStyle = styled("yyyy-mm-dd") }
        }

        assertOnlyDiffers(source, roundTrip(source))
    }

    @Test
    fun unEditedNumbersAreWrittenBackDigitForDigit() {
        // A date or time is read into whole milliseconds; rebuilding the serial from those
        // changed the stored number (POI corpus 58747.xlsx: 42354.58333333334 became
        // 42354.583333333336). The first value is a plain number that Kexcel takes for a
        // date because of the `[Red]` in its format.
        val values = listOf("[Red]0.00" to 1234.123456789, "h:mm:ss" to 0.64604166, "yyyy-mm-dd h:mm" to 42354.58333333334, "0.00" to 0.1 + 0.2)
        val source = poiWorkbook { wb ->
            val sheet = wb.createSheet("S")
            values.forEachIndexed { i, (format, value) ->
                sheet.createRow(i).createCell(0).apply {
                    setCellValue(value)
                    cellStyle = wb.createCellStyle().apply { dataFormat = wb.createDataFormat().getFormat(format) }
                }
            }
        }

        val saved = roundTrip(source)

        assertOnlyDiffers(source, saved)
        readWithPoi(saved) { wb -> assertEquals(values.map { it.second }, values.indices.map { wb.getSheetAt(0).getRow(it).getCell(0).numericCellValue }) }
    }

    @Test
    fun changingAValueKeepsTheCellsFormatting() {
        // updateCell without a style used to reset the cell to the bare default style.
        val source = poiWorkbook { wb ->
            val style = wb.createCellStyle().apply {
                dataFormat = 10 // 0.00%
                setFont(wb.createFont().apply { bold = true })
                fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
                fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.YELLOW.index
            }
            val sheet = wb.createSheet("S")
            for (r in 0..2) sheet.createRow(r).createCell(0).apply { setCellValue(0.25); cellStyle = style }
        }

        val saved = roundTrip(source) { excel ->
            excel["S"].updateCell(CellIndex.indexByString("A1"), DoubleCellValue(0.5))
            excel["S"].cell(CellIndex.indexByString("A2")).value = DoubleCellValue(0.75)
        }

        assertOnlyDiffers(source, saved, expected = setOf("number"))
        readWithPoi(saved) { wb -> assertEquals(listOf(0.5, 0.75, 0.25), (0..2).map { wb.getSheetAt(0).getRow(it).getCell(0).numericCellValue }) }
    }

    // endregion


    // region --- New and derived styles ---

    private fun signatureOf(bytes: ByteArray, ref: String): Map<String, String> = readWithPoi(bytes) { wb ->
        val address = org.apache.poi.ss.util.CellReference(ref)
        val style = wb.getSheetAt(0).getRow(address.row).getCell(address.col.toInt()).cellStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle
        PoiOracle.styleSignature(style).associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    @Test
    fun aStyleDerivedFromAnExistingOneKeepsEverythingThatWasNotChanged() {
        // copyWith on a style read from a file. The new <xf> used to get fontId, fillId or
        // borderId 0 for whichever of the three it *kept*, because an id was only worked
        // out for entries added in the same save; and nothing CellStyle does not model
        // (here: unlocked, indent) was carried over.
        val source = poiWorkbook { wb ->
            val style = (wb.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle).apply {
                setFont(wb.createFont().apply { bold = true; fontName = "Arial"; fontHeightInPoints = 14 })
                fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
                setFillForegroundColor(org.apache.poi.xssf.usermodel.XSSFColor(byteArrayOf(0xFF.toByte(), 0, 0), null))
                borderLeft = org.apache.poi.ss.usermodel.BorderStyle.THIN
                borderBottom = org.apache.poi.ss.usermodel.BorderStyle.THIN
                locked = false
                indention = 2
            }
            wb.createSheet("S").createRow(0).createCell(0).apply { setCellValue("a"); cellStyle = style }
        }

        val saved = roundTrip(source) { excel ->
            val sheet = excel["S"]
            val a1 = sheet.cell(CellIndex.indexByString("A1")).cellStyle!!
            sheet.updateCell(CellIndex.indexByString("B1"), TextCellValue("b"), a1.copyWith(italicVal = true))
            sheet.updateCell(CellIndex.indexByString("C1"), TextCellValue("c"), a1.copyWith(backgroundColorHexVal = ExcelColor.fromHexString("FF0000FF")))
            sheet.updateCell(
                CellIndex.indexByString("D1"), TextCellValue("d"),
                a1.copyWith(leftBorderVal = com.gyanoba.kexcel.sheet.Border(borderStyle = com.gyanoba.kexcel.sheet.BorderStyle.Thick)),
            )
            sheet.updateCell(CellIndex.indexByString("E1"), IntCellValue(5), a1.copyWith(numberFormat = CustomNumericNumFormat("0.000")))
        }

        val a1 = signatureOf(saved, "A1")
        fun changed(ref: String) = signatureOf(saved, ref).filter { (part, value) -> a1[part] != value }.keys
        assertEquals(signatureOf(source, "A1"), a1)
        assertEquals(setOf("font"), changed("B1"))
        assertTrue("italic=true" in signatureOf(saved, "B1").getValue("font"))
        assertEquals(setOf("fill"), changed("C1"))
        assertTrue("FF0000FF" in signatureOf(saved, "C1").getValue("fill"))
        assertEquals(setOf("border"), changed("D1"))
        assertEquals("THICK,NONE,NONE,THIN", signatureOf(saved, "D1").getValue("border"))
        assertEquals(setOf("number format"), changed("E1"))
    }

    @Test
    fun aNewStyleGetsItsOwnFontFillAndBorderInAWorkbookThatAlreadyHasMany() {
        // Three traps for the index arithmetic: fonts no cell uses (every Excel workbook
        // has some), and a conditional format, whose <dxf> holds a <font>, a <fill> and a
        // <border> that are not numbered by fontId / fillId / borderId (§18.8.45).
        val source = poiWorkbook { wb ->
            repeat(3) { wb.createFont().apply { fontName = "Unused $it" } }
            val sheet = wb.createSheet("S")
            sheet.createRow(0).createCell(0).setCellValue(10.0)
            val formatting = sheet.sheetConditionalFormatting
            val rule = formatting.createConditionalFormattingRule(org.apache.poi.ss.usermodel.ComparisonOperator.GT, "5")
            rule.createPatternFormatting().apply {
                fillBackgroundColor = org.apache.poi.ss.usermodel.IndexedColors.RED.index
                fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND.code
            }
            rule.createFontFormatting().setFontStyle(true, true)
            rule.createBorderFormatting().apply { borderLeft = org.apache.poi.ss.usermodel.BorderStyle.THIN }
            formatting.addConditionalFormatting(arrayOf(CellRangeAddress.valueOf("A1:A5")), rule)
        }

        val saved = roundTrip(source) { excel ->
            excel["S"].updateCell(
                CellIndex.indexByString("C1"), TextCellValue("c"),
                CellStyle(
                    bold = true, fontFamily = "Courier New", fontSize = 20,
                    backgroundColorHex = ExcelColor.fromHexString("FF00FF00"),
                    leftBorder = com.gyanoba.kexcel.sheet.Border(borderStyle = com.gyanoba.kexcel.sheet.BorderStyle.Thick),
                ),
            )
        }

        val c1 = signatureOf(saved, "C1")
        assertTrue(c1.getValue("font").startsWith("Courier New,400,bold=true"), c1.getValue("font"))
        assertTrue("FF00FF00" in c1.getValue("fill"), c1.getValue("fill"))
        assertEquals("THICK,NONE,NONE,NONE", c1.getValue("border"))
        assertOnlyDiffers(source, saved, expected = setOf("cell appeared"))
    }

    @Test
    fun poiSeesTheBordersKexcelWrites() {
        // POI reports no border unless the <xf> says applyBorder (XSSFCellStyle.getBorderLeft).
        val excel = Excel.createExcel()
        excel["Sheet1"].updateCell(
            CellIndex.indexByString("A1"), TextCellValue("boxed"),
            CellStyle(
                leftBorder = com.gyanoba.kexcel.sheet.Border(borderStyle = com.gyanoba.kexcel.sheet.BorderStyle.Thin),
                bottomBorder = com.gyanoba.kexcel.sheet.Border(borderStyle = com.gyanoba.kexcel.sheet.BorderStyle.Double),
            ),
        )

        assertEquals("THIN,NONE,NONE,DOUBLE", signatureOf(excel.encode()!!, "A1").getValue("border"))
    }

    @Test
    fun anyArgbBackgroundColourIsWrittenAsAFill() {
        // Only colours whose alpha is FF used to be written. A translucent one — many of the
        // named ExcelColor constants — was counted but skipped, so the next cell's fill id
        // pointed at the wrong fill and the last one past the end of the table.
        val colours = listOf(ExcelColor.black26, ExcelColor.fromHexString("FFFF0000"), ExcelColor.fromHexString("FF0000FF"))
        val excel = Excel.createExcel()
        colours.forEachIndexed { i, colour ->
            excel["Sheet1"].updateCell(CellIndex.indexByColumnRow(0, i), TextCellValue("c$i"), CellStyle(backgroundColorHex = colour))
        }
        val bytes = excel.encode()!!

        readWithPoi(bytes) { wb ->
            val written = colours.indices.map { (wb.getSheetAt(0).getRow(it).getCell(0).cellStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle).fillForegroundColorColor.argbHex }
            assertEquals(colours.map { it.colorHex }, written)
        }
        val reread = Excel.decodeBytes(bytes)["Sheet1"]
        assertEquals(colours, colours.indices.map { reread.cell(CellIndex.indexByColumnRow(0, it)).cellStyle?.backgroundColor })
    }

    @Test
    fun changingAStyleObjectReadFromAFileIsSaved() {
        // The object returned by Data.cellStyle is the one the parser built. A change made
        // to it used to be dropped silently: the cell was written under its old index.
        val source = poiWorkbook { wb -> wb.createSheet("S").createRow(0).createCell(0).apply { setCellValue("a"); cellStyle = wb.createCellStyle().apply { locked = false } } }

        val saved = roundTrip(source) { it["S"].cell(CellIndex.indexByString("A1")).cellStyle!!.isBold = true }

        val after = signatureOf(saved, "A1")
        assertTrue("bold=true" in after.getValue("font"))
        assertEquals("locked:false,hidden:false", after.getValue("protection"))
    }

    // endregion


    // region --- Rows and columns ---

    private fun workbookWithRowAndColumnProperties(): ByteArray = poiWorkbook { wb ->
        val sheet = wb.createSheet("S") as org.apache.poi.xssf.usermodel.XSSFSheet
        val bold = (wb.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle).apply { setFont(wb.createFont().apply { bold = true }) }
        sheet.createRow(0).createCell(0).setCellValue("a")
        sheet.createRow(1).apply { createCell(0).setCellValue("hidden row"); zeroHeight = true }
        sheet.createRow(2).apply { createCell(0).setCellValue("styled row"); rowStyle = bold }
        sheet.createRow(3).heightInPoints = 40f // a height, no cells
        sheet.createRow(4).createCell(0).setCellValue("grouped")
        sheet.createRow(5).createCell(0).setCellValue("grouped")
        sheet.groupRow(4, 5)
        sheet.createRow(6).apply { createCell(0).setCellValue("tall"); heightInPoints = 33.3f }
        sheet.getRow(0).createCell(25).setCellValue("z")
        sheet.setColumnHidden(1, true)
        sheet.setColumnWidth(2, 5000)
        sheet.setDefaultColumnStyle(3, bold)
        sheet.groupColumn(5, 6)
        // One <col> for columns J to T.
        sheet.ctWorksheet.getColsArray(0).addNewCol().apply { min = 10; max = 20; width = 30.0; customWidth = true }
    }

    @Test
    fun rowAndColumnPropertiesSurviveANoEditRoundTrip() {
        // <sheetData> and <cols> used to be rebuilt from cell values, heights and widths
        // alone: hidden rows and columns became visible, grouping and row/column styles
        // went, a <col> range kept its width on the first column only, and a row with a
        // height but no cells disappeared.
        val source = workbookWithRowAndColumnProperties()
        assertOnlyDiffers(source, roundTrip(source))
    }

    @Test
    fun aColumnRangeGivesItsWidthToEveryColumnInIt() {
        // §18.3.1.13: one <col min="10" max="20"> describes eleven columns.
        val sheet = Excel.decodeBytes(workbookWithRowAndColumnProperties())["S"]
        assertEquals(List(11) { 30.0 }, (9..19).map { sheet.getColumnWidth(it) })
    }

    @Test
    fun changingOneColumnWidthLeavesEverythingElseAboutTheColumnsAlone() {
        val source = workbookWithRowAndColumnProperties()
        val saved = roundTrip(source) { excel ->
            excel["S"].setColumnWidth(1, 12.0)   // the hidden column
            excel["S"].setColumnWidth(14, 50.0)  // in the middle of the J:T range
        }

        readWithPoi(saved) { wb ->
            val sheet = wb.getSheetAt(0)
            assertTrue(sheet.isColumnHidden(1), "a hidden column stays hidden when its width changes")
            assertEquals(12 * 256, sheet.getColumnWidth(1))
            assertEquals(50 * 256, sheet.getColumnWidth(14))
            assertEquals(listOf(7680, 7680, 7680, 7680), listOf(9, 13, 15, 19).map { sheet.getColumnWidth(it) })
            assertEquals(5000, sheet.getColumnWidth(2))
            assertTrue((sheet.getColumnStyle(3) as org.apache.poi.xssf.usermodel.XSSFCellStyle).font.bold)
            assertEquals(1, sheet.getColumnOutlineLevel(5))
            assertEquals(2048, sheet.getColumnWidth(0), "a column that never had a width is not given one")
        }
    }

    @Test
    fun changingARowHeightKeepsTheRowsOtherProperties() {
        val saved = roundTrip(workbookWithRowAndColumnProperties()) { excel ->
            excel["S"].setRowHeight(1, 20.0)  // the hidden row
            excel["S"].setRowHeight(9, 55.0)  // a row with no cells at all
        }

        readWithPoi(saved) { wb ->
            val sheet = wb.getSheetAt(0)
            assertTrue(sheet.getRow(1).zeroHeight)
            assertEquals(20f, sheet.getRow(1).heightInPoints)
            assertEquals(55f, sheet.getRow(9).heightInPoints, "a height set on an empty row is saved")
            assertEquals(1, sheet.getRow(4).outlineLevel.toInt())
            assertTrue((sheet.getRow(2) as org.apache.poi.xssf.usermodel.XSSFRow).rowStyle.font.bold)
            assertEquals(40f, sheet.getRow(3).heightInPoints)
        }
    }

    @Test
    fun theBorderAroundAMergedRegionSurvivesARoundTrip() {
        // Excel stores the border of a merged region on the empty cells beneath it. Kexcel
        // keeps only the top-left cell of a merge in its model, so those cells — and with
        // them three sides of the border — were dropped on save.
        val source = poiWorkbook { wb ->
            val sheet = wb.createSheet("S")
            for (r in 0..2) sheet.createRow(r).apply { for (c in 0..2) createCell(c) }
            sheet.getRow(0).getCell(0).setCellValue("merged")
            val region = CellRangeAddress.valueOf("A1:C3")
            sheet.addMergedRegion(region)
            for (set in listOf(
                org.apache.poi.ss.util.RegionUtil::setBorderTop, org.apache.poi.ss.util.RegionUtil::setBorderBottom,
                org.apache.poi.ss.util.RegionUtil::setBorderLeft, org.apache.poi.ss.util.RegionUtil::setBorderRight,
            )) set(org.apache.poi.ss.usermodel.BorderStyle.THICK, region, sheet)
        }

        val saved = roundTrip(source)

        assertOnlyDiffers(source, saved)
        // The model is unchanged: only the anchor is a cell.
        assertEquals(1, Excel.decodeBytes(source)["S"].rows.flatten().count { it != null })
    }

    // endregion
}
