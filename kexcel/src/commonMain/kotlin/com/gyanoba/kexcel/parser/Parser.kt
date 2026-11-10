package com.gyanoba.kexcel.parser

import com.fleeksoft.ksoup.nodes.Attribute
import com.fleeksoft.ksoup.nodes.Element
import com.gyanoba.kexcel.archive.ArchiveFile
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.number_format.CustomNumFormat
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.shared_strings.SharedString
import com.gyanoba.kexcel.shared_strings.itemKey
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderSet
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellSource
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.CellValue
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import com.gyanoba.kexcel.sheet.Element
import com.gyanoba.kexcel.sheet.FontStyle
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.HeaderFooter
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.getBorderStyleByName
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.FontScheme
import com.gyanoba.kexcel.utils.HorizontalAlign
import com.gyanoba.kexcel.utils.TextWrapping
import com.gyanoba.kexcel.utils.Underline
import com.gyanoba.kexcel.utils.VerticalAlign
import com.gyanoba.kexcel.utils.damagedExcel
import com.gyanoba.kexcel.utils.findCells
import com.gyanoba.kexcel.utils.findRows
import com.gyanoba.kexcel.utils.*
import com.gyanoba.kexcel.utils.getCellNumber
import com.gyanoba.kexcel.utils.getRowNumber
import com.gyanoba.kexcel.utils.normalizeNewLine
import com.gyanoba.kexcel.utils.toExcelColor

public class Parser internal constructor(private val excel: Excel) {

    private val rId: MutableList<String> = mutableListOf()
    // rId -> archive entry name of the worksheet part, e.g. `xl/worksheets/sheet1.xml`.
    private val worksheetPaths: MutableMap<String, String> = mutableMapOf()
    // rId -> relationship type, for every relationship of the workbook part.
    private val relationshipTypes: MutableMap<String, String> = mutableMapOf()

    internal fun startParsing() {
        putContentXml()
        parseRelations()
        parseStyles(excel.stylesPath)
        parseSharedStrings()
        parseContent()
        parseMergedCells()
        excel.valuesEdited = false
    }

    private fun normalizeTable(sheet: Sheet) {
        if (sheet.maxRows == 0 || sheet.maxColumns == 0) {
            sheet.sheetData.clear()
        }
        sheet.countRowsAndColumns()
    }

    private fun putContentXml() {
        val file = excel.archive.findFile("[Content_Types].xml") ?: damagedExcel()
        file.decompress()
        excel.xmlFiles["[Content_Types].xml"] = parseXmlPart(file.content.decodeToString())
    }

    private fun parseRelations() {
        val relations = excel.archive.findFile("xl/_rels/workbook.xml.rels")
            ?: damagedExcel()

        relations.decompress()
        val document = parseXmlPart(relations.content.decodeToString())
        excel.xmlFiles["xl/_rels/workbook.xml.rels"] = document

        document.getElementsByTag("Relationship").forEach { node ->
            val id = node.attr("Id")
            val target = node.attr("Target")
            if (id != null) relationshipTypes[id] = node.attr("Type")
            if (target != null) {
                when (node.attr("Type")) {
                    RELATIONSHIPS_STYLES -> excel.stylesPath = resolveWorkbookRelTarget(target)
                    RELATIONSHIPS_WORKSHEET ->
                        if (id != null) worksheetPaths[id] = resolveWorkbookRelTarget(target)
                    RELATIONSHIPS_SHARED_STRINGS -> excel.sharedStringsPath = resolveWorkbookRelTarget(target)
                }
            }
            if (id != null && !rId.contains(id)) {
                rId.add(id)
            }
        }
    }

    private fun parseSharedStrings() {
        var sharedStrings = excel.archive.findFile(excel.sharedStringsPath)

        if (sharedStrings == null) {
            excel.sharedStringsPath = "xl/sharedStrings.xml"

            // Running with run=false collects all rIds to find an available one
            // to assign to sharedStrings.xml
            parseContent(run = false)

            if (excel.xmlFiles.containsKey("xl/_rels/workbook.xml.rels")) {
                val rIdNumber = getAvailableRid()

                excel.xmlFiles["xl/_rels/workbook.xml.rels"]
                    ?.getElementsByTag("Relationships")
                    ?.firstOrNull()
                    ?.appendChild(
                        Element(
                            "Relationship",
                            listOf(
                                Attribute("Id", "rId$rIdNumber"),
                                Attribute(
                                    "Type",
                                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings"
                                ),
                                Attribute("Target", "sharedStrings.xml")
                            )
                        )
                    )

                if (!rId.contains("rId$rIdNumber")) {
                    rId.add("rId$rIdNumber")
                }

                val contentType =
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"
                var alreadyContains = false

                excel.xmlFiles["[Content_Types].xml"]
                    ?.getElementsByTag("Override")
                    ?.forEach { node ->
                        if (node.attr("ContentType") == contentType) {
                            alreadyContains = true
                        }
                    }

                if (!alreadyContains) {
                    excel.xmlFiles["[Content_Types].xml"]
                        ?.getElementsByTag("Types")
                        ?.firstOrNull()
                        ?.appendChild(
                            Element(
                                "Override",
                                listOf(
                                    Attribute("PartName", "/xl/sharedStrings.xml"),
                                    Attribute("ContentType", contentType)
                                )
                            )
                        )
                }
            }

            val emptySharedStrings =
                "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"0\" uniqueCount=\"0\"/>"
                    .encodeToByteArray()
            excel.archive.addFile(
                ArchiveFile("xl/sharedStrings.xml", emptySharedStrings.size, emptySharedStrings)
            )
            sharedStrings = excel.archive.findFile("xl/sharedStrings.xml")
        }

        sharedStrings!!.decompress()
        val document = parseXmlPart(sharedStrings.content.decodeToString())
        excel.xmlFiles[excel.sharedStringsPath] = document

        document.getElementsByTag("si").forEach { node ->
            parseSharedString(node)
        }
    }

    private fun parseSharedString(node: Element) {
        val sharedString = SharedString(node = node)
        // Keyed by the node itself, matching how the save path registers entries — plain
        // text alone cannot tell two differently styled rich strings apart.
        excel.sharedStrings.addParsed(sharedString, itemKey(node))
    }

    private fun parseContent(run: Boolean = true) {
        val workbook = excel.archive.findFile("xl/workbook.xml") ?: damagedExcel()
        workbook.decompress()
        val document = parseXmlPart(workbook.content.decodeToString())
        excel.xmlFiles["xl/workbook.xml"] = document

        val sheetNodes = document.getElementsByTag("sheet")
        // A workbook always has at least one sheet (§18.2.20). Finding none means the part
        // was not understood, and saving such a model would write an empty workbook over
        // the real one.
        if (run && sheetNodes.isEmpty()) damagedExcel(text = "xl/workbook.xml lists no sheets")

        sheetNodes.forEach { node ->
            if (run) {
                // <sheets> also lists chart sheets, macro sheets and dialog sheets
                // (§18.2.19). Their parts are not worksheets and have no cells; they stay
                // in the package as they are and simply have no Sheet in the model.
                // An entry with no relationship id at all (POI corpus 60825.xlsx) names no
                // part; POI skips it too. An id whose part is missing is still damage.
                val id = relationshipId(node) ?: return@forEach
                val type = relationshipTypes[id]
                if (type != null && type != RELATIONSHIPS_WORKSHEET) return@forEach
                parseTable(node)
            } else {
                val rid = relationshipId(node)
                if (rid != null && !rId.contains(rid)) {
                    rId.add(rid)
                }
            }
        }
    }

    /**
     * Parses and processes merged cells within the spreadsheet.
     *
     * Identifies merged cell regions in each sheet and removes all cells within a merged
     * region except for the top-left cell, preserving its content.
     */
    private fun parseMergedCells() {
        excel.sheets.forEach { (sheetName, node) ->
            excel.availSheet(sheetName)
            val sheetDataNode = node as Element
            val sheet = excel.sheetMap[sheetName]!!

            // Membership is checked in a set: against the list it was a linear scan per
            // merge, and with it a rebuild of the sheet's reference list, which made a
            // sheet with 50,000 merges (POI corpus 57893-many-merges.xlsx) take minutes.
            val known = HashSet<Span>(sheet.spanList.filterNotNull())

            val worksheetNode = sheetDataNode.parent()
            worksheetNode?.getElementsByTag("mergeCell")?.forEach { element ->
                val corners = element.attr("ref").split(':')
                if (corners.size == 2) {
                    val spanObj = Span.fromCellIndex(
                        start = CellIndex.indexByString(corners[0]),
                        end = CellIndex.indexByString(corners[1]),
                    )
                    if (known.add(spanObj)) {
                        sheet.spanList.add(spanObj)
                        deleteAllButTopLeftCellsOfSpanObj(spanObj, sheet)
                    }
                    excel.addMergeChangeLookup(sheetName)
                }
            }
            // Derives the sheet's list of merge references from the spans just read.
            sheet.spannedItems
        }
    }

    /**
     * Deletes all cells within the span of the given [Span] object except for the top-left cell.
     */
    private fun deleteAllButTopLeftCellsOfSpanObj(spanObj: Span, sheet: Sheet) {
        val columnSpanStart = spanObj.columnSpanStart
        val columnSpanEnd = spanObj.columnSpanEnd
        val rowSpanStart = spanObj.rowSpanStart
        val rowSpanEnd = spanObj.rowSpanEnd

        for (columnI in columnSpanStart..columnSpanEnd) {
            for (rowI in rowSpanStart..rowSpanEnd) {
                val isTopLeft = columnI == columnSpanStart && rowI == rowSpanStart
                if (isTopLeft) continue
                // The cell stays in the file even though the model shows only the anchor:
                // remember it for the save path.
                sheet.sheetData[rowI]?.get(columnI)?.let { covered ->
                    sheet.mergedCells.getOrPut(rowI) { mutableMapOf() }[columnI] = covered
                }
                sheet.removeCell(rowI, columnI)
            }
        }
    }

    // Reads the styles from the Excel file.
    private fun parseStyles(declaredPath: String) {
        var stylesPath = declaredPath
        if (stylesPath.isEmpty()) {
            // The styles part is optional (§18.8): a workbook without one is unformatted,
            // not damaged, and minimal writers leave it out. Give it the default one so
            // that styling such a workbook works like any other.
            stylesPath = "xl/styles.xml"
            excel.stylesPath = stylesPath
            val content = DEFAULT_STYLES.encodeToByteArray()
            excel.archive.addFile(ArchiveFile(stylesPath, content.size, content))

            val rIdNumber = getAvailableRid()
            excel.xmlFiles["xl/_rels/workbook.xml.rels"]?.getElementsByTag("Relationships")?.firstOrNull()?.appendChild(
                Element(
                    "Relationship",
                    listOf(Attribute("Id", "rId$rIdNumber"), Attribute("Type", RELATIONSHIPS_STYLES), Attribute("Target", "styles.xml")),
                )
            )
            rId.add("rId$rIdNumber")

            val contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"
            val types = excel.xmlFiles["[Content_Types].xml"]
            if (types?.getElementsByTag("Override")?.none { it.attr("ContentType") == contentType } == true) {
                types.getElementsByTag("Types").firstOrNull()?.appendChild(
                    Element("Override", listOf(Attribute("PartName", "/xl/styles.xml"), Attribute("ContentType", contentType)))
                )
            }
        }

        val styles = excel.archive.findFile(stylesPath)
            ?: damagedExcel(text = "styles")

        styles.decompress()
        val document = parseXmlPart(styles.content.decodeToString())
        excel.xmlFiles[stylesPath] = document

        excel.fontStyleList = mutableListOf()
        excel.patternFill = mutableListOf()
        excel.cellStyleList = mutableListOf()
        excel.cellStylePristine = mutableListOf()
        excel.borderSetList = mutableListOf()

        // `fontId`, `fillId` and `borderId` (§18.8.45) count the direct children of
        // <fonts>, <fills> and <borders>, and nothing else. The same element names occur
        // under <dxfs> for conditional formats; taking every <font>, <patternFill> and
        // <border> in the document made the three lists longer than the tables, and the
        // ids of every style added later pointed past the end.
        fun tableEntries(table: String, entry: String): List<Element> =
            document.getElementsByTag(table).firstOrNull()?.children()?.filter { it.tagName() == entry }
                ?: emptyList()

        tableEntries("fonts", "font").forEach { excel.fontStyleList.add(parseFont(it)) }

        tableEntries("fills", "fill").forEach { fill ->
            val pattern = fill.children().firstOrNull { it.tagName() == "patternFill" }
            val foreground = pattern?.children()?.firstOrNull { it.tagName() == "fgColor" }
            excel.patternFill.add(
                when {
                    // A gradient, or a pattern without a colour Kexcel can express: the
                    // entry still takes its place in the list, and reads as "no colour".
                    pattern == null -> UNMODELLED_FILL
                    // "" for a theme or indexed colour.
                    foreground != null -> foreground.attr("rgb")
                    pattern.children().isEmpty() -> pattern.attr("patternType").ifEmpty { "none" }
                    else -> UNMODELLED_FILL
                }
            )
        }

        tableEntries("borders", "border").forEach { node ->
            val diagonalUp = node.attr("diagonalUp").trim()
                .let { it.isNotEmpty() && it != "0" && it != "false" }
            val diagonalDown = node.attr("diagonalDown").trim()
                .let { it.isNotEmpty() && it != "0" && it != "false" }

            val borderElementNames = listOf("left", "right", "top", "bottom", "diagonal")
            val borderElements = mutableMapOf<String, Border>()

            for (elementName in borderElementNames) {
                val element = node.getElementsByTag(elementName).singleOrNull()
                val borderStyle = element?.attr("style")?.trim()
                    ?.let { getBorderStyleByName(it) }
                val borderColorHex = element?.getElementsByTag("color")?.singleOrNull()
                    ?.attr("rgb")?.trim()?.takeIf { it.isNotEmpty() }

                borderElements[elementName] = Border(
                    borderStyle = borderStyle,
                    borderColorHex = borderColorHex?.toExcelColor()
                )
            }

            excel.borderSetList.add(
                BorderSet(
                    leftBorder = borderElements["left"]!!,
                    rightBorder = borderElements["right"]!!,
                    topBorder = borderElements["top"]!!,
                    bottomBorder = borderElements["bottom"]!!,
                    diagonalBorder = borderElements["diagonal"]!!,
                    diagonalBorderDown = diagonalDown,
                    diagonalBorderUp = diagonalUp
                )
            )
        }

        document.getElementsByTag("numFmts").forEach { node1 ->
            node1.getElementsByTag("numFmt").forEach { node ->
                val numFmtId = node.attr("numFmtId")!!.toInt()
                val formatCode = node.attr("formatCode")!!
                if (numFmtId >= 164) {
                    excel.numFormats.add(numFmtId, NumFormat.custom(formatCode) as CustomNumFormat)
                }
            }
        }

        document.getElementsByTag("cellXfs").forEach { node1 ->
            node1.getElementsByTag("xf").forEach { node ->
                val numFmtId = getFontIndex(node, "numFmtId")
                excel.numFmtIds.add(numFmtId)

                var fontColor = ExcelColor.black.colorHex
                var backgroundColor = ExcelColor.none.colorHex
                var fontFamily: String? = null
                var fontScheme = FontScheme.Unset
                var borderSet: BorderSet? = null

                var fontSize = 12
                var isBold = false
                var isItalic = false
                var underline = Underline.None
                var horizontalAlign = HorizontalAlign.Left
                var verticalAlign = VerticalAlign.Bottom
                var textWrapping: TextWrapping? = null
                var rotation = 0

                val font = excel.fontStyleList.getOrNull(getFontIndex(node, "fontId"))
                if (font != null) {
                    fontColor = font.fontColorHex.colorHex
                    fontSize = font.fontSize ?: 12
                    isBold = font.isBold
                    isItalic = font.isItalic
                    underline = font.underline
                    fontFamily = font.fontFamily
                    fontScheme = font.fontScheme
                }

                val fillId = getFontIndex(node, "fillId")
                if (fillId < excel.patternFill.size) {
                    backgroundColor = excel.patternFill[fillId]
                }

                val borderId = getFontIndex(node, "borderId")
                if (borderId < excel.borderSetList.size) {
                    borderSet = excel.borderSetList[borderId]
                }

                if (node.children().isNotEmpty()) {
                    node.getElementsByTag("alignment").forEach { child ->
                        if (getFontIndex(child, "wrapText") == 1) {
                            textWrapping = TextWrapping.WrapText
                        } else if (getFontIndex(child, "shrinkToFit") == 1) {
                            textWrapping = TextWrapping.Clip
                        }

                        // The attributes live on <alignment> itself; older Dart-written files
                        // put them on the parent <xf>, so fall back to `node`.
                        fun alignmentAttr(name: String): String? =
                            child.attr(name)?.takeIf { it.isNotEmpty() }
                                ?: node.attr(name)?.takeIf { it.isNotEmpty() }

                        val vertical = alignmentAttr("vertical")
                        if (vertical != null) {
                            verticalAlign = when (vertical) {
                                "top" -> VerticalAlign.Top
                                "center" -> VerticalAlign.Center
                                else -> verticalAlign
                            }
                        }

                        val horizontal = alignmentAttr("horizontal")
                        if (horizontal != null) {
                            horizontalAlign = when (horizontal) {
                                "center" -> HorizontalAlign.Center
                                "right" -> HorizontalAlign.Right
                                else -> horizontalAlign
                            }
                        }

                        val rotationString = alignmentAttr("textRotation")
                        if (rotationString != null) {
                            rotation = rotationString.toDoubleOrNull()?.toInt() ?: 0
                        }
                    }
                }

                var numFormat = excel.numFormats.getByNumFmtId(numFmtId)
                if (numFormat == null) {
                    numFormat = NumFormat.standard_0
                }

                val cellStyle = CellStyle(
                    fontColorHex = fontColor.toExcelColor(),
                    fontFamily = fontFamily,
                    fontScheme = fontScheme,
                    fontSize = fontSize,
                    bold = isBold,
                    italic = isItalic,
                    underline = underline,
                    // A pattern name ("gray125") is not a colour either.
                    backgroundColorHex = if (assertHexString(backgroundColor))
                        backgroundColor.toExcelColor() else ExcelColor.none,
                    horizontalAlign = horizontalAlign,
                    verticalAlign = verticalAlign,
                    textWrapping = textWrapping,
                    rotation = rotation,
                    leftBorder = borderSet?.leftBorder,
                    rightBorder = borderSet?.rightBorder,
                    topBorder = borderSet?.topBorder,
                    bottomBorder = borderSet?.bottomBorder,
                    diagonalBorder = borderSet?.diagonalBorder,
                    diagonalBorderUp = borderSet?.diagonalBorderUp ?: false,
                    diagonalBorderDown = borderSet?.diagonalBorderDown ?: false,
                    numberFormat = numFormat
                )

                excel.cellStylePristine.add(cellStyle.copyWith())
                cellStyle.xfIndex = excel.cellStyleList.size
                cellStyle.owner = excel
                excel.cellStyleList.add(cellStyle)
            }
        }
    }

    /** One `<font>` of the styles part (§18.8.22), reduced to what [CellStyle] models. */
    private fun parseFont(font: Element): FontStyle {
        fun child(name: String) = font.children().firstOrNull { it.tagName() == name }

        return FontStyle(
            // A theme or indexed colour has no `rgb` and reads as the default black.
            fontColorHex = (child("color")?.attr("rgb")?.takeIf { it.isNotEmpty() } ?: ExcelColor.black.colorHex).toExcelColor(),
            fontSize = child("sz")?.attr("val")?.toDoubleOrNull()?.toInt() ?: 12,
            fontFamily = child("name")?.attr("val")?.takeIf { it.isNotEmpty() },
            fontScheme = child("scheme")?.let { if (it.attr("val") == "major") FontScheme.Major else FontScheme.Minor }
                ?: FontScheme.Unset,
            // Presence alone is not enough: <b val="0"/> is an explicit "not bold".
            isBold = child("b")?.let { isOnOff(it.attr("val")) } ?: false,
            isItalic = child("i")?.let { isOnOff(it.attr("val")) } ?: false,
            // <u/> is a single underline, <u val="double"/> a double one.
            underline = child("u")?.let {
                when (it.attr("val")) {
                    "double" -> Underline.Double
                    "none" -> Underline.None
                    else -> Underline.Single
                }
            } ?: Underline.None,
        )
    }

    private fun getFontIndex(node: Element, text: String): Int {
        val value = node.attr(text)?.trim() ?: return 0
        return value.toIntOrNull()
            ?: if (value.lowercase() == "true") 1 else 0
    }

    internal fun parseTable(node: Element?) {
        node ?: damagedExcel(text = "Missing <sheet> node in workbook.xml")
        val name = node.attr("name")
        val path = worksheetPaths[relationshipId(node)]

        if (excel.sheetMap[name] == null) {
            excel.sheetMap[name] = Sheet(excel, name)
        }

        val sheetObject = excel.sheetMap[name]!!

        val file = path?.let { excel.archive.findFile(it) }
            ?: damagedExcel(text = "Missing worksheet part for sheet '$name'")
        file.decompress()

        val content = parseXmlPart(file.content.decodeToString())
        val worksheet = content.getElementsByTag("worksheet").first()

        // Check for right-to-left view
        val sheetViews = worksheet?.getElementsByTag("sheetView")?.toList()
        if (sheetViews?.isNotEmpty() == true) {
            val rtl = sheetViews.first().attr("rightToLeft")
            sheetObject.isRTL = rtl != null && rtl == "1"
        }

        val sheet = worksheet?.getElementsByTag("sheetData")?.first()

        // `r` is optional on both <row> and <c> (§18.3.1.73, §18.3.1.4). When it is absent
        // the element's position is implied by document order, so the running index of the
        // previous sibling is threaded through the parse.
        var previousRowIndex = -1
        val sharedFormulas = SharedFormulas()
        findRows(sheet).forEach { child ->
            previousRowIndex = parseRow(child, sheetObject, name, previousRowIndex, sharedFormulas)
        }
        sharedFormulas.resolveLateFollowers(sheetObject)

        parseHeaderFooter(worksheet, sheetObject)
        parseColWidthsRowHeights(worksheet, sheetObject)

        excel.sheets[name] = sheet!!
        excel.xmlFiles[path] = content
        excel.xmlSheetId[name] = path

        normalizeTable(sheetObject)
    }

    /**
     * The shared formulas of one worksheet (§18.3.1.40). The expression is written once, on
     * the first cell of the group (`<f t="shared" ref="B1:B3" si="0">A1*2</f>`); every other
     * cell says only `<f t="shared" si="0"/>` and means "that expression, moved to where I
     * am". Excel writes a formula this way whenever it is filled down or across.
     *
     * The followers are expanded while reading, as POI does
     * (`XSSFCell.convertSharedFormula`), so each cell holds its own expression and the
     * group does not have to be kept consistent through later edits.
     */
    private class SharedFormulas {
        class Master(val formula: String, val row: Int, val column: Int)
        class Follower(val row: Int, val column: Int, val si: Int, val source: CellSource)

        val masters = mutableMapOf<Int, Master>()
        val lateFollowers = mutableListOf<Follower>()

        fun expand(si: Int, row: Int, column: Int): String? =
            masters[si]?.let { shiftFormulaReferences(it.formula, row - it.row, column - it.column) }

        /** A follower normally comes after its master in document order; this handles one that did not. */
        fun resolveLateFollowers(sheet: Sheet) {
            lateFollowers.forEach { follower ->
                val formula = expand(follower.si, follower.row, follower.column) ?: return@forEach
                val data = sheet.sheetData[follower.row]?.get(follower.column) ?: return@forEach
                data._value = FormulaCellValue(formula).also {
                    it.source = CellSource(
                        type = follower.source.type,
                        rawValue = follower.source.rawValue,
                        hasFormula = true,
                        formulaAttributes = follower.source.formulaAttributes - SHARED_GROUP_ATTRIBUTES,
                        cellAttributes = follower.source.cellAttributes,
                    )
                }
            }
        }
    }

    /** Parses one `<row>` and returns the row index it occupied. */
    private fun parseRow(
        node: Element,
        sheetObject: Sheet,
        name: String,
        previousRowIndex: Int,
        sharedFormulas: SharedFormulas,
    ): Int {
        val rowIndex = getRowNumber(node)?.minus(1) ?: (previousRowIndex + 1)
        if (rowIndex < 0) return previousRowIndex

        val attributes = node.attributes().filter { it.key != "r" }.associate { it.key to it.value }
        if (attributes.isNotEmpty()) sheetObject.rowAttributes[rowIndex] = attributes

        var previousColumnIndex = -1
        findCells(node).forEach { child ->
            previousColumnIndex = parseCell(child, sheetObject, rowIndex, name, previousColumnIndex, sharedFormulas)
        }
        return rowIndex
    }

    /** Parses one `<c>` and returns the column index it occupied. */
    private fun parseCell(
        node: Element,
        sheetObject: Sheet,
        rowIndex: Int,
        name: String,
        previousColumnIndex: Int,
        sharedFormulas: SharedFormulas,
    ): Int {
        val columnIndex = getCellNumber(node) ?: (previousColumnIndex + 1)

        val s1 = node.attribute("s")?.value
        var s = 0
        if (s1 != null) {
            s = s1.trim().toIntOrNull() ?: 0
            val rC = node.attr("r").toString()

            if (excel.cellStyleReferenced[name] == null) {
                excel.cellStyleReferenced[name] = mutableMapOf(rC to s)
            } else {
                excel.cellStyleReferenced[name]!![rC] = s
            }
        }
        val style = excel.cellStyleList.getOrNull(s)
            ?: damagedExcel("Cell ${node.attr("r")} uses style $s, but styles.xml has only ${excel.cellStyleList.size}")

        val type = node.attr("t").takeIf { it.isNotEmpty() }
        val valueNode = node.getElementsByTag("v").firstOrNull()
        val rawValue = valueNode?.let { parseValue(it) }
        val cellAttributes = CELL_PASS_THROUGH_ATTRIBUTES
            .mapNotNull { key -> node.attribute(key)?.let { key to it.value } }
            .toMap()

        // `t` describes the cached result in <v>, not the cell: a boolean-, error- or
        // string-typed cell can still be a formula. The expression is what Kexcel models
        // (and writes back as <f>), so it wins over the cache whatever the type.
        val formulaNode = node.getElementsByTag("f").firstOrNull()

        val value: CellValue? = if (formulaNode != null) {
            parseFormula(formulaNode, node, type, rawValue, cellAttributes, rowIndex, columnIndex, sharedFormulas)
        } else when (type) {
            // shared string
            "s" -> {
                val raw = (rawValue ?: "").trim()
                // No index at all is a string cell with nothing in it; POI reads it as an
                // empty string. Only an index that is *wrong* means damage.
                if (raw.isEmpty()) TextCellValue("").also {
                    it.source = CellSource(type, rawValue = null, cellAttributes = cellAttributes)
                } else {
                    val index = raw.toIntOrNull()
                        ?: damagedExcel("Cell ${node.attr("r")} has a non-numeric shared string index: `$raw`")
                    val sharedString = excel.sharedStrings.value(index)
                        ?: damagedExcel(
                            "Cell ${node.attr("r")} references shared string $index, which is not in sharedStrings.xml"
                        )
                    TextCellValue.span(sharedString.textSpan).also {
                        it.sourceItem = sharedString.node
                        it.source = CellSource(type, rawValue = null, cellAttributes = cellAttributes)
                    }
                }
            }
            // A date in ISO 8601 form (§18.18.11, ST_CellType `d`), which is how the Strict
            // conformance class stores dates and what some exporters write.
            "d" -> if (rawValue.isNullOrBlank()) null else {
                isoDateTime(rawValue.trim(), node.attr("r"))
                    .also { it.source = CellSource(type, rawValue, cellAttributes = cellAttributes) }
            }
            // boolean
            "b" -> BoolCellValue(rawValue == "1")
                .also { it.source = CellSource(type, rawValue, cellAttributes = cellAttributes) }
            // error. No dedicated error type exists, so the literal is kept as a formula
            // that evaluates to the same error; its source remembers it had no <f>, and an
            // untouched cell is written back as the error literal it was.
            "e" -> FormulaCellValue(rawValue ?: "")
                .also { it.source = CellSource(type, rawValue, hasFormula = false, cellAttributes = cellAttributes) }
            // A formula's string result without its <f>: all that is left is the text. As a
            // formula it would be written back as <f>text</f>, which Excel shows as #NAME?.
            "str" -> TextCellValue(rawValue ?: "")
                .also { it.source = CellSource(type, rawValue, cellAttributes = cellAttributes) }
            // inline string. <is> shares the CT_Rst content model with a shared string's
            // <si>, so it is read with the same code — otherwise everything after the
            // first run of a rich inline string would be dropped.
            "inlineStr" -> inlineString(node)
                .also { it.source = CellSource(type, rawValue = null, cellAttributes = cellAttributes) }
            // number (default)
            else -> when {
                // An empty <v/> carries no number. Producers emit it for a cell that
                // has a style but no value; feeding "" to a NumFormat would throw.
                rawValue.isNullOrBlank() -> null
                else -> {
                    val numFormat = if (s1 != null) {
                        excel.numFormats.getByNumFmtId(excel.numFmtIds.getOrElse(s) { 0 }) ?: NumFormat.defaultNumeric
                    } else NumFormat.defaultNumeric
                    // Pretty-printed XML puts whitespace around the number.
                    val number = rawValue.trim()
                    val read = try {
                        numFormat.read(number)
                    } catch (e: NumberFormatException) {
                        damagedExcel("Cell ${node.attr("r")} holds `$number`, which is not a number")
                    }
                    read.also { it.source = CellSource(type, rawValue, cellAttributes = cellAttributes) }
                }
            }
        }

        sheetObject.putParsed(rowIndex, columnIndex, value, style, styleIsImplicit = s1 == null)
        return columnIndex
    }

    /** Reads `2024-01-15`, `2024-01-15T10:30:00`, `2024-01-15T10:30:00.250Z` or `10:30:00`. */
    private fun isoDateTime(text: String, cellRef: String): CellValue {
        val value = text.removeSuffix("Z")
        return try {
            when {
                'T' in value -> DateTimeCellValue.fromLocalDateTime(LocalDateTime.parse(value))
                '-' in value -> DateCellValue.fromLocalDate(LocalDate.parse(value))
                else -> LocalTime.parse(value).let { TimeCellValue(it.hour, it.minute, it.second, it.nanosecond / 1_000_000, (it.nanosecond / 1_000) % 1_000) }
            }
        } catch (e: IllegalArgumentException) {
            damagedExcel("Cell $cellRef holds `$text`, which is not an ISO 8601 date")
        }
    }

    private fun inlineString(cell: Element): TextCellValue {
        val isEl = cell.getElementsByTag("is").firstOrNull() ?: return TextCellValue("")
        val item = SharedString(Element("si", emptyList(), isEl.childNodesCopy()))
        return TextCellValue.span(item.textSpan).also { it.sourceItem = item.node }
    }

    private fun parseFormula(
        formulaNode: Element,
        cell: Element,
        type: String?,
        rawValue: String?,
        cellAttributes: Map<String, String>,
        rowIndex: Int,
        columnIndex: Int,
        sharedFormulas: SharedFormulas,
    ): FormulaCellValue {
        var formula = parseValue(formulaNode)
        var attributes = formulaNode.attributes().associate { it.key to it.value }

        // The cached result of a string-typed cell is an index into a table that is rebuilt
        // on save, or sits in <is>; either way it is kept as the text itself (`t="str"`).
        var cachedType = type
        var cachedValue = rawValue
        when (type) {
            "s" -> {
                cachedType = "str"
                cachedValue = rawValue?.trim()?.toIntOrNull()?.let { excel.sharedStrings.value(it)?.stringValue }
            }
            "inlineStr" -> {
                cachedType = "str"
                cachedValue = inlineString(cell).toString()
            }
        }

        var lateFollower: Int? = null
        if (attributes["t"] == "shared") {
            val si = attributes["si"]?.trim()?.toIntOrNull()
            if (si != null) {
                if (formula.isNotEmpty()) {
                    // The first cell that spells the expression out is the master. A follower
                    // may repeat its own expression; it is already the right one.
                    if (si !in sharedFormulas.masters) {
                        sharedFormulas.masters[si] = SharedFormulas.Master(formula, rowIndex, columnIndex)
                    }
                    attributes = attributes - SHARED_GROUP_ATTRIBUTES
                } else {
                    val expanded = sharedFormulas.expand(si, rowIndex, columnIndex)
                    if (expanded != null) {
                        formula = expanded
                        attributes = attributes - SHARED_GROUP_ATTRIBUTES
                    } else {
                        // Master not seen yet. The group attributes stay on the source so
                        // that, should it never turn up, the cell is written back as it was.
                        lateFollower = si
                    }
                }
            }
        }

        val source = CellSource(cachedType, cachedValue, hasFormula = true, attributes, cellAttributes)
        if (lateFollower != null) {
            sharedFormulas.lateFollowers += SharedFormulas.Follower(rowIndex, columnIndex, lateFollower, source)
        }
        return FormulaCellValue(formula).also { it.source = source }
    }



    /**
     * A number N such that `rIdN` is not yet a relationship id of the workbook.
     *
     * Ids are opaque strings (OPC §9.2); `rId1`, `rId2`, … is only Excel's habit. The
     * OpenXML SDK writes ids such as `R856eb3046363446b`, and parsing those as numbers
     * threw, which made such a workbook impossible to open or to add a sheet to.
     */
    private fun getAvailableRid(): Int {
        val pattern = Regex("""^rId(\d+)$""")
        val highest = rId.mapNotNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return highest + 1
    }

    /**
     * Creates a new sheet with the given [newSheet] name, adds it to the
     * `xl/worksheets/` directory and registers it in the workbook.
     */
    internal fun createSheet(newSheet: String) {
        var sheetId = -1
        val sheetIdList = mutableListOf<Int>()

        excel.xmlFiles["xl/workbook.xml"]
            ?.getElementsByTag("sheet")
            ?.forEach { sheetIdNode ->
                val sheetIdAttr = sheetIdNode.attr("sheetId")
                if (sheetIdAttr != null) {
                    val t = sheetIdAttr.trim().toInt()
                    if (!sheetIdList.contains(t)) sheetIdList.add(t)
                } else {
                    damagedExcel(text = "Corrupted Sheet Indexing")
                }
            }

        sheetIdList.sort()

        for (i in sheetIdList.indices) {
            if ((i + 1) != sheetIdList[i]) {
                sheetId = i + 1
                break
            }
        }
        if (sheetId == -1) {
            sheetId = if (sheetIdList.isEmpty()) 1 else sheetIdList.size + 1
        }

        val sheetNumber = sheetId
        // `sheetId` and the part name are unrelated (§18.2.19): after a sheet is deleted in
        // Excel the ids keep their gap while the parts are renumbered. Naming the new part
        // after the id could therefore land on a part that already holds another sheet.
        var partNumber = sheetNumber
        while (excel.archive.findFile("xl/worksheets/sheet$partNumber.xml") != null ||
            worksheetPaths.containsValue("xl/worksheets/sheet$partNumber.xml")
        ) {
            partNumber++
        }
        val ridNumber = getAvailableRid()

        // NOTE: `Element.children()` returns a detached copy of the child list — appending to it
        // does not touch the document. Always use `appendChild` to actually modify the DOM.
        excel.xmlFiles["xl/_rels/workbook.xml.rels"]
            ?.getElementsByTag("Relationships")
            ?.firstOrNull()
            ?.appendChild(
                Element(
                    "Relationship",
                    listOf(
                        Attribute("Id", "rId$ridNumber"),
                        Attribute("Type", "$RELATIONSHIPS/worksheet"),
                        Attribute("Target", "worksheets/sheet$partNumber.xml")
                    )
                )
            )

        if (!rId.contains("rId$ridNumber")) {
            rId.add("rId$ridNumber")
        }

        val sheetNode = Element(
            "sheet",
            listOf(
                Attribute("state", "visible"),
                Attribute("name", newSheet),
                Attribute("sheetId", "$sheetNumber"),
                Attribute("${relationshipsPrefix(excel.xmlFiles["xl/workbook.xml"]!!)}:id", "rId$ridNumber")
            )
        )

        val sheetsNode = excel.xmlFiles["xl/workbook.xml"]
            ?.getElementsByTag("sheets")
            ?.firstOrNull()
            ?: damagedExcel(text = "Missing <sheets> node in workbook.xml")

        sheetsNode.appendChild(sheetNode)

        worksheetPaths["rId$ridNumber"] = "xl/worksheets/sheet$partNumber.xml"

        val worksheetXml = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
             xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
             mc:Ignorable="x14ac xr xr2 xr3"
             xmlns:x14ac="http://schemas.microsoft.com/office/spreadsheetml/2009/9/ac"
             xmlns:xr="http://schemas.microsoft.com/office/spreadsheetml/2014/revision"
             xmlns:xr2="http://schemas.microsoft.com/office/spreadsheetml/2015/revision2"
             xmlns:xr3="http://schemas.microsoft.com/office/spreadsheetml/2016/revision3">
             <dimension ref="A1"/>
             <sheetViews><sheetView workbookViewId="0"/></sheetViews>
             <sheetData/>
             <pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>
            </worksheet>
        """.trimIndent().encodeToByteArray()

        excel.archive.addFile(
            ArchiveFile(
                "xl/worksheets/sheet$partNumber.xml",
                worksheetXml.size,
                worksheetXml
            )
        )

        val newSheetFile = excel.archive.findFile("xl/worksheets/sheet$partNumber.xml")!!
        newSheetFile.decompress()
        val document = parseXmlPart(newSheetFile.content.decodeToString())
        excel.xmlFiles["xl/worksheets/sheet$partNumber.xml"] = document
        excel.xmlSheetId[newSheet] = "xl/worksheets/sheet$partNumber.xml"

        excel.xmlFiles["[Content_Types].xml"]
            ?.getElementsByTag("Types")
            ?.firstOrNull()
            ?.appendChild(
                Element(
                    "Override",
                    listOf(
                        Attribute(
                            "ContentType",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
                        ),
                        Attribute("PartName", "/xl/worksheets/sheet$partNumber.xml")
                    )
                )
            )

        parseTable(sheetNode)
    }

    private fun parseHeaderFooter(worksheet: Element?, sheetObject: Sheet) {
        val results = worksheet?.getElementsByTag("headerFooter")?.toList() ?: return
        if (results.isEmpty()) return
        sheetObject.headerFooter = HeaderFooter.fromXmlElement(results.first())
    }

    private fun parseColWidthsRowHeights(worksheet: Element?, sheetObject: Sheet) {
        // Parse default column width and default row height
        // e.g. <sheetFormatPr defaultColWidth="26.33" defaultRowHeight="13" />
        worksheet?.getElementsByTag("sheetFormatPr")?.forEach { element ->
            // The two attributes are independent and either may be absent — Excel and POI
            // routinely write `defaultRowHeight` alone. Requiring both meant a lone
            // attribute was dropped on read, and the save path then deleted the whole
            // (now attribute-less) <sheetFormatPr>.
            element.attr("defaultColWidth").toDoubleOrNull()
                ?.let { sheetObject.setDefaultColumnWidth(it) }
            element.attr("defaultRowHeight").toDoubleOrNull()
                ?.let { sheetObject.setDefaultRowHeight(it) }
        }

        // Parse the column definitions.
        // e.g. <col min="2" max="4" width="71.83" customWidth="1" hidden="1" style="3"/>
        // One <col> covers every column from `min` to `max` (§18.3.1.13). Reading only `min`
        // gave the width to the first column of a range and lost it for the others.
        worksheet?.getElementsByTag("cols")?.firstOrNull()?.children()?.filter { it.tagName() == "col" }?.forEach { element ->
            val min = element.attr("min").trim().toIntOrNull() ?: return@forEach
            val max = (element.attr("max").trim().toIntOrNull() ?: min).coerceAtMost(16384)
            if (min < 1 || max < min) return@forEach
            sheetObject.columnSpecs.add(
                Sheet.ColumnSpec(
                    min = min - 1,
                    max = max - 1,
                    attributes = element.attributes().filter { it.key != "min" && it.key != "max" }.associate { it.key to it.value },
                )
            )
            val width = element.attr("width").toDoubleOrNull() ?: return@forEach
            for (columnIndex in (min - 1)..(max - 1)) sheetObject._columnWidths[columnIndex] = width
        }
        sheetObject.columnsDirty = false

        // Parse custom row heights
        // e.g. <row r="1" ht="44" customHeight="1" ...>
        worksheet?.getElementsByTag("row")?.forEach { element ->
            val rowAttr = element.attr("r")
            val heightAttr = element.attr("ht")
            if (rowAttr != null && heightAttr != null) {
                val row = rowAttr.toIntOrNull()
                val height = heightAttr.toDoubleOrNull()
                if (row != null && height != null) {
                    val rowIndex = row - 1 // convert to 0-based index
                    if (rowIndex >= 0) {
                        sheetObject._rowHeights[rowIndex] = height
                    }
                }
            }
        }
    }

    internal companion object {
        /**
         * What a workbook with no styles part is given: the two fills every workbook has, one
         * border, one `<xf>`, and the default font POI assumes for such a workbook
         * (`StylesTable.createDefaultFont`).
         */
        const val DEFAULT_STYLES =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
                """<fonts count="1"><font><sz val="11"/><color indexed="8"/><name val="Calibri"/><family val="2"/><scheme val="minor"/></font></fonts>""" +
                """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
                """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
                """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
                """<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>""" +
                """</styleSheet>"""

        /** Takes the place, in the fill list, of a `<fill>` that has no ARGB colour. */
        const val UNMODELLED_FILL = ""

        /** `<f>` attributes that describe a shared-formula group rather than the formula. */
        val SHARED_GROUP_ATTRIBUTES = setOf("t", "si", "ref")

        /**
         * `<c>` attributes kept for an unedited cell: `cm` and `vm` index the cell and value
         * metadata that make a formula a dynamic array or a value a rich data type
         * (§18.3.1.4); `ph` marks phonetic text.
         */
        val CELL_PASS_THROUGH_ATTRIBUTES = listOf("cm", "vm", "ph")

        fun parseValue(node: Element?): String {
            if (node == null) return ""
            // Dart's _parseValue concatenates only this element's own text nodes
            // (ignoring descendant elements), preserving whitespace. Ksoup's
            // wholeOwnText() is the exact equivalent; children() would exclude
            // text nodes entirely and yield "".
            return normalizeNewLine(node.wholeOwnText())
        }
    }
}