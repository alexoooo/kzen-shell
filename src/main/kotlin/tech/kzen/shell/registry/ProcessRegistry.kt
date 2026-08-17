package tech.kzen.shell.registry

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path


// Concurrency invariant: this registry's monitor is a LEAF. No method calls out to ProjectRegistry or
//  MainJarProcess while holding it, and exit callbacks are dispatched asynchronously — never on the JVM's
//  process-reaper thread (small stack, must not block), never under a ProjectRegistry entry monitor.
class ProcessRegistry {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ProcessRegistry::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val processes = mutableMapOf<String, Info>()
    private var closed = false


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun start(
        name: String,
        processBuilder: ProcessBuilder,
        port: Int,
        jarPath: Path
    ): Process {
        check(!closed) { "already closed" }
        check(!processes.containsKey(name)) { "already started: $name" }

        logger.info("Running process '{}': {} at {}",
            name,
            processBuilder.command(),
            processBuilder.directory().toPath().toAbsolutePath().normalize())

        val process = processBuilder.start()!!

        processes[name] = Info(
                name, process, port, jarPath)

        // thenAcceptAsync, not thenAccept: the continuation must not run on the process-reaper thread.
        process.onExit().thenAcceptAsync { exited ->
            onProcessExit(name, process, exited.exitValue())
        }

        return process
    }


    @Synchronized
    private fun onProcessExit(name: String, process: Process, exitCode: Int) {
        if (closed) {
            // Shutdown reaps every child; those deaths are not crashes.
            return
        }

        if (processes[name]?.process !== process) {
            // A restart already replaced this name, or kill() unregistered it.
            return
        }

        processes.remove(name)

        logger.info("Process '{}' exited with code {}", name, exitCode)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun unregister(process: Process) {
        val entry =
            processes.entries.find { it.value.process == process }
            ?: return

        processes.remove(entry.key)
        logger.info("Removed process named '{}'", entry.key)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun contains(name: String): Boolean {
        return processes.contains(name)
    }


    @Synchronized
    fun getOrNull(name: String): Info? {
        return processes[name]
    }


    // The launcher is registered under a name the shell chose from its unpack dir, so the '/main/' alias
    //  finds it by the jar it was spawned from instead.
    @Synchronized
    fun findByJarPath(jarPath: Path): Info? {
        return processes.values.find { it.jarPath == jarPath }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun close() {
        closed = true
        for (process in processes.values) {
            // Release each child's stdin: managed children (launcher / projects) observe the EOF and
            //  self-reap gracefully via their own shutdown hooks, rather than being hard-killed. The
            //  shell's own imminent exit closes these pipes too, so a slow child still gets reaped.
            try {
                process.process.outputStream.close()
            }
            catch (ignored: IOException) {
            }
        }
        processes.clear()
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Info(
        val name: String,
        val process: Process,

        // The port the child serves on, and the jar it was spawned from — what the proxy needs to route to it.
        val port: Int,
        val jarPath: Path)
}