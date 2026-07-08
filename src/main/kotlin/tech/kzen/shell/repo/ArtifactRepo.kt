package tech.kzen.shell.repo

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
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
        val isLocalSource = download.scheme == "file"

        if (Files.exists(path)) {
            // Remote (https) release artifacts are immutable per version — install once. Local
            //  (file://) sources are mutable dev SNAPSHOTs — re-acquire so a rebuilt zip is picked up,
            //  but only wipe a directory that looks like one of our own prior extractions (file safety).
            if (!isLocalSource) {
                return false
            }
            if (!looksLikeExtraction(path)) {
                logger.warn("not refreshing (not a recognized extraction dir): {}", path)
                return false
            }
            logger.info("refreshing local artifact: {}", path)
            deleteRecursively(path)
        }

        Files.createDirectories(path)
        val zipPath = path.resolve("archive.zip")

        if (isLocalSource) {
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


    // A dir is safe to wipe-and-refresh only if it carries evidence of a prior extraction —
    //  the extracted entry point (main.jar) or the staging archive we leave behind.
    private fun looksLikeExtraction(path: Path): Boolean {
        return Files.exists(path.resolve("main.jar")) ||
                Files.exists(path.resolve("archive.zip"))
    }


    private fun deleteRecursively(path: Path) {
        MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE)
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
                if (!Files.isDirectory(newFile)) {
                    Files.createDirectories(newFile)
                }
            }
            else {
                // fix for Windows-created archives
                val parent = newFile.parent
                if (!Files.isDirectory(parent)) {
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
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: " + zipEntry.name)
        }
        return destFile
    }
}