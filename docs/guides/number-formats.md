# Number Formats

A **number format** controls how a value is *displayed* — it never changes the
stored value. `1234.5` can be shown as `1234.50`, `1,234.5`, `123450%` or a
currency string depending on its format.

Number formats live on `CellStyle.numberFormat` and come from the `NumFormat`
type.

## Applying a format

```kotlin
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.DoubleCellValue

sheet.updateCell(
    CellIndex.indexByString("A1"),
    DoubleCellValue(1234.5),
    cellStyle = CellStyle(numberFormat = NumFormat.standard_2), // shows "1234.50"
)
```

## Built-in `standard_*` formats

`NumFormat` exposes the standard OOXML built-in formats as constants. The most
useful ones:

| Constant | Format code | Example |
| --- | --- | --- |
| `standard_0` | `General` | `1234.5` |
| `standard_1` | `0` | `1235` |
| `standard_2` | `0.00` | `1234.50` |
| `standard_3` | `#,##0` | `1,235` |
| `standard_4` | `#,##0.00` | `1,234.50` |
| `standard_9` | `0%` | `123450%` |
| `standard_10` | `0.00%` | `123450.00%` |
| `standard_11` | `0.00E+00` | `1.23E+03` |
| `standard_14` | `mm-dd-yy` | date |
| `standard_15` | `d-mmm-yy` | date |
| `standard_20` | `h:mm` | time |
| `standard_22` | `m/d/yy h:mm` | date-time |
| `standard_49` | `@` | text |

(There are more — `standard_12`, `13`, `16`–`19`, `21`, `37`–`40`, `45`–`48` —
covering fractions, currencies with parentheses/red negatives, and elapsed-time
formats.)

Read a format's code with `.formatCode`:

```kotlin
println(NumFormat.standard_2.formatCode) // "0.00"
```

## Sensible defaults per value type

`NumFormat` also names the default applied to each value type. You rarely call
these directly — Kexcel applies them automatically when you write a value — but
they are useful reference points:

| Helper | Points at |
| --- | --- |
| `NumFormat.defaultNumeric` | `standard_1` (`0`) |
| `NumFormat.defaultFloat` | `standard_2` (`0.00`) |
| `NumFormat.defaultBool` | `standard_0` (`General`) |
| `NumFormat.defaultDate` | `standard_14` (`mm-dd-yy`) |
| `NumFormat.defaultTime` | `standard_20` (`h:mm`) |
| `NumFormat.defaultDateTime` | `standard_22` (`m/d/yy h:mm`) |

!!! info "Formats must match the value"
    When you set a `CellStyle` whose `numberFormat` doesn't fit the value you're
    writing (say, a date format on a boolean), Kexcel swaps in the correct default
    for that value automatically, so the file stays valid.

## Custom format codes

For anything the built-ins don't cover, build a custom format from a format-code
string:

```kotlin
import com.gyanoba.kexcel.number_format.CustomNumericNumFormat

// Directly:
CellStyle(numberFormat = CustomNumericNumFormat("0.00%"))

// ...or via the factory, which picks numeric vs. date/time for you:
CellStyle(numberFormat = NumFormat.custom("\$#,##0.00"))   // currency
CellStyle(numberFormat = NumFormat.custom("yyyy-mm-dd"))    // date
```

`NumFormat.custom(code)` inspects the code and returns a `CustomDateTimeNumFormat`
for date/time-looking codes, or a `CustomNumericNumFormat` otherwise.

Some handy custom codes:

| Goal | Format code |
| --- | --- |
| Currency | `$#,##0.00` |
| Thousands, 1 decimal | `#,##0.0` |
| Percent, no decimals | `0%` |
| ISO date | `yyyy-mm-dd` |
| 24-hour time | `hh:mm:ss` |
| Scientific | `0.00E+00` |
