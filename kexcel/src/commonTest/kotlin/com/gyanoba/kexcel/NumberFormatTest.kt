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
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the `number_format` package: how format codes are classified, which cell
 * values each format accepts, how serialized cell text is read back into a
 * [com.gyanoba.kexcel.sheet.CellValue], the custom-format id registry, and how default
 * and custom formats survive a save through a real workbook.
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
        // Serial 1 is 1 January 1900. This asserted 31 December 1899, which is what a plain
        // offset from 30 December 1899 gives; Excel and POI (DateUtil.getJavaDate) count a
        // 29 February 1900 that never existed, so every serial below 61 is a day later.
        assertEquals(DateCellValue(year = 1900, month = 1, day = 1), fmt.read("1"))
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
        // (This used 5 as its example; 5-8 and 41-44 are assigned, ECMA-376 §18.8.30.)
        assertNull(maintainer.getByNumFmtId(23))
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

    // region --- Formats through a workbook ---

    // Dart: 'Testing customNumFormats'
    @Test
    fun customNumFormats() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]

        val format1 = CustomNumericNumFormat(formatCode = "0.00%")
        val format2 = CustomNumericNumFormat(formatCode = "#,##0.00")

        sheet.updateCell(
            CellIndex.indexByString("A1"),
            DoubleCellValue(0.15),
            cellStyle = CellStyle(numberFormat = format1),
        )
        sheet.updateCell(
            CellIndex.indexByString("B1"),
            DoubleCellValue(123456.789),
            cellStyle = CellStyle(numberFormat = format2),
        )

        val bytes = excel.encode()
        assertNotNull(bytes)

        val excel2 = Excel.decodeBytes(bytes)
        val sheet2 = excel2["Sheet1"]
        val a1 = sheet2.cell(CellIndex.indexByString("A1"))
        val b1 = sheet2.cell(CellIndex.indexByString("B1"))

        assertEquals(format1, a1.cellStyle?.numberFormat)
        assertEquals(DoubleCellValue(0.15), a1.value)
        assertEquals(format2, b1.cellStyle?.numberFormat)
        assertEquals(DoubleCellValue(123456.789), b1.value)
    }

    // Dart: 'Saving XLSX File with appendRow'
    @Test
    fun savingXlsxWithAppendRow() {
        val excel = Excel.createExcel()
        val sheet = excel["Sheet1"]

        sheet.appendRow(
            listOf(
                IntCellValue(8),
                DoubleCellValue(999.62221),
                DateCellValue(year = 2023, month = 4, day = 20),
                DateTimeCellValue(
                    year = 2023, month = 4, day = 20,
                    hour = 15, minute = 44, second = 13,
                ),
                TextCellValue("value"),
            )
        )

        val fileBytes = excel.encode()
        assertNotNull(fileBytes)

        val newExcel = Excel.decodeBytes(fileBytes)
        assertEquals(1, newExcel.tables.size)
        val s = newExcel.tables["Sheet1"]!!
        assertEquals(5, s.maxColumns)

        assertEquals(IntCellValue(8), s.rows[0][0]!!.value)
        assertEquals(
            NumFormat.defaultNumeric.toString(),
            s.rows[0][0]!!.cellStyle?.numberFormat.toString(),
        )

        assertEquals(DoubleCellValue(999.62221), s.rows[0][1]!!.value)
        assertEquals(
            NumFormat.defaultFloat.toString(),
            s.rows[0][1]!!.cellStyle?.numberFormat.toString(),
        )

        assertEquals(DateCellValue(year = 2023, month = 4, day = 20), s.rows[0][2]!!.value)
        assertEquals(
            NumFormat.defaultDate.toString(),
            s.rows[0][2]!!.cellStyle?.numberFormat.toString(),
        )

        assertEquals(
            DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13),
            s.rows[0][3]!!.value,
        )
        assertEquals(
            NumFormat.defaultDateTime.toString(),
            s.rows[0][3]!!.cellStyle?.numberFormat.toString(),
        )

        assertEquals(TextCellValue("value"), s.rows[0][4]!!.value)
        assertEquals(
            NumFormat.standard_0.toString(),
            s.rows[0][4]!!.cellStyle?.numberFormat.toString(),
        )
    }

    // endregion


    // region --- Date detection, the 1900 calendar and built-in formats ---

    @Test
    fun aFormatIsADateFormatOnlyIfItsFirstSectionHasDateCodes() {
        // Expected values are what POI's DateUtil.isADateFormat returns for the same code,
        // except where noted. The old scan stopped at the first y/m/d/h/s outside quotes:
        // the `d` of `[Red]` made a number format a date format, and upper-case codes were missed.
        val dates = listOf(
            "yyyy-mm-dd", "DD/MM/YYYY", "YYYY-MM-DD HH:MM", "D-MMM-YY", "mmm", "[h]:mm", "hh:mm:ss.000",
            "[\$-409]d-mmm-yy;@", "[\$-F800]dddd, mmmm dd, yyyy", "[\$-409]h:mm AM/PM;@", "m/d/yyyy;@", "mm:ss.0",
            "yyyy\"年\"m\"月\"d\"日\"",
        )
        val numbers = listOf(
            "[Red]0.00", "[White]0", "[Cyan]0.0", "[Blue]0", "[\$Rs.-4009] #,##0.00", "[Red][<0]0.00;0.00",
            "#,##0_);[Red](#,##0)", "0.00E+00", "# ?/?", "General", "General;[Red]-General", "@",
            "0 \"days\"", "\"Total: \"0", "0\"s\"", "#\"h\"", "0.0 \\m", "#,##0.00 [\$USD]", "[>100][Green]0;0",
            "0.0,,\"M\"", "[DBNum1][\$-804]General", "0 h", "_(* #,##0.00_);_(* (#,##0.00);_(* \"-\"??_);_(@_)",
        )
        dates.forEach { assertTrue(NumFormat.custom(it) is DateTimeNumFormat, "$it should be a date format") }
        numbers.forEach { assertTrue(NumFormat.custom(it) is CustomNumericNumFormat, "$it should be a number format") }

        // Where POI goes against §18.8.31. A character after a backslash is a literal:
        assertTrue(NumFormat.custom("\\d0") is CustomNumericNumFormat)
        // quoted text may hold any letters:
        assertTrue(NumFormat.custom("h\"时\"mm\"分\"ss\"秒\";@") is DateTimeNumFormat)
        assertTrue(NumFormat.custom("dd \"de\" mmmm") is DateTimeNumFormat)
        // and [ss] is elapsed seconds, not a colour:
        assertTrue(NumFormat.custom("[ss].00") is DateTimeNumFormat)
    }

    @Test
    fun serialsBeforeMarch1900AreOneDayLaterThanAPlainOffsetGives() {
        // Excel's 1900 calendar has a 29 February 1900 (serial 60). POI corrects for it in
        // DateUtil.getJavaDate (`wholeDays < 61`), and so must a reader that agrees with Excel.
        val fmt = NumFormat.defaultDate
        assertEquals(DateCellValue(1900, 1, 1), fmt.read("1"))
        assertEquals(DateCellValue(1900, 1, 2), fmt.read("2"))
        assertEquals(DateCellValue(1900, 2, 28), fmt.read("59"))
        assertEquals(DateCellValue(1900, 3, 1), fmt.read("60")) // the day that never was; POI reads 1 March too
        assertEquals(DateCellValue(1900, 3, 1), fmt.read("61"))
        assertEquals(DateCellValue(1900, 3, 2), fmt.read("62"))

        val writer = NumFormat.defaultDate as DateTimeNumFormat
        assertEquals("1.0", writer.writeDate(DateCellValue(1900, 1, 1)))
        assertEquals("59.0", writer.writeDate(DateCellValue(1900, 2, 28)))
        assertEquals("61.0", writer.writeDate(DateCellValue(1900, 3, 1)))
        assertEquals("45036.0", writer.writeDate(DateCellValue(2023, 4, 20)))
    }

    @Test
    fun everyWholeSecondOfTheDayIsReadExactly() {
        // 11 seconds is stored as 1.273148148148148E-4 of a day, which is 10999.99… ms.
        // Truncating instead of rounding read 11,593 of these 86,399 values a millisecond short.
        val formats = listOf(NumFormat.custom("hh:mm:ss"), NumFormat.defaultDate, NumFormat.defaultTime)
        for (format in formats) {
            for (second in 1 until 86_400) {
                val value = format.read((second / 86_400.0).toString()) as TimeCellValue
                assertEquals(
                    second * 1000,
                    ((value.hour * 60 + value.minute) * 60 + value.second) * 1000 + value.millisecond,
                    "$format read second $second as $value.${value.millisecond}",
                )
            }
        }
    }

    @Test
    fun aNumberOutsideTheDateRangeIsReadAsANumber() {
        // Excel shows `####` for these; there is no date in them.
        assertEquals(IntCellValue(-5), NumFormat.defaultDate.read("-5"))
        assertEquals(DoubleCellValue(-0.25), NumFormat.defaultTime.read("-0.25"))
        assertEquals(DoubleCellValue(1.0E12), NumFormat.defaultDate.read("1.0E12"))
    }

    @Test
    fun theBuiltInFormatCodesAreTheOnesTheSpecificationLists() {
        // ECMA-376 §18.8.30. 21, 40 and 48 were mistyped (`h:mm:dd`, `[Red](#,#)`, `##0.0`).
        assertEquals("h:mm:ss", NumFormat.standard_21.formatCode)
        assertEquals("#,##0.00;[Red](#,##0.00)", NumFormat.standard_40.formatCode)
        assertEquals("##0.0E+0", NumFormat.standard_48.formatCode)

        val maintainer = NumFormatMaintainer()
        assertEquals("\"\$\"#,##0_);(\"\$\"#,##0)", maintainer.getByNumFmtId(5)?.formatCode)
        assertEquals("_(\"\$\"* #,##0.00_);_(\"\$\"* (#,##0.00);_(\"\$\"* \"-\"??_);_(@_)", maintainer.getByNumFmtId(44)?.formatCode)
        for (id in listOf(5, 6, 7, 8, 41, 42, 43, 44)) {
            assertEquals(id, (maintainer.getByNumFmtId(id) as StandardNumFormat).numFmtId)
        }
    }

    // endregion
}
