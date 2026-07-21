package tech.kzen.shell.registry

import org.slf4j.LoggerFactory
import tech.kzen.shell.model.RunningProjectStatus
import tech.kzen.shell.process.MainJarProcess
import tech.kzen.shell.process.MainJarProcessStartException
import tech.kzen.shell.process.MainJarRunner
import tech.kzen.shell.util.FreePortUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong


// Tracks user-launched projects and their lifecycle state (starting -> running -> stopping, or
//  starting -> failed, or running -> exited). Both start() and stop() are ASYNCHRONOUS: they mutate state
//  and return immediately, doing the blocking spawn / kill on a background thread. The launcher polls
//  list() for state, so a page refresh no longer loses in-progress work. Replaces the previous Guava-cache
//  single-flight model, which blocked the HTTP request for the entire child-JVM boot.
class ProjectRegistry(
    private val mainJarRunner: MainJarRunner,
    private val processRegistry: ProcessRegistry
) {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        val logger = LoggerFactory.getLogger(ProjectRegistry::class.java)!!

        const val mainJar = "main.jar"
    }


    //-----------------------------------------------------------------------------------------------------------------
    enum class ProjectState(val wire: String) {
        STARTING("starting"),
        RUNNING("running"),
        STOPPING("stopping"),

        // Never came up: spawn error, death during boot, or readiness timeout.
        FAILED("failed"),

        // Died on its own after it was running.
        EXITED("exited");


        // Nothing further is pending, so a start replaces the entry and a stop dismisses it.
        val terminal: Boolean
            get() = this == FAILED || this == EXITED
    }


    // Mutable per-project record. `state`, `process` and the failure detail are guarded by the entry's
    //  monitor for the start/stop hand-off (a stop arriving mid-boot must be observed by the start task,
    //  and vice versa); other reads (list()) tolerate a slightly-stale snapshot. `sequence` is a monotonic
    //  start ordinal used to render list() newest-first (a just-started project appears at the top).
    private class Entry(
        val name: String,
        val location: Path,
        val jvmArgs: String,
        val sequence: Long
    ) {
        var state: ProjectState = ProjectState.STARTING
        var process: MainJarProcess? = null
        var exitCode: Int? = null
        var failureOutput: List<String>? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val entries = ConcurrentHashMap<String, Entry>()

    private val sequenceCounter = AtomicLong()

    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "kzen-project-lifecycle").apply { isDaemon = true }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(name: String): Boolean {
        return entries.containsKey(name)
    }


    // Newest-first: the most recently started project is index 0, so the launcher renders it at the
    //  top of Running Projects. A FAILED->restart gets a fresh (higher) sequence and jumps to the top;
    //  a STARTING->RUNNING transition keeps the same Entry, so a running project holds its position.
    fun list(): List<RunningProjectStatus> {
        return entries.values
            .sortedByDescending { it.sequence }
            .map { RunningProjectStatus(it.name, it.state.wire, it.exitCode, recentOutput(it)) }
    }


    // The child's last words, shown by the launcher under a failed/exited row. Read lazily for EXITED
    //  rather than snapshotted when the child dies: the drain has consumed the tail long before the
    //  first poll arrives, and the exit callback stays free of blocking work.
    private fun recentOutput(entry: Entry): List<String>? {
        return when (entry.state) {
            ProjectState.FAILED -> entry.failureOutput
            ProjectState.EXITED -> entry.process?.recentOutput()
            else -> null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Idempotent: a start for a name that is already starting/running/stopping is a no-op. A start for
    //  a name in a terminal state replaces it with a fresh attempt.
    fun start(name: String, location: Path, jvmArgs: String) {
        var created: Entry? = null

        entries.compute(name) { _, existing ->
            if (existing != null && !existing.state.terminal) {
                existing
            }
            else {
                val fresh = Entry(name, location, jvmArgs, sequenceCounter.incrementAndGet())
                created = fresh
                fresh
            }
        }

        val entry = created
            ?: return

        executor.submit { runStart(entry) }
    }


    private fun runStart(entry: Entry) {
        val process: MainJarProcess
        try {
            process = startImpl(entry.name, entry.location, entry.jvmArgs)
        }
        catch (e: Throwable) {
            logger.warn("Project '{}' failed to start", entry.name, e)
            synchronized(entry) {
                if (entry.state == ProjectState.STOPPING) {
                    entries.remove(entry.name, entry)
                }
                else {
                    entry.state = ProjectState.FAILED
                    if (e is MainJarProcessStartException) {
                        entry.exitCode = e.exitCode
                        entry.failureOutput = e.recentOutput
                    }
                    else {
                        // Nothing was spawned (bad layout, corrupt jar path): the message is all there is.
                        entry.failureOutput = e.message?.let { listOf(it) }
                    }
                }
            }
            return
        }

        val stopRequested: Boolean
        synchronized(entry) {
            entry.process = process
            stopRequested = entry.state == ProjectState.STOPPING
            if (! stopRequested) {
                entry.state = ProjectState.RUNNING
            }
        }

        // A stop arrived while the child was still booting: kill it now that it is up.
        if (stopRequested) {
            process.kill()
            entries.remove(entry.name, entry)
            return
        }

        process.onExit { exitCode ->
            onChildExit(entry, exitCode)
        }
    }


    // A child death only surfaces as EXITED while the project is RUNNING: from STOPPING the stop path
    //  owns removal, so a deliberate stop is never reported as a crash.
    private fun onChildExit(entry: Entry, exitCode: Int) {
        synchronized(entry) {
            if (entry.state != ProjectState.RUNNING) {
                return
            }

            entry.state = ProjectState.EXITED
            entry.exitCode = exitCode
        }

        logger.warn("Project '{}' exited with code {}", entry.name, exitCode)
    }


    private fun startImpl(name: String, projectHome: Path, jvmArgs: String): MainJarProcess {
        val jarPath = locateJar(projectHome)
        val freePort = FreePortUtil.findAvailableTcpPort()
        return mainJarRunner.start(name, jarPath, freePort, projectHome, jvmArgs)
    }


    private fun locateJar(projectHome: Path): Path {
        val mainJar = projectHome.resolve(mainJar)
        if (Files.exists(mainJar)) {
            return mainJar
        }

        throw IllegalArgumentException("Not found: $mainJar")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Idempotent. RUNNING -> kill in the background. STARTING -> flag so the start task kills on
    //  completion. FAILED / EXITED -> remove (this is the UI "dismiss"). Absent -> false.
    fun stop(name: String): Boolean {
        val entry = entries[name]
            ?: return false

        var dismissedExited = false

        synchronized(entry) {
            when (entry.state) {
                ProjectState.RUNNING -> {
                    entry.state = ProjectState.STOPPING
                    val process = entry.process
                    executor.submit {
                        process?.kill()
                        entries.remove(name, entry)
                    }
                }

                ProjectState.STARTING -> {
                    // Not yet spawned/ready; runStart will kill once the child comes up.
                    entry.state = ProjectState.STOPPING
                }

                ProjectState.STOPPING -> {
                    // Already stopping.
                }

                ProjectState.FAILED -> {
                    entries.remove(name, entry)
                }

                ProjectState.EXITED -> {
                    entries.remove(name, entry)
                    dismissedExited = true
                }
            }
        }

        // Deliberately outside the entry monitor: the ProcessRegistry monitor is a leaf and the two
        //  must never nest.
        if (dismissedExited) {
            processRegistry.clearTombstone(name)
        }

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close() {
        executor.shutdownNow()
    }
}
