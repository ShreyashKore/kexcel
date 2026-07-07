package dev.shreyash.kxcel.sheet

import dev.shreyash.kxcel.utils.ExcelColor
import dev.shreyash.kxcel.utils.isColorAppropriate


public enum class BorderStyle(public val style: String) {
    None("none"),
    DashDot("dashDot"),
    DashDotDot("dashDotDot"),
    Dashed("dashed"),
    Dotted("dotted"),
    Double("double"),
    Hair("hair"),
    Medium("medium"),
    MediumDashDot("mediumDashDot"),
    MediumDashDotDot("mediumDashDotDot"),
    MediumDashed("mediumDashed"),
    SlantDashDot("slantDashDot"),
    Thick("thick"),
    Thin("thin");
}

public fun getBorderStyleByName(name: String): BorderStyle? =
    BorderStyle.entries.firstOrNull {
        it.name.equals(name, ignoreCase = true)
    }

// endregion

// region --- Border ---

public class Border(
    borderStyle: BorderStyle? = null,
    borderColorHex: ExcelColor? = null,
) {
    public val borderStyle: BorderStyle? = if (borderStyle == BorderStyle.None) null else borderStyle
    public val borderColorHex: String? = borderColorHex?.colorHex?.let { isColorAppropriate(it) }

    override fun toString(): String =
        "Border(borderStyle: $borderStyle, borderColorHex: $borderColorHex)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Border) return false
        return borderStyle == other.borderStyle && borderColorHex == other.borderColorHex
    }

    override fun hashCode(): Int {
        var h = borderStyle?.hashCode() ?: 0
        h = 31 * h + (borderColorHex?.hashCode() ?: 0)
        return h
    }
}

// endregion

// region --- BorderSet ---

internal class BorderSet(
    val leftBorder: Border,
    val rightBorder: Border,
    val topBorder: Border,
    val bottomBorder: Border,
    val diagonalBorder: Border,
    val diagonalBorderUp: Boolean,
    val diagonalBorderDown: Boolean,
) {
    fun copyWith(
        leftBorder: Border? = null,
        rightBorder: Border? = null,
        topBorder: Border? = null,
        bottomBorder: Border? = null,
        diagonalBorder: Border? = null,
        diagonalBorderUp: Boolean? = null,
        diagonalBorderDown: Boolean? = null,
    ): BorderSet = BorderSet(
        leftBorder = leftBorder ?: this.leftBorder,
        rightBorder = rightBorder ?: this.rightBorder,
        topBorder = topBorder ?: this.topBorder,
        bottomBorder = bottomBorder ?: this.bottomBorder,
        diagonalBorder = diagonalBorder ?: this.diagonalBorder,
        diagonalBorderUp = diagonalBorderUp ?: this.diagonalBorderUp,
        diagonalBorderDown = diagonalBorderDown ?: this.diagonalBorderDown,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BorderSet) return false
        return leftBorder == other.leftBorder &&
                rightBorder == other.rightBorder &&
                topBorder == other.topBorder &&
                bottomBorder == other.bottomBorder &&
                diagonalBorder == other.diagonalBorder &&
                diagonalBorderUp == other.diagonalBorderUp &&
                diagonalBorderDown == other.diagonalBorderDown
    }

    override fun hashCode(): Int {
        var h = leftBorder.hashCode()
        h = 31 * h + rightBorder.hashCode()
        h = 31 * h + topBorder.hashCode()
        h = 31 * h + bottomBorder.hashCode()
        h = 31 * h + diagonalBorder.hashCode()
        h = 31 * h + diagonalBorderUp.hashCode()
        h = 31 * h + diagonalBorderDown.hashCode()
        return h
    }
}

// endregion