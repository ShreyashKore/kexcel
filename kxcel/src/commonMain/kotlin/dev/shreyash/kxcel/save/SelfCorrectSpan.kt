package dev.shreyash.kxcel.save

import dev.shreyash.kxcel.Excel
import dev.shreyash.kxcel.utils.Span
import dev.shreyash.kxcel.utils.isLocationChangeRequired

/**
 * Self-corrects the spanning of rows and columns by checking cross-sectional
 * relationships between spans and merging overlapping ones.
 */
fun selfCorrectSpanMap(excel: Excel) {
    excel.mergeChangeLook.forEach { key ->
        val sheet = excel.sheetMap[key] ?: return@forEach
        if (sheet.spanList.isEmpty()) return@forEach

        val spanList: MutableList<Span?> = sheet.spanList.map { it }.toMutableList()

        for (i in spanList.indices) {
            val checkerPos = spanList[i] ?: continue

            var startRow = checkerPos.rowSpanStart
            var startColumn = checkerPos.columnSpanStart
            var endRow = checkerPos.rowSpanEnd
            var endColumn = checkerPos.columnSpanEnd

            for (j in (i + 1) until spanList.size) {
                val spanObj = spanList[j] ?: continue

                val locationChange = isLocationChangeRequired(
                    startColumn, startRow, endColumn, endRow, spanObj
                )
                if (locationChange.changed) {
                    startColumn = locationChange.bounds.startColumn
                    startRow = locationChange.bounds.startRow
                    endColumn = locationChange.bounds.endColumn
                    endRow = locationChange.bounds.endRow
                    spanList[j] = null
                } else {
                    val locationChange2 = isLocationChangeRequired(
                        spanObj.columnSpanStart,
                        spanObj.rowSpanStart,
                        spanObj.columnSpanEnd,
                        spanObj.rowSpanEnd,
                        checkerPos,
                    )
                    if (locationChange2.changed) {
                        startColumn = locationChange2.bounds.startColumn
                        startRow = locationChange2.bounds.startRow
                        endColumn = locationChange2.bounds.endColumn
                        endRow = locationChange2.bounds.endRow
                        spanList[j] = null
                    }
                }
            }

            spanList[i] = Span(
                rowSpanStart = startRow,
                columnSpanStart = startColumn,
                rowSpanEnd = endRow,
                columnSpanEnd = endColumn,
            )
        }

        sheet.spanList = spanList.toMutableList()
        sheet.cleanUpSpanMap()
    }
}

data class LocationChanged(
    val changed: Boolean,
    val bounds: SpanBounds,
)

/**
 * Result of [isLocationChangeRequired].
 *
 * @property changed Whether the bounds need to be updated.
 * @property bounds  The new merged bounds (only meaningful when [changed] is true).
 */
data class LocationChangeResult(
    val changed: Boolean,
    val bounds: SpanBounds,
)

data class SpanBounds(
    val startColumn: Int,
    val startRow: Int,
    val endColumn: Int,
    val endRow: Int,
)