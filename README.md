# Kexcel

> ⚠️ **Experimental** — Kexcel is under active development. The API is unstable and may change without notice.

A **Kotlin Multiplatform** library for working with Excel (`.xlsx`) files. Written in **pure Kotlin** with no platform-specific dependencies.

Kexcel is a Kotlin port of the Dart [`excel`](https://github.com/justkawal/excel) library by [justkawal](https://github.com/justkawal).

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

 - Desktop JVM: `./gradlew :sample:composeApp:run`
 - Android: `open project in Android Studio and run the sample app`
 - iOS: `open 'sample/iosApp/iosApp.xcodeproj' in Xcode and run the sample app`
 - JavaScript: `./gradlew :sample:composeApp:jsBrowserDevelopmentRun`
 - Wasm: `./gradlew :sample:composeApp:wasmJsBrowserDevelopmentRun`
 - Linux/Macos/Windows native: `./gradlew :sample:terminalApp:runDebugExecutable[architecture]`