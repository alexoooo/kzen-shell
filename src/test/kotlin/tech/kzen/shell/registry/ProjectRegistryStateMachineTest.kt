package tech.kzen.shell.registry

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import tech.kzen.shell.model.RunningProjectStatus
import tech.kzen.shell.process.MainJarRunner
import tech.kzen.shell.testutil.StubProjectFixture
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


// Drives the real spawn / readiness / reap path against scripted child JVMs (see StubProjectFixture), so
//  the lifecycle transitions are pinned end to end rather than against a stand-in process abstraction.
class ProjectRegistryStateMachineTest {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        const val name = "stub-project"

        // Well over a child JVM's boot, so a slow machine doesn't turn into a flaky failure.
        val awaitTimeout: Duration = Duration.ofSeconds(30)

        // Short enough that the alive-but-silent case is a quick test rather than a two-minute one.
        val readinessTimeout: Duration = Duration.ofSeconds(5)

        // Long enough for the child to serve HTTP and be seen RUNNING before it dies.
        const val liveMillis = 3_000L

        const val exitCode = 3
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val tempDirs = mutableListOf<Path>()

    private val logDir = tempDir()
    private val home = tempDir()

    private val processRegistry = ProcessRegistry()
    private val mainJarRunner = MainJarRunner(processRegistry, logDir, readinessTimeout)
    private val projectRegistry = ProjectRegistry(mainJarRunner, processRegistry)


    @AfterTest
    fun tearDown() {
        projectRegistry.stop(name)
        projectRegistry.close()
        processRegistry.close()

        for (dir in tempDirs) {
            try {
                MoreFiles.deleteRecursively(dir, RecursiveDeleteOption.ALLOW_INSECURE)
            }
            catch (ignored: IOException) {
                // On Windows a child still reaping holds main.jar open; the OS reclaims the temp dir.
            }
        }
    }


    private fun tempDir(): Path {
        val dir = Files.createTempDirectory("project-registry-test")
        tempDirs.add(dir)
        return dir
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun awaitState(state: ProjectRegistry.ProjectState): RunningProjectStatus {
        val deadline = System.nanoTime() + awaitTimeout.toNanos()
        while (true) {
            val status = projectRegistry.list().find { it.name == name }
            if (status?.state == state.wire) {
                return status
            }
            check(System.nanoTime() < deadline) { "Timed out awaiting ${state.wire} for '$name', was: $status" }
            Thread.sleep(50)
        }
    }


    private fun awaitTombstone(): ProcessRegistry.Tombstone {
        val deadline = System.nanoTime() + awaitTimeout.toNanos()
        while (true) {
            val tombstone = processRegistry.tombstone(name)
            if (tombstone != null) {
                return tombstone
            }
            check(System.nanoTime() < deadline) { "Timed out awaiting tombstone for '$name'" }
            Thread.sleep(50)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `child death surfaces as exited, and a restart supersedes it`() {
        StubProjectFixture.serving(home, dieAfterMillis = liveMillis, exitCode = exitCode)
        projectRegistry.start(name, home, "")
        awaitState(ProjectRegistry.ProjectState.RUNNING)

        val exited = awaitState(ProjectRegistry.ProjectState.EXITED)
        assertEquals(exitCode, exited.exitCode)
        assertTrue(exited.recentOutput?.isNotEmpty() == true)
        assertEquals(exitCode, awaitTombstone().exitCode)
        assertTrue(Files.readString(logDir.resolve("$name.log")).contains("stub project ready"))

        StubProjectFixture.serving(home)
        projectRegistry.start(name, home, "")

        val restarted = awaitState(ProjectRegistry.ProjectState.RUNNING)
        assertNull(restarted.exitCode)
        assertNull(processRegistry.tombstone(name))
    }


    @Test
    fun `dismissing an exited project clears it`() {
        StubProjectFixture.serving(home, dieAfterMillis = liveMillis, exitCode = exitCode)
        projectRegistry.start(name, home, "")
        awaitState(ProjectRegistry.ProjectState.RUNNING)
        awaitState(ProjectRegistry.ProjectState.EXITED)
        awaitTombstone()

        assertTrue(projectRegistry.stop(name))

        assertTrue(projectRegistry.list().none { it.name == name })
        assertNull(processRegistry.tombstone(name))
    }


    @Test
    fun `a child that dies during boot fails with its output`() {
        StubProjectFixture.corruptJar(home)
        projectRegistry.start(name, home, "")

        val failed = awaitState(ProjectRegistry.ProjectState.FAILED)
        assertNotNull(failed.exitCode)
        assertTrue(
            failed.recentOutput?.any { it.contains("jarfile") } == true,
            "Expected the launcher's complaint about the jar, got: ${failed.recentOutput}")
        assertFalse(processRegistry.contains(name))
    }


    @Test
    fun `a child that never serves fails once readiness times out`() {
        StubProjectFixture.silent(home)
        projectRegistry.start(name, home, "")

        val failed = awaitState(ProjectRegistry.ProjectState.FAILED)
        // Reaped rather than dead of its own accord, so there is no exit code to report.
        assertNull(failed.exitCode)
        assertTrue(failed.recentOutput?.contains("stub project wedged") == true)
        assertFalse(processRegistry.contains(name))
    }


    @Test
    fun `concurrent starts of one project spawn a single child`() {
        StubProjectFixture.serving(home)

        val starts = (1..2).map {
            Thread { projectRegistry.start(name, home, "") }
        }
        starts.forEach { it.start() }
        starts.forEach { it.join() }

        awaitState(ProjectRegistry.ProjectState.RUNNING)
        assertEquals(1, projectRegistry.list().count { it.name == name })
        assertTrue(processRegistry.contains(name))
    }
}
