package tech.kzen.shell.process

import tech.kzen.shell.registry.ProcessRegistry
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit


class BootJarProcess private constructor (
        val name: String,
        private val process: Process,
        private val drain: Thread,
        private val processRegistry: ProcessRegistry
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        //-------------------------------------------------
        fun start(
                name: String,
                location: Path,
                port: Int,
                processRegistry: ProcessRegistry
        ): BootJarProcess {
            val home = location.parent
            return start(name, location, port, processRegistry, home)
        }


        fun start(
                name: String,
                location: Path,
                port: Int,
                processRegistry: ProcessRegistry,
                home: Path
        ): BootJarProcess {
            val process = startProcess(
                    name, home, location, port, processRegistry)

            val drain = startDrain(process)

            return BootJarProcess(name, process, drain, processRegistry)
        }


        //-------------------------------------------------
        private fun startProcess(
                name: String,
                home: Path,
                jar: Path,
                port: Int,
                processRegistry: ProcessRegistry
        ): Process {
            val javaHome = System.getProperty("java.home")
            val javaBin =  "$javaHome/bin/java"

            val jarPath = jar.toAbsolutePath().toString()
            val processSpec = ProcessBuilder()
                    .command(javaBin, "-jar", jarPath, "--server.port=$port")
                    .directory(home.toFile())
                    .redirectErrorStream(true)

            val attributes = mapOf(
                    "port" to port,
                    "location" to jarPath)

            return processRegistry.start(
                    name, processSpec, attributes)
        }


        private fun startDrain(process: Process): Thread {
            val drain = Thread({
                val reader = process.inputStream.bufferedReader()

                while (true) {
                    val line = reader.readLine()
                            ?: break

                    println(">> $line")
                }
            })

            drain.start()

            return drain
        }
    }



    //-----------------------------------------------------------------------------------------------------------------
    fun number() {
//        process.
    }


    fun kill(
            forceAfter: Duration =
                    Duration.ofSeconds(15)
    ) {
        process.destroy()

        val exited = process.waitFor(forceAfter.toMillis(), TimeUnit.MILLISECONDS)

        if (! exited) {
            process.destroyForcibly()
        }

        await()
    }


    fun await() {
        process.waitFor()
        drain.join()
        processRegistry.unregister(process)
    }
}