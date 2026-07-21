package tech.kzen.shell.registry

import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant


// Concurrency invariant: this registry's monitor is a LEAF. No method calls out to ProjectRegistry or
//  MainJarProcess while holding it, and exit callbacks are dispatched asynchronously — never on the JVM's
//  process-reaper thread (small stack, must not block), never under a ProjectRegistry entry monitor.
class ProcessRegistry(
    private val maxTombstones: Int = defaultMaxTombstones
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ProcessRegistry::class.java)

        private const val defaultMaxTombstones = 100
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val processes = mutableMapOf<String, Info>()
    private var closed = false

    // Death records of children that exited on their own, bounded and insertion-ordered so a long-lived
    //  shell can't accumulate them. An entry is superseded by the next successful start under the same
    //  name, or cleared when the user dismisses the exited project.
    private val tombstones = object: LinkedHashMap<String, Tombstone>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Tombstone>): Boolean {
            return size > maxTombstones
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun start(
        name: String,
        processBuilder: ProcessBuilder,
        attributes: Map<String, Any>
    ): Process {
        check(!closed) { "already closed" }
        check(!processes.containsKey(name)) { "already started: $name" }

        logger.info("Running process '{}': {} at {}",
            name,
            processBuilder.command(),
            processBuilder.directory().toPath().toAbsolutePath().normalize())

        val process = processBuilder.start()!!

        processes[name] = Info(
                name, process, attributes)
        tombstones.remove(name)

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
        tombstones[name] = Tombstone(name, exitCode, Instant.now())

        logger.info("Process '{}' exited with code {}", name, exitCode)
    }


    @Synchronized
    fun tombstone(name: String): Tombstone? {
        return tombstones[name]
    }


    @Synchronized
    fun clearTombstone(name: String) {
        tombstones.remove(name)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun unregister(name: String) {
        processes.remove(name)
        logger.info("Removed process '{}'", name)
    }


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
    fun get(name: String): Info {
        return processes[name]
            ?: throw IllegalArgumentException("Unknown project: $name")
    }


    @Synchronized
    fun getOrNull(name: String): Info? {
        return processes[name]
    }


    @Synchronized
    fun findByAttribute(attribute: String, target: Any): Info? {
        return processes.values.find { it.attributes[attribute] == target }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @PreDestroy
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
        val attributes: Map<String, Any>)


    data class Tombstone(
        val name: String,
        val exitCode: Int,
        val exitedAt: Instant)
}