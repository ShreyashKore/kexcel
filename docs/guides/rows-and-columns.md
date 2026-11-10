---
description: >-
  Append, insert and remove rows and columns in an Excel spreadsheet from Kotlin
  Multiplatform.
---

# Rows & Columns

Kexcel can append, insert and remove whole rows and columns. All indices are
**0-based**. These operations exist both on the workbook (taking a sheet name) and
on the `Sheet` object directly.

## Appending a row

`appendRow` writes a list of values just after the last filled row:

```kotlin
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.IntCellValue

excel.appendRow("Sheet1", listOf(TextCellValue("a"), IntCellValue(1)))

// ...or on the sheet:
excel["Sheet1"].appendRow(listOf(TextCellValue("b"), IntCellValue(2)))
```

## Writing a row at a specific position

`insertRowIterables` places a list of values at a given `rowIndex`, optionally
starting from a column offset:

```kotlin
excel.insertRowIterables(
    "Sheet1",
    listOf(TextCellValue("x"), TextCellValue("y")),
    rowIndex = 5,
    startingColumn = 1, // start writing at column B
)
```

| Parameter | Meaning |
| --- | --- |
| `row` | the values to write |
| `rowIndex` | which row (0-based) |
| `startingColumn` | column offset to start from (default `0`) |
| `overwriteMergedCells` | when `false`, values skip over merged regions instead of overwriting them (default `true`) |

!!! note "This overwrites, it does not shift"
    `insertRowIterables` writes *into* the given row. To push existing content
    down first, insert a blank row with `insertRow` (below).

## Inserting & removing blank rows/columns

These shift existing content to make (or reclaim) space:

```kotlin
excel.insertRow("Sheet1", rowIndex = 0)      // push everything down by one row
excel.removeRow("Sheet1", rowIndex = 3)      // delete row 3, pull the rest up

excel.insertColumn("Sheet1", columnIndex = 0) // push everything right by one column
excel.removeColumn("Sheet1", columnIndex = 2) // delete column C, pull the rest left
```

Negative indices are ignored. Removing an index at or beyond the used range is a
no-op.

What moves with the cells: their styles, merged regions, row heights, column widths,
and the hidden, grouping and style settings of rows and columns.

What points at the cells follows them too, as it does in Excel:

- formulas, on this sheet and on every other sheet that refers to it. `SUM(A1:A5)`
  becomes `SUM(A1:A6)` when a row is inserted inside the range, and a reference to
  a removed row or column becomes `#REF!`;
- defined names and print areas;
- hyperlinks, cell comments, data validations, conditional formats, the autofilter,
  protected ranges and ignored errors that came with the file.

!!! warning "What does not follow"
    Tables, images and charts, pivot tables and threaded comments are kept in the
    file as they were read, and their positions are not adjusted. Avoid inserting or
    removing rows and columns on a sheet that has them.

## Clearing a row

`clearRow` empties the cells of a row **without** shifting anything. It returns
`false` (and does nothing) if the row intersects a merged region:

```kotlin
val cleared: Boolean = excel["Sheet1"].clearRow(2)
```

## Reading dimensions

```kotlin
val sheet = excel["Sheet1"]
println("${sheet.maxRows} x ${sheet.maxColumns}")
val row2: List<Data?> = sheet.row(1) // the whole second row
```

## Limits

Kexcel enforces the spreadsheet grid limits and throws
`IllegalArgumentException` if you exceed them:

| Axis | Maximum |
| --- | --- |
| Rows | 1,048,576 |
| Columns | 16,384 (`XFD`) |

## Full example

```kotlin
val excel = Excel.createExcel()

excel.appendRow("Sheet1", listOf(TextCellValue("r0c0"), TextCellValue("r0c1")))
excel.appendRow("Sheet1", listOf(TextCellValue("r1c0"), TextCellValue("r1c1")))

// Write a value at row 0 offset into column C:
excel.insertRowIterables("Sheet1", listOf(TextCellValue("offset")), rowIndex = 0, startingColumn = 2)

// Make room at the top-left, then take it back:
excel.insertRow("Sheet1", rowIndex = 0)
excel.insertColumn("Sheet1", columnIndex = 0)
excel.removeRow("Sheet1", rowIndex = 0)
excel.removeColumn("Sheet1", columnIndex = 0)
```
