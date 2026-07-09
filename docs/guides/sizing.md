# Column Widths & Row Heights

Control the size of columns and rows, or ask Excel to auto-fit a column. These are
methods on the `Sheet` object.

## Setting sizes

```kotlin
val sheet = excel["Sheet1"]

sheet.setColumnWidth(columnIndex = 0, columnWidth = 30.0)
sheet.setRowHeight(rowIndex = 0, rowHeight = 24.0)
```

- **Column width** is measured in Excel's character-width units (roughly the
  number of `0` characters that fit).
- **Row height** is measured in points.

Negative values are ignored.

## Auto-fit a column

Mark a column to size itself to its content when the file is opened:

```kotlin
sheet.setColumnAutoFit(columnIndex = 1)
```

## Defaults for the whole sheet

Set fallback sizes used by any column/row you didn't size explicitly:

```kotlin
sheet.setDefaultColumnWidth(15.0)
sheet.setDefaultRowHeight(18.0)
```

## Reading sizes back

```kotlin
sheet.getColumnWidth(0)     // Double
sheet.getRowHeight(0)       // Double
sheet.getColumnAutoFit(1)   // Boolean

sheet.getColumnWidths       // Map<Int, Double> of explicitly set widths
sheet.getRowHeights         // Map<Int, Double>
sheet.getColumnAutoFits     // Map<Int, Boolean>

sheet.defaultColumnWidth    // Double? (null until you set one)
sheet.defaultRowHeight      // Double?
```

!!! warning "`getColumnWidth` / `getRowHeight` need a default"
    `getColumnWidth(i)` returns the explicit width if one was set, otherwise the
    sheet default. If neither has been set it will throw, so set a default (or an
    explicit width) before reading.

## Example

```kotlin
val excel = Excel.createExcel()
val sheet = excel["Sheet1"]
sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("a wide column"))

sheet.setDefaultColumnWidth(12.0)
sheet.setColumnWidth(0, 30.0)
sheet.setRowHeight(0, 24.0)
sheet.setColumnAutoFit(1)

println(sheet.getColumnWidth(0))   // 30.0
println(sheet.getColumnWidth(2))   // 12.0 (falls back to the default)
println(sheet.getColumnAutoFit(1)) // true
```
