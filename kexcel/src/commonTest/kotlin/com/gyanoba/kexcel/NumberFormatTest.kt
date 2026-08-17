package com.gyanoba.kexcel

import com.gyanoba.kexcel.number_format.CustomDateTimeNumFormat
import com.gyanoba.kexcel.number_format.CustomNumericNumFormat
import com.gyanoba.kexcel.number_format.CustomTimeNumFormat
import com.gyanoba.kexcel.number_format.DateTimeNumFormat
import com.gyanoba.kexcel.number_format.TimeNumFormat
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.number_format.NumFormatMaintainer
import com.gyanoba.kexcel.number_format.StandardNumFormat
import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the `number_format` package: how format codes are classified, which cell
 * values each format accepts, how serialized cell text is read back into a
 * [com.gyanoba.kexcel.sheet.CellValue], and the custom-format id registry.
 */
class NumberFormatTest {

    // region --- Classification ---

    @Test
    fun customFormatCodesAreClassifiedByShape() {
        assertTrue(NumFormat.custom("yyyy-mm-dd") is CustomDateTimeNumFormat)
        assertTrue(NumFormat.custom("dd/mm/yy hh:mm") is CustomDateTimeNumFormat)
        assertTrue(NumFormat.custom("#,##0.00") is CustomNumericNumFormat)
        assertTrue(NumFormat.custom("0.00%") is CustomNumericNumFormat)
        // "General" is numeric even though it contains no digits.
        assertTrue(NumFormat.custom("General") is CustomNumericNumFormat)
        // A ';' section separator only appears in numeric formats, so it wins over the
        // 'd' that follows it.
        assertTrue(NumFormat.custom("#,##0;[Red]-#,##0") is CustomNumericNumFormat)
        // Escaped and quoted letters are literals, not date fields.
        assertTrue(NumFormat.custom("""0\m\²""") is CustomNumericNumFormat)
        assertTrue(NumFormat.custom("""0" days"""") is CustomNumericNumFormat)
    }

    @Test
    fun standardFormatsExposeTheirNumFmtId() {
        assertEquals(0, (NumFormat.standard_0 as StandardNumFormat).numFmtId)
        assertEquals(14, (NumFormat.defaultDate as StandardNumFormat).numFmtId)
        assertEquals(20, (NumFormat.defaultTime as StandardNumFormat).numFmtId)
        assertEquals(22, (NumFormat.defaultDateTime as StandardNumFormat).numFmtId)
        assertEquals("General", NumFormat.standard_0.formatCode)
        assertEquals("0.00", NumFormat.defaultFloat.formatCode)
        assertEquals("@", NumFormat.standard_49.formatCode)
    }

    @Test
    fun formatEqualityComparesTypeAndCode() {
        assertEquals(CustomNumericNumFormat("0.00%"), CustomNumericNumFormat("0.00%"))
        assertEquals(CustomNumericNumFormat("0.00%").hashCode(), CustomNumericNumFormat("0.00%").hashCode())
        assertNotEquals<Any>(CustomNumericNumFormat("0.00%"), CustomNumericNumFormat("0.0%"))
        // Same code, different kind of format — not interchangeable.
        assertNotEquals<Any>(CustomNumericNumFormat("h:mm"), CustomTimeNumFormat("h:mm"))
        // A standard format is not equal to a custom one carrying the same code.
        assertNotEquals<Any>(NumFormat.standard_2, CustomNumericNumFormat("0.00"))
    }

    // endregion

    // region --- defaultFor / accepts ---

    @Test
    fun defaultFormatPerValueType() {
        assertEquals(NumFormat.standard_0, NumFormat.defaultFor(null))
        assertEquals(NumFormat.standard_0, NumFormat.defaultFor(TextCellValue("x")))
        assertEquals(NumFormat.standard_0, NumFormat.defaultFor(FormulaCellValue("=A1")))
        assertEquals(NumFormat.defaultNumeric, NumFormat.defaultFor(IntCellValue(1)))
        assertEquals(NumFormat.defaultFloat, NumFormat.defaultFor(DoubleCellValue(1.5)))
        assertEquals(NumFormat.defaultBool, NumFormat.defaultFor(BoolCellValue(true)))
        assertEquals(NumFormat.defaultDate, NumFormat.defaultFor(DateCellValue(2023, 4, 20)))
        assertEquals(NumFormat.defaultTime, NumFormat.defaultFor(TimeCellValue(hour = 1)))
        assertEquals(
            NumFormat.defaultDateTime,
            NumFormat.defaultFor(DateTimeCellValue(2023, 4, 20, 1, 2)),
        )
    }

    @Test
    fun everyDefaultFormatAcceptsItsOwnValueType() {
        val values = listOf(
            null,
            TextCellValue("x"),
            FormulaCellValue("=A1"),
            IntCellValue(1),
            DoubleCellValue(1.5),
            BoolCellValue(true),
            DateCellValue(2023, 4, 20),
            TimeCellValue(hour = 1),
            DateTimeCellValue(2023, 4, 20, 1, 2),
        )
        values.forEach { value ->
            assertTrue(
                NumFormat.defaultFor(value).accepts(value),
                "${NumFormat.defaultFor(value)} should accept $value",
            )
        }
    }

    @Test
    fun numericFormatsRejectDatesAndTimes() {
        val numeric = NumFormat.standard_2
        assertTrue(numeric.accepts(IntCellValue(1)))
        assertTrue(numeric.accepts(DoubleCellValue(1.5)))
        assertTrue(numeric.accepts(BoolCellValue(true)))
        assertTrue(numeric.accepts(null))
        assertTrue(numeric.accepts(FormulaCellValue("=A1")))
        assertFalse(numeric.accepts(DateCellValue(2023, 4, 20)))
        assertFalse(numeric.accepts(TimeCellValue(hour = 1)))
        assertFalse(numeric.accepts(DateTimeCellValue(2023, 4, 20, 1, 2)))

        // Text only fits the "General" standard format; every other numeric format
        // (standard or custom) rejects it.
        assertTrue(NumFormat.standard_0.accepts(TextCellValue("x")))
        assertFalse(NumFormat.standard_49.accepts(TextCellValue("x")))
        assertFalse(CustomNumericNumFormat("0.00").accepts(TextCellValue("x")))
    }

    @Test
    fun dateAndTimeFormatsAreMutuallyExclusive() {
        val date = NumFormat.defaultDate
        assertTrue(date.accepts(DateCellValue(2023, 4, 20)))
        assertTrue(date.accepts(DateTimeCellValue(2023, 4, 20, 1, 2)))
        assertFalse(date.accepts(TimeCellValue(hour = 1)))
        assertFalse(date.accepts(IntCellValue(1)))

        val time = NumFormat.defaultTime
        assertTrue(time.accepts(TimeCellValue(hour = 1)))
        assertFalse(time.accepts(DateCellValue(2023, 4, 20)))
        assertFalse(time.accepts(DateTimeCellValue(2023, 4, 20, 1, 2)))
        assertFalse(time.accepts(DoubleCellValue(1.0)))

        // A null or formula cell is compatible with any format.
        listOf(date, time, NumFormat.standard_0).forEach {
            assertTrue(it.accepts(null))
            assertTrue(it.accepts(FormulaCellValue("=A1")))
        }
    }

    // endregion

    // region --- read() ---

    @Test
    fun numericFormatsReadWholeNumbersAsInts() {
        val fmt = NumFormat.standard_2
        assertEquals(IntCellValue(42), fmt.read("42"))
        assertEquals(IntCellValue(-7), fmt.read("-7"))
        // Trailing zeroes carry no information, so these stay integers.
        assertEquals(IntCellValue(42), fmt.read("42.0"))
        assertEquals(IntCellValue(42), fmt.read("42.000"))
        assertEquals(DoubleCellValue(12.3), fmt.read("12.3"))
        assertEquals(DoubleCellValue(0.05), fmt.read("0.05"))
    }

    @Test
    fun dateTimeFormatsReadSerialNumbers() {
        val fmt = NumFormat.defaultDate
        // Excel serial 45036 is 2023-04-20; an integral serial has no time part.
        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), fmt.read("45036"))
        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), fmt.read("45036.0"))
        // The fractional part becomes the time of day.
        assertEquals(
            DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 12, minute = 0, second = 0),
            fmt.read("45036.5"),
        )
        // Serial 0 and sub-1 serials carry no date, only a time.
        assertEquals(TimeCellValue(), fmt.read("0"))
        assertEquals(TimeCellValue(hour = 6), fmt.read("0.25"))
    }

    @Test
    fun timeFormatsReadFractionsOfADay() {
        val fmt = NumFormat.defaultTime
        assertEquals(TimeCellValue(), fmt.read("0"))
        assertEquals(TimeCellValue(hour = 12), fmt.read("0.5"))
        assertEquals(TimeCellValue(hour = 2, minute = 20, second = 10), fmt.read("0.09733796296296296"))
        // Serials of 1 and above are dates, matching how Excel overflows a time column.
        assertEquals(DateCellValue(year = 1899, month = 12, day = 31), fmt.read("1"))
    }

    @Test
    fun serialNumbersSurviveAWriteReadRoundTrip() {
        val dateFmt = NumFormat.defaultDate as DateTimeNumFormat
        val date = DateCellValue(year = 2023, month = 4, day = 20)
        assertEquals(date, dateFmt.read(dateFmt.writeDate(date)))

        val dateTimeFmt = NumFormat.defaultDateTime as DateTimeNumFormat
        val dateTime = DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13)
        assertEquals(dateTime, dateTimeFmt.read(dateTimeFmt.writeDateTime(dateTime)))

        val timeFmt = NumFormat.defaultTime as TimeNumFormat
        val time = TimeCellValue(hour = 2, minute = 20, second = 10)
        assertEquals(time, timeFmt.read(timeFmt.writeTime(time)))
    }

    // endregion

    // region --- NumFormatMaintainer ---

    @Test
    fun maintainerKnowsEveryStandardFormatUpFront() {
        val maintainer = NumFormatMaintainer()
        assertEquals(NumFormat.standard_0, maintainer.getByNumFmtId(0))
        assertEquals(NumFormat.standard_14, maintainer.getByNumFmtId(14))
        assertEquals(NumFormat.standard_49, maintainer.getByNumFmtId(49))
        // Ids that OOXML leaves unassigned (and unregistered custom ids) are unknown.
        assertNull(maintainer.getByNumFmtId(5))
        assertNull(maintainer.getByNumFmtId(164))
    }

    @Test
    fun maintainerAssignsCustomIdsFrom164AndDeduplicates() {
        val maintainer = NumFormatMaintainer()
        val percent = CustomNumericNumFormat("0.000%")
        val thousands = CustomNumericNumFormat("#,##0.000")

        val firstId = maintainer.findOrAdd(percent)
        assertEquals(164, firstId)
        assertEquals(165, maintainer.findOrAdd(thousands))
        // Asking again for a known format reuses its id instead of burning a new one.
        assertEquals(164, maintainer.findOrAdd(percent))
        assertEquals(164, maintainer.findOrAdd(CustomNumericNumFormat("0.000%")))
        assertEquals(percent, maintainer.getByNumFmtId(164))
        assertEquals(thousands, maintainer.getByNumFmtId(165))
    }

    @Test
    fun maintainerAcceptsExplicitCustomIdsAndRejectsBadOnes() {
        val maintainer = NumFormatMaintainer()
        maintainer.add(200, CustomNumericNumFormat("0.0000"))
        assertEquals(CustomNumericNumFormat("0.0000"), maintainer.getByNumFmtId(200))
        // The next generated id continues past the highest explicit one.
        assertEquals(201, maintainer.findOrAdd(CustomNumericNumFormat("0.00000")))

        // Reusing an id, or claiming one from the standard range, is rejected.
        assertFailsWith<IllegalArgumentException> {
            maintainer.add(200, CustomNumericNumFormat("different"))
        }
        assertFailsWith<IllegalArgumentException> {
            maintainer.add(14, CustomNumericNumFormat("also-different"))
        }
    }

    @Test
    fun maintainerClearForgetsCustomFormatsOnly() {
        val maintainer = NumFormatMaintainer()
        maintainer.findOrAdd(CustomNumericNumFormat("0.000%"))
        maintainer.clear()

        assertNull(maintainer.getByNumFmtId(164))
        assertEquals(NumFormat.standard_14, maintainer.getByNumFmtId(14))
        // Ids restart from the bottom of the custom range.
        assertEquals(164, maintainer.findOrAdd(CustomNumericNumFormat("brand-new")))
    }

    // endregion
}
