# Reading & Writing Cells

Every cell value in Kexcel is wrapped in a **`CellValue`** — a sealed hierarchy of
typed values. This page covers addressing cells, the value types, writing, and
the several ways to read data back.

## Addressing cells with `CellIndex`

Cells are addressed with a `CellIndex`. Rows and columns are **0-based**:

```kotlin
import com.gyanoba.kexcel.sheet.CellIndex

CellIndex.indexByString("A1")                            // column 0, row 0
CellIndex.indexByString("C5")                            // column 2, row 4
CellIndex.indexByColumnRow(columnIndex = 2, rowIndex = 4) // also "C5"
```

A `CellIndex` can turn itself back into an `"A1"`-style string:

```kotlin
val id = CellIndex.indexByColumnRow(2, 4).cellId // "C5"
```

!!! warning "`cellId` is not free"
    Converting an index back to a string is relatively expensive — avoid it in
    tight loops.

## Cell value types

`CellValue` is `sealed`, so the compiler can check that a `when` covers every
case.

| Type | Constructor | Holds |
| --- | --- | --- |
| `TextCellValue` | `TextCellValue("hello")` | text |
| `IntCellValue` | `IntCellValue(42)` | `Long` (accepts `Int`) |
| `DoubleCellValue` | `DoubleCellValue(3.14)` | `Double` |
| `BoolCellValue` | `BoolCellValue(true)` | `Boolean` |
| `DateCellValue` | `DateCellValue(year = 2026, month = 7, day = 9)` | a calendar date |
| `TimeCellValue` | `TimeCellValue(hour = 14, minute = 30)` | a time of day |
| `DateTimeCellValue` | `DateTimeCellValue(2026, 7, 9, 14, 30)` | date + time |
| `FormulaCellValue` | `FormulaCellValue("=SUM(A1:A10)")` | a formula string |

```kotlin
import com.gyanoba.kexcel.sheet.*

TextCellValue("Some text")
IntCellValue(42)
DoubleCellValue(3.14)
BoolCellValue(true)
DateCellValue(year = 2026, month = 7, day = 9)
TimeCellValue(hour = 14, minute = 30, second = 0)
DateTimeCellValue(year = 2026, month = 7, day = 9, hour = 14, minute = 30)
FormulaCellValue("=SUM(A1:A10)")
```

### Working with `kotlinx-datetime`

The date/time value types convert to and from `kotlinx.datetime` types:

```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

DateCellValue.fromLocalDate(LocalDate(2026, 7, 9))
DateTimeCellValue.fromLocalDateTime(LocalDateTime(2026, 7, 9, 14, 30))

val date = DateCellValue(2026, 7, 9).asLocalDate()          // LocalDate
val dt = DateTimeCellValue(2026, 7, 9, 14, 30).asDateTimeLocal() // LocalDateTime
```

`TimeCellValue` also builds from a `Duration` or a fraction of a day:

```kotlin
import kotlin.time.Duration.Companion.hours

TimeCellValue.fromDuration(1.5.hours)      // 01:30:00
TimeCellValue.fromFractionOfDay(0.5)       // 12:00:00
```

## Writing values

Use `updateCell`, either on the sheet or on the workbook (the two are equivalent —
the workbook overload just takes a sheet name first):

```kotlin
val sheet = excel["Sheet1"]

// Via the sheet
sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Hello"))

// Via the workbook
excel.updateCell("Sheet1", CellIndex.indexByString("A2"), IntCellValue(42))
```

Writing `null` clears a cell:

```kotlin
sheet.updateCell(CellIndex.indexByString("A1"), null)
```

You can also assign through the `Data` object returned by `cell(...)`:

```kotlin
sheet.cell(CellIndex.indexByString("A1")).value = TextCellValue("Hello")
```

!!! info "Number formats are applied automatically"
    When you write a typed value, Kexcel attaches a sensible default number format
    (dates get a date format, times a time format, and so on). See
    [Number Formats](number-formats.md) to override it.

## Reading values

### A single cell

`cell(index)` returns a `Data` object; its `value` is the `CellValue?`
(`null` for an empty cell):

```kotlin
val value: CellValue? = sheet.cell(CellIndex.indexByString("A1")).value
```

### Pattern-matching the value

Because `CellValue` is sealed, a `when` expression reads the underlying typed
value cleanly:

```kotlin
val text = when (val v = sheet.cell(CellIndex.indexByString("A1")).value) {
    is TextCellValue -> v.value.toString()
    is IntCellValue -> v.value.toString()
    is DoubleCellValue -> v.value.toString()
    is BoolCellValue -> v.value.toString()
    is DateCellValue -> v.asLocalDate().toString()
    is DateTimeCellValue -> v.asDateTimeLocal().toString()
    is TimeCellValue -> v.asDuration().toString()
    is FormulaCellValue -> v.formula
    null -> ""
}
```

### Iterating rows

`sheet.rows` is a `List<List<Data?>>`. Each inner list is a row; a `null` entry is
an empty cell:

```kotlin
for (row in sheet.rows) {
    for (data in row) {
        println(data?.value)
    }
}
```

A single row by index:

```kotlin
val firstRow: List<Data?> = sheet.row(0)
```

### Reading a range

Select a rectangular range either as `Data` objects or as raw values:

```kotlin
// Raw values (List<List<Any?>>), the common case:
val values = sheet.selectRangeValues(
    CellIndex.indexByString("A1"),
    CellIndex.indexByString("C3"),
)

// ...or with a string range:
val values2 = sheet.selectRangeValuesWithString("A1:C3")

// Data objects (keeps styles, indices, etc.):
val cells = sheet.selectRangeWithString("A1:C3")
```

| Method | Returns |
| --- | --- |
| `selectRangeValues(start, end?)` | `List<List<Any?>>` — raw values |
| `selectRangeValuesWithString("A1:C3")` | `List<List<Any?>>` |
| `selectRange(start, end?)` | `List<List<Data?>?>` — `Data` objects |
| `selectRangeWithString("A1:C3")` | `List<List<Data?>?>` |

!!! tip "A single-cell range"
    Pass just a start (no `:`), e.g. `selectRangeValuesWithString("A1")`, to select
    from that cell to the end of the used range.

## The `Data` object

`cell(index)` returns a `Data`, which is more than just a value holder:

```kotlin
val data = sheet.cell(CellIndex.indexByString("A1"))

data.value        // CellValue?
data.cellStyle    // CellStyle?
data.rowIndex     // 0
data.columnIndex  // 0
data.cellIndex    // CellIndex -> "A1"
data.setFormula("=SUM(B1:B10)")
```
