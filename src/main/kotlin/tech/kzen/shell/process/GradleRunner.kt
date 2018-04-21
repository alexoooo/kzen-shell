package tech.kzen.shell.process

import java.nio.file.Path


object GradleRunner {
    fun run(home: Path, command: String) {
        GradleProcess.start(home, command).await()
    }
}