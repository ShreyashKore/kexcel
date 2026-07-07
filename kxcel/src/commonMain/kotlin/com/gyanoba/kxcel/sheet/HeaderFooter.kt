package com.gyanoba.kxcel.sheet

import com.fleeksoft.ksoup.nodes.Attribute
import com.fleeksoft.ksoup.nodes.Attributes
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.parser.Tag

public class HeaderFooter(
    public var alignWithMargins: Boolean?,
    public var differentFirst: Boolean?,
    public var differentOddEven: Boolean?,
    public var scaleWithDoc: Boolean?,
    public var evenFooter: String?,
    public var evenHeader: String?,
    public var firstFooter: String?,
    public var firstHeader: String?,
    public var oddFooter: String?,
    public var oddHeader: String?,
) {

    internal fun toXmlElement(): Element {
        val attributes = mutableListOf<Attribute>()
        if (alignWithMargins != null) {
            attributes.add(
                Attribute(
                    "alignWithMargins", alignWithMargins.toString()
                )
            )
        }
        if (differentFirst != null) {
            attributes.add(
                Attribute("differentFirst", differentFirst.toString())
            )
        }
        if (differentOddEven != null) {
            attributes.add(
                Attribute(
                    "differentOddEven", differentOddEven.toString()
                )
            )
        }
        if (scaleWithDoc != null) {
            attributes
                .add(Attribute("scaleWithDoc", scaleWithDoc.toString()))
        }

        val children = mutableListOf<Element>()
        if (evenHeader != null) {
            children.add(
                Element(
                    "evenHeader", TextNode(evenHeader!!.simplifyText())
                )
            )
        }
        if (evenFooter != null) {
            children.add(
                Element(
                    "evenFooter", TextNode(evenFooter!!.simplifyText())
                )
            )
        }
        if (firstHeader != null) {
            children.add(
                Element(
                    "firstHeader", TextNode(firstHeader!!.simplifyText())
                )
            )
        }
        if (firstFooter != null) {
            children.add(
                Element(
                    "firstFooter", TextNode(firstFooter!!.simplifyText())
                )
            )
        }
        if (oddHeader != null) {
            children.add(
                Element(
                    "oddHeader", TextNode(oddHeader!!.simplifyText())
                )
            )
        }
        if (oddFooter != null) {
            children.add(
                Element(
                    "oddFooter", TextNode(oddFooter!!.simplifyText())
                )
            )
        }

        return Element(Tag("headerFooter"), null, Attributes(attributes)).apply {
            addChildren(*children.toTypedArray())
        }
    }

    internal companion object {
        fun fromXmlElement(headerFooterElement: Element): HeaderFooter {
            return HeaderFooter(
                alignWithMargins =
                    headerFooterElement.attr("alignWithMargins").takeIf { it.isNotEmpty() }?.parseBool(),
                differentFirst =
                    headerFooterElement.attr("differentFirst").takeIf { it.isNotEmpty() }?.parseBool(),
                differentOddEven =
                    headerFooterElement.attr("differentOddEven").takeIf { it.isNotEmpty() }?.parseBool(),
                scaleWithDoc =
                    headerFooterElement.attr("scaleWithDoc").takeIf { it.isNotEmpty() }?.parseBool(),
                evenHeader = headerFooterElement.getElementsByTag("evenHeader").first()?.text(),
                evenFooter = headerFooterElement.getElementsByTag("evenFooter").first()?.text(),
                firstHeader = headerFooterElement.getElementsByTag("firstHeader").first()?.text(),
                firstFooter = headerFooterElement.getElementsByTag("firstFooter").first()?.text(),
                oddFooter = headerFooterElement.getElementsByTag("oddFooter").first()?.text(),
                oddHeader = headerFooterElement.getElementsByTag("oddHeader").first()?.text()
            )
        }
    }
}

internal fun String.parseBool(): Boolean {
    var value = lowercase()
    if (value == "true" || value == "1") {
        return true
    } else if (value == "false" || value == "0") {
        return false
    }

    throw IllegalStateException("\"$this\" can not be parsed to boolean.")
}

internal fun String.simplifyText(): String {
    var value = this.replace("&amp", "&")
    value = value.replace("amp", "&")
    value = value.replace("&", "&amp;")
    value = value.replace("\"", "&quot;")
    return value
}

internal fun Element(tag: String, vararg children: Node): Element = Element(tag).apply {
    addChildren(*children)
}

internal fun Attributes(list: List<Attribute>): Attributes = Attributes().apply {
    list.forEach { add(it.key, it.value) }
}

internal fun Element(tag: String, attributes: List<Attribute>, children: List<Node> = emptyList()): Element = Element(Tag(tag), null, Attributes(attributes)).apply {
    addChildren(*children.toTypedArray())
}

