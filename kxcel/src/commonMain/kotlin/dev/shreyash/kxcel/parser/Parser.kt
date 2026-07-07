package dev.shreyash.kxcel.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Attribute
import com.fleeksoft.ksoup.nodes.Element
import dev.shreyash.kxcel.archive.ArchiveFile
import dev.shreyash.kxcel.Excel
import dev.shreyash.kxcel.number_format.CustomNumFormat
import dev.shreyash.kxcel.number_format.NumFormat
import dev.shreyash.kxcel.shared_strings.SharedString
import dev.shreyash.kxcel.sheet.BoolCellValue
import dev.shreyash.kxcel.sheet.Border
import dev.shreyash.kxcel.sheet.BorderSet
import dev.shreyash.kxcel.sheet.CellIndex
import dev.shreyash.kxcel.sheet.CellStyle
import dev.shreyash.kxcel.sheet.CellValue
import dev.shreyash.kxcel.sheet.Element
import dev.shreyash.kxcel.sheet.FontStyle
import dev.shreyash.kxcel.sheet.FormulaCellValue
import dev.shreyash.kxcel.sheet.HeaderFooter
import dev.shreyash.kxcel.sheet.Sheet
import dev.shreyash.kxcel.sheet.TextCellValue
import dev.shreyash.kxcel.sheet.getBorderStyleByName
import dev.shreyash.kxcel.utils.ExcelColor
import dev.shreyash.kxcel.utils.FontScheme
import dev.shreyash.kxcel.utils.HorizontalAlign
import dev.shreyash.kxcel.utils.TextWrapping
import dev.shreyash.kxcel.utils.Underline
import dev.shreyash.kxcel.utils.VerticalAlign
import dev.shreyash.kxcel.utils.damagedExcel
import dev.shreyash.kxcel.utils.findCells
import dev.shreyash.kxcel.utils.findRows
import dev.shreyash.kxcel.utils.*
import dev.shreyash.kxcel.utils.getCellNumber
import dev.shreyash.kxcel.utils.getRowNumber
import dev.shreyash.kxcel.utils.normalizeNewLine
import dev.shreyash.kxcel.utils.toExcelColor

class Parser(private val excel: Excel) {

    private val rId: MutableList<String> = mutableListOf()
    private val worksheetTargets: MutableMap<String, String> = mutableMapOf()

    fun startParsing() {
        putContentXml()
        parseRelations()
        parseStyles(excel.stylesTarget)
        parseSharedStrings()
        parseContent()
        parseMergedCells()
    }

    private fun normalizeTable(sheet: Sheet) {
        if (sheet.maxRows == 0 || sheet.maxColumns == 0) {
            sheet.sheetData.clear()
        }
        sheet._countRowsAndColumns()
    }

    private fun putContentXml() {
        val file = excel.archive.findFile("[Content_Types].xml") ?: damagedExcel()
        file.decompress()
        excel.xmlFiles["[Content_Types].xml"] = Ksoup.parseXml(file.content.decodeToString())
    }

    private fun parseRelations() {
        val relations = excel.archive.findFile("xl/_rels/workbook.xml.rels")
            ?: damagedExcel()

        relations.decompress()
        val document = Ksoup.parseXml(relations.content.decodeToString())
        excel.xmlFiles["xl/_rels/workbook.xml.rels"] = document

        document.getElementsByTag("Relationship").forEach { node ->
            val id = node.attr("Id")
            val target = node.attr("Target")
            if (target != null) {
                when (node.attr("Type")) {
                    RELATIONSHIPS_STYLES -> excel.stylesTarget = target
                    RELATIONSHIPS_WORKSHEET -> if (id != null) worksheetTargets[id] = target
                    RELATIONSHIPS_SHARED_STRINGS -> excel.sharedStringsTarget = target
                }
            }
            if (id != null && !rId.contains(id)) {
                rId.add(id)
            }
        }
    }

    private fun parseSharedStrings() {
        var sharedStrings = excel.archive.findFile(excel.absSharedStringsTarget)

        if (sharedStrings == null) {
            excel.sharedStringsTarget = "sharedStrings.xml"

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
        val document = Ksoup.parseXml(sharedStrings.content.decodeToString())
        excel.xmlFiles["xl/${excel.sharedStringsTarget}"] = document

        document.getElementsByTag("si").forEach { node ->
            parseSharedString(node)
        }
    }

    private fun parseSharedString(node: Element) {
        val sharedString = SharedString(node = node)
        excel.sharedStrings.add(sharedString, sharedString.stringValue)
    }

    private fun parseContent(run: Boolean = true) {
        val workbook = excel.archive.findFile("xl/workbook.xml") ?: damagedExcel()
        workbook.decompress()
        val document = Ksoup.parseXml(workbook.content.decodeToString())
        excel.xmlFiles["xl/workbook.xml"] = document

        document.getElementsByTag("sheet").forEach { node ->
            if (run) {
                parseTable(node)
            } else {
                val rid = node.attr("r:id")
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
        val spannedCells = mutableMapOf<String, MutableList<String>>()

        excel.sheets.forEach { (sheetName, node) ->
            excel.availSheet(sheetName)
            val sheetDataNode = node as Element
            val spanList = mutableListOf<String>()
            val sheet = excel.sheetMap[sheetName]!!

            val worksheetNode = sheetDataNode.parent()
            worksheetNode?.getElementsByTag("mergeCell")?.forEach { element ->
                val ref = element.attr("ref")
                if (ref != null && ref.contains(':') && ref.split(':').size == 2) {
                    if (!sheet.spannedItems.contains(ref)) {
                        sheet.addSpannedItem(ref)
                    }

                    val startCell = ref.split(':')[0]
                    val endCell = ref.split(':')[1]

                    if (!spanList.contains(startCell)) {
                        spanList.add(startCell)
                    }
                    spannedCells[sheetName] = spanList

                    val startIndex = CellIndex.indexByString(startCell)
                    val endIndex = CellIndex.indexByString(endCell)
                    val spanObj = Span.fromCellIndex(start = startIndex, end = endIndex)

                    if (!sheet.spanList.contains(spanObj)) {
                        sheet.spanList.add(spanObj)
                        deleteAllButTopLeftCellsOfSpanObj(spanObj, sheet)
                    }
                    excel.addMergeChangeLookup(sheetName)
                }
            }
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
                sheet.removeCell(rowI, columnI)
            }
        }
    }

    // Reads the styles from the Excel file.
    private fun parseStyles(stylesTarget: String) {
        val styles = excel.archive.findFile("xl/$stylesTarget")
            ?: damagedExcel(text = "styles")

        styles.decompress()
        val document = Ksoup.parseXml(styles.content.decodeToString())
        excel.xmlFiles["xl/$stylesTarget"] = document

        excel.fontStyleList = mutableListOf()
        excel.patternFill = mutableListOf()
        excel.cellStyleList = mutableListOf()
        excel.borderSetList = mutableListOf()

        val fontList = document.getElementsByTag("font").toList()

        document.getElementsByTag("patternFill").forEach { node ->
            val patternType = node.attr("patternType") ?: ""
            if (node.children().isNotEmpty()) {
                node.getElementsByTag("fgColor").forEach { child ->
                    val rgb = child.attr("rgb") ?: ""
                    excel.patternFill.add(rgb)
                }
            } else {
                excel.patternFill.add(patternType)
            }
        }

        document.getElementsByTag("border").forEach { node ->
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

                val fontId = getFontIndex(node, "fontId")
                val fontStyle = FontStyle()

                if (fontId < fontList.size) {
                    val font = fontList[fontId]

                    val clr = nodeChildren(font, "color", attribute = "rgb")
                    if (clr != null && clr !is Boolean) {
                        fontColor = clr.toString()
                    }

                    val size = nodeChildren(font, "sz", attribute = "val")
                    if (size != null) {
                        fontSize = size.toString().toDouble().toInt()
                    }

                    val bold = nodeChildren(font, "b")
                    if (bold != null && bold is Boolean && bold) {
                        isBold = true
                    }

                    val italic = nodeChildren(font, "i")
                    if (italic != null && italic is Boolean && italic) {
                        isItalic = true
                    }

                    val doubleUnderline = nodeChildren(font, "u", attribute = "val")
                    if (doubleUnderline != null) {
                        underline = Underline.Double
                    }

                    val singleUnderline = nodeChildren(font, "u")
                    if (singleUnderline != null) {
                        underline = Underline.Single
                    }

                    val family = nodeChildren(font, "name", attribute = "val")
                    if (family != null && family != true) {
                        fontFamily = family.toString()
                    }

                    val scheme = nodeChildren(font, "scheme", attribute = "val")
                    if (scheme != null) {
                        fontScheme = if (scheme == "major") FontScheme.Major else FontScheme.Minor
                    }

                    fontStyle.isBold = isBold
                    fontStyle.isItalic = isItalic
                    fontStyle.fontSize = fontSize
                    fontStyle.fontFamily = fontFamily
                    fontStyle.fontScheme = fontScheme
                    fontStyle.fontColorHex = fontColor.toExcelColor()
                }

                if (fontStyleIndex(excel.fontStyleList, fontStyle) == -1) {
                    excel.fontStyleList.add(fontStyle)
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

                        // Note: Dart reads alignment attributes from `node` (parent xf), not `child`
                        val vertical = node.attr("vertical")
                        if (vertical != null) {
                            verticalAlign = when (vertical) {
                                "top" -> VerticalAlign.Top
                                "center" -> VerticalAlign.Center
                                else -> verticalAlign
                            }
                        }

                        val horizontal = node.attr("horizontal")
                        if (horizontal != null) {
                            horizontalAlign = when (horizontal) {
                                "center" -> HorizontalAlign.Center
                                "right" -> HorizontalAlign.Right
                                else -> horizontalAlign
                            }
                        }

                        val rotationString = node.attr("textRotation")
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
                    fontSize = fontSize,
                    bold = isBold,
                    italic = isItalic,
                    underline = underline,
                    backgroundColorHex = if (backgroundColor == "none" || backgroundColor.isEmpty())
                        ExcelColor.none else backgroundColor.toExcelColor(),
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

                excel.cellStyleList.add(cellStyle)
            }
        }
    }

    /**
     * Returns the attribute value of [attribute] on the first [child] element found under [node],
     * returns `true` if the child exists but has no [attribute], or `null` if [child] is absent.
     */
    private fun nodeChildren(node: Element, child: String, attribute: String? = null): Any? {
        val elements = node.getElementsByTag(child)
        if (elements.isEmpty()) return null
        if (attribute != null) {
            return elements.first()?.attr(attribute)
        }
        return true // child present — used as a signal for bold/italic
    }

    private fun getFontIndex(node: Element, text: String): Int {
        val value = node.attr(text)?.trim() ?: return 0
        return value.toIntOrNull()
            ?: if (value.lowercase() == "true") 1 else 0
    }

    internal fun parseTable(node: Element?) {
        node ?: damagedExcel(text = "Missing <sheet> node in workbook.xml")
        val name = node.attr("name")
        val target = worksheetTargets[node.attr("r:id")]

        if (excel.sheetMap[name] == null) {
            excel.sheetMap[name] = Sheet(excel, name)
        }

        val sheetObject = excel.sheetMap[name]!!

        val file = excel.archive.findFile("xl/$target")!!
        file.decompress()

        val content = Ksoup.parseXml(file.content.decodeToString())
        val worksheet = content.getElementsByTag("worksheet").first()

        // Check for right-to-left view
        val sheetViews = worksheet?.getElementsByTag("sheetView")?.toList()
        if (sheetViews?.isNotEmpty() == true) {
            val rtl = sheetViews.first().attr("rightToLeft")
            sheetObject.isRTL = rtl != null && rtl == "1"
        }

        val sheet = worksheet?.getElementsByTag("sheetData")?.first()

        findRows(sheet).forEach { child ->
            parseRow(child, sheetObject, name)
        }

        parseHeaderFooter(worksheet, sheetObject)
        parseColWidthsRowHeights(worksheet, sheetObject)

        excel.sheets[name] = sheet!!
        excel.xmlFiles["xl/$target"] = content
        excel.xmlSheetId[name] = "xl/$target"

        normalizeTable(sheetObject)
    }

    private fun parseRow(node: Element, sheetObject: Sheet, name: String) {
        val rowIndex = (getRowNumber(node) ?: -1) - 1
        if (rowIndex < 0) return

        findCells(node).forEach { child ->
            parseCell(child, sheetObject, rowIndex, name)
        }
    }

    private fun parseCell(node: Element, sheetObject: Sheet, rowIndex: Int, name: String) {
        val columnIndex = getCellNumber(node) ?: return

        val s1 = node.attr("s")
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

        val value: CellValue?
        val type = node.attr("t")

        value = when (type) {
            // shared string
            "s" -> {
                val index = parseValue(node.getElementsByTag("v").first()).toInt()
                val sharedString = excel.sharedStrings.value(index)!!
                TextCellValue.span(sharedString.textSpan)
            }
            // boolean
            "b" -> BoolCellValue(parseValue(node.getElementsByTag("v").first()) == "1")
            // error
            "e",
                // formula string
            "str" -> FormulaCellValue(parseValue(node.getElementsByTag("v").first()))
            // inline string
            "inlineStr" -> TextCellValue(parseValue(node.getElementsByTag("t").first()))
            // number (default)
            else -> {
                val formulaNode = node.getElementsByTag("f")
                if (formulaNode.isNotEmpty()) {
                    FormulaCellValue(parseValue(formulaNode.first()).toString())
                } else {
                    val vNode = node.getElementsByTag("v").firstOrNull()
                    when {
                        vNode == null -> null
                        s1 != null -> {
                            val v = parseValue(vNode)
                            val numFmtId = excel.numFmtIds[s]
                            val numFormat = excel.numFormats.getByNumFmtId(numFmtId)
                                ?: NumFormat.defaultNumeric
                            numFormat.read(v)
                        }
                        else -> NumFormat.defaultNumeric.read(parseValue(vNode))
                    }
                }
            }
        }

        sheetObject.updateCell(
            CellIndex.indexByColumnRow(columnIndex = columnIndex, rowIndex = rowIndex),
            value,
            cellStyle = excel.cellStyleList[s]
        )
    }



    private fun getAvailableRid(): Int {
        rId.sortWith(Comparator { a, b ->
            a.substring(3).toInt().compareTo(b.substring(3).toInt())
        })
        val lastRid = rId.last()
        val digits = lastRid.filter { it.isDigit() }
        return digits.toInt() + 1
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
        val ridNumber = getAvailableRid()

        excel.xmlFiles["xl/_rels/workbook.xml.rels"]
            ?.getElementsByTag("Relationships")
            ?.firstOrNull()
            ?.children()
            ?.add(
                Element(
                    "Relationship",
                    listOf(
                        Attribute("Id", "rId$ridNumber"),
                        Attribute("Type", "$RELATIONSHIPS/worksheet"),
                        Attribute("Target", "worksheets/sheet$sheetNumber.xml")
                    )
                )
            )

        if (!rId.contains("rId$ridNumber")) {
            rId.add("rId$ridNumber")
        }

        excel.xmlFiles["xl/workbook.xml"]
            ?.getElementsByTag("sheets")
            ?.firstOrNull()
            ?.children()
            ?.add(
                Element(
                    "sheet",
                    listOf(
                        Attribute("state", "visible"),
                        Attribute("name", newSheet),
                        Attribute("sheetId", "$sheetNumber"),
                        Attribute("r:id", "rId$ridNumber")
                    )
                )
            )

        worksheetTargets["rId$ridNumber"] = "worksheets/sheet$sheetNumber.xml"

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
                "xl/worksheets/sheet$sheetNumber.xml",
                worksheetXml.size,
                worksheetXml
            )
        )

        val newSheetFile = excel.archive.findFile("xl/worksheets/sheet$sheetNumber.xml")!!
        newSheetFile.decompress()
        val document = Ksoup.parseXml(newSheetFile.content.decodeToString())
        excel.xmlFiles["xl/worksheets/sheet$sheetNumber.xml"] = document
        excel.xmlSheetId[newSheet] = "xl/worksheets/sheet$sheetNumber.xml"

        excel.xmlFiles["[Content_Types].xml"]
            ?.getElementsByTag("Types")
            ?.firstOrNull()
            ?.children()
            ?.add(
                Element(
                    "Override",
                    listOf(
                        Attribute(
                            "ContentType",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
                        ),
                        Attribute("PartName", "/xl/worksheets/sheet$sheetNumber.xml")
                    )
                )
            )

        excel.xmlFiles["xl/workbook.xml"]?.let { workbook ->
            parseTable(workbook.getElementsByTag("sheet").last())
        }
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
            val defaultColWidth = element.attr("defaultColWidth").toDoubleOrNull()
            val defaultRowHeight = element.attr("defaultRowHeight").toDoubleOrNull()
            if (defaultColWidth != null && defaultRowHeight != null) {
                sheetObject.setDefaultColumnWidth(defaultColWidth)
                sheetObject.setDefaultRowHeight(defaultRowHeight)
            }
        }

        // Parse custom column widths
        // e.g. <col min="2" max="2" width="71.83" customWidth="1"/>
        worksheet?.getElementsByTag("col")?.forEach { element ->
            val colAttr = element.attr("min")
            val widthAttr = element.attr("width")
            if (colAttr != null && widthAttr != null) {
                val col = colAttr.toIntOrNull()
                val width = widthAttr.toDoubleOrNull()
                if (col != null && width != null) {
                    val colIndex = col - 1 // convert to 0-based index
                    if (colIndex >= 0) {
                        sheetObject._columnWidths[colIndex] = width
                    }
                }
            }
        }

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

    companion object {
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