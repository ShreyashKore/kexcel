package com.gyanoba.kexcel

import kotlin.test.Test

/**
 * Real workbooks from the Apache POI test data, saved by Kexcel **without an edit** and
 * compared with the original by POI.
 *
 * A round trip through Kexcel's own reader cannot see a property Kexcel does not model:
 * if the reader drops it, the writer never knew it was there, and both agree. POI can see
 * it. Each fixture was chosen because it caught a defect (the finding is named beside it,
 * from `AUDIT-FINDINGS.md`); the `known` sets list what a file still loses, and
 * [assertOnlyDiffers] fails when an entry there stops being true, so they can only shrink.
 */
class PoiCorpusRoundTripTest {

    private fun assertLossless(name: String, known: Set<String> = emptySet()) {
        val original = fixture("poi/$name")
        assertOnlyDiffers(original, roundTrip(original), expected = known, what = name)
    }

    // D-01: <x:workbook>, <x:sst>, <x:worksheet> — the main namespace bound to a prefix.
    @Test
    fun aWorkbookWithPrefixedPartsSurvives() = assertLossless(
        "59746_NoRowNums.xlsx",
        known = setOf("row: hidden", "row: outline level"), // F-04
    )

    // D-01: <x:worksheet> with a rich inline string.
    @Test
    fun aPrefixedWorksheetWithInlineStringsSurvives() = assertLossless("InlineString.xlsx")

    // D-01: <ns0:Relationships>, as Python's ElementTree writes it.
    @Test
    fun prefixedRelationshipsSurvive() = assertLossless("58760.xlsx")
}
