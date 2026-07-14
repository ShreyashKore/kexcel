---
description: >-
  Create, edit, save and reload an Excel (.xlsx) workbook in Kotlin
  Multiplatform — a complete end-to-end walkthrough.
---

# Quick Start

This page walks through the whole lifecycle: **create → edit → save → reload**.
By the end you will have written a small, styled spreadsheet and read it back.

!!! info "Runnable samples"
    Every snippet here has a complete, runnable counterpart in the sample app at
    `sample/sharedUI/src/commonMain/kotlin/sample/app/ExcelSamples.kt`. Run it with
    `./gradlew :sample:desktopApp:run`.

## 1. Create or load a workbook

The [`Excel`](../guides/workbooks-and-sheets.md) class is the single entry point
and represents the whole workbook. Use its companion object to create or load one:

```kotlin
import com.gyanoba.kexcel.Excel

// Create a new, empty workbook (contains a single sheet named "Sheet1").
val excel = Excel.createExcel()

// ...or load an existing workbook from bytes / a stream:
val fromBytes = Excel.decodeBytes(bytes)
val fromStream = Excel.decodeStream(inputStream)
```

| Method | Description |
| --- | --- |
| `Excel.createExcel()` | Creates a new, empty workbook with one sheet, `Sheet1`. |
| `Excel.decodeBytes(data: ByteArray)` | Loads a workbook from raw `.xlsx` bytes. |
| `Excel.decodeStream(input: InputStream)` | Loads a workbook from an input stream. |

## 2. Get a sheet

Index the workbook by sheet name. If the sheet does not exist, it is **created
automatically**:

```kotlin
val sheet = excel["Sheet1"]
```

## 3. Write values

Every value is wrapped in a [`CellValue`](../guides/reading-and-writing.md). Cells
are addressed with a `CellIndex`, and rows/columns are **0-based**:

```kotlin
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue

sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Product"))
sheet.updateCell(CellIndex.indexByString("B1"), TextCellValue("Price"))
sheet.updateCell(CellIndex.indexByString("A2"), TextCellValue("Widget"))
sheet.updateCell(CellIndex.indexByString("B2"), DoubleCellValue(9.99))
sheet.updateCell(CellIndex.indexByString("A3"), TextCellValue("Gadget"))
sheet.updateCell(CellIndex.indexByString("B3"), DoubleCellValue(19.50))
```

Or append whole rows at once:

```kotlin
sheet.appendRow(listOf(TextCellValue("Gizmo"), DoubleCellValue(4.25)))
```

## 4. Style a header

Pass a [`CellStyle`](../guides/styling.md) to make the header stand out:

```kotlin
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.HorizontalAlign

val header = CellStyle(
    fontColorHex = ExcelColor.white,
    backgroundColorHex = ExcelColor.green,
    bold = true,
    horizontalAlign = HorizontalAlign.Center,
)

sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Product"), cellStyle = header)
sheet.updateCell(CellIndex.indexByString("B1"), TextCellValue("Price"), cellStyle = header)
```

## 5. Add a formula

Formulas are stored as-is and evaluated by the spreadsheet app that opens the file:

```kotlin
import com.gyanoba.kexcel.sheet.FormulaCellValue

sheet.updateCell(CellIndex.indexByString("B5"), FormulaCellValue("=SUM(B2:B4)"))
```

## 6. Save

`encode()` serializes the workbook to `.xlsx` bytes **in memory**. Writing those
bytes to disk is platform-specific:

=== "JVM / Desktop"

    ```kotlin
    import java.io.File

    val bytes = excel.encode() ?: error("Failed to encode workbook")
    File("products.xlsx").writeBytes(bytes)
    ```

=== "Android"

    ```kotlin
    val bytes = excel.encode() ?: error("Failed to encode workbook")
    context.openFileOutput("products.xlsx", Context.MODE_PRIVATE).use { it.write(bytes) }
    ```

=== "Common (in-memory)"

    ```kotlin
    // Works on every target — hand the bytes to your own storage / share sheet.
    val bytes: ByteArray? = excel.encode()
    ```

## 7. Read it back

The bytes are a valid `.xlsx` file, so you can decode them again:

```kotlin
val reloaded = Excel.decodeBytes(bytes)
val price = reloaded["Sheet1"].cell(CellIndex.indexByString("B2")).value
println(price) // 9.99
```

## Putting it all together

```kotlin
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.sheet.*
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.HorizontalAlign

fun buildProductSheet(): ByteArray {
    val excel = Excel.createExcel()
    val sheet = excel["Sheet1"]

    val header = CellStyle(
        fontColorHex = ExcelColor.white,
        backgroundColorHex = ExcelColor.green,
        bold = true,
        horizontalAlign = HorizontalAlign.Center,
    )

    sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Product"), cellStyle = header)
    sheet.updateCell(CellIndex.indexByString("B1"), TextCellValue("Price"), cellStyle = header)

    sheet.appendRow(listOf(TextCellValue("Widget"), DoubleCellValue(9.99)))
    sheet.appendRow(listOf(TextCellValue("Gadget"), DoubleCellValue(19.50)))
    sheet.appendRow(listOf(TextCellValue("Gizmo"), DoubleCellValue(4.25)))

    sheet.updateCell(CellIndex.indexByString("B5"), FormulaCellValue("=SUM(B2:B4)"))

    return excel.encode() ?: error("Failed to encode workbook")
}
```

## Next steps

- [Workbooks & Sheets](../guides/workbooks-and-sheets.md) — sheet operations.
- [Reading & Writing Cells](../guides/reading-and-writing.md) — every value type.
- [Cell Styling](../guides/styling.md) — colors, fonts, borders, alignment.
