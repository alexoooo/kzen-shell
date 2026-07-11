package tech.kzen.shell.repo

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class ArtifactInstallerTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val tempDirs = mutableListOf<Path>()

    private val workDir = tempDir()
    private val sourceDir = tempDir()

    private val installer = ArtifactInstaller(DownloadService())


    @AfterTest
    fun tearDown() {
        for (dir in tempDirs) {
            MoreFiles.deleteRecursively(dir, RecursiveDeleteOption.ALLOW_INSECURE)
        }
    }


    private fun tempDir(): Path {
        val dir = Files.createTempDirectory("artifact-installer-test")
        tempDirs.add(dir)
        return dir
    }


    private fun sourceZip(mainJarContent: String): URI {
        val zip = sourceDir.resolve("artifact.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("main.jar"))
            out.write(mainJarContent.toByteArray())
            out.closeEntry()
        }
        return zip.toUri()
    }


    private fun extraction(name: String): Path {
        val dir = workDir.resolve(name)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("main.jar"), name)
        return dir
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `installs and extracts from file source`() {
        val target = workDir.resolve("kzen-launcher-0.30.0-SNAPSHOT")

        assertTrue(installer.downloadIfAbsent(target, sourceZip("v1")))

        assertEquals("v1", Files.readString(target.resolve("main.jar")))
    }


    @Test
    fun `file source re-acquired on every call`() {
        val target = workDir.resolve("kzen-launcher-0.30.0-SNAPSHOT")
        installer.downloadIfAbsent(target, sourceZip("v1"))

        assertTrue(installer.downloadIfAbsent(target, sourceZip("v2")))

        assertEquals("v2", Files.readString(target.resolve("main.jar")))
    }


    @Test
    fun `complete remote extraction installs once and cleans residue`() {
        val target = extraction("kzen-launcher-0.30.0")
        val staging = workDir.resolve("kzen-launcher-0.30.0.staging")
        Files.createDirectories(staging)
        val retired = extraction("kzen-launcher-0.30.0.old")

        // Unreachable host: the early return must dedupe before any network access.
        assertFalse(installer.downloadIfAbsent(target, URI("https://invalid.invalid/kzen-launcher-0.30.0.zip")))

        assertEquals("kzen-launcher-0.30.0", Files.readString(target.resolve("main.jar")))
        assertFalse(Files.exists(staging))
        assertFalse(Files.exists(retired))
    }


    @Test
    fun `failed re-acquisition degrades to existing extraction`() {
        val target = extraction("kzen-launcher-0.30.0-SNAPSHOT")

        val missingSource = sourceDir.resolve("missing.zip").toUri()
        assertFalse(installer.downloadIfAbsent(target, missingSource))

        assertEquals("kzen-launcher-0.30.0-SNAPSHOT", Files.readString(target.resolve("main.jar")))
    }


    @Test
    fun `locked extraction survives failed swap intact`() {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            // File locking blocks the rename only on Windows.
            return
        }

        val target = workDir.resolve("kzen-launcher-0.30.0-SNAPSHOT")
        installer.downloadIfAbsent(target, sourceZip("v1"))

        Files.newInputStream(target.resolve("main.jar")).use {
            // A running instance holds the extraction open: the swap must fail fast,
            //  degrading to the existing extraction without destroying any of it.
            assertFalse(installer.downloadIfAbsent(target, sourceZip("v2")))
            assertEquals("v1", Files.readString(target.resolve("main.jar")))
        }
    }


    @Test
    fun `crash residue between swap steps self-heals`() {
        // Simulates a crash after the previous extraction was moved aside: target gone,
        //  .old holding the previous version, staging holding a partial copy.
        val retired = extraction("kzen-launcher-0.30.0-SNAPSHOT.old")
        val staging = workDir.resolve("kzen-launcher-0.30.0-SNAPSHOT.staging")
        Files.createDirectories(staging)
        val target = workDir.resolve("kzen-launcher-0.30.0-SNAPSHOT")

        assertTrue(installer.downloadIfAbsent(target, sourceZip("v2")))

        assertEquals("v2", Files.readString(target.resolve("main.jar")))
        assertFalse(Files.exists(retired))
        assertFalse(Files.exists(staging))
    }


    @Test
    fun `failed acquisition with nothing cached fails hard`() {
        val target = workDir.resolve("kzen-launcher-0.30.0-SNAPSHOT")

        val missingSource = sourceDir.resolve("missing.zip").toUri()
        assertFailsWith<Exception> {
            installer.downloadIfAbsent(target, missingSource)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `prunes stale snapshot siblings, keeps current and releases and unrelated dirs`() {
        val current = extraction("kzen-launcher-0.30.0-SNAPSHOT")
        extraction("kzen-launcher-0.29.1-SNAPSHOT")
        extraction("kzen-launcher-0.29.0")
        val staleStaging = workDir.resolve("kzen-launcher-0.28.0-SNAPSHOT.staging")
        Files.createDirectories(staleStaging)
        val unrelated = workDir.resolve("kzen-proj")
        Files.createDirectories(unrelated)

        installer.pruneStaleSnapshotSiblings(current)

        assertTrue(Files.exists(current))
        assertFalse(Files.exists(workDir.resolve("kzen-launcher-0.29.1-SNAPSHOT")))
        assertTrue(Files.exists(workDir.resolve("kzen-launcher-0.29.0")))
        assertFalse(Files.exists(staleStaging))
        assertTrue(Files.exists(unrelated))
    }


    @Test
    fun `prune requires extraction evidence in snapshot siblings`() {
        val current = extraction("kzen-launcher-0.30.0-SNAPSHOT")
        val notOurs = workDir.resolve("kzen-launcher-0.29.1-SNAPSHOT")
        Files.createDirectories(notOurs)
        Files.writeString(notOurs.resolve("user-notes.txt"), "keep me")

        installer.pruneStaleSnapshotSiblings(current)

        assertTrue(Files.exists(notOurs.resolve("user-notes.txt")))
    }


    @Test
    fun `prune is a no-op for unversioned current dir name`() {
        val current = extraction("kzen-launcher")
        extraction("kzen-launcher-0.29.1-SNAPSHOT")

        installer.pruneStaleSnapshotSiblings(current)

        assertTrue(Files.exists(workDir.resolve("kzen-launcher-0.29.1-SNAPSHOT")))
    }
}
