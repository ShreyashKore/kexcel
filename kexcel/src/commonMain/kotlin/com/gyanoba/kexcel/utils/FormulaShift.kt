package com.gyanoba.kexcel.utils

private const val MAX_COLUMNS = 16384
private const val MAX_ROWS = 1048576

private val CELL = Regex("""(\$?)([A-Za-z]{1,3})(\$?)([0-9]{1,7})""")
private val COLUMN_RANGE = Regex("""(\$?)([A-Za-z]{1,3}):(\$?)([A-Za-z]{1,3})""")
private val ROW_RANGE = Regex("""(\$?)([0-9]{1,7}):(\$?)([0-9]{1,7})""")

private fun Char.isNameChar(): Boolean = isLetterOrDigit() || this == '_' || this == '.'

/**
 * Moves every *relative* cell reference in [formula] by [rowDelta] rows and [columnDelta]
 * columns, the way Excel does when a formula is filled down or across. `$`-anchored parts
 * stay put: shifting `A1+$B$1+C$1` by one row gives `A2+$B$1+C$1`.
 *
 * This is what turns the master expression of a shared formula (§18.3.1.40, `t="shared"`)
 * into the expression of each cell that shares it — the counterpart of POI's
 * `SharedFormula.convertSharedFormulas`. Like POI, a reference pushed past the edge of the
 * sheet wraps around.
 *
 * The scan is lexical, not a full parse. It leaves alone everything that only looks like a
 * reference: text in string literals, quoted and unquoted sheet names (`Sheet1!`, `'A1'!`),
 * function names (`LOG10(`), defined names (`Q1_total`), structured and external references
 * in `[…]`, and numbers (`1E5`).
 */
internal fun shiftFormulaReferences(formula: String, rowDelta: Int, columnDelta: Int): String {
    if (rowDelta == 0 && columnDelta == 0) return formula
    val out = StringBuilder(formula.length + 8)
    var i = 0
    val n = formula.length

    fun column(dollar: String, letters: String): String =
        if (dollar.isNotEmpty()) "$dollar$letters"
        else numericToLetters(((lettersToNumeric(letters) - 1 + columnDelta).mod(MAX_COLUMNS)) + 1)

    fun row(dollar: String, digits: String): String =
        if (dollar.isNotEmpty()) "$dollar$digits"
        else (((digits.toInt() - 1 + rowDelta).mod(MAX_ROWS)) + 1).toString()

    fun validColumn(letters: String) = lettersToNumeric(letters) in 1..MAX_COLUMNS
    fun validRow(digits: String) = digits.toInt() in 1..MAX_ROWS

    /** A reference must end the token, and must not turn out to be a sheet or function name. */
    fun endsToken(end: Int): Boolean {
        if (end >= n) return true
        val next = formula[end]
        return !(next.isNameChar() || next == '(' || next == '!' || next == '$')
    }

    while (i < n) {
        val c = formula[i]
        when {
            // String literal: "…" with "" as the escaped quote.
            c == '"' -> {
                var j = i + 1
                while (j < n) {
                    if (formula[j] == '"') { if (j + 1 < n && formula[j + 1] == '"') j += 2 else break } else j++
                }
                val end = minOf(j + 1, n)
                out.append(formula, i, end); i = end
            }
            // Quoted sheet name: '…' with '' as the escaped quote.
            c == '\'' -> {
                var j = i + 1
                while (j < n) {
                    if (formula[j] == '\'') { if (j + 1 < n && formula[j + 1] == '\'') j += 2 else break } else j++
                }
                val end = minOf(j + 1, n)
                out.append(formula, i, end); i = end
            }
            // Structured reference or external workbook index: copied through the matching ].
            c == '[' -> {
                var depth = 0
                var j = i
                while (j < n) {
                    if (formula[j] == '[') depth++ else if (formula[j] == ']') { depth--; if (depth == 0) break }
                    j++
                }
                val end = minOf(j + 1, n)
                out.append(formula, i, end); i = end
            }
            c == '$' || c.isLetter() || c.isDigit() || c == '_' -> {
                val cell = if (c.isDigit() || c == '_') null else CELL.matchAt(formula, i)
                val columns = if (c.isDigit() || c == '_') null else COLUMN_RANGE.matchAt(formula, i)
                val rows = if (c.isLetter() || c == '_') null else ROW_RANGE.matchAt(formula, i)
                when {
                    cell != null && endsToken(cell.range.last + 1) &&
                        validColumn(cell.groupValues[2]) && validRow(cell.groupValues[4]) -> {
                        val (d1, letters, d2, digits) = cell.destructured
                        out.append(column(d1, letters)).append(row(d2, digits))
                        i = cell.range.last + 1
                    }
                    columns != null && endsToken(columns.range.last + 1) &&
                        validColumn(columns.groupValues[2]) && validColumn(columns.groupValues[4]) -> {
                        val (d1, a, d2, b) = columns.destructured
                        out.append(column(d1, a)).append(':').append(column(d2, b))
                        i = columns.range.last + 1
                    }
                    rows != null && endsToken(rows.range.last + 1) &&
                        validRow(rows.groupValues[2]) && validRow(rows.groupValues[4]) -> {
                        val (d1, a, d2, b) = rows.destructured
                        out.append(row(d1, a)).append(':').append(row(d2, b))
                        i = rows.range.last + 1
                    }
                    else -> {
                        // Not a reference: skip the whole name or number so nothing inside
                        // it ("Sheet1", "LOG10", "1E5") is mistaken for one.
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

private val BARE_SHEET_NAME = Regex("""[A-Za-z_][A-Za-z0-9_.]*""")
private val LOOKS_LIKE_A_CELL = Regex("""[A-Za-z]{1,3}[0-9]+""")

/** [name] as it has to be written in front of `!`: quoted unless it is a plain identifier. */
internal fun sheetNameInFormula(name: String): String =
    if (BARE_SHEET_NAME.matches(name) && !LOOKS_LIKE_A_CELL.matches(name)) name
    else "'" + name.replace("'", "''") + "'"

/**
 * Rewrites every reference to the sheet [oldName] in [formula] so that it names [newName]:
 * `Data!A1`, `'Data'!A1`, and either end of a 3-D reference (`Data:Other!A1`). Sheet names
 * are compared without regard to case, as Excel does. Text in string literals and
 * references into other workbooks (`[1]Data!A1`) are left alone.
 */
internal fun renameSheetReferences(formula: String, oldName: String, newName: String): String {
    if (!formula.contains('!')) return formula
    val replacement = sheetNameInFormula(newName)
    val out = StringBuilder(formula.length + 8)
    var i = 0
    val n = formula.length

    fun isOld(name: String) = name.equals(oldName, ignoreCase = true)

    while (i < n) {
        val c = formula[i]
        when {
            c == '"' -> {
                var j = i + 1
                while (j < n) {
                    if (formula[j] == '"') { if (j + 1 < n && formula[j + 1] == '"') j += 2 else break } else j++
                }
                val end = minOf(j + 1, n)
                out.append(formula, i, end); i = end
            }
            c == '\'' -> {
                var j = i + 1
                while (j < n) {
                    if (formula[j] == '\'') { if (j + 1 < n && formula[j + 1] == '\'') j += 2 else break } else j++
                }
                val end = minOf(j + 1, n)
                val quoted = formula.substring(i + 1, minOf(j, n)).replace("''", "'")
                val isSheetReference = end < n && formula[end] == '!'
                when {
                    !isSheetReference || quoted.startsWith('[') -> out.append(formula, i, end)
                    isOld(quoted) -> out.append(replacement)
                    // 'First:Last'!A1
                    ':' in quoted -> {
                        val ends = quoted.split(':', limit = 2).map { if (isOld(it)) newName else it }
                        out.append("'").append(ends.joinToString(":").replace("'", "''")).append("'")
                    }
                    else -> out.append(formula, i, end)
                }
                i = end
            }
            c == '[' -> {
                // A reference into another workbook; its sheet names are not ours.
                var j = i
                var depth = 0
                while (j < n) {
                    if (formula[j] == '[') depth++ else if (formula[j] == ']') { depth--; if (depth == 0) break }
                    j++
                }
                var end = minOf(j + 1, n)
                while (end < n && (formula[end].isLetterOrDigit() || formula[end] == '_' || formula[end] == '.')) end++
                out.append(formula, i, end); i = end
            }
            c.isLetter() || c == '_' -> {
                var j = i + 1
                while (j < n && (formula[j].isLetterOrDigit() || formula[j] == '_' || formula[j] == '.')) j++
                val name = formula.substring(i, j)
                // Followed by `!`, or by `:Other!` as the first end of a 3-D reference.
                val qualifies = j < n && (formula[j] == '!' || (formula[j] == ':' && bareQualifierAt(formula, j + 1)?.sheet != null))
                out.append(if (qualifies && isOld(name)) replacement else name)
                i = j
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}
