package com.gyanoba.kexcel.sheet

import com.gyanoba.kexcel.shared_strings.TextSpan
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// region --- Data ---

public class Data private constructor(
    private var sheet: Sheet,
    internal var _rowIndex: Int,
    internal var _columnIndex: Int,
    internal var _value: CellValue? = null,
    internal var _cellStyle: CellStyle? = null,
) {
    private var _sheetName: String = sheet.sheetName

    public companion object {
        /**
         * Returns a new Data object — called from Sheet class.
         */
        public fun newData(sheet: Sheet, row: Int, column: Int): Data =
            Data(sheet, row, column)

        /**
         * Clones a Data object, updating the sheet reference while copying all values.
         */
        internal fun clone(sheet: Sheet, dataObject: Data): Data =
            Data(
                sheet = sheet,
                _rowIndex = dataObject.rowIndex,
                _columnIndex = dataObject.columnIndex,
                _value = dataObject.value,
                _cellStyle = dataObject.cellStyle,
            )
    }

    /** Returns the row index. */
    public val rowIndex: Int get() = _rowIndex

    /** Returns the column index. */
    public val columnIndex: Int get() = _columnIndex

    /** Returns the sheet name. */
    public val sheetName: String get() = _sheetName

    /** Returns the cell index as a [CellIndex] (e.g. A1, Z5). */
    public val cellIndex: CellIndex
        get() = CellIndex.indexByColumnRow(columnIndex = _columnIndex, rowIndex = _rowIndex)

    /**
     * Sets a formula on this cell.
     * ```
     * val cell = sheet.cell(CellIndex.indexByString("E5"))
     * cell.setFormula("=SUM(1,2)")
     * ```
     */
    public fun setFormula(formula: String) {
        sheet.updateCell(cellIndex, FormulaCellValue(formula))
    }

    /** Returns the value stored in this cell, or `null` if empty. */
    public var value: CellValue?
        get() = _value
        set(newValue) {
            sheet.updateCell(cellIndex, newValue)
        }

    /**
     * Returns the user-defined [CellStyle], or `null` if none is set.
     * Setting this marks the workbook as having style changes.
     */
    public var cellStyle: CellStyle?
        get() = _cellStyle
        set(newStyle) {
            sheet.excel.styleChanges = true
            _cellStyle = newStyle
        }

    // Equatable-equivalent component list
    private fun props(): List<Any?> =
        listOf(_value, _columnIndex, _rowIndex, _cellStyle, _sheetName)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Data) return false
        return props() == other.props()
    }

    override fun hashCode(): Int = props().hashCode()
}

// endregion

// region --- CellValue sealed hierarchy ---

public sealed class CellValue

// endregion

// region --- CellValue subclasses ---

public class FormulaCellValue(public val formula: String) : CellValue() {
    override fun toString(): String = formula
    override fun hashCode(): Int = 31 * this::class.hashCode() + formula.hashCode()
    override fun equals(other: Any?): Boolean =
        other is FormulaCellValue && other.formula == formula
}

public class IntCellValue(public val value: Long) : CellValue() {
    public constructor(value: Int) : this(value.toLong())

    override fun toString(): String = value.toString()
    override fun hashCode(): Int = 31 * this::class.hashCode() + value.hashCode()
    override fun equals(other: Any?): Boolean = other is IntCellValue && other.value == value
}

public class DoubleCellValue(public val value: Double) : CellValue() {
    override fun toString(): String = value.toString()
    override fun hashCode(): Int = 31 * this::class.hashCode() + value.hashCode()
    override fun equals(other: Any?): Boolean = other is DoubleCellValue && other.value == value
}

public class BoolCellValue(public val value: Boolean) : CellValue() {
    override fun toString(): String = value.toString()
    override fun hashCode(): Int = 31 * this::class.hashCode() + value.hashCode()
    override fun equals(other: Any?): Boolean = other is BoolCellValue && other.value == value
}

public class TextCellValue private constructor(public val value: TextSpan) : CellValue() {
    public constructor(text: String) : this(TextSpan(text = text))

    public companion object {
        public fun span(value: TextSpan): TextCellValue = TextCellValue(value)
    }

    override fun toString(): String = value.toString()
    override fun hashCode(): Int = 31 * this::class.hashCode() + value.hashCode()
    override fun equals(other: Any?): Boolean = other is TextCellValue && other.value == value
}

// endregion

// region --- DateCellValue ---

public class DateCellValue(
    public val year: Int,
    public val month: Int,
    public val day: Int,
) : CellValue() {

    init {
        require(month in 1..12) { "month must be in 1..12, was $month" }
        require(day in 1..31) { "day must be in 1..31, was $day" }
    }

    public companion object {
        public fun fromLocalDateTime(dt: LocalDateTime): DateCellValue =
            DateCellValue(year = dt.year, month = dt.monthNumber, day = dt.dayOfMonth)

        public fun fromLocalDate(dt: LocalDate): DateCellValue =
            DateCellValue(year = dt.year, month = dt.monthNumber, day = dt.dayOfMonth)
    }

    public fun asLocalDate(): LocalDate = LocalDate(year, month, day)

    public fun asDateTimeUtc(): LocalDateTime = LocalDateTime(year, month, day, 0, 0, 0)

    override fun toString(): String = asLocalDate().toString()

    override fun hashCode(): Int {
        var h = this::class.hashCode()
        h = 31 * h + year
        h = 31 * h + month
        h = 31 * h + day
        return h
    }

    override fun equals(other: Any?): Boolean =
        other is DateCellValue && other.year == year && other.month == month && other.day == day
}

// endregion

// region --- TimeCellValue ---

public class TimeCellValue(
    public val hour: Int = 0,
    public val minute: Int = 0,
    public val second: Int = 0,
    public val millisecond: Int = 0,
    public val microsecond: Int = 0,
) : CellValue() {

    init {
        require(hour >= 0) { "hour must be >= 0, was $hour" }
        require(minute in 0..60) { "minute must be in 0..60, was $minute" }
        require(second in 0..60) { "second must be in 0..60, was $second" }
        require(millisecond in 0..1000) { "millisecond must be in 0..1000, was $millisecond" }
        require(microsecond in 0..1000) { "microsecond must be in 0..1000, was $microsecond" }
    }

    public companion object {
        /**
         * [fractionOfDay] = 1.0 is 24 hours, 0.5 is 12 hours, etc.
         */
        public fun fromFractionOfDay(fractionOfDay: Double): TimeCellValue {
            val totalMs = (fractionOfDay * 24 * 3600 * 1000).toLong()
            return fromDuration(totalMs.milliseconds)
        }

        public fun fromDuration(duration: Duration): TimeCellValue {
            val totalMs = duration.inWholeMilliseconds
            val h = (totalMs / (3600 * 1000)).toInt()
            val m = ((totalMs % (3600 * 1000)) / 60_000).toInt()
            val s = ((totalMs % 60_000) / 1000).toInt()
            val ms = (totalMs % 1000).toInt()
            return TimeCellValue(
                hour = h,
                minute = m,
                second = s,
                millisecond = ms,
                microsecond = 0
            )
        }

        public fun fromLocalDateTime(dt: LocalDateTime): TimeCellValue = TimeCellValue(
            hour = dt.hour,
            minute = dt.minute,
            second = dt.second,
            millisecond = dt.nanosecond / 1_000_000,
            microsecond = (dt.nanosecond / 1_000) % 1_000,
        )
    }

    public fun asDuration(): Duration =
        hour.hours + minute.minutes + second.seconds + millisecond.milliseconds + microsecond.microseconds

    override fun toString(): String =
        "${twoDigits(hour)}:${twoDigits(minute)}:${twoDigits(second)}"

    override fun hashCode(): Int {
        var h = this::class.hashCode()
        h = 31 * h + hour
        h = 31 * h + minute
        h = 31 * h + second
        h = 31 * h + millisecond
        h = 31 * h + microsecond
        return h
    }

    override fun equals(other: Any?): Boolean =
        other is TimeCellValue &&
                other.hour == hour &&
                other.minute == minute &&
                other.second == second &&
                other.millisecond == millisecond &&
                other.microsecond == microsecond
}

// endregion

// region --- DateTimeCellValue ---

/**
 * Excel does not know if this is UTC or not.
 * Use [asDateTimeLocal] or [asDateTimeUtc] to get the [LocalDateTime] you prefer.
 */
public class DateTimeCellValue(
    public val year: Int,
    public val month: Int,
    public val day: Int,
    public val hour: Int,
    public val minute: Int,
    public val second: Int = 0,
    public val millisecond: Int = 0,
    public val microsecond: Int = 0,
) : CellValue() {

    init {
        require(month in 1..12) { "month must be in 1..12, was $month" }
        require(day in 1..31) { "day must be in 1..31, was $day" }
        require(hour in 0..24) { "hour must be in 0..24, was $hour" }
        require(minute in 0..60) { "minute must be in 0..60, was $minute" }
        require(second in 0..60) { "second must be in 0..60, was $second" }
        require(millisecond in 0..1000) { "millisecond must be in 0..1000, was $millisecond" }
        require(microsecond in 0..1000) { "microsecond must be in 0..1000, was $microsecond" }
    }

    public companion object {
        public fun fromLocalDateTime(dt: LocalDateTime): DateTimeCellValue = DateTimeCellValue(
            year = dt.year,
            month = dt.monthNumber,
            day = dt.dayOfMonth,
            hour = dt.hour,
            minute = dt.minute,
            second = dt.second,
            millisecond = dt.nanosecond / 1_000_000,
            microsecond = (dt.nanosecond / 1_000) % 1_000,
        )
    }

    /** Returns this value as a local [LocalDateTime] (no timezone conversion). */
    public fun asDateTimeLocal(): LocalDateTime =
        LocalDateTime(
            year,
            month,
            day,
            hour,
            minute,
            second,
            (millisecond * 1_000_000) + (microsecond * 1_000)
        )

    /** Returns this value as a UTC [LocalDateTime] (no timezone conversion — caller must treat it as UTC). */
    public fun asDateTimeUtc(): LocalDateTime = asDateTimeLocal()

    override fun toString(): String = asDateTimeLocal().toString()

    override fun hashCode(): Int {
        var h = this::class.hashCode()
        h = 31 * h + year
        h = 31 * h + month
        h = 31 * h + day
        h = 31 * h + hour
        h = 31 * h + minute
        h = 31 * h + second
        h = 31 * h + millisecond
        h = 31 * h + microsecond
        return h
    }

    override fun equals(other: Any?): Boolean =
        other is DateTimeCellValue &&
                other.year == year &&
                other.month == month &&
                other.day == day &&
                other.hour == hour &&
                other.minute == minute &&
                other.second == second &&
                other.millisecond == millisecond &&
                other.microsecond == microsecond
}

// endregion

// region --- Helpers ---

private fun twoDigits(n: Int): String = if (n < 10) "0$n" else "$n"

// endregion