package com.gyanoba.kexcel.sheet

import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.FontScheme
import com.gyanoba.kexcel.utils.HorizontalAlign
import com.gyanoba.kexcel.utils.HorizontalAlign.*
import com.gyanoba.kexcel.utils.TextWrapping
import com.gyanoba.kexcel.utils.Underline
import com.gyanoba.kexcel.utils.Underline.*
import com.gyanoba.kexcel.utils.VerticalAlign
import com.gyanoba.kexcel.utils.isColorAppropriate
import com.gyanoba.kexcel.utils.toExcelColor


/**
 * Styling class for cells.
 */
public class CellStyle(
    fontColorHex: ExcelColor = ExcelColor.black,
    backgroundColorHex: ExcelColor = ExcelColor.none,
    fontSize: Int? = null,
    fontFamily: String? = null,
    fontScheme: FontScheme? = null,
    horizontalAlign: HorizontalAlign = Left,
    verticalAlign: VerticalAlign = VerticalAlign.Bottom,
    textWrapping: TextWrapping? = null,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Underline = None,
    rotation: Int = 0,
    leftBorder: Border? = null,
    rightBorder: Border? = null,
    topBorder: Border? = null,
    bottomBorder: Border? = null,
    diagonalBorder: Border? = null,
    diagonalBorderUp: Boolean = false,
    diagonalBorderDown: Boolean = false,
    public var numberFormat: NumFormat = NumFormat.standard_0,
) {
    // region --- Backing fields ---

    private var _fontColorHex: String = isColorAppropriate(fontColorHex.colorHex)
    private var _backgroundColorHex: String = isColorAppropriate(backgroundColorHex.colorHex)
    private var _fontFamily: String? = fontFamily
    private var _fontScheme: FontScheme = fontScheme ?: FontScheme.Unset
    private var _horizontalAlign: HorizontalAlign = horizontalAlign
    private var _verticalAlign: VerticalAlign = verticalAlign
    private var _textWrapping: TextWrapping? = textWrapping
    private var _bold: Boolean = bold
    private var _italic: Boolean = italic
    private var _underline: Underline = underline
    private var _fontSize: Int? = fontSize
    private var _rotation: Int = rotation
    private var _leftBorder: Border = leftBorder ?: Border()
    private var _rightBorder: Border = rightBorder ?: Border()
    private var _topBorder: Border = topBorder ?: Border()
    private var _bottomBorder: Border = bottomBorder ?: Border()
    private var _diagonalBorder: Border = diagonalBorder ?: Border()
    private var _diagonalBorderUp: Boolean = diagonalBorderUp
    private var _diagonalBorderDown: Boolean = diagonalBorderDown

    // endregion

    // region --- copyWith ---

    public fun copyWith(
        fontColorHexVal: ExcelColor? = null,
        backgroundColorHexVal: ExcelColor? = null,
        fontFamilyVal: String? = null,
        fontSchemeVal: FontScheme? = null,
        horizontalAlignVal: HorizontalAlign? = null,
        verticalAlignVal: VerticalAlign? = null,
        textWrappingVal: TextWrapping? = null,
        boldVal: Boolean? = null,
        italicVal: Boolean? = null,
        underlineVal: Underline? = null,
        fontSizeVal: Int? = null,
        rotationVal: Int? = null,
        leftBorderVal: Border? = null,
        rightBorderVal: Border? = null,
        topBorderVal: Border? = null,
        bottomBorderVal: Border? = null,
        diagonalBorderVal: Border? = null,
        diagonalBorderUpVal: Boolean? = null,
        diagonalBorderDownVal: Boolean? = null,
        numberFormat: NumFormat? = null,
    ): CellStyle = CellStyle(
        fontColorHex = fontColorHexVal ?: _fontColorHex.toExcelColor(),
        backgroundColorHex = backgroundColorHexVal ?: _backgroundColorHex.toExcelColor(),
        fontFamily = fontFamilyVal ?: _fontFamily,
        fontScheme = fontSchemeVal ?: _fontScheme,
        horizontalAlign = horizontalAlignVal ?: _horizontalAlign,
        verticalAlign = verticalAlignVal ?: _verticalAlign,
        textWrapping = textWrappingVal ?: _textWrapping,
        bold = boldVal ?: _bold,
        italic = italicVal ?: _italic,
        underline = underlineVal ?: _underline,
        fontSize = fontSizeVal ?: _fontSize,
        rotation = rotationVal ?: _rotation,
        leftBorder = leftBorderVal ?: _leftBorder,
        rightBorder = rightBorderVal ?: _rightBorder,
        topBorder = topBorderVal ?: _topBorder,
        bottomBorder = bottomBorderVal ?: _bottomBorder,
        diagonalBorder = diagonalBorderVal ?: _diagonalBorder,
        diagonalBorderUp = diagonalBorderUpVal ?: _diagonalBorderUp,
        diagonalBorderDown = diagonalBorderDownVal ?: _diagonalBorderDown,
        numberFormat = numberFormat ?: this.numberFormat,
    )

    // endregion

    // region --- Font color ---

    /** Get font color. */
    public var fontColor: ExcelColor
        get() = _fontColorHex.toExcelColor()
        set(value) { _fontColorHex = isColorAppropriate(value.colorHex) }

    // endregion

    // region --- Background color ---

    /** Get background color. */
    public var backgroundColor: ExcelColor
        get() = _backgroundColorHex.toExcelColor()
        set(value) { _backgroundColorHex = isColorAppropriate(value.colorHex) }

    // endregion

    // region --- Alignment ---

    /** Get/set horizontal alignment. */
    public var horizontalAlignment: HorizontalAlign
        get() = _horizontalAlign
        set(value) { _horizontalAlign = value }

    /** Get/set vertical alignment. */
    public var verticalAlignment: VerticalAlign
        get() = _verticalAlign
        set(value) { _verticalAlign = value }

    // endregion

    // region --- Text wrapping ---

    /** Get/set text wrapping. */
    public var wrap: TextWrapping?
        get() = _textWrapping
        set(value) { _textWrapping = value }

    // endregion

    // region --- Font family / scheme / size ---

    /** Get/set font family. */
    public var fontFamily: String?
        get() = _fontFamily
        set(value) { _fontFamily = value }

    /** Get/set font scheme. */
    public var fontScheme: FontScheme
        get() = _fontScheme
        set(value) { _fontScheme = value }

    /** Get/set font size. */
    public var fontSize: Int?
        get() = _fontSize
        set(value) { _fontSize = value }

    // endregion

    // region --- Rotation ---

    /**
     * Get/set rotation. Valid range is [-90, 90].
     * Out-of-range values are clamped to 0.
     * Negative values are stored as `abs(value) + 90` to match Excel's encoding.
     */
    public var rotation: Int
        get() = _rotation
        set(value) {
            var r = value
            if (r > 90 || r < -90) r = 0
            if (r < 0) r = -r + 90
            _rotation = r
        }

    // endregion

    // region --- Text style ---

    /** Get/set underline. */
    public var underline: Underline
        get() = _underline
        set(value) { _underline = value }

    /** Get/set bold. */
    public var isBold: Boolean
        get() = _bold
        set(value) { _bold = value }

    /** Get/set italic. */
    public var isItalic: Boolean
        get() = _italic
        set(value) { _italic = value }

    // endregion

    // region --- Borders ---

    /** Get/set left border. */
    public var leftBorder: Border
        get() = _leftBorder
        set(value) { _leftBorder = value }

    /** Get/set right border. */
    public var rightBorder: Border
        get() = _rightBorder
        set(value) { _rightBorder = value }

    /** Get/set top border. */
    public var topBorder: Border
        get() = _topBorder
        set(value) { _topBorder = value }

    /** Get/set bottom border. */
    public var bottomBorder: Border
        get() = _bottomBorder
        set(value) { _bottomBorder = value }

    /** Get/set diagonal border. */
    public var diagonalBorder: Border
        get() = _diagonalBorder
        set(value) { _diagonalBorder = value }

    /** Get/set diagonal border up. */
    public var diagonalBorderUp: Boolean
        get() = _diagonalBorderUp
        set(value) { _diagonalBorderUp = value }

    /** Get/set diagonal border down. */
    public var diagonalBorderDown: Boolean
        get() = _diagonalBorderDown
        set(value) { _diagonalBorderDown = value }

    // endregion

    // region --- Equatable ---

    private fun props(): List<Any?> = listOf(
        _bold,
        _rotation,
        _italic,
        _underline,
        _fontSize,
        _fontFamily,
        _fontScheme,
        _textWrapping,
        _verticalAlign,
        _horizontalAlign,
        _fontColorHex,
        _backgroundColorHex,
        _leftBorder,
        _rightBorder,
        _topBorder,
        _bottomBorder,
        _diagonalBorder,
        _diagonalBorderUp,
        _diagonalBorderDown,
        numberFormat,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CellStyle) return false
        return props() == other.props()
    }

    override fun hashCode(): Int = props().hashCode()

    // endregion
}