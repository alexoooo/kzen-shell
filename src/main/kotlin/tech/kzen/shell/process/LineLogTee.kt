package tech.kzen.shell.process

import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption


// Mirrors a child process's output to a log file alongside the shell's own, so a crash trace survives
//  packaged (javaw) runs where the console echo goes nowhere. Truncated per start — the previous run's
//  output is superseded rather than appended to — and capped, so a looping child cannot fill the disk.
//  Any I/O problem retires the tee alone: the console echo and the in-memory tail are independent of it.
class LineLogTee(
    private val file: Path,
    private val capBytes: Long = defaultCapBytes
) {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        val logger = LoggerFactory.getLogger(LineLogTee::class.java)!!

        const val defaultCapBytes = 10L * 1024 * 1024

        const val capMarker = "[log cap reached; further output not written]"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val lineSeparator = System.lineSeparator()

    private var writer: BufferedWriter? = null
    private var bytesWritten = 0L
    private var retired = false


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun appendLine(line: String) {
        if (retired) {
            return
        }

        try {
            val target = writer ?: open()

            target.write(line)
            target.write(lineSeparator)

            // Per line: a hard death of the shell must still leave the child's last words on disk.
            target.flush()

            bytesWritten += line.toByteArray(StandardCharsets.UTF_8).size + lineSeparator.length
            if (bytesWritten >= capBytes) {
                target.write(capMarker)
                target.write(lineSeparator)
                target.flush()
                closeQuietly()
                retired = true
            }
        }
        catch (e: IOException) {
            logger.warn("Unable to write child log '{}', further output not captured", file, e)
            closeQuietly()
            retired = true
        }
    }


    @Synchronized
    fun close() {
        closeQuietly()
        retired = true
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun open(): BufferedWriter {
        file.parent?.let {
            Files.createDirectories(it)
        }

        val opened = Files.newBufferedWriter(
            file,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)

        writer = opened
        return opened
    }


    private fun closeQuietly() {
        try {
            writer?.close()
        }
        catch (ignored: IOException) {
        }
        writer = null
    }
}
