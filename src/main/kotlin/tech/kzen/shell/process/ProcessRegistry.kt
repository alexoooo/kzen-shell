package tech.kzen.shell.process

import org.springframework.stereotype.Component
import javax.annotation.PreDestroy


@Component
class ProcessRegistry {
    private val processes = mutableListOf<Process>()


    fun start(processBuilder: ProcessBuilder): Process {
        val process = processBuilder.start()!!

        processes.add(process)

        return process
    }


    // TODO: automatic un-registration (e.g. by polling)
    fun unregister(process: Process) {
        processes.remove(process)
    }


    @PreDestroy
    fun close() {
        for (process in processes) {
            process.destroy()
        }
    }
}