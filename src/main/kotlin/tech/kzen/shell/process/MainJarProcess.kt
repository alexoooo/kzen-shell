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
    private val processRegistry: ProcessRegistry,
    logFile: Path
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Backstop for a child that spawns but never serves HTTP (hang / mis-config). Child crashes are
        //  detected sooner via process liveness; this only bounds the pathological alive-but-silent case.
        val defaultReadinessTimeout: Duration = Duration.ofSeconds(120)

        // Bounds both the memory a chatty child costs and the payload a failed/exited row puts on the wire.
        private const val recentOutputLines = 100


        //-------------------------------------------------
        fun start(
            name: String,
            location: Path,
            port: Int,
            processRegistry: ProcessRegistry,
            jvmArgs: String,
            logDir: Path,
            readinessTimeout: Duration
        ): MainJarProcess {
            val home = location.parent
            return start(name, location, port, processRegistry, home, jvmArgs, logDir, readinessTimeout)
        }


        fun start(
            name: String,
            location: Path,
            port: Int,
            processRegistry: ProcessRegistry,
            home: Path,
            jvmArgs: String,
            logDir: Path,
            readinessTimeout: Duration
        ): MainJarProcess {
            val process = startProcess(
                    name, home, location, port, processRegistry, jvmArgs)

            val mainJarProcess = MainJarProcess(
                    name, process, processRegistry, logDir.resolve("$name.log"))

            val ready = ProcessAwaitUtil.awaitAvailable(port, process, readinessTimeout)
            if (! ready) {
                // Child died or never came up: reap it (also unregisters from processRegistry) and fail,
                //  so ProjectRegistry marks the project FAILED rather than leaving it stuck STARTING.
                val bootExitCode =
                    if (process.isAlive) {
                        null
                    }
                    else {
                        process.exitValue()
                    }

                // kill() joins the drain, so the output tail is complete by the time it returns.
                mainJarProcess.kill()

                throw MainJarProcessStartException(
                    "Project '$name' did not become available on port $port",
                    bootExitCode,
                    mainJarProcess.recentOutput())
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val recentLines = ArrayDeque<String>()

    // Initialized last: the drain thread it starts publishes into the fields above.
    private val drain = startDrain(LineLogTee(logFile))


    private fun startDrain(tee: LineLogTee): Thread {
        val drain = Thread {
            try {
                val reader = process.inputStream.bufferedReader()

                while (true) {
                    val line = reader.readLine()
                            ?: break

                    println(">> $line")
                    record(line)
                    tee.appendLine(line)
                }
            }
            finally {
                tee.close()
            }
        }

        drain.start()

        return drain
    }


    private fun record(line: String) {
        synchronized(recentLines) {
            if (recentLines.size == recentOutputLines) {
                recentLines.removeFirst()
            }
            recentLines.addLast(line)
        }
    }


    // The tail of what the child has written so far (stderr included — the spawn merges it into stdout).
    fun recentOutput(): List<String> {
        return synchronized(recentLines) {
            recentLines.toList()
        }
    }


    // Process.onExit() hands out a fresh future per call, so this composes with the ProcessRegistry's own
    //  exit handling. Dispatched asynchronously to keep the callback off the process-reaper thread; it
    //  also fires for a child that already exited before this was called.
    fun onExit(callback: (Int) -> Unit) {
        process.onExit().thenAcceptAsync { exited ->
            callback(exited.exitValue())
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