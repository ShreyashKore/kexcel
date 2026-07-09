# Contributing

Kexcel is a Kotlin Multiplatform library. The library itself lives in the
`:kexcel` module; everything under `sample/` is demo apps that consume it and is
**not** part of the published artifact.

## Building & testing

All commands use the Gradle wrapper (`./gradlew`).

| Task | Command |
| --- | --- |
| Build the library | `./gradlew :kexcel:build` |
| All tests, every target | `./gradlew :kexcel:allTests` |
| Common + JVM tests (fastest to iterate) | `./gradlew :kexcel:jvmTest` |
| Android unit tests | `./gradlew :kexcel:testDebugUnitTest` |
| A single test class | `./gradlew :kexcel:jvmTest --tests "com.gyanoba.kexcel.ExcelFileTest"` |
| A single test method | `./gradlew :kexcel:jvmTest --tests "…ExcelFileTest.<method>"` |

`commonTest` (`ExcelInMemoryTest`) runs on every target; `jvmTest`
(`ExcelFileTest`, which reads/writes real files) runs only on the JVM.

## Running the sample app

The sample runs each API example and prints the results:

- **Desktop (Compose):** `./gradlew :sample:desktopApp:run`
- **Android:** open the project in Android Studio and run `sample/androidApp`.
- **iOS:** open `sample/iosApp/iosApp.xcodeproj` in Xcode and run.

The tour lives in `sample/sharedUI/src/commonMain/kotlin/sample/app/ExcelSamples.kt`
and is written to double as documentation — read it top to bottom.

## API conventions

The `:kexcel` module enables `explicitApi()`. **All public declarations require
explicit visibility modifiers and explicit return types** — the build fails
otherwise. Internal machinery is marked `internal`; the public surface is
deliberately small.

When adding behavior, keep the read and write paths symmetric: extend the
`Sheet` / `CellValue` model, teach the parser to read the corresponding XML, and
teach save to emit it. See [How Kexcel Works](concepts/architecture.md).

## Documentation

This site is built with **Material for MkDocs** (guides) and **Dokka** (API
reference), and deployed to GitHub Pages by a GitHub Actions workflow.

### Preview the guides locally

```bash
pip install -r requirements.txt
mkdocs serve
```

Then open <http://127.0.0.1:8000>. (The API reference is generated separately, so
the `API Reference` link 404s under `mkdocs serve` unless you generate it first —
see below.)

### Generate the API reference locally

```bash
./gradlew :kexcel:dokkaGenerate
# output: kexcel/build/dokka/html/

# To preview it inside the site, copy it in before serving/building:
mkdir -p docs/api && cp -r kexcel/build/dokka/html/. docs/api/
```

`docs/api/` is git-ignored; the CI workflow regenerates it on every deploy.

## Publishing

- Local Maven (`~/.m2`): `./gradlew :kexcel:publishToMavenLocal`
- Maven Central: `./gradlew :kexcel:publishAndReleaseToMavenCentral --no-configuration-cache`
  (requires GPG signing keys and Sonatype credentials).
