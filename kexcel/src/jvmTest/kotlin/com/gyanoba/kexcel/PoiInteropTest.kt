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
}
