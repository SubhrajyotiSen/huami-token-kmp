package org.huamitoken.desktoppkg

import org.jetbrains.amper.plugins.*
import java.io.File
import java.nio.file.Path

/**
 * Reads the `VERSION_NAME` property from `gradle.properties` at the project root.
 * Falls back to "1.0.0" if the file or property is not found.
 */
private fun readProjectVersion(projectRoot: Path): String {
    val gradleProps = projectRoot.resolve("gradle.properties")
    if (gradleProps.toFile().exists()) {
        val content = gradleProps.toFile().readText()
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("VERSION_NAME=")) {
                val value = trimmed.substringAfter("=").trim()
                if (value.isNotEmpty()) return value
            }
        }
    }
    return "1.0.0"
}

/**
 * Invokes `jpackage` to create native installers (.dmg, .msi, .deb, etc.).
 */
@TaskAction
fun packageDesktop(
    @Input appClasspath: Classpath,
    @Input moduleJar: CompilationArtifact,
    mainClass: String?,
    @Output outputDir: Path,
    @Input moduleRootDir: Path,
    packageName: String,
    targetFormats: List<String>,
    appId: String,
    vendor: String,
) {
    // Project root is one level above the module directory
    val projectRoot = moduleRootDir.parent
    val packageVersion = readProjectVersion(projectRoot)
    System.out.println("Using project version: $packageVersion")
    val os = System.getProperty("os.name").lowercase()
    val isMac = os.contains("mac")
    val isWindows = os.contains("win")
    val isLinux = os.contains("nix") || os.contains("nux")

    // Filter formats by OS
    val allowedFormats = when {
        isMac -> setOf("dmg", "pkg", "app-image")
        isWindows -> setOf("msi", "exe", "app-image")
        isLinux -> setOf("deb", "rpm", "app-image")
        else -> emptySet()
    }

    val formats = targetFormats.filter { it in allowedFormats }
    if (formats.isEmpty()) {
        val osLabel = if (isMac) "macOS" else if (isWindows) "Windows" else if (isLinux) "Linux" else "unknown OS"
        System.err.println("WARNING: No valid target formats for $osLabel. Allowed: $allowedFormats")
        return
    }

    // Resolve jpackage from JAVA_HOME or PATH
    val javaHome = System.getProperty("java.home")
    var jpackageBin = File(javaHome).parentFile.resolve("bin/jpackage")
    if (!jpackageBin.exists()) {
        val alt = File(javaHome).resolve("../bin/jpackage")
        if (alt.exists()) jpackageBin = alt
    }

    if (!jpackageBin.exists()) {
        System.out.println("jpackage not found at $jpackageBin, falling back to PATH")
    }

    // Copy all runtime JARs to a staging directory
    val stagingDir = outputDir.resolve("staging")
    stagingDir.toFile().mkdirs()

    val jarFiles = appClasspath.resolvedFiles.map { it.toFile() }
    jarFiles.forEach { jar ->
        jar.copyTo(stagingDir.toFile().resolve(jar.name), overwrite = true)
    }

    // Ensure the module JAR is in staging
    val mainJar = moduleJar.artifact.toFile()
    if (!stagingDir.toFile().resolve(mainJar.name).exists()) {
        mainJar.copyTo(stagingDir.toFile().resolve(mainJar.name), overwrite = true)
    }

    for (format in formats) {
        System.out.println("Packaging $packageName as $format...")

        val jpackageCmd = if (jpackageBin.exists()) jpackageBin.absolutePath else "jpackage"

        val args = mutableListOf(
            jpackageCmd,
            "--name", packageName,
            "--type", format,
            "--input", stagingDir.toString(),
            "--main-jar", mainJar.name,
            "--main-class", mainClass ?: "org.huamitoken.desktop.MainKt",
            "--app-version", packageVersion,
            "--dest", outputDir.toString()
        )

        if (isMac) {
            args += "--mac-package-identifier"
            args += appId
            args += "--mac-package-name"
            args += packageName.replace("-", " ").replaceFirstChar { it.uppercase() }
        }

        if (isWindows) {
            args += "--win-menu"
            args += "--win-shortcut"
        }

        if (vendor.isNotBlank()) {
            args += "--vendor"
            args += vendor
        }

        val processBuilder = ProcessBuilder(args)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            System.err.println("jpackage failed with exit code $exitCode:")
            System.err.println(output)
            throw RuntimeException("jpackage failed for format $format")
        }

        System.out.println("Created $format package at $outputDir")
        if (output.isNotBlank()) {
            System.out.println(output)
        }
    }

    // Clean up staging directory
    stagingDir.toFile().deleteRecursively()

    // Copy final artifacts to a predictable location for CI
    val packagesDir = moduleRootDir.resolve("build").resolve("packages")
    packagesDir.toFile().mkdirs()
    outputDir.toFile().listFiles()?.forEach { file ->
        if (file.name.endsWith(".dmg") || file.name.endsWith(".msi") ||
            file.name.endsWith(".deb") || file.name.endsWith(".rpm") ||
            file.name.endsWith(".pkg") || file.name.endsWith(".exe")) {
            file.copyTo(packagesDir.toFile().resolve(file.name), overwrite = true)
            System.out.println("Copied ${file.name} to $packagesDir")
        }
    }
}
