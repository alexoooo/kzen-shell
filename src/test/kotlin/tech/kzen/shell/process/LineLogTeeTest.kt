package tech.kzen.shell.process

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class LineLogTeeTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val workDir: Path = Files.createTempDirectory("line-log-tee-test")


    @AfterTest
    fun tearDown() {
        MoreFiles.deleteRecursively(workDir, RecursiveDeleteOption.ALLOW_INSECURE)
    }


    private fun logFile(): Path {
        return workDir.resolve("nested").resolve("child.log")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `lines land verbatim, in a created directory`() {
        val file = logFile()
        val tee = LineLogTee(file)

        tee.appendLine("first")
        tee.appendLine("second")
        tee.close()

        assertEquals(listOf("first", "second"), Files.readAllLines(file))
    }


    @Test
    fun `each run supersedes the previous log`() {
        val file = logFile()
        LineLogTee(file).apply {
            appendLine("previous run")
            close()
        }

        LineLogTee(file).apply {
            appendLine("current run")
            close()
        }

        assertEquals(listOf("current run"), Files.readAllLines(file))
    }


    @Test
    fun `output stops at the cap, with a marker`() {
        val file = logFile()
        val tee = LineLogTee(file, capBytes = 16)

        tee.appendLine("0123456789abcdef")
        tee.appendLine("beyond the cap")
        tee.close()

        val lines = Files.readAllLines(file)
        assertEquals("0123456789abcdef", lines[0])
        assertTrue(lines[1].contains("log cap reached"), "Expected a cap marker, got: $lines")
        assertFalse(lines.contains("beyond the cap"))
    }


    @Test
    fun `an unwritable target retires the tee instead of throwing`() {
        // A directory where the log file should be: opening it for writing always fails.
        val file = logFile()
        Files.createDirectories(file)

        val tee = LineLogTee(file)
        tee.appendLine("first")
        tee.appendLine("second")
        tee.close()

        assertTrue(Files.isDirectory(file))
    }
}
