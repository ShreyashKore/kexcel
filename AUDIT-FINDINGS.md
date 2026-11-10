# Kexcel audit: data integrity, spec coverage and test gaps

| | |
|---|---|
| Audited commit | `53a21be` (`chore/reference-libraries`) |
| Date | 2026-11-10 |
| Oracles | Apache POI `REL_5_4_1` (`reference/poi`, and the `poi-ooxml` jar in `jvmTest`), ECMA-376, Dart `excel` 5.0.0 (`reference/dart-excel`) |
| Fix branch | `fix/audit-findings-2026-11-10`, branched from the audited commit |
| Status of this document | Findings and their status. See **Progress** for what is fixed. |

This is a living list. Each finding has a stable ID. When one is fixed, set its **Status**
to `Fixed in <commit>` rather than deleting the row, and name the test that now covers it.

## Progress

Last updated 2026-11-10, on `fix/audit-findings-2026-11-10`. Of 64 findings: **44 fixed**,
6 partly fixed, 14 open.

- Each fix comes with tests for the behaviour the probes showed to be broken. They were not
  each re-run against the unfixed code.
- The tests that existed before still pass. Four of their expectations changed, each with
  POI or the specification as evidence (see T-02).
- `:kexcel:jvmTest` (404 tests) and `:kexcel:macosArm64Test` (197 common tests, run
  natively) pass; the wasm and iOS simulator targets compile; `:kexcel:checkKotlinAbi`
  passes, so the public API is unchanged.
- Nothing was checked in Excel itself.

| ID | Status | Commit | Note |
|---|---|---|---|
| D-01 | Fixed | `f95a967` |  |
| D-02 | Fixed | `375eacb` | Followers are expanded on read, as POI does. |
| D-03 | Fixed | `375eacb` |  |
| D-04 | Fixed | `d63c381` |  |
| D-05 | Fixed | `8ce5e08` `375eacb` | Detection ported from POI; unedited numbers are written back digit for digit. |
| D-06 | Fixed | `8ce5e08` |  |
| D-07 | Partly | `8ce5e08` | Serials below 61 fixed in both directions. A date before 1900 is still written as a negative serial. |
| D-08 | Fixed | `8ce5e08` `375eacb` |  |
| D-09 | Fixed | `010c5a9` |  |
| D-10 | Fixed | `308bd3b` |  |
| D-11 | Fixed | `8b86da7` |  |
| D-12 | Partly | `32cb58c` | Formulas on every sheet, defined names, hyperlinks, comments, data validations, conditional formats, autofilter, protected ranges and ignored errors now follow the edit; the result agrees with POI's `shiftRows` / `shiftColumns`. Tables, image and chart anchors, pivot tables and threaded comments still do not. |
| D-13 | Fixed | `27724f8` | `copy()` still starts from an empty worksheet part, so panes and validations are not copied (F-11). |
| D-14 | Fixed | `27724f8` | `setDefaultSheet` still moves the tab to the front; the names follow it now. |
| D-15 | Fixed | `375eacb` | Cached results kept until a value changes, then dropped with `fullCalcOnLoad` set and the calc chain removed. Reading a cached result through the API is still not possible. |
| D-17 | Fixed | `7bc8d77` |  |
| D-18 | Partly | `375eacb` | An unedited error cell is written back as an error. There is still no error value type. |
| D-19 | Fixed | `7bc8d77` |  |
| D-20 | Fixed | `375eacb` |  |
| D-21 | Fixed | `83cc03a` | Written back on save. The model still shows only the top-left cell of a merge. |
| D-22 | **Open** | — | By design for now. |
| D-23 | Fixed | `8b86da7` | Found while fixing D-11. |
| O-01 | Fixed | `99426ca` |  |
| O-02 | Fixed | `e30bf68` |  |
| O-04 | Fixed | `99426ca` |  |
| O-05 | Fixed | `27724f8` |  |
| O-06 | Fixed | `5e152ae` |  |
| O-07 | Fixed | `99426ca` | `applyBorder` and `applyNumberFormat` on new styles. Not checked in Excel. |
| O-08 | **Open** | — |  |
| O-09 | **Open** | — | Needs an API decision: `Left` is also the default value. |
| O-10 | Fixed | `f2435fc` |  |
| O-11 | **Open** | — |  |
| R-01 | Fixed | `62cd4e5` |  |
| R-02 | Fixed | `62cd4e5` |  |
| R-03 | Fixed | `62cd4e5` | Reads as an empty string, as POI does. |
| R-04 | Fixed | `62cd4e5` |  |
| R-05 | Fixed | `62cd4e5` |  |
| R-06 | Fixed | `62cd4e5` |  |
| R-07 | Fixed | `375eacb` |  |
| R-08 | Fixed | `62cd4e5` |  |
| R-09 | **Open** | — |  |
| R-10 | **Open** | — |  |
| R-11 | **Open** | — |  |
| R-12 | **Open** | — |  |
| R-13 | Partly | `62cd4e5` | A bad number or date names its cell in a damaged-file error. Other paths unchanged. |
| F-01 | Fixed | `375eacb` `0377f78` `99426ca` |  |
| F-02 | Fixed | `375eacb` | On load. The editing API still swaps a format that cannot show the new value. |
| F-03 | Partly | `8ce5e08` | Ids 5-8 and 41-44 added, three codes corrected, ids below 164 honoured. The locale-dependent ids 23-36 and 50-81 are still unknown. |
| F-04 | Fixed | `5e152ae` |  |
| F-05 | Fixed | `5e152ae` |  |
| F-06 | Fixed | `38a45d0` |  |
| F-07 | Fixed | `5e152ae` `83cc03a` |  |
| F-08 | Fixed | `5e152ae` | Unchanged sizes keep their stored text. A size set in code is still written with two decimals. |
| F-09 | Fixed | `99426ca` |  |
| F-10 | **Open** | — |  |
| F-11 | Partly | `5e152ae` `27724f8` | Default sizes, row and column settings and merges are copied. What lives only in the XML is not. |
| F-12 | **Open** | — | No longer loses anything on a round trip (F-01); still not modelled. |
| M-01 | **Open** | — |  |
| M-02 | Fixed | `308bd3b` |  |
| M-03 | Fixed | `308bd3b` |  |
| M-04 | Fixed | `f2435fc` | `kotlinx-datetime` now resolves its own `kotlinx-serialization-core` (1.6.2) for consumers instead of the newer one the removed dependency pulled in. |
| M-05 | **Open** | — |  |
| M-06 | **Open** | — |  |
| M-07 | **Open** | — | `:kexcel:macosArm64Test` was run by hand for this work and passes. |

Also on the branch: `4c3c9ad` adds the POI oracle the fidelity tests are built on (T-01), and
the last code commit keeps the common source set free of JVM-only calls.

### The corpus, before and after

The same no-edit round trip of the 354 POI files, run again at the end.

| Change after a no-edit round trip | Files before | Files after |
|---|---|---|
| Kexcel cannot open a file POI opens | 28 | 9 |
| Round trip completed | 301 | 321 |
| Cached formula results dropped | 120 | 1 |
| Formulas emptied (shared-formula followers) | 47 | 0 |
| Every shared string wiped | 1 | 0 |
| Numeric values changed | 3 | 0 |
| Header/footer text corrupted | 26 | 0 |
| Number format of a cell changed | 58 | 0 |
| Font / fill / alignment / protection / border of a cell changed | 47 / 40 / 25 / 14 / 11 | 0 |
| Column widths changed | 87 | 0 |
| Column / row default style lost | 55 / 48 | 0 |
| Hidden columns / rows became visible | 11 / 8 | 0 |
| Rows with attributes but no cells dropped | 32 | 0 |
| Styling of merged regions lost | 32 | 0 |
| `<fonts count>` no longer matches the table | 134 | 0 |

The nine files Kexcel still cannot open are seven fuzzer outputs, one whose zip entries
use backslashes (R-11) and one decompression bomb (R-10).

Two files still differ: `56702.xlsx` has 25 cells with an empty `<v></v>`, which POI
counts as the number 0 and Kexcel as empty; `52575_main.xlsx` has one formula whose cached
string is stored in a non-standard place. POI also prints the text of 1,394 expanded
shared-formula followers with different spacing than the file has; the formulas are the same.

## How the evidence was gathered

1. **Code read** of every file under `kexcel/src/commonMain`, against the matching POI and Dart sources.
2. **Targeted probes**: about 50 throwaway JVM tests, one per suspected defect. Each builds a
   workbook (with POI, with Kexcel, or from hand-written XML), runs it through Kexcel, and
   reads the result back with POI.
3. **Corpus run**: all 354 `.xlsx`/`.xlsm` files in `reference/poi/test-data/spreadsheet`
   were put through `Excel.decodeBytes(bytes).encode()` with **no edits**, and POI compared
   the workbook before and after, cell by cell (value, formula, cached result, style, row and
   column attributes, sheet-level features).

The probe sources are not committed. Every finding below says which kind of evidence backs it:

- **Probe**: reproduced by a targeted probe; the observed output is quoted.
- **Corpus**: observed on real files; the file count is out of the 301 files that completed
  the round trip.
- **Read**: found by reading the code and not executed. Treat these as probable.

Nothing here was checked in Excel itself. Where POI is lenient and Excel may not be (or the
reverse), the finding says so.

## Corpus result at a glance

A no-edit load and save should change nothing. Of 354 files:

| Outcome | Files |
|---|---|
| POI cannot open the original (corrupt on purpose, Strict OOXML, encrypted) | 24 |
| Kexcel cannot open a file POI opens | 28 |
| Round trip completed | 301 (one more completed when run alone) |

What changed in the 301 files that completed, with no edits made:

| Change after a no-edit round trip | Files | Finding |
|---|---|---|
| Cached formula results dropped | 120 | D-15 |
| Column widths changed | 87 | F-05 |
| Number format of a cell changed | 58 | F-01, F-02, F-03 |
| Column default style lost | 55 | F-05 |
| Row default style lost | 48 | F-04 |
| **Formulas emptied (shared-formula followers)** | **47** (17,785 cells) | D-02 |
| Font of a cell changed | 47 | F-01, F-02 |
| Fill of a cell changed | 40 | F-01, F-02 |
| Rows with attributes but no cells dropped | 32 | F-04 |
| Styling of merged regions lost | 32 (10,825 cells) | F-07 |
| **Header/footer text corrupted** | **26** | D-09 |
| Alignment of a cell changed | 25 | F-01 |
| Cell protection (locked/hidden) changed | 14 | F-01 |
| Hidden columns became visible | 11 | F-05 |
| Borders of a cell changed | 11 | F-01, F-02 |
| Hidden rows became visible | 8 | F-04 |
| Numeric values changed | 3 | D-05, D-08 |
| **Every shared string wiped** | 1 | D-01 |
| `<fonts count>` no longer matches the table | 134 | O-01 |

## Severity

| Level | Meaning |
|---|---|
| **S1** | Cell data is lost or wrong: values, formulas or text, on read or on save, without an error. |
| **S2** | Kexcel writes a package that other tools reject, repair, or read differently. |
| **S3** | Kexcel cannot open a valid workbook. |
| **S4** | Formatting or sheet structure is lost or changed on a round trip. |
| **S5** | Minor: API traps, hardening, cleanup. |

**Origin** says where a defect came from: *Dart* (inherited from the upstream library,
present in `reference/dart-excel`), *Port* (introduced while porting), or *Kexcel*.

---

## S1: data loss and corruption

### D-01 A workbook written with a namespace prefix loses every string on save

- **Evidence**: Corpus + Probe. `65016.xlsx` uses `<x:workbook>` / `<x:sheet>`. `decodeBytes`
  succeeds with **0 sheets** in the model (POI sees 3). `encode()` then rewrites
  `sharedStrings.xml` from the empty model: 89,379 bytes become 142, and `Splits!A1` goes
  from `Leg 1` to an empty string, as do 2,616 other cells.
- **Cause**: every lookup is by qualified tag name, for example `getElementsByTag("sheet")`
  (`parser/Parser.kt:186`), `"Relationship"` (`:76`), `"worksheet"` / `"sheetData"` (`:529`, `:538`).
  A prefix is legal XML and is what the OpenXML SDK and Python's ElementTree emit.
- **Related failures from the same cause**
  - A prefixed worksheet (`InlineString.xlsx`, `<x:worksheet>`): `NullPointerException` at `Parser.kt:551`.
  - Prefixed relationships (`58760.xlsx`, `<ns0:Relationship>`): "Damaged Excel file: styles".
  - Four corpus files lose sheets this way: `65016.xlsx`, `59746_NoRowNums.xlsx`,
    `craftonhills…xlsx`, `style-alternate-content.xlsx`.
- **Fix**: match on namespace URI and local name everywhere. Independently, `encode()` should
  refuse to rewrite `sharedStrings.xml` for a workbook whose sheets it did not parse, and a
  workbook that yields zero sheets should fail on decode.
- **Tests**: reader conformance with a prefixed workbook, worksheet, shared strings, styles and
  rels; a corpus fixture (`65016.xlsx`).
- **Origin**: Dart. **Status**: Fixed in `f95a967`.

### D-02 Shared formulas: every follower cell is saved as an empty formula

- **Evidence**: Corpus + Probe. 47 of 301 files, 17,785 cells. Excel writes a shared formula
  whenever a formula is filled down, so this affects most real spreadsheets with formulas.
  Probe: `B2`, which holds `<f t="shared" si="0"/>`, reads as `FormulaCellValue("")` and is
  written as `<f></f><v></v>`. POI reads `A2*2` before and an empty formula after.
- **Cause**: the reader takes the text of `<f>` and nothing else (`parser/Parser.kt:600-603`),
  and the writer emits a bare `<f>` (`save/SaveFile.kt:134-137`), which drops `t`, `si` and `ref`.
- **Reference**: `XSSFCell.convertSharedFormula` (`reference/poi/poi-ooxml/.../usermodel/XSSFCell.java:428`).
- **Fix, in two steps**: (1) keep the `<f>` attributes on the value and write them back, which
  makes the round trip lossless without a formula parser; (2) expand followers on read the
  way POI does, which needs a tokenizer that can shift relative references.
- **Tests**: `ForeignWorksheetXmlTest.theHostOfASharedFormulaKeepsItsExpression` currently
  *asserts* the empty follower. It has to change when this is fixed. Add a POI cross-check on
  `shared_formulas.xlsx` and `51710.xlsx`.
- **Origin**: Dart. **Status**: Fixed in `375eacb`. Followers are expanded on read, as POI does.

### D-03 Array, data-table and dynamic-array formulas lose their type

- **Evidence**: Probe + Corpus. `<f t="array" ref="C1:C3">` is saved as a plain `<f>`; POI
  reports `isPartOfArrayFormulaGroup` true before and false after. A `t="dataTable"` formula
  has no text and is saved as `<f></f>`. The `cm` and `vm` cell attributes (dynamic arrays,
  rich values) are dropped because `<c>` is rebuilt with only `r`, `s`, `t` (`SaveFile.kt:104-110`).
  In `MatrixFormulaEvalTestData.xlsx`, 119 cells change from formula to number for POI.
- **Fix**: same carrier as D-02 step 1: preserve `<f>` attributes, and `cm` / `vm` / `ph` on `<c>`.
- **Origin**: Dart. **Status**: Fixed in `375eacb`.

### D-04 Adding a sheet can overwrite an existing sheet's data

- **Evidence**: Probe. A workbook with sheets `One` (`sheetId=1`, `sheet1.xml`) and `Two`
  (`sheetId=3`, `sheet2.xml`). Excel produces this layout after a sheet is deleted. Adding
  sheet `New` picks the free id 2 and writes `xl/worksheets/sheet2.xml`, replacing `Two`'s
  part. Both relationships then point at the same file and `Two`'s content is gone.
- **Cause**: `createSheet` derives the part name from the lowest free `sheetId`
  (`parser/Parser.kt:692-704`, `:718`, `:764`). `sheetId` and part names are unrelated.
- **Fix**: choose a part name that is not already in the archive.
- **Tests**: package test: add a sheet to a workbook with a `sheetId` gap, then assert no
  part is replaced and every sheet's cells survive.
- **Origin**: Dart. **Status**: Fixed in `d63c381`.

### D-05 Numbers are read as dates when the format contains a colour or currency tag, and are then altered

- **Evidence**: Probe + Corpus.
  - `[Red]0.00` holding `1234.123456789` reads as `DateTimeCellValue(1903-05-18T02:57:46.667)`
    and is saved as `1234.1234567939814`.
  - `[Red]0` holding `1.0E12` is saved as `1.0675199116730064E11`.
  - A value below 1 in such a format reads as a time and its format is **replaced with
    `h:mm`** (see F-02), so `0.5` displays as `12:00`.
  - A negative value makes the whole workbook fail to open (R-04).
  - `58896.xlsx`: 1,207 cells in format `[Red]#,##0;\-#,##0;0` read as times, 5 values altered.
- **Cause**: `formatCodeLooksLikeDateTime` (`number_format/BaseNumFormat.kt:427-452`) looks for
  the letters `y m d h s` anywhere outside quotes. `[Red]`, `[White]`, `[Cyan]` and
  `[$Rs.-4009]` contain them. It is also case-sensitive, so `DD/MM/YYYY` is **not** seen as a
  date (F-03).
- **Reference**: `DateUtil.isADateFormat` (`reference/poi/poi/.../ss/usermodel/DateUtil.java`,
  patterns at lines 69-78) strips bracketed sections first and is case-insensitive. Kexcel
  disagreed with it on 12 of 39 sample format codes.
- **Fix**: port POI's rule. Never let a date reading change the stored number: keep the raw
  serial and write it back unchanged if the cell was not edited.
- **Origin**: Dart. **Status**: Fixed in `8ce5e08`, `375eacb`. Detection ported from POI; unedited numbers are written back digit for digit.

### D-06 The 1904 date system is ignored

- **Evidence**: Probe + Corpus. In a workbook with `<workbookPr date1904="1"/>`, POI reads
  `2024-03-15` and Kexcel reads `2020-03-14`. A date Kexcel writes into that workbook shows
  as `2028-03-16`. Corpus: `DateFormatTests.xlsx` and `48962.xlsx`, 52 dates, all 1,462 days early.
- **Cause**: a single fixed epoch (`BaseNumFormat.kt:246-251`). Nothing reads `date1904`.
- **Reference**: `XSSFWorkbook.isDate1904` (`XSSFWorkbook.java:1946`).
- **Origin**: Dart. **Status**: Fixed in `8ce5e08`.

### D-07 Dates before 1 March 1900 are off by one day; earlier dates become negative serials

- **Evidence**: Probe. Serial `1` reads as `1899-12-31` (POI: `1900-01-01`), serial `59` as
  `1900-02-27`. Writing `DateCellValue(1900, 1, 1)` stores serial `2`, which Excel shows as
  2 January. `DateCellValue(1850, 6, 1)` is written as `-18109`, which is not a valid Excel date.
- **Cause**: the `1899-12-30` epoch is only right from serial 61 on, because Excel counts a
  29 February 1900 that never existed.
- **Reference**: `DateUtil.getJavaDate`, the `wholeDays < 61` branch (`DateUtil.java:428`).
- **Fix**: adjust below serial 61, and reject dates Excel cannot represent.
- **Origin**: Dart. **Status**: Partly in `8ce5e08`. Serials below 61 fixed in both directions. A date before 1900 is still written as a negative serial.

### D-08 Times of day are truncated to the millisecond below, and date-times are re-quantised

- **Evidence**: Probe + Corpus.
  - 11,593 of the 86,399 whole-second times of a day read back one millisecond short through
    a date or custom time format. `00:00:11` (`1.273148148148148E-4`) reads as `00:00:10.999`
    and prints as `00:00:10`.
  - On save the value is rebuilt from milliseconds, so unedited cells change:
    `0.64604166` becomes `0.6460416550925926` (`123233_charts.xlsx`), and
    `42354.58333333334` becomes `42354.583333333336` (`58747.xlsx`).
- **Cause**: `TimeCellValue.fromFractionOfDay` uses `.toLong()` (`sheet/DataModel.kt:224-227`).
  Dart uses `.round()` (`reference/dart-excel/lib/src/sheet/data_model.dart:272-276`).
  `TimeNumFormat.read` rounds correctly, so only this path is affected.
- **Fix**: round. Separately, keep the original serial for cells that were not edited.
- **Origin**: Port (truncation); Dart (re-quantising). **Status**: Fixed in `8ce5e08`, `375eacb`.

### D-09 Header and footer text is corrupted on every save

- **Evidence**: Corpus + Probe. 26 of 301 files, with no edit. `Page &P` becomes
  `Page &amp;P` as literal text, so every `&` field code (`&P`, `&N`, `&L`, `&"Font,Bold"`) is
  destroyed. The letters `amp` inside a word are replaced too: `Sample` becomes `S&amp;le` and
  `Campaign` becomes `C&amp;aign`. Line breaks collapse to spaces.
- **Cause**: `simplifyText` replaces `amp` with `&` and then escapes by hand
  (`sheet/HeaderFooter.kt:131-137`); ksoup escapes the text node again on output. The reader
  uses `.text()`, which normalises whitespace (`HeaderFooter.kt:109-114`).
- **Fix**: delete `simplifyText`, pass the raw string to the text node, read with `wholeText()`.
- **Tests**: the existing header/footer tests compare Kexcel with itself, which is why they
  pass. Add a POI cross-check with `&P`, a literal `&`, a quote and a newline.
- **Origin**: Dart (escaping), Port (whitespace). **Status**: Fixed in `010c5a9`.

### D-10 After a row or column is removed or inserted, `Data` objects write to the wrong cell

- **Evidence**: Probe. After `removeRow(0)`, the cell now at row 0 still reports
  `rowIndex = 1`. Assigning `data.value` on it changed the cell in row **1**. After
  `insertColumn(0)`, the cell at column 1 reports `columnIndex = 0`.
- **Cause**: only `insertRow` updates `_rowIndex` (`sheet/Sheet.kt:558-560`). `removeRow`
  (`:475-488`), `insertColumn` (`:388-409`) and `removeColumn` (`:309-332`) move the objects
  without updating them, and `Data.value` / `setFormula` address the sheet by the stored index
  (`sheet/DataModel.kt:67-76`).
- **Also**: row heights and column widths are keyed by index and are not shifted by any of
  the four operations (probe: `{2=40.0}` unchanged after `removeRow(0)`).
- **Origin**: Dart. **Status**: Fixed in `308bd3b`.

### D-11 `_xHHHH_` escapes are not decoded on read or encoded on write

- **Evidence**: Probe + Corpus.
  - Read: Excel stores a carriage return in a string as `_x000D_`. Kexcel returns the literal
    text `line1_x000D__x000A_line2`; POI returns `line1\r\nline2` (`60289.xlsx`).
  - Write: control characters are dropped without an error (`a\u0001b` is saved as `ab`). A
    lone `\r` is written raw and becomes `\n` for any XML reader. A literal `_x000D_` typed by
    a user is read by Excel and POI as a carriage return.
- **Reference**: `XSSFRichTextString.utfDecode` (`XSSFRichTextString.java:539`, pattern at `:80`).
- **Origin**: Dart. **Status**: Fixed in `8b86da7`.

### D-12 Inserting or removing rows and columns shifts cell values only

- **Evidence**: Probe. After `insertRow(0)`: `SUM(A1:A3)` moved to `A5` and still reads
  `SUM(A1:A3)`; the hyperlink stays on `A2`, the comment on `A3`, the data validation and
  conditional format on `A1:A3`, the autofilter on `A1:A3`, and the defined name on `$A$1:$A$3`.
  All of them now point at different data than before.
- **Cause**: the model holds values and merges only. Everything else is kept as untouched
  XML, which is right for a no-edit save and wrong after a structural edit.
- **Reference**: `XSSFRowShifter` / `XSSFRowColShifter` update formulas, named ranges,
  conditional formatting and hyperlinks (`reference/poi/poi-ooxml/.../usermodel/helpers/`).
- **Fix options**: shift the ranges in the retained XML, or document the limit clearly and
  refuse structural edits on sheets that carry such features.
- **Origin**: Dart. **Status**: Partly fixed in `32cb58c`. Formulas, defined names and the ranges in the worksheet and comments parts follow the edit, checked against POI's `shiftRows` and `shiftColumns`. Still open: tables, image and chart anchors, pivot tables, threaded comments, page breaks and the saved selection.

### D-13 `rename()` rebuilds the sheet from scratch; `copy()` drops merges

- **Evidence**: Probe. After `rename("Data", "Renamed")`: the sheet moves to the **last**
  tab; the freeze pane, data validation, hyperlink and conditional format are gone; a formula
  in another sheet still reads `Data!A1*2`; the print area and a named range still refer to
  `Data`. `copy()` gives a sheet without the source's merged regions or pane.
- **Cause**: `rename` is `copy` followed by `delete` (`Excel.kt:166-174`), so the new name
  gets a blank worksheet part at save time. A cloned sheet registers its merges but does not
  set `excel.mergeChanges` (`sheet/Sheet.kt:61-64`), and `<mergeCells>` is only written when
  that flag is set (`save/SaveFile.kt:492`).
- **Side effect**: the old sheet's `_rels`, comments and drawing parts stay in the package
  as orphans. If a later sheet reuses the part name, it inherits them (seen in the probe).
- **Fix**: rename by changing the `name` attribute of `<sheet>` and rewriting sheet
  references in formulas and defined names.
- **Origin**: Dart. **Status**: Fixed in `27724f8`. `copy()` still starts from an empty worksheet part, so panes and validations are not copied (F-11).

### D-14 `delete()` and `setDefaultSheet()` break sheet-scoped names and the active tab

- **Evidence**: Probe. Three sheets; a print area scoped to `S3` (`localSheetId="2"`) and a
  name scoped to `S2`.
  - `delete("S1")`: `localSheetId="2"` now points past the end, the other name has silently
    moved from `S2` to `S3`, and `activeTab="2"` is out of range.
  - `setDefaultSheet("S3")`: the tab order becomes `S3, S1, S2`, and both names are now
    scoped to the wrong sheets.
- **Cause**: `localSheetId` and `activeTab` are positions. `delete` (`Excel.kt:179-226`) and
  the default-sheet code, which moves the `<sheet>` element to the front
  (`save/SaveFile.kt:577-601`), change positions without renumbering.
- **Fix**: renumber or drop `localSheetId` on delete, clamp `activeTab`, and implement the
  default sheet with `activeTab` instead of reordering tabs.
- **Origin**: Dart. **Status**: Fixed in `27724f8`. `setDefaultSheet` still moves the tab to the front; the names follow it now.

### D-15 Cached formula results are discarded on every save, and cannot be read

- **Evidence**: Corpus + Probe. 120 of 301 files. `<f>A1+A2</f><v>5.0</v>` is saved as
  `<f>A1+A2</f><v></v>`, and `t="str"` is dropped from string results. Nothing sets
  `fullCalcOnLoad`. `xl/calcChain.xml` is kept even when a formula it lists was replaced with
  a number (probe on `45540_classic_Footer.xlsx`).
- **Impact**: Excel recalculates on open, so it looks fine there. Anything that does not
  calculate sees empty cells or `0`: pandas/openpyxl with `data_only`, POI without an
  evaluator, previewers, and Kexcel itself. A stale calc chain is a known cause of Excel's
  repair prompt (not verified here).
- **API gap**: a formula cell reads as `FormulaCellValue(formula)`. There is no way to get
  the value the formula evaluated to, which is what most readers want.
- **Reference**: POI removes calc-chain entries when a formula is removed
  (`XSSFWorkbook.onDeleteFormula`, `XSSFWorkbook.java:2048`).
- **Fix**: carry the cached value and its type on `FormulaCellValue`; write it back for
  unedited cells; drop `calcChain.xml` (and its relationship and content type) or set
  `fullCalcOnLoad` once any cell has changed.
- **Origin**: Dart. **Status**: Fixed in `375eacb`. Cached results kept until a value changes, then dropped with `fullCalcOnLoad` set and the calc chain removed. Reading a cached result through the API is still not possible.

### D-17 to D-23 Other S1 findings

Status for the findings listed in tables is in **Progress** above.

| ID | Finding | Evidence | Origin |
|---|---|---|---|
| D-17 | `DoubleCellValue(NaN)` / `Infinity` are written as `<v>NaN</v>` / `<v>Infinity</v>`. Kexcel then cannot re-read its own file (`NumberFormatException`). Neither is a valid cell value. Reject on write. | Probe | Dart |
| D-18 | An error cell (`t="e"`, `#N/A`) reads as `FormulaCellValue("#N/A")` and is saved as `<f>#N/A</f>`: a literal error becomes a formula. 389 cells in 3 corpus files. Needs an error value type. Pinned by `ForeignWorksheetXmlTest.errorCellsSurfaceTheirErrorLiteral`. | Corpus | Kexcel |
| D-19 | A formula with a leading `=` is written verbatim (`<f>=SUM(A1:A1)</f>`). POI: "starts with an equals sign which is not allowed". The docs tell users to include it (`docs/guides/formulas.md:55`, README, quick start, the `setFormula` KDoc at `DataModel.kt:64`). Strip one leading `=` on write. | Probe | Dart |
| D-20 | A cell typed `t="s"` or `t="inlineStr"` that also has `<f>` loses the formula (`Parser.kt:600`). Seen in `52575_main.xlsx` (LibreOffice). | Corpus | Kexcel |
| D-21 | Cells inside a merged region other than the top-left are deleted on read (`Parser.kt:246-259`), with their values and styles. Excel allows such cells to exist. See F-07 for the styling effect. | Corpus | Dart |
| D-22 | `findAndReplace` and any text edit replace a rich-text cell with plain text. Documented by a test; listed for completeness. | Read | Dart |
| D-23 | The XML library writes tabs and line breaks in attribute values, and carriage returns in text, unescaped. XML then normalises them: a two-line data validation message or hyperlink tooltip came back as one line after a no-edit save, and a carriage return as a line feed. Found while fixing D-11. | Probe | Kexcel |

---

## S2: output other tools reject or misread

### O-01 New styles get wrong font, fill and border indexes

Four separate defects in `processStylesFile` (`save/SaveFile.kt:209-435`) and the style
reader (`parser/Parser.kt:262-492`). Together they are the most common cause of visible
damage, because the parser itself creates "new" styles (F-02).

- **(a) Reusing an existing font, fill or border gives index 0.**
  Probe: `a1Style.copyWith(italicVal = true)` on a bold, red-filled, bordered cell keeps the
  font and loses the fill and border. `copyWith(backgroundColorHexVal = blue)` keeps the fill
  and loses the font and border.
  Cause: an id is only computed for entries added in this save (`SaveFile.kt:383-398`); one
  already in the file is looked up in the wrong list and falls back to `0`.
- **(b) The font index base is wrong.**
  Probe on an Excel-made fixture: a new Courier New 20 pt bold style is written with a
  `fontId` that points at Calibri 11.
  Cause: the base is the number of *distinct fonts referenced by `cellXfs`*
  (`Parser.kt:407`), not the number of `<font>` elements. Most Excel files have more.
- **(c) Fill and border bases count entries that are not in `<fills>` / `<borders>`.**
  Probe: a workbook with one conditional format, plus one new background colour, is written
  with `fillId="3"` where only fills 0 to 2 exist. POI: `IndexOutOfBoundsException`.
  Cause: `getElementsByTag("patternFill")` and `("border")` also match the entries under
  `<dxfs>` (`Parser.kt:277`, `:289`); a fill with no `fgColor`, or a `gradientFill`, adds none.
- **(d) A background colour whose alpha is not `FF` is skipped but still counted.**
  Probe: cells filled `ExcelColor.black26`, red, blue. `<fills count="5">` holds 4 entries;
  the first cell shows red, the second blue, and the third has an out-of-range `fillId`.
  Cause: `SaveFile.kt:301-325` only emits a fill when the hex starts with `FF`. Many named
  `ExcelColor` constants (`black12`…`black87`, `white10`…) have another alpha.
- **Also**: the `count` attributes are written from the same wrong lists. After a no-edit
  round trip `<fonts count>` is wrong in 134 of 301 files, `<fills count>` in 9, `<borders count>` in 7.
- **Tests**: `OoxmlPackageTest.everyCellStyleIndexPointsAtAnExistingCellXf` checks `s` only.
  Add the same check for `fontId`, `fillId`, `borderId` and `numFmtId`, and for each
  `count`, on files that have `dxfs` and unused fonts.
- **Origin**: Dart. **Status**: Fixed in `99426ca`.

### O-02 Worksheet children are written out of schema order

CT_Worksheet is a sequence, and Excel rejects a part whose children are out of order. POI is lenient.

| Case (Probe) | Result |
|---|---|
| New sheet, then `setDefaultRowHeight` | `sheetFormatPr` first, before `dimension` and `sheetViews` |
| Sheet with no `<sheetFormatPr>`, then `setDefaultColumnWidth` | `sheetFormatPr` before `sheetPr` |
| Sheet with `autoFilter` and `sheetProtection`, then a new merge | `mergeCells` before `sheetProtection` and `autoFilter` |
| Sheet with no `<sheetViews>`, then `isRTL = true` | `sheetViews` last, after `sheetData` and `pageMargins` |

- **Cause**: `prependChildren` for `sheetFormatPr` (`SaveFile.kt:777-780`), `sheetData.after`
  for `mergeCells` (`:665-676`), `appendChild` for `sheetViews` (`:724-732`).
- **Fix**: one helper that inserts a child at its position in the sequence. `setHeaderFooter`
  already does this for one element (`:634-645`).
- **Tests**: the two existing order tests use the built-in template, which never has the
  elements that expose this. Run the order assertion over fixtures with autofilter, protection,
  conditional formatting and no sheet views.
- **Origin**: Dart. **Status**: Fixed in `e30bf68`.

### O-04 to O-11 Other S2 findings

| ID | Finding | Evidence | Origin |
|---|---|---|---|
| O-04 | `encode()` is not idempotent. Each call appends the new fonts, fills and `xf` entries again while the `count` attributes stay at the first value (fonts `count=4`, actual 7, then 10). Build the output from a copy of the parsed DOM. | Probe | Dart |
| O-05 | No sheet-name validation. Accepted: empty name, 40 characters, `/ : * ? [ ]`, a name in quotes, and `sheet1` next to `Sheet1`. Excel refuses or repairs all of these. Reference: `WorkbookUtil.validateSheetName` (`reference/poi/poi/.../ss/util/WorkbookUtil.java:125`). | Probe | Dart |
| O-06 | `<dimension>` is never updated. A new sheet always says `A1`; a loaded sheet keeps its old range after cells are added outside it. Readers that trust it (openpyxl read-only mode, streaming readers) truncate. | Probe | Dart |
| O-07 | Borders are written without `applyBorder="1"`, so POI reports `NONE` for every border Kexcel creates. Excel's behaviour was not checked. `applyNumberFormat` and `applyProtection` are never written either. | Probe | Dart |
| O-08 | No limits are enforced: a 40,000-character string is accepted (Excel's limit is 32,767), as are column widths above 255 and row heights above 409. | Probe (string) / Read | Dart |
| O-09 | `HorizontalAlign.Left` is the default and is never written, so a left-aligned number is saved as `general` and shows right-aligned. The same holds for `VerticalAlign.Bottom`. | Probe | Dart |
| O-10 | `[Content_Types].xml` is the last zip entry. OPC does not require it first, but some streaming consumers expect it early. | Probe | Kexcel |
| O-11 | Integers beyond 2^53 are written with all digits (`9007199254740993`). Excel stores doubles with 15 significant digits, so the value changes there. Document or warn. | Probe | Kexcel |

---

## S3: valid workbooks that cannot be opened

28 corpus files that POI opens fail in Kexcel. Seven are fuzzer output and are ignored here.

| ID | Finding | Files / Evidence | Origin |
|---|---|---|---|
| R-01 | A workbook containing a **chart sheet, macro sheet or dialog sheet** fails with "Missing worksheet part for sheet '…'". `<sheet>` entries whose relationship is not a worksheet must be kept and skipped (`Parser.kt:516-525`). | 9 files, e.g. `WithChartSheet.xlsx`, `chart_sheet.xlsx`, `xlmmacro.xlsm` | Dart |
| R-02 | A workbook with **no styles part** fails with "Damaged Excel file: styles" (`Parser.kt:262-264`). The part is optional and minimal writers omit it. | `56278.xlsx`, `59021.xlsx`; Probe | Dart |
| R-03 | `<c t="s"><v/></c>` (a string cell with an empty value) fails with "non-numeric shared string index". Treat as blank. `aNonNumericSharedStringIndexIsReportedAsADamagedFile` should keep covering real garbage only. | `64508.xlsx`, `64667.xlsx` | Kexcel |
| R-04 | A **negative number** in a date or time format, or in a format mis-detected as one (D-05), throws `IllegalArgumentException: hour must be >= 0` and the whole workbook fails. Probe: `-5` in `[Red]0.00`, `-0.25` in `h:mm`. | `bug60858.xlsx`; Probe | Dart |
| R-05 | **Relationship ids that are not `rId<number>`** throw `NumberFormatException` in `getAvailableRid` (`Parser.kt:663-670`): on open when the workbook has no shared-strings part, and on save when a sheet is added. Such ids are what the OpenXML SDK writes. | `60825.xlsx`, `52348.xlsx`; Probe | Dart |
| R-06 | **Numeric forms**: a lowercase exponent (`1e5`), whitespace around the number (pretty-printed XML), an integer above `Long` range, and ISO dates in `t="d"` cells all throw `NumberFormatException` (`BaseNumFormat.kt:175-196`). | Probe | Dart |
| R-07 | A cell `s` beyond `cellXfs` throws `IndexOutOfBoundsException` instead of a damaged-file error. | Probe | Dart |
| R-08 | **Merged regions are parsed in more than quadratic time.** `57893-many-merges.xlsx` (50,000 merges, 850 KB) did not finish in 120 s. Each `mergeCell` calls the `spannedItems` getter, which rebuilds the whole list with a linear `contains` (`Parser.kt:214-239`, `Sheet.kt:1096-1109`). | `57893-many-merges.xlsx` | Dart |
| R-09 | **Memory**: about 2 KiB of heap per cell. 200,000 cells use about 400 MiB to encode; 1,000,000 cells run out of a 512 MiB heap. `Sheet.rows` allocates `maxRows × maxColumns`. There is no streaming path. | Probe | Dart |
| R-10 | **No guard against decompression bombs.** `poc-shared-strings.xlsx` exhausts the heap in 264 ms. POI refuses it (`ZipSecureFile`). Matters wherever the input is untrusted. | `poc-shared-strings.xlsx` | Dart |
| R-11 | Zip entries with backslashes or different letter case (`xl\workbook.xml`, `[content_types].xml`) are rejected. POI opens them. Low priority. | `49609.xlsx` | Dart |
| R-12 | The workbook part is assumed to be `xl/workbook.xml` (`Excel.kt:451-467`). It should be found through `_rels/.rels`. | Read | Dart |
| R-13 | Errors surface as a mix of `IllegalArgumentException`, `NumberFormatException`, `NullPointerException`, `IndexOutOfBoundsException`, `StringIndexOutOfBoundsException` and `UnsupportedOperationException`. Callers cannot tell a bad file from a bug. | Corpus | Kexcel |

---

## S4: formatting and structure lost on a round trip

### F-01 Cells are re-pointed at the first style that "looks equal"

- **Evidence**: Probe + Corpus. With no edits: a theme-colour fill becomes no fill; an
  indexed-colour fill (what POI's `IndexedColors` writes) becomes no fill; indent 3 becomes 0;
  `justify` becomes `general`; an unlocked cell becomes locked; a cell with a theme font
  colour **gains strike-through**, because the first "equal" style happened to have it.
  Corpus: number format changed in 58 files, font in 47, fill in 40, alignment in 25,
  protection in 14, borders in 11. In `FillWithoutColor.xlsx` locked cells become unlocked.
- **Cause**: parsing every cell sets `styleChanges` (`sheet/Sheet.kt:612-615`), so the save
  path always re-resolves the index with `cellStyleList.indexOf(style)`
  (`save/SaveFile.kt:114-124`). `CellStyle` equality covers only the properties Kexcel
  models, so two `xf` entries that differ in anything else are the same style to it, and the
  cell takes the earlier one.
- **Fix**: remember the original `s` on each parsed cell and write it back unless the cell's
  style was changed. Derive an edited style from the original `xf` rather than from the model alone.
- **Tests**: a POI cross-check that compares a full style signature per cell before and
  after, not just values.
- **Origin**: Dart. **Status**: Fixed in `375eacb`, `0377f78`, `99426ca`.

### F-02 The number format is replaced when Kexcel thinks it does not fit the value

- **Evidence**: Probe. On a plain load and save:

  | Cell | Format before | Format after |
  |---|---|---|
  | Text `00123` | `@` (Text) | `General` |
  | Text header | `#,##0` | `General` |
  | `0.5` | `hh:mm:ss` | `h:mm` |
  | `1.25` (30 hours) | `[h]:mm:ss` | `m/d/yy h:mm` |
  | `0.25` | `m/d/yy` | `h:mm` |

  In the first three rows the cell also lost its **font, fill and border** (Arial 14 bold on
  red with a border became Calibri 11, no fill, no border), because the replacement is a new
  style and O-01(a) applies.
- **Cause**: `updateCell` swaps in `NumFormat.defaultFor(value)` when
  `numberFormat.accepts(value)` is false (`sheet/Sheet.kt:599-610`), and the parser goes
  through `updateCell`. Text is only "accepted" by `General` (`BaseNumFormat.kt:208-218`);
  time values are not accepted by date formats, or the reverse (`:285-295`, `:362-372`).
- **Fix**: any format may sit on any value in Excel. Never change a format while parsing.
  If the check stays for the editing API, it should not run on load.
- **Tests**: `WorkbookRoundTripTest.aStyleThatRejectsTheValueFallsBackToTheDefaultFormat` and
  `changingAValueRelaxesAnIncompatibleFormat` pin the current behaviour.
- **Origin**: Dart. **Status**: Fixed in `375eacb`. On load. The editing API still swaps a format that cannot show the new value.

### F-03 The number-format table is incomplete

- Built-in ids **5-8, 23-36, 41-44 and 50-81 are missing** (`BaseNumFormat.kt:392-421`). They
  read as `General`: accounting (44) and currency (5) cells lose their format on save (Probe),
  and the CJK date ids are read as numbers.
- **Custom formats with an id below 164 are ignored** (`Parser.kt:324-332`). `bug66675.xlsx`
  defines id 82 as `[$-010409]dd-mmm-yyyy`: 312 dates read as plain numbers and the format is
  lost on save. The fixture `customNumFmtIdBelow164.xlsx` is only tested for not throwing.
- **Upper-case date codes are not recognised**: `DD/MM/YYYY` and `YYYY-MM-DD HH:MM` read as
  numbers (see D-05).
- Three codes in the table are wrong: id 21 is `h:mm:dd` (should be `h:mm:ss`), id 40 is
  `#,##0.00;[Red](#,#)` (should be `#,##0.00;[Red](#,##0.00)`), id 48 is `##0.0` (should be
  `##0.0E+0`). All three are the same in Dart.
- **Reference**: `BuiltinFormats` (`reference/poi/poi/.../ss/usermodel/BuiltinFormats.java`).
- **Origin**: Dart. **Status**: Partly in `8ce5e08`. Ids 5-8 and 41-44 added, three codes corrected, ids below 164 honoured. The locale-dependent ids 23-36 and 50-81 are still unknown.

### F-04 Row attributes are discarded

`<sheetData>` is emptied and rebuilt from cell values (`save/SaveFile.kt:766`, `:557-575`,
`:194-203`). Only `r`, `ht` and `customHeight` are written.

- Lost (Probe + Corpus): `hidden` (8 files: hidden rows become visible, including rows hidden
  by an autofilter that still claims to be applied), `outlineLevel` / `collapsed` (grouping),
  the row style `s` / `customFormat` (48 files), and any row that has a height or style but no
  cells (32 files).
- **Fix**: keep the original `<row>` attributes and re-attach them.
- **Origin**: Dart. **Status**: Fixed in `5e152ae`.

### F-05 `<cols>` is rebuilt from widths alone

- The reader takes `min` and `width` and ignores `max` (`Parser.kt:817-830`), so
  `<col min="10" max="20" width="30">` sets one column. The writer drops and rebuilds the
  element (`SaveFile.kt:517-555`) and emits an entry with `customWidth="1" bestFit="1"` for
  every column up to the last one that has a width.
- Lost (Probe + Corpus): widths of all but the first column of a range (width changed in 87
  files), `hidden` (11 files), the column default style (55 files), outline levels.
- **Origin**: Dart. **Status**: Fixed in `5e152ae`.

### F-06 Setting a value clears the cell's style

- **Evidence**: Probe. A bold, yellow, `0.00%` cell updated with `sheet.updateCell(index, value)`
  or `data.value = …` comes back as Calibri, no fill, format `0.00`.
- **Cause**: `putData` assigns a fresh `CellStyle` every time (`sheet/Sheet.kt:893-894`), and
  `updateCell` reads "the style before" only after that reset (`:606`).
- **Fix**: keep the existing style when none is passed.
- **Origin**: Dart. **Status**: Fixed in `38a45d0`.

### F-07 to F-12

| ID | Finding | Evidence | Origin |
|---|---|---|---|
| F-07 | Styling of merged regions is lost: the cells under a merge are deleted on read, and Excel stores the region's borders and fill on them. 32 files, 10,825 cells. Probe: thick border around `A1:C3` survives on `A1` only. | Corpus + Probe | Dart |
| F-08 | Widths and heights are written with two decimals (`Utility.kt:12-18`). Excel's are multiples of 1/256. Column widths shift by a unit in 115 files, default column width in 42. | Corpus | Dart |
| F-09 | Every style used by a cell is appended to `cellXfs` again on each save. `example.xlsx`: 4, 7, 9, 11 entries over three load-and-save cycles. Excel allows about 64,000. | Probe | Dart |
| F-10 | Font sizes are integers: 10.5 pt becomes 10 when a style is rebuilt (Dart rounds to 11). | Read | Port |
| F-11 | `copy()` does not carry the default column width and row height, and nothing kept only in the XML (panes, validations, …). See D-13. | Probe | Dart |
| F-12 | Colours are ARGB hex only. Theme, indexed and tinted colours read as black or "none", which is what makes F-01 so common. | Read | Dart |

---

## S5: minor

| ID | Finding | Evidence |
|---|---|---|
| M-01 | Reading with `sheet.cell(index)` creates the cell and grows `maxRows` / `maxColumns` (`Sheet.kt:118-142`). Reading `Z500` on a one-cell sheet makes it 500 × 26 and adds an empty `<c>` to the file. | Probe |
| M-02 | After `removeRow` / `removeColumn`, the old merge reference stays in an internal list (`Sheet.kt:296-301`, `:462-467`), so a later `merge()` of that range is silently ignored. | Probe |
| M-03 | `insertRowIterables` checks `columnIndex + row.size` against the column limit, which rejects a row that ends exactly at `XFD` (`Sheet.kt:853`). | Read |
| M-04 | `kotlinx-coroutines-core` and `kotlinx-serialization-json` are `commonMain` dependencies (`kexcel/build.gradle.kts`) and are not imported anywhere. They ship in the POM. | Read |
| M-05 | `TextWrapping.Clip` writes `shrinkToFit`, which shrinks text rather than clipping it. A naming trap. | Read |
| M-06 | `docProps/app.xml` (`TitlesOfParts`) is not updated when sheets are added, renamed or deleted. Harmless to Excel. | Read |
| M-07 | CI runs `:kexcel:jvmTest` and `connectedDebugAndroidTest` (`.github/workflows/publish.yaml`). `commonTest` is never run on iOS, macOS or wasm, so behaviour specific to those targets is untested. One to check first: how each target prints a `Double` such as `1.0E21`, because Kexcel's own reader rejects a lowercase exponent (R-06). | Read |

---

## Missing features

"Up to date with the spec" has two parts: not damaging what Kexcel does not understand
(covered above), and offering an API for it (this section). Status key: **API** = can be read
and written through Kexcel; **Kept** = no API, survives a no-edit save as raw XML;
**Lost** = damaged or dropped today.

| Area (ECMA-376 Part 1) | Status | Notes |
|---|---|---|
| Cell values: number, string, boolean | API | |
| Dates and times | API, with defects | D-05 to D-08; no 1904 system; no ISO `t="d"` |
| Error values | Lost | D-18; no error type |
| Formulas: plain | API | no cached result (D-15), no evaluation |
| Formulas: shared, array, data table, dynamic array | Lost | D-02, D-03 |
| Rich text | API (partial) | unmodelled run properties are kept while the cell is unedited |
| Number formats | API (partial) | F-03; no formatted display text (POI `DataFormatter`) |
| Fonts, fills, borders, alignment | API (partial) | no theme/indexed colours, pattern or gradient fills, strike, sub/superscript, indent, justify, reading order, fractional sizes |
| Cell protection (locked, hidden) | Lost | F-01 |
| Named cell styles (`cellStyleXfs`, `cellStyles`), `dxfs` | Kept | miscounted by O-01 |
| Merged cells | API | styling lost (F-07) |
| Column width, row height | API | F-04, F-05 |
| Hidden rows and columns, grouping/outline | Lost | F-04, F-05 |
| Row and column default styles | Lost | F-04, F-05 |
| Header and footer | API, corrupts | D-09 |
| Right-to-left sheets | API | |
| Freeze and split panes, zoom, gridlines, selection | Kept | lost by `rename` / `copy` |
| Sheet visibility, tab colour, sheet order | Kept | no API to hide or move a sheet |
| Defined names, print area, print titles | Kept | broken by delete / default sheet (D-14) |
| Hyperlinks | Kept | not shifted (D-12) |
| Comments and notes, threaded comments | Kept | not shifted |
| Data validation | Kept | not shifted |
| Conditional formatting | Kept | not shifted; triggers O-01(c) |
| Autofilter, sort state | Kept | not shifted; row `hidden` lost (F-04) |
| Tables (list objects), structured references | Kept | not shifted |
| Images, drawings, charts | Kept | chart **sheets** cannot be opened (R-01) |
| Pivot tables, slicers, sparklines (`extLst`) | Kept | |
| Page setup, margins, breaks | Kept | |
| Sheet and workbook protection | Kept | |
| Document properties (`docProps`) | Kept | no API |
| VBA project (`.xlsm`) | Kept | macro sheets cannot be opened (R-01) |
| Workbook calculation settings, calc chain | Kept, stale | D-15 |
| Strict OOXML (`purl.oclc.org` namespaces) | Not supported | POI 5.4.1 also refuses it |
| Encrypted workbooks, `.xls`, `.xlsb` | Not supported | reported as "format unsupported" |
| Large files (streaming read or write) | Not supported | R-09 |

Feature parity with the Dart library is complete: `reference/dart-excel` is at 5.0.0, the
latest release, and every public operation and all 21 upstream fixtures have a Kotlin
counterpart. Almost every defect above is inherited from it, so upstream is not a source of fixes.

---

## Missing tests

The suite is strong where reader and writer are compared with each other, and where POI
checks values. The gaps are all on the other side: fidelity of what Kexcel does not model,
and inputs from other producers.

### T-01 No test uses a real third-party corpus

**Done.** `PoiOracle` (jvmTest) compares two workbooks through POI, `assertOnlyDiffers`
turns that into a fidelity assertion with a list of known differences that can only
shrink, and `assertReadsLikePoi` checks every cell Kexcel reads against POI's reading.
`PoiCorpusRoundTripTest` runs 19 files copied into `test_resources/poi/`; every one now
round-trips with no difference. The paragraph below is the original proposal.

`reference/poi/test-data/spreadsheet` has 354 files and none are used. The one-off corpus
run in this audit found D-01, D-02, D-09, F-01 and most of S3 in minutes.

Proposed: copy a curated set (about 25 files, one per finding) into `test_resources/`, and
add one parameterised `jvmTest`: decode, encode, then let POI compare before and after
(values, formulas, cached results, style signature, row and column attributes, sheet
features). Keep a per-file allow-list of known differences so the test passes today and
each fix removes entries. Candidates: `65016.xlsx`, `51710.xlsx`, `shared_formulas.xlsx`,
`WithChartSheet.xlsx`, `56278.xlsx`, `64508.xlsx`, `bug60858.xlsx`, `60825.xlsx`,
`DateFormatTests.xlsx`, `48962.xlsx`, `58896.xlsx`, `bug66675.xlsx`, `Tables.xlsx`,
`GroupTest.xlsx`, `FillWithoutColor.xlsx`, `ConditionalFormattingSamples.xlsx`,
`45540_classic_Header.xlsx`, `60289.xlsx`, `57893-many-merges.xlsx`, `InlineString.xlsx`,
`58760.xlsx`, `xlookup.xlsx`, `MatrixFormulaEvalTestData.xlsx`.

### T-02 Tests that pin behaviour this audit calls a defect

| Test | Pins | Finding |
|---|---|---|
| `ForeignWorksheetXmlTest.theHostOfASharedFormulaKeepsItsExpression` | follower reads as an empty formula | D-02 |
| `ForeignWorksheetXmlTest.errorCellsSurfaceTheirErrorLiteral` | error literal as a formula | D-18 |
| `ForeignWorksheetXmlTest.aNonNumericSharedStringIndexIsReportedAsADamagedFile` | must not cover an empty `<v/>` | R-03 |
| `WorkbookRoundTripTest.aStyleThatRejectsTheValueFallsBackToTheDefaultFormat`, `changingAValueRelaxesAnIncompatibleFormat` | format replacement | F-02 |
| `HeaderFooterFileTest.*`, `ExcelInMemoryTest.setAndRemoveHeaderFooterInMemory` | compare Kexcel with itself, so the double escaping is invisible | D-09 |
| `ExcelFileTest.decodeCustomNumFmtIdBelow164` | only "does not throw" | F-03 |
| Docs, README and KDoc formula examples with a leading `=` | invalid `<f>` | D-19 |

Per the repo rule, change an expected value only with POI or the spec as evidence. For
each row the evidence is cited in the finding.

**Changed so far**, each with the evidence in the test comment and the commit message:

- `theHostOfASharedFormulaKeepsItsExpression` is now `aSharedFormulaIsExpandedForEveryCellThatSharesIt`.
- `NumberFormatTest.timeFormatsReadFractionsOfADay` expected serial 1 to be 31 December 1899; it is 1 January 1900.
- `NumberFormatTest.maintainerKnowsEveryStandardFormatUpFront` used the assigned id 5 as its example of an unassigned one.
- `SheetOperationsTest.sheetRemoveAndRenameOperations` expected no default sheet after renaming the default sheet.
- `aNonNumericSharedStringIndexIsReportedAsADamagedFile` is unchanged; an empty index has its own test.

Still pinned: `errorCellsSurfaceTheirErrorLiteral` (D-18), the two format-replacement tests
(the editing API keeps that behaviour), and `decodeCustomNumFmtIdBelow164`, which is now
backed by a POI comparison on `bug66675.xlsx`.

### T-03 Reader conformance (`ForeignWorksheetXmlTest`) cases to add

Added for every fixed finding. Not yet covered: every built-in format id, and the open findings.

Prefixed namespaces on every part (D-01); shared, array and data-table formulas (D-02, D-03);
`cm` / `vm` attributes; `t="d"`; lowercase exponent; whitespace inside `<v>`; an integer
above `Long`; `<c t="s"><v/></c>` (R-03); `_xHHHH_` escapes (D-11); `date1904` (D-06);
serials 1, 59, 60, 61 (D-07); a negative serial in a date format (R-04); `<col>` ranges with
`hidden` and `style`; `<row hidden>`; every built-in format id; custom ids below 164; a
workbook with no styles part, with no shared strings, with non-`rId` ids, and with a chart sheet.

### T-04 Package tests (`OoxmlPackageTest`) to add

All of these are in place.

- Child order after **each** mutating operation, on sheets that already have `autoFilter`,
  `sheetProtection`, `conditionalFormatting`, no `sheetViews`, no `sheetFormatPr` (O-02).
- `fontId`, `fillId`, `borderId`, `numFmtId` in range, and every `count` attribute equal to
  the real child count, on files with `dxfs` and unused fonts (O-01).
- `encode()` twice gives the same bytes (O-04).
- Adding a sheet never replaces an existing part, with a `sheetId` gap (D-04).
- `<dimension>` matches the used range (O-06).
- After `delete` / `rename` / `setDefaultSheet`: `localSheetId` and `activeTab` in range, no
  orphan parts, no content-type override for a missing part (D-13, D-14).
- No `calcChain.xml` entry for a cell that is no longer a formula (D-15).

### T-05 Model tests (`commonTest`) to add

In place, except `cell()` not growing the sheet (M-01, open).

- `Data.rowIndex` / `columnIndex` / `cellIndex` after each of the four structural
  operations, and that `data.value = …` edits the cell it was read from (D-10).
- Row heights and column widths follow their rows and columns (D-10).
- `merge()` after `removeRow` (M-02); `copy()` keeps merges (D-13).
- `updateCell` without a style keeps the style (F-06).
- `cell()` on an empty position does not grow the sheet (M-01).
- All 86,399 whole-second times, and date-times, round-trip exactly (D-08).
- Date detection: a table of format codes with POI's verdict as the expected value (D-05).
- Sheet-name validation (O-05); `NaN` / `Infinity` rejected (D-17).

### T-06 Interop tests (`PoiInteropTest`) to add

All in place. The last item compares Kexcel's insert and remove with POI's on the same workbook.

- Borders Kexcel writes are visible to POI (O-07). `poiReadsKexcelStyling` does not look at borders.
- Style fidelity, not only values: full style signature per cell before and after a Kexcel
  edit of a POI workbook, for theme and indexed colours, protection, indent, built-in formats (F-01).
- Hidden rows and columns, grouping, row and column styles survive (F-04, F-05).
- Header and footer field codes survive (D-09).
- Cached formula values survive for unedited cells (D-15).
- After `insertRow`: formulas, hyperlinks, comments, validations, conditional formats and
  names either move or the operation is refused (D-12).

### T-07 Wider gaps

- **Error contract**: no test says which exception a bad file produces (R-13). The seven
  fuzzer files in the POI corpus are a ready-made input set.
- **Scale**: no test above a few hundred cells. Add a time and memory budget for 100,000
  cells and for 10,000 merges (R-08, R-09).
- **Other targets**: `commonTest` is not run on iOS, macOS or wasm in CI (M-07).
- **Excel itself**: nothing is verified in Excel. A short manual checklist per release (open
  the sample outputs, check for the repair prompt) would cover O-02, O-07, D-15 and D-19,
  which POI cannot judge.

---

## Suggested order of work

Steps 1 to 6 are done. What is left, roughly by value: an API for cached formula results and
error values (D-15, D-18), the rest of D-12 (tables, drawings, pivot tables), R-09 and R-10
(memory and untrusted input), R-12, and the open S2 and S5 items.

1. **Stop destroying data on a no-edit round trip.** D-01, D-02 (step 1: preserve `<f>`
   attributes), D-09, D-04. Each is a small, contained change.
2. **Keep what was parsed.** One mechanism fixes a large group: remember, per cell, the
   original `s`, the raw `<v>` and the `<f>` attributes, and per row and column the original
   attributes; write them back when unedited. That closes F-01, F-02 (on load), F-04, F-05,
   F-07 (mostly), D-05 and D-08 (the value-changing part), D-15 and D-03.
3. **Fix the style writer.** O-01 (a) to (d), O-04, then O-02.
4. **Open more files.** R-01 to R-06, in corpus-frequency order.
5. **Dates.** D-05 (port POI's detection), D-06, D-07, D-08.
6. **Structural edits.** D-10, D-12, D-13, D-14.
7. **Put T-01 in place early.** With an allow-list it can land before any fix, and every
   later change shows up as entries removed from it.
