# Workbooks & Sheets

The [`Excel`](../../api/index.html) object is the whole workbook. A workbook contains one
or more [`Sheet`](../../api/index.html) objects, keyed by name.

## Creating and loading a workbook

```kotlin
import com.gyanoba.kexcel.Excel

val blank = Excel.createExcel()          // one sheet, "Sheet1"
val fromBytes = Excel.decodeBytes(bytes) // from a ByteArray
val fromStream = Excel.decodeStream(input) // from an InputStream
```

!!! note "Only `.xlsx` is supported"
    `decodeBytes` / `decodeStream` throw `UnsupportedOperationException` for
    anything that isn't an Office Open XML spreadsheet (for example, legacy
    `.xls` files).

## Accessing sheets

Index the workbook by name with the `[]` operator. **If the sheet doesn't exist,
it is created on demand** — this is the idiomatic way to add a new sheet:

```kotlin
val sheet = excel["Sheet1"]     // existing
val report = excel["Report"]    // created automatically
```

Get every sheet as a map:

```kotlin
val sheets: Map<String, Sheet> = excel.getSheets()
println(sheets.keys) // e.g. [Sheet1, Report]
```

Read a sheet's dimensions (both are 0-based counts of the used range):

```kotlin
println("${sheet.maxRows} rows x ${sheet.maxColumns} columns")
```

## Sheet operations

All of the following are methods on the `Excel` workbook object.

### Copy

Copies the contents of one sheet into another. The destination is created if it
doesn't exist, and receives an **independent** copy:

```kotlin
excel.copy(fromSheet = "Sheet1", toSheet = "Backup")
```

### Rename

Renames a sheet. The old name must exist and the new name must not:

```kotlin
excel.rename(oldSheetName = "Backup", newSheetName = "Archive")
```

### Delete

Deletes a sheet. A workbook always keeps **at least one** sheet, so a delete that
would empty the workbook is silently ignored:

```kotlin
excel.delete("Archive")
```

### The default sheet

The default sheet is the one shown first when the file is opened:

```kotlin
excel.setDefaultSheet("Sheet1")            // returns true on success
val name: String? = excel.getDefaultSheet()
```

## Linking sheets

Two names can point at the **same** underlying `Sheet` object. After linking,
edits through either name affect both:

```kotlin
excel.link("Alias", excel["Sheet1"]) // "Alias" and "Sheet1" now share data
excel.unLink("Alias")                // break the link; "Alias" keeps a copy
```

Compare this with [`copy`](#copy), which always produces independent data.

!!! tip "`link` vs `set`"
    Assigning with `excel["Name"] = otherSheet` (the `set` operator) *clones*
    `otherSheet` into a new, unlinked sheet. Use `link` when you specifically want
    the two names to stay in sync.

## The `tables` property

For compatibility with the original Dart API, `excel.tables` returns the same
`Map<String, Sheet>` as `getSheets()`. Prefer `getSheets()` in new code.

## Right-to-left sheets

Each sheet exposes an `isRTL` flag for right-to-left layouts:

```kotlin
excel["Sheet1"].isRTL = true
```

## Method reference

| Operation | Call |
| --- | --- |
| Get / create a sheet | `excel["Name"]` |
| All sheets | `excel.getSheets()` |
| Copy a sheet | `excel.copy(from, to)` |
| Rename a sheet | `excel.rename(old, new)` |
| Delete a sheet | `excel.delete(name)` |
| Set default sheet | `excel.setDefaultSheet(name)` |
| Get default sheet | `excel.getDefaultSheet()` |
| Link two names | `excel.link(name, sheet)` |
| Break a link | `excel.unLink(name)` |
