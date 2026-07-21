package tech.kzen.shell.registry

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import tech.kzen.shell.testutil.StubProjectFixture
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull


class ProcessRegistryTombstoneTest {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        const val name = "stub"

        val awaitTimeout: Duration = Duration.ofSeconds(10)

        // How long a death is watched for a tombstone that must never appear: the recording is async, so
        //  asserting absence the instant the child exits would pass before the callback even ran.
        val settleMillis = 500L
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

        return registry.start(name, processBuilder, mapOf())
    }


    private fun awaitTombstone(): ProcessRegistry.Tombstone {
        val deadline = System.nanoTime() + awaitTimeout.toNanos()
        while (true) {
            val tombstone = registry.tombstone(name)
            if (tombstone != null) {
                return tombstone
            }
            check(System.nanoTime() < deadline) { "Timed out awaiting tombstone for '$name'" }
            Thread.sleep(50)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `exit unregisters the process and records its code`() {
        val process = startStub("dieAfterMillis=100", "exitCode=3")
        process.waitFor()

        assertEquals(3, awaitTombstone().exitCode)
        assertFalse(registry.contains(name))
    }


    @Test
    fun `tombstone can be cleared`() {
        startStub("dieAfterMillis=100", "exitCode=3").waitFor()
        awaitTombstone()

        registry.clearTombstone(name)

        assertNull(registry.tombstone(name))
    }


    @Test
    fun `next start supersedes the previous death`() {
        startStub("dieAfterMillis=100", "exitCode=3").waitFor()
        awaitTombstone()

        startStub()

        assertNull(registry.tombstone(name))
    }


    @Test
    fun `shutdown reaping is not recorded as a death`() {
        val process = startStub()

        registry.close()
        process.waitFor()
        Thread.sleep(settleMillis)

        assertNull(registry.tombstone(name))
    }
}
