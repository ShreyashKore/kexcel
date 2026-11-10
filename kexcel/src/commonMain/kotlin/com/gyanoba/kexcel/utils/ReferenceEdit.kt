package com.gyanoba.kexcel.utils

internal enum class Axis { Row, Column }

/**
 * One row or column inserted at, or removed from, the zero-based [index] of a sheet.
 *
 * Everything that names a cell of that sheet by address has to follow: a reference at or
 * beyond the edit moves by one, a range the edit falls inside grows or shrinks, and a
 * reference to exactly what was removed has nothing left to point at.
 */
internal class StructuralEdit(val axis: Axis, val index: Int, val removed: Boolean) {
    private val at get() = index + 1
    private val limit get() = if (axis == Axis.Row) MAX_ROWS else MAX_COLUMNS

    /** Where the one-based [position] is afterwards, or `null` when it was removed. */
    fun move(position: Int): Int? = when {
        position < at -> position
        !removed -> (position + 1).takeIf { it <= limit }
        position == at -> null
        else -> position - 1
    }

    /** What the inclusive span [start]..[end] covers afterwards, or `null` when all of it was removed. */
    fun moveSpan(start: Int, end: Int): Pair<Int, Int>? {
        if (!removed) {
            val newStart = if (start >= at) start + 1 else start
            val newEnd = if (end >= at) end + 1 else end
            return if (newStart > limit) null else newStart to minOf(newEnd, limit)
        }
        return when {
            at < start -> (start - 1) to (end - 1)
            at > end -> start to end
            start == end -> null
            else -> start to (end - 1)
        }
    }

    private companion object {
        const val MAX_COLUMNS = 16384
        const val MAX_ROWS = 1048576
    }
}

private const val DELETED_REFERENCE = "#REF!"

private val AREA = Regex("""(\$?)([A-Za-z]{1,3})(\$?)([0-9]{1,7}):(\$?)([A-Za-z]{1,3})(\$?)([0-9]{1,7})""")
private val CELL = Regex("""(\$?)([A-Za-z]{1,3})(\$?)([0-9]{1,7})""")
private val COLUMN_RANGE = Regex("""(\$?)([A-Za-z]{1,3}):(\$?)([A-Za-z]{1,3})""")
private val ROW_RANGE = Regex("""(\$?)([0-9]{1,7}):(\$?)([0-9]{1,7})""")

private fun Char.isNameChar(): Boolean = isLetterOrDigit() || this == '_' || this == '.'

/** An unquoted sheet qualifier: where it ends (after the `!`), and the sheet it names — `null` for a 3-D `First:Last!`. */
internal class BareQualifier(val end: Int, val sheet: String?)

/**
 * Reads `Name!` or `First:Last!` at [start] of [text]. Letters are any Unicode letters:
 * Excel leaves a name such as `Données` or `売上` unquoted, so an ASCII-only pattern would
 * take the reference after it for an unqualified one.
 */
internal fun bareQualifierAt(text: String, start: Int): BareQualifier? {
    fun nameEnd(from: Int): Int {
        if (from >= text.length || !(text[from].isLetter() || text[from] == '_')) return from
        var j = from + 1
        while (j < text.length && text[j].isNameChar()) j++
        return j
    }
    val first = nameEnd(start)
    if (first == start || first >= text.length) return null
    if (text[first] == '!') return BareQualifier(first + 1, text.substring(start, first))
    if (text[first] == ':') {
        val second = nameEnd(first + 1)
        if (second > first + 1 && second < text.length && text[second] == '!') return BareQualifier(second + 1, null)
    }
    return null
}

private fun validColumn(letters: String) = lettersToNumeric(letters) in 1..16384
private fun validRow(digits: String) = digits.toIntOrNull()?.let { it in 1..1048576 } == true

private fun StructuralEdit.column(letters: String): String? =
    if (axis == Axis.Column) move(lettersToNumeric(letters))?.let { numericToLetters(it) } else letters

private fun StructuralEdit.row(digits: String): String? =
    if (axis == Axis.Row) move(digits.toInt())?.toString() else digits

private fun StructuralEdit.columns(first: String, last: String): Pair<String, String>? {
    if (axis != Axis.Column) return first to last
    val a = lettersToNumeric(first)
    val b = lettersToNumeric(last)
    val moved = moveSpan(minOf(a, b), maxOf(a, b)) ?: return null
    val (low, high) = numericToLetters(moved.first) to numericToLetters(moved.second)
    return if (a <= b) low to high else high to low
}

private fun StructuralEdit.rows(first: String, last: String): Pair<String, String>? {
    if (axis != Axis.Row) return first to last
    val a = first.toInt()
    val b = last.toInt()
    val moved = moveSpan(minOf(a, b), maxOf(a, b)) ?: return null
    val (low, high) = moved.first.toString() to moved.second.toString()
    return if (a <= b) low to high else high to low
}

/** One reference found at a position of a formula: how long it is, and what it becomes. */
private class Found(val end: Int, val adjusted: String)

/**
 * Reads a cell, area, whole-column or whole-row reference starting at [start] of [text]
 * and works out what [edit] turns it into. `$` anchors are kept: unlike a fill, a
 * structural edit moves absolute references too.
 */
private fun referenceAt(text: String, start: Int, edit: StructuralEdit): Found? {
    fun endsToken(end: Int): Boolean {
        if (end >= text.length) return true
        val next = text[end]
        return !(next.isNameChar() || next == '(' || next == '!' || next == '$')
    }

    AREA.matchAt(text, start)?.let { m ->
        val (d1, c1, d2, r1, d3, c2, d4, r2) = m.destructured
        if (endsToken(m.range.last + 1) && validColumn(c1) && validColumn(c2) && validRow(r1) && validRow(r2)) {
            val columns = edit.columns(c1, c2)
            val rows = edit.rows(r1, r2)
            val adjusted = if (columns == null || rows == null) DELETED_REFERENCE
            else "$d1${columns.first}$d2${rows.first}:$d3${columns.second}$d4${rows.second}"
            return Found(m.range.last + 1, adjusted)
        }
    }
    CELL.matchAt(text, start)?.let { m ->
        val (d1, c, d2, r) = m.destructured
        if (endsToken(m.range.last + 1) && validColumn(c) && validRow(r)) {
            val column = edit.column(c)
            val row = edit.row(r)
            val adjusted = if (column == null || row == null) DELETED_REFERENCE else "$d1$column$d2$row"
            return Found(m.range.last + 1, adjusted)
        }
    }
    COLUMN_RANGE.matchAt(text, start)?.let { m ->
        val (d1, c1, d2, c2) = m.destructured
        if (endsToken(m.range.last + 1) && validColumn(c1) && validColumn(c2)) {
            val columns = edit.columns(c1, c2)
            return Found(m.range.last + 1, if (columns == null) DELETED_REFERENCE else "$d1${columns.first}:$d2${columns.second}")
        }
    }
    ROW_RANGE.matchAt(text, start)?.let { m ->
        val (d1, r1, d2, r2) = m.destructured
        if (endsToken(m.range.last + 1) && validRow(r1) && validRow(r2)) {
            val rows = edit.rows(r1, r2)
            return Found(m.range.last + 1, if (rows == null) DELETED_REFERENCE else "$d1${rows.first}:$d2${rows.second}")
        }
    }
    return null
}

/**
 * [formula] after [edit] was made to the sheets named [editedSheets] (one sheet, unless it
 * is linked under several names).
 *
 * A reference is adjusted when it points into the edited sheet: one qualified with its
 * name (`Data!A1`, `'My Data'!A1`), or an unqualified one when the formula itself lives on
 * that sheet ([inEditedSheet]). A reference to exactly the removed row or column becomes
 * `#REF!`, as in Excel. References into other sheets, 3-D references (`First:Last!A1`) and
 * references into other workbooks (`[1]Data!A1`) are left as they are, and so is anything
 * that only looks like a reference: string literals, function names, defined names.
 *
 * This is what Excel does to a formula when a row or column is inserted or deleted, and
 * what POI does in `FormulaShifter` for `Sheet.shiftRows` / `shiftColumns`.
 */
internal fun adjustFormulaForEdit(
    formula: String,
    edit: StructuralEdit,
    editedSheets: Set<String>,
    inEditedSheet: Boolean,
): String {
    val out = StringBuilder(formula.length + 8)
    var i = 0
    val n = formula.length

    fun isEdited(sheet: String) = editedSheets.any { it.equals(sheet, ignoreCase = true) }

    /** Copies the reference at [at], adjusted when it [targets] the edited sheet. Returns the position after it. */
    fun reference(at: Int, targets: Boolean): Int {
        val found = referenceAt(formula, at, edit) ?: return at
        if (targets) out.append(found.adjusted) else out.append(formula, at, found.end)
        return found.end
    }

    fun quotedEnd(from: Int, quote: Char): Int {
        var j = from + 1
        while (j < n) {
            if (formula[j] == quote) { if (j + 1 < n && formula[j + 1] == quote) j += 2 else break } else j++
        }
        return minOf(j + 1, n)
    }

    while (i < n) {
        val c = formula[i]
        when {
            c == '"' -> {
                val end = quotedEnd(i, '"')
                out.append(formula, i, end); i = end
            }
            c == '\'' -> {
                val end = quotedEnd(i, '\'')
                out.append(formula, i, end)
                if (end < n && formula[end] == '!') {
                    val sheet = formula.substring(i + 1, end - 1).replace("''", "'")
                    out.append('!')
                    i = reference(end + 1, targets = !sheet.startsWith('[') && isEdited(sheet))
                } else {
                    i = end
                }
            }
            // A structured reference, or the workbook index of an external one: `[1]Data!A1`.
            c == '[' -> {
                var depth = 0
                var j = i
                while (j < n) {
                    if (formula[j] == '[') depth++ else if (formula[j] == ']') { depth--; if (depth == 0) break }
                    j++
                }
                var end = minOf(j + 1, n)
                while (end < n && formula[end].isNameChar()) end++
                if (end < n && formula[end] == '!') end++
                out.append(formula, i, end)
                i = reference(end, targets = false)
            }
            // An error literal such as #REF! or #N/A; its letters are not a sheet name.
            c == '#' -> {
                var j = i + 1
                while (j < n && (formula[j].isLetterOrDigit() || formula[j] == '/' || formula[j] == '!' || formula[j] == '?')) j++
                out.append(formula, i, j); i = j
            }
            c == '$' || c.isLetter() || c.isDigit() || c == '_' -> {
                val qualifier = bareQualifierAt(formula, i)
                if (qualifier != null) {
                    out.append(formula, i, qualifier.end)
                    i = reference(qualifier.end, targets = qualifier.sheet != null && isEdited(qualifier.sheet))
                } else {
                    val next = reference(i, targets = inEditedSheet)
                    if (next != i) i = next else {
                        // A name, a function or a number: skipped whole, so that nothing
                        // inside it ("LOG10", "1E5") is taken for a reference.
                        var j = i + 1
                        while (j < n && (formula[j].isNameChar() || formula[j] == '$')) j++
                        out.append(formula, i, j); i = j
                    }
                }
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}

/**
 * A single reference as attributes hold it (`ref="A2"`, `ref="A1:C5"`) after [edit], or
 * `null` when all of it was removed. Text that is not a reference is returned unchanged.
 */
internal fun adjustReferenceForEdit(reference: String, edit: StructuralEdit): String? {
    val found = referenceAt(reference, 0, edit)?.takeIf { it.end == reference.length } ?: return reference
    return found.adjusted.takeUnless { it == DELETED_REFERENCE }
}

/** A space-separated list of references (`sqref`) after [edit]; the ones that were removed are dropped. */
internal fun adjustReferenceListForEdit(references: String, edit: StructuralEdit): String =
    references.split(' ').filter { it.isNotEmpty() }.mapNotNull { adjustReferenceForEdit(it, edit) }.joinToString(" ")
