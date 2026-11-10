# Fixtures from Apache POI

These workbooks are copied unchanged from the Apache POI test data
(`reference/poi/test-data/spreadsheet`, tag `REL_5_4_1`), which is licensed under the
Apache License 2.0. They are files that real producers wrote and that POI's own suite
uses as regression inputs, so they exercise shapes Kexcel would never write itself.

`PoiCorpusRoundTripTest` reads each one, saves it without edits, and lets POI compare the
result with the original. Add a file here when a defect is found in the corpus, and name
the finding from `AUDIT-FINDINGS.md` next to it in that test.
