# Saving & Loading

Kexcel serializes a workbook to `.xlsx` bytes **in memory** with `encode()`.
Writing those bytes to a file is platform-specific — the library stays free of
platform I/O so it can run everywhere.

## Encoding to bytes

```kotlin
val bytes: ByteArray? = excel.encode()
```

`encode()` returns the complete `.xlsx` file as a `ByteArray` (or `null` if
encoding fails). These bytes are a valid workbook you can save, upload, or hand to
a share sheet.

## Writing to a file

=== "JVM / Desktop"

    ```kotlin
    import java.io.File

    val bytes = excel.encode() ?: error("Failed to encode workbook")
    File("out.xlsx").writeBytes(bytes)
    ```

=== "Android"

    ```kotlin
    val bytes = excel.encode() ?: error("Failed to encode workbook")

    // App-private storage:
    context.openFileOutput("out.xlsx", Context.MODE_PRIVATE).use { it.write(bytes) }

    // ...or a user-chosen location via the Storage Access Framework:
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    ```

=== "iOS"

    ```kotlin
    // Hand `bytes` to your Kotlin/Native or Swift file-writing code, e.g. via
    // NSData, or use a multiplatform file picker such as FileKit.
    val bytes = excel.encode() ?: error("Failed to encode workbook")
    ```

!!! tip "Multiplatform file I/O"
    For picking and writing files across targets, a library like
    [FileKit](https://github.com/vinceglb/FileKit) pairs well with Kexcel — the
    sample app uses it.

## Loading

Load a workbook from bytes or a stream:

```kotlin
val fromBytes = Excel.decodeBytes(bytes)
val fromStream = Excel.decodeStream(inputStream)
```

## A full round-trip

Because `encode()` produces valid `.xlsx` bytes, encoding and decoding round-trips
cleanly:

```kotlin
val excel = Excel.createExcel()
excel["Sheet1"].appendRow(listOf(TextCellValue("persisted"), IntCellValue(7)))

val bytes = excel.encode()!!             // encode in memory
val reloaded = Excel.decodeBytes(bytes)  // decode the same bytes

val a1 = reloaded["Sheet1"].cell(CellIndex.indexByString("A1")).value // persisted
val b1 = reloaded["Sheet1"].cell(CellIndex.indexByString("B1")).value // 7
```

## Notes

- `encode()` reflects the current in-memory model — call it after all your edits.
- Only the `.xlsx` (Office Open XML) format is supported. Legacy `.xls` files
  cannot be read or written.
- Encoding does no filesystem access, so it is safe to call on any thread and any
  platform.
