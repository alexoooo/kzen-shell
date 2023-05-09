package tech.kzen.shell.process

import tech.kzen.shell.registry.ProcessRegistry
import java.nio.file.Path


class MainJarRunner(
    private val processRegistry: ProcessRegistry
) {
    fun start(
        name: String,
        location: Path,
        port: Int,
        jvmArgs: String
    ): MainJarProcess {
        return MainJarProcess.start(
                name, location, port, processRegistry, jvmArgs)
    }


    fun start(
        name: String,
        location: Path,
        port: Int,
        home: Path,
        jvmArgs: String
    ): MainJarProcess {
        return MainJarProcess.start(
                name, location, port, processRegistry, home, jvmArgs)
    }
}