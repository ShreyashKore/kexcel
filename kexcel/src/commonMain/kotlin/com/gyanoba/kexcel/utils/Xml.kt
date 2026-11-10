package com.gyanoba.kexcel.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.CDataNode
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

internal const val NS_SPREADSHEETML = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
internal const val NS_PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships"
internal const val NS_CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types"

/** Namespaces whose elements the rest of the library looks up by their unprefixed name. */
private val UNPREFIXED_NAMESPACES = setOf(NS_SPREADSHEETML, NS_PACKAGE_RELATIONSHIPS, NS_CONTENT_TYPES)

/**
 * Parses one XML part of the package.
 *
 * Every lookup in the library is by plain tag name (`sheet`, `row`, `c`, `Relationship`, …),
 * which is how Excel writes them: the part's namespace is the default namespace. It is just
 * as valid to bind that namespace to a prefix — `<x:workbook xmlns:x="…/main">` — and the
 * OpenXML SDK and Python's ElementTree do. Left alone, none of the lookups match and the
 * workbook reads as empty, so such a part is rewritten here to the default-namespace form.
 * The document means the same thing either way (XML Namespaces §6.2).
 */
internal fun parseXmlPart(text: String): Document {
    // A UTF-8 byte order mark decodes to U+FEFF, which is not part of the document.
    // Line ends are normalised before parsing, as XML requires (§2.11): a literal CRLF or
    // CR is a line feed. ksoup does not do this, and doing it here rather than on the
    // parsed text keeps a carriage return that was written as `&#13;`, which is data.
    val document = Ksoup.parseXml(text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n'))
    moveRootNamespaceToDefault(document)
    return document
}

private fun moveRootNamespaceToDefault(document: Document) {
    val root = document.children().firstOrNull() ?: return
    val prefix = root.tagName().substringBefore(':', missingDelimiterValue = "")
    if (prefix.isEmpty()) return

    val uri = root.attr("xmlns:$prefix")
    if (uri !in UNPREFIXED_NAMESPACES) return
    // Unprefixed names already mean something else in this document; renaming would
    // merge two vocabularies.
    val defaultNamespace = root.attr("xmlns")
    if (defaultNamespace.isNotEmpty() && defaultNamespace != uri) return

    val marker = "$prefix:"
    root.getAllElements().forEach { element ->
        val name = element.tagName()
        if (name.startsWith(marker)) element.tagName(name.substring(marker.length))
    }
    // The prefix stays declared: an attribute or extension element may still use it.
    root.attr("xmlns", uri)
}

/**
 * The relationship id an element points at. The attribute is `id` in the relationships
 * namespace; `r` is only the prefix Excel happens to bind it to.
 */
internal fun relationshipId(node: Element): String? {
    node.attr("r:id").takeIf { it.isNotEmpty() }?.let { return it }
    return node.attributes().firstOrNull { it.key.endsWith(":id") }?.value?.takeIf { it.isNotEmpty() }
}

/**
 * The prefix [document]'s root binds to the officeDocument relationships namespace,
 * declaring `r` on the root when there is none.
 */
internal fun relationshipsPrefix(document: Document): String {
    val root = document.children().firstOrNull() ?: return "r"
    root.attributes().firstOrNull { it.key.startsWith("xmlns:") && it.value == RELATIONSHIPS }
        ?.let { return it.key.removePrefix("xmlns:") }
    root.attr("xmlns:r", RELATIONSHIPS)
    return "r"
}

/** The order CT_Workbook requires of the children of `<workbook>` (§18.2.27). */
internal val WORKBOOK_CHILD_ORDER = listOf(
    "fileVersion", "fileSharing", "workbookPr", "workbookProtection", "bookViews", "sheets",
    "functionGroups", "externalReferences", "definedNames", "calcPr", "oleSize",
    "customWorkbookViews", "pivotCaches", "smartTagPr", "smartTagTypes", "webPublishing",
    "fileRecoveryPr", "webPublishObjects", "extLst",
)

/** The order CT_Worksheet requires of the children of `<worksheet>` (§18.3.1.99). */
internal val WORKSHEET_CHILD_ORDER = listOf(
    "sheetPr", "dimension", "sheetViews", "sheetFormatPr", "cols", "sheetData",
    "sheetCalcPr", "sheetProtection", "protectedRanges", "scenarios", "autoFilter",
    "sortState", "dataConsolidate", "customSheetViews", "mergeCells", "phoneticPr",
    "conditionalFormatting", "dataValidations", "hyperlinks", "printOptions",
    "pageMargins", "pageSetup", "headerFooter", "rowBreaks", "colBreaks",
    "customProperties", "cellWatches", "ignoredErrors", "smartTags", "drawing",
    "legacyDrawing", "legacyDrawingHF", "drawingHF", "picture", "oleObjects", "controls",
    "webPublishItems", "tableParts", "extLst",
)

/**
 * Adds [child] to [parent] at the position [order] gives its tag. The content models of
 * `<workbook>` and `<worksheet>` are sequences: the same children in another order are a
 * schema violation, which Excel reports as a damaged file.
 */
internal fun insertInSequence(parent: Element, child: Element, order: List<String>) {
    val position = order.indexOf(child.tagName())
    val follower = parent.children().firstOrNull { order.indexOf(it.tagName()) > position }
    if (follower != null) follower.before(child) else parent.appendChild(child)
}

/**
 * Sets an attribute with its name exactly as given. `Element.attr(name, value)` consults the
 * owner document's parser settings and, for an element that is not attached to an XML
 * document yet, falls back to HTML rules and lower-cases the name (`fontId` → `fontid`).
 */
internal fun Element.setAttribute(name: String, value: String): Element {
    attributes().put(name, value)
    return this
}

/** Removes an attribute by its exact name; see [setAttribute]. */
internal fun Element.dropAttribute(name: String): Element {
    attributes().remove(name)
    return this
}

/**
 * Writes [document] as XML, character for character what the tree holds.
 *
 * ksoup's own serializer leaves tabs, line feeds and carriage returns in attribute values
 * and carriage returns in text as they are. A reader then applies XML's normalisation
 * rules (§2.11, §3.3.3) and sees spaces and line feeds instead: a two-line data validation
 * message or hyperlink tooltip came back as one line, and a carriage return in a cell as a
 * line feed — in parts Kexcel never meant to touch. Those characters are written as
 * character references here, which is the only form that survives.
 */
internal fun serializeXml(document: Document): String {
    val out = StringBuilder()
    document.childNodes().forEach { writeNode(it, out) }
    return out.toString()
}

private fun writeNode(node: Node, out: StringBuilder) {
    when (node) {
        is CDataNode -> out.append("<![CDATA[").append(node.getWholeText()).append("]]>")
        is TextNode -> escapeInto(node.getWholeText(), out, inAttribute = false)
        is Element -> {
            out.append('<').append(node.tagName())
            node.attributes().forEach { attribute ->
                out.append(' ').append(attribute.key).append("=\"")
                escapeInto(attribute.value, out, inAttribute = true)
                out.append('"')
            }
            val children = node.childNodes()
            if (children.isEmpty()) {
                out.append("/>")
            } else {
                out.append('>')
                children.forEach { writeNode(it, out) }
                out.append("</").append(node.tagName()).append('>')
            }
        }
        // The XML declaration, comments and anything else ksoup models as a leaf.
        else -> out.append(node.outerHtml())
    }
}

private fun escapeInto(text: String, out: StringBuilder, inAttribute: Boolean) {
    for (c in text) {
        when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '\r' -> out.append("&#13;")
            '"' -> if (inAttribute) out.append("&quot;") else out.append(c)
            '\n' -> if (inAttribute) out.append("&#10;") else out.append(c)
            '\t' -> if (inAttribute) out.append("&#9;") else out.append(c)
            else -> out.append(c)
        }
    }
}
