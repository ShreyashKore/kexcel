# Module Kexcel

Kexcel is a **Kotlin Multiplatform** library for reading and writing Excel
(`.xlsx`) files, written in pure Kotlin with no platform-specific dependencies.

This is the generated API reference. For guides, tutorials and a getting-started
walkthrough, see the [documentation site](https://shreyashkore.github.io/kexcel/).

## Where to start

- [`com.gyanoba.kexcel.Excel`](com.gyanoba.kexcel/-excel/index.html) — the single
  entry point. Create a workbook with `Excel.createExcel()` or load one with
  `Excel.decodeBytes` / `Excel.decodeStream`.
- [`com.gyanoba.kexcel.sheet.Sheet`](com.gyanoba.kexcel.sheet/-sheet/index.html) —
  a worksheet: read and write cells, rows, columns and ranges.
- [`com.gyanoba.kexcel.sheet.CellValue`](com.gyanoba.kexcel.sheet/-cell-value/index.html) —
  the sealed hierarchy of typed cell values (text, numbers, dates, formulas…).
- [`com.gyanoba.kexcel.sheet.CellStyle`](com.gyanoba.kexcel.sheet/-cell-style/index.html) —
  colors, fonts, alignment, borders and number formats.

# Package com.gyanoba.kexcel

The workbook entry point. [Excel] holds the whole document state and exposes the
public constructors (`createExcel`, `decodeBytes`, `decodeStream`) plus
workbook-level operations that delegate to the relevant sheet.

# Package com.gyanoba.kexcel.sheet

The worksheet model: [Sheet], the [CellValue] sealed hierarchy, [CellStyle],
[Border]/[BorderStyle], [CellIndex] for `"A1"` ⇄ `(column, row)` conversion, and
the per-cell [Data] wrapper.

# Package com.gyanoba.kexcel.number_format

Number, date and time formats. [NumFormat] exposes the built-in `standard_*`
constants and factory helpers for custom format codes.

# Package com.gyanoba.kexcel.utils

Cross-cutting helpers exposed on the public API, including [ExcelColor] (named
Material colors and hex/int conversion) and the alignment / underline / wrapping
enums used by [CellStyle].
