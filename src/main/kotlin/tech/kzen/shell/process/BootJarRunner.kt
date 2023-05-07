package tech.kzen.shell.process

import tech.kzen.shell.registry.ProcessRegistry
import java.nio.file.Path


class BootJarRunner(
    private val processRegistry: ProcessRegistry
) {
    fun start(
        name: String,
        location: Path,
        port: Int,
        jvmArgs: String
    ): BootJarProcess {
        return BootJarProcess.start(
                name, location, port, processRegistry, jvmArgs)
    }


    fun start(
        name: String,
        location: Path,
        port: Int,
        home: Path,
        jvmArgs: String
    ): BootJarProcess {
        return BootJarProcess.start(
                name, location, port, processRegistry, home, jvmArgs)
    }
}