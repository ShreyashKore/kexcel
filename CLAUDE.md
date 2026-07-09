# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Kexcel is a **Kotlin Multiplatform** library for reading and writing Excel (`.xlsx`) files, written in **pure Kotlin** with no platform-specific dependencies. It is a Kotlin port of the Dart [`excel`](https://github.com/justkawal/excel) library by justkawal.

The library lives in the `:kexcel` module. Everything under `sample/` is demo apps that consume it and is not part of the published artifact.

Published targets: JVM, Android, iOS (`iosArm64`, `iosSimulatorArm64`). `wasmJs` and `macosArm64` are active build targets in `kexcel/build.gradle.kts` but are not part of the published Maven artifact. JS, `linuxX64`, and `mingwX64` are commented out — enabling one means uncommenting it in both `kexcel/build.gradle.kts` and the relevant `sample/*/build.gradle.kts`.

The repo and Maven artifact are both `kexcel` (`github.com/ShreyashKore/kexcel`); docs/site URLs point there.

## Commands

All commands use the Gradle wrapper (`./gradlew`).

**Build the library:** `./gradlew :kexcel:build`

**Tests:**
- All targets: `./gradlew :kexcel:allTests`
- Common + JVM tests on the JVM (fastest for iterating): `./gradlew :kexcel:jvmTest`
- Android unit tests: `./gradlew :kexcel:testDebugUnitTest`
- A single test class: `./gradlew :kexcel:jvmTest --tests "com.gyanoba.kexcel.ExcelFileTest"`
- A single test method: `./gradlew :kexcel:jvmTest --tests "com.gyanoba.kexcel.ExcelFileTest.<methodName>"`

Note `commonTest` (`ExcelInMemoryTest`) runs on every target; `jvmTest` (`ExcelFileTest`, reads/writes real files) runs only on the JVM.

**Run the sample apps:**
- Desktop (Compose): `./gradlew :sample:desktopApp:run`
- Android: open the project in Android Studio and run `sample/androidApp`
- iOS: open `sample/iosApp/iosApp.xcodeproj` in Xcode and run

**Publish:**
- Local Maven (`~/.m2`): `./gradlew :kexcel:publishToMavenLocal`
- Maven Central: `./gradlew :kexcel:publishAndReleaseToMavenCentral --no-configuration-cache` (requires GPG signing keys and Sonatype credentials in `gradle.properties`; see README)

**Docs:**
- Generate the API reference (Dokka HTML → `kexcel/build/dokka/html`): `./gradlew :kexcel:dokkaGenerate`
- Preview the guides (needs `pip install -r requirements.txt`): `mkdocs serve`
- Full-site build the way CI does it: `./gradlew :kexcel:dokkaGenerate && mkdir -p docs/api && cp -r kexcel/build/dokka/html/. docs/api/ && mkdocs build --strict`

## API conventions

The `:kexcel` module enables `explicitApi()`. **All public declarations require explicit visibility modifiers and explicit return types** — the build fails otherwise. Internal machinery is marked `internal`; the public surface is deliberately small.

## Architecture

An `.xlsx` file is a ZIP archive of XML parts. Kexcel reads it into an in-memory object graph, lets you mutate it, then serializes back to a ZIP. Two third-party libraries do the heavy lifting: **`no.synth:kmp-zip`** for ZIP I/O and **`com.fleeksoft.ksoup`** for XML parsing/DOM.

**`Excel` (`Excel.kt`) is the single entry point and the mutable document state.** Its companion object exposes the only public constructors: `createExcel()` (blank workbook from an embedded template), `decodeBytes(ByteArray)`, and `decodeStream(InputStream)`. The `Excel` instance holds the parsed XML documents (`xmlFiles`), the `Sheet` objects (`sheetMap`), shared strings, and the style/font/border/number-format tables. All sheet-level operations (`updateCell`, `insertRow`, `merge`, `findAndReplace`, `copy`, `rename`, `delete`, …) are methods on `Excel` that delegate to the relevant `Sheet`.

The lifecycle is a three-stage pipeline, each stage owning a package:

1. **Parse (read path) — `parser/Parser.kt`.** Constructed by `Excel`'s `init` block, `startParsing()` walks the archive: `[Content_Types].xml` → workbook relationships → `styles.xml` → `sharedStrings.xml` → each worksheet → merged cells. It populates the `Excel` object's maps and builds `Sheet` instances.

2. **Model / edit — `sheet/`.** `Sheet` stores cells as a nested `Map<row, Map<col, Data>>`. Each cell's value is a `CellValue` sealed hierarchy (`TextCellValue`, `IntCellValue`, `DoubleCellValue`, `BoolCellValue`, `DateCellValue`, `DateTimeCellValue`, `TimeCellValue`, `FormulaCellValue`) plus optional `CellStyle` (which references `FontStyle`, `BorderSet`, and number formats). `CellIndex` converts between `"A3"` strings and 0-based `(column, row)` coordinates. Sheets can be linked/cloned; `Sheet.clone` gives an independent copy.

3. **Save (write path) — `save/SaveFile.kt`.** `Excel.encode()` (the only public save API, returning `ByteArray?`) runs the `Save`/`SaveFile` machinery, which mutates the ksoup XML documents to reflect the current model, then re-zips everything into a `ByteArray`. Writing those bytes to a file is left to the caller — the library does no filesystem I/O, which is what keeps it fully multiplatform. (`save/SelfCorrectSpan.kt` reconciles overlapping merged regions during this step.)

**Supporting packages:**
- `archive/` — the ZIP/stream abstraction (`Archive`, `ArchiveFile`, input/output memory streams) that both parse and save build on.
- `shared_strings/` — the workbook's deduplicated string pool.
- `number_format/` — number/date/time format types and the `NumFormatMaintainer` registry.
- `utils/` — cross-cutting helpers: coordinate/color conversion, enums, constants (including the `NEW_SHEET` base64 template used by `createExcel()`), and `damagedExcel()` for corrupt-file errors.

When adding behavior, the recurring pattern is: extend the `Sheet`/`CellValue` model, teach `Parser` to read the corresponding XML on load, and teach `Save` to emit it — the read and write paths must stay symmetric.

## Documentation site

The project ships a documentation site built from two toolchains and deployed to GitHub Pages by GitHub Actions:

- **Material for MkDocs** renders the prose guides. Config is `mkdocs.yml` (repo root); pages are Markdown under `docs/`; the Python deps are in `requirements.txt`. Nav is grouped into *Getting Started*, *Guides*, *Concepts*, *Contributing*, and an *API Reference* link.
- **Dokka** (v2 Gradle plugin, `org.jetbrains.dokka`) generates the API reference from KDoc. It is wired up in `kexcel/build.gradle.kts` (source links point at the GitHub repo; the intro/module doc is `kexcel/Module.md`). Dokka v2 is enabled via the `org.jetbrains.dokka.experimental.gradle.pluginMode=V2Enabled` flag in `gradle.properties`.
- **GitHub Actions** (`.github/workflows/docs.yaml`) runs `dokkaGenerate`, copies the HTML into `docs/api/`, runs `mkdocs build --strict`, and publishes with `actions/deploy-pages`. It only deploys from the `ShreyashKore/kexcel` repo. One-time setup: repo Settings → Pages → Source = "GitHub Actions".

Key detail for editing docs: the site uses `use_directory_urls`, so a page at `docs/guides/foo.md` is served at `/guides/foo/`. Markdown links **between** pages are normal relative `.md` links (e.g. `../guides/styling.md`), but links **into the Dokka reference** must be depth-correct relative paths to the static `api/` dir — `api/index.html` from a root-level page, `../../api/index.html` from a page nested one folder deep (guides, getting-started, concepts). Generated dirs `site/`, `docs/api/`, and `.cache/` are git-ignored.

Doc content is grounded in the real public API — when the API changes, update the affected page under `docs/` (and the KDoc, which flows into Dokka). The sample tour at `sample/sharedUI/src/commonMain/kotlin/sample/app/ExcelSamples.kt` is written to double as documentation and mirrors the guide topics.
