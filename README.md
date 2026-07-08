# Kexcel

[![latest version](https://img.shields.io/maven-central/v/com.gyanoba.kexcel/kexcel?color=blue&label=Version)](https://central.sonatype.com/artifact/com.gyanoba.kexcel/kexcel)

> ⚠️ **Experimental** — Kexcel is under active development. The API is unstable and may change without notice.

A **Kotlin Multiplatform** library for working with Excel (`.xlsx`) files. Written in **pure Kotlin** with no platform-specific dependencies.

Kexcel is a Kotlin port of the Dart [`excel`](https://github.com/justkawal/excel) library by [justkawal](https://github.com/justkawal).

## Installation

Add Kexcel to your project.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.gyanoba.kexcel:kexcel:0.0.1")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.gyanoba.kexcel:kexcel:0.0.1'
}
```

## Getting Started

The `Excel` class is the entry point. Use its companion object to create or load a workbook:

```kotlin
// Create a new, empty workbook
val excel = Excel.createExcel()

// Load a workbook from a ByteArray
val excel = Excel.decodeBytes(bytes)

// Load a workbook from an InputStream
val excel = Excel.decodeStream(inputStream)
```

| Method | Description |
| --- | --- |
| `Excel.createExcel(): Excel` | Creates a new, empty Excel workbook. |
| `Excel.decodeBytes(data: ByteArray): Excel` | Loads a workbook from raw `.xlsx` bytes. |
| `Excel.decodeStream(input: InputStream): Excel` | Loads a workbook from an input stream. |

## Documentation

📚 Full documentation is coming soon.

## Development

### Run Sample App

- Desktop JVM: `./gradlew :sample:desktopApp:run`
- Android: Open the project in Android Studio and run the `sample/androidApp` configuration.
- iOS: Open `sample/iosApp/iosApp.xcodeproj` in Xcode and run.
