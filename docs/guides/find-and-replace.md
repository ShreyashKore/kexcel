# Find & Replace

`findAndReplace` replaces text across a sheet and returns the number of
replacements made. It only touches **text cells** (`TextCellValue`) — numbers,
dates and formulas are left alone.

## Basic usage

The `source` is a `Regex`; the `target` is the replacement string:

```kotlin
val count = excel.findAndReplace("Sheet1", Regex("Widget"), "Gadget")
println("Replaced $count occurrence(s)")
```

## Using regular expressions

Because `source` is a real `Regex`, you get the full power of Kotlin regexes:

```kotlin
// Case-insensitive:
excel.findAndReplace("Sheet1", Regex("widget", RegexOption.IGNORE_CASE), "Gadget")

// Capture groups in the replacement:
excel.findAndReplace("Sheet1", Regex("""(\d{4})-(\d{2})"""), "$2/$1")
```

## Limiting the scope

Optional parameters restrict how many matches are replaced and which
rows/columns are searched:

```kotlin
excel.findAndReplace(
    "Sheet1",
    Regex("Widget"),
    "Gadget",
    first = 2,            // only the first 2 matches (default -1 = all)
    startingRow = 1,      // inclusive, 0-based (default -1 = from the top)
    endingRow = 10,
    startingColumn = 0,
    endingColumn = 3,
)
```

| Parameter | Default | Meaning |
| --- | --- | --- |
| `first` | `-1` | replace at most this many matches; `-1` means all |
| `startingRow` / `endingRow` | `-1` | inclusive row bounds; `-1` disables the bound |
| `startingColumn` / `endingColumn` | `-1` | inclusive column bounds |

The same method is available on the `Sheet` object:

```kotlin
excel["Sheet1"].findAndReplace(Regex("Widget"), "Gadget")
```

## Example

```kotlin
val excel = Excel.createExcel()
val sheet = excel["Sheet1"]
sheet.appendRow(listOf(TextCellValue("Widget A"), TextCellValue("Widget B")))
sheet.appendRow(listOf(TextCellValue("Gizmo"), TextCellValue("Widget C")))

val replaced = excel.findAndReplace("Sheet1", Regex("Widget"), "Gadget")
println(replaced) // 3
println(sheet.row(0).map { it?.value?.toString() }) // [Gadget A, Gadget B]
```
