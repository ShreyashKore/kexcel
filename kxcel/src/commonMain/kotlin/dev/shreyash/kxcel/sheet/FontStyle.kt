package dev.shreyash.kxcel.sheet

import dev.shreyash.kxcel.utils.ExcelColor
import dev.shreyash.kxcel.utils.FontScheme
import dev.shreyash.kxcel.utils.Underline
import dev.shreyash.kxcel.utils.isColorAppropriate
import dev.shreyash.kxcel.utils.toExcelColor

/**
 * Internal styling class holding font properties parsed from the Excel styles XML.
 */
internal class FontStyle(
    fontColorHex: ExcelColor? = ExcelColor.black,
    fontSize: Int? = null,
    fontFamily: String? = null,
    fontScheme: FontScheme = FontScheme.Unset,
    bold: Boolean = false,
    underline: Underline = Underline.None,
    italic: Boolean = false,
) {
    var fontColorHex: ExcelColor = if (fontColorHex != null)
        isColorAppropriate(fontColorHex.colorHex).toExcelColor()
    else
        ExcelColor.black

    var fontFamily: String? = fontFamily
    var fontScheme: FontScheme = fontScheme
    var isBold: Boolean = bold
    var isItalic: Boolean = italic
    var underline: Underline = underline
    var fontSize: Int? = fontSize

    private fun props(): List<Any?> = listOf(isBold, isItalic, fontSize, underline, fontFamily, fontColorHex)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FontStyle) return false
        return props() == other.props()
    }

    override fun hashCode(): Int = props().hashCode()
}

/**
 * Returns the index of [fontStyle] in [fontStyleList], or -1 if not found.
 */
internal fun fontStyleIndex(fontStyleList: List<FontStyle>, fontStyle: FontStyle): Int =
    fontStyleList.indexOf(fontStyle)