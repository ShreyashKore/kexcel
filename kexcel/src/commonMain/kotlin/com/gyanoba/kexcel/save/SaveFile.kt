package com.gyanoba.kexcel.save

import com.fleeksoft.ksoup.nodes.Attribute
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.gyanoba.kexcel.archive.ArchiveFile
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.number_format.CustomNumFormat
import com.gyanoba.kexcel.number_format.DATE_1904_OFFSET_DAYS
import com.gyanoba.kexcel.number_format.DateTimeNumFormat
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.number_format.NumericNumFormat
import com.gyanoba.kexcel.number_format.StandardNumFormat
import com.gyanoba.kexcel.number_format.TimeNumFormat
import com.gyanoba.kexcel.parser.Parser
import com.gyanoba.kexcel.shared_strings.SharedString
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.BorderSet
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.Data
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.Element
import com.gyanoba.kexcel.sheet.FontStyle
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import com.gyanoba.kexcel.utils.FontScheme
import com.gyanoba.kexcel.utils.HorizontalAlign
import com.gyanoba.kexcel.utils.TextWrapping
import com.gyanoba.kexcel.utils.Underline
import com.gyanoba.kexcel.utils.VerticalAlign
import com.gyanoba.kexcel.utils.cloneArchive
import com.gyanoba.kexcel.utils.damagedExcel
import com.gyanoba.kexcel.utils.getCellId
import com.gyanoba.kexcel.utils.WORKBOOK_CHILD_ORDER
import com.gyanoba.kexcel.utils.WORKSHEET_CHILD_ORDER
import com.gyanoba.kexcel.utils.insertInSequence
import com.gyanoba.kexcel.utils.serializeXml
import com.gyanoba.kexcel.utils.setAttribute
import com.gyanoba.kexcel.utils.dropAttribute
import com.gyanoba.kexcel.utils.resolveWorkbookRelTarget
import com.gyanoba.kexcel.utils.toFixed2
import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.truncate

public class Save internal constructor(private val excel: Excel, private val parser: Parser) {

    private val archiveFiles: MutableMap<String, ArchiveFile> = mutableMapOf()
    // First <xf> that says exactly what a given style says; rebuilt at the start of each save.
    private val pristineIndex: MutableMap<CellStyle, Int> = mutableMapOf()
    private var hasFormulas: Boolean = false

    // region --- Column helpers ---

    private fun calcAutoFitColumnWidth(sheet: Sheet, column: Int): Double {
        var maxChars = 0
        sheet.sheetData.forEach { (_, columnMap) ->
            val data = columnMap[column]
            if (data != null && data.value !is FormulaCellValue) {
                maxChars = max(data.value.toString().length, maxChars)
            }
        }
        return truncate((maxChars * 7.0 + 9.0) / 7.0 * 256) / 256.0
    }

    // endregion

    // region --- Cell creation ---

    private fun createCell(
        sheet: String,
        columnIndex: Int,
        rowIndex: Int,
        data: Data,
    ): Element {
        val value = data.value
        // How the cell was stored, when the value is still the object the parser made.
        val source = value?.source
        // A formula's string result whose <f> lives elsewhere — a cell an array or dynamic
        // array formula spills into. It stays `t="str"`; as a shared string it would no
        // longer be part of the array.
        val formulaString = source?.rawValue?.takeIf { value is TextCellValue && source.type == "str" }

        var sharedString: SharedString? = null
        if (value is TextCellValue && formulaString == null) {
            // A string read from a file goes back as the <si> it came from, which keeps run
            // properties the model does not cover. The check catches a span whose styles
            // were mutated in place after parsing; that one is rebuilt from the model.
            val source = value.sourceItem?.takeIf { SharedString(it).textSpan == value.value }
            // Otherwise pass the whole span, not its flattened text, so rich-text runs are kept.
            sharedString = if (source != null) excel.sharedStrings.addFromItem(source)
            else excel.sharedStrings.addFromSpan(value.value)
        }

        // An error literal reads as a formula for want of an error type, but it never had an <f>.
        val isErrorLiteral = value is FormulaCellValue && source != null && !source.hasFormula
        // A cached result is only as good as the cells it was computed from.
        val cached = source?.rawValue?.takeIf { value is FormulaCellValue && !isErrorLiteral && !excel.valuesEdited }

        val rC = getCellId(columnIndex, rowIndex)

        val attributes = mutableListOf(
            Attribute("r", rC),
        )
        when {
            formulaString != null -> attributes.add(Attribute("t", "str"))
            value is TextCellValue -> attributes.add(Attribute("t", "s"))
            value is BoolCellValue -> attributes.add(Attribute("t", "b"))
            isErrorLiteral -> attributes.add(Attribute("t", "e"))
            value is FormulaCellValue -> if (cached != null) source.type?.let { attributes.add(Attribute("t", it)) }
            // `t="n"` spelled out, or an ISO 8601 date (`t="d"`), on an unedited number.
            value != null && source?.rawValue != null -> source.type?.let { attributes.add(Attribute("t", it)) }
        }

        val cellStyle = data.cellStyle

        if (excel.styleChanges && cellStyle != null) {
            val index = styleIndexOf(cellStyle)
            // A cell read without `s` stays without one unless its style was replaced or changed.
            val wasNeverStyled = index == 0 && data.implicitStyle === cellStyle
            if (!wasNeverStyled) attributes.add(1, Attribute("s", "$index"))
        } else if (excel.cellStyleReferenced.containsKey(sheet) &&
            excel.cellStyleReferenced[sheet]!!.containsKey(rC)
        ) {
            attributes.add(1, Attribute("s", "${excel.cellStyleReferenced[sheet]!![rC]}"))
        }

        source?.cellAttributes?.forEach { (key, attributeValue) -> attributes.add(Attribute(key, attributeValue)) }

        fun v(text: String) = Element("v", emptyList(), listOf(TextNode(text)))

        val children: List<Element> = when (value) {
            null -> emptyList()

            is FormulaCellValue -> if (isErrorLiteral) {
                listOf(v(source!!.rawValue ?: value.formula))
            } else {
                hasFormulas = true
                val formulaAttributes = source?.formulaAttributes.orEmpty().map { Attribute(it.key, it.value) }
                listOf(
                    // <f> holds the expression without the `=` that a formula is typed with
                    // (§18.3.1.40); POI refuses to parse one that has it.
                    Element("f", formulaAttributes, listOf(TextNode(value.formula.removePrefix("=")))),
                    v(cached ?: ""),
                )
            }

            // The serial number of a date does not depend on how the cell is formatted, so
            // none of these consult the number format: a duration may be shown as a date
            // and a date as a number (§18.17.4).
            is IntCellValue -> listOf(v(source?.rawValue ?: value.value.toString()))
            is DoubleCellValue -> listOf(v(source?.rawValue ?: value.value.toString()))
            is DateTimeCellValue -> listOf(v(source?.rawValue ?: dateSerial((NumFormat.defaultDateTime as DateTimeNumFormat).writeDateTime(value))))
            is DateCellValue -> listOf(v(source?.rawValue ?: dateSerial((NumFormat.defaultDate as DateTimeNumFormat).writeDate(value))))
            is TimeCellValue -> listOf(v(source?.rawValue ?: NumFormat.defaultTime.let { (it as TimeNumFormat).writeTime(value) }))

            is TextCellValue -> listOf(v(formulaString ?: excel.sharedStrings.indexOf(sharedString!!).toString()))

            is BoolCellValue -> listOf(v(source?.rawValue ?: if (value.value) "1" else "0"))
        }

        return Element("c", attributes, children)
    }

    /** [serial1900] as this workbook stores dates: unchanged, or moved to the 1904 system. */
    private fun dateSerial(serial1900: String): String =
        if (excel.date1904) (serial1900.toDouble() - DATE_1904_OFFSET_DAYS).toString() else serial1900

    /** A style that needs an `<xf>` of its own: what it says, and the `<xf>` it was derived from. */
    private data class StyleKey(val style: CellStyle, val baseXf: Int)

    /**
     * The `<xf>` [style] started out as, or `-1` for a style made from nothing.
     *
     * That is the `<xf>` the parser built the object from — even if the object has been
     * changed since — or the one it was copied from with `copyWith`. The index is only
     * meaningful in the workbook the style came from.
     */
    private fun baseOf(style: CellStyle): Int {
        val own = style.xfIndex
        if (own >= 0 && excel.cellStyleList.getOrNull(own) === style) return own
        return if (style.owner === excel && style.baseXf in excel.cellStylePristine.indices) style.baseXf else -1
    }

    /**
     * The existing `<xf>` that already is [style], or `null` when it needs a new one.
     *
     * A style that still says what its base `<xf>` said goes back under that index. Looking
     * styles up by equality alone — as this used to — returns the first `<xf>` that agrees
     * on the properties [CellStyle] models, and two `<xf>` that differ only in a theme
     * colour, an indent or the locked flag then collapsed into one.
     */
    private fun existingXfFor(style: CellStyle, base: Int): Int? {
        if (base >= 0) return base.takeIf { style == excel.cellStylePristine[base] }
        // A style made in code that matches one already in the file shares it.
        return pristineIndex[style]
    }

    /** The `cellXfs` index to write for [cellStyle]. */
    private fun styleIndexOf(cellStyle: CellStyle): Int {
        val base = baseOf(cellStyle)
        return existingXfFor(cellStyle, base) ?: excel.derivedXf[StyleKey(cellStyle, base)] ?: 0
    }

    /**
     * A `<row>` for [rowIndex], with the attributes it was read with.
     *
     * The model owns the height only. `hidden`, `outlineLevel`, `collapsed`, the row style
     * and the rest are written back as they were — rebuilding the element from the height
     * alone made hidden rows visible and dropped grouping on every save. `spans` is an
     * optimisation hint about the cells in the row, so it is only kept while they are unchanged.
     */
    private fun createNewRow(table: Element, sheetObject: Sheet, rowIndex: Int): Element {
        val stored = sheetObject.rowAttributes[rowIndex].orEmpty()
        val height = sheetObject.getRowHeights[rowIndex]

        val attrs = mutableListOf(Attribute("r", (rowIndex + 1).toString()))
        if (height != null) {
            val storedHeight = stored["ht"]
            if (storedHeight != null && storedHeight.toDoubleOrNull() == height) {
                // Unchanged: the stored text, not a re-rounded copy of it.
                attrs.add(Attribute("ht", storedHeight))
                stored["customHeight"]?.let { attrs.add(Attribute("customHeight", it)) }
            } else {
                attrs.add(Attribute("ht", height.toFixed2()))
                attrs.add(Attribute("customHeight", "1"))
            }
        }
        stored.forEach { (key, value) ->
            if (key == "ht" || key == "customHeight") return@forEach
            if (key == "spans" && excel.valuesEdited) return@forEach
            attrs.add(Attribute(key, value))
        }
        val row = Element("row", attrs, mutableListOf())
        table.appendChild(row)
        return row
    }

    // endregion

    // region --- Styles file ---

    /**
     * Adds an `<xf>` to `xl/styles.xml` for every cell style that is not in it yet, along
     * with whatever fonts, fills and borders those need.
     *
     * A style derived from an existing `<xf>` — with `copyWith`, or by changing the object
     * the parser handed out — starts as a copy of that `<xf>` and has only the parts that
     * were changed replaced. Everything the `<xf>` says that [CellStyle] has no field for
     * (protection, indent, a theme-coloured fill left alone) is carried over.
     */
    private fun processStylesFile() {
        val stylesXml = excel.xmlFiles[excel.stylesPath]!!
        fun table(name: String) = stylesXml.getElementsByTag(name).first()
            ?: damagedExcel("Missing <$name> element in xl/styles.xml")

        val fonts = table("fonts")
        val fills = table("fills")
        val borders = table("borders")
        val cellXfs = table("cellXfs")

        pristineIndex.clear()
        excel.cellStylePristine.forEachIndexed { index, style -> if (style !in pristineIndex) pristineIndex[style] = index }

        val needed = LinkedHashSet<StyleKey>()
        val seen = HashSet<CellStyle>()
        excel.sheetMap.values.forEach { sheetObject ->
            sheetObject.sheetData.values.forEach { columnMap ->
                columnMap.values.forEach { dataObject ->
                    val style = dataObject.cellStyle ?: return@forEach
                    val base = baseOf(style)
                    // Most cells hold one of the parser's own, untouched instances.
                    if (base >= 0 && style === excel.cellStyleList[base] && !seen.add(style)) return@forEach
                    if (existingXfFor(style, base) != null) return@forEach
                    val key = StyleKey(style, base)
                    // The key outlives this save, so it holds a copy the caller cannot change.
                    if (key !in excel.derivedXf) needed.add(StyleKey(style.copyWith(), base))
                }
            }
        }

        val xfElements = cellXfs.children().filter { it.tagName() == "xf" }
        val fontElements = fonts.children().filter { it.tagName() == "font" }
        var xfCount = xfElements.size

        for (key in needed) {
            if (key in excel.derivedXf) continue
            val style = key.style
            val baseStyle = excel.cellStylePristine.getOrNull(key.baseXf)
            val baseXf = if (baseStyle != null) xfElements.getOrNull(key.baseXf) else null
            val xf = baseXf?.clone() ?: Element(
                "xf",
                listOf(
                    Attribute("numFmtId", "0"), Attribute("fontId", "0"), Attribute("fillId", "0"),
                    Attribute("borderId", "0"), Attribute("xfId", "0"),
                ),
            )

            // --- font ---
            val font = fontStyleOf(style)
            val baseFont = baseStyle?.let { fontStyleOf(it) }
            if (baseFont == null || !sameFont(font, baseFont)) {
                var fontId = excel.fontStyleList.indexOfFirst { sameFont(it, font) }
                if (fontId == -1) {
                    val baseFontElement = baseXf?.attr("fontId")?.toIntOrNull()?.let { fontElements.getOrNull(it) }
                    fonts.appendChild(
                        if (baseFontElement != null && baseFont != null) patchedFont(baseFontElement.clone(), font, baseFont)
                        else newFont(font)
                    )
                    fontId = excel.fontStyleList.size
                    excel.fontStyleList.add(font)
                }
                xf.setAttribute("fontId", fontId.toString())
                xf.setAttribute("applyFont", "1")
            }

            // --- fill ---
            val background = style.backgroundColor.colorHex
            if (baseStyle == null || baseStyle.backgroundColor.colorHex != background) {
                var fillId = excel.patternFill.indexOf(background)
                if (fillId == -1) {
                    fills.appendChild(
                        if (background == "none") {
                            Element("fill", emptyList(), listOf(Element("patternFill", listOf(Attribute("patternType", "none")))))
                        } else {
                            // Any ARGB value is a solid fill. Only colours whose alpha was
                            // `FF` used to be written; the others were counted but left
                            // out, which shifted the id of every fill after them.
                            Element(
                                "fill", emptyList(), listOf(
                                    Element(
                                        "patternFill", listOf(Attribute("patternType", "solid")), listOf(
                                            Element("fgColor", listOf(Attribute("rgb", background))),
                                            Element("bgColor", listOf(Attribute("rgb", background))),
                                        )
                                    )
                                )
                            )
                        }
                    )
                    fillId = excel.patternFill.size
                    excel.patternFill.add(background)
                }
                xf.setAttribute("fillId", fillId.toString())
                if (background != "none") xf.setAttribute("applyFill", "1")
            }

            // --- border ---
            val borderSet = createBorderSetFromCellStyle(style)
            if (baseStyle == null || createBorderSetFromCellStyle(baseStyle) != borderSet) {
                var borderId = excel.borderSetList.indexOf(borderSet)
                if (borderId == -1) {
                    borders.appendChild(newBorder(borderSet))
                    borderId = excel.borderSetList.size
                    excel.borderSetList.add(borderSet)
                }
                xf.setAttribute("borderId", borderId.toString())
                // Without applyBorder a reader may ignore the border (POI does).
                if (borderId != 0) xf.setAttribute("applyBorder", "1")
            }

            // --- number format ---
            if (baseStyle == null || baseStyle.numberFormat != style.numberFormat) {
                val numFmtId = when (val nf = style.numberFormat) {
                    is StandardNumFormat -> nf.numFmtId
                    is CustomNumFormat -> excel.numFormats.findOrAdd(nf)
                    else -> 0
                }
                xf.setAttribute("numFmtId", numFmtId.toString())
                if (numFmtId != 0) xf.setAttribute("applyNumberFormat", "1")
            }

            // --- alignment ---
            applyAlignment(xf, style, baseStyle)

            cellXfs.appendChild(xf)
            val index = xfCount++
            excel.derivedXf[key] = index
            // Keep the two lists parallel to <cellXfs>, as the parser leaves them.
            excel.cellStylePristine.add(style)
            excel.cellStyleList.add(style.copyWith().also { it.xfIndex = index; it.owner = excel })
            if (style !in pristineIndex) pristineIndex[style] = index
        }

        // `count` is the number of entries actually in each table.
        for ((element, entry) in listOf(fonts to "font", fills to "fill", borders to "border", cellXfs to "xf")) {
            element.setAttribute("count", element.children().count { it.tagName() == entry }.toString())
        }

        // --- custom numFmts ---
        // Only custom formats are written back; standard (built-in) formats live
        // implicitly in the reader and must not be emitted as <numFmt> entries.
        val customNumberFormats = excel.numFormats.map
            .entries
            .filter { it.value is CustomNumFormat }
            .sortedBy { it.key }

        if (customNumberFormats.isNotEmpty()) {
            var numFmtsElement = stylesXml.getElementsByTag("numFmts")
                .filterIsInstance<Element>()
                .firstOrNull()

            if (numFmtsElement == null) {
                numFmtsElement = Element("numFmts", mutableListOf(), mutableListOf())
                stylesXml.getElementsByTag("styleSheet").first()?.prependChildren(listOf(numFmtsElement))
            }

            var count = numFmtsElement.attribute("count")?.value?.toIntOrNull() ?: 0

            customNumberFormats.forEach { (numFmtId, format) ->
                val numFmtIdString = numFmtId.toString()
                val formatCode = format.formatCode
                val existing = numFmtsElement!!.children()
                    .filterIsInstance<Element>()
                    .firstOrNull { it.normalName() == "numfmt" && it.attribute("numFmtId")?.value == numFmtIdString }

                if (existing == null) {
                    numFmtsElement.appendChild(
                        Element(
                            "numFmt",
                            listOf(
                                Attribute("numFmtId", numFmtIdString),
                                Attribute("formatCode", formatCode),
                            )
                        )
                    )
                    count++
                } else if ((existing.attribute("formatCode")?.value ?: "") != formatCode) {
                    existing.attr("formatCode", formatCode)
                }
            }
            numFmtsElement.attr("count", count.toString())
        }
    }

    // endregion

    // region --- Save ---

    internal fun save(): ByteArray? {
        if (excel.styleChanges) processStylesFile()
        setSheetElements()
        if (excel.valuesEdited) invalidateCalculationState()
        if (excel.defaultSheet != null) setDefaultSheet(excel.defaultSheet)
        setSharedStrings()
        if (excel.mergeChanges) setMerge()
        if (excel.rtlChanges) setRTL()

        for (xmlFile in excel.xmlFiles.keys) {
            val xml = serializeXml(excel.xmlFiles[xmlFile]!!)
            val content = xml.encodeToByteArray()
            archiveFiles[xmlFile] = ArchiveFile(xmlFile, content.size, content)
        }

        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zos ->
            val merged = cloneArchive(excel.archive, archiveFiles)
            merged.files.forEach { file ->
                zos.putNextEntry(ZipEntry(file.name))
                zos.write(file.content)
                zos.closeEntry()
            }
        }
        return buf.toByteArray()
    }

    // endregion

    // region --- Calculation state ---

    /**
     * Called once a value has changed since the workbook was loaded.
     *
     * Two things in the package describe the *previous* calculation. `xl/calcChain.xml`
     * lists the formula cells in evaluation order; an entry for a cell that is no longer a
     * formula makes Excel offer to repair the file, and the part is optional, so it is
     * removed (POI drops entries the same way, `XSSFWorkbook.onDeleteFormula`). And the
     * cached results are no longer trustworthy, so they are not written (see `createCell`)
     * and `fullCalcOnLoad` (§18.2.2) asks the next application to recalculate.
     */
    private fun invalidateCalculationState() {
        val relationships = excel.xmlFiles["xl/_rels/workbook.xml.rels"]
        relationships?.getElementsByTag("Relationship")
            ?.filter { it.attr("Type").endsWith("/calcChain") }
            ?.forEach { relationship ->
                val path = resolveWorkbookRelTarget(relationship.attr("Target"))
                relationship.remove()
                excel.xmlFiles["[Content_Types].xml"]?.getElementsByTag("Override")
                    ?.filter { it.attr("PartName") == "/$path" }
                    ?.forEach { it.remove() }
                excel.archive = cloneArchive(excel.archive, emptyMap(), excludedFile = path)
            }

        if (!hasFormulas) return
        val workbook = excel.xmlFiles["xl/workbook.xml"]?.getElementsByTag("workbook")?.first() ?: return
        val calcPr = workbook.children().firstOrNull { it.tagName() == "calcPr" }
            ?: Element("calcPr").also { insertInSequence(workbook, it, WORKBOOK_CHILD_ORDER) }
        calcPr.setAttribute("fullCalcOnLoad", "1")
    }

    // endregion

    // region --- Sheet writing ---

    /**
     * Writes `<cols>` (§18.3.1.17).
     *
     * While no width was changed the element is left exactly as it was read. Once one was,
     * it is rebuilt from the `<col>` ranges the sheet came with: a column whose width now
     * differs is split out of its range, and every other attribute — `hidden`, the column
     * style, `outlineLevel` — stays where it was. Rebuilding the element from the widths
     * alone, as this used to, made hidden columns visible, dropped column styles and gave
     * every column up to the last one an explicit width.
     */
    private fun setColumns(sheetObject: Sheet, xmlFile: Document) {
        if (!sheetObject.columnsDirty) return

        val customWidths = sheetObject.getColumnWidths
        val autoFits = sheetObject.getColumnAutoFits

        val specs = sheetObject.columnSpecs.sortedBy { it.min }.toMutableList()
        for (columnIndex in (customWidths.keys + autoFits.keys).toSortedSet()) {
            val autoFit = autoFits[columnIndex] == true && !customWidths.containsKey(columnIndex)
            val width = if (autoFit) calcAutoFitColumnWidth(sheetObject, columnIndex) else customWidths[columnIndex] ?: continue

            val position = specs.indexOfFirst { columnIndex in it.min..it.max }
            val existing = specs.getOrNull(position)
            if (existing != null && !autoFit && existing.attributes["width"]?.toDoubleOrNull() == width) continue

            val attributes = existing?.attributes.orEmpty() +
                mapOf("width" to width.toFixed2(), "bestFit" to "1", "customWidth" to "1")
            val replacement = buildList {
                if (existing != null && existing.min < columnIndex) add(Sheet.ColumnSpec(existing.min, columnIndex - 1, existing.attributes))
                add(Sheet.ColumnSpec(columnIndex, columnIndex, attributes))
                if (existing != null && existing.max > columnIndex) add(Sheet.ColumnSpec(columnIndex + 1, existing.max, existing.attributes))
            }
            if (existing != null) {
                specs.removeAt(position)
                specs.addAll(position, replacement)
            } else {
                specs.addAll(replacement)
                specs.sortBy { it.min }
            }
        }

        val columnElements = xmlFile.getElementsByTag("cols").toList()
        if (specs.isEmpty()) {
            columnElements.firstOrNull()?.remove()
            return
        }

        if (columnElements.isEmpty()) {
            val worksheet = xmlFile.getElementsByTag("worksheet").first()
                ?: damagedExcel("Missing <worksheet> element in sheet XML")
            insertInSequence(worksheet, Element("cols"), WORKSHEET_CHILD_ORDER)
        }

        val columns = xmlFile.getElementsByTag("cols").first()
            ?: damagedExcel("Missing <cols> element in sheet XML")
        columns.empty()
        specs.forEach { spec ->
            columns.appendChild(
                Element(
                    "col",
                    listOf(Attribute("min", (spec.min + 1).toString()), Attribute("max", (spec.max + 1).toString())) +
                        spec.attributes.map { Attribute(it.key, it.value) },
                )
            )
        }
    }

    private fun setRows(sheetName: String, sheetObject: Sheet) {
        // A row is written when it holds cells, and also when it only has a height, a
        // style or a flag such as `hidden`: those are properties of the row itself.
        val rowIndexes = (
            sheetObject.sheetData.keys + sheetObject.getRowHeights.keys +
                sheetObject.rowAttributes.keys + sheetObject.mergedCells.keys
            ).toSortedSet()

        for (rowIndex in rowIndexes) {
            val cells = sheetObject.sheetData[rowIndex].orEmpty()
            // The cells under a merged region, which the model leaves out.
            val coveredByMerge = sheetObject.mergedCells[rowIndex].orEmpty()

            val foundRow = createNewRow(excel.sheets[sheetName]!! as Element, sheetObject, rowIndex)
            for (columnIndex in (cells.keys + coveredByMerge.keys).toSortedSet()) {
                val data = cells[columnIndex] ?: coveredByMerge.getValue(columnIndex)
                updateCell(sheetName, foundRow, columnIndex, rowIndex, data)
            }
        }
    }

    /**
     * Brings `<dimension>` (§18.3.1.35) up to date once cells have changed. It is the used
     * range of the sheet, and readers that trust it — openpyxl in read-only mode, streaming
     * readers — stop at its edge. A new sheet was always written with `A1`.
     */
    private fun setDimension(sheetObject: Sheet, xmlFile: Document) {
        if (!excel.valuesEdited) return
        val dimension = xmlFile.getElementsByTag("dimension").firstOrNull() ?: return

        val rows = sheetObject.sheetData.filterValues { it.isNotEmpty() }
        val ref = if (rows.isEmpty()) "A1" else {
            val firstRow = rows.keys.min()
            val lastRow = rows.keys.max()
            val firstColumn = rows.values.minOf { it.keys.min() }
            val lastColumn = rows.values.maxOf { it.keys.max() }
            val topLeft = getCellId(firstColumn, firstRow)
            val bottomRight = getCellId(lastColumn, lastRow)
            if (topLeft == bottomRight) topLeft else "$topLeft:$bottomRight"
        }
        dimension.attr("ref", ref)
    }

    private fun setDefaultSheet(sheetName: String?): Boolean {
        if (sheetName == null || excel.xmlFiles["xl/workbook.xml"] == null) return false
        val sheetList = excel.xmlFiles["xl/workbook.xml"]!!.getElementsByTag("sheet").toList()

        var elementFound: Element? = null
        var position = -1
        for (i in sheetList.indices) {
            if (sheetList[i].attribute("name")?.value.toString() == sheetName) {
                elementFound = sheetList[i]
                position = i
                break
            }
        }

        if (position == -1 || elementFound == null) return false

        selectOnly(sheetName)
        if (position == 0) return true

        val sheets = excel.xmlFiles["xl/workbook.xml"]!!.getElementsByTag("sheets").first()
        elementFound.remove()
        sheets?.prependChildren(listOf(elementFound))

        // A defined name scoped to a sheet names it by position (`localSheetId`, §18.2.5).
        // The sheet at `position` is now first and the ones before it moved down by one.
        excel.xmlFiles["xl/workbook.xml"]!!.getElementsByTag("definedName").forEach { definedName ->
            val scope = definedName.attr("localSheetId").trim().toIntOrNull() ?: return@forEach
            val moved = when {
                scope == position -> 0
                scope < position -> scope + 1
                else -> scope
            }
            definedName.setAttribute("localSheetId", moved.toString())
        }

        return excel.getDefaultSheetInternal() == sheetName
    }

    /**
     * Makes [sheetName] the only selected sheet once it has been moved to the front.
     *
     * `<sheetView>` is patched in place rather than rebuilt (see [setRTL]), so the
     * `tabSelected` of whichever sheet was selected in the source file survives. Left
     * alone, Excel opens with that sheet *and* the new default grouped, and every edit
     * lands on both. `activeTab` is reset for the same reason: it is a position, and the
     * default sheet is now at position 0.
     */
    private fun selectOnly(sheetName: String) {
        excel.xmlFiles["xl/workbook.xml"]!!.getElementsByTag("workbookView").forEach {
            it.removeAttr("activeTab")
        }
        excel.sheetMap.keys.forEach { name ->
            val sheetView = excel.xmlFiles[excel.xmlSheetId[name]]
                ?.getElementsByTag("sheetView")?.firstOrNull() ?: return@forEach
            if (name == sheetName) sheetView.attr("tabSelected", "1")
            else sheetView.removeAttr("tabSelected")
        }
    }

    private fun setHeaderFooter(sheetName: String) {
        val sheet = excel.sheetMap[sheetName] ?: return
        val xmlFile = excel.xmlFiles[excel.xmlSheetId[sheetName]] ?: return
        val worksheetEl = xmlFile.getElementsByTag("worksheet").first()
            ?: damagedExcel("Missing <worksheet> element in sheet XML")

        worksheetEl.getElementsByTag("headerFooter").toList().forEach { it.remove() }

        val headerFooterEl = sheet.headerFooter?.toXmlElement() ?: return

        // CT_Worksheet is a *sequence*: <headerFooter> has to precede <rowBreaks>,
        // <colBreaks>, <drawing> and everything after them. Appending it at the end put it
        // after <drawing> — which the template always carries — and Excel rejects a
        // worksheet whose children are out of order.
        val followers = setOf(
            "rowBreaks", "colBreaks", "customProperties", "cellWatches", "ignoredErrors",
            "smartTags", "drawing", "legacyDrawing", "legacyDrawingHF", "drawingHF", "picture",
            "oleObjects", "controls", "webPublishItems", "tableParts", "extLst",
        )
        val firstFollower = worksheetEl.children().firstOrNull { it.tagName() in followers }
        if (firstFollower != null) firstFollower.before(headerFooterEl)
        else worksheetEl.appendChild(headerFooterEl)
    }

    private fun setMerge() {
        selfCorrectSpanMap(excel)
        excel.mergeChangeLook.forEach { s ->
            val sheetObj = excel.sheetMap[s] ?: return@forEach
            val xmlSheetPath = excel.xmlSheetId[s] ?: return@forEach
            val xmlDoc = excel.xmlFiles[xmlSheetPath] ?: return@forEach

            val spannedItems = sheetObj.spannedItems.toList()
            val iterMerge = xmlDoc.getElementsByTag("mergeCells").toList()

            if (spannedItems.isEmpty()) {
                // The last merge was removed: drop the part entirely rather than leaving the
                // stale refs behind, which would resurrect the merges on the next read.
                iterMerge.forEach { it.remove() }
                return@forEach
            }

            val mergeElement: Element = if (iterMerge.isNotEmpty()) {
                iterMerge.first()
            } else {
                val worksheetEl = xmlDoc.getElementsByTag("worksheet").firstOrNull()
                    ?: damagedExcel()
                val sheetDataEl = xmlDoc.getElementsByTag("sheetData").firstOrNull()
                    ?: damagedExcel()
                val index = worksheetEl.children().indexOf(sheetDataEl)
                if (index == -1) damagedExcel()
                val newMerge = Element("mergeCells", listOf(Attribute("count", "0")))
                // After <sheetData>, but also after sheetProtection, autoFilter and the
                // other elements the sequence puts between the two.
                insertInSequence(worksheetEl, newMerge, WORKSHEET_CHILD_ORDER)
                newMerge
            }

            mergeElement.attr("count", spannedItems.size.toString())
            // Older versions of this library wrote a bogus `value` attribute here (the count was
            // set through `Element.value()`, which sets `value`); drop it so the part validates.
            mergeElement.removeAttr("value")

            mergeElement.empty()
            spannedItems.forEach { ref ->
                mergeElement.appendChild(
                    Element("mergeCell", listOf(Attribute("ref", ref)))
                )
            }
        }
    }

    private fun setRTL() {
        excel.rtlChangeLook.forEach { s ->
            val sheetObject = excel.sheetMap[s] ?: return@forEach
            val xmlSheetPath = excel.xmlSheetId[s] ?: return@forEach
            val xmlDoc = excel.xmlFiles[xmlSheetPath] ?: return@forEach

            // `rightToLeft` is the only thing the model owns here, so patch it onto the
            // existing <sheetView> instead of replacing the element. Rebuilding it would
            // discard everything unmodelled on every save: <pane> (frozen/split panes),
            // <selection>, tabSelected, zoomScale, showGridLines, view="pageLayout", … .
            // Every parsed sheet lands in `rtlChangeLook` (the parser always assigns
            // `isRTL`), so this path runs for ordinary saves, not just RTL ones.
            val existingSheetView = xmlDoc.getElementsByTag("sheetView").firstOrNull()
            if (existingSheetView != null) {
                if (sheetObject.isRTL) existingSheetView.attr("rightToLeft", "1")
                else existingSheetView.removeAttr("rightToLeft")
                if (!existingSheetView.hasAttr("workbookViewId")) {
                    existingSheetView.attr("workbookViewId", "0")
                }
                return@forEach
            }

            val sheetViewsIter = xmlDoc.getElementsByTag("sheetViews").toList()
            val sheetViewEl = Element(
                "sheetView",
                buildList {
                    if (sheetObject.isRTL) add(Attribute("rightToLeft", "1"))
                    add(Attribute("workbookViewId", "0"))
                }
            )

            if (sheetViewsIter.isNotEmpty()) {
                sheetViewsIter.first().appendChild(sheetViewEl)
            } else {
                val worksheetEl = xmlDoc.getElementsByTag("worksheet").first()
                    ?: damagedExcel("Missing <worksheet> element in sheet XML")
                insertInSequence(
                    worksheetEl,
                    Element("sheetViews", emptyList(), mutableListOf(sheetViewEl)),
                    WORKSHEET_CHILD_ORDER,
                )
            }
        }
    }

    private fun setSharedStrings() {
        var uniqueCount = 0
        var count = 0

        val shareString = excel.xmlFiles[excel.sharedStringsPath]!!
            .getElementsByTag("sst").first()

        shareString?.empty()

        excel.sharedStrings.map.forEach { (sharedString, indexingHolder) ->
            uniqueCount++
            count += indexingHolder.count
            shareString?.appendChild(sharedString.node)
        }

        listOf("count" to "$count", "uniqueCount" to "$uniqueCount").forEach { (key, value) ->
            val attr = shareString?.attribute(key)
            if (attr == null) shareString?.attributes()?.add(key, value)
            else attr.setValue(value)
        }
    }

    private fun setSheetElements() {
        excel.sharedStrings.clear()
        hasFormulas = false

        excel.sheetMap.forEach { (sheetName, sheetObject) ->
            if (excel.sheets[sheetName] == null) {
                parser.createSheet(sheetName)
            }

            excel.sheets[sheetName]?.empty()

            val xmlFile = excel.xmlFiles[excel.xmlSheetId[sheetName]] ?: return@forEach

            val defaultRowHeight = sheetObject.defaultRowHeight
            val defaultColumnWidth = sheetObject.defaultColumnWidth

            val worksheetEl = xmlFile.getElementsByTag("worksheet").first()
            var sheetFormatPrEl = worksheetEl?.children()
                ?.firstOrNull { it.nodeName() == "sheetFormatPr" }

            if (sheetFormatPrEl == null && worksheetEl != null && (defaultRowHeight != null || defaultColumnWidth != null)) {
                sheetFormatPrEl = Element("sheetFormatPr")
                insertInSequence(worksheetEl, sheetFormatPrEl, WORKSHEET_CHILD_ORDER)
            }

            // Only the two attributes the model owns are rewritten here. Everything else the
            // producing application put on <sheetFormatPr> (baseColWidth, outlineLevelRow,
            // zeroHeight, x14ac:dyDescent, …) is unmodelled and must survive a round trip.
            sheetFormatPrEl?.let { el ->
                // An unchanged size keeps its stored text; rounding it to two decimals on
                // every save moved widths that Excel keeps in 1/256 steps.
                if (defaultRowHeight == null) el.removeAttr("defaultRowHeight")
                else if (el.attr("defaultRowHeight").toDoubleOrNull() != defaultRowHeight) el.attr("defaultRowHeight", defaultRowHeight.toFixed2())

                if (defaultColumnWidth == null) el.removeAttr("defaultColWidth")
                else if (el.attr("defaultColWidth").toDoubleOrNull() != defaultColumnWidth) el.attr("defaultColWidth", defaultColumnWidth.toFixed2())

                // Nothing left to say — an attribute-less <sheetFormatPr> carries no information.
                if (el.attributesSize() == 0) el.remove()
            }

            setColumns(sheetObject, xmlFile)
            setRows(sheetName, sheetObject)
            setDimension(sheetObject, xmlFile)
            setHeaderFooter(sheetName)
        }
    }

    // endregion

    // region --- Cell update ---

    private fun updateCell(
        sheet: String,
        row: Element,
        columnIndex: Int,
        rowIndex: Int,
        data: Data,
    ): Element {
        val cell = createCell(sheet, columnIndex, rowIndex, data)
        row.appendChild(cell)
        return cell
    }

    // endregion

    // region --- Border helper ---

    private fun createBorderSetFromCellStyle(cellStyle: CellStyle): BorderSet = BorderSet(
        leftBorder = cellStyle.leftBorder,
        rightBorder = cellStyle.rightBorder,
        topBorder = cellStyle.topBorder,
        bottomBorder = cellStyle.bottomBorder,
        diagonalBorder = cellStyle.diagonalBorder,
        diagonalBorderUp = cellStyle.diagonalBorderUp,
        diagonalBorderDown = cellStyle.diagonalBorderDown,
    )

    private fun newBorder(bs: BorderSet): Element {
        val borderElement = Element("border", mutableListOf(), mutableListOf())
        if (bs.diagonalBorderDown) borderElement.attributes().add("diagonalDown", "1")
        if (bs.diagonalBorderUp) borderElement.attributes().add("diagonalUp", "1")

        linkedMapOf(
            "left" to bs.leftBorder,
            "right" to bs.rightBorder,
            "top" to bs.topBorder,
            "bottom" to bs.bottomBorder,
            "diagonal" to bs.diagonalBorder,
        ).forEach { (key, borderValue) ->
            val element = Element(key, mutableListOf(), mutableListOf())
            borderValue.borderStyle?.let { element.attributes().add("style", it.style) }
            borderValue.borderColorHex?.let { element.appendChild(Element("color", listOf(Attribute("rgb", it)))) }
            borderElement.appendChild(element)
        }
        return borderElement
    }

    // endregion

    // region --- Font and alignment helpers ---

    private fun fontStyleOf(style: CellStyle): FontStyle = FontStyle(
        isBold = style.isBold,
        isItalic = style.isItalic,
        fontColorHex = style.fontColor,
        underline = style.underline,
        fontSize = style.fontSize,
        fontFamily = style.fontFamily,
        fontScheme = style.fontScheme,
    )

    /** [FontStyle] equality leaves the scheme out; two fonts are only the same font with it. */
    private fun sameFont(a: FontStyle, b: FontStyle): Boolean = a == b && a.fontScheme == b.fontScheme

    private fun newFont(fs: FontStyle): Element {
        val fontChildren = mutableListOf<Element>()

        if (fs.fontColorHex.colorHex != "FF000000") {
            fontChildren.add(Element("color", listOf(Attribute("rgb", fs.fontColorHex.colorHex))))
        }
        if (fs.isBold) fontChildren.add(Element("b"))
        if (fs.isItalic) fontChildren.add(Element("i"))
        when (fs.underline) {
            Underline.Single -> fontChildren.add(Element("u"))
            Underline.Double -> fontChildren.add(Element("u", listOf(Attribute("val", "double"))))
            Underline.None -> Unit
        }
        val ff = fs.fontFamily
        if (!ff.isNullOrEmpty() && ff.lowercase() != "null") {
            fontChildren.add(Element("name", listOf(Attribute("val", ff))))
        }
        if (fs.fontScheme != FontScheme.Unset) {
            val schemeVal = if (fs.fontScheme == FontScheme.Major) "major" else "minor"
            fontChildren.add(Element("scheme", listOf(Attribute("val", schemeVal))))
        }
        val fSize = fs.fontSize
        if (fSize != null) {
            fontChildren.add(Element("sz", listOf(Attribute("val", fSize.toString()))))
        }
        return Element("font", emptyList(), fontChildren)
    }

    /**
     * [font], a copy of an existing `<font>`, with the properties that differ between
     * [wanted] and [was] replaced. What the model does not cover — strike-through, charset,
     * a theme colour that was left alone — stays as it is.
     */
    private fun patchedFont(font: Element, wanted: FontStyle, was: FontStyle): Element {
        fun replace(tag: String, replacement: Element?) {
            font.children().filter { it.tagName() == tag }.forEach { it.remove() }
            if (replacement != null) font.appendChild(replacement)
        }
        if (wanted.isBold != was.isBold) replace("b", if (wanted.isBold) Element("b") else null)
        if (wanted.isItalic != was.isItalic) replace("i", if (wanted.isItalic) Element("i") else null)
        if (wanted.underline != was.underline) replace(
            "u",
            when (wanted.underline) {
                Underline.Single -> Element("u")
                Underline.Double -> Element("u", listOf(Attribute("val", "double")))
                Underline.None -> null
            },
        )
        if (wanted.fontSize != was.fontSize) replace("sz", wanted.fontSize?.let { Element("sz", listOf(Attribute("val", it.toString()))) })
        if (wanted.fontColorHex != was.fontColorHex) replace("color", Element("color", listOf(Attribute("rgb", wanted.fontColorHex.colorHex))))
        if (wanted.fontFamily != was.fontFamily) replace("name", wanted.fontFamily?.let { Element("name", listOf(Attribute("val", it))) })
        if (wanted.fontScheme != was.fontScheme) replace(
            "scheme",
            if (wanted.fontScheme == FontScheme.Unset) null
            else Element("scheme", listOf(Attribute("val", if (wanted.fontScheme == FontScheme.Major) "major" else "minor"))),
        )
        return font
    }

    /**
     * Writes the alignment of [style] onto [xf]. For a derived style only the attributes
     * whose modelled value changed are touched, so `indent`, `readingOrder` or a
     * `horizontal="justify"` that reads as [HorizontalAlign.Left] survive.
     */
    private fun applyAlignment(xf: Element, style: CellStyle, baseStyle: CellStyle?) {
        val changedHorizontal = baseStyle == null || baseStyle.horizontalAlignment != style.horizontalAlignment
        val changedVertical = baseStyle == null || baseStyle.verticalAlignment != style.verticalAlignment
        val changedWrap = baseStyle == null || baseStyle.wrap != style.wrap
        val changedRotation = baseStyle == null || baseStyle.rotation != style.rotation
        if (!changedHorizontal && !changedVertical && !changedWrap && !changedRotation) return

        val alignment = xf.children().firstOrNull { it.tagName() == "alignment" } ?: Element("alignment")
        if (changedWrap) {
            alignment.dropAttribute("wrapText")
            alignment.dropAttribute("shrinkToFit")
            style.wrap?.let { alignment.attr(if (it == TextWrapping.Clip) "shrinkToFit" else "wrapText", "1") }
        }
        if (changedVertical) when (style.verticalAlignment) {
            VerticalAlign.Bottom -> alignment.dropAttribute("vertical")
            VerticalAlign.Top -> alignment.setAttribute("vertical", "top")
            VerticalAlign.Center -> alignment.setAttribute("vertical", "center")
        }
        if (changedHorizontal) when (style.horizontalAlignment) {
            HorizontalAlign.Left -> alignment.dropAttribute("horizontal")
            HorizontalAlign.Right -> alignment.setAttribute("horizontal", "right")
            HorizontalAlign.Center -> alignment.setAttribute("horizontal", "center")
        }
        if (changedRotation) {
            if (style.rotation == 0) alignment.dropAttribute("textRotation")
            else alignment.setAttribute("textRotation", style.rotation.toString())
        }

        if (alignment.attributesSize() == 0) {
            alignment.remove()
            xf.dropAttribute("applyAlignment")
        } else {
            if (alignment.parent() == null) xf.appendChild(alignment)
            xf.setAttribute("applyAlignment", "1")
        }
    }

    // endregion
}
