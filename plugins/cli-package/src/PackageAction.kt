package org.huamitoken.clipkg

import org.jetbrains.amper.plugins.*
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
 * Creates a zip distribution containing all runtime JARs and a launcher shell script.
 */
@TaskAction
fun packageCli(
    @Input runtimeClasspath: Classpath,
    @Input moduleJar: CompilationArtifact,
    @Output outputDir: Path,
    @Input moduleRootDir: Path,
    mainClass: String?,
    moduleName: String,
) {
    // Project root is one level above the module directory
    val projectRoot = moduleRootDir.parent
    val version = readProjectVersion(projectRoot)
    System.out.println("Packaging CLI version: $version")

    // Create distributions directory
    val distributionsDir = moduleRootDir.resolve("build").resolve("distributions")
    distributionsDir.toFile().mkdirs()

    val zipName = "${moduleName}-$version.zip"
    val zipFile = distributionsDir.resolve(zipName).toFile()

    // Create a temporary staging directory
    val stagingDir = File.createTempFile("cli-staging-", "")
    stagingDir.delete()
    stagingDir.mkdirs()
    val appName = "$moduleName-$version"
    val appDir = stagingDir.resolve(appName)
    appDir.mkdirs()
    val binDir = appDir.resolve("bin")
    binDir.mkdirs()
    val libDir = appDir.resolve("lib")
    libDir.mkdirs()

    try {
        // Copy all runtime classpath JARs to lib/
        val jarFiles = runtimeClasspath.resolvedFiles.map { it.toFile() }
        val mainJar = moduleJar.artifact.toFile()
        jarFiles.forEach { jar ->
            jar.copyTo(libDir.resolve(jar.name), overwrite = true)
        }
        // Ensure the module JAR is present
        if (!libDir.resolve(mainJar.name).exists()) {
            mainJar.copyTo(libDir.resolve(mainJar.name), overwrite = true)
        }
        System.out.println("Copied ${jarFiles.size} JARs to lib/")

        // Create launcher shell script
        val launcherScript = binDir.resolve(moduleName)
        val mainClassName = mainClass ?: "org.huamitoken.cli.MainKt"
        launcherScript.writeText(
            "#!/bin/sh\n" +
            "# Auto-generated launcher for $moduleName\n" +
            "SCRIPT_DIR=\"\$(cd \"\$(dirname \"\$0\")\" && pwd)\"\n" +
            "APP_DIR=\"\$(dirname \"\$SCRIPT_DIR\")\"\n" +
            "java -cp \"\$APP_DIR/lib/*\" $mainClassName \"\$@\"\n"
        )
        launcherScript.setExecutable(true)

        // Create Windows batch script
        val launcherBatch = binDir.resolve("$moduleName.bat")
        launcherBatch.writeText(
            "@echo off\n" +
            "REM Auto-generated launcher for $moduleName\n" +
            "set \"APP_DIR=%~dp0..\"\n" +
            "java -cp \"%APP_DIR%\\lib\\*\" $mainClassName %*\n"
        )

        // Create the zip file
        System.out.println("Creating $zipName ...")
        val zos = ZipOutputStream(zipFile.outputStream())
        try {
            zos.zipDirectory(appDir, appName)
        } finally {
            zos.close()
        }

        System.out.println("Created ${zipFile.absolutePath} (${zipFile.length()} bytes)")
    } finally {
        stagingDir.deleteRecursively()
    }
}

private fun ZipOutputStream.zipDirectory(baseDir: File, entryPrefix: String) {
    baseDir.listFiles()?.forEach { file ->
        val entryPath = "$entryPrefix/${file.name}"
        if (file.isDirectory) {
            zipDirectory(file, entryPath)
        } else {
            putNextEntry(ZipEntry(entryPath))
            file.inputStream().copyTo(this)
            closeEntry()
        }
    }
}
