package tech.kzen.shell.process

import tech.kzen.shell.registry.ProcessRegistry
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration


class MainJarRunner(
    private val processRegistry: ProcessRegistry,

    // Resolved against the working directory, matching logback's own LOG_DIR, so a child's log sits
    //  next to the shell's.
    private val logDir: Path = Paths.get("logs"),

    private val readinessTimeout: Duration = MainJarProcess.defaultReadinessTimeout
) {
    fun start(
        name: String,
        location: Path,
        port: Int,
        jvmArgs: String
    ): MainJarProcess {
        return MainJarProcess.start(
                name, location, port, processRegistry, jvmArgs, logDir, readinessTimeout)
    }


    fun start(
        name: String,
        location: Path,
        port: Int,
        home: Path,
        jvmArgs: String
    ): MainJarProcess {
        return MainJarProcess.start(
                name, location, port, processRegistry, home, jvmArgs, logDir, readinessTimeout)
    }
}
