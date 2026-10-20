package com.gyanoba.kexcel

import kotlin.test.Test

/**
 * Reading spanned (merged) items from a fixture and re-saving them.
 */
class SpannedItemsTest {

    // Dart: 'Spanned Items' group -> 'read spanned items'
    @Test
    fun readSpannedItems() {
        val excel = Excel.decodeBytes(fixture("spannedItemExample.xlsx"))
        val sheet = excel.tables["Spanned Items"]!!
        assertSpannedItemsList(sheet)
        assertSpannedItemsSheetValues(sheet)

        val newExcel = Excel.decodeBytes(excel.encode()!!)
        val newSheet = newExcel.tables["Spanned Items"]!!
        assertSpannedItemsList(newSheet)
        assertSpannedItemsSheetValues(newSheet)
    }
}