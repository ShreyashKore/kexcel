# AGENTS.md

Guidance for AI coding agents working in this repository. It mirrors
[`CLAUDE.md`](CLAUDE.md) — keep the two in sync when you change either. `CLAUDE.md`
holds the fuller architecture deep-dive; this file is a self-contained quick
reference.

## What this is

Kexcel is a **Kotlin Multiplatform** library for reading and writing Excel
(`.xlsx`) files, in **pure Kotlin** with no platform-specific dependencies. It is a
Kotlin port of the Dart [`excel`](https://github.com/justkawal/excel) library.

- The published library is the **`:kexcel`** module. Everything under `sample/` is
  demo apps that consume it and is **not** part of the published artifact.
- Published targets: JVM, Android, iOS (`iosArm64`, `iosSimulatorArm64`).
  `wasmJs` and `macosArm64` are built but not published; JS / `linuxX64` /
  `mingwX64` are commented out in the build.
- The repo and Maven artifact are both `kexcel` (`github.com/ShreyashKore/kexcel`).

## Commands

All commands use the Gradle wrapper (`./gradlew`).

| Task | Command |
| --- | --- |
| Build the library | `./gradlew :kexcel:build` |
| All tests (every target) | `./gradlew :kexcel:allTests` |
| Common + JVM tests (fastest) | `./gradlew :kexcel:jvmTest` |
| Android unit tests | `./gradlew :kexcel:testDebugUnitTest` |
| Single test class | `./gradlew :kexcel:jvmTest --tests "com.gyanoba.kexcel.ExcelFileTest"` |
| Single test method | `./gradlew :kexcel:jvmTest --tests "…ExcelFileTest.<method>"` |
| Run desktop sample | `./gradlew :sample:desktopApp:run` |
| Publish to local Maven | `./gradlew :kexcel:publishToMavenLocal` |
| Generate API reference (Dokka) | `./gradlew :kexcel:dokkaGenerate` |
| Preview docs guides | `pip install -r requirements.txt && mkdocs serve` |

`commonTest` (`ExcelInMemoryTest`) runs on every target; `jvmTest`
(`ExcelFileTest`, real file I/O) runs only on the JVM. Android sample runs from
Android Studio; iOS from `sample/iosApp/iosApp.xcodeproj` in Xcode.

## Conventions

- The `:kexcel` module enables **`explicitApi()`**. All public declarations
  **require explicit visibility modifiers and explicit return types** — the build
  fails otherwise. Mark internal machinery `internal`; keep the public surface
  small.
- Match the surrounding code's style, naming and comment density.
- Read and write paths must stay **symmetric**: to add a feature, extend the
  `Sheet`/`CellValue` model, teach `parser/Parser.kt` to read the XML, and teach
  `save/SaveFile.kt` to emit it.
- Never commit `deploy.sh` — it is git-ignored and contains Maven Central signing
  secrets.

## How it works (short version)

An `.xlsx` file is a ZIP of XML parts. Kexcel parses it into an in-memory object
graph, lets you mutate it, then re-serializes to a ZIP. `no.synth:kmp-zip` does
ZIP I/O; `com.fleeksoft.ksoup` does XML/DOM.

`Excel` (`Excel.kt`) is the single entry point and the mutable document state.
Public constructors: `createExcel()`, `decodeBytes(ByteArray)`,
`decodeStream(InputStream)`. Save with `encode(): ByteArray?` (no filesystem I/O —
callers write the bytes). Three-stage pipeline: **parse** (`parser/`) →
**model/edit** (`sheet/`) → **save** (`save/`). See `CLAUDE.md` for the full
version.

## Project layout

```
├─ build.gradle.kts            root build (plugins applied `false`)
├─ settings.gradle.kts         module includes (webApp/terminalApp commented out)
├─ gradle.properties           Gradle + POM + Dokka V2 flag
├─ gradle/libs.versions.toml   version catalog
├─ mkdocs.yml                  Material for MkDocs config (docs site)
├─ requirements.txt            MkDocs Python deps
├─ deploy.sh                   git-ignored; Maven Central signing secrets
├─ .github/workflows/
│  ├─ publish.yaml             CI: tests, sample build, Maven Central on `v*` tags
│  └─ docs.yaml                CI: build + deploy docs to GitHub Pages
├─ docs/                       MkDocs Markdown sources
│  ├─ index.md
│  ├─ getting-started/         installation.md, quick-start.md
│  ├─ guides/                  workbooks, reading-and-writing, styling, number-formats,
│  │                          formulas, rows-and-columns, merging, sizing,
│  │                          find-and-replace, saving
│  ├─ concepts/architecture.md
│  └─ contributing.md
├─ kexcel/                     the published library module
│  ├─ build.gradle.kts         KMP + Android + Dokka + maven-publish
│  ├─ Module.md                Dokka module/package docs
│  └─ src/
│     ├─ commonMain/kotlin/com/gyanoba/kexcel/
│     │  ├─ Excel.kt           entry point + document state
│     │  ├─ parser/            Parser.kt (read path)
│     │  ├─ sheet/             Sheet, Data, CellValue, CellStyle, CellIndex,
│     │  │                     BorderStyle, FontStyle, HeaderFooter
│     │  ├─ save/              SaveFile.kt (Save), SelfCorrectSpan.kt
│     │  ├─ archive/           ZIP/stream abstraction
│     │  ├─ shared_strings/    deduplicated string pool
│     │  ├─ number_format/     NumFormat + NumFormatMaintainer
│     │  └─ utils/             ExcelColor (Colors.kt), Enums, Constants, Span, Utility
│     ├─ commonTest/…/ExcelInMemoryTest.kt   runs on every target
│     └─ jvmTest/…/ExcelFileTest.kt          JVM-only; test_resources/*.xlsx
└─ sample/                     demo apps — NOT published
   ├─ sharedUI/                Compose UI + ExcelSamples.kt (runnable API tour)
   ├─ androidApp/  desktopApp/  iosApp/       active
   └─ webApp/  terminalApp/                    excluded in settings.gradle.kts
```

## Documentation site

Guides are **Material for MkDocs** (`mkdocs.yml` + `docs/`); the API reference is
**Dokka v2** (`org.jetbrains.dokka`, configured in `kexcel/build.gradle.kts`,
intro in `kexcel/Module.md`, enabled via the
`org.jetbrains.dokka.experimental.gradle.pluginMode=V2Enabled` flag in
`gradle.properties`). `.github/workflows/docs.yaml` runs `dokkaGenerate`, copies
the HTML into `docs/api/`, runs `mkdocs build --strict`, and deploys with
`actions/deploy-pages`.

When editing docs, remember the site uses `use_directory_urls`: links between
Markdown pages are normal relative `.md` links, but links into the static Dokka
`api/` dir must be depth-correct (`api/index.html` from root pages,
`../../api/index.html` from pages one folder deep). `site/`, `docs/api/`, and
`.cache/` are git-ignored. Keep doc content in sync with the real public API and
its KDoc.
