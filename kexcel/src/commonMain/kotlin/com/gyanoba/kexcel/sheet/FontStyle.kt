package com.gyanoba.kexcel.sheet

import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.FontScheme
import com.gyanoba.kexcel.utils.Underline
import com.gyanoba.kexcel.utils.isColorAppropriate
import com.gyanoba.kexcel.utils.toExcelColor

/**
 * Internal styling class holding font properties parsed from the Excel styles XML.
 */
internal class FontStyle(
    fontColorHex: ExcelColor? = ExcelColor.black,
    var fontSize: Int? = null,
    var fontFamily: String? = null,
    var fontScheme: FontScheme = FontScheme.Unset,
    var isBold: Boolean = false,
    var underline: Underline = Underline.None,
    var isItalic: Boolean = false,
) {
    var fontColorHex: ExcelColor = if (fontColorHex != null)
        isColorAppropriate(fontColorHex.colorHex).toExcelColor()
    else
        ExcelColor.black

    private fun props(): List<Any?> = listOf(isBold, isItalic, fontSize, underline, fontFamily, fontColorHex)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FontStyle) return false
        return props() == other.props()
    }

    override fun hashCode(): Int = props().hashCode()
}