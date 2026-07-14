---
description: >-
  Style Excel cells — fonts, colors, bold, italic, alignment and borders — from
  Kotlin Multiplatform with CellStyle.
---

# Cell Styling

Pass a **`CellStyle`** when updating a cell to control colors, fonts, alignment,
borders and number formats.

```kotlin
import com.gyanoba.kexcel.sheet.*
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.utils.*

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
```

## Styles are immutable — use `copyWith`

A `CellStyle` is a value you build once and reuse. Derive variants with
`copyWith` instead of mutating:

```kotlin
val redText = style.copyWith(fontColorHexVal = ExcelColor.red)
val onGrey = style.copyWith(backgroundColorHexVal = ExcelColor.grey)
```

!!! note "Two ways to change a property"
    Every visual property is both a constructor parameter *and* a mutable
    property on the instance (`style.isBold = true`, `style.backgroundColor = …`).
    For shared styles, prefer `copyWith` so you don't accidentally mutate a style
    other cells reference.

## Colors — `ExcelColor`

Colors come from the `ExcelColor` type. There are named Material colors, plus
factory methods for hex and int values:

```kotlin
ExcelColor.red
ExcelColor.blue
ExcelColor.green
ExcelColor.white
ExcelColor.black
ExcelColor.blueGrey
ExcelColor.orangeAccent

ExcelColor.fromHexString("FF00FF00") // ARGB hex
ExcelColor.fromInt(0xFF00FF00.toInt())

ExcelColor.none // "no fill" — the default background
```

The named palette follows Material Design (base colors like `red`, `blue`,
`teal`; accents like `redAccent`, `blueAccent`; and shade variants such as
`redAccent100`, `redAccent700`). Read a color's hex back with `.colorHex`.

!!! info "Hex format is ARGB"
    Hex strings are 8-digit **ARGB** (`AARRGGBB`). `FF` alpha is fully opaque.

## Fonts

```kotlin
CellStyle(
    fontFamily = "Calibri",
    fontSize = 12,
    bold = true,
    italic = true,
    underline = Underline.Double,
    fontColorHex = ExcelColor.deepPurple,
)
```

| Property | Type | Notes |
| --- | --- | --- |
| `fontFamily` | `String?` | e.g. `"Calibri"`, `"Arial"` |
| `fontSize` | `Int?` | points |
| `bold` / `isBold` | `Boolean` | |
| `italic` / `isItalic` | `Boolean` | |
| `underline` | `Underline` | `None`, `Single`, `Double` |
| `fontColorHex` / `fontColor` | `ExcelColor` | |

## Alignment & wrapping

```kotlin
import com.gyanoba.kexcel.utils.*

CellStyle(
    horizontalAlign = HorizontalAlign.Center,   // Left, Center, Right
    verticalAlign = VerticalAlign.Center,       // Top, Center, Bottom
    textWrapping = TextWrapping.WrapText,        // WrapText, Clip
    rotation = 45,                               // -90..90
)
```

!!! note "Rotation range"
    `rotation` accepts `-90..90`. Values outside that range are clamped to `0`.
    Negative angles are stored using Excel's own encoding (`abs(value) + 90`).

## Borders

Each side of a cell takes its own `Border`, made of a `BorderStyle` and a color:

```kotlin
val thin = Border(borderStyle = BorderStyle.Thin, borderColorHex = ExcelColor.black)

CellStyle(
    topBorder = thin,
    bottomBorder = Border(BorderStyle.Medium, ExcelColor.grey),
    leftBorder = thin,
    rightBorder = thin,
    diagonalBorder = Border(BorderStyle.Dashed, ExcelColor.red),
    diagonalBorderUp = true,
    diagonalBorderDown = false,
)
```

Available `BorderStyle` values:

`None`, `Hair`, `Thin`, `Dotted`, `Dashed`, `DashDot`, `DashDotDot`, `Medium`,
`MediumDashed`, `MediumDashDot`, `MediumDashDotDot`, `SlantDashDot`, `Double`,
`Thick`.

### Bordering a merged region

Applying a border to a merged range needs to reach every underlying cell. Use
`setMergedCellStyle`, which distributes the outer border correctly across the
merged block:

```kotlin
sheet.setMergedCellStyle(
    start = CellIndex.indexByString("A1"),
    mergedCellStyle = CellStyle(
        topBorder = thin, bottomBorder = thin, leftBorder = thin, rightBorder = thin,
    ),
)
```

See [Merging Cells](merging.md) for the full merge workflow.

## Reading a style back

```kotlin
val s = sheet.cell(CellIndex.indexByString("A1")).cellStyle
println(s?.isBold)                 // true
println(s?.backgroundColor?.colorHex)
```

## Number formats

The `numberFormat` property controls how a numeric, date or time value is
*displayed* (the stored value is unchanged). This has its own guide:
[Number Formats](number-formats.md).
