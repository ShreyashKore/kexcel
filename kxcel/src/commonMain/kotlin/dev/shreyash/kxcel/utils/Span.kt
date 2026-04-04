package dev.shreyash.kxcel.utils

import dev.shreyash.kxcel.sheet.CellIndex

data class Span(
    val rowSpanStart: Int,
    val columnSpanStart: Int,
    val rowSpanEnd: Int,
    val columnSpanEnd: Int
) {
    companion object {
        fun fromCellIndex(
            start: CellIndex,
            end: CellIndex
        ): Span {
            return Span(
                rowSpanStart = start.rowIndex,
                columnSpanStart = start.columnIndex,
                rowSpanEnd = end.rowIndex,
                columnSpanEnd = end.columnIndex
            )
        }
    }
}