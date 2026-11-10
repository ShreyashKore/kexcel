package com.gyanoba.kexcel.utils

import com.fleeksoft.ksoup.nodes.Element
import com.gyanoba.kexcel.save.LocationChanged
import com.gyanoba.kexcel.save.SpanBounds
import com.gyanoba.kexcel.sheet.FontStyle
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

/**
 * The column index a `<c>` declares through its `r` reference, or `null` when `r` is
 * absent or unparseable — the caller then falls back to the cell's position in the row,
 * which is what the spec prescribes for a cell without a reference.
 */
internal fun getCellNumber(cell: Element): Int? {
    val r = cell.attr("r")
    if (r.isNullOrBlank()) return null
    val column = runCatching { cellCoordsFromCellId(r).second }.getOrNull() ?: return null
    return if (column < 0) null else column
}

/**
 * Reads an ST_OnOff `val` attribute (`<b val="0"/>`, `<i val="false"/>`, …). An absent or
 * empty attribute means "on" — a bare `<b/>` is bold — so only an explicit off value is off.
 */
internal fun isOnOff(value: String?): Boolean = when (value?.trim()?.lowercase()) {
    "0", "false", "off" -> false
    else -> true
}

internal fun getRowNumber(row: Element?): Int? {
    return row?.attr("r")?.toIntOrNull()
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

/**
 * Resolves a relationship `Target` read from `xl/_rels/workbook.xml.rels` to the archive entry
 * name of the part it points at: `worksheets/sheet1.xml` and `/xl/worksheets/sheet1.xml` both
 * resolve to `xl/worksheets/sheet1.xml`.
 *
 * Per ECMA-376 Part 2 (Open Packaging Conventions) §9.3, a target starting with `/` is absolute
 * from the package root (openpyxl, and so pandas, writes these), and any other target is
 * relative to the folder of the source part, `xl/` (Excel writes these). `.` and `..` segments
 * are resolved; `..` never climbs above the package root. Percent-encoded characters are not
 * decoded.
 */
internal fun resolveWorkbookRelTarget(target: String): String = resolveRelationshipTarget("xl", target)

/**
 * Resolves a relationship `Target` to the archive entry name of the part it points at, for
 * a source part that lives in [folder] (`xl` for the workbook, `xl/worksheets` for a sheet).
 * See [resolveWorkbookRelTarget] for the rules.
 */
internal fun resolveRelationshipTarget(folder: String, target: String): String {
    val segments = if (target.startsWith('/')) mutableListOf() else folder.split('/').filter { it.isNotEmpty() }.toMutableList()
    target.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> segments.removeLastOrNull()
            else -> segments += segment
        }
    }
    return segments.joinToString("/")
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
                                (spanObj.columnSpanStart in (startColumn + 1)..endColumn) ||
                                        (spanObj.columnSpanEnd in startColumn..<endColumn)
                                ) &&
                                (
                                        (startRow in spanObj.rowSpanStart..spanObj.rowSpanEnd) ||
                                                (endRow in spanObj.rowSpanStart..spanObj.rowSpanEnd)
                                        )
                        ) ||
                (
                        (
                                (spanObj.rowSpanStart in (startRow + 1)..endRow) ||
                                        (spanObj.rowSpanEnd in startRow..<endRow)
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

/**
 * Returns the index of [fontStyle] in [fontStyleList], or -1 if not found.
 */
internal fun fontStyleIndex(fontStyleList: List<FontStyle>, fontStyle: FontStyle): Int =
    fontStyleList.indexOf(fontStyle)
private val STRING_ESCAPE = Regex("_x([0-9A-Fa-f]{4})_")

/**
 * Decodes the `_xHHHH_` escapes of a string in the file (ST_Xstring, §22.9.2.19).
 *
 * XML 1.0 cannot hold most control characters, and reads a carriage return as a line
 * feed, so they are stored as `_x000D_`, `_x0001_`, … ; a literal underscore that would
 * otherwise start such a sequence is stored as `_x005F_`. POI decodes them in
 * `XSSFRichTextString.utfDecode`. Left undecoded, a line break typed on Windows showed up
 * as the text `_x000D_`.
 */
internal fun decodeXmlString(text: String): String {
    if ("_x" !in text) return text
    return STRING_ESCAPE.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
}

/** The inverse of [decodeXmlString]: escapes what an XML text node cannot carry unchanged. */
internal fun encodeXmlString(text: String): String {
    var needsWork = false
    for (i in text.indices) {
        val c = text[i]
        if ((c < ' ' && c != '\t' && c != '\n') || c == '\uFFFE' || c == '\uFFFF' || (c == '_' && STRING_ESCAPE.matchesAt(text, i))) {
            needsWork = true
            break
        }
    }
    if (!needsWork) return text

    val out = StringBuilder(text.length + 16)
    for (i in text.indices) {
        val c = text[i]
        when {
            (c < ' ' && c != '\t' && c != '\n') || c == '\uFFFE' || c == '\uFFFF' ->
                out.append("_x").append(c.code.toString(16).uppercase().padStart(4, '0')).append('_')
            // Text that already reads as an escape must not be decoded into something else.
            c == '_' && STRING_ESCAPE.matchesAt(text, i) -> out.append("_x005F_")
            else -> out.append(c)
        }
    }
    return out.toString()
}
