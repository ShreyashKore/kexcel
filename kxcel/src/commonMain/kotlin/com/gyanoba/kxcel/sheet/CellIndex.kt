package com.gyanoba.kxcel.sheet

import com.gyanoba.kxcel.utils.cellCoordsFromCellId
import com.gyanoba.kxcel.utils.getCellId

public class CellIndex private constructor(
    public val columnIndex: Int,
    public val rowIndex: Int,
) {
    public companion object {
        /**
         * ```
         * CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 0) // A1
         * CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 1) // A2
         * ```
         */
        public fun indexByColumnRow(columnIndex: Int, rowIndex: Int): CellIndex =
            CellIndex(columnIndex = columnIndex, rowIndex = rowIndex)

        /**
         * ```
         * CellIndex.indexByString("A1") // columnIndex: 0, rowIndex: 0
         * CellIndex.indexByString("A2") // columnIndex: 0, rowIndex: 1
         * ```
         */
        public fun indexByString(cellIndex: String): CellIndex {
            val (row, column) = cellCoordsFromCellId(cellIndex)
            return CellIndex(columnIndex = column, rowIndex = row)
        }
    }

    /**
     * Returns the cell ID string (e.g. "A1").
     * Avoid using this in hot paths — it is a relatively expensive operation.
     */
    public val cellId: String
        get() = getCellId(columnIndex, rowIndex)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CellIndex) return false
        return rowIndex == other.rowIndex && columnIndex == other.columnIndex
    }

    override fun hashCode(): Int = 31 * rowIndex + columnIndex

    override fun toString(): String = cellId
}