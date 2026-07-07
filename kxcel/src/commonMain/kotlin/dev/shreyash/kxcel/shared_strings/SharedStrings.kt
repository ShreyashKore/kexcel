package dev.shreyash.kxcel.shared_strings

import com.fleeksoft.ksoup.nodes.Attribute
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.nodes.XmlDeclaration
import dev.shreyash.kxcel.parser.Parser
import dev.shreyash.kxcel.sheet.CellStyle
import dev.shreyash.kxcel.sheet.Element
import dev.shreyash.kxcel.utils.Underline
import dev.shreyash.kxcel.utils.toExcelColor

// region --- SharedStringsMaintainer ---

class SharedStringsMaintainer {

    val map: MutableMap<SharedString, IndexingHolder> = mutableMapOf()
    private val mapString: MutableMap<String, SharedString> = mutableMapOf()
    private val list: MutableList<SharedString> = mutableListOf()
    private var index: Int = 0

    fun tryFind(value: String): SharedString? = mapString[value]

    fun addFromString(value: String): SharedString {
        val newSharedString = SharedString(
            node = Element(
                "si",
                Element(
                    "t",
                    listOf(Attribute("xml:space", "preserve")),
                    listOf(TextNode(value)),
                ),
            )
        )

        add(newSharedString, value)
        return newSharedString
    }

    fun add(value: SharedString, key: String) {
        if (map.containsKey(value)) {
            map[value]!!.increaseCount()
        } else {
            mapString[key] = value
            list.add(value)
            map[value] = IndexingHolder(index++)
        }
    }

    fun indexOf(value: SharedString): Int = map[value]?.index ?: -1

    fun value(i: Int): SharedString? = if (i < list.size) list[i] else null

    fun clear() {
        index = 0
        list.clear()
        map.clear()
        mapString.clear()
    }
}

// endregion

// region --- IndexingHolder ---

class IndexingHolder(val index: Int, count: Int = 1) {
    var count: Int = count
        private set

    fun increaseCount() {
        count += 1
    }
}

// endregion

// region --- SharedString ---

class SharedString(val node: Element) {

    private val cachedHashCode: Int = node.toString().hashCode()

    val textSpan: TextSpan
        get() {
            fun getBool(element: Element): Boolean =
                element.attr("val")?.toBooleanStrictOrNull() ?: true

            fun getInt(element: Element): Int =
                element.attr("val")!!.toDouble().toInt()

            var text: String? = null
            var children: MutableList<TextSpan>? = null

            // SharedStringItem
            // https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.sharedstringitem
            check(node.nodeName() == "si")

            for (child in node.childElementsList()) {
                when (child.nodeName()) {
                    // 18.4.12 t (Text)
                    "t" -> {
                        // wholeText() preserves significant whitespace (xml:space="preserve");
                        // text() would collapse/trim it and drop spaces between runs.
                        text = (text ?: "") + child.wholeText()
                    }

                    // 18.4.4 r (Rich Text Run)
                    "r" -> {
                        var style = CellStyle()
                        for (runChild in child.childElementsList()) {
                            when (runChild.tagName()) {
                                // 18.4.5 rPr (RunProperties)
                                "rPr" -> {
                                    for (runProperty in runChild.childElementsList()) {
                                        when (runProperty.tagName()) {
                                            "b"      -> style = style.copyWith(boldVal = getBool(runProperty))
                                            "i"      -> style = style.copyWith(italicVal = getBool(runProperty))
                                            "u"      -> style = style.copyWith(
                                                underlineVal = if (runProperty.attr("val") == "double")
                                                    Underline.Double else Underline.Single
                                            )
                                            "sz"     -> style = style.copyWith(fontSizeVal = getInt(runProperty))
                                            "rFont"  -> style = style.copyWith(
                                                fontFamilyVal = runProperty.attr("val")
                                            )
                                            "color"  -> style = style.copyWith(
                                                fontColorHexVal = runProperty.attr("rgb").toExcelColor()
                                            )
                                        }
                                    }
                                }
                                // 18.4.12 t (Text)
                                "t" -> {
                                    if (children == null) children = mutableListOf()
                                    children!!.add(TextSpan(text = runChild.wholeText(), style = style))
                                }
                            }
                        }
                    }

                    // 18.4.6 rPh (Phonetic Run) — ignored
                    "rPh" -> Unit
                }
            }

            return TextSpan(text = text, children = children)
        }

    val stringValue: String
        get() {
            val buffer = StringBuilder()
            node.getElementsByTag("t").forEach { child ->
                val parentLocal = child.parentElement()?.tagName()
                if (parentLocal == null || parentLocal != "rPh") {
                    buffer.append(Parser.parseValue(child))
                }
            }
            return buffer.toString()
        }

    fun matches(value: String): Boolean = value.isNotEmpty() && value == stringValue

    override fun hashCode(): Int = cachedHashCode

    override fun equals(other: Any?): Boolean {
        return other is SharedString &&
                other.hashCode() == cachedHashCode &&
                other.stringValue == stringValue
    }

    override fun toString(): String = stringValue
}

// endregion

// region --- TextSpan ---

data class TextSpan(
    val text: String? = null,
    val children: List<TextSpan>? = null,
    val style: CellStyle? = null,
) {
    override fun toString(): String {
        val sb = StringBuilder()
        text?.let { sb.append(it) }
        children?.forEach { sb.append(it.toString()) }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextSpan) return false
        return other.text == text &&
                other.style == style &&
                other.children == children
    }

    override fun hashCode(): Int {
        var result = text?.hashCode() ?: 0
        result = 31 * result + (style?.hashCode() ?: 0)
        result = 31 * result + (children?.hashCode() ?: 0)
        return result
    }
}

// endregion