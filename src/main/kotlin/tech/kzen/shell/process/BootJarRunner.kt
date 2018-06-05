package tech.kzen.shell.process

import org.springframework.stereotype.Component
import tech.kzen.shell.registry.ProcessRegistry
import java.nio.file.Path


@Component
class BootJarRunner(
        private val processRegistry: ProcessRegistry
) {
    fun start(
            name: String,
            location: Path,
            port: Int
    ): BootJarProcess {
        return BootJarProcess.start(
                name, location, port, processRegistry)
    }


    fun start(
            name: String,
            location: Path,
            port: Int,
            home: Path
    ): BootJarProcess {
        return BootJarProcess.start(
                name, location, port, processRegistry, home)
    }
}