import java.util.Base64
import java.util.zip.GZIPInputStream

val rootPath = "generated/kotlin"
val wasmWasiResourcesPath = "generated/wasmWasiTestResources"
val isGithubActions: Boolean = System.getenv("GITHUB_ACTIONS")?.toBoolean() == true

val libBuildType = project.findProperty("libBuildType")?.toString()
kotlin {
    sourceSets {
        wasmJsTest {
            dependencies {
                implementation(libs.korlibs.io)
            }
        }

        commonTest {
            dependencies {
                when (libBuildType) {
                    "kotlinx" -> {
//                        implementation("com.fleeksoft.ksoup:ksoup-kotlinx:${libs.versions.libraryVersion.get()}")
                        implementation(project(":ksoup-kotlinx"))
                    }

                    "okio" -> {
//                        implementation("com.fleeksoft.ksoup:ksoup-okio:${libs.versions.libraryVersion.get()}")
                        implementation(project(":ksoup-okio"))
                    }

                    else -> {
                        implementation(project(":ksoup-io-fake"))
                    }
                }
            }
        }
    }
}
kotlin {
    sourceSets {
        commonTest {
            this.kotlin.srcDir(layout.buildDirectory.file(rootPath))
        }
        wasmWasiTest {
            this.kotlin.srcDir(layout.buildDirectory.dir(wasmWasiResourcesPath))
        }
    }
}

val generateBuildConfigFile: Task by tasks.creating {
    group = "build setup"
    val file = layout.buildDirectory.file("$rootPath/BuildConfig.kt")
    outputs.file(file)

    doLast {
        val content =
            """
            package com.fleeksoft.ksoup

            object BuildConfig {
                const val PROJECT_ROOT: String = "${rootProject.rootDir.absolutePath.replace("\\", "\\\\")}"
                const val isGithubActions: Boolean = $isGithubActions
                const val libBuildType: String = "$libBuildType"
                const val isKotlinx: Boolean = ${libBuildType == "kotlinx" || libBuildType == "common"}
                const val isOkio: Boolean = ${libBuildType == "okio"}
                const val isCore: Boolean = ${libBuildType == "core"}
            }
            """.trimIndent()
        file.get().asFile.writeText(content)
    }
}

val generateWasmWasiTestResources by tasks.registering {
    group = "build setup"
    val resourceRoot = layout.projectDirectory.dir("testResources")
    val testSourceRoot = layout.projectDirectory.dir("test")
    val outputFile = layout.buildDirectory.file("$wasmWasiResourcesPath/com/fleeksoft/ksoup/WasmWasiTestResources.kt")
    inputs.dir(resourceRoot)
    inputs.dir(testSourceRoot)
    outputs.file(outputFile)

    doLast {
        val rootDir = resourceRoot.asFile
        val embeddedResources = discoverEmbeddedTestResources(rootDir, testSourceRoot.asFile)
        val resources = mutableListOf<Pair<String, String>>()
        val uncompressedResources = mutableListOf<Pair<String, String>>()

        rootDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(rootDir).invariantSeparatorsPath to it }
            .filter { (resourceName, _) -> resourceName in embeddedResources }
            .sortedBy { (resourceName, _) -> resourceName }
            .forEach { (resourceName, resourceFile) ->
                if (resourceName.endsWith(".gz") || resourceName.endsWith(".z")) {
                    val uncompressed = GZIPInputStream(resourceFile.inputStream()).use { it.readBytes() }
                    uncompressedResources += resourceName to Base64.getEncoder().encodeToString(uncompressed)
                } else {
                    resources += resourceName to Base64.getEncoder().encodeToString(resourceFile.readBytes())
                }
            }

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("package com.fleeksoft.ksoup")
                appendLine()
                appendLine("import kotlin.io.encoding.Base64")
                appendLine("import kotlin.io.encoding.ExperimentalEncodingApi")
                appendLine()
                appendLine("@OptIn(ExperimentalEncodingApi::class)")
                appendLine("internal object WasmWasiTestResources {")
                appendLine("    fun read(resource: String): ByteArray {")
                appendLine("        val name = resource.normalizedResourceName()")
                appendLine("        val encoded = encodedResource(name) ?: encodedUncompressedResource(name)")
                appendLine("            ?: throw IllegalArgumentException(\"Missing test resource: \${'$'}resource\")")
                appendLine("        return Base64.Default.decode(encoded)")
                appendLine("    }")
                appendLine()
                appendLine("    fun readUncompressed(resource: String): ByteArray {")
                appendLine("        val name = resource.normalizedResourceName()")
                appendLine("        val encoded = encodedUncompressedResource(name) ?: encodedResource(name)")
                appendLine("            ?: throw IllegalArgumentException(\"Missing test resource: \${'$'}resource\")")
                appendLine("        return Base64.Default.decode(encoded)")
                appendLine("    }")
                appendLine()
                appendEncodedWhen("encodedResource", resources)
                appendLine()
                appendEncodedWhen("encodedUncompressedResource", uncompressedResources)
                appendLine("}")
                appendLine()
                appendLine("private fun String.normalizedResourceName(): String = trimStart('/')")
                appendLine()
                appendLine("private fun encoded(vararg chunks: String): String = chunks.joinToString(separator = \"\")")
            }
        )
    }
}

tasks.configureEach {
    if (name != generateBuildConfigFile.name &&
        name != generateWasmWasiTestResources.name &&
        !name.contains("publish", ignoreCase = true)
    ) {
        dependsOn(generateBuildConfigFile.name)
    }
    if (name.contains("wasmWasi", ignoreCase = true) &&
        name != generateWasmWasiTestResources.name &&
        !name.contains("publish", ignoreCase = true)
    ) {
        dependsOn(generateWasmWasiTestResources.name)
    }
}

fun String.toKotlinStringLiteral(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun String.chunkedForKotlin(): List<String> = chunked(8_000)

fun StringBuilder.appendEncodedWhen(functionName: String, resources: List<Pair<String, String>>) {
    appendLine("    private fun $functionName(resource: String): String? = when (resource) {")
    resources.forEach { (resource, encoded) ->
        appendLine("        ${resource.toKotlinStringLiteral()} -> encoded(")
        encoded.chunkedForKotlin().forEach { chunk ->
            appendLine("            ${chunk.toKotlinStringLiteral()},")
        }
        appendLine("        )")
    }
    appendLine("        else -> null")
    appendLine("    }")
}

fun discoverEmbeddedTestResources(resourceRoot: File, testSourceRoot: File): Set<String> {
    val resourceNamePattern = Regex(""""(/?[A-Za-z0-9_./-]+\.(?:html|gz|z|txt|xml|xhtml|jpg|md|crt|key|p12|pfx))"""")
    val resources = linkedSetOf<String>()

    testSourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .forEach { sourceFile ->
            resourceNamePattern.findAll(sourceFile.readText()).forEach { match ->
                val resourceName = match.groupValues[1].removePrefix("/")
                when {
                    resourceRoot.resolve(resourceName).isFile -> resources += resourceName
                    !resourceName.contains("/") && resourceRoot.resolve("fuzztests/$resourceName").isFile -> {
                        resources += "fuzztests/$resourceName"
                    }
                }
            }
        }

    return resources
}
