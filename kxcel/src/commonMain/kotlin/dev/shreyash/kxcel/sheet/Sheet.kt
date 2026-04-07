package dev.shreyash.kxcel.sheet

import dev.shreyash.kxcel.Excel
import dev.shreyash.kxcel.number_format.NumFormat
import dev.shreyash.kxcel.utils.Span
import kotlin.math.max


class Sheet constructor(
    val excel: Excel,
    private val _sheet: String,
    sh: Map<Int, Map<Int, Data>>? = null,
    spanL_: List<Span?>? = null,
    spanI_: MutableList<String>? = null,
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
    var _defaultColumnWidth: Double? = null
    var _defaultRowHeight: Double? = null
    var _columnWidths: MutableMap<Int, Double> = mutableMapOf()
    var _rowHeights: MutableMap<Int, Double> = mutableMapOf()
    var _columnAutoFit: MutableMap<Int, Boolean> = mutableMapOf()
    var _spannedItems: MutableList<String> = mutableListOf()
    var spanList: MutableList<Span?> = mutableListOf()
    var sheetData: MutableMap<Int, MutableMap<Int, Data>> = mutableMapOf()
    var _headerFooter: HeaderFooter? = null

    companion object {
        const val _excelDefaultColumnWidth = 8.43
        const val _excelDefaultRowHeight = 15.0

        fun clone(excel: Excel, sheetName: String, oldSheetObject: Sheet): Sheet {
            return Sheet(
                excel,
                sheetName,
                sh = oldSheetObject.sheetData,
                spanL_ = oldSheetObject.spanList,
                spanI_ = oldSheetObject._spannedItems,
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

        if (spanL_ != null) {
            spanList = spanL_.toMutableList()
            excel.addMergeChangeLookup(sheetName)
        }
        if (spanI_ != null) {
            _spannedItems = spanI_
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
        _countRowsAndColumns()
    }

    fun removeCell(rowIndex: Int, columnIndex: Int) {
        sheetData[rowIndex]?.remove(columnIndex)
        val rowIsEmptyAfterRemovalOfCell = sheetData[rowIndex]?.isEmpty() == true
        if (rowIsEmptyAfterRemovalOfCell) {
            sheetData.remove(rowIndex)
        }
    }

    var isRTL: Boolean
        get() = _isRTL
        set(value) {
            _isRTL = value
            excel.addRtlChangeLookup(sheetName)
        }

    fun cell(cellIndex: CellIndex): Data {
        _checkMaxColumn(cellIndex.columnIndex)
        _checkMaxRow(cellIndex.rowIndex)
        if (cellIndex.columnIndex < 0 || cellIndex.rowIndex < 0) {
            _damagedExcel("Negative index does not exist.")
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

    val rows: List<List<Data?>>
        get() {
            val _data = mutableListOf<List<Data?>>()

            if (sheetData.isEmpty()) {
                return _data
            }

            if (_maxRows > 0 && maxColumns > 0) {
                _data.addAll(List(_maxRows) { rowIndex ->
                    List(maxColumns) { columnIndex ->
                        sheetData[rowIndex]?.get(columnIndex)
                    }
                })
            }

            return _data
        }

    fun selectRangeWithString(range: String): List<List<Data?>?> {
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

    fun selectRange(start: CellIndex, end: CellIndex? = null): List<List<Data?>?> {
        _checkMaxColumn(start.columnIndex)
        _checkMaxRow(start.rowIndex)
        end?.let {
            _checkMaxColumn(it.columnIndex)
            _checkMaxRow(it.rowIndex)
        }

        var _startColumn = start.columnIndex
        var _startRow = start.rowIndex
        var _endColumn = end?.columnIndex
        var _endRow = end?.rowIndex

        if (_endColumn != null && _endRow != null) {
            if (_startRow > _endRow) {
                _startRow = end.rowIndex
                _endRow = start.rowIndex
            }
            if (_endColumn < _startColumn) {
                _endColumn = start.columnIndex
                _startColumn = end.columnIndex
            }
        }

        val _selectedRange = mutableListOf<List<Data?>?>()
        if (sheetData.isEmpty()) {
            return _selectedRange
        }

        for (i in _startRow..(_endRow ?: maxRows)) {
            val mapData = sheetData[i]
            if (mapData != null) {
                val row = mutableListOf<Data?>()
                for (j in _startColumn..(_endColumn ?: maxColumns)) {
                    row.add(mapData[j])
                }
                _selectedRange.add(row)
            } else {
                _selectedRange.add(null)
            }
        }

        return _selectedRange
    }

    fun selectRangeValuesWithString(range: String): List<List<Any?>> {
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

    fun selectRangeValues(start: CellIndex, end: CellIndex? = null): List<List<Any?>> {
        val _list = if (end == null) selectRange(start) else selectRange(start, end = end)
        return _list.map { e ->
            e?.map { it?.value } ?: emptyList()
        }
    }

    fun _countRowsAndColumns() {
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

    fun removeColumn(columnIndex: Int) {
        _checkMaxColumn(columnIndex)
        if (columnIndex < 0 || columnIndex >= maxColumns) {
            return
        }

        var updateSpanCell = false

        for (i in 0 until spanList.size) {
            val spanObj = spanList[i] ?: continue
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
                    spanList[i] = null
                } else {
                    val newSpanObj = Span(
                        rowSpanStart = startRow,
                        columnSpanStart = startColumn,
                        rowSpanEnd = endRow,
                        columnSpanEnd = endColumn
                    )
                    spanList[i] = newSpanObj
                }
                updateSpanCell = true
                excel.mergeChanges = true
            }

            if (spanList[i] != null) {
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

        val _data = mutableMapOf<Int, MutableMap<Int, Data>>()
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
                _data[rowKey] = columnMap
            }
            sheetData = _data
        }

        if (_maxColumns - 1 <= columnIndex) {
            _maxColumns -= 1
        }
    }

    fun insertColumn(columnIndex: Int) {
        if (columnIndex < 0) {
            return
        }
        _checkMaxColumn(columnIndex)

        var updateSpanCell = false

        _spannedItems = mutableListOf()
        for (i in 0 until spanList.size) {
            val spanObj = spanList[i] ?: continue
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
                spanList[i] = newSpanObj
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
            val _data = mutableMapOf<Int, MutableMap<Int, Data>>()
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
                    _data[rowKey] = columnMap
                }
                sheetData = _data
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

    fun removeRow(rowIndex: Int) {
        if (rowIndex < 0 || rowIndex >= _maxRows) {
            return
        }
        _checkMaxRow(rowIndex)

        var updateSpanCell = false

        for (i in 0 until spanList.size) {
            val spanObj = spanList[i] ?: continue
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
                    spanList[i] = null
                } else {
                    val newSpanObj = Span(
                        rowSpanStart = startRow,
                        columnSpanStart = startColumn,
                        rowSpanEnd = endRow,
                        columnSpanEnd = endColumn
                    )
                    spanList[i] = newSpanObj
                }
                updateSpanCell = true
                excel.mergeChanges = true
            }
            if (spanList[i] != null) {
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
            val _data = mutableMapOf<Int, MutableMap<Int, Data>>()
            if (rowIndex <= maxRows - 1) {
                val sortedKeys = sheetData.keys.sorted()
                sortedKeys.forEach { rowKey ->
                    if (rowKey < rowIndex && sheetData[rowKey] != null) {
                        _data[rowKey] = sheetData[rowKey]!!.toMutableMap()
                    }
                    if (rowIndex < rowKey && sheetData[rowKey] != null) {
                        _data[rowKey - 1] = sheetData[rowKey]!!.toMutableMap()
                    }
                }
                sheetData = _data
            }
        } else {
            _maxRows = 0
            _maxColumns = 0
        }

        if (_maxRows - 1 <= rowIndex) {
            _maxRows -= 1
        }
    }

    fun insertRow(rowIndex: Int) {
        if (rowIndex < 0) {
            return
        }

        _checkMaxRow(rowIndex)

        var updateSpanCell = false

        _spannedItems = mutableListOf()
        for (i in 0 until spanList.size) {
            val spanObj = spanList[i] ?: continue
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
                spanList[i] = newSpanObj
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

        val _data = mutableMapOf<Int, MutableMap<Int, Data>>()
        if (sheetData.isNotEmpty()) {
            val sortedKeys = sheetData.keys.sortedDescending()
            if (rowIndex <= maxRows - 1) {
                sortedKeys.forEach { rowKey ->
                    if (rowKey < rowIndex) {
                        _data[rowKey] = sheetData[rowKey]!!
                    }
                    if (rowIndex <= rowKey) {
                        _data[rowKey + 1] = sheetData[rowKey]!!
                        _data[rowKey + 1]!!.forEach { (_, value) ->
                            value._rowIndex++
                        }
                    }
                }
            }
        }
        _data[rowIndex] = mutableMapOf(0 to Data.newData(this, rowIndex, 0))
        sheetData = _data

        if (_maxRows - 1 <= rowIndex) {
            _maxRows = rowIndex + 1
        } else {
            _maxRows += 1
        }
    }

    fun updateCell(cellIndex: CellIndex, value: CellValue?, cellStyle: CellStyle? = null) {
        val columnIndex = cellIndex.columnIndex
        val rowIndex = cellIndex.rowIndex
        if (columnIndex < 0 || rowIndex < 0) {
            return
        }
        _checkMaxColumn(columnIndex)
        _checkMaxRow(rowIndex)

        var newRowIndex = rowIndex
        var newColumnIndex = columnIndex

        if (spanList.isNotEmpty()) {
            val (nr, nc) = _isInsideSpanning(rowIndex, columnIndex)
            newRowIndex = nr
            newColumnIndex = nc
        }

        _putData(newRowIndex, newColumnIndex, value)

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

    fun merge(start: CellIndex, end: CellIndex, customValue: CellValue? = null) {
        var startColumn = start.columnIndex
        var startRow = start.rowIndex
        var endColumn = end.columnIndex
        var endRow = end.rowIndex

        _checkMaxColumn(startColumn)
        _checkMaxColumn(endColumn)
        _checkMaxRow(startRow)
        _checkMaxRow(endRow)

        if ((startColumn == endColumn && startRow == endRow) || (startColumn < 0 || startRow < 0 || endColumn < 0 || endRow < 0) || (_spannedItems.contains(getSpanCellId(startColumn, startRow, endColumn, endRow)))) {
            return
        }

        val gotPosition = _getSpanPosition(start, end)

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

        spanList.add(s)
        excel.addMergeChangeLookup(sheetName)
    }

    fun unMerge(unmergeCells: String) {
        if (_spannedItems.isNotEmpty() && spanList.isNotEmpty() && _spannedItems.contains(unmergeCells)) {
            val lis = unmergeCells.split(Regex(":"))
            if (lis.size == 2) {
                var remove = false
                val start = CellIndex.indexByString(lis[0])
                val end = CellIndex.indexByString(lis[1])
                for (i in 0 until spanList.size) {
                    val spanObject = spanList[i] ?: continue

                    if (spanObject.columnSpanStart == start.columnIndex && spanObject.rowSpanStart == start.rowIndex && spanObject.columnSpanEnd == end.columnIndex && spanObject.rowSpanEnd == end.rowIndex) {
                        spanList[i] = null
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

    fun setMergedCellStyle(start: CellIndex, mergedCellStyle: CellStyle) {
        val _mergedCells = spannedItems.map { e ->
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
                        _putData(i, j, null)
                        sheetData[i]!![j]!!.cellStyle = cellStyle
                    }
                }
            }
        }
    }

    fun _getSpanPosition(start: CellIndex, end: CellIndex): List<Int> {
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

        for (i in 0 until spanList.size) {
            val spanObj = spanList[i] ?: continue

            val locationChange = _isLocationChangeRequired(startColumn, startRow, endColumn, endRow, spanObj)

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
                spanList[i] = null
            }
        }
        if (remove) {
            cleanUpSpanMap()
        }

        return listOf(startColumn, startRow, endColumn, endRow)
    }

    fun appendRow(row: List<CellValue?>) {
        val targetRow = maxRows
        insertRowIterables(row, targetRow)
    }

    fun _getSpannedObjects(rowIndex: Int, startingColumnIndex: Int): List<Span> {
        val obtained = mutableListOf<Span>()

        if (spanList.isNotEmpty()) {
            obtained.addAll(spanList.filterNotNull().filter { spanObject ->
                spanObject.rowSpanStart <= rowIndex && rowIndex <= spanObject.rowSpanEnd && startingColumnIndex <= spanObject.columnSpanEnd
            })
        }
        return obtained
    }

    fun _isInsideSpanObject(spanObjectList: List<Span>, columnIndex: Int, rowIndex: Int): Boolean {
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

    fun insertRowIterables(row: List<CellValue?>, rowIndex: Int, startingColumn: Int = 0, overwriteMergedCells: Boolean = true) {
        if (row.isEmpty() || rowIndex < 0) {
            return
        }

        _checkMaxRow(rowIndex)
        var columnIndex = 0
        if (startingColumn > 0) {
            columnIndex = startingColumn
        }
        _checkMaxColumn(columnIndex + row.size)
        val rowsLength = _maxRows
        val maxIterationIndex = row.size - 1
        var currentRowPosition = 0

        if (overwriteMergedCells || rowIndex >= rowsLength) {
            while (currentRowPosition <= maxIterationIndex) {
                _putData(rowIndex, columnIndex++, row[currentRowPosition++])
            }
        } else {
            _selfCorrectSpanMap(excel)
            val _spanObjectsList = _getSpannedObjects(rowIndex, columnIndex)

            if (_spanObjectsList.isEmpty()) {
                while (currentRowPosition <= maxIterationIndex) {
                    _putData(rowIndex, columnIndex++, row[currentRowPosition++])
                }
            } else {
                while (currentRowPosition <= maxIterationIndex) {
                    if (_isInsideSpanObject(_spanObjectsList, columnIndex, rowIndex)) {
                        _putData(rowIndex, columnIndex, row[currentRowPosition++])
                    }
                    columnIndex++
                }
            }
        }
    }

    fun _putData(rowIndex: Int, columnIndex: Int, value: CellValue?) {
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

    val defaultRowHeight: Double?
        get() = _defaultRowHeight

    val defaultColumnWidth: Double?
        get() = _defaultColumnWidth

    val getColumnAutoFits: Map<Int, Boolean>
        get() = _columnAutoFit

    val getColumnWidths: Map<Int, Double>
        get() = _columnWidths

    val getRowHeights: Map<Int, Double>
        get() = _rowHeights

    fun getColumnAutoFit(columnIndex: Int): Boolean {
        return _columnAutoFit[columnIndex] ?: false
    }

    fun getColumnWidth(columnIndex: Int): Double {
        return _columnWidths[columnIndex] ?: _defaultColumnWidth!!
    }

    fun getRowHeight(rowIndex: Int): Double {
        return _rowHeights[rowIndex] ?: _defaultRowHeight!!
    }

    fun setDefaultColumnWidth(columnWidth: Double = _excelDefaultColumnWidth) {
        if (columnWidth < 0) return
        _defaultColumnWidth = columnWidth
    }

    fun setDefaultRowHeight(rowHeight: Double = _excelDefaultRowHeight) {
        if (rowHeight < 0) return
        _defaultRowHeight = rowHeight
    }

    fun setColumnAutoFit(columnIndex: Int) {
        _checkMaxColumn(columnIndex)
        if (columnIndex < 0) return
        _columnAutoFit[columnIndex] = true
    }

    fun setColumnWidth(columnIndex: Int, columnWidth: Double) {
        _checkMaxColumn(columnIndex)
        if (columnWidth < 0) return
        _columnWidths[columnIndex] = columnWidth
    }

    fun setRowHeight(rowIndex: Int, rowHeight: Double) {
        _checkMaxRow(rowIndex)
        if (rowHeight < 0) return
        _rowHeights[rowIndex] = rowHeight
    }

    fun addSpannedItem(ref: String) {
        _spannedItems.add(ref)
    }

    fun findAndReplace(source: Regex, target: String, first: Int = -1, startingRow: Int = -1, endingRow: Int = -1, startingColumn: Int = -1, endingColumn: Int = -1): Int {
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

    fun clearRow(rowIndex: Int): Boolean {
        if (rowIndex < 0) {
            return false
        }

        var isNotInside = true

        if (sheetData[rowIndex] != null && sheetData[rowIndex]!!.isNotEmpty()) {
            for (i in 0 until spanList.size) {
                val spanObj = spanList[i] ?: continue
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

    fun _isInsideSpanning(rowIndex: Int, columnIndex: Int): Pair<Int, Int> {
        var newRowIndex = rowIndex
        var newColumnIndex = columnIndex

        for (i in 0 until spanList.size) {
            val spanObj = spanList[i] ?: continue

            if (rowIndex >= spanObj.rowSpanStart && rowIndex <= spanObj.rowSpanEnd && columnIndex >= spanObj.columnSpanStart && columnIndex <= spanObj.columnSpanEnd) {
                newRowIndex = spanObj.rowSpanStart
                newColumnIndex = spanObj.columnSpanStart
                break
            }
        }

        return Pair(newRowIndex, newColumnIndex)
    }

    fun _checkMaxColumn(columnIndex: Int) {
        if (_maxColumns >= 16384 || columnIndex >= 16384) {
            throw IllegalArgumentException("Reached Max (16384) or (XFD) columns value.")
        }
        if (columnIndex < 0) {
            throw IllegalArgumentException("Negative columnIndex found: $columnIndex")
        }
    }

    fun _checkMaxRow(rowIndex: Int) {
        if (_maxRows >= 1048576 || rowIndex >= 1048576) {
            throw IllegalArgumentException("Reached Max (1048576) rows value.")
        }
        if (rowIndex < 0) {
            throw IllegalArgumentException("Negative rowIndex found: $rowIndex")
        }
    }

    val spannedItems: List<String>
        get() {
            _spannedItems = mutableListOf()

            for (i in 0 until spanList.size) {
                val spanObj = spanList[i] ?: continue
                val rC = getSpanCellId(spanObj.columnSpanStart, spanObj.rowSpanStart, spanObj.columnSpanEnd, spanObj.rowSpanEnd)
                if (!_spannedItems.contains(rC)) {
                    _spannedItems.add(rC)
                }
            }

            return _spannedItems
        }

    fun cleanUpSpanMap() {
        if (spanList.isNotEmpty()) {
            spanList.removeAll { it == null }
        }
    }

    val sheetName: String
        get() = _sheet

    fun row(rowIndex: Int): List<Data?> {
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

    val maxRows: Int
        get() = _maxRows

    val maxColumns: Int
        get() = _maxColumns

    var headerFooter: HeaderFooter?
        get() = _headerFooter
        set(value) {
            _headerFooter = value
        }
}

// Helper functions that are not defined in the provided code, assuming they exist or need to be implemented
fun getSpanCellId(startColumn: Int, startRow: Int, endColumn: Int, endRow: Int): String {
    // Implement based on CellIndex
    return "${CellIndex.indexByColumnRow(startColumn, startRow)}:${CellIndex.indexByColumnRow(endColumn, endRow)}"
}

fun _damagedExcel(text: String) {
    throw IllegalArgumentException(text)
}

fun _selfCorrectSpanMap(excel: Excel) {
    // Implement if needed
}

fun _isLocationChangeRequired(startColumn: Int, startRow: Int, endColumn: Int, endRow: Int, spanObj: Span): Pair<Boolean, List<Int>> {
    // Implement based on original logic, assuming it's a method that checks if location changes
    // For now, return false and original positions
    return Pair(false, listOf(startColumn, startRow, endColumn, endRow))
}
