package dev.shreyash.kxcel.sheet

import dev.shreyash.kxcel.Excel
import dev.shreyash.kxcel.number_format.NumFormat
import dev.shreyash.kxcel.utils.Span
import kotlin.math.max


public class Sheet internal constructor(
    internal val excel: Excel,
    private val _sheet: String,
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

    internal companion object {
        const val excelDefaultColumnWidth = 8.43
        const val excelDefaultRowHeight = 15.0

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
            )
        }
    }

    init {
        _headerFooter = headerFooter

        if (spanList != null) {
            this@Sheet.spanList = spanList.toMutableList()
            excel.addMergeChangeLookup(sheetName)
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
        val _selectedRange = mutableListOf<List<Data?>?>()
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

        val _selectedRange = mutableListOf<List<Data?>?>()
        if (sheetData.isEmpty()) {
            return _selectedRange
        }

        for (i in startRow..(endRow ?: maxRows)) {
            val mapData = sheetData[i]
            if (mapData != null) {
                val row = mutableListOf<Data?>()
                for (j in startColumn..(endColumn ?: maxColumns)) {
                    row.add(mapData[j])
                }
                _selectedRange.add(row)
            } else {
                _selectedRange.add(null)
            }
        }

        return _selectedRange
    }

    public fun selectRangeValuesWithString(range: String): List<List<Any?>> {
        val _selectedRange = mutableListOf<List<Any?>>()
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

        var updateSpanCell = false

        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue
            var startColumn = spanObj.columnSpanStart
            var startRow = spanObj.rowSpanStart
            var endColumn = spanObj.columnSpanEnd
            var endRow = spanObj.rowSpanEnd

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
                            sheetData[rowKey]!!.remove(columnKey)
                        }
                    }
                }
                data[rowKey] = columnMap
            }
            sheetData = data
        }

        if (_maxColumns - 1 <= columnIndex) {
            _maxColumns -= 1
        }
    }

    public fun insertColumn(columnIndex: Int) {
        if (columnIndex < 0) {
            return
        }
        checkMaxColumn(columnIndex)

        var updateSpanCell = false

        _spannedItems = mutableListOf()
        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue
            var startColumn = spanObj.columnSpanStart
            var startRow = spanObj.rowSpanStart
            var endColumn = spanObj.columnSpanEnd
            var endRow = spanObj.rowSpanEnd

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
        if (_maxColumns - 1 <= columnIndex) {
            _maxColumns += 1
        } else {
            _maxColumns = columnIndex + 1
        }
    }

    public fun removeRow(rowIndex: Int) {
        if (rowIndex < 0 || rowIndex >= _maxRows) {
            return
        }
        checkMaxRow(rowIndex)

        var updateSpanCell = false

        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue
            var startColumn = spanObj.columnSpanStart
            var startRow = spanObj.rowSpanStart
            var endColumn = spanObj.columnSpanEnd
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
                        data[rowKey - 1] = sheetData[rowKey]!!.toMutableMap()
                    }
                }
                sheetData = data
            }
        } else {
            _maxRows = 0
            _maxColumns = 0
        }

        if (_maxRows - 1 <= rowIndex) {
            _maxRows -= 1
        }
    }

    public fun insertRow(rowIndex: Int) {
        if (rowIndex < 0) {
            return
        }

        checkMaxRow(rowIndex)

        var updateSpanCell = false

        _spannedItems = mutableListOf()
        for (i in 0 until this@Sheet.spanList.size) {
            val spanObj = this@Sheet.spanList[i] ?: continue
            var startColumn = spanObj.columnSpanStart
            var startRow = spanObj.rowSpanStart
            var endColumn = spanObj.columnSpanEnd
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
            }
        }
        data[rowIndex] = mutableMapOf(0 to Data.newData(this, rowIndex, 0))
        sheetData = data

        if (_maxRows - 1 <= rowIndex) {
            _maxRows = rowIndex + 1
        } else {
            _maxRows += 1
        }
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

        putData(newRowIndex, newColumnIndex, value)

        var cellStyleToUse = cellStyle
        if (cellStyle != null) {
            val numberFormat = cellStyle.numberFormat
            if (!numberFormat.accepts(value)) {
                cellStyleToUse = cellStyle.copyWith(numberFormat = NumFormat.defaultFor(value))
            }
        } else {
            val cellStyleBefore = sheetData[cellIndex.rowIndex]?.get(cellIndex.columnIndex)?.cellStyle
            if (cellStyleBefore != null && !cellStyleBefore.numberFormat.accepts(value)) {
                cellStyleToUse = cellStyleBefore.copyWith(numberFormat = NumFormat.defaultFor(value))
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
            excel.addMergeChangeLookup(sheetName)
        }
    }

    public fun setMergedCellStyle(start: CellIndex, mergedCellStyle: CellStyle) {
        val _mergedCells = this@Sheet.spannedItems.map { e ->
            e.split(":").map { CellIndex.indexByString(it) }
        }

        val _startIndices = _mergedCells.map { it[0] }
        val _endIndices = _mergedCells.map { it[1] }

        if (_mergedCells.isEmpty() || start.columnIndex < 0 || start.rowIndex < 0 || !_startIndices.contains(start)) {
            return
        }

        val end = _endIndices[_startIndices.indexOf(start)]

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

            if (locationChange.first) {
                startColumn = locationChange.second[0]
                startRow = locationChange.second[1]
                endColumn = locationChange.second[2]
                endRow = locationChange.second[3]
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
        checkMaxColumn(columnIndex + row.size)
        val rowsLength = _maxRows
        val maxIterationIndex = row.size - 1
        var currentRowPosition = 0

        if (overwriteMergedCells || rowIndex >= rowsLength) {
            while (currentRowPosition <= maxIterationIndex) {
                putData(rowIndex, columnIndex++, row[currentRowPosition++])
            }
        } else {
            selfCorrectSpanMap(excel)
            val _spanObjectsList = getSpannedObjects(rowIndex, columnIndex)

            if (_spanObjectsList.isEmpty()) {
                while (currentRowPosition <= maxIterationIndex) {
                    putData(rowIndex, columnIndex++, row[currentRowPosition++])
                }
            } else {
                while (currentRowPosition <= maxIterationIndex) {
                    if (isInsideSpanObject(_spanObjectsList, columnIndex, rowIndex)) {
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

    public fun setDefaultColumnWidth(columnWidth: Double = excelDefaultColumnWidth) {
        if (columnWidth < 0) return
        _defaultColumnWidth = columnWidth
    }

    public fun setDefaultRowHeight(rowHeight: Double = excelDefaultRowHeight) {
        if (rowHeight < 0) return
        _defaultRowHeight = rowHeight
    }

    public fun setColumnAutoFit(columnIndex: Int) {
        checkMaxColumn(columnIndex)
        if (columnIndex < 0) return
        _columnAutoFit[columnIndex] = true
    }

    public fun setColumnWidth(columnIndex: Int, columnWidth: Double) {
        checkMaxColumn(columnIndex)
        if (columnWidth < 0) return
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
                val result = source.replace(sourceData.value.toString()) { match ->
                    if (first == -1 || first != replaceCount) {
                        replaceCount++
                        match.value.replaceRange(match.range.first, match.range.last + 1, target)
                    } else {
                        match.value
                    }
                }
                sheetData[i]!![j]!!.value = TextCellValue(result)
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
        if (_maxColumns >= 16384 || columnIndex >= 16384) {
            throw IllegalArgumentException("Reached Max (16384) or (XFD) columns value.")
        }
        if (columnIndex < 0) {
            throw IllegalArgumentException("Negative columnIndex found: $columnIndex")
        }
    }

    internal fun checkMaxRow(rowIndex: Int) {
        if (_maxRows >= 1048576 || rowIndex >= 1048576) {
            throw IllegalArgumentException("Reached Max (1048576) rows value.")
        }
        if (rowIndex < 0) {
            throw IllegalArgumentException("Negative rowIndex found: $rowIndex")
        }
    }

    public val spannedItems: List<String>
        get() {
            _spannedItems = mutableListOf()

            for (i in 0 until this@Sheet.spanList.size) {
                val spanObj = this@Sheet.spanList[i] ?: continue
                val rC = getSpanCellId(spanObj.columnSpanStart, spanObj.rowSpanStart, spanObj.columnSpanEnd, spanObj.rowSpanEnd)
                if (!_spannedItems.contains(rC)) {
                    _spannedItems.add(rC)
                }
            }

            return _spannedItems
        }

    internal fun cleanUpSpanMap() {
        if (this@Sheet.spanList.isNotEmpty()) {
            this@Sheet.spanList.removeAll { it == null }
        }
    }

    public val sheetName: String
        get() = _sheet

    public fun row(rowIndex: Int): List<Data?> {
        if (rowIndex < 0) {
            return emptyList()
        }
        if (rowIndex < _maxRows) {
            if (sheetData[rowIndex] != null) {
                return List(_maxColumns) { columnIndex ->
                    sheetData[rowIndex]!![columnIndex]
                }
            } else {
                return List(_maxColumns) { null }
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
            var spanList = excel.sheetMap[key]!!.spanList.toMutableList()

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
                    if (locationChange.first) {
                        startColumn = locationChange.second[0]
                        startRow = locationChange.second[1]
                        endColumn = locationChange.second[2]
                        endRow = locationChange.second[3]
                        spanList[j] = null
                    } else {
                        val locationChange2 = isLocationChangeRequired(
                            spanObj.columnSpanStart,
                            spanObj.rowSpanStart,
                            spanObj.columnSpanEnd,
                            spanObj.rowSpanEnd,
                            checkerPos
                        )

                        if (locationChange2.first) {
                            startColumn = locationChange2.second[0]
                            startRow = locationChange2.second[1]
                            endColumn = locationChange2.second[2]
                            endRow = locationChange2.second[3]
                            spanList[j] = null;
                        }
                    }
                }
                val spanObj1 = Span(
                    rowSpanStart = startRow,
                    columnSpanStart = startColumn,
                    rowSpanEnd = endRow,
                    columnSpanEnd = endColumn,
                );
                spanList[i] = spanObj1
            }
            excel.sheetMap[key]!!.spanList = spanList.toMutableList()
            excel.sheetMap[key]!!.cleanUpSpanMap()
        }
    }
}

internal fun isLocationChangeRequired(startColumn: Int, startRow: Int, endColumn: Int, endRow: Int, spanObj: Span): Pair<Boolean, List<Int>> {
    // Implement based on original logic, assuming it's a method that checks if location changes
    // For now, return false and original positions
    return Pair(false, listOf(startColumn, startRow, endColumn, endRow))
}
