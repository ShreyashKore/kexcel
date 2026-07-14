---
description: >-
  Merge and unmerge cells in an Excel .xlsx worksheet from Kotlin Multiplatform.
---

# Merging Cells

Merge a rectangular block of cells into one, and unmerge it later.

## Merging

`merge` takes the top-left and bottom-right corners of the region:

```kotlin
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.TextCellValue

excel.merge(
    "Sheet1",
    CellIndex.indexByString("A1"),
    CellIndex.indexByString("C1"),
    customValue = TextCellValue("Merged title"),
)
```

If you omit `customValue`, the merged cell takes the **first non-empty value**
found in the region, scanning row by row, left to right:

```kotlin
// A1 is empty, B1 = "Kept". The merged region shows "Kept".
excel["Sheet1"].updateCell(CellIndex.indexByString("B1"), TextCellValue("Kept"))
excel.merge("Sheet1", CellIndex.indexByString("A1"), CellIndex.indexByString("C1"))
```

## Listing merged regions

```kotlin
val merged: List<String> = excel.getMergedCells("Sheet1") // e.g. ["A1:C1"]
```

The same list is available on the sheet as `sheet.spannedItems`.

## Unmerging

Pass the exact range string (as it appears in `getMergedCells`):

```kotlin
excel.unMerge("Sheet1", "A1:C1")
```

A typical round-trip:

```kotlin
excel.merge("Sheet1", CellIndex.indexByString("A1"), CellIndex.indexByString("C1"),
    customValue = TextCellValue("Merged title"))

println(excel.getMergedCells("Sheet1")) // [A1:C1]

excel.unMerge("Sheet1", "A1:C1")
println(excel.getMergedCells("Sheet1")) // []
```

## Styling a merged region

Because a border must be written to every underlying cell of a merge, apply it
with `setMergedCellStyle` rather than a plain `updateCell`. It distributes the
outer border across the block correctly:

```kotlin
import com.gyanoba.kexcel.sheet.*

val thin = Border(BorderStyle.Thin, ExcelColor.black)
excel["Sheet1"].setMergedCellStyle(
    start = CellIndex.indexByString("A1"),
    mergedCellStyle = CellStyle(
        backgroundColorHex = ExcelColor.green,
        topBorder = thin, bottomBorder = thin, leftBorder = thin, rightBorder = thin,
    ),
)
```

`start` must be the top-left corner of an existing merged region.

## Behavior notes

- Merging a degenerate region (start == end) is a no-op.
- Overlapping merges are reconciled automatically; existing spans that intersect
  the new one are absorbed.
- When you [insert or remove rows/columns](rows-and-columns.md), existing merged
  regions are shifted to follow the content.
