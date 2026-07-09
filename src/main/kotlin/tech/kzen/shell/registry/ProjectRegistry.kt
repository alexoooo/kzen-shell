package tech.kzen.shell.registry

import org.slf4j.LoggerFactory
import tech.kzen.shell.model.RunningProjectStatus
import tech.kzen.shell.process.MainJarProcess
import tech.kzen.shell.process.MainJarRunner
import tech.kzen.shell.util.FreePortUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


// Tracks user-launched projects and their lifecycle state (starting -> running -> stopping, or
//  starting -> failed). Both start() and stop() are ASYNCHRONOUS: they mutate state and return
//  immediately, doing the blocking spawn / kill on a background thread. The launcher polls list()
//  for state, so a page refresh no longer loses in-progress work. Replaces the previous Guava-cache
//  single-flight model, which blocked the HTTP request for the entire child-JVM boot.
class ProjectRegistry(
    private val mainJarRunner: MainJarRunner
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
        FAILED("failed")
    }


    // Mutable per-project record. `state` and `process` are guarded by the entry's monitor for the
    //  start/stop hand-off (a stop arriving mid-boot must be observed by the start task, and vice
    //  versa); other reads (list()) tolerate a slightly-stale snapshot.
    private class Entry(
        val name: String,
        val location: Path,
        val jvmArgs: String
    ) {
        var state: ProjectState = ProjectState.STARTING
        var process: MainJarProcess? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val entries = ConcurrentHashMap<String, Entry>()

    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "kzen-project-lifecycle").apply { isDaemon = true }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(name: String): Boolean {
        return entries.containsKey(name)
    }


    fun list(): List<RunningProjectStatus> {
        return entries.values.map { RunningProjectStatus(it.name, it.state.wire) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Idempotent: a start for a name that is already starting/running/stopping is a no-op. A start for
    //  a name whose previous attempt FAILED replaces it with a fresh attempt.
    fun start(name: String, location: Path, jvmArgs: String) {
        var created: Entry? = null

        entries.compute(name) { _, existing ->
            if (existing != null && existing.state != ProjectState.FAILED) {
                existing
            }
            else {
                val fresh = Entry(name, location, jvmArgs)
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
        }
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
    //  completion. FAILED -> remove (this is the UI "dismiss"). Absent -> false.
    fun stop(name: String): Boolean {
        val entry = entries[name]
            ?: return false

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
            }
        }

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun close() {
        executor.shutdownNow()
    }
}
