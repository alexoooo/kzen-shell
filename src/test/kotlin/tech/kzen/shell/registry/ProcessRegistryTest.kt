package tech.kzen.shell.registry

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import tech.kzen.shell.testutil.StubProjectFixture
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test


class ProcessRegistryTest {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        const val name = "stub"

        // The stub is spawned from the classpath rather than a jar, and serves no HTTP: the registry only
        //  stores these for the proxy's lookups, which this test doesn't exercise.
        const val unusedPort = 0
        val unusedJarPath: Path = Paths.get("main.jar")

        val awaitTimeout: Duration = Duration.ofSeconds(10)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val workDir: Path = Files.createTempDirectory("process-registry-test")
    private val registry = ProcessRegistry()


    @AfterTest
    fun tearDown() {
        registry.close()
        MoreFiles.deleteRecursively(workDir, RecursiveDeleteOption.ALLOW_INSECURE)
    }


    private fun startStub(vararg args: String): Process {
        val processBuilder = ProcessBuilder(StubProjectFixture.stubCommand(*args))
            .directory(workDir.toFile())
            .redirectErrorStream(true)

        return registry.start(name, processBuilder, unusedPort, unusedJarPath)
    }


    // The unregistration is async (the exit callback runs off the process-reaper thread), so
    //  asserting it the instant the child exits would race the callback.
    private fun awaitUnregistered() {
        val deadline = System.nanoTime() + awaitTimeout.toNanos()
        while (registry.contains(name)) {
            check(System.nanoTime() < deadline) { "Timed out awaiting unregistration of '$name'" }
            Thread.sleep(50)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `exit unregisters the process`() {
        val process = startStub("dieAfterMillis=100", "exitCode=3")
        process.waitFor()

        awaitUnregistered()
    }
}
