---
title: Kotlin Multiplatform Excel Library
description: >-
  Kexcel is a pure Kotlin Multiplatform library to read and write Excel (.xlsx)
  files on JVM, Android and iOS — an Apache POI alternative with no JVM-only
  dependencies.
---

# Kexcel — Excel (`.xlsx`) for Kotlin Multiplatform

[![Maven Central](https://img.shields.io/maven-central/v/com.gyanoba.kexcel/kexcel?color=blue&label=Maven%20Central)](https://central.sonatype.com/artifact/com.gyanoba.kexcel/kexcel)
[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)

**Kexcel** is a **Kotlin Multiplatform** library for **reading and writing Excel
spreadsheets** (`.xlsx`, the Office Open XML format) — written in **pure Kotlin**,
with no platform-specific dependencies.

Because it doesn't wrap **Apache POI** — or any other JVM library — the *same*
spreadsheet code compiles and runs on **JVM, Android and iOS**. That makes it a
practical **Apache POI alternative** for Kotlin Multiplatform (KMP/KMM) and
Compose Multiplatform apps, which otherwise have no way to touch `.xlsx` files
outside the JVM.

Kexcel is a Kotlin port of the Dart [`excel`](https://github.com/justkawal/excel)
library by [justkawal](https://github.com/justkawal).

!!! warning "Experimental"
    Kexcel is under active development. The API is unstable and may change
    without notice between `0.0.x` releases.

## Why Kexcel?

<div class="grid cards" markdown>

- :material-language-kotlin: __Pure Kotlin__

    No POI, no JVM-only APIs. The same code runs on every supported target.

- :material-devices: __Truly multiplatform__

    JVM, Android and iOS (`iosArm64`, `iosSimulatorArm64`) are published today.

- :material-table: __A small, focused API__

    One entry point — [`Excel`](guides/workbooks-and-sheets.md) — and a handful
    of value and style types. Read a file, mutate it, write it back.

- :material-memory: __In-memory round-trips__

    [`encode()`](guides/saving.md) turns a workbook into `.xlsx` bytes with no
    filesystem access, which is handy on every platform.

</div>

## Supported targets

| Target | Status |
| --- | --- |
| JVM | ✅ Published |
| Android (`minSdk 23`) | ✅ Published |
| iOS (`iosArm64`, `iosSimulatorArm64`) | ✅ Published |
| `wasmJs`, `macosArm64` | 🧪 Build targets present, not yet published |
| JS, Linux, MinGW | ⚙️ Commented out in the build |

## A 30-second taste

```kotlin
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.sheet.IntCellValue

// Create a blank workbook (it starts with a single sheet, "Sheet1").
val excel = Excel.createExcel()
val sheet = excel["Sheet1"]

// Write a couple of cells.
sheet.updateCell(CellIndex.indexByString("A1"), TextCellValue("Hello"))
sheet.updateCell(CellIndex.indexByString("B1"), IntCellValue(42))

// Serialize to .xlsx bytes (entirely in memory).
val bytes: ByteArray? = excel.encode()
```

## Where next?

<div class="grid cards" markdown>

- :material-download: __[Installation](getting-started/installation.md)__ — add the
  dependency to your Gradle build.
- :material-rocket-launch: __[Quick Start](getting-started/quick-start.md)__ —
  create, edit and save a workbook end to end.
- :material-book-open-variant: __[Guides](guides/workbooks-and-sheets.md)__ —
  task-focused walkthroughs of every part of the API.
- :material-api: __[API Reference](api/index.html)__ — the full generated Dokka
  reference.

</div>

## Frequently asked questions

### Does Kexcel use Apache POI?

No. Kexcel is written in pure Kotlin and depends only on multiplatform libraries
([`kmp-zip`](https://github.com/henrik242/kmp-zip) for the ZIP container and
[Ksoup](https://github.com/fleeksoft/ksoup) for XML). That is precisely why it
runs outside the JVM — Apache POI, and every Kotlin wrapper built on it, is
JVM-only.

### Can I read and write Excel files on Android and iOS?

Yes. JVM, Android and iOS (`iosArm64`, `iosSimulatorArm64`) are published
targets, so the same `commonMain` code works on all of them — including in a
Compose Multiplatform app.

### Which file formats are supported?

`.xlsx` (Office Open XML) only — the format Excel 2007 and later, Google Sheets,
Numbers and LibreOffice all write. The legacy binary `.xls` format is not
supported.

### Can it do styling, formulas and merged cells?

Yes — see [Cell Styling](guides/styling.md), [Formulas](guides/formulas.md) and
[Merging Cells](guides/merging.md). Number formats, column widths, row heights
and find-and-replace are covered in the guides too.

### Does it need the filesystem?

No. [`encode()`](guides/saving.md) returns the workbook as a `ByteArray`, so you
can round-trip a spreadsheet entirely in memory and hand the bytes to whatever
storage, network or file API your platform provides.

## License

Kexcel is released under the [MIT License](https://opensource.org/licenses/MIT).
