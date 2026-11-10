package com.gyanoba.kexcel

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.gyanoba.kexcel.number_format.NumFormatMaintainer
import com.gyanoba.kexcel.parser.Parser
import com.gyanoba.kexcel.save.Save
import com.gyanoba.kexcel.shared_strings.SharedStringsMaintainer
import com.gyanoba.kexcel.sheet.BorderSet
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.CellValue
import com.gyanoba.kexcel.sheet.FontStyle
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.archive.Archive
import com.gyanoba.kexcel.archive.ArchiveFile
import com.gyanoba.kexcel.utils.NEW_SHEET
import com.gyanoba.kexcel.utils.SPREADSHEET_XLSX
import com.gyanoba.kexcel.utils.cloneArchive
import com.gyanoba.kexcel.utils.damagedExcel
import com.gyanoba.kexcel.utils.readZipArchive
import com.gyanoba.kexcel.utils.resolveWorkbookRelTarget
import com.gyanoba.kexcel.utils.renameSheetReferences
import com.gyanoba.kexcel.utils.serializeXml
import com.gyanoba.kexcel.utils.setAttribute
import no.synth.kmpzip.io.ByteArrayInputStream
import no.synth.kmpzip.io.InputStream
import kotlin.io.encoding.Base64

/**
 * Decode an Excel file.
 */
public class Excel internal constructor(internal var archive: Archive) {

    internal var styleChanges: Boolean = false
    internal var mergeChanges: Boolean = false
    internal var rtlChanges: Boolean = false

    /**
     * Set once any cell value, or the shape of any sheet, has been changed after loading.
     * Until then the cached results of formulas are still right and are written back; after
     * that they may be stale, so they are dropped and Excel is asked to recalculate.
     */
    internal var valuesEdited: Boolean = false

    /**
     * Whether the workbook counts dates from 1 January 1904 (`<workbookPr date1904="1">`,
     * §18.2.28) instead of 1900. Workbooks that began on a Mac often do.
     */
    internal var date1904: Boolean = false

    internal val sheets: MutableMap<String, Element> = mutableMapOf()
    internal val xmlFiles: MutableMap<String, Document> = mutableMapOf()
    internal val xmlSheetId: MutableMap<String, String> = mutableMapOf()
    internal val cellStyleReferenced: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    internal val sheetMap: MutableMap<String, Sheet> = mutableMapOf()

    internal var cellStyleList: MutableList<CellStyle> = mutableListOf()

    /**
     * One untouched copy per `<xf>` of what the parser read, parallel to [cellStyleList].
     * The instances in [cellStyleList] are handed to callers and can be mutated; these
     * cannot, so they say what each `<xf>` in the file really is.
     */
    internal var cellStylePristine: MutableList<CellStyle> = mutableListOf()

    /** `<xf>` entries added for styles made or changed in code, so a second save reuses them. */
    internal val derivedXf: MutableMap<Any, Int> = mutableMapOf()
    internal var patternFill: MutableList<String> = mutableListOf()
    internal val mergeChangeLook: MutableList<String> = mutableListOf()
    internal val rtlChangeLook: MutableList<String> = mutableListOf()
    internal var fontStyleList: MutableList<FontStyle> = mutableListOf()
    internal val numFmtIds: MutableList<Int> = mutableListOf()
    internal val numFormats: NumFormatMaintainer = NumFormatMaintainer()
    internal var borderSetList: MutableList<BorderSet> = mutableListOf()

    internal var sharedStrings: SharedStringsMaintainer = SharedStringsMaintainer()

    // Archive entry names (e.g. `xl/styles.xml`), resolved from the workbook relationships.
    internal var stylesPath: String = ""
    internal var sharedStringsPath: String = ""

    internal var defaultSheet: String? = null
    internal val parser: Parser = Parser(this)

    init {
        parser.startParsing()
    }

    public companion object {
        public fun createExcel(): Excel {
            val decoded = Base64.decode(NEW_SHEET)
            return decodeBytes(decoded)
        }

        public fun decodeBytes(data: ByteArray): Excel {
            val archive: Archive = try {
                val inputStream = ByteArrayInputStream(data)
                readZipArchive(inputStream)
            } catch (e: Exception) {
                throw UnsupportedOperationException(
                    "Excel format unsupported. Only .xlsx files are supported",
                    e
                )
            }
            return decodeExcel(archive)
        }

        public fun decodeStream(input: InputStream): Excel {
            return decodeExcel(readZipArchive(input))
        }
    }

    /**
     * Returns `tables` as a map to mimic previous versions reading the data.
     */
    public val tables: Map<String, Sheet>
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
    public operator fun get(sheet: String): Sheet {
        availSheet(sheet)
        return sheetMap[sheet]!!
    }

    /**
     * Returns `Map<String, Sheet>` where key is the Sheet Name and value is the Sheet Object.
     */
    public fun getSheets(): Map<String, Sheet> = sheetMap.toMap()

    /**
     * If [sheet] does not exist it will be automatically created with contents of [sheetObject].
     * Newly created sheet will have a separate reference and will not be linked to sheetObject.
     */
    public operator fun set(sheet: String, sheetObject: Sheet) {
        availSheet(sheet)
        valuesEdited = true
        sheetMap[sheet] = Sheet.clone(this, sheet, sheetObject)
    }

    /**
     * Links [existingSheetObject] with [sheet1].
     * If [sheet1] does not exist it will be automatically created.
     * After linkage, operations on [sheet1] will also be performed on [existingSheetObject] and vice-versa.
     */
    public fun link(sheet1: String, existingSheetObject: Sheet) {
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
    public fun unLink(sheet: String) {
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
    public fun copy(fromSheet: String, toSheet: String) {
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
    public fun rename(oldSheetName: String, newSheetName: String) {
        val sheet = sheetMap[oldSheetName] ?: return
        if (oldSheetName == newSheetName || sheetMap[newSheetName] != null) return
        requireValidSheetName(newSheetName, except = oldSheetName)

        valuesEdited = true
        if (defaultSheet == oldSheetName) {
            defaultSheet = newSheetName
        }

        // The sheet keeps its place among the tabs and everything it holds. This used to
        // be a copy followed by a delete: the name then went to a brand-new worksheet part
        // at the end of the tab list, and the freeze panes, data validations, conditional
        // formats, hyperlinks and column settings of the old part were left behind.
        val entries = sheetMap.entries.map { it.key to it.value }
        sheetMap.clear()
        entries.forEach { (name, value) -> sheetMap[if (name == oldSheetName) newSheetName else name] = value }
        if (sheet.sheetName == oldSheetName) sheet.renameTo(newSheetName)

        sheets.remove(oldSheetName)?.let { sheets[newSheetName] = it }
        xmlSheetId.remove(oldSheetName)?.let { xmlSheetId[newSheetName] = it }
        cellStyleReferenced.remove(oldSheetName)?.let { cellStyleReferenced[newSheetName] = it }
        mergeChangeLook.replaceAll { if (it == oldSheetName) newSheetName else it }
        rtlChangeLook.replaceAll { if (it == oldSheetName) newSheetName else it }

        val workbook = xmlFiles["xl/workbook.xml"]
        workbook?.getElementsByTag("sheet")?.firstOrNull { it.attr("name") == oldSheetName }
            ?.setAttribute("name", newSheetName)

        // What refers to the sheet by name follows it, as in POI's XSSFWorkbook.setSheetName:
        // formulas in every sheet, and defined names (print areas, named ranges).
        sheetMap.values.toSet().forEach { other ->
            other.sheetData.values.forEach { row ->
                row.values.forEach { data ->
                    val value = data.value as? FormulaCellValue ?: return@forEach
                    val renamed = renameSheetReferences(value.formula, oldSheetName, newSheetName)
                    if (renamed != value.formula) data._value = FormulaCellValue(renamed).also { it.source = value.source }
                }
            }
        }
        workbook?.getElementsByTag("definedName")?.forEach { definedName ->
            val text = definedName.wholeOwnText()
            val renamed = renameSheetReferences(text, oldSheetName, newSheetName)
            if (renamed != text) definedName.text(renamed)
        }
    }

    /**
     * Excel's rules for a sheet name (the same ones POI enforces in
     * `WorkbookUtil.validateSheetName`): 1 to 31 characters, none of `/ \ ? * [ ] :`, no
     * apostrophe at either end, and unique without regard to case. A workbook that breaks
     * them is one Excel refuses to open or repairs.
     */
    private fun requireValidSheetName(name: String, except: String? = null) {
        require(name.isNotEmpty() && name.length <= 31) { "A sheet name must have 1 to 31 characters, was ${name.length}: `$name`" }
        require(name.none { it in "/\\?*[]:" }) { "A sheet name must not contain any of / \\ ? * [ ] : — was `$name`" }
        require(!name.startsWith('\'') && !name.endsWith('\'')) { "A sheet name must not begin or end with an apostrophe: `$name`" }
        val clash = sheetMap.keys.firstOrNull { it != except && it.equals(name, ignoreCase = true) }
        require(clash == null) { "A sheet named `$clash` already exists; sheet names differ by more than case" }
    }

    /**
     * If [sheet] exists in `tables.keys` and `tables.keys.size >= 2`, it will be deleted.
     */
    public fun delete(sheet: String) {
        if (sheetMap.size <= 1) return
        valuesEdited = true

        if (defaultSheet == sheet) {
            defaultSheet = null
        }

        sheetMap.remove(sheet)
        mergeChangeLook.remove(sheet)
        rtlChangeLook.remove(sheet)

        xmlSheetId[sheet]?.let { sheetPath ->
            xmlFiles["xl/_rels/workbook.xml.rels"]
                ?.getElementsByTag("Relationship")
                ?.filter { child -> resolveWorkbookRelTarget(child.attr("Target")) == sheetPath }
                ?.forEach { it.remove() }

            xmlFiles["[Content_Types].xml"]
                ?.getElementsByTag("Override")
                ?.filter { child -> child.attr("PartName") == "/$sheetPath" }
                ?.forEach { it.remove() }

            xmlFiles.remove(xmlSheetId[sheet])

            archive = cloneArchive(
                archive,
                xmlFiles.map { (k, v) ->
                    val encoded = serializeXml(v).encodeToByteArray()
                    k to ArchiveFile(k, encoded.size, encoded)
                }.toMap(),
                excludedFile = xmlSheetId[sheet]
            )

            xmlSheetId.remove(sheet)
        }

        if (sheets[sheet] != null) {
            val workbook = xmlFiles["xl/workbook.xml"]
            val sheetElements = workbook?.getElementsByTag("sheets")?.firstOrNull()?.children().orEmpty()
            val position = sheetElements.indexOfFirst { element -> element.attr("name") == sheet }
            sheetElements.filter { element -> element.attr("name") == sheet }.forEach { it.remove() }
            sheets.remove(sheet)
            if (workbook != null && position >= 0) forgetSheetPosition(workbook, position, sheetElements.size - 1)
        }

        cellStyleReferenced.remove(sheet)
    }

    /**
     * Brings what refers to sheets by *position* in line after the sheet at [position] has
     * gone, leaving [remaining] sheets. `localSheetId` scopes a defined name to a sheet
     * (§18.2.5) and `activeTab` / `firstSheet` name tabs (§18.2.30); all three are indexes
     * into `<sheets>`. Left alone, a print area ended up attached to the next sheet, or
     * pointed past the end of the list.
     */
    private fun forgetSheetPosition(workbook: Document, position: Int, remaining: Int) {
        workbook.getElementsByTag("definedName").toList().forEach { definedName ->
            val scope = definedName.attr("localSheetId").trim().toIntOrNull() ?: return@forEach
            when {
                scope == position -> definedName.remove()
                scope > position -> definedName.setAttribute("localSheetId", (scope - 1).toString())
            }
        }
        // <definedNames> may not be empty.
        workbook.getElementsByTag("definedNames").toList().filter { it.children().isEmpty() }.forEach { it.remove() }

        workbook.getElementsByTag("workbookView").forEach { view ->
            for (attribute in listOf("activeTab", "firstSheet")) {
                val tab = view.attr(attribute).trim().toIntOrNull() ?: continue
                val moved = if (tab > position) tab - 1 else tab
                view.setAttribute(attribute, moved.coerceIn(0, maxOf(remaining - 1, 0)).toString())
            }
        }
    }

    /**
     * Sets the edited values of [sheetMap] into the files and exports the file.
     */
    public fun encode(): ByteArray? {
        val s = Save(this, parser)
        return s.save()
    }

    /**
     * Returns the name of the default sheet (the sheet which opens first when the xlsx file is opened).
     */
    public fun getDefaultSheet(): String? {
        return defaultSheet ?: getDefaultSheetInternal()
    }

    /**
     * Internal function which returns the default sheet name by reading from `workbook.xml`.
     */
    internal fun getDefaultSheetInternal(): String? {
        val elements = xmlFiles["xl/workbook.xml"]?.getElementsByTag("sheet")
        // The first worksheet: a chart sheet may come first, but it is not in the model.
        val sheet = elements?.firstOrNull { sheetMap.containsKey(it.attr("name")) } ?: elements?.firstOrNull()

        return if (sheet != null) {
            val name = sheet.attribute("name")?.value
            name ?: damagedExcel(text = "Excel sheet corrupted!! Try creating new excel file.")
        } else null
    }

    /**
     * Returns `true` if [sheetName] is successfully set as the default opening sheet, otherwise `false`.
     */
    public fun setDefaultSheet(sheetName: String): Boolean {
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
    public fun insertColumn(sheet: String, columnIndex: Int) {
        if (columnIndex < 0) return
        availSheet(sheet)
        sheetMap[sheet]!!.insertColumn(columnIndex)
    }

    /**
     * If [sheet] exists and [columnIndex] < maxColumns, removes column at [columnIndex].
     */
    public fun removeColumn(sheet: String, columnIndex: Int) {
        if (columnIndex >= 0 && sheetMap[sheet] != null) {
            sheetMap[sheet]!!.removeColumn(columnIndex)
        }
    }

    /**
     * Inserts an empty row in [sheet] at position [rowIndex].
     * If [rowIndex] < 0 it will not execute.
     * If [sheet] does not exist it will be created automatically.
     */
    public fun insertRow(sheet: String, rowIndex: Int) {
        if (rowIndex < 0) return
        availSheet(sheet)
        sheetMap[sheet]!!.insertRow(rowIndex)
    }

    /**
     * If [sheet] exists and [rowIndex] < maxRows, removes row at [rowIndex].
     */
    public fun removeRow(sheet: String, rowIndex: Int) {
        if (rowIndex >= 0 && sheetMap[sheet] != null) {
            sheetMap[sheet]!!.removeRow(rowIndex)
        }
    }

    /**
     * Appends [row] iterables just after the last filled index in [sheet].
     * If [sheet] does not exist it will be automatically created.
     */
    public fun appendRow(sheet: String, row: List<CellValue?>) {
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
    public fun insertRowIterables(
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
    public fun findAndReplace(
        sheet: String,
        source: Regex, // Regex or String
        target: String,
        first: Int = -1,
        startingRow: Int = -1,
        endingRow: Int = -1,
        startingColumn: Int = -1,
        endingColumn: Int = -1
    ): Int {
        if (sheetMap[sheet] == null) return 0

        return sheetMap[sheet]!!.findAndReplace(
            source,
            target,
            first = first,
            startingRow = startingRow,
            endingRow = endingRow,
            startingColumn = startingColumn,
            endingColumn = endingColumn
        )
    }

    /**
     * Makes [sheet] available in [sheetMap] if it doesn't already exist.
     */
    internal fun availSheet(sheet: String) {
        if (sheetMap[sheet] == null) {
            requireValidSheetName(sheet)
            sheetMap[sheet] = Sheet(this, sheet)
        }
    }

    /**
     * Updates the contents of [sheet] at [cellIndex].
     * Indexing starts from 0; e.g. `CellIndex.indexByColumnRow(0, 0)` or `CellIndex.indexByString("A3")`.
     * If [sheet] does not exist it will be automatically created.
     */
    public fun updateCell(
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
    public fun merge(
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
    public fun getMergedCells(sheet: String): List<String> {
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
    public fun unMerge(sheet: String, unmergeCells: String) {
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

private fun decodeExcel(archive: Archive): Excel {
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