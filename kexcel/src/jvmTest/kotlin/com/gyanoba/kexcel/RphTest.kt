package com.gyanoba.kexcel

import com.gyanoba.kexcel.sheet.TextCellValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cells carrying `rPh` (phonetic hint) runs must read and save without error.
 */
class RphTest {

    // Dart: 'Read Cell shared text without rPh elements'
    @Test
    fun readCellSharedTextWithoutRph() {
        val t = Excel.decodeBytes(fixture("rphSample.xlsx")).tables["Sheet1"]!!
        assertEquals("plainText", t.rows[1][0]!!.value.toString())
        assertEquals("Hellow world", t.rows[1][1]!!.value.toString())
        assertEquals("世界よこんにちは", t.rows[1][2]!!.value.toString())
        assertEquals("ようこそユーザー", t.rows[2][2]!!.value.toString())
        assertEquals("ロケール選択", t.rows[3][2]!!.value.toString())
        assertEquals("ロケール選択", t.rows[4][2]!!.value.toString())
    }

    // Dart: 'saving XLSX File without rPh elements'
    @Test
    fun savingXlsxWithoutRph() {
        val excel = Excel.decodeBytes(fixture("rphSample.xlsx"))
        excel.tables["Sheet1"]!!.rows[3][2]!!.value = TextCellValue("ロケール選択")
        val newExcel = Excel.decodeBytes(excel.encode()!!)
        assertEquals("ロケール選択", newExcel.tables["Sheet1"]!!.rows[3][2]!!.value.toString())
    }
}