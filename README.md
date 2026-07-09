<img src="art/logo.png" alt="Kexcel logo" align="left" width="84" height="84" hspace="16" />

# Kexcel

Read and write Excel (`.xlsx`) files from Kotlin Multiplatform.

[![latest version](https://img.shields.io/maven-central/v/com.gyanoba.kexcel/kexcel?color=blue&label=Version)](https://central.sonatype.com/artifact/com.gyanoba.kexcel/kexcel)

<br clear="left" />

> ⚠️ **Experimental** — Kexcel is under active development. The API is unstable and may change without notice.

A **Kotlin Multiplatform** library for working with Excel (`.xlsx`) files. Written in **pure Kotlin** with no platform-specific dependencies.

Kexcel is a Kotlin port of the Dart [`excel`](https://github.com/justkawal/excel) library by [justkawal](https://github.com/justkawal).

Supported targets: **JVM**, **Android**, **iOS** (`iosArm64`, `iosSimulatorArm64`).

## Installation

Add Kexcel to your project.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.gyanoba.kexcel:kexcel:0.0.2")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.gyanoba.kexcel:kexcel:0.0.2'
}
```

## Getting Started

The `Excel` class is the single entry point and represents the whole workbook. Use its companion object to create or load one:

```kotlin
// Create a new, empty workbook (contains a single sheet named "Sheet1")
val excel = Excel.createExcel()

// Load a workbook from a ByteArray
val excel = Excel.decodeBytes(bytes)

// Load a workbook from an InputStream
val excel = Excel.decodeStream(inputStream)
```

| Method | Description |
| --- | --- |
| `Excel.createExcel(): Excel` | Creates a new, empty workbook with one sheet, `Sheet1`. |
| `Excel.decodeBytes(data: ByteArray): Excel` | Loads a workbook from raw `.xlsx` bytes. |
| `Excel.decodeStream(input: InputStream): Excel` | Loads a workbook from an input stream. |

## Usage

> A complete, runnable version of every snippet below lives in the sample app
> ([`sample/sharedUI/.../ExcelSamples.kt`](sample/sharedUI/src/commonMain/kotlin/sample/app/ExcelSamples.kt)).
> Run it with `./gradlew :sample:desktopApp:run`.

### Accessing sheets

```kotlin
val excel = Excel.createExcel()

// Get a sheet by name. If it does not exist, it is created automatically.
val sheet = excel["Sheet1"]

// All sheets as a Map<String, Sheet>
val sheets: Map<String, Sheet> = excel.getSheets()

// Dimensions of a sheet
println("${sheet.maxRows} rows x ${sheet.maxColumns} columns")
```

### Addressing cells

Cells are addressed with a `CellIndex`. Rows and columns are **0-based**.

```kotlin
CellIndex.indexByString("A1")                       // column 0, row 0
CellIndex.indexByColumnRow(columnIndex = 2, rowIndex = 4) // "C5"
```

### Writing values

Every value is wrapped in a `CellValue`. Update a cell either through the workbook or the sheet:

```kotlin
val sheet = excel["Sheet1"]

// Via the sheet
sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Hello"))

// Via the workbook (equivalent)
excel.updateCell("Sheet1", CellIndex.indexByString("A2"), IntCellValue(42))
```

Available `CellValue` types:

```kotlin
TextCellValue("Some text")
IntCellValue(42)
DoubleCellValue(3.14)
BoolCellValue(true)
DateCellValue(year = 2026, month = 7, day = 9)
TimeCellValue(hour = 14, minute = 30, second = 0)
DateTimeCellValue(year = 2026, month = 7, day = 9, hour = 14, minute = 30)
FormulaCellValue("=SUM(A1:A10)")
```

Writing `null` clears a cell.

### Reading values

```kotlin
val sheet = excel["Sheet1"]

// A single cell
val value: CellValue? = sheet.cell(CellIndex.indexByString("A1")).value

// Iterate every row (each row is a List<Data?>, nulls are empty cells)
for (row in sheet.rows) {
    for (data in row) {
        println(data?.value)
    }
}

// A range, as raw values
val values: List<List<Any?>> =
    sheet.selectRangeValues(
        CellIndex.indexByString("A1"),
        CellIndex.indexByString("C3"),
    )
// ...or with a string range
val values2 = sheet.selectRangeValuesWithString("A1:C3")
```

`CellValue` is a sealed type — pattern-match to read the underlying value:

```kotlin
when (val v = sheet.cell(CellIndex.indexByString("A1")).value) {
    is TextCellValue -> v.value.toString()
    is IntCellValue -> v.value
    is DoubleCellValue -> v.value
    is BoolCellValue -> v.value
    is DateCellValue -> v.asLocalDate()
    else -> null
}
```

### Styling cells

Pass a `CellStyle` when updating a cell. Styles cover colors, fonts, alignment,
borders and number formats.

```kotlin
val style = CellStyle(
    fontColorHex = ExcelColor.white,
    backgroundColorHex = ExcelColor.blue,
    bold = true,
    italic = false,
    fontSize = 14,
    horizontalAlign = HorizontalAlign.Center,
    verticalAlign = VerticalAlign.Center,
    underline = Underline.Single,
    leftBorder = Border(borderStyle = BorderStyle.Thin, borderColorHex = ExcelColor.black),
    rightBorder = Border(borderStyle = BorderStyle.Thin, borderColorHex = ExcelColor.black),
    numberFormat = NumFormat.standard_2, // "0.00"
)

sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Header"), cellStyle = style)

// Styles are immutable — derive variants with copyWith
val redText = style.copyWith(fontColorHexVal = ExcelColor.red)
```

Colors come from `ExcelColor` (named Material colors like `ExcelColor.red`, or
`ExcelColor.fromHexString("FF00FF00")`). Number formats come from `NumFormat`
(built-in `standard_*` constants, or `CustomNumericNumFormat("0.00%")`).

### Formulas

```kotlin
// Via a FormulaCellValue
sheet.updateCell(CellIndex.indexByString("A4"), FormulaCellValue("=SUM(A1:A3)"))

// Or on the Data object directly
sheet.cell(CellIndex.indexByString("A5")).setFormula("=AVERAGE(A1:A3)")
```

### Rows and columns

```kotlin
// Append a row after the last filled row
excel.appendRow("Sheet1", listOf(TextCellValue("a"), IntCellValue(1)))

// Write an iterable at a specific row (optionally offset by a starting column)
excel.insertRowIterables(
    "Sheet1",
    listOf(TextCellValue("x"), TextCellValue("y")),
    rowIndex = 5,
    startingColumn = 1,
)

// Insert / remove blank rows and columns
excel.insertRow("Sheet1", rowIndex = 0)
excel.removeRow("Sheet1", rowIndex = 3)
excel.insertColumn("Sheet1", columnIndex = 0)
excel.removeColumn("Sheet1", columnIndex = 2)
```

### Merging cells

```kotlin
excel.merge(
    "Sheet1",
    CellIndex.indexByString("A1"),
    CellIndex.indexByString("C1"),
    customValue = TextCellValue("Merged title"),
)

val merged: List<String> = excel.getMergedCells("Sheet1") // e.g. ["A1:C1"]

excel.unMerge("Sheet1", "A1:C1")
```

### Column widths & row heights

```kotlin
val sheet = excel["Sheet1"]
sheet.setColumnWidth(columnIndex = 0, columnWidth = 30.0)
sheet.setRowHeight(rowIndex = 0, rowHeight = 24.0)
sheet.setColumnAutoFit(columnIndex = 1)
sheet.setDefaultColumnWidth(15.0)
sheet.setDefaultRowHeight(18.0)
```

### Find and replace

Replaces text in `TextCellValue` cells. `source` is a `Regex`; the return value is the number of replacements.

```kotlin
val count = excel.findAndReplace("Sheet1", Regex("Widget"), "Gadget")
```

### Sheet operations

```kotlin
excel.copy("Sheet1", "Backup")          // copy a sheet
excel.rename("Backup", "Archive")       // rename a sheet
excel.delete("Archive")                 // delete (keeps at least one sheet)

excel.setDefaultSheet("Sheet1")         // sheet shown first when the file opens
val name: String? = excel.getDefaultSheet()

// Link two names to the same underlying sheet, then break the link
excel.link("Alias", excel["Sheet1"])
excel.unLink("Alias")
```

### Saving

```kotlin
// Serialize the workbook to .xlsx bytes (in memory)
val bytes: ByteArray? = excel.encode()
File("out.xlsx").writeBytes(bytes) // Write to disk to save as a .xlsx file
```

## Documentation

The [Usage](#usage) section above covers the common operations. For a full,
runnable tour of the API see the sample app source in
[`ExcelSamples.kt`](sample/sharedUI/src/commonMain/kotlin/sample/app/ExcelSamples.kt).

## Development

### Run Sample App

The sample app runs each API example and prints the results.

- Desktop JVM: `./gradlew :sample:desktopApp:run`
- Android: Open the project in Android Studio and run the `sample/androidApp` configuration.
- iOS: Open `sample/iosApp/iosApp.xcodeproj` in Xcode and run.

### Build & test

- Build the library: `./gradlew :kexcel:build`
- Run all tests: `./gradlew :kexcel:allTests`
- JVM tests only (fastest): `./gradlew :kexcel:jvmTest`
