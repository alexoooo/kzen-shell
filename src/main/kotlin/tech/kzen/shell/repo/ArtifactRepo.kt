package tech.kzen.shell.repo

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


class ArtifactRepo(
    private val downloadService: DownloadService
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(DownloadService::class.java)!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun downloadIfAbsent(
        path: Path,
        download: URI
    ): Boolean {
        if (Files.exists(path)) {
            return false
        }

        Files.createDirectories(path)
        val zipPath = path.resolve("archive.zip")

        if (download.scheme == "file") {
            val sourcePath = Paths.get(download)
            logger.info("reading from disk: {}", sourcePath)

            Files.copy(sourcePath, zipPath)
        }
        else {
            downloadService.download(download, zipPath)
        }

        extractZip(zipPath, path)

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    // https://www.baeldung.com/java-compress-and-uncompress
    private fun extractZip(zipFile: Path, outputDir: Path) {
        val buffer = ByteArray(1024)
        val zis = ZipInputStream(Files.newInputStream(zipFile))
        var zipEntry = zis.nextEntry
        while (zipEntry != null) {
            val newFile = newFile(outputDir, zipEntry)
            if (zipEntry.isDirectory) {
                if (! Files.isDirectory(newFile)) {
                    Files.createDirectories(newFile)
                }
            }
            else {
                // fix for Windows-created archives
                val parent = newFile.parent
                if (! Files.isDirectory(parent)) {
                    Files.createDirectories(parent)
                }

                // write file content
                val fos = Files.newOutputStream(newFile)
                var len: Int
                while (zis.read(buffer).also { len = it } > 0) {
                    fos.write(buffer, 0, len)
                }
                fos.close()
            }
            zipEntry = zis.nextEntry
        }

        zis.closeEntry()
        zis.close()
    }

    private fun newFile(destinationDir: Path, zipEntry: ZipEntry): Path {
        val destFile = Paths.get(destinationDir.toString(), zipEntry.name)
        val destDirPath = destinationDir.toFile().canonicalPath
        val destFilePath = destFile.toFile().canonicalPath
        if (! destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: " + zipEntry.name)
        }
        return destFile
    }
}