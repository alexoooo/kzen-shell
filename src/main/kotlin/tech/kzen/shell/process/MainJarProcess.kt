package tech.kzen.shell.process

import com.google.common.collect.ImmutableList
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.util.ProcessAwaitUtil
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit


class MainJarProcess private constructor (
    val name: String,
    private val process: Process,
    private val drain: Thread,
    private val processRegistry: ProcessRegistry
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Backstop for a child that spawns but never serves HTTP (hang / mis-config). Child crashes are
        //  detected sooner via process liveness; this only bounds the pathological alive-but-silent case.
        private val readinessTimeout: Duration = Duration.ofSeconds(120)


        //-------------------------------------------------
        fun start(
            name: String,
            location: Path,
            port: Int,
            processRegistry: ProcessRegistry,
            jvmArgs: String
        ): MainJarProcess {
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
        ): MainJarProcess {
            val process = startProcess(
                    name, home, location, port, processRegistry, jvmArgs)

            val drain = startDrain(process)

            val mainJarProcess = MainJarProcess(name, process, drain, processRegistry)

            val ready = ProcessAwaitUtil.awaitAvailable(port, process, readinessTimeout)
            if (! ready) {
                // Child died or never came up: reap it (also unregisters from processRegistry) and fail,
                //  so ProjectRegistry marks the project FAILED rather than leaving it stuck STARTING.
                mainJarProcess.kill()
                throw IllegalStateException("Project '$name' did not become available on port $port")
            }

            return mainJarProcess
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

            val jarPath = jar.toAbsolutePath().normalize().toString()

            val commandBuilder = ImmutableList.builder<String>()
            commandBuilder.add(javaBin)

            if (jvmArgs.isNotBlank()) {
                val individualArgs = jvmArgs.trim().split(Regex("""\s+"""))
                commandBuilder.addAll(individualArgs)
            }

            commandBuilder.add("-jar")
            commandBuilder.add(jarPath)
            commandBuilder.add("--server.port=$port")

            // Bind the child's lifetime to ours: it self-reaps on stdin EOF (our death closes the
            //  inherited pipe on every OS) plus a parent-pid backup. See KzenAutoMain /
            //  KzenLauncherMain (the launcher and project mains that honour these flags).
            commandBuilder.add("--managed.lifeline=stdin")
            commandBuilder.add("--parent.pid=${ProcessHandle.current().pid()}")

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
        // Graceful first: signal via the stdin lifeline so the child self-exits cleanly and frees
        //  its port. OS-agnostic — works even on Windows where process.destroy() == TerminateProcess
        //  (a hookless hard kill).
        signalShutdown()

        val exited = process.waitFor(forceAfter.toMillis(), TimeUnit.MILLISECONDS)

        if (!exited) {
            process.destroy()
            if (!process.waitFor(forceAfter.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
        }

        await()
    }


    private fun signalShutdown() {
        // Best-effort: a child that already exited gives a broken pipe — fine. Send the "SHUTDOWN"
        //  sentinel AND close stdin so the child sees a sentinel line and/or EOF.
        val childStdin = process.outputStream
        try {
            childStdin.write("SHUTDOWN\n".toByteArray(Charsets.UTF_8))
            childStdin.flush()
        }
        catch (ignored: IOException) {
        }
        try {
            childStdin.close()
        }
        catch (ignored: IOException) {
        }
    }


    fun await() {
        process.waitFor()
        drain.join()
        processRegistry.unregister(process)
    }
}