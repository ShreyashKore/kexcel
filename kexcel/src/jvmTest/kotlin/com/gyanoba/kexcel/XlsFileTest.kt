package com.gyanoba.kexcel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Old `.xls` binary files must be rejected with a clear error, not mis-parsed.
 */
class XlsFileTest {

    // Dart: 'Exception when opening old .xls file'
    @Test
    fun exceptionWhenOpeningOldXls() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            Excel.decodeBytes(fixture("oldXLSFile.xls"))
        }
        assertEquals(ex.message?.contains("Only .xlsx files are supported"), true)
    }

    // Dart: 'Exception when opening new .xls file'
    @Test
    fun exceptionWhenOpeningNewXls() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            Excel.decodeBytes(fixture("newXLSFile.xls"))
        }
        assertEquals(ex.message?.contains("Only .xlsx files are supported"), true)
    }
}