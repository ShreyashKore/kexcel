import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    // This is a library: require explicit visibility & return types on public API.
    explicitApi()

    androidTarget {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
//    js { browser() }
//    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
//    macosArm64()
//    linuxX64()
//    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.kmp.zip)
            implementation(libs.kmp.zip.kotlinx)
            implementation(libs.ksoup)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }

    }
}

android {
    namespace = "com.gyanoba.kexcel.kexcel"
    compileSdk = 37
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Publishing to Maven Central. Coordinates are set here; the POM metadata
// (name, description, url, license, developer, scm) comes from the POM_* keys
// in gradle.properties, read automatically by the maven-publish plugin.
mavenPublishing {
    publishToMavenCentral()
    coordinates("com.gyanoba.kexcel", "kexcel", "0.0.2")
    // Maven Central rejects unsigned releases. Sign whenever credentials are
    // present: `signing.keyId` for a local GPG keyring (see README) or
    // `signingInMemoryKey` from the ORG_GRADLE_PROJECT_* env vars used in CI.
    if (project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }
}
