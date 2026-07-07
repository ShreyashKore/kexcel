package dev.shreyash.kxcel.utils

import com.fleeksoft.ksoup.nodes.Element
import dev.shreyash.kxcel.save.LocationChanged
import dev.shreyash.kxcel.save.SpanBounds
import dev.shreyash.kxcel.sheet.CellStyle
import dev.shreyash.kxcel.sheet.FontStyle
import kotlin.math.abs
import kotlin.math.round

/// Format a [Double] with exactly two decimal places, e.g. 8.0 -> "8.00".
/// Multiplatform replacement for the JVM-only "%.2f".format(value).
internal fun Double.toFixed2(): String {
    val scaled = round(abs(this) * 100.0).toLong()
    val intPart = scaled / 100
    val frac = (scaled % 100).toString().padStart(2, '0')
    val sign = if (this < 0) "-" else ""
    return "$sign$intPart.$frac"
}

internal val NO_COMPRESSION = listOf(
    "mimetype",
    "Thumbnails/thumbnail.png"
)

public fun getCellId(columnIndex: Int, rowIndex: Int): String {
    return "${numericToLetters(columnIndex + 1)}${rowIndex + 1}"
}

internal fun isColorAppropriate(value: String): String {
    return when (value.length) {
        7 -> value.replace("#", "FF")
        9 -> value.replace("#", "")
        else -> value
    }
}

/// Convert a character based column
public fun lettersToNumeric(letters: String): Int {
    var sum = 0
    var mul = 1

    for (index in letters.length - 1 downTo 0) {
        val c = letters[index].code
        var n = 1
        if (c in 65..90) {
            n += c - 65
        } else if (c in 97..122) {
            n += c - 97
        }
        sum += n * mul
        mul *= 26
    }
    return sum
}

internal fun findRows(table: Element?): List<Element> {
    if (table == null) return emptyList()
    return table.getElementsByTag("row").toList()
}

internal fun findCells(row: Element?): List<Element> {
    if (row == null) return emptyList()
    return row.getElementsByTag("c").toList()
}

internal fun getCellNumber(cell: Element): Int? {
    val r = cell.attr("r") ?: return null
    return cellCoordsFromCellId(r).second
}

internal fun getRowNumber(row: Element?): Int? {
    return row?.attr("r")?.toIntOrNull()
}

internal fun checkPosition(list: List<CellStyle>, cellStyle: CellStyle): Int {
    return list.indexOf(cellStyle)
}

internal fun letterOnly(rune: Int): Int {
    return when (rune) {
        in 65..90 -> rune
        in 97..122 -> rune - 32
        else -> 0
    }
}

internal fun twoDigits(n: Int): String {
    return if (n > 9) "$n" else "0$n"
}

/// Convert a number to character based column
internal fun numericToLetters(numberInput: Int): String {
    var number = numberInput
    var letters = ""

    while (number != 0) {
        var remainder = number % 26
        if (remainder == 0) remainder = 26

        val letter = ('A'.code + remainder - 1).toChar()
        letters = "$letter$letters"

        number = (number - 1) / 26
    }

    return letters
}

/// Normalize line
internal fun normalizeNewLine(text: String): String {
    return text.replace("\r\n", "\n")
}

/// Returns the coordinates from a cell name.
internal fun cellCoordsFromCellId(cellId: String): Pair<Int, Int> {
    val lettersPart = cellId
        .map { letterOnly(it.code) }
        .filter { it > 0 }
        .map { it.toChar() }
        .joinToString("")

    val numericsPart = cellId.substring(lettersPart.length)

    return Pair(
        numericsPart.toInt() - 1,
        lettersToNumeric(lettersPart) - 1
    )
}

/// Throw error when excel is damaged
internal fun damagedExcel(text: String = ""): Nothing {
    throw IllegalArgumentException("\nDamaged Excel file: $text\n")
}

/// return A2:B2
public fun getSpanCellId(
    startColumn: Int,
    startRow: Int,
    endColumn: Int,
    endRow: Int
): String {
    return "${getCellId(startColumn, startRow)}:${getCellId(endColumn, endRow)}"
}

/// Returns updated SpanObject location
internal fun isLocationChangeRequired(
    startColumnInput: Int,
    startRowInput: Int,
    endColumnInput: Int,
    endRowInput: Int,
    spanObj: Span
): LocationChanged {

    var startColumn = startColumnInput
    var startRow = startRowInput
    var endColumn = endColumnInput
    var endRow = endRowInput

    val changeValue =
        (
                startRow <= spanObj.rowSpanStart &&
                        startColumn <= spanObj.columnSpanStart &&
                        endRow >= spanObj.rowSpanEnd &&
                        endColumn >= spanObj.columnSpanEnd
                ) ||
                (
                        (
                                (startColumn < spanObj.columnSpanStart && endColumn >= spanObj.columnSpanStart) ||
                                        (startColumn <= spanObj.columnSpanEnd && endColumn > spanObj.columnSpanEnd)
                                ) &&
                                (
                                        (startRow in spanObj.rowSpanStart..spanObj.rowSpanEnd) ||
                                                (endRow in spanObj.rowSpanStart..spanObj.rowSpanEnd)
                                        )
                        ) ||
                (
                        (
                                (startRow < spanObj.rowSpanStart && endRow >= spanObj.rowSpanStart) ||
                                        (startRow <= spanObj.rowSpanEnd && endRow > spanObj.rowSpanEnd)
                                ) &&
                                (
                                        (startColumn in spanObj.columnSpanStart..spanObj.columnSpanEnd) ||
                                                (endColumn in spanObj.columnSpanStart..spanObj.columnSpanEnd)
                                        )
                        )

    if (changeValue) {
        if (startColumn > spanObj.columnSpanStart) {
            startColumn = spanObj.columnSpanStart
        }
        if (endColumn < spanObj.columnSpanEnd) {
            endColumn = spanObj.columnSpanEnd
        }
        if (startRow > spanObj.rowSpanStart) {
            startRow = spanObj.rowSpanStart
        }
        if (endRow < spanObj.rowSpanEnd) {
            endRow = spanObj.rowSpanEnd
        }
    }

    return LocationChanged(changeValue, SpanBounds(startColumn, startRow, endColumn, endRow))
}

public fun getColumnAlphabet(columnIndex: Int): String {
    return numericToLetters(columnIndex + 1)
}

public fun getColumnIndex(columnAlphabet: String): Int {
    return cellCoordsFromCellId(columnAlphabet).second
}

internal fun fontStyleIndex(list: List<FontStyle>, fontStyle: FontStyle): Int {
    return list.indexOf(fontStyle)
}