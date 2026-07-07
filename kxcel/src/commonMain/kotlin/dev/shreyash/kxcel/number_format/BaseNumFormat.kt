package dev.shreyash.kxcel.number_format

import dev.shreyash.kxcel.sheet.BoolCellValue
import dev.shreyash.kxcel.sheet.CellValue
import dev.shreyash.kxcel.sheet.DateCellValue
import dev.shreyash.kxcel.sheet.DateTimeCellValue
import dev.shreyash.kxcel.sheet.DoubleCellValue
import dev.shreyash.kxcel.sheet.FormulaCellValue
import dev.shreyash.kxcel.sheet.IntCellValue
import dev.shreyash.kxcel.sheet.TextCellValue
import dev.shreyash.kxcel.sheet.TimeCellValue
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
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
        public val standard_21: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 21, formatCode = "h:mm:dd")
        public val standard_22: StandardDateTimeNumFormat = StandardDateTimeNumFormat(numFmtId = 22, formatCode = "m/d/yy h:mm")
        public val standard_37: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 37, formatCode = "#,##0 ;(#,##0)")
        public val standard_38: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 38, formatCode = "#,##0 ;[Red](#,##0)")
        public val standard_39: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 39, formatCode = "#,##0.00;(#,##0.00)")
        public val standard_40: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 40, formatCode = "#,##0.00;[Red](#,#)")
        public val standard_45: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 45, formatCode = "mm:ss")
        public val standard_46: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 46, formatCode = "[h]:mm:ss")
        public val standard_47: StandardTimeNumFormat = StandardTimeNumFormat(numFmtId = 47, formatCode = "mmss.0")
        public val standard_48: StandardNumericNumFormat = StandardNumericNumFormat(numFmtId = 48, formatCode = "##0.0")
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
        val eIdx = v.indexOf('E')
        val decimalIdx = v.indexOf('.')

        if (decimalIdx == -1 && eIdx == -1) {
            return IntCellValue(v.toLong())
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
            return IntCellValue(v.substring(0, decimalIdx).toLong())
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

public sealed class DateTimeNumFormat(override val formatCode: String) : BaseNumFormat(formatCode) {

    override fun read(v: String): CellValue {
        if (v == "0") {
            return TimeCellValue(hour = 0, minute = 0, second = 0, millisecond = 0, microsecond = 0)
        }
        val value = v.toDouble()
        if (value < 1.0) {
            return TimeCellValue.fromFractionOfDay(value)
        }
        val deltaMs = (value * 24 * 3600 * 1000).roundToLong()
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
        return dayFractions.toString()
    }

    public fun writeDateTime(value: DateTimeCellValue): String {
        val epochMs = value.asDateTimeUtc().toInstant(TimeZone.UTC).toEpochMilliseconds()
        val dayFractions = (epochMs - DATE_OFFSET_MILLIS).toDouble() / (1000 * 3600 * 24)
        return dayFractions.toString()
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
        val deltaMs = (value * 24 * 3600 * 1000).roundToLong()
        val utcDate = Instant.fromEpochMilliseconds(DATE_OFFSET_MILLIS + deltaMs)
            .toLocalDateTime(TimeZone.UTC)
        return if (!v.contains('.') || v.endsWith(".0")) {
            DateCellValue(year = utcDate.year, month = utcDate.monthNumber, day = utcDate.dayOfMonth)
        } else {
            DateTimeCellValue(
                year = utcDate.year,
                month = utcDate.monthNumber,
                day = utcDate.dayOfMonth,
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

internal val standardNumFormats: Map<Int, NumFormat> = mapOf(
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

internal fun formatCodeLooksLikeDateTime(formatCode: String): Boolean {
    var inEscape = false
    var inQuotes = false
    for (c in formatCode) {
        when {
            inEscape -> {
                inEscape = false
            }
            c == '\\' -> {
                inEscape = true
            }
            inQuotes -> {
                if (c == '"') inQuotes = false
            }
            c == '"' -> {
                inQuotes = true
            }
            else -> when (c) {
                'y', 'm', 'd', 'h', 's' -> return true
                ';' -> return false  // separator only exists for decimal formats
                else -> { /* continue */ }
            }
        }
    }
    return false
}

// endregion