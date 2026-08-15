plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.dokka).apply(false)
}

// The published version is written down once, as VERSION_NAME in
// gradle.properties. `:kexcel`'s mavenPublishing block reads it for the Maven
// coordinates; the Markdown install snippets can't read Gradle properties, so
// these tasks derive them from the same source instead:
//   ./gradlew syncDocsVersion   — rewrite the snippets to match VERSION_NAME
//   ./gradlew checkDocsVersion  — fail if they have drifted (CI runs this)
abstract class DocsVersionTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:InputFiles
    abstract val docs: ConfigurableFileCollection

    /** Report drift instead of fixing it. */
    @get:Input
    abstract val checkOnly: Property<Boolean>

    @TaskAction
    fun sync() {
        val target = version.get()
        val stale = mutableListOf<String>()
        docs.forEach { file ->
            val original = file.readText()
            val updated = original
                // `implementation("com.gyanoba.kexcel:kexcel:<version>")`
                .replace(Regex("""(com\.gyanoba\.kexcel:kexcel:)[0-9][^"']*"""), "\$1$target")
                // `kexcel = "<version>"` in the version-catalog snippet
                .replace(Regex("""(kexcel\s*=\s*")[0-9][^"]*"""), "\$1$target")
            if (updated == original) return@forEach
            if (checkOnly.get()) {
                stale += file.name
            } else {
                file.writeText(updated)
                logger.lifecycle("Updated ${file.name} to $target")
            }
        }
        check(stale.isEmpty()) {
            "${stale.joinToString()} quote a version other than VERSION_NAME=$target. " +
                "Run ./gradlew syncDocsVersion."
        }
    }
}

val versionedDocs = files("README.md", "docs/getting-started/installation.md")

tasks.register<DocsVersionTask>("syncDocsVersion") {
    group = "documentation"
    description = "Rewrites the version quoted in README.md and the install guide from VERSION_NAME."
    version = providers.gradleProperty("VERSION_NAME")
    docs.from(versionedDocs)
    checkOnly = false
}

tasks.register<DocsVersionTask>("checkDocsVersion") {
    group = "verification"
    description = "Fails if README.md or the install guide quote a version other than VERSION_NAME."
    version = providers.gradleProperty("VERSION_NAME")
    docs.from(versionedDocs)
    checkOnly = true
}
