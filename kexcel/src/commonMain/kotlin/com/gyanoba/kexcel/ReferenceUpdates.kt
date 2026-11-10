package com.gyanoba.kexcel

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.gyanoba.kexcel.sheet.CellSource
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.utils.StructuralEdit
import com.gyanoba.kexcel.utils.adjustFormulaForEdit
import com.gyanoba.kexcel.utils.adjustReferenceForEdit
import com.gyanoba.kexcel.utils.adjustReferenceListForEdit
import com.gyanoba.kexcel.utils.parseXmlPart
import com.gyanoba.kexcel.utils.resolveRelationshipTarget
import com.gyanoba.kexcel.utils.setAttribute

/** Elements of a worksheet part whose text is a formula. `xm:f` is the form used inside `<extLst>`. */
private val FORMULA_ELEMENTS = listOf("formula1", "formula2", "formula", "xm:f")

/** Element → the attribute that holds a single reference into its own sheet. */
private val REFERENCE_ATTRIBUTES = listOf("hyperlink" to "ref", "autoFilter" to "ref", "sortState" to "ref", "sortCondition" to "ref")

/** Element → the attribute that holds a space-separated list of references into its own sheet. */
private val REFERENCE_LIST_ATTRIBUTES = listOf(
    "dataValidation" to "sqref", "conditionalFormatting" to "sqref", "ignoredError" to "sqref", "protectedRange" to "sqref",
)

/**
 * Makes everything that names a cell of [sheet] by address follow a row or column that
 * was just inserted or removed there.
 *
 * The cells themselves, with their styles, merges and sizes, are moved by [Sheet]. This
 * moves what points at them, which Excel does as part of the same operation and POI does
 * in `XSSFRowShifter` / `XSSFColumnShifter`:
 *
 * - formulas, on this sheet and on every other one;
 * - defined names (named ranges, print areas);
 * - in the worksheet part: hyperlinks, data validations, conditional formats, the
 *   autofilter and sort state, ignored errors and protected ranges;
 * - cell comments, in the comments part and its legacy drawing.
 *
 * What still does not follow, because it lives in parts Kexcel does not read: tables,
 * images and charts (their anchors), pivot tables and threaded comments.
 */
internal fun Excel.moveReferences(sheet: Sheet, edit: StructuralEdit) {
    // One sheet, unless it is linked under several names.
    val editedNames = sheetMap.filterValues { it === sheet }.keys
    if (editedNames.isEmpty()) return

    sheetMap.values.toSet().forEach { other ->
        val own = other === sheet
        other.sheetData.values.forEach { row ->
            row.values.forEach { data ->
                val value = data.value as? FormulaCellValue ?: return@forEach
                val source = value.source
                // An error literal is not an expression.
                if (source != null && !source.hasFormula) return@forEach

                val formula = adjustFormulaForEdit(value.formula, edit, editedNames, own)
                // The range an array formula fills (`<f t="array" ref="C1:C3">`) is on its own sheet.
                val range = source?.formulaAttributes?.get("ref")
                val movedRange = if (own && range != null) adjustReferenceForEdit(range, edit) ?: range else range
                if (formula == value.formula && movedRange == range) return@forEach

                data._value = FormulaCellValue(formula).also {
                    it.source = source?.let { old ->
                        CellSource(
                            type = old.type,
                            rawValue = old.rawValue,
                            hasFormula = true,
                            formulaAttributes = if (movedRange != null) old.formulaAttributes + ("ref" to movedRange) else old.formulaAttributes,
                            cellAttributes = old.cellAttributes,
                        )
                    }
                }
            }
        }
    }

    // A defined name always says which sheet it means.
    xmlFiles["xl/workbook.xml"]?.getElementsByTag("definedName")?.forEach { definedName ->
        val text = definedName.wholeOwnText()
        val adjusted = adjustFormulaForEdit(text, edit, editedNames, inEditedSheet = false)
        if (adjusted != text) definedName.text(adjusted)
    }

    sheetMap.forEach { (name, other) ->
        val path = xmlSheetId[name] ?: return@forEach
        val worksheet = xmlFiles[path] ?: return@forEach
        val own = other === sheet

        FORMULA_ELEMENTS.forEach { tag ->
            worksheet.getElementsByTag(tag).forEach { element ->
                val text = element.wholeOwnText()
                val adjusted = adjustFormulaForEdit(text, edit, editedNames, own)
                if (adjusted != text) element.text(adjusted)
            }
        }
        if (own) {
            moveWorksheetRanges(worksheet, edit)
            moveComments(path, edit)
        }
    }
}

private fun moveWorksheetRanges(worksheet: Document, edit: StructuralEdit) {
    for ((tag, attribute) in REFERENCE_ATTRIBUTES) {
        worksheet.getElementsByTag(tag).toList().forEach { element ->
            val reference = element.attr(attribute)
            if (reference.isEmpty()) return@forEach
            val adjusted = adjustReferenceForEdit(reference, edit)
            if (adjusted == null) element.remove() else if (adjusted != reference) element.setAttribute(attribute, adjusted)
        }
    }
    for ((tag, attribute) in REFERENCE_LIST_ATTRIBUTES) {
        worksheet.getElementsByTag(tag).toList().forEach { element ->
            val references = element.attr(attribute)
            if (references.isEmpty()) return@forEach
            val adjusted = adjustReferenceListForEdit(references, edit)
            // Nothing left for it to apply to.
            if (adjusted.isEmpty()) element.remove() else if (adjusted != references) element.setAttribute(attribute, adjusted)
        }
    }
    worksheet.getElementsByTag("xm:sqref").toList().forEach { element ->
        val references = element.wholeOwnText()
        val adjusted = adjustReferenceListForEdit(references, edit)
        if (adjusted.isEmpty()) element.parent()?.remove() else if (adjusted != references) element.text(adjusted)
    }

    // A container left without children is not valid, and its count has to match.
    for (container in listOf("hyperlinks", "dataValidations", "ignoredErrors", "protectedRanges")) {
        worksheet.getElementsByTag(container).toList().forEach { element ->
            val children = element.children().size
            if (children == 0) element.remove() else if (element.hasAttr("count")) element.setAttribute("count", children.toString())
        }
    }
}

/**
 * Moves the cell comments of the worksheet at [worksheetPath]. A comment is stored twice:
 * its text in the comments part, keyed by cell (`<comment ref="A3">`), and its box in the
 * legacy VML drawing, which names the cell again as zero-based `<x:Row>` and `<x:Column>`.
 * POI updates both in `XSSFComment.setRow` / `setColumn`.
 */
private fun Excel.moveComments(worksheetPath: String, edit: StructuralEdit) {
    val folder = worksheetPath.substringBeforeLast('/', missingDelimiterValue = "")
    val relationshipsPath = "$folder/_rels/${worksheetPath.substringAfterLast('/')}.rels"
    val relationships = archive.findFile(relationshipsPath) ?: return
    relationships.decompress()

    parseXmlPart(relationships.content.decodeToString()).getElementsByTag("Relationship").forEach { relationship ->
        if (relationship.attr("TargetMode") == "External") return@forEach
        val type = relationship.attr("Type")
        val isComments = type.endsWith("/comments")
        val isLegacyDrawing = type.endsWith("/vmlDrawing")
        if (!isComments && !isLegacyDrawing) return@forEach

        val path = resolveRelationshipTarget(folder, relationship.attr("Target"))
        val part = xmlFiles[path] ?: run {
            val file = archive.findFile(path) ?: return@forEach
            file.decompress()
            parseXmlPart(file.content.decodeToString()).also { xmlFiles[path] = it }
        }

        if (isComments) {
            part.getElementsByTag("comment").toList().forEach { comment ->
                val reference = comment.attr("ref")
                val adjusted = adjustReferenceForEdit(reference, edit)
                if (adjusted == null) comment.remove() else if (adjusted != reference) comment.setAttribute("ref", adjusted)
            }
        } else {
            part.getElementsByTag("x:ClientData").toList().forEach { clientData ->
                if (clientData.attr("ObjectType") != "Note") return@forEach
                val tag = if (edit.axis == com.gyanoba.kexcel.utils.Axis.Row) "x:Row" else "x:Column"
                val position = clientData.children().firstOrNull { it.tagName() == tag } ?: return@forEach
                val index = position.wholeOwnText().trim().toIntOrNull() ?: return@forEach
                val moved = edit.move(index + 1)
                // The box goes with its comment.
                if (moved == null) noteShapeOf(clientData).remove() else if (moved - 1 != index) position.text((moved - 1).toString())
            }
        }
    }
}

/** The `<v:shape>` a note's `<x:ClientData>` belongs to. */
private fun noteShapeOf(clientData: Element): Element = clientData.parent() ?: clientData
