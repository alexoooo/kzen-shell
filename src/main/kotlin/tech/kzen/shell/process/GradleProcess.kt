package tech.kzen.shell.process

import java.nio.file.Path


class GradleProcess private constructor (
        private val process: Process,
        private val drain: Thread
) {
    companion object {
        fun start(home: Path, command: String): GradleProcess {
            val process = startProcess(home, command)
            val drain = startDrain(process)

            return GradleProcess(process, drain)
        }


        private fun startProcess(home: Path, command: String): Process {
            val isWindows = System
                    .getProperties()
                    .getProperty("os.name")
                    .lowercase()
                    .contains("windows")

            val gradleExecutable =
                    if (isWindows) {
                        "${home}/gradlew.bat"
                    }
                    else {
                        "./gradlew"
                    }

            val javaHome = System.getProperty("java.home")
            val gradleJavaHome = "-Dorg.gradle.java.home=$javaHome"

            return ProcessBuilder()
                    .command(gradleExecutable, gradleJavaHome, command)
                    .directory(home.toFile())
                    .redirectErrorStream(true)
                    .start()!!
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


    fun await() {
        process.waitFor()
        drain.join()
    }
}