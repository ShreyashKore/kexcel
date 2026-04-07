package dev.shreyash.kxcel

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.XmlDeclaration
import dev.shreyash.kxcel.number_format.NumFormatMaintainer
import dev.shreyash.kxcel.parser.Parser
import dev.shreyash.kxcel.save.Save
import dev.shreyash.kxcel.shared_strings.SharedStringsMaintainer
import dev.shreyash.kxcel.sheet.BorderSet
import dev.shreyash.kxcel.sheet.CellIndex
import dev.shreyash.kxcel.sheet.CellStyle
import dev.shreyash.kxcel.sheet.CellValue
import dev.shreyash.kxcel.sheet.FontStyle
import dev.shreyash.kxcel.sheet.Sheet
import dev.shreyash.kxcel.archive.Archive
import dev.shreyash.kxcel.archive.ArchiveFile
import dev.shreyash.kxcel.utils.NEW_SHEET
import dev.shreyash.kxcel.utils.cloneArchive
import dev.shreyash.kxcel.utils.damagedExcel
import dev.shreyash.kxcel.utils.readZipArchive
import dev.shreyash.kxcel.web_helper.SavingHelper
import no.synth.kmpzip.io.ByteArrayInputStream
import no.synth.kmpzip.io.InputStream
import kotlin.io.encoding.Base64

private const val SPREADSHEET_XLSX = "xlsx"

fun newExcel(archive: Archive): Excel {
    var format: String? = null

    val mimetype = archive.findFile("mimetype")
    if (mimetype == null) {
        val xl = archive.findFile("xl/workbook.xml")
        if (xl != null) {
            format = SPREADSHEET_XLSX
        }
    }

    return when (format) {
        SPREADSHEET_XLSX -> Excel(archive)
        else -> throw UnsupportedOperationException(
            "Excel format unsupported. Only .xlsx files are supported"
        )
    }
}

/**
 * Decode an Excel file.
 */
class Excel internal constructor(internal var archive: Archive) {

    var styleChanges: Boolean = false
    internal var mergeChanges: Boolean = false
    internal var rtlChanges: Boolean = false

    internal val sheets: MutableMap<String, Element> = mutableMapOf()
    internal val xmlFiles: MutableMap<String, Document> = mutableMapOf()
    internal val xmlSheetId: MutableMap<String, String> = mutableMapOf()
    internal val cellStyleReferenced: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    internal val sheetMap: MutableMap<String, Sheet> = mutableMapOf()

    internal var cellStyleList: MutableList<CellStyle> = mutableListOf()
    internal var patternFill: MutableList<String> = mutableListOf()
    internal val mergeChangeLook: MutableList<String> = mutableListOf()
    internal val rtlChangeLook: MutableList<String> = mutableListOf()
    internal var fontStyleList: MutableList<FontStyle> = mutableListOf()
    internal val numFmtIds: MutableList<Int> = mutableListOf()
    internal val numFormats: NumFormatMaintainer = NumFormatMaintainer()
    internal var borderSetList: MutableList<BorderSet> = mutableListOf()

    internal var sharedStrings: SharedStringsMaintainer = SharedStringsMaintainer()

    internal var stylesTarget: String = ""
    internal var sharedStringsTarget: String = ""

    internal val absSharedStringsTarget: String
        get() {
            return if (sharedStringsTarget.isNotEmpty() && sharedStringsTarget[0] == '/') {
                sharedStringsTarget.substring(1)
            } else {
                "xl/$sharedStringsTarget"
            }
        }

    internal var defaultSheet: String? = null
    internal val parser: Parser = Parser(this)

    init {
        parser.startParsing()
    }

    companion object {
        fun createExcel(): Excel {
            val decoded = Base64.Default.decode(NEW_SHEET)
            return decodeBytes(decoded)
        }

        fun decodeBytes(data: ByteArray): Excel {
            val archive: Archive = try {
                val inputStream = ByteArrayInputStream(data)
                readZipArchive(inputStream)
            } catch (e: Exception) {
                throw UnsupportedOperationException(
                    "Excel format unsupported. Only .xlsx files are supported"
                )
            }
            return newExcel(archive)
        }

        fun decodeStream(input: InputStream): Excel {
            return newExcel(readZipArchive(input))
        }
    }

    /**
     * Returns `tables` as a map to mimic previous versions reading the data.
     */
    val tables: Map<String, Sheet>
        get() {
            if (sheetMap.isEmpty()) {
                damagedExcel(text = "Corrupted Excel file.")
            }
            return sheetMap.toMap()
        }

    /**
     * Returns the SheetObject of [sheet].
     * If the [sheet] does not exist it will be created with a new Sheet Object.
     */
    operator fun get(sheet: String): Sheet {
        availSheet(sheet)
        return sheetMap[sheet]!!
    }

    /**
     * Returns `Map<String, Sheet>` where key is the Sheet Name and value is the Sheet Object.
     */
    fun getSheets(): Map<String, Sheet> = sheetMap.toMap()

    /**
     * If [sheet] does not exist it will be automatically created with contents of [sheetObject].
     * Newly created sheet will have a separate reference and will not be linked to sheetObject.
     */
    operator fun set(sheet: String, sheetObject: Sheet) {
        availSheet(sheet)
        sheetMap[sheet] = Sheet.clone(this, sheet, sheetObject)
    }

    /**
     * Links [existingSheetObject] with [sheet1].
     * If [sheet1] does not exist it will be automatically created.
     * After linkage, operations on [sheet1] will also be performed on [existingSheetObject] and vice-versa.
     */
    fun link(sheet1: String, existingSheetObject: Sheet) {
        if (sheetMap[existingSheetObject.sheetName] != null) {
            availSheet(sheet1)
            sheetMap[sheet1] = sheetMap[existingSheetObject.sheetName]!!
            cellStyleReferenced[existingSheetObject.sheetName]?.let { ref ->
                cellStyleReferenced[sheet1] = ref.toMutableMap()
            }
        }
    }

    /**
     * If [sheet] is linked with any other sheet's object, the link will be broken.
     */
    fun unLink(sheet: String) {
        if (sheetMap[sheet] != null) {
            // Copying the sheet into itself breaks the linkage since Sheet.clone() provides a new reference
            copy(sheet, sheet)
        }
    }

    /**
     * Copies the content of [fromSheet] into [toSheet].
     * [fromSheet] should exist in `tables.keys`.
     * If [toSheet] does not exist it will be automatically created.
     */
    fun copy(fromSheet: String, toSheet: String) {
        availSheet(toSheet)
        if (sheetMap[fromSheet] != null) {
            this[toSheet] = this[fromSheet]
        }
        cellStyleReferenced[fromSheet]?.let { ref ->
            cellStyleReferenced[toSheet] = ref.toMutableMap()
        }
    }

    /**
     * Renames [oldSheetName] to [newSheetName].
     * [oldSheetName] must exist and [newSheetName] must not exist.
     */
    fun rename(oldSheetName: String, newSheetName: String) {
        if (sheetMap[oldSheetName] != null && sheetMap[newSheetName] == null) {
            if (defaultSheet == oldSheetName) {
                defaultSheet = newSheetName
            }
            copy(oldSheetName, newSheetName)
            delete(oldSheetName)
        }
    }

    /**
     * If [sheet] exists in `tables.keys` and `tables.keys.size >= 2`, it will be deleted.
     */
    fun delete(sheet: String) {
        if (sheetMap.size <= 1) return

        if (defaultSheet == sheet) {
            defaultSheet = null
        }

        sheetMap.remove(sheet)
        mergeChangeLook.remove(sheet)
        rtlChangeLook.remove(sheet)

        xmlSheetId[sheet]?.let { sheetPath ->
            val sheetId1 = "worksheets" + sheetPath.split("worksheets")[1]
            val sheetId2 = sheetPath

            xmlFiles["xl/_rels/workbook.xml.rels"]
                ?.root()
                ?.children()
                ?.removeAll { child ->
                    child.attr("Target") == sheetId1
                }

            xmlFiles["[Content_Types].xml"]
                ?.root()
                ?.children()
                ?.removeAll { child ->
                    child.attr("PartName") == "/$sheetId2"
                }

            xmlFiles.remove(xmlSheetId[sheet])

            archive = cloneArchive(
                archive,
                xmlFiles.map { (k, v) ->
                    val encoded = v.toString().encodeToByteArray()
                    k to ArchiveFile(k, encoded.size, encoded)
                }.toMap(),
                excludedFile = xmlSheetId[sheet]
            )

            xmlSheetId.remove(sheet)
        }

        if (sheets[sheet] != null) {
            xmlFiles["xl/workbook.xml"]
                ?.getElementsByTag("sheets")
                ?.firstOrNull()
                ?.children()
                ?.removeAll { element ->
                    element.attribute("name")?.toString() == sheet
                }
            sheets.remove(sheet)
        }

        cellStyleReferenced.remove(sheet)
    }

    /**
     * Sets the edited values of [sheetMap] into the files and exports the file.
     */
    fun encode(): ByteArray? {
        val s = Save(this, parser)
        return s.save()
    }

    /**
     * Saves the file and returns its bytes.
     */
    fun save(fileName: String = "FlutterExcel.xlsx"): ByteArray? {
        val s = Save(this, parser)
        val onValue = s.save()
        return SavingHelper.saveFile(onValue, fileName)
    }

    /**
     * Returns the name of the default sheet (the sheet which opens first when the xlsx file is opened).
     */
    fun getDefaultSheet(): String? {
        return defaultSheet ?: getDefaultSheetInternal()
    }

    /**
     * Internal function which returns the default sheet name by reading from `workbook.xml`.
     */
    fun getDefaultSheetInternal(): String? {
        val elements = xmlFiles["xl/workbook.xml"]?.getElementsByTag("sheet")
        val sheet = elements?.firstOrNull()

        return if (sheet != null) {
            val name = sheet.attr("name")
            if (name != null) {
                name
            } else {
                damagedExcel(text = "Excel sheet corrupted!! Try creating new excel file.")
                null
            }
        } else null
    }

    /**
     * Returns `true` if [sheetName] is successfully set as the default opening sheet, otherwise `false`.
     */
    fun setDefaultSheet(sheetName: String): Boolean {
        return if (sheetMap[sheetName] != null) {
            defaultSheet = sheetName
            true
        } else {
            false
        }
    }

    /**
     * Inserts an empty column in [sheet] at position [columnIndex].
     * If [columnIndex] < 0 it will not execute.
     * If [sheet] does not exist it will be created automatically.
     */
    fun insertColumn(sheet: String, columnIndex: Int) {
        if (columnIndex < 0) return
        availSheet(sheet)
        sheetMap[sheet]!!.insertColumn(columnIndex)
    }

    /**
     * If [sheet] exists and [columnIndex] < maxColumns, removes column at [columnIndex].
     */
    fun removeColumn(sheet: String, columnIndex: Int) {
        if (columnIndex >= 0 && sheetMap[sheet] != null) {
            sheetMap[sheet]!!.removeColumn(columnIndex)
        }
    }

    /**
     * Inserts an empty row in [sheet] at position [rowIndex].
     * If [rowIndex] < 0 it will not execute.
     * If [sheet] does not exist it will be created automatically.
     */
    fun insertRow(sheet: String, rowIndex: Int) {
        if (rowIndex < 0) return
        availSheet(sheet)
        sheetMap[sheet]!!.insertRow(rowIndex)
    }

    /**
     * If [sheet] exists and [rowIndex] < maxRows, removes row at [rowIndex].
     */
    fun removeRow(sheet: String, rowIndex: Int) {
        if (rowIndex >= 0 && sheetMap[sheet] != null) {
            sheetMap[sheet]!!.removeRow(rowIndex)
        }
    }

    /**
     * Appends [row] iterables just after the last filled index in [sheet].
     * If [sheet] does not exist it will be automatically created.
     */
    fun appendRow(sheet: String, row: List<CellValue?>) {
        if (row.isEmpty()) return
        availSheet(sheet)
        val targetRow = sheetMap[sheet]!!.maxRows
        insertRowIterables(sheet, row, targetRow)
    }

    /**
     * Adds the [row] iterables at the given [rowIndex] in [sheet].
     * If [sheet] does not exist it will be automatically created.
     *
     * @param startingColumn tells from where to start placing the [row] iterables.
     * @param overwriteMergedCells when `true` will overwrite merged cells;
     *   when `false` puts the value in merged cells only once and jumps to next unique cell.
     */
    fun insertRowIterables(
        sheet: String,
        row: List<CellValue?>,
        rowIndex: Int,
        startingColumn: Int = 0,
        overwriteMergedCells: Boolean = true
    ) {
        if (rowIndex < 0) return
        availSheet(sheet)
        sheetMap[sheet]!!.insertRowIterables(
            row,
            rowIndex,
            startingColumn = startingColumn,
            overwriteMergedCells = overwriteMergedCells
        )
    }

    /**
     * Returns the count of replaced [source] occurrences with [target].
     * [source] can be a Regex or a String.
     * Optional [first] limits replacement to the first N occurrences.
     */
    fun findAndReplace(
        sheet: String,
        source: Regex, // Regex or String
        target: String,
        first: Int = -1,
        startingRow: Int = -1,
        endingRow: Int = -1,
        startingColumn: Int = -1,
        endingColumn: Int = -1
    ): Int {
        var replaceCount = 0
        if (sheetMap[sheet] == null) return replaceCount

        sheetMap[sheet]!!.findAndReplace(
            source,
            target,
            first = first,
            startingRow = startingRow,
            endingRow = endingRow,
            startingColumn = startingColumn,
            endingColumn = endingColumn
        )

        return replaceCount
    }

    /**
     * Makes [sheet] available in [sheetMap] if it doesn't already exist.
     */
    internal fun availSheet(sheet: String) {
        if (sheetMap[sheet] == null) {
            sheetMap[sheet] = Sheet(this, sheet)
        }
    }

    /**
     * Updates the contents of [sheet] at [cellIndex].
     * Indexing starts from 0; e.g. `CellIndex.indexByColumnRow(0, 0)` or `CellIndex.indexByString("A3")`.
     * If [sheet] does not exist it will be automatically created.
     */
    fun updateCell(
        sheet: String,
        cellIndex: CellIndex,
        value: CellValue?,
        cellStyle: CellStyle? = null
    ) {
        availSheet(sheet)
        sheetMap[sheet]!!.updateCell(cellIndex, value, cellStyle = cellStyle)
    }

    /**
     * Merges the cells from [start] to [end].
     * If [customValue] is not defined, it looks for the first available value in range row-wise left to right.
     * If [sheet] does not exist it will be automatically created.
     */
    fun merge(
        sheet: String,
        start: CellIndex,
        end: CellIndex,
        customValue: CellValue? = null
    ) {
        availSheet(sheet)
        sheetMap[sheet]!!.merge(start, end, customValue = customValue)
    }

    /**
     * Returns a list of cell IDs for previously merged cells.
     */
    fun getMergedCells(sheet: String): List<String> {
        return sheetMap[sheet]?.spannedItems?.toList() ?: emptyList()
    }

    /**
     * Unmerges the merged cells specified by [unmergeCells].
     *
     * Example:
     * ```
     * val spannedCells = excel.getMergedCells(sheet)
     * excel.unMerge(sheet, "A1:A2")
     * ```
     */
    fun unMerge(sheet: String, unmergeCells: String) {
        sheetMap[sheet]?.unMerge(unmergeCells)
    }

    /**
     * Adds [value] to [mergeChangeLook] if not already present.
     */
    internal fun addMergeChangeLookup(value: String) {
        if (!mergeChangeLook.contains(value)) {
            mergeChangeLook.add(value)
        }
    }

    /**
     * Adds [value] to [rtlChangeLook] if not already present, and sets [rtlChanges] to true.
     */
    internal fun addRtlChangeLookup(value: String) {
        if (!rtlChangeLook.contains(value)) {
            rtlChangeLook.add(value)
            rtlChanges = true
        }
    }
}
