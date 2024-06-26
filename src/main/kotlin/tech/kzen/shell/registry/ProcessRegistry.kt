package tech.kzen.shell.registry

import org.slf4j.LoggerFactory


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
        attributes: Map<String, Any>
    ): Process {
        check(! closed) { "already closed" }
        check(! processes.containsKey(name)) { "already started: $name" }

        logger.info("Running process '{}': {} at {}",
            name,
            processBuilder.command(),
            processBuilder.directory().toPath().toAbsolutePath().normalize())

        val process = processBuilder.start()!!

        processes[name] = Info(
                name, process, attributes)

        return process
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: automatic un-registration (e.g. by polling)
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
    fun findByAttribute(attribute: String, target: Any): Info {
        return processes.values.find { it.attributes[attribute] == target }!!
    }


    //-----------------------------------------------------------------------------------------------------------------
//    @PreDestroy
    @Synchronized
    fun close() {
        closed = true
        for (process in processes.values) {
            process.process.destroy()
        }
        processes.clear()
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Info(
        val name: String,
        val process: Process,
        val attributes: Map<String, Any>)
}