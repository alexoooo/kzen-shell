package tech.kzen.shell.process

import org.springframework.stereotype.Component
import java.nio.file.Path


@Component
class BootJarRunner(
        private val processRegistry: ProcessRegistry
) {
    fun start(
            location: Path,
            port: Int
    ): BootJarProcess {
        return BootJarProcess.start(location, port, processRegistry)
    }
}