# Formulas

Kexcel stores formulas as strings; it does **not** evaluate them. The spreadsheet
application that opens the file (Excel, Numbers, LibreOffice, Google Sheets…)
computes the result.

## Writing a formula

There are two equivalent ways:

```kotlin
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.FormulaCellValue

// 1. As a FormulaCellValue
sheet.updateCell(CellIndex.indexByString("A4"), FormulaCellValue("=SUM(A1:A3)"))

// 2. On the Data object directly
sheet.cell(CellIndex.indexByString("A5")).setFormula("=AVERAGE(A1:A3)")
```

## A worked example

```kotlin
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.sheet.*

val excel = Excel.createExcel()
val sheet = excel["Sheet1"]

sheet.updateCell(CellIndex.indexByString("A1"), IntCellValue(10))
sheet.updateCell(CellIndex.indexByString("A2"), IntCellValue(20))
sheet.updateCell(CellIndex.indexByString("A3"), IntCellValue(30))

sheet.updateCell(CellIndex.indexByString("A4"), FormulaCellValue("=SUM(A1:A3)"))
sheet.cell(CellIndex.indexByString("A5")).setFormula("=AVERAGE(A1:A3)")

// The value read back is the formula string, not its result:
println(sheet.cell(CellIndex.indexByString("A4")).value) // =SUM(A1:A3)
```

!!! warning "Formulas are stored, not computed"
    `sheet.cell(...).value` for a formula cell returns a `FormulaCellValue` — i.e.
    the formula text. There is no cached numeric result until a spreadsheet app
    opens the file and recalculates.

## Tips

- Include the leading `=`, exactly as Excel expects (`"=SUM(A1:A3)"`).
- Use A1-style references. Kexcel does not rewrite references when you insert or
  remove rows/columns, so keep that in mind if you [reshape the sheet](rows-and-columns.md)
  after adding formulas.
- Reading a formula cell:

    ```kotlin
    val v = sheet.cell(CellIndex.indexByString("A4")).value
    if (v is FormulaCellValue) println(v.formula)
    ```
