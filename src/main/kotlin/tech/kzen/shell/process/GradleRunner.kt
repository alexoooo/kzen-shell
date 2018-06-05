package tech.kzen.shell.process

import org.springframework.stereotype.Component
import java.nio.file.Path


@Component
class GradleRunner {
    fun run(home: Path, command: String) {
        GradleProcess.start(home, command).await()
    }
}