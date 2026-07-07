package dev.shreyash.kxcel.save

import com.fleeksoft.ksoup.nodes.Attribute
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import dev.shreyash.kxcel.archive.ArchiveFile
import dev.shreyash.kxcel.Excel
import dev.shreyash.kxcel.number_format.CustomNumFormat
import dev.shreyash.kxcel.number_format.DateTimeNumFormat
import dev.shreyash.kxcel.number_format.NumFormat
import dev.shreyash.kxcel.number_format.NumericNumFormat
import dev.shreyash.kxcel.number_format.StandardNumFormat
import dev.shreyash.kxcel.number_format.TimeNumFormat
import dev.shreyash.kxcel.parser.Parser
import dev.shreyash.kxcel.shared_strings.SharedString
import dev.shreyash.kxcel.sheet.BoolCellValue
import dev.shreyash.kxcel.sheet.BorderSet
import dev.shreyash.kxcel.sheet.CellStyle
import dev.shreyash.kxcel.sheet.CellValue
import dev.shreyash.kxcel.sheet.DateCellValue
import dev.shreyash.kxcel.sheet.DateTimeCellValue
import dev.shreyash.kxcel.sheet.DoubleCellValue
import dev.shreyash.kxcel.sheet.Element
import dev.shreyash.kxcel.sheet.FontStyle
import dev.shreyash.kxcel.sheet.FormulaCellValue
import dev.shreyash.kxcel.sheet.IntCellValue
import dev.shreyash.kxcel.sheet.Sheet
import dev.shreyash.kxcel.sheet.TextCellValue
import dev.shreyash.kxcel.sheet.TimeCellValue
import dev.shreyash.kxcel.utils.FontScheme
import dev.shreyash.kxcel.utils.HorizontalAlign
import dev.shreyash.kxcel.utils.TextWrapping
import dev.shreyash.kxcel.utils.Underline
import dev.shreyash.kxcel.utils.VerticalAlign
import dev.shreyash.kxcel.utils.cloneArchive
import dev.shreyash.kxcel.utils.damagedExcel
import dev.shreyash.kxcel.utils.fontStyleIndex
import dev.shreyash.kxcel.utils.getCellId
import dev.shreyash.kxcel.utils.toFixed2
import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.truncate

private const val EXCEL_DEFAULT_COLUMN_WIDTH = 8.0

class Save(private val excel: Excel, private val parser: Parser) {

    private val archiveFiles: MutableMap<String, ArchiveFile> = mutableMapOf()
    private val innerCellStyle: MutableList<CellStyle> = mutableListOf()

    // region --- Column helpers ---

    private fun addNewColumn(columns: Element, min: Int, max: Int, width: Double) {
        columns.appendChild(
            Element(
                "col",
                listOf(
                    Attribute("min", (min + 1).toString()),
                    Attribute("max", (max + 1).toString()),
                    Attribute("width", width.toFixed2()),
                    Attribute("bestFit", "1"),
                    Attribute("customWidth", "1"),
                )
            )
        )
    }

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
        value: CellValue?,
        numberFormat: NumFormat?,
    ): Element {
        var sharedString: SharedString? = null
        if (value is TextCellValue) {
            val existing = excel.sharedStrings.tryFind(value.toString())
            sharedString = if (existing != null) {
                excel.sharedStrings.add(existing, value.toString())
                existing
            } else {
                excel.sharedStrings.addFromString(value.toString())
            }
        }

        val rC = getCellId(columnIndex, rowIndex)

        val attributes = mutableListOf(
            Attribute("r", rC),
        )
        if (value is TextCellValue) attributes.add(Attribute("t", "s"))
        if (value is BoolCellValue) attributes.add(Attribute("t", "b"))

        val cellStyle = excel.sheetMap[sheet]?.sheetData?.get(rowIndex)?.get(columnIndex)?.cellStyle

        if (excel.styleChanges && cellStyle != null) {
            var upperLevelPos = checkPosition(excel.cellStyleList, cellStyle)
            if (upperLevelPos == -1) {
                val lowerLevelPos = checkPosition(innerCellStyle, cellStyle)
                upperLevelPos = if (lowerLevelPos != -1) {
                    lowerLevelPos + excel.cellStyleList.size
                } else {
                    0
                }
            }
            attributes.add(1, Attribute("s", "$upperLevelPos"))
        } else if (excel.cellStyleReferenced.containsKey(sheet) &&
            excel.cellStyleReferenced[sheet]!!.containsKey(rC)
        ) {
            attributes.add(1, Attribute("s", "${excel.cellStyleReferenced[sheet]!![rC]}"))
        }

        val children: List<Element> = when (value) {
            null -> emptyList()

            is FormulaCellValue -> listOf(
                Element("f", emptyList(), listOf(TextNode(value.formula))),
                Element("v", emptyList(), listOf(TextNode(""))),
            )

            is IntCellValue -> {
                val v = when (numberFormat) {
                    is NumericNumFormat -> numberFormat.writeInt(value)
                    else -> throw IllegalStateException("$numberFormat does not work for IntCellValue")
                }
                listOf(Element("v", emptyList(), listOf(TextNode(v))))
            }

            is DoubleCellValue -> {
                val v = when (numberFormat) {
                    is NumericNumFormat -> numberFormat.writeDouble(value)
                    else -> throw IllegalStateException("$numberFormat does not work for DoubleCellValue")
                }
                listOf(Element("v", emptyList(), listOf(TextNode(v))))
            }

            is DateTimeCellValue -> {
                val v = when (numberFormat) {
                    is DateTimeNumFormat -> numberFormat.writeDateTime(value)
                    else -> throw IllegalStateException("$numberFormat does not work for DateTimeCellValue")
                }
                listOf(Element("v", emptyList(), listOf(TextNode(v))))
            }

            is DateCellValue -> {
                val v = when (numberFormat) {
                    is DateTimeNumFormat -> numberFormat.writeDate(value)
                    else -> throw IllegalStateException("$numberFormat does not work for DateCellValue")
                }
                listOf(Element("v", emptyList(), listOf(TextNode(v))))
            }

            is TimeCellValue -> {
                val v = when (numberFormat) {
                    is TimeNumFormat -> numberFormat.writeTime(value)
                    else -> throw IllegalStateException("$numberFormat does not work for TimeCellValue")
                }
                listOf(Element("v", emptyList(), listOf(TextNode(v))))
            }

            is TextCellValue -> listOf(
                Element(
                    "v", emptyList(),
                    listOf(TextNode(excel.sharedStrings.indexOf(sharedString!!).toString()))
                )
            )

            is BoolCellValue -> listOf(
                Element("v", emptyList(), listOf(TextNode(if (value.value) "1" else "0")))
            )
        }

        return Element("c", attributes, children)
    }

    private fun createNewRow(table: Element, rowIndex: Int, height: Double?): Element {
        val attrs = mutableListOf(Attribute("r", (rowIndex + 1).toString()))
        if (height != null) {
            attrs.add(Attribute("ht", height.toFixed2()))
            attrs.add(Attribute("customHeight", "1"))
        }
        val row = Element("row", attrs, mutableListOf())
        table.appendChild(row)
        return row
    }

    // endregion

    // region --- Styles file ---

    private fun processStylesFile() {
        innerCellStyle.clear()
        val innerPatternFill = mutableListOf<String>()
        val innerFontStyle = mutableListOf<FontStyle>()
        val innerBorderSet = mutableListOf<BorderSet>()

        excel.sheetMap.forEach { (_, sheetObject) ->
            sheetObject.sheetData.forEach { (_, columnMap) ->
                columnMap.forEach { (_, dataObject) ->
                    val cs = dataObject.cellStyle ?: return@forEach
                    if (checkPosition(innerCellStyle, cs) == -1) {
                        innerCellStyle.add(cs)
                    }
                }
            }
        }

        innerCellStyle.forEach { cellStyle ->
            val fs = FontStyle(
                bold = cellStyle.isBold,
                italic = cellStyle.isItalic,
                fontColorHex = cellStyle.fontColor,
                underline = cellStyle.underline,
                fontSize = cellStyle.fontSize,
                fontFamily = cellStyle.fontFamily,
                fontScheme = cellStyle.fontScheme,
            )
            if (fontStyleIndex(excel.fontStyleList, fs) == -1 && fontStyleIndex(innerFontStyle, fs) == -1) {
                innerFontStyle.add(fs)
            }

            val bgColor = cellStyle.backgroundColor.colorHex
            if (!excel.patternFill.contains(bgColor) && !innerPatternFill.contains(bgColor)) {
                innerPatternFill.add(bgColor)
            }

            val bs = createBorderSetFromCellStyle(cellStyle)
            if (!excel.borderSetList.contains(bs) && !innerBorderSet.contains(bs)) {
                innerBorderSet.add(bs)
            }
        }

        val stylesXml = excel.xmlFiles["xl/styles.xml"]!!

        // --- fonts ---
        val fonts = stylesXml.getElementsByTag("fonts").first()
            ?: damagedExcel("Missing <fonts> element in xl/styles.xml")
        val fontCountAttr = fonts.attribute("count")
        val fontCount = (excel.fontStyleList.size + innerFontStyle.size).toString()
        if (fontCountAttr != null) fontCountAttr.setValue(fontCount)
        else fonts.attributes().add("count", fontCount)

        innerFontStyle.forEach { fs ->
            val fontChildren = mutableListOf<Element>()

            if (fs.fontColorHex != null && fs.fontColorHex!!.colorHex != "FF000000") {
                fontChildren.add(
                    Element("color", listOf(Attribute("rgb", fs.fontColorHex!!.colorHex)))
                )
            }
            if (fs.isBold) fontChildren.add(Element("b"))
            if (fs.isItalic) fontChildren.add(Element("i"))
            when {
                fs.underline == Underline.Single ->
                    fontChildren.add(Element("u"))
                fs.underline == Underline.Double ->
                    fontChildren.add(Element("u", listOf(Attribute("val", "double"))))
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

            fonts.appendChild(Element("font", emptyList(), fontChildren))
        }

        // --- fills ---
        val fills = stylesXml.getElementsByTag("fills").first()
            ?: damagedExcel("Missing <fills> element in xl/styles.xml")
        val fillCountAttr = fills.attribute("count")
        val fillCount = (excel.patternFill.size + innerPatternFill.size).toString()
        if (fillCountAttr != null) fillCountAttr.setValue(fillCount)
        else fills.attributes().add("count", fillCount)

        innerPatternFill.forEach { color ->
            if (color.length >= 2) {
                when {
                    color.substring(0, 2).uppercase() == "FF" -> {
                        fills.appendChild(
                            Element("fill", emptyList(), listOf(
                                Element("patternFill", listOf(Attribute("patternType", "solid")), listOf(
                                    Element("fgColor", listOf(Attribute("rgb", color))),
                                    Element("bgColor", listOf(Attribute("rgb", color))),
                                ))
                            ))
                        )
                    }
                    color == "none" || color == "gray125" || color == "lightGray" -> {
                        fills.appendChild(
                            Element("fill", emptyList(), listOf(
                                Element("patternFill", listOf(Attribute("patternType", color)))
                            ))
                        )
                    }
                }
            } else {
                damagedExcel(text = "Corrupted Styles Found. Can't process further, Open up issue in github.")
            }
        }

        // --- borders ---
        val borders = stylesXml.getElementsByTag("borders").first()
            ?: damagedExcel("Missing <borders> element in xl/styles.xml")
        val borderCountAttr = borders.attribute("count")
        val borderCount = (excel.borderSetList.size + innerBorderSet.size).toString()
        if (borderCountAttr != null) borderCountAttr.setValue(borderCount)
        else borders.attributes().add("count", borderCount)

        innerBorderSet.forEach { bs ->
            val borderElement = Element("border", mutableListOf(), mutableListOf())
            if (bs.diagonalBorderDown) borderElement.attributes().add("diagonalDown", "1")
            if (bs.diagonalBorderUp) borderElement.attributes().add("diagonalUp", "1")

            val borderMap = linkedMapOf(
                "left" to bs.leftBorder,
                "right" to bs.rightBorder,
                "top" to bs.topBorder,
                "bottom" to bs.bottomBorder,
                "diagonal" to bs.diagonalBorder,
            )
            borderMap.forEach { (key, borderValue) ->
                val element = Element(key, mutableListOf(), mutableListOf())
                borderValue.borderStyle?.let {
                    element.attributes().add("style", it.style)
                }
                borderValue.borderColorHex?.let {
                    element.appendChild(Element("color", listOf(Attribute("rgb", it))))
                }
                borderElement.appendChild(element)
            }
            borders.appendChild(borderElement)
        }

        // --- cellXfs ---
        val celx = stylesXml.getElementsByTag("cellXfs").first()
            ?: damagedExcel("Missing <cellXfs> element in xl/styles.xml")
        val cellCountAttr = celx.attribute("count")
        val cellCount = (excel.cellStyleList.size + innerCellStyle.size).toString()
        if (cellCountAttr != null) cellCountAttr.setValue(cellCount)
        else celx.attributes().add("count", cellCount)

        innerCellStyle.forEach { cellStyle ->
            val bgColor = cellStyle.backgroundColor.colorHex
            val fs = FontStyle(
                bold = cellStyle.isBold,
                italic = cellStyle.isItalic,
                fontColorHex = cellStyle.fontColor,
                underline = cellStyle.underline,
                fontSize = cellStyle.fontSize,
                fontFamily = cellStyle.fontFamily,
            )
            val hAlign = cellStyle.horizontalAlignment
            val vAlign = cellStyle.verticalAlignment
            val rotation = cellStyle.rotation
            val textWrapping = cellStyle.wrap

            val bgIndex = innerPatternFill.indexOf(bgColor)
            val fontIndex = fontStyleIndex(innerFontStyle, fs)
            val bs = createBorderSetFromCellStyle(cellStyle)
            val borderIndex = innerBorderSet.indexOf(bs)

            val numFmtId = when (val nf = cellStyle.numberFormat) {
                is StandardNumFormat -> nf.numFmtId
                is CustomNumFormat -> excel.numFormats.findOrAdd(nf)
                else -> 0
            }

            val attrs = mutableListOf(
                Attribute("borderId", "${if (borderIndex == -1) 0 else borderIndex + excel.borderSetList.size}"),
                Attribute("fillId", "${if (bgIndex == -1) 0 else bgIndex + excel.patternFill.size}"),
                Attribute("fontId", "${if (fontIndex == -1) 0 else fontIndex + excel.fontStyleList.size}"),
                Attribute("numFmtId", numFmtId.toString()),
                Attribute("xfId", "0"),
            )

            if ((excel.patternFill.contains(bgColor) || innerPatternFill.contains(bgColor)) &&
                bgColor != "none" && bgColor != "gray125" && bgColor.lowercase() != "lightgray"
            ) {
                attrs.add(Attribute("applyFill", "1"))
            }

            if (fontStyleIndex(excel.fontStyleList, fs) != -1 || fontStyleIndex(innerFontStyle, fs) != -1) {
                attrs.add(Attribute("applyFont", "1"))
            }

            val xfChildren = mutableListOf<Element>()
            if (hAlign != HorizontalAlign.Left || textWrapping != null ||
                vAlign != VerticalAlign.Bottom || rotation != 0
            ) {
                attrs.add(Attribute("applyAlignment", "1"))
                val childAttrs = mutableListOf<Attribute>()

                textWrapping?.let {
                    childAttrs.add(Attribute(if (it == TextWrapping.Clip) "shrinkToFit" else "wrapText", "1"))
                }
                if (vAlign != VerticalAlign.Bottom) {
                    childAttrs.add(Attribute("vertical", if (vAlign == VerticalAlign.Top) "top" else "center"))
                }
                if (hAlign != HorizontalAlign.Left) {
                    childAttrs.add(Attribute("horizontal", if (hAlign == HorizontalAlign.Right) "right" else "center"))
                }
                if (rotation != 0) {
                    childAttrs.add(Attribute("textRotation", rotation.toString()))
                }
                xfChildren.add(Element("alignment", childAttrs, emptyList()))
            }

            celx.appendChild(Element("xf", attrs, xfChildren))
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

    fun save(): ByteArray? {
        if (excel.styleChanges) processStylesFile()
        setSheetElements()
        if (excel.defaultSheet != null) setDefaultSheet(excel.defaultSheet)
        setSharedStrings()
        if (excel.mergeChanges) setMerge()
        if (excel.rtlChanges) setRTL()

        for (xmlFile in excel.xmlFiles.keys) {
            val xml = excel.xmlFiles[xmlFile].toString()
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

    // region --- Sheet writing ---

    private fun setColumns(sheetObject: Sheet, xmlFile: Document) {
        val columnElements = xmlFile.getElementsByTag("cols").toList()

        if (sheetObject.getColumnWidths.isEmpty() && sheetObject.getColumnAutoFits.isEmpty()) {
            if (columnElements.isEmpty()) return
            val worksheet = xmlFile.getElementsByTag("worksheet").first()
                ?: damagedExcel("Missing <worksheet> element in sheet XML")
            worksheet.children().remove(columnElements.first())
            return
        }

        if (columnElements.isEmpty()) {
            val sheetData = xmlFile.getElementsByTag("sheetData").first()
                ?: damagedExcel("Missing <sheetData> element in sheet XML")
            sheetData.before(Element("cols"))
        }

        val columns = xmlFile.getElementsByTag("cols").first()
            ?: damagedExcel("Missing <cols> element in sheet XML")
        columns.children().clear()

        val autoFits = sheetObject.getColumnAutoFits
        val customWidths = sheetObject.getColumnWidths

        val columnCount = max(
            if (autoFits.isEmpty()) 0 else autoFits.keys.max() + 1,
            if (customWidths.isEmpty()) 0 else customWidths.keys.max() + 1,
        )

        val defaultColumnWidth = sheetObject.defaultColumnWidth ?: EXCEL_DEFAULT_COLUMN_WIDTH

        for (index in 0 until columnCount) {
            val width = when {
                autoFits.containsKey(index) && !customWidths.containsKey(index) ->
                    calcAutoFitColumnWidth(sheetObject, index)
                customWidths.containsKey(index) -> customWidths[index]!!
                else -> defaultColumnWidth
            }
            addNewColumn(columns, index, index, width)
        }
    }

    private fun setRows(sheetName: String, sheetObject: Sheet) {
        val customHeights = sheetObject.getRowHeights

        for (rowIndex in 0 until sheetObject.maxRows) {
            val height = customHeights[rowIndex]

            if (sheetObject.sheetData[rowIndex] == null) continue

            val foundRow = createNewRow(
                excel.sheets[sheetName]!! as Element,
                rowIndex,
                height,
            )
            for (columnIndex in 0 until sheetObject.maxColumns) {
                val data = sheetObject.sheetData[rowIndex]!![columnIndex] ?: continue
                updateCell(sheetName, foundRow, columnIndex, rowIndex, data.value, data.cellStyle?.numberFormat)
            }
        }
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
        if (position == 0) return true

        val sheets = excel.xmlFiles["xl/workbook.xml"]!!.getElementsByTag("sheets").first()
        elementFound.remove()
        sheets?.prependChildren(listOf(elementFound))

        return excel.getDefaultSheetInternal() == sheetName
    }

    private fun setHeaderFooter(sheetName: String) {
        val sheet = excel.sheetMap[sheetName] ?: return
        val xmlFile = excel.xmlFiles[excel.xmlSheetId[sheetName]] ?: return
        val worksheetEl = xmlFile.getElementsByTag("worksheet").first()
            ?: damagedExcel("Missing <worksheet> element in sheet XML")

        val existing = worksheetEl.getElementsByTag("headerFooter").toList()
        if (existing.isNotEmpty()) worksheetEl.children().remove(existing.first())

        sheet.headerFooter?.let { worksheetEl.appendChild(it.toXmlElement()) }
    }

    private fun setMerge() {
        selfCorrectSpanMap(excel)
        excel.mergeChangeLook.forEach { s ->
            val sheetObj = excel.sheetMap[s] ?: return@forEach
            if (sheetObj.spanList.isEmpty()) return@forEach
            val xmlSheetPath = excel.xmlSheetId[s] ?: return@forEach
            val xmlDoc = excel.xmlFiles[xmlSheetPath] ?: return@forEach

            val iterMerge = xmlDoc.getElementsByTag("mergeCells").toList()
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
                sheetDataEl.after(newMerge)
                newMerge
            }

            val spannedItems = sheetObj.spannedItems.toList()

            val countAttr = mergeElement.getElementsByAttribute("count").first()
            if (countAttr == null) {
                mergeElement.attributes().add("count", spannedItems.size.toString())
            } else {
                countAttr.value(spannedItems.size.toString())
            }

            mergeElement.children().clear()
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

            val sheetViewsIter = xmlDoc.getElementsByTag("sheetViews").toList()
            val sheetViewEl = Element(
                "sheetView",
                buildList {
                    if (sheetObject.isRTL) add(Attribute("rightToLeft", "1"))
                    add(Attribute("workbookViewId", "0"))
                }
            )

            if (sheetViewsIter.isNotEmpty()) {
                val sheetViewsEl = sheetViewsIter.first()
                sheetViewsEl.children().clear()
                sheetViewsEl.appendChild(sheetViewEl)
            } else {
                val worksheetEl = xmlDoc.getElementsByTag("worksheet").first()
                    ?: damagedExcel("Missing <worksheet> element in sheet XML")
                worksheetEl.appendChild(
                    Element("sheetViews", emptyList(), mutableListOf(sheetViewEl))
                )
            }
        }
    }

    private fun setSharedStrings() {
        var uniqueCount = 0
        var count = 0

        val shareString = excel.xmlFiles["xl/${excel.sharedStringsTarget}"]!!
            .getElementsByTag("sst").first()

        shareString?.children()?.clear()

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

        excel.sheetMap.forEach { (sheetName, sheetObject) ->
            if (excel.sheets[sheetName] == null) {
                parser.createSheet(sheetName)
            }

            if (excel.sheets[sheetName]?.children()?.isNotEmpty() == true) {
                excel.sheets[sheetName]!!.children().clear()
            }

            val xmlFile = excel.xmlFiles[excel.xmlSheetId[sheetName]] ?: return@forEach

            val defaultRowHeight = sheetObject.defaultRowHeight
            val defaultColumnWidth = sheetObject.defaultColumnWidth

            val worksheetEl = xmlFile.getElementsByTag("worksheet").first()
            var sheetFormatPrEl = worksheetEl?.find { it.nodeName() == "sheetFormatPr" }?.firstOrNull()

            if (sheetFormatPrEl != null) {
                sheetFormatPrEl.clearAttributes()
                if (defaultRowHeight == null && defaultColumnWidth == null) {
                    worksheetEl?.children()?.remove(sheetFormatPrEl)
                    sheetFormatPrEl = null
                }
            } else if (defaultRowHeight != null || defaultColumnWidth != null) {
                sheetFormatPrEl = Element("sheetFormatPr")
                worksheetEl?.prependChildren(listOf(sheetFormatPrEl))
            }

            defaultRowHeight?.let {
                sheetFormatPrEl!!.attributes().add("defaultRowHeight", it.toFixed2())
            }
            defaultColumnWidth?.let {
                sheetFormatPrEl!!.attributes().add("defaultColWidth", it.toFixed2())
            }

            setColumns(sheetObject, xmlFile)
            setRows(sheetName, sheetObject)
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
        value: CellValue?,
        numberFormat: NumFormat?,
    ): Element {
        val cell = createCell(sheet, columnIndex, rowIndex, value, numberFormat)
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

    // endregion
}

// region --- Top-level helper ---

private fun checkPosition(list: List<CellStyle>, cellStyle: CellStyle): Int = list.indexOf(cellStyle)

// endregion