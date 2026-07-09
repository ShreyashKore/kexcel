# Installation

Kexcel is published to **Maven Central** under the coordinates
`com.gyanoba.kexcel:kexcel`.

[![Maven Central](https://img.shields.io/maven-central/v/com.gyanoba.kexcel/kexcel?color=blue&label=latest%20version)](https://central.sonatype.com/artifact/com.gyanoba.kexcel/kexcel)

## Add the dependency

=== "Kotlin Multiplatform"

    Add Kexcel to your `commonMain` source set:

    ```kotlin title="build.gradle.kts"
    kotlin {
        sourceSets {
            commonMain.dependencies {
                implementation("com.gyanoba.kexcel:kexcel:0.0.2")
            }
        }
    }
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin title="build.gradle.kts"
    dependencies {
        implementation("com.gyanoba.kexcel:kexcel:0.0.2")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy title="build.gradle"
    dependencies {
        implementation 'com.gyanoba.kexcel:kexcel:0.0.2'
    }
    ```

=== "Version catalog"

    ```toml title="gradle/libs.versions.toml"
    [versions]
    kexcel = "0.0.2"

    [libraries]
    kexcel = { module = "com.gyanoba.kexcel:kexcel", version.ref = "kexcel" }
    ```

    ```kotlin title="build.gradle.kts"
    dependencies {
        implementation(libs.kexcel)
    }
    ```

!!! tip "Check for the latest version"
    Always prefer the version shown by the badge above — this page may lag behind
    the newest release.

## Make sure Maven Central is a repository

Kexcel and its transitive dependencies all live on Maven Central, so it must be
declared in your repositories:

```kotlin title="settings.gradle.kts"
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // if you also target Android
    }
}
```

## Requirements

| Requirement | Value |
| --- | --- |
| Kotlin | 2.0+ (the library is built with 2.3.x) |
| JVM / Android | Java 17 bytecode; Android `minSdk` 23 |
| iOS | `iosArm64`, `iosSimulatorArm64` |

## Transitive dependencies

Kexcel pulls in a few pure-Kotlin libraries for you — you do **not** need to add
them yourself:

- [`no.synth:kmp-zip`](https://github.com/gaelmarhic/kmp-zip) — ZIP archive I/O.
- [`com.fleeksoft.ksoup`](https://github.com/fleeksoft/ksoup) — XML parsing / DOM.
- `kotlinx-coroutines`, `kotlinx-serialization`, `kotlinx-datetime`.

## Next step

Head to the [Quick Start](quick-start.md) to build your first workbook.
