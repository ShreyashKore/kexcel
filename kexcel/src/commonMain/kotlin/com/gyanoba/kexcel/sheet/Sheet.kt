package com.gyanoba.kexcel.sheet

import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.moveReferences
import com.gyanoba.kexcel.utils.Axis
import com.gyanoba.kexcel.utils.StructuralEdit
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.utils.EXCEL_DEFAULT_COLUMN_WIDTH
import com.gyanoba.kexcel.utils.EXCEL_DEFAULT_ROW_HEIGHT
import com.gyanoba.kexcel.utils.Span
import com.gyanoba.kexcel.utils.isLocationChangeRequired
import kotlin.math.max


public class Sheet internal constructor(
    internal val excel: Excel,
    private var _sheet: String,
    sh: Map<Int, Map<Int, Data>>? = null,
    spanList: List<Span?>? = null,
    spannedItems: MutableList<String>? = null,
    maxRowsVal: Int? = null,
    maxColumnsVal: Int? = null,
    isRTLVal: Boolean? = null,
    columnWidthsVal: Map<Int, Double>? = null,
    rowHeightsVal: Map<Int, Double>? = null,
    columnAutoFitVal: Map<Int, Boolean>? = null,
    headerFooter: HeaderFooter? = null
) {
    private var _isRTL: Boolean = false
    private var _maxRows: Int = 0
    private var _maxColumns: Int = 0
    internal var _defaultColumnWidth: Double? = null
    internal var _defaultRowHeight: Double? = null
    internal var _columnWidths: MutableMap<Int, Double> = mutableMapOf()
    internal var _rowHeights: MutableMap<Int, Double> = mutableMapOf()
    internal var _columnAutoFit: MutableMap<Int, Boolean> = mutableMapOf()
    internal var _spannedItems: MutableList<String> = mutableListOf()
    internal var spanList: MutableList<Span?> = mutableListOf()
    internal var sheetData: MutableMap<Int, MutableMap<Int, Data>> = mutableMapOf()
    internal var _headerFooter: HeaderFooter? = null

    /**
     * The attributes each `<row>` was read with (§18.3.1.73), apart from `r`: `hidden`,
     * `outlineLevel`, `collapsed`, the row style `s` / `customFormat`, `ht`, … . The model
     * only knows row heights; the rest is written back as it was.
     */
    internal var rowAttributes: MutableMap<Int, Map<String, String>> = mutableMapOf()

    /** The `<col>` elements the sheet was read with (§18.3.1.13), ranges and all. */
    internal var columnSpecs: MutableList<ColumnSpec> = mutableListOf()

    /** Whether column widths were changed in code; until then `<cols>` is left untouched. */
    internal var columnsDirty: Boolean = false

    /**
     * The cells a merged region covers, other than its top-left one. Reading a merge keeps
     * only that anchor in [sheetData], which is what the API has always shown. But the file
     * has cells beneath a merge — Excel stores the region's borders and fill on them — so
     * they are kept here and written back for as long as the sheet's layout is unchanged.
     * Row index → column index → cell.
     */
    internal val mergedCells: MutableMap<Int, MutableMap<Int, Data>> = mutableMapOf()

    /** A `<col>` element: a zero-based, inclusive column range and its other attributes. */
    internal class ColumnSpec(val min: Int, val max: Int, val attributes: Map<String, String>)

    internal companion object {
        fun clone(excel: Excel, sheetName: String, oldSheetObject: Sheet): Sheet {
            return Sheet(
                excel,
                sheetName,
                sh = oldSheetObject.sheetData,
                spanList = oldSheetObject.spanList,
                spannedItems = oldSheetObject._spannedItems,
                maxRowsVal = oldSheetObject._maxRows,
                maxColumnsVal = oldSheetObject._maxColumns,
                columnWidthsVal = oldSheetObject._columnWidths,
                rowHeightsVal = oldSheetObject._rowHeights,
                columnAutoFitVal = oldSheetObject._columnAutoFit,
                isRTLVal = oldSheetObject._isRTL,
                headerFooter = oldSheetObject._headerFooter
            ).also { copy ->
                copy._defaultColumnWidth = oldSheetObject._defaultColumnWidth
                copy._defaultRowHeight = oldSheetObject._defaultRowHeight
                copy.rowAttributes = oldSheetObject.rowAttributes.toMutableMap()
                copy.columnSpecs = oldSheetObject.columnSpecs.toMutableList()
                // The copy is written to a worksheet part that has no <cols> yet.
                copy.columnsDirty = true
            }
        }
    }

    init {
        _headerFooter = headerFooter

        if (spanList != null) {
            this@Sheet.spanList = spanList.toMutableList()
            excel.addMergeChangeLookup(sheetName)
            // A copy starts in a worksheet part that has no <mergeCells>; without the flag
            // the save path does not write one, and the copy lost its merged regions.
            if (spanList.any { it != null }) excel.mergeChanges = true
        }
        if (spannedItems != null) {
            this@Sheet._spannedItems = spannedItems
        }
        if (maxColumnsVal != null) {
            _maxColumns = maxColumnsVal
        }
        if (maxRowsVal != null) {
            _maxRows = maxRowsVal
        }
        if (isRTLVal != null) {
            _isRTL = isRTLVal
            excel.addRtlChangeLookup(sheetName)
        }
        if (columnWidthsVal != null) {
            _columnWidths = columnWidthsVal.toMutableMap()
        }
        if (rowHeightsVal != null) {
            _rowHeights = rowHeightsVal.toMutableMap()
        }
        if (columnAutoFitVal != null) {
            _columnAutoFit = columnAutoFitVal.toMutableMap()
        }

        if (sh != null) {
            sheetData = mutableMapOf()
            val temp = sh.mapValues { it.value.toMutableMap() }
            temp.forEach { (key, value) ->
                if (sheetData[key] == null) {
                    sheetData[key] = mutableMapOf()
                }
                temp[key]!!.forEach { (key1, oldDataObject) ->
                    sheetData[key]!![key1] = Data.clone(this, oldDataObject)
                }
            }
        }
        countRowsAndColumns()
    }

    /**
     * Places a cell exactly as it was read from the file. Unlike [updateCell] this neither
     * redirects into a merged region nor swaps the number format for one that "fits" the
     * value: any format may sit on any value in a file (a text cell formatted `@`, a
     * duration in `[h]:mm:ss`), and changing it while loading altered workbooks that were
     * never edited.
     */
    internal fun putParsed(
        rowIndex: Int,
        columnIndex: Int,
        value: CellValue?,
        style: CellStyle?,
        styleIsImplicit: Boolean = false,
    ) {
        checkMaxColumn(columnIndex)
        checkMaxRow(rowIndex)
        val row = sheetData.getOrPut(rowIndex) { mutableMapOf() }
        val cell = row.getOrPut(columnIndex) { Data.newData(this, rowIndex, columnIndex) }
        cell._value = value
        cell._cellStyle = style
        cell.implicitStyle = style.takeIf { styleIsImplicit }
        if (style != null) excel.styleChanges = true
        if (_maxColumns < columnIndex + 1) _maxColumns = columnIndex + 1
        if (_maxRows < rowIndex + 1) _maxRows = rowIndex + 1
    }

    internal fun removeCell(rowIndex: Int, columnIndex: Int) {
        sheetData[rowIndex]?.remove(columnIndex)
        val rowIsEmptyAfterRemovalOfCell = sheetData[rowIndex]?.isEmpty() == true
        if (rowIsEmptyAfterRemovalOfCell) {
            sheetData.remove(rowIndex)
        }
    }

    public var isRTL: Boolean
        get() = _isRTL
        set(value) {
            _isRTL = value
            excel.addRtlChangeLookup(sheetName)
        }

    public fun cell(cellIndex: CellIndex): Data {
        checkMaxColumn(cellIndex.columnIndex)
        checkMaxRow(cellIndex.rowIndex)
        if (cellIndex.columnIndex < 0 || cellIndex.rowIndex < 0) {
            damagedExcel("Negative index does not exist.")
        }

        if (_maxRows < (cellIndex.rowIndex + 1)) {
            _maxRows = cellIndex.rowIndex + 1
        }

        if (_maxColumns < (cellIndex.columnIndex + 1)) {
            _maxColumns = cellIndex.columnIndex + 1
        }

        if (sheetData[cellIndex.rowIndex] != null) {
            if (sheetData[cellIndex.rowIndex]!![cellIndex.columnIndex] == null) {
                sheetData[cellIndex.rowIndex]!![cellIndex.columnIndex] = Data.newData(this, cellIndex.rowIndex, cellIndex.columnIndex)
            }
        } else {
            sheetData[cellIndex.rowIndex] = mutableMapOf(cellIndex.columnIndex to Data.newData(this, cellIndex.rowIndex, cellIndex.columnIndex))
        }

        return sheetData[cellIndex.rowIndex]!![cellIndex.columnIndex]!!
    }

    public val rows: List<List<Data?>>
        get() {
            val data = mutableListOf<List<Data?>>()

            if (sheetData.isEmpty()) {
                return data
            }

            if (_maxRows > 0 && maxColumns > 0) {
                data.addAll(List(_maxRows) { rowIndex ->
                    List(maxColumns) { columnIndex ->
                        sheetData[rowIndex]?.get(columnIndex)
                    }
                })
            }

            return data
        }

    public fun selectRangeWithString(range: String): List<List<Data?>?> {
        if (!range.contains(':')) {
            val start = CellIndex.indexByString(range)
            return selectRange(start)
        } else {
            val rangeVars = range.split(':')
            val start = CellIndex.indexByString(rangeVars[0])
            val end = CellIndex.indexByString(rangeVars[1])
            return selectRange(start, end = end)
        }
    }

    public fun selectRange(start: CellIndex, end: CellIndex? = null): List<List<Data?>?> {
        checkMaxColumn(start.columnIndex)
        checkMaxRow(start.rowIndex)
        end?.let {
            checkMaxColumn(it.columnIndex)
            checkMaxRow(it.rowIndex)
        }

        var startColumn = start.columnIndex
        var startRow = start.rowIndex
        var endColumn = end?.columnIndex
        var endRow = end?.rowIndex

        if (endColumn != null && endRow != null) {
            if (startRow > endRow) {
                startRow = end.rowIndex
                endRow = start.rowIndex
            }
            if (endColumn < startColumn) {
                endColumn = start.columnIndex
                startColumn = end.columnIndex
            }
        }

        val selectedRange = mutableListOf<List<Data?>?>()
        if (sheetData.isEmpty()) {
            return selectedRange
        }

        // maxRows/maxColumns are counts, so an unbounded range ends one before them.
        for (i in startRow..(endRow ?: (maxRows - 1))) {
            val mapData = sheetData[i]
            if (mapData != null) {
                val row = mutableListOf<Data?>()
                for (j in startColumn..(endColumn ?: (maxColumns - 1))) {
                    row.add(mapData[j])
                }
                selectedRange.add(row)
            } else {
                selectedRange.add(null)
            }
        }

        return selectedRange
    }

    public fun selectRangeValuesWithString(range: String): List<List<Any?>> {
        if (!range.contains(':')) {
            val start = CellIndex.indexByString(range)
            return selectRangeValues(start)
        } else {
            val rangeVars = range.split(':')
            val start = CellIndex.indexByString(rangeVars[0])
            val end = CellIndex.indexByString(rangeVars[1])
            return selectRangeValues(start, end = end)
        }
    }

    public fun selectRangeValues(start: CellIndex, end: CellIndex? = null): List<List<Any?>> {
        val list = if (end == null) selectRange(start) else selectRange(start, end = end)
        return list.map { e ->
            e?.map { it?.value } ?: emptyList()
        }
    }

    internal fun countRowsAndColumns() {
        var maximumColumnIndex = -1
        var maximumRowIndex = -1
        val sortedKeys = sheetData.keys.sorted()
        sortedKeys.forEach { rowKey ->
            if (sheetData[rowKey] != null && sheetData[rowKey]!!.isNotEmpty()) {
                val keys = sheetData[rowKey]!!.keys.sorted()
                if (keys.isNotEmpty() && keys.last() > maximumColumnIndex) {
                    maximumColumnIndex = keys.last()
                }
            }
        }

        if (sortedKeys.isNotEmpty()) {
            maximumRowIndex = sortedKeys.last()
        }

        _maxColumns = maximumColumnIndex + 1
        _maxRows = maximumRowIndex + 1
    }

    public fun removeColumn(columnIndex: Int) {
        checkMaxColumn(columnIndex)
        if (columnIndex < 0 || columnIndex >= maxColumns) {
            return
        }
        excel.valuesEdited = true
        mergedCells.clear()
        // The references are rebuilt below. Left in place, the old position of a moved
        // merge stayed in the list and a later merge() of that range was silently ignored.
        _spannedItems = mutableListOf()

        var updateSpanCell = false

        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue
            var startColumn = spanObj.columnSpanStart
            val startRow = spanObj.rowSpanStart
            var endColumn = spanObj.columnSpanEnd
            val endRow = spanObj.rowSpanEnd

            if (columnIndex <= endColumn) {
                if (columnIndex < startColumn) {
                    startColumn -= 1
                }
                endColumn -= 1
                if (columnIndex == (endColumn + 1) && columnIndex == (if (columnIndex < startColumn) startColumn + 1 else startColumn)) {
                    this@Sheet.spanList[i] = null
                } else {
                    val newSpanObj = Span(
                        rowSpanStart = startRow,
                        columnSpanStart = startColumn,
                        rowSpanEnd = endRow,
                        columnSpanEnd = endColumn
                    )
                    this@Sheet.spanList[i] = newSpanObj
                }
                updateSpanCell = true
                excel.mergeChanges = true
            }

            if (this@Sheet.spanList[i] != null) {
                val rc = getSpanCellId(startColumn, startRow, endColumn, endRow)
                if (!_spannedItems.contains(rc)) {
                    _spannedItems.add(rc)
                }
            }
        }
        cleanUpSpanMap()

        if (updateSpanCell) {
            excel.addMergeChangeLookup(sheetName)
        }

        val data = mutableMapOf<Int, MutableMap<Int, Data>>()
        if (columnIndex <= maxColumns - 1) {
            val sortedKeys = sheetData.keys.sorted()
            sortedKeys.forEach { rowKey ->
                val columnMap = mutableMapOf<Int, Data>()
                val sortedColumnKeys = sheetData[rowKey]!!.keys.sorted()
                sortedColumnKeys.forEach { columnKey ->
                    if (sheetData[rowKey] != null && sheetData[rowKey]!![columnKey] != null) {
                        if (columnKey < columnIndex) {
                            columnMap[columnKey] = sheetData[rowKey]!![columnKey]!!
                        }
                        if (columnKey == columnIndex) {
                            sheetData[rowKey]!!.remove(columnKey)
                        }
                        if (columnIndex < columnKey) {
                            columnMap[columnKey - 1] = sheetData[rowKey]!![columnKey]!!
                                .also { it._columnIndex = columnKey - 1 }
                            sheetData[rowKey]!!.remove(columnKey)
                        }
                    }
                }
                data[rowKey] = columnMap
            }
            sheetData = data
        }

        // The guard above already rejected out-of-range indices, so a column always went away.
        if (_maxColumns > 0) {
            _maxColumns -= 1
        }
        shiftColumnProperties(columnIndex, removed = true)
        excel.moveReferences(this, StructuralEdit(Axis.Column, columnIndex, removed = true))
    }

    public fun insertColumn(columnIndex: Int) {
        if (columnIndex < 0) {
            return
        }
        checkMaxColumn(columnIndex)
        // Inserting inside the used range shifts the last column right. Once that column is
        // XFD there is nowhere to shift it to, and it would be written as an invalid `XFE`.
        if (columnIndex < _maxColumns && _maxColumns >= 16384) {
            throw IllegalArgumentException(
                "Cannot insert a column: the sheet already uses the last column (XFD), which would be pushed past Max (16384)."
            )
        }
        excel.valuesEdited = true
        mergedCells.clear()

        var updateSpanCell = false

        _spannedItems = mutableListOf()
        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue
            var startColumn = spanObj.columnSpanStart
            val startRow = spanObj.rowSpanStart
            var endColumn = spanObj.columnSpanEnd
            val endRow = spanObj.rowSpanEnd

            if (columnIndex <= endColumn) {
                if (columnIndex <= startColumn) {
                    startColumn += 1
                }
                endColumn += 1
                val newSpanObj = Span(
                    rowSpanStart = startRow,
                    columnSpanStart = startColumn,
                    rowSpanEnd = endRow,
                    columnSpanEnd = endColumn
                )
                this@Sheet.spanList[i] = newSpanObj
                updateSpanCell = true
                excel.mergeChanges = true
            }
            val rc = getSpanCellId(startColumn, startRow, endColumn, endRow)
            if (!_spannedItems.contains(rc)) {
                _spannedItems.add(rc)
            }
        }

        if (updateSpanCell) {
            excel.addMergeChangeLookup(sheetName)
        }

        if (sheetData.isNotEmpty()) {
            val data = mutableMapOf<Int, MutableMap<Int, Data>>()
            val sortedKeys = sheetData.keys.sorted()
            if (columnIndex <= maxColumns - 1) {
                sortedKeys.forEach { rowKey ->
                    val columnMap = mutableMapOf<Int, Data>()

                    val sortedColumnKeys = sheetData[rowKey]!!.keys.sortedDescending()
                    sortedColumnKeys.forEach { columnKey ->
                        if (sheetData[rowKey] != null && sheetData[rowKey]!![columnKey] != null) {
                            if (columnKey < columnIndex) {
                                columnMap[columnKey] = sheetData[rowKey]!![columnKey]!!
                            }
                            if (columnIndex <= columnKey) {
                                columnMap[columnKey + 1] = sheetData[rowKey]!![columnKey]!!
                                    .also { it._columnIndex = columnKey + 1 }
                            }
                        }
                    }
                    columnMap[columnIndex] = Data.newData(this, rowKey, columnIndex)
                    data[rowKey] = columnMap
                }
                sheetData = data
            } else {
                sortedKeys.firstOrNull()?.let { firstRow ->
                    sheetData[firstRow]!![columnIndex] = Data.newData(this, firstRow, columnIndex)
                }
            }
        } else {
            sheetData = mutableMapOf()
            sheetData[0] = mutableMapOf(columnIndex to Data.newData(this, 0, columnIndex))
        }
        // Inserting inside the used range shifts every trailing column one to the right;
        // inserting past the end simply extends the sheet up to the new column.
        if (columnIndex <= _maxColumns - 1) {
            _maxColumns += 1
        } else {
            _maxColumns = columnIndex + 1
        }
        shiftColumnProperties(columnIndex, removed = false)
        excel.moveReferences(this, StructuralEdit(Axis.Column, columnIndex, removed = false))
    }

    /**
     * Moves what is keyed by column — widths, auto-fit flags and the `<col>` definitions —
     * along with the cells when a column is inserted at or removed from [columnIndex].
     * These used to stay where they were, so the width of column C became the width of
     * whatever ended up in column C.
     */
    private fun shiftColumnProperties(columnIndex: Int, removed: Boolean) {
        _columnWidths = shiftedKeys(_columnWidths, columnIndex, removed)
        _columnAutoFit = shiftedKeys(_columnAutoFit, columnIndex, removed)
        columnSpecs = columnSpecs.mapNotNull { spec ->
            if (removed) when {
                spec.max < columnIndex -> spec
                spec.min > columnIndex -> ColumnSpec(spec.min - 1, spec.max - 1, spec.attributes)
                spec.min == spec.max -> null
                else -> ColumnSpec(spec.min, spec.max - 1, spec.attributes)
            } else when {
                spec.max < columnIndex -> spec
                spec.min >= columnIndex -> ColumnSpec(spec.min + 1, minOf(spec.max + 1, 16383), spec.attributes)
                // The new column lands inside the range and takes its formatting, as in Excel.
                else -> ColumnSpec(spec.min, minOf(spec.max + 1, 16383), spec.attributes)
            }
        }.toMutableList()
        columnsDirty = true
    }

    /** [map] after an insertion at, or the removal of, key [index]. */
    private fun <V> shiftedKeys(map: Map<Int, V>, index: Int, removed: Boolean): MutableMap<Int, V> {
        val shifted = mutableMapOf<Int, V>()
        map.forEach { (key, value) ->
            when {
                key < index -> shifted[key] = value
                removed && key == index -> Unit
                removed -> shifted[key - 1] = value
                else -> shifted[key + 1] = value
            }
        }
        return shifted
    }

    public fun removeRow(rowIndex: Int) {
        if (rowIndex < 0 || rowIndex >= _maxRows) {
            return
        }
        checkMaxRow(rowIndex)
        excel.valuesEdited = true
        mergedCells.clear()
        _spannedItems = mutableListOf()

        var updateSpanCell = false

        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue
            val startColumn = spanObj.columnSpanStart
            var startRow = spanObj.rowSpanStart
            val endColumn = spanObj.columnSpanEnd
            var endRow = spanObj.rowSpanEnd

            if (rowIndex <= endRow) {
                if (rowIndex < startRow) {
                    startRow -= 1
                }
                endRow -= 1
                if (rowIndex == (endRow + 1) && rowIndex == (if (rowIndex < startRow) startRow + 1 else startRow)) {
                    this@Sheet.spanList[i] = null
                } else {
                    val newSpanObj = Span(
                        rowSpanStart = startRow,
                        columnSpanStart = startColumn,
                        rowSpanEnd = endRow,
                        columnSpanEnd = endColumn
                    )
                    this@Sheet.spanList[i] = newSpanObj
                }
                updateSpanCell = true
                excel.mergeChanges = true
            }
            if (this@Sheet.spanList[i] != null) {
                val rc = getSpanCellId(startColumn, startRow, endColumn, endRow)
                if (!_spannedItems.contains(rc)) {
                    _spannedItems.add(rc)
                }
            }
        }
        cleanUpSpanMap()

        if (updateSpanCell) {
            excel.addMergeChangeLookup(sheetName)
        }

        if (sheetData.isNotEmpty()) {
            val data = mutableMapOf<Int, MutableMap<Int, Data>>()
            if (rowIndex <= maxRows - 1) {
                val sortedKeys = sheetData.keys.sorted()
                sortedKeys.forEach { rowKey ->
                    if (rowKey < rowIndex && sheetData[rowKey] != null) {
                        data[rowKey] = sheetData[rowKey]!!.toMutableMap()
                    }
                    if (rowIndex < rowKey && sheetData[rowKey] != null) {
                        // The cell objects move up with their row. Left with the old index,
                        // `data.value = …` on one of them wrote into the row below.
                        data[rowKey - 1] = sheetData[rowKey]!!.toMutableMap()
                            .onEach { it.value._rowIndex = rowKey - 1 }
                    }
                }
                sheetData = data
            }
        } else {
            _maxRows = 0
            _maxColumns = 0
        }

        // The guard above already rejected out-of-range indices, so a row always went away.
        if (_maxRows > 0) {
            _maxRows -= 1
        }
        _rowHeights = shiftedKeys(_rowHeights, rowIndex, removed = true)
        rowAttributes = shiftedKeys(rowAttributes, rowIndex, removed = true)
        excel.moveReferences(this, StructuralEdit(Axis.Row, rowIndex, removed = true))
    }

    public fun insertRow(rowIndex: Int) {
        if (rowIndex < 0) {
            return
        }

        checkMaxRow(rowIndex)
        // See [insertColumn]: the last row cannot be shifted past row 1048576.
        if (rowIndex < _maxRows && _maxRows >= 1048576) {
            throw IllegalArgumentException(
                "Cannot insert a row: the sheet already uses the last row, which would be pushed past Max (1048576)."
            )
        }
        excel.valuesEdited = true
        mergedCells.clear()

        var updateSpanCell = false

        _spannedItems = mutableListOf()
        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue
            val startColumn = spanObj.columnSpanStart
            var startRow = spanObj.rowSpanStart
            val endColumn = spanObj.columnSpanEnd
            var endRow = spanObj.rowSpanEnd

            if (rowIndex <= endRow) {
                if (rowIndex <= startRow) {
                    startRow += 1
                }
                endRow += 1
                val newSpanObj = Span(
                    rowSpanStart = startRow,
                    columnSpanStart = startColumn,
                    rowSpanEnd = endRow,
                    columnSpanEnd = endColumn
                )
                this@Sheet.spanList[i] = newSpanObj
                updateSpanCell = true
                excel.mergeChanges = true
            }
            val rc = getSpanCellId(startColumn, startRow, endColumn, endRow)
            if (!_spannedItems.contains(rc)) {
                _spannedItems.add(rc)
            }
        }

        if (updateSpanCell) {
            excel.addMergeChangeLookup( sheetName)
        }

        val data = mutableMapOf<Int, MutableMap<Int, Data>>()
        if (sheetData.isNotEmpty()) {
            val sortedKeys = sheetData.keys.sortedDescending()
            if (rowIndex <= maxRows - 1) {
                sortedKeys.forEach { rowKey ->
                    if (rowKey < rowIndex) {
                        data[rowKey] = sheetData[rowKey]!!
                    }
                    if (rowIndex <= rowKey) {
                        data[rowKey + 1] = sheetData[rowKey]!!
                        data[rowKey + 1]!!.forEach { (_, value) ->
                            value._rowIndex++
                        }
                    }
                }
            } else {
                // Inserting past the last used row shifts nothing, but `data` still
                // replaces sheetData below — so carry the existing rows over.
                data.putAll(sheetData)
            }
        }
        data[rowIndex] = mutableMapOf(0 to Data.newData(this, rowIndex, 0))
        sheetData = data

        if (_maxRows - 1 <= rowIndex) {
            _maxRows = rowIndex + 1
        } else {
            _maxRows += 1
        }
        _rowHeights = shiftedKeys(_rowHeights, rowIndex, removed = false)
        rowAttributes = shiftedKeys(rowAttributes, rowIndex, removed = false)
        excel.moveReferences(this, StructuralEdit(Axis.Row, rowIndex, removed = false))
    }

    public fun updateCell(cellIndex: CellIndex, value: CellValue?, cellStyle: CellStyle? = null) {
        val columnIndex = cellIndex.columnIndex
        val rowIndex = cellIndex.rowIndex
        if (columnIndex < 0 || rowIndex < 0) {
            return
        }
        checkMaxColumn(columnIndex)
        checkMaxRow(rowIndex)

        var newRowIndex = rowIndex
        var newColumnIndex = columnIndex

        if (this@Sheet.spanList.isNotEmpty()) {
            val (nr, nc) = isInsideSpanning(rowIndex, columnIndex)
            newRowIndex = nr
            newColumnIndex = nc
        }

        // Read before [putData], which resets the style to the bare default for the new
        // value. Reading it afterwards — as this used to — always saw that default, so
        // changing a value discarded the cell's font, fill, border and number format.
        val cellBefore = sheetData[newRowIndex]?.get(newColumnIndex)
        val styleBefore = cellBefore?.cellStyle
            // A style [putData] assigned on its own carries no intent; the new value gets
            // its own default (an Int's "0" must not stick to a Double written later).
            ?.takeUnless { it == CellStyle(numberFormat = NumFormat.defaultFor(cellBefore.value)) }

        putData(newRowIndex, newColumnIndex, value)

        var cellStyleToUse = cellStyle
        if (cellStyle != null) {
            val numberFormat = cellStyle.numberFormat
            if (!numberFormat.accepts(value)) {
                cellStyleToUse = cellStyle.copyWith(numberFormat = NumFormat.defaultFor(value))
            }
        } else if (styleBefore != null) {
            // The font, fill, border and alignment stay. So does a specific number format
            // that can show the new value; `General` gives way to the default for the
            // value's type, as it always has (a Double written over a General cell gets
            // "0.00"), and clearing a cell clears its number format with it.
            val format = styleBefore.numberFormat
            cellStyleToUse = if (value != null && format != NumFormat.standard_0 && format.accepts(value)) styleBefore
            else NumFormat.defaultFor(value).let { default ->
                if (default == format) styleBefore else styleBefore.copyWith(numberFormat = default)
            }
        }

        if (cellStyleToUse != null) {
            sheetData[newRowIndex]!![newColumnIndex]!!._cellStyle = cellStyleToUse
            excel.styleChanges = true
        }
    }

    public fun merge(start: CellIndex, end: CellIndex, customValue: CellValue? = null) {
        var startColumn = start.columnIndex
        var startRow = start.rowIndex
        var endColumn = end.columnIndex
        var endRow = end.rowIndex

        checkMaxColumn(startColumn)
        checkMaxColumn(endColumn)
        checkMaxRow(startRow)
        checkMaxRow(endRow)

        if ((startColumn == endColumn && startRow == endRow) || (startColumn < 0 || startRow < 0 || endColumn < 0 || endRow < 0) || (_spannedItems.contains(getSpanCellId(startColumn, startRow, endColumn, endRow)))) {
            return
        }

        val gotPosition = getSpanPosition(start, end)

        excel.mergeChanges = true
        excel.valuesEdited = true
        mergedCells.clear()

        startColumn = gotPosition[0]
        startRow = gotPosition[1]
        endColumn = gotPosition[2]
        endRow = gotPosition[3]

        _maxColumns = max(_maxColumns, endColumn + 1)
        _maxRows = max(_maxRows, endRow + 1)

        var getValue = true

        var value = Data.newData(this, startRow, startColumn)
        if (customValue != null) {
            value._value = customValue
            getValue = false
        }

        for (j in startRow..endRow) {
            for (k in startColumn..endColumn) {
                if (sheetData[j] != null) {
                    if (getValue && sheetData[j]!![k]?.value != null) {
                        value = sheetData[j]!![k]!!
                        getValue = false
                    }
                    sheetData[j]!!.remove(k)
                }
            }
        }

        if (sheetData[startRow] != null) {
            sheetData[startRow]!![startColumn] = value
        } else {
            sheetData[startRow] = mutableMapOf(startColumn to value)
        }

        val sp = getSpanCellId(startColumn, startRow, endColumn, endRow)

        if (!_spannedItems.contains(sp)) {
            _spannedItems.add(sp)
        }

        val s = Span(
            rowSpanStart = startRow,
            columnSpanStart = startColumn,
            rowSpanEnd = endRow,
            columnSpanEnd = endColumn
        )

        this@Sheet.spanList.add(s)
        excel.addMergeChangeLookup(sheetName)
    }

    public fun unMerge(unmergeCells: String) {
        if (_spannedItems.isNotEmpty() && this@Sheet.spanList.isNotEmpty() && _spannedItems.contains(unmergeCells)) {
            val lis = unmergeCells.split(Regex(":"))
            if (lis.size == 2) {
                var remove = false
                val start = CellIndex.indexByString(lis[0])
                val end = CellIndex.indexByString(lis[1])
                for (i in 0 until this@Sheet.spanList.size) {
                    val spanObject = this@Sheet.spanList[i] ?: continue

                    if (spanObject.columnSpanStart == start.columnIndex && spanObject.rowSpanStart == start.rowIndex && spanObject.columnSpanEnd == end.columnIndex && spanObject.rowSpanEnd == end.rowIndex) {
                        this@Sheet.spanList[i] = null
                        remove = true
                    }
                }
                if (remove) {
                    cleanUpSpanMap()
                }
            }
            _spannedItems.remove(unmergeCells)
            mergedCells.clear()
            // Like merge(), the save path only rewrites <mergeCells> when this flag is set.
            excel.mergeChanges = true
            excel.addMergeChangeLookup(sheetName)
        }
    }

    public fun setMergedCellStyle(start: CellIndex, mergedCellStyle: CellStyle) {
        val mergedCells = this@Sheet.spannedItems.map { e ->
            e.split(":").map { CellIndex.indexByString(it) }
        }

        val startIndices = mergedCells.map { it[0] }
        val endIndices = mergedCells.map { it[1] }

        if (mergedCells.isEmpty() || start.columnIndex < 0 || start.rowIndex < 0 || !startIndices.contains(start)) {
            return
        }

        val end = endIndices[startIndices.indexOf(start)]

        val hasBorder = mergedCellStyle.topBorder != Border() || mergedCellStyle.bottomBorder != Border() || mergedCellStyle.leftBorder != Border() || mergedCellStyle.rightBorder != Border() || mergedCellStyle.diagonalBorderUp || mergedCellStyle.diagonalBorderDown
        if (hasBorder) {
            for (i in start.rowIndex..end.rowIndex) {
                for (j in start.columnIndex..end.columnIndex) {
                    var cellStyle = mergedCellStyle.copyWith(
                        topBorderVal = Border(),
                        bottomBorderVal = Border(),
                        leftBorderVal = Border(),
                        rightBorderVal = Border(),
                        diagonalBorderUpVal = false,
                        diagonalBorderDownVal = false
                    )

                    if (i == start.rowIndex) {
                        cellStyle = cellStyle.copyWith(topBorderVal = mergedCellStyle.topBorder)
                    }
                    if (i == end.rowIndex) {
                        cellStyle = cellStyle.copyWith(bottomBorderVal = mergedCellStyle.bottomBorder)
                    }
                    if (j == start.columnIndex) {
                        cellStyle = cellStyle.copyWith(leftBorderVal = mergedCellStyle.leftBorder)
                    }
                    if (j == end.columnIndex) {
                        cellStyle = cellStyle.copyWith(rightBorderVal = mergedCellStyle.rightBorder)
                    }

                    if (i == j || start.rowIndex == end.rowIndex || start.columnIndex == end.columnIndex) {
                        cellStyle = cellStyle.copyWith(
                            diagonalBorderUpVal = mergedCellStyle.diagonalBorderUp,
                            diagonalBorderDownVal = mergedCellStyle.diagonalBorderDown
                        )
                    }

                    if (i == start.rowIndex && j == start.columnIndex) {
                        cell(start).cellStyle = cellStyle
                    } else {
                        putData(i, j, null)
                        sheetData[i]!![j]!!.cellStyle = cellStyle
                    }
                }
            }
        }
    }

    internal fun getSpanPosition(start: CellIndex, end: CellIndex): List<Int> {
        var startColumn = start.columnIndex
        var startRow = start.rowIndex
        var endColumn = end.columnIndex
        var endRow = end.rowIndex

        var remove = false

        if (startRow > endRow) {
            startRow = end.rowIndex
            endRow = start.rowIndex
        }
        if (endColumn < startColumn) {
            endColumn = start.columnIndex
            startColumn = end.columnIndex
        }

        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue

            val locationChange = isLocationChangeRequired(startColumn, startRow, endColumn, endRow, spanObj)

            if (locationChange.changed) {
                startColumn = locationChange.bounds.startColumn
                startRow = locationChange.bounds.startRow
                endColumn = locationChange.bounds.endColumn
                endRow = locationChange.bounds.endRow
                val sp = getSpanCellId(spanObj.columnSpanStart, spanObj.rowSpanStart, spanObj.columnSpanEnd, spanObj.rowSpanEnd)
                if (_spannedItems.contains(sp)) {
                    _spannedItems.remove(sp)
                }
                remove = true
                this@Sheet.spanList[i] = null
            }
        }
        if (remove) {
            cleanUpSpanMap()
        }

        return listOf(startColumn, startRow, endColumn, endRow)
    }

    public fun appendRow(row: List<CellValue?>) {
        val targetRow = maxRows
        insertRowIterables(row, targetRow)
    }

    internal fun getSpannedObjects(rowIndex: Int, startingColumnIndex: Int): List<Span> {
        val obtained = mutableListOf<Span>()

        if (this@Sheet.spanList.isNotEmpty()) {
            obtained.addAll(this@Sheet.spanList.filterNotNull().filter { spanObject ->
                spanObject.rowSpanStart <= rowIndex && rowIndex <= spanObject.rowSpanEnd && startingColumnIndex <= spanObject.columnSpanEnd
            })
        }
        return obtained
    }

    internal fun isInsideSpanObject(spanObjectList: List<Span>, columnIndex: Int, rowIndex: Int): Boolean {
        for (spanObject in spanObjectList) {
            if (spanObject.columnSpanStart <= columnIndex && columnIndex <= spanObject.columnSpanEnd && spanObject.rowSpanStart <= rowIndex && rowIndex <= spanObject.rowSpanEnd) {
                if (columnIndex < spanObject.columnSpanEnd) {
                    return false
                } else if (columnIndex == spanObject.columnSpanEnd) {
                    return true
                }
            }
        }
        return true
    }

    public fun insertRowIterables(row: List<CellValue?>, rowIndex: Int, startingColumn: Int = 0, overwriteMergedCells: Boolean = true) {
        if (row.isEmpty() || rowIndex < 0) {
            return
        }

        checkMaxRow(rowIndex)
        var columnIndex = 0
        if (startingColumn > 0) {
            columnIndex = startingColumn
        }
        // The last cell written, not one past it: a row that ends exactly at XFD is fine.
        checkMaxColumn(columnIndex + row.size - 1)
        val rowsLength = _maxRows
        val maxIterationIndex = row.size - 1
        var currentRowPosition = 0

        if (overwriteMergedCells || rowIndex >= rowsLength) {
            while (currentRowPosition <= maxIterationIndex) {
                putData(rowIndex, columnIndex++, row[currentRowPosition++])
            }
        } else {
            selfCorrectSpanMap(excel)
            val spanObjectsList = getSpannedObjects(rowIndex, columnIndex)

            if (spanObjectsList.isEmpty()) {
                while (currentRowPosition <= maxIterationIndex) {
                    putData(rowIndex, columnIndex++, row[currentRowPosition++])
                }
            } else {
                while (currentRowPosition <= maxIterationIndex) {
                    if (isInsideSpanObject(spanObjectsList, columnIndex, rowIndex)) {
                        putData(rowIndex, columnIndex, row[currentRowPosition++])
                    }
                    columnIndex++
                }
            }
        }
    }

    internal fun putData(rowIndex: Int, columnIndex: Int, value: CellValue?) {
        var row = sheetData[rowIndex]
        if (row == null) {
            row = mutableMapOf()
            sheetData[rowIndex] = row
        }
        var cell = row[columnIndex]
        if (cell == null) {
            cell = Data.newData(this, rowIndex, columnIndex)
            row[columnIndex] = cell
        }

        excel.valuesEdited = true
        cell._value = value
        cell._cellStyle = CellStyle(numberFormat = NumFormat.defaultFor(value))
        if (cell._cellStyle != null && cell._cellStyle!!.numberFormat != NumFormat.standard_0) {
            excel.styleChanges = true
        }

        if ((_maxColumns - 1) < columnIndex) {
            _maxColumns = columnIndex + 1
        }

        if ((_maxRows - 1) < rowIndex) {
            _maxRows = rowIndex + 1
        }
    }

    public val defaultRowHeight: Double?
        get() = _defaultRowHeight

    public val defaultColumnWidth: Double?
        get() = _defaultColumnWidth

    public val getColumnAutoFits: Map<Int, Boolean>
        get() = _columnAutoFit

    public val getColumnWidths: Map<Int, Double>
        get() = _columnWidths

    public val getRowHeights: Map<Int, Double>
        get() = _rowHeights

    public fun getColumnAutoFit(columnIndex: Int): Boolean {
        return _columnAutoFit[columnIndex] ?: false
    }

    public fun getColumnWidth(columnIndex: Int): Double {
        return _columnWidths[columnIndex] ?: _defaultColumnWidth!!
    }

    public fun getRowHeight(rowIndex: Int): Double {
        return _rowHeights[rowIndex] ?: _defaultRowHeight!!
    }

    public fun setDefaultColumnWidth(columnWidth: Double = EXCEL_DEFAULT_COLUMN_WIDTH) {
        if (columnWidth < 0) return
        _defaultColumnWidth = columnWidth
    }

    public fun setDefaultRowHeight(rowHeight: Double = EXCEL_DEFAULT_ROW_HEIGHT) {
        if (rowHeight < 0) return
        _defaultRowHeight = rowHeight
    }

    public fun setColumnAutoFit(columnIndex: Int) {
        checkMaxColumn(columnIndex)
        if (columnIndex < 0) return
        columnsDirty = true
        _columnAutoFit[columnIndex] = true
    }

    public fun setColumnWidth(columnIndex: Int, columnWidth: Double) {
        checkMaxColumn(columnIndex)
        if (columnWidth < 0) return
        columnsDirty = true
        _columnWidths[columnIndex] = columnWidth
    }

    public fun setRowHeight(rowIndex: Int, rowHeight: Double) {
        checkMaxRow(rowIndex)
        if (rowHeight < 0) return
        _rowHeights[rowIndex] = rowHeight
    }

    internal fun addSpannedItem(ref: String) {
        _spannedItems.add(ref)
    }

    public fun findAndReplace(source: Regex, target: String, first: Int = -1, startingRow: Int = -1, endingRow: Int = -1, startingColumn: Int = -1, endingColumn: Int = -1): Int {
        var replaceCount = 0
        var _startingRow = 0
        var _endingRow = -1
        var _startingColumn = 0
        var _endingColumn = -1

        if (startingRow != -1 && endingRow != -1) {
            if (startingRow > endingRow) {
                _endingRow = startingRow
                _startingRow = endingRow
            } else {
                _endingRow = endingRow
                _startingRow = startingRow
            }
        }

        if (startingColumn != -1 && endingColumn != -1) {
            if (startingColumn > endingColumn) {
                _endingColumn = startingColumn
                _startingColumn = endingColumn
            } else {
                _endingColumn = endingColumn
                _startingColumn = startingColumn
            }
        }

        val rowsLength = maxRows
        val columnLength = maxColumns

        for (i in _startingRow until rowsLength) {
            if (_endingRow != -1 && i > _endingRow) {
                break
            }
            for (j in _startingColumn until columnLength) {
                if (_endingColumn != -1 && j > _endingColumn) {
                    break
                }
                val sourceData = sheetData[i]?.get(j)?.value
                if (sourceData !is TextCellValue) {
                    continue
                }
                val original = sourceData.value.toString()
                val result = source.replace(original) { match ->
                    if (first == -1 || first != replaceCount) {
                        replaceCount++
                        // The lambda returns the replacement for this match only, so the
                        // surrounding text must not be spliced back in here.
                        target
                    } else {
                        match.value
                    }
                }
                // Rewriting is only needed when the text changed. A rewrite stores plain
                // text, so doing it unconditionally stripped the rich-text runs of every
                // cell in the range, matched or not.
                if (result != original) {
                    sheetData[i]!![j]!!.value = TextCellValue(result)
                }
            }
        }

        return replaceCount
    }

    public fun clearRow(rowIndex: Int): Boolean {
        if (rowIndex < 0) {
            return false
        }

        var isNotInside = true

        if (sheetData[rowIndex] != null && sheetData[rowIndex]!!.isNotEmpty()) {
            for (i in 0 until this@Sheet.spanList.size) {
                val spanObj = this@Sheet.spanList[i] ?: continue
                if (rowIndex >= spanObj.rowSpanStart && rowIndex <= spanObj.rowSpanEnd) {
                    isNotInside = false
                    break
                }
            }

            if (isNotInside) {
                excel.valuesEdited = true
                sheetData[rowIndex]!!.keys.toList().forEach { key ->
                    sheetData[rowIndex]!![key] = Data.newData(this, rowIndex, key)
                }
            }
        }
        return isNotInside
    }

    internal fun isInsideSpanning(rowIndex: Int, columnIndex: Int): Pair<Int, Int> {
        var newRowIndex = rowIndex
        var newColumnIndex = columnIndex

        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue

            if (rowIndex >= spanObj.rowSpanStart && rowIndex <= spanObj.rowSpanEnd && columnIndex >= spanObj.columnSpanStart && columnIndex <= spanObj.columnSpanEnd) {
                newRowIndex = spanObj.rowSpanStart
                newColumnIndex = spanObj.columnSpanStart
                break
            }
        }

        return Pair(newRowIndex, newColumnIndex)
    }

    internal fun checkMaxColumn(columnIndex: Int) {
        // Only the index is checked. `_maxColumns` is a *count*, so testing it against the
        // same limit would reject the last legal column (XFD, index 16383) as soon as it had
        // been written — writing it bumped the count to 16384, and every later read threw.
        if (columnIndex >= 16384) {
            throw IllegalArgumentException("Reached Max (16384) or (XFD) columns value.")
        }
        if (columnIndex < 0) {
            throw IllegalArgumentException("Negative columnIndex found: $columnIndex")
        }
    }

    internal fun checkMaxRow(rowIndex: Int) {
        // See [checkMaxColumn]: `_maxRows` is a count, not an index.
        if (rowIndex >= 1048576) {
            throw IllegalArgumentException("Reached Max (1048576) rows value.")
        }
        if (rowIndex < 0) {
            throw IllegalArgumentException("Negative rowIndex found: $rowIndex")
        }
    }

    public val spannedItems: List<String>
        get() {
            val references = LinkedHashSet<String>()
            for (spanObj in this@Sheet.spanList) {
                spanObj ?: continue
                references.add(getSpanCellId(spanObj.columnSpanStart, spanObj.rowSpanStart, spanObj.columnSpanEnd, spanObj.rowSpanEnd))
            }
            _spannedItems = references.toMutableList()
            return _spannedItems
        }

    internal fun cleanUpSpanMap() {
        if (this@Sheet.spanList.isNotEmpty()) {
            this@Sheet.spanList.removeAll { it == null }
        }
    }

    public val sheetName: String
        get() = _sheet

    internal fun renameTo(name: String) {
        _sheet = name
    }

    public fun row(rowIndex: Int): List<Data?> {
        if (rowIndex < 0) {
            return emptyList()
        }
        if (rowIndex < _maxRows) {
            return if (sheetData[rowIndex] != null) {
                List(_maxColumns) { columnIndex ->
                    sheetData[rowIndex]!![columnIndex]
                }
            } else {
                List(_maxColumns) { null }
            }
        }
        return emptyList()
    }

    public val maxRows: Int
        get() = _maxRows

    public val maxColumns: Int
        get() = _maxColumns

    public var headerFooter: HeaderFooter?
        get() = _headerFooter
        set(value) {
            _headerFooter = value
        }
}

// Helper functions that are not defined in the provided code, assuming they exist or need to be implemented
internal fun getSpanCellId(startColumn: Int, startRow: Int, endColumn: Int, endRow: Int): String {
    // Implement based on CellIndex
    return "${CellIndex.indexByColumnRow(startColumn, startRow)}:${CellIndex.indexByColumnRow(endColumn, endRow)}"
}

internal fun damagedExcel(text: String) {
    throw IllegalArgumentException(text)
}

internal fun selfCorrectSpanMap(excel: Excel) {
    excel.mergeChangeLook.forEach { key: String ->
        if (excel.sheetMap[key] != null &&
            excel.sheetMap[key]!!.spanList.isNotEmpty()
        ) {
            val spanList = excel.sheetMap[key]!!.spanList.toMutableList()

            for (i in 0..<spanList.size) {
                val checkerPos = spanList[i] ?: continue
                var startRow = checkerPos.rowSpanStart
                var startColumn = checkerPos.columnSpanStart
                var endRow = checkerPos.rowSpanEnd
                var endColumn = checkerPos.columnSpanEnd

                for (j in (i + 1)..<spanList.size) {
                    val spanObj = spanList[j] ?: continue

                    val locationChange = isLocationChangeRequired(
                        startColumn, startRow, endColumn, endRow, spanObj
                    )
                    if (locationChange.changed) {
                        startColumn = locationChange.bounds.startColumn
                        startRow = locationChange.bounds.startRow
                        endColumn = locationChange.bounds.endColumn
                        endRow = locationChange.bounds.endRow
                        spanList[j] = null
                    } else {
                        val locationChange2 = isLocationChangeRequired(
                            spanObj.columnSpanStart,
                            spanObj.rowSpanStart,
                            spanObj.columnSpanEnd,
                            spanObj.rowSpanEnd,
                            checkerPos
                        )

                        if (locationChange2.changed) {
                            startColumn = locationChange2.bounds.startColumn
                            startRow = locationChange2.bounds.startRow
                            endColumn = locationChange2.bounds.endColumn
                            endRow = locationChange2.bounds.endRow
                            spanList[j] = null
                        }
                    }
                }
                val spanObj1 = Span(
                    rowSpanStart = startRow,
                    columnSpanStart = startColumn,
                    rowSpanEnd = endRow,
                    columnSpanEnd = endColumn,
                )
                spanList[i] = spanObj1
            }
            excel.sheetMap[key]!!.spanList = spanList.toMutableList()
            excel.sheetMap[key]!!.cleanUpSpanMap()
        }
    }
}
