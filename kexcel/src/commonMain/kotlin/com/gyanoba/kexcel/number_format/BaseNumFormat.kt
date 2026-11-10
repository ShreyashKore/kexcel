package com.gyanoba.kexcel.number_format

import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.CellValue
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.roundToLong

// region --- Helpers ---

internal fun <K, V> createInverseMap(map: Map<K, V>): Map<V, K> {
    val inverse = mutableMapOf<V, K>()
    for ((key, value) in map) {
        require(!inverse.containsKey(value)) { "map values are not unique" }
        inverse[value] = key
    }
    return inverse
}

// endregion

// region --- NumFormatMaintainer ---

public class NumFormatMaintainer {

    private val firstCustomFmtId = 164
    private var nextFmtId = firstCustomFmtId
    internal var map: MutableMap<Int, NumFormat> = standardNumFormats.toMutableMap()
        private set
    private var inverseMap: MutableMap<NumFormat, Int> = createInverseMap(standardNumFormats).toMutableMap()

    public fun add(numFmtId: Int, format: CustomNumFormat) {
        require(!map.containsKey(numFmtId)) { "numFmtId $numFmtId already exists" }
        require(numFmtId >= firstCustomFmtId) {
            "invalid numFmtId $numFmtId, custom numFmtId must be $firstCustomFmtId or greater"
        }
        map[numFmtId] = format
        inverseMap[format] = numFmtId
        if (numFmtId >= nextFmtId) {
            nextFmtId = numFmtId + 1
        }
    }

    /**
     * Registers a format a styles part declares under [numFmtId], whatever the id. Ids
     * below 164 are reserved for the built-in formats, but files do declare their own
     * there — POI corpus bug66675.xlsx defines 82 as `[$-010409]dd-mmm-yyyy` — and a file's
     * declaration is what its cells mean. Ignoring it read those dates as plain numbers.
     */
    internal fun declare(numFmtId: Int, format: NumFormat) {
        map[numFmtId] = format
        if (!inverseMap.containsKey(format)) inverseMap[format] = numFmtId
        if (numFmtId >= nextFmtId) nextFmtId = numFmtId + 1
    }

    public fun findOrAdd(format: CustomNumFormat): Int {
        inverseMap[format]?.let { return it }
        val fmtId = nextFmtId++
        map[fmtId] = format
        inverseMap[format] = fmtId
        return fmtId
    }

    public fun clear() {
        nextFmtId = firstCustomFmtId
        map = standardNumFormats.toMutableMap()
        inverseMap = createInverseMap(standardNumFormats).toMutableMap()
    }

    public fun getByNumFmtId(numFmtId: Int): NumFormat? = map[numFmtId]
}

// endregion

// region --- NumFormat sealed hierarchy ---

public interface NumFormat {
    public val formatCode: String

    public fun read(v: String): CellValue

    public fun accepts(value: CellValue?): Boolean

    public companion object {
        public val defaultNumeric: BaseNumFormat get() = standard_1
        public val defaultFloat: BaseNumFormat get() = standard_2
        public val defaultBool: BaseNumFormat get() = standard_0
        public val defaultDate: BaseNumFormat get() = standard_14
        public val defaultTime: BaseNumFormat get() = standard_20
        public val defaultDateTime: BaseNumFormat get() = standard_22

        public val standard_0: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 0,  formatCode = "General")
        public val standard_1: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 1,  formatCode = "0")
        public val standard_2: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 2,  formatCode = "0.00")
        public val standard_3: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 3,  formatCode = "#,##0")
        public val standard_4: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 4,  formatCode = "#,##0.00")
        public val standard_9: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 9,  formatCode = "0%")
        public val standard_10: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 10, formatCode = "0.00%")
        public val standard_11: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 11, formatCode = "0.00E+00")
        public val standard_12: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 12, formatCode = "# ?/?")
        public val standard_13: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 13, formatCode = "# ??/??")
        public val standard_14: StandardDateTimeNumFormat = StandardDateTimeNumFormat(numFmtId = 14, formatCode = "mm-dd-yy")
        public val standard_15: StandardDateTimeNumFormat = StandardDateTimeNumFormat(numFmtId = 15, formatCode = "d-mmm-yy")
        public val standard_16: StandardDateTimeNumFormat = StandardDateTimeNumFormat(numFmtId = 16, formatCode = "d-mmm")
        public val standard_17: StandardDateTimeNumFormat = StandardDateTimeNumFormat(numFmtId = 17, formatCode = "mmm-yy")
        public val standard_18: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 18, formatCode = "h:mm AM/PM")
        public val standard_19: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 19, formatCode = "h:mm:ss AM/PM")
        public val standard_20: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 20, formatCode = "h:mm")
        public val standard_21: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 21, formatCode = "h:mm:ss")
        public val standard_22: StandardDateTimeNumFormat = StandardDateTimeNumFormat(numFmtId = 22, formatCode = "m/d/yy h:mm")
        public val standard_37: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 37, formatCode = "#,##0 ;(#,##0)")
        public val standard_38: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 38, formatCode = "#,##0 ;[Red](#,##0)")
        public val standard_39: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 39, formatCode = "#,##0.00;(#,##0.00)")
        public val standard_40: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 40, formatCode = "#,##0.00;[Red](#,##0.00)")
        public val standard_45: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 45, formatCode = "mm:ss")
        public val standard_46: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 46, formatCode = "[h]:mm:ss")
        public val standard_47: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 47, formatCode = "mmss.0")
        public val standard_48: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 48, formatCode = "##0.0E+0")
        public val standard_49: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 49, formatCode = "@")

        public fun custom(formatCode: String): CustomNumFormat {
            if (formatCode == "General") {
                return CustomNumericNumFormat(formatCode = "General")
            }
            return if (formatCodeLooksLikeDateTime(formatCode)) {
                CustomDateTimeNumFormat(formatCode = formatCode)
            } else {
                CustomNumericNumFormat(formatCode = formatCode)
            }
        }

        public fun defaultFor(value: CellValue?): BaseNumFormat = when (value) {
            null,
            is FormulaCellValue,
            is TextCellValue   -> standard_0
            is IntCellValue    -> defaultNumeric
            is DoubleCellValue -> defaultFloat
            is DateCellValue   -> defaultDate
            is BoolCellValue   -> defaultBool
            is TimeCellValue   -> defaultTime
            is DateTimeCellValue -> defaultDateTime
        }
    }
}

public sealed class BaseNumFormat(override val formatCode: String): NumFormat {

    abstract override fun read(v: String): CellValue

    abstract override fun accepts(value: CellValue?): Boolean

    override fun hashCode(): Int = 31 * this::class.hashCode() + formatCode.hashCode()

    override fun equals(other: Any?): Boolean =
        other != null && other::class == this::class && (other as BaseNumFormat).formatCode == formatCode


}

// Marker interfaces replacing Dart's sealed sub-interfaces
public interface StandardNumFormat: NumFormat {
    public val numFmtId: Int
}

public interface CustomNumFormat: NumFormat {
    override val formatCode: String
}

// endregion

// region --- NumericNumFormat ---

public sealed class NumericNumFormat(override val formatCode: String) : BaseNumFormat(formatCode) {

    override fun read(v: String): CellValue {
        // xsd:double allows either case for the exponent; Excel writes `E`, others `e`.
        val eIdx = v.indexOfFirst { it == 'E' || it == 'e' }
        val decimalIdx = v.indexOf('.')

        if (decimalIdx == -1 && eIdx == -1) {
            // More digits than a Long holds is still a number.
            return v.toLongOrNull()?.let { IntCellValue(it) } ?: DoubleCellValue(v.toDouble())
        }

        // treat .0 or .00 as int
        var noActualDecimalPlaces = true
        for (idx in (decimalIdx + 1) until v.length) {
            if (v[idx] != '0') {
                noActualDecimalPlaces = false
                break
            }
        }
        if (noActualDecimalPlaces) {
            v.substring(0, decimalIdx).toLongOrNull()?.let { return IntCellValue(it) }
        }

        return DoubleCellValue(v.toDouble())
    }

    public fun writeDouble(value: DoubleCellValue): String = value.value.toString()

    public fun writeInt(value: IntCellValue): String = value.value.toString()
}

public class StandardNumericNumFormat(
    override val numFmtId: Int,
    override val formatCode: String,
) : NumericNumFormat(formatCode), StandardNumFormat {

    override fun accepts(value: CellValue?): Boolean = when (value) {
        null               -> true
        is FormulaCellValue -> true
        is IntCellValue    -> true
        is TextCellValue   -> numFmtId == 0
        is BoolCellValue   -> true
        is DoubleCellValue -> true
        is DateCellValue   -> false
        is TimeCellValue   -> false
        is DateTimeCellValue -> false
    }

    override fun toString(): String = "StandardNumericNumFormat($numFmtId, \"$formatCode\")"
}

public class CustomNumericNumFormat(
    override val formatCode: String,
) : NumericNumFormat(formatCode), CustomNumFormat {

    override fun accepts(value: CellValue?): Boolean = when (value) {
        null               -> true
        is FormulaCellValue -> true
        is IntCellValue    -> true
        is TextCellValue   -> false
        is BoolCellValue   -> true
        is DoubleCellValue -> true
        is DateCellValue   -> false
        is TimeCellValue   -> false
        is DateTimeCellValue -> false
    }

    override fun toString(): String = "CustomNumericNumFormat(\"$formatCode\")"
}

// endregion

// region --- DateTimeNumFormat ---

private val DATE_OFFSET_MILLIS: Long by lazy {
    // 1899-12-30 00:00:00 UTC as epoch milliseconds
    LocalDateTime(1899, 12, 30, 0, 0, 0)
        .toInstant(TimeZone.UTC)
        .toEpochMilliseconds()
}

/**
 * Whether [serial] is a date Excel can show: from day 0 to 31 December 9999 (serial
 * 2958465). A negative number, or a larger one, in a date-formatted cell is displayed as
 * `####` and has no date or time to read, so it is surfaced as the number it is — as POI
 * does (`DateUtil.isValidExcelDate`). Reading it as a time used to throw, and one such
 * cell made the whole workbook impossible to open.
 */
internal fun isDateSerial(serial: Double): Boolean = serial >= 0.0 && serial < 2958466.0

/**
 * Days between 30 December 1899 and the date a 1900-system [serial] stands for.
 *
 * From 1 March 1900 the two are equal, which is why that date is used as the epoch. Before
 * it they differ by one: Excel counts a 29 February 1900 that never existed (serial 60), so
 * serial 1 is 1 January 1900, not 31 December 1899. POI makes the same correction
 * (`DateUtil.getJavaDate`, `wholeDays < 61`); serial 60 itself reads as 1 March.
 */
internal fun daysSinceEpoch(serial: Double): Double = if (serial >= 1.0 && serial < 61.0) serial + 1.0 else serial

/** The inverse of [daysSinceEpoch]: the serial to store for a date [days] after 30 December 1899. */
internal fun serialOf(days: Double): Double = if (days >= 1.0 && days < 61.0) days - 1.0 else days

/** Serials in the 1904 date system (§18.2.28, `date1904`) start 1,462 days later. */
internal const val DATE_1904_OFFSET_DAYS: Int = 1462

/**
 * The 1900-system serial for a number stored in a 1904-system workbook. A value below 1
 * is a time of day and has no date to move.
 */
internal fun serialFrom1904(v: String): String {
    val value = v.toDoubleOrNull() ?: return v
    if (value < 1.0) return v
    return v.toLongOrNull()?.let { (it + DATE_1904_OFFSET_DAYS).toString() } ?: (value + DATE_1904_OFFSET_DAYS).toString()
}

public sealed class DateTimeNumFormat(override val formatCode: String) : BaseNumFormat(formatCode) {

    override fun read(v: String): CellValue {
        if (v == "0") {
            return TimeCellValue(hour = 0, minute = 0, second = 0, millisecond = 0, microsecond = 0)
        }
        val value = v.toDouble()
        if (!isDateSerial(value)) return NumFormat.defaultNumeric.read(v)
        if (value < 1.0) {
            return TimeCellValue.fromFractionOfDay(value)
        }
        val deltaMs = (daysSinceEpoch(value) * 24 * 3600 * 1000).roundToLong()
        val utcDate = Instant.fromEpochMilliseconds(DATE_OFFSET_MILLIS + deltaMs)
            .toLocalDateTime(TimeZone.UTC)
        return if (!v.contains('.') || v.endsWith(".0")) {
            DateCellValue.fromLocalDateTime(utcDate)
        } else {
            DateTimeCellValue.fromLocalDateTime(utcDate)
        }
    }

    public fun writeDate(value: DateCellValue): String {
        val epochMs = value.asDateTimeUtc().toInstant(TimeZone.UTC).toEpochMilliseconds()
        val dayFractions = (epochMs - DATE_OFFSET_MILLIS).toDouble() / (1000 * 3600 * 24)
        return serialOf(dayFractions).toString()
    }

    public fun writeDateTime(value: DateTimeCellValue): String {
        val epochMs = value.asDateTimeUtc().toInstant(TimeZone.UTC).toEpochMilliseconds()
        val dayFractions = (epochMs - DATE_OFFSET_MILLIS).toDouble() / (1000 * 3600 * 24)
        return serialOf(dayFractions).toString()
    }

    override fun accepts(value: CellValue?): Boolean = when (value) {
        null               -> true
        is FormulaCellValue -> true
        is IntCellValue    -> false
        is TextCellValue   -> false
        is BoolCellValue   -> false
        is DoubleCellValue -> false
        is DateCellValue   -> true
        is DateTimeCellValue -> true
        is TimeCellValue   -> false
    }
}

public class StandardDateTimeNumFormat(
    override val numFmtId: Int,
    override val formatCode: String,
) : DateTimeNumFormat(formatCode), StandardNumFormat {
    override fun toString(): String = "StandardDateTimeNumFormat($numFmtId, \"$formatCode\")"
}

public class CustomDateTimeNumFormat(
    override val formatCode: String,
) : DateTimeNumFormat(formatCode), CustomNumFormat {
    override fun toString(): String = "CustomDateTimeNumFormat(\"$formatCode\")"
}

// endregion

// region --- TimeNumFormat ---

public sealed class TimeNumFormat(override val formatCode: String) : BaseNumFormat(formatCode) {

    override fun read(v: String): CellValue {
        if (v == "0") {
            return TimeCellValue(hour = 0, minute = 0, second = 0, millisecond = 0, microsecond = 0)
        }
        val value = v.toDouble()
        if (!isDateSerial(value)) return NumFormat.defaultNumeric.read(v)
        if (value < 1.0) {
            val deltaMs = (value * 24 * 3600 * 1000).roundToLong()
            val duration = deltaMs.milliseconds
            val totalSeconds = duration.inWholeSeconds
            val hours = (totalSeconds / 3600).toInt()
            val minutes = ((totalSeconds % 3600) / 60).toInt()
            val seconds = (totalSeconds % 60).toInt()
            val ms = (deltaMs % 1000).toInt()
            return TimeCellValue(
                hour = hours,
                minute = minutes,
                second = seconds,
                millisecond = ms,
                microsecond = 0
            )
        }
        val deltaMs = (daysSinceEpoch(value) * 24 * 3600 * 1000).roundToLong()
        val utcDate = Instant.fromEpochMilliseconds(DATE_OFFSET_MILLIS + deltaMs)
            .toLocalDateTime(TimeZone.UTC)
        return if (!v.contains('.') || v.endsWith(".0")) {
            DateCellValue(year = utcDate.year, month = utcDate.month.number, day = utcDate.day)
        } else {
            DateTimeCellValue(
                year = utcDate.year,
                month = utcDate.month.number,
                day = utcDate.day,
                hour = utcDate.hour,
                minute = utcDate.minute,
                second = utcDate.second,
                millisecond = utcDate.nanosecond / 1_000_000,
                microsecond = (utcDate.nanosecond / 1_000) % 1_000
            )
        }
    }

    public fun writeTime(value: TimeCellValue): String {
        val fractionOfDay = value.asDuration().inWholeMilliseconds.toDouble() / (1000 * 3600 * 24)
        return fractionOfDay.toString()
    }

    override fun accepts(value: CellValue?): Boolean = when (value) {
        null               -> true
        is FormulaCellValue -> true
        is IntCellValue -> false
        is TextCellValue -> false
        is BoolCellValue -> false
        is DoubleCellValue -> false
        is DateCellValue -> false
        is DateTimeCellValue -> false
        is TimeCellValue   -> true
    }
}

public class StandardTimeNumFormat(
    override val numFmtId: Int,
    override val formatCode: String,
) : TimeNumFormat(formatCode), StandardNumFormat {
    override fun toString(): String = "StandardTimeNumFormat($numFmtId, \"$formatCode\")"
}

public class CustomTimeNumFormat(
    override val formatCode: String,
) : TimeNumFormat(formatCode), CustomNumFormat {
    override fun toString(): String = "CustomTimeNumFormat(\"$formatCode\")"
}

// endregion

// region --- Standard formats map ---

/**
 * The built-in formats that have no named constant on [NumFormat]: currency (5-8) and
 * accounting (41-44), with the format codes of ECMA-376 §18.8.30. Without them a cell in one
 * of these formats — accounting is the default for money in Excel — read as `General`.
 */
private val unnamedStandardNumFormats: Map<Int, NumFormat> = mapOf(
    5 to StandardNumericNumFormat(5, "\"$\"#,##0_);(\"$\"#,##0)"),
    6 to StandardNumericNumFormat(6, "\"$\"#,##0_);[Red](\"$\"#,##0)"),
    7 to StandardNumericNumFormat(7, "\"$\"#,##0.00_);(\"$\"#,##0.00)"),
    8 to StandardNumericNumFormat(8, "\"$\"#,##0.00_);[Red](\"$\"#,##0.00)"),
    41 to StandardNumericNumFormat(41, "_(* #,##0_);_(* (#,##0);_(* \"-\"_);_(@_)"),
    42 to StandardNumericNumFormat(42, "_(\"$\"* #,##0_);_(\"$\"* (#,##0);_(\"$\"* \"-\"_);_(@_)"),
    43 to StandardNumericNumFormat(43, "_(* #,##0.00_);_(* (#,##0.00);_(* \"-\"??_);_(@_)"),
    44 to StandardNumericNumFormat(44, "_(\"$\"* #,##0.00_);_(\"$\"* (#,##0.00);_(\"$\"* \"-\"??_);_(@_)"),
)

internal val standardNumFormats: Map<Int, NumFormat> = unnamedStandardNumFormats + mapOf(
    0  to NumFormat.standard_0,
    1  to NumFormat.standard_1,
    2  to NumFormat.standard_2,
    3  to NumFormat.standard_3,
    4  to NumFormat.standard_4,
    9  to NumFormat.standard_9,
    10 to NumFormat.standard_10,
    11 to NumFormat.standard_11,
    12 to NumFormat.standard_12,
    13 to NumFormat.standard_13,
    14 to NumFormat.standard_14,
    15 to NumFormat.standard_15,
    16 to NumFormat.standard_16,
    17 to NumFormat.standard_17,
    18 to NumFormat.standard_18,
    19 to NumFormat.standard_19,
    20 to NumFormat.standard_20,
    21 to NumFormat.standard_21,
    22 to NumFormat.standard_22,
    37 to NumFormat.standard_37,
    38 to NumFormat.standard_38,
    39 to NumFormat.standard_39,
    40 to NumFormat.standard_40,
    45 to NumFormat.standard_45,
    46 to NumFormat.standard_46,
    47 to NumFormat.standard_47,
    48 to NumFormat.standard_48,
    49 to NumFormat.standard_49,
)

// endregion

// region --- formatCodeLooksLikeDateTime ---

private val ELAPSED_TIME = Regex("h+|m+|s+", RegexOption.IGNORE_CASE)
private val FRACTIONAL_SECONDS = Regex("""\.0+""")

/**
 * Whether [formatCode] formats a number as a date or a time (§18.8.31).
 *
 * Only the first section — the one for positive numbers — is examined, with everything
 * that is not a format code taken out first: text in quotes, a character after `\`, `_`
 * or `*`, and bracketed parts such as a colour (`[Red]`), a condition (`[>100]`) or a
 * locale (`[$-409]`). What is left is a date format if it has a date or time letter, in
 * either case, and no digit placeholder; an elapsed-time code (`[h]`, `[mm]`, `[ss]`) makes
 * it one outright.
 *
 * This follows POI's `DateUtil.isADateFormat`, with two deliberate differences where POI
 * goes against the specification: an escaped letter (`\d0`) is a literal, not a day code,
 * and quoted text may contain anything (`h"时"mm"分"`, `dd "de" mmmm`).
 *
 * The scan used to stop at the first of `y m d h s` found anywhere outside quotes, so the
 * `d` of `[Red]` or the `h` of `[White]` made a number format a date format, and it was
 * case-sensitive, so `DD/MM/YYYY` was not one.
 */
internal fun formatCodeLooksLikeDateTime(formatCode: String): Boolean {
    val codes = StringBuilder()
    var i = 0
    while (i < formatCode.length) {
        val c = formatCode[i]
        when (c) {
            ';' -> break
            '\\', '_', '*' -> i += 2
            '"' -> {
                val close = formatCode.indexOf('"', i + 1)
                i = if (close == -1) formatCode.length else close + 1
            }
            '[' -> {
                val close = formatCode.indexOf(']', i + 1)
                if (close == -1) { i = formatCode.length } else {
                    if (ELAPSED_TIME.matches(formatCode.substring(i + 1, close))) return true
                    i = close + 1
                }
            }
            else -> { codes.append(c); i++ }
        }
    }
    // `0` is a digit placeholder, except as fractional seconds: `ss.000`.
    val section = codes.toString().replace(FRACTIONAL_SECONDS, "")
    if (section.any { it == '0' || it == '#' || it == '?' }) return false
    return section.any { it in "yYmMdDhHsS" }
}

// endregion