package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.BoolCellValue
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.DateCellValue
import com.gyanoba.kexcel.sheet.DateTimeCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.TimeCellValue
import com.gyanoba.kexcel.shared_strings.TextSpan
import com.gyanoba.kexcel.utils.ColorType
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.getCellId
import com.gyanoba.kexcel.utils.getColumnAlphabet
import com.gyanoba.kexcel.utils.getSpanCellId
import com.gyanoba.kexcel.utils.lettersToNumeric
import com.gyanoba.kexcel.utils.toExcelColor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for the value model: [CellIndex], the [com.gyanoba.kexcel.sheet.CellValue]
 * hierarchy and [ExcelColor]. These need no workbook, so they run on every target.
 */
class CellValueTest {

    // region --- CellIndex ---

    @Test
    fun cellIndexFromString() {
        CellIndex.indexByString("A1").let {
            assertEquals(0, it.columnIndex)
            assertEquals(0, it.rowIndex)
        }
        CellIndex.indexByString("A2").let {
            assertEquals(0, it.columnIndex)
            assertEquals(1, it.rowIndex)
        }
        CellIndex.indexByString("C3").let {
            assertEquals(2, it.columnIndex)
            assertEquals(2, it.rowIndex)
        }
        // Two-letter columns: Z -> 25, AA -> 26, AB -> 27.
        assertEquals(25, CellIndex.indexByString("Z1").columnIndex)
        assertEquals(26, CellIndex.indexByString("AA1").columnIndex)
        assertEquals(27, CellIndex.indexByString("AB100").columnIndex)
        assertEquals(99, CellIndex.indexByString("AB100").rowIndex)
    }

    @Test
    fun cellIndexRoundTripsThroughCellId() {
        listOf("A1", "B2", "Z26", "AA1", "AZ99", "BA1000", "XFD1048576").forEach { id ->
            assertEquals(id, CellIndex.indexByString(id).cellId, "round trip failed for $id")
        }
    }

    @Test
    fun cellIndexEqualityAndToString() {
        val a = CellIndex.indexByColumnRow(columnIndex = 2, rowIndex = 4)
        val b = CellIndex.indexByString("C5")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals("C5", a.toString())
        assertNotEquals(a, CellIndex.indexByString("C6"))
        // (column, row) is not symmetric — B1 is not A2.
        assertNotEquals(
            CellIndex.indexByColumnRow(columnIndex = 1, rowIndex = 0),
            CellIndex.indexByColumnRow(columnIndex = 0, rowIndex = 1),
        )
    }

    @Test
    fun columnCoordinateHelpers() {
        assertEquals("A", getColumnAlphabet(0))
        assertEquals("Z", getColumnAlphabet(25))
        assertEquals("AA", getColumnAlphabet(26))
        assertEquals(1, lettersToNumeric("A"))
        assertEquals(27, lettersToNumeric("AA"))
        assertEquals("B3", getCellId(1, 2))
        assertEquals("A1:C3", getSpanCellId(startColumn = 0, startRow = 0, endColumn = 2, endRow = 2))
    }

    // endregion

    // region --- Scalar cell values ---

    @Test
    fun scalarCellValueEqualityAndToString() {
        assertEquals(IntCellValue(42), IntCellValue(42L))
        assertEquals(IntCellValue(42).hashCode(), IntCellValue(42L).hashCode())
        assertNotEquals<Any>(IntCellValue(42), DoubleCellValue(42.0))
        assertEquals("42", IntCellValue(42).toString())

        assertEquals(DoubleCellValue(12.5), DoubleCellValue(12.5))
        assertEquals("12.5", DoubleCellValue(12.5).toString())

        assertEquals(BoolCellValue(true), BoolCellValue(true))
        assertNotEquals(BoolCellValue(true), BoolCellValue(false))
        assertEquals("true", BoolCellValue(true).toString())

        assertEquals(FormulaCellValue("=SUM(A1:A2)"), FormulaCellValue("=SUM(A1:A2)"))
        assertEquals("=SUM(A1:A2)", FormulaCellValue("=SUM(A1:A2)").toString())

        assertEquals(TextCellValue("hi"), TextCellValue("hi"))
        assertNotEquals(TextCellValue("hi"), TextCellValue("ho"))
        assertEquals("hi", TextCellValue("hi").toString())
    }

    @Test
    fun textCellValueFromSpanConcatenatesChildren() {
        val span = TextSpan(
            children = listOf(TextSpan(text = "Hello "), TextSpan(text = "world")),
        )
        val value = TextCellValue.span(span)
        assertEquals("Hello world", value.toString())
        assertEquals(span, value.value)
        assertEquals(2, value.value.children!!.size)
        // A plain string builds a childless span carrying the text directly.
        assertEquals("plain", TextCellValue("plain").value.text)
    }

    // endregion

    // region --- Date / time cell values ---

    @Test
    fun dateCellValueConversions() {
        val date = DateCellValue(year = 2023, month = 4, day = 20)
        assertEquals(LocalDate(2023, 4, 20), date.asLocalDate())
        assertEquals(LocalDateTime(2023, 4, 20, 0, 0, 0), date.asDateTimeUtc())
        assertEquals("2023-04-20", date.toString())
        assertEquals(date, DateCellValue.fromLocalDate(LocalDate(2023, 4, 20)))
        assertEquals(date, DateCellValue.fromLocalDateTime(LocalDateTime(2023, 4, 20, 15, 44, 13)))
    }

    @Test
    fun dateCellValueRejectsImpossibleFields() {
        assertFailsWith<IllegalArgumentException> { DateCellValue(year = 2023, month = 13, day = 1) }
        assertFailsWith<IllegalArgumentException> { DateCellValue(year = 2023, month = 0, day = 1) }
        assertFailsWith<IllegalArgumentException> { DateCellValue(year = 2023, month = 1, day = 32) }
    }

    @Test
    fun timeCellValueConversions() {
        val time = TimeCellValue(hour = 2, minute = 20, second = 10)
        assertEquals(2.hours + 20.minutes + 10.seconds, time.asDuration())
        assertEquals("02:20:10", time.toString())

        // Half a day is noon; a full day is 24:00 rather than rolling over to 0.
        assertEquals(TimeCellValue(hour = 12), TimeCellValue.fromFractionOfDay(0.5))
        assertEquals(TimeCellValue(hour = 6, minute = 0), TimeCellValue.fromFractionOfDay(0.25))
        assertEquals(TimeCellValue(hour = 24), TimeCellValue.fromFractionOfDay(1.0))

        assertEquals(
            TimeCellValue(hour = 1, minute = 30, second = 0, millisecond = 500),
            TimeCellValue.fromDuration(1.hours + 30.minutes + 500.milliseconds),
        )
        // Durations beyond a day are kept as an hour count, not wrapped.
        assertEquals(TimeCellValue(hour = 26, minute = 5), TimeCellValue.fromDuration(26.hours + 5.minutes))

        assertEquals(
            TimeCellValue(hour = 15, minute = 44, second = 13),
            TimeCellValue.fromLocalDateTime(LocalDateTime(2023, 4, 20, 15, 44, 13)),
        )
    }

    @Test
    fun dateTimeCellValueConversions() {
        val dt = DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 13)
        assertEquals(LocalDateTime(2023, 4, 20, 15, 44, 13), dt.asDateTimeLocal())
        assertEquals(dt.asDateTimeLocal(), dt.asDateTimeUtc())
        assertEquals("2023-04-20T15:44:13", dt.toString())
        assertEquals(dt, DateTimeCellValue.fromLocalDateTime(LocalDateTime(2023, 4, 20, 15, 44, 13)))
        assertNotEquals(dt, DateTimeCellValue(year = 2023, month = 4, day = 20, hour = 15, minute = 44, second = 14))
    }

    @Test
    fun dateTimeCellValueKeepsSubSecondPrecision() {
        val dt = DateTimeCellValue.fromLocalDateTime(
            LocalDateTime(2023, 4, 20, 15, 44, 13, 123_456_000),
        )
        assertEquals(123, dt.millisecond)
        assertEquals(456, dt.microsecond)
        assertEquals(123_456_000, dt.asDateTimeLocal().nanosecond)
    }

    // endregion

    // region --- ExcelColor ---

    @Test
    fun namedColorsCarryHexAndType() {
        assertEquals("FFF44336", ExcelColor.red.colorHex)
        assertEquals(ColorType.Material, ExcelColor.red.type)
        assertEquals(ColorType.Color, ExcelColor.black.type)
        assertEquals(ColorType.MaterialAccent, ExcelColor.redAccent.type)
        assertEquals("none", ExcelColor.none.colorHex)
        // `values` is the palette the styling API offers; `none` is deliberately not in it.
        assertTrue(ExcelColor.values.contains(ExcelColor.red))
        assertFalse(ExcelColor.values.contains(ExcelColor.none))
        assertTrue(ExcelColor.values.all { it.type != null && it.name != null })
    }

    @Test
    fun colorsFromHexResolveBackToNamedConstants() {
        assertEquals(ExcelColor.red, "FFF44336".toExcelColor())
        assertEquals(ExcelColor.none, "none".toExcelColor())
        // A valid hex with no named equivalent stays anonymous but keeps its value.
        val custom = "FF123456".toExcelColor()
        assertEquals("FF123456", custom.colorHex)
        assertEquals(null, custom.name)
        // Anything that is not a hex string falls back to black rather than throwing.
        assertEquals(ExcelColor.black, "not-a-color".toExcelColor())
    }

    @Test
    fun colorsRoundTripThroughInts() {
        val color = ExcelColor.fromInt(0x2196F3)
        assertEquals("2196F3", color.colorHex)
        assertEquals(0x2196F3, color.colorInt)
        assertEquals(ExcelColor.fromHexString("2196F3"), color)
        assertEquals("0", ExcelColor.fromInt(0).colorHex)
    }

    @Test
    fun sevenAndNineCharacterHexAreNormalised() {
        // CellStyle stores colors through isColorAppropriate(): "#RRGGBB" gains an FF alpha
        // and "#AARRGGBB" just loses the hash.
        assertEquals("FF2196F3", com.gyanoba.kexcel.utils.isColorAppropriate("#2196F3"))
        assertEquals("802196F3", com.gyanoba.kexcel.utils.isColorAppropriate("#802196F3"))
        assertEquals("FF2196F3", com.gyanoba.kexcel.utils.isColorAppropriate("FF2196F3"))
    }

    // endregion
}
