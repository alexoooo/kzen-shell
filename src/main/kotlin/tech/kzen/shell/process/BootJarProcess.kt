package tech.kzen.shell.process

import com.google.common.collect.ImmutableList
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.util.ProcessAwaitUtil
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
                processRegistry: ProcessRegistry,
                jvmArgs: String
        ): BootJarProcess {
            val home = location.parent
            return start(name, location, port, processRegistry, home, jvmArgs)
        }


        fun start(
                name: String,
                location: Path,
                port: Int,
                processRegistry: ProcessRegistry,
                home: Path,
                jvmArgs: String
        ): BootJarProcess {
            val process = startProcess(
                    name, home, location, port, processRegistry, jvmArgs)

            val drain = startDrain(process)

            ProcessAwaitUtil.waitUntilAvailable(port)

            return BootJarProcess(name, process, drain, processRegistry)
        }


        //-------------------------------------------------
        private fun startProcess(
                name: String,
                home: Path,
                jar: Path,
                port: Int,
                processRegistry: ProcessRegistry,
                jvmArgs: String
        ): Process {
            val javaHome = System.getProperty("java.home")
            val javaBin =  "$javaHome/bin/java"

            val jarPath = jar.toAbsolutePath().toString()

            val commandBuilder = ImmutableList.builder<String>()
            commandBuilder.add(javaBin)

            if (jvmArgs.isNotBlank()) {
                val individualArgs = jvmArgs.trim().split(Regex("""\s+"""))
                commandBuilder.addAll(individualArgs)
            }

            commandBuilder.add("-jar")
            commandBuilder.add(jarPath)
            commandBuilder.add("--server.port=$port")

            val command = commandBuilder.build()
            val processSpec = ProcessBuilder()
                    .command(command)
                    .directory(home.toFile())
                    .redirectErrorStream(true)

            val attributes = mapOf(
                    "port" to port,
                    "location" to jarPath)

            return processRegistry.start(
                    name, processSpec, attributes)
        }


        private fun startDrain(process: Process): Thread {
            val drain = Thread {
                val reader = process.inputStream.bufferedReader()

                while (true) {
                    val line = reader.readLine()
                            ?: break

                    println(">> $line")
                }
            }

            drain.start()

            return drain
        }
    }



    //-----------------------------------------------------------------------------------------------------------------
//    fun number() {
//        process.
//    }


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