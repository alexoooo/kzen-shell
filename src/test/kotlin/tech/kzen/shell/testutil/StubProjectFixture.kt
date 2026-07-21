package tech.kzen.shell.testutil

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest


// Lays out a project home the shell can really spawn: a main.jar wrapping ShellTestStub, plus the config
//  that scripts the child's behaviour (read from the working directory, which the spawn sets to this home).
object StubProjectFixture {
    //-----------------------------------------------------------------------------------------------------------------
    private const val mainJar = "main.jar"
    private const val configFile = "stub-config.properties"
    private const val classSuffix = ".class"

    private val stubClassesDir: Path = Paths.get(
        ShellTestStub::class.java.protectionDomain.codeSource.location.toURI())


    //-----------------------------------------------------------------------------------------------------------------
    // A child that serves HTTP, so the project reaches RUNNING.
    fun serving(home: Path, dieAfterMillis: Long? = null, exitCode: Int = 0) {
        writeMainJar(home)
        writeConfig(
            home,
            "serve" to "true",
            "exitCode" to exitCode.toString(),
            "dieAfterMillis" to dieAfterMillis?.toString(),
            "line.1" to "stub project starting",
            "line.2" to "stub project ready")
    }


    // A child that stays alive but never serves HTTP, so the start hits the readiness timeout.
    fun silent(home: Path) {
        writeMainJar(home)
        writeConfig(
            home,
            "line.1" to "stub project starting",
            "line.2" to "stub project wedged")
    }


    fun corruptJar(home: Path) {
        Files.createDirectories(home)
        Files.writeString(home.resolve(mainJar), "not actually a jar")
    }


    // Spawns the stub without a jar, for tests that exercise the process registry directly.
    fun stubCommand(vararg args: String): List<String> {
        val javaBin = "${System.getProperty("java.home")}/bin/java"
        return listOf(javaBin, "-cp", stubClassesDir.toString(), ShellTestStub::class.java.name) + args
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun writeConfig(home: Path, vararg entries: Pair<String, String?>) {
        val content = entries
            .mapNotNull { (key, value) -> value?.let { "$key=$it" } }
            .joinToString("\n")

        Files.writeString(home.resolve(configFile), content)
    }


    private fun writeMainJar(home: Path) {
        Files.createDirectories(home)

        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = ShellTestStub::class.java.name

        JarOutputStream(Files.newOutputStream(home.resolve(mainJar)), manifest).use { jar ->
            Files.walk(stubClassesDir).use { paths ->
                for (path in paths) {
                    if (!Files.isRegularFile(path) || !path.fileName.toString().endsWith(classSuffix)) {
                        continue
                    }

                    jar.putNextEntry(JarEntry(stubClassesDir.relativize(path).joinToString("/")))
                    Files.copy(path, jar)
                    jar.closeEntry()
                }
            }
        }
    }
}
