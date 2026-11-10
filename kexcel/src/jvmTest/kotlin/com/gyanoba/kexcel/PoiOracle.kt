package com.gyanoba.kexcel

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.fail

/**
 * Apache POI as an independent reader: what a workbook *is*, according to an implementation
 * that shares no code with Kexcel.
 *
 * [differences] reduces two workbooks to the set of ways they differ — "formula", "cached
 * result", "style: fill", "row: hidden", … — which is what a fidelity test wants to assert
 * on: a no-edit save must produce the empty set, and an edit must change only what it meant to.
 */
internal object PoiOracle {

    fun workbook(build: (XSSFWorkbook) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            build(wb)
            wb.write(out)
        }
        return out.toByteArray()
    }

    fun <T> read(bytes: ByteArray, block: (XSSFWorkbook) -> T): T =
        XSSFWorkbook(ByteArrayInputStream(bytes)).use(block)

    /** Everything POI can say about one cell style, as comparable `name=value` parts. */
    fun styleSignature(style: XSSFCellStyle): List<String> {
        val font = style.font
        return listOf(
            "number format=${style.dataFormat}:${style.dataFormatString}",
            "font=${font.fontName},${font.fontHeight},bold=${font.bold},italic=${font.italic}," +
                "strike=${font.strikeout},underline=${font.underline},colour=${colour(font.xssfColor)},offset=${font.typeOffset}",
            "fill=${style.fillPattern},${colour(style.fillForegroundColorColor)},${colour(style.fillBackgroundColorColor)}",
            "border=${style.borderLeft},${style.borderRight},${style.borderTop},${style.borderBottom}",
            "alignment=${style.alignment},${style.verticalAlignment},wrap=${style.wrapText}," +
                "rotation=${style.rotation},indent=${style.indention},shrink=${style.shrinkToFit}",
            "protection=locked:${style.locked},hidden:${style.hidden}",
        )
    }

    private fun colour(c: XSSFColor?): String = if (c == null) "-" else
        "${c.argbHex}/theme:${if (c.isThemed) c.theme else -1}/indexed:${if (c.isIndexed) c.indexed.toInt() else -1}/tint:${c.tint}"

    private class CellFacts(
        val type: CellType, val value: String, val formula: String?, val rawValue: String?,
        val style: List<String>, val styled: Boolean,
    )

    private class SheetFacts(
        val cells: Map<String, CellFacts>,
        val rows: Map<Int, List<String>>,
        val columns: List<List<String>>,
        val facts: Map<String, String>,
    )

    private class Facts(val workbook: Map<String, String>, val sheets: Map<String, SheetFacts>)

    private fun <T> safe(default: T, f: () -> T): T = try { f() } catch (_: Throwable) { default }

    private fun facts(bytes: ByteArray): Facts = read(bytes) { wb ->
        val signatures = HashMap<Int, List<String>>()
        fun signature(style: XSSFCellStyle) = signatures.getOrPut(style.index.toInt()) { styleSignature(style) }

        val sheets = LinkedHashMap<String, SheetFacts>()
        for (i in 0 until wb.numberOfSheets) {
            val sheet = wb.getSheetAt(i)
            val cells = LinkedHashMap<String, CellFacts>()
            val rows = LinkedHashMap<Int, List<String>>()
            var lastColumn = 0
            for (row in sheet) {
                row as XSSFRow
                rows[row.rowNum] = listOf(
                    "hidden=${row.zeroHeight}",
                    "height=${if (row.ctRow.isSetHt) row.ctRow.ht.toString() else "-"}",
                    "outline level=${row.outlineLevel}",
                    "style=${if (row.isFormatted) signature(row.rowStyle).toString() else "-"}",
                )
                for (cell in row) {
                    cell as XSSFCell
                    lastColumn = maxOf(lastColumn, cell.columnIndex)
                    var formula: String? = null
                    val value = when (cell.cellType) {
                        CellType.STRING -> cell.richStringCellValue.string
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        CellType.ERROR -> cell.errorCellString
                        CellType.FORMULA -> { formula = safe("?") { cell.cellFormula }; "" }
                        else -> ""
                    }
                    cells[cell.address.formatAsString()] = CellFacts(
                        cell.cellType, value, formula,
                        if (cell.ctCell.isSetV) cell.ctCell.v else null,
                        signature(cell.cellStyle), cell.cellStyle.index.toInt() != 0,
                    )
                }
            }
            val columns = (0..minOf(lastColumn + 2, 80)).map { c ->
                listOf(
                    "width=${sheet.getColumnWidth(c)}",
                    "hidden=${sheet.isColumnHidden(c)}",
                    "outline level=${sheet.getColumnOutlineLevel(c)}",
                    "style=${safe("-") { (sheet.getColumnStyle(c) as XSSFCellStyle).let { if (it.index.toInt() == 0) "-" else signature(it).toString() } }}",
                )
            }
            val ct = sheet.ctWorksheet
            val sheetFacts = linkedMapOf(
                "merged regions" to sheet.mergedRegions.map { it.formatAsString() }.sorted().toString(),
                "hyperlinks" to safe("?") { sheet.hyperlinkList.map { it.cellRef }.toString() },
                "comments" to safe("?") { sheet.cellComments.keys.map { it.formatAsString() }.sorted().toString() },
                "data validations" to safe("?") { sheet.dataValidations.size.toString() },
                "conditional formatting" to safe("?") { sheet.sheetConditionalFormatting.numConditionalFormattings.toString() },
                "tables" to safe("?") { sheet.tables.size.toString() },
                "drawing shapes" to safe("?") { (sheet.drawingPatriarch?.shapes?.size ?: 0).toString() },
                "pane" to safe("?") { sheet.paneInformation?.let { "${it.verticalSplitPosition},${it.horizontalSplitPosition},${it.isFreezePane}" } ?: "-" },
                "autofilter" to safe("?") { if (ct.isSetAutoFilter) ct.autoFilter.ref else "-" },
                "protection" to safe("?") { sheet.protect.toString() },
                "default column width" to safe("?") { if (ct.isSetSheetFormatPr && ct.sheetFormatPr.isSetDefaultColWidth) ct.sheetFormatPr.defaultColWidth.toString() else "-" },
                "default row height" to safe("?") { if (ct.isSetSheetFormatPr) ct.sheetFormatPr.defaultRowHeight.toString() else "-" },
                "header and footer" to safe("?") {
                    if (ct.isSetHeaderFooter) ct.headerFooter.let { h ->
                        listOf(h.oddHeader, h.oddFooter, h.evenHeader, h.evenFooter, h.firstHeader, h.firstFooter).toString()
                    } else "-"
                },
            )
            sheets[wb.getSheetName(i)] = SheetFacts(cells, rows, columns, sheetFacts)
        }
        Facts(
            linkedMapOf(
                "sheet order" to sheets.keys.toString(),
                "defined names" to safe("?") { wb.allNames.map { "${it.nameName}|scope=${it.sheetIndex}|${safe("?") { it.refersToFormula }}" }.sorted().toString() },
                "active sheet" to wb.activeSheetIndex.toString(),
                "sheet visibility" to (0 until wb.numberOfSheets).map { wb.getSheetVisibility(it) }.toString(),
                "date1904" to wb.isDate1904.toString(),
            ),
            sheets,
        )
    }

    /**
     * The ways [after] differs from [before], keyed by kind of difference, each with one
     * example. Sheets are matched by name; a sheet present on one side only shows up under
     * "workbook: sheet order".
     */
    fun differences(before: ByteArray, after: ByteArray): Map<String, String> {
        val a = facts(before)
        val b = facts(after)
        val found = LinkedHashMap<String, String>()
        fun hit(kind: String, example: String) { found.getOrPut(kind) { example } }

        for ((k, v) in a.workbook) if (b.workbook[k] != v) hit("workbook: $k", "$v -> ${b.workbook[k]}")
        for ((name, sa) in a.sheets) {
            val sb = b.sheets[name] ?: continue
            for ((ref, ca) in sa.cells) {
                val at = "$name!$ref"
                val cb = sb.cells[ref]
                if (cb == null) {
                    if (ca.type == CellType.BLANK) { if (ca.styled) hit("styled blank cell dropped", at) } else hit("cell lost", "$at ${ca.type} '${ca.value}${ca.formula ?: ""}'")
                    continue
                }
                if (ca.type != cb.type) hit("cell type", "$at ${ca.type} -> ${cb.type}")
                else when (ca.type) {
                    CellType.STRING -> if (ca.value != cb.value) hit("string", "$at '${ca.value}' -> '${cb.value}'")
                    CellType.NUMERIC -> if (ca.value != cb.value) hit("number", "$at ${ca.value} -> ${cb.value}")
                    CellType.BOOLEAN -> if (ca.value != cb.value) hit("boolean", at)
                    CellType.ERROR -> if (ca.value != cb.value) hit("error", "$at ${ca.value} -> ${cb.value}")
                    CellType.FORMULA -> {
                        // Whitespace is not significant in a formula, and POI re-renders the
                        // text of a shared formula's followers without the author's spacing.
                        if (ca.formula?.filterNot(Char::isWhitespace) != cb.formula?.filterNot(Char::isWhitespace)) hit("formula", "$at '${ca.formula}' -> '${cb.formula}'")
                        if (!ca.rawValue.isNullOrEmpty() && ca.rawValue != cb.rawValue) hit("cached result", "$at '${ca.rawValue}' -> '${cb.rawValue}'")
                    }
                    else -> Unit
                }
                ca.style.zip(cb.style).filter { it.first != it.second }.forEach { (x, y) -> hit("style: ${x.substringBefore('=')}", "$at $x -> $y") }
            }
            for ((ref, cb) in sb.cells) if (ref !in sa.cells && cb.type != CellType.BLANK) hit("cell appeared", "$name!$ref")
            for ((r, ra) in sa.rows) {
                val rb = sb.rows[r]
                if (rb == null) {
                    if (ra != listOf("hidden=false", "height=-", "outline level=0", "style=-")) hit("row dropped", "$name row ${r + 1} $ra")
                    continue
                }
                ra.zip(rb).filter { it.first != it.second }.forEach { (x, y) -> hit("row: ${x.substringBefore('=')}", "$name row ${r + 1}: $x -> $y") }
            }
            sa.columns.zip(sb.columns).forEachIndexed { c, (x, y) ->
                x.zip(y).filter { it.first != it.second }.forEach { (p, q) -> hit("column: ${p.substringBefore('=')}", "$name column ${c + 1}: $p -> $q") }
            }
            for ((k, v) in sa.facts) if (sb.facts[k] != v) hit("sheet: $k", "$name: $v -> ${sb.facts[k]}")
        }
        return found
    }
}

/** Shorthand for the common Kexcel-side step: read, optionally edit, save. */
internal fun roundTrip(bytes: ByteArray, edit: (Excel) -> Unit = {}): ByteArray =
    Excel.decodeBytes(bytes).also(edit).encode()!!

/**
 * Asserts that POI sees no difference between [before] and [after] apart from the kinds
 * named in [expected] — and that every kind in [expected] really does differ, so an entry
 * cannot outlive the defect it was excusing.
 */
internal fun assertOnlyDiffers(before: ByteArray, after: ByteArray, expected: Set<String> = emptySet(), what: String = "workbook") {
    val found = PoiOracle.differences(before, after)
    val unexpected = found.filterKeys { it !in expected }
    if (unexpected.isNotEmpty()) {
        fail("$what changed in ways it should not have:\n" + unexpected.entries.joinToString("\n") { "  ${it.key}: ${it.value}" })
    }
    val stale = expected - found.keys
    if (stale.isNotEmpty()) fail("$what no longer differs in $stale — remove them from the expected set")
}

