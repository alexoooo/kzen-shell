package tech.kzen.shell.repo

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


class ArtifactRepo(
    private val downloadService: DownloadService
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactRepo::class.java)!!

        private const val mainJarName = "main.jar"
        private const val archiveName = "archive.zip"

        // Extraction happens in a sibling staging dir and is atomically swapped into the target, so a
        //  crash mid-extract leaves only the staging dir (cleaned next boot), never a half-populated target.
        private const val stagingSuffix = ".staging"
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun downloadIfAbsent(
        path: Path,
        download: URI
    ): Boolean {
        val isLocalSource = download.scheme == "file"

        if (Files.exists(path)) {
            // A complete remote (https) artifact is immutable per version — install once.
            if (isComplete(path) && !isLocalSource) {
                return false
            }
            // Never clobber a directory that isn't one of our own extractions (file safety).
            if (!looksLikeExtraction(path)) {
                logger.warn("not refreshing (not a recognized extraction dir): {}", path)
                return false
            }
            // Local (file://) sources are mutable dev SNAPSHOTs; a dir missing main.jar is a
            //  crash-mid-extract half-state. Either way re-acquire — but only swap in the fresh copy
            //  once it is fully staged and verified below, so the existing copy survives a failed retry.
            logger.info("re-acquiring artifact: {}", path)
        }

        val staging = stagingDir(path)
        if (Files.exists(staging)) {
            deleteRecursively(staging)
        }
        Files.createDirectories(staging)

        val zipPath = staging.resolve(archiveName)
        if (isLocalSource) {
            val sourcePath = Paths.get(download)
            logger.info("reading from disk: {}", sourcePath)
            Files.copy(sourcePath, zipPath)
        }
        else {
            downloadService.download(download, zipPath)
        }

        extractZip(zipPath, staging)
        Files.delete(zipPath)

        check(Files.exists(staging.resolve(mainJarName))) {
            "artifact missing $mainJarName after extract: $download"
        }

        swapIntoPlace(staging, path)
        return true
    }


    private fun isComplete(path: Path): Boolean {
        return Files.exists(path.resolve(mainJarName))
    }


    // A dir is safe to wipe-and-refresh only if it carries evidence of a prior extraction —
    //  the extracted entry point (main.jar) or the staging archive an older layout left behind.
    private fun looksLikeExtraction(path: Path): Boolean {
        return Files.exists(path.resolve(mainJarName)) ||
                Files.exists(path.resolve(archiveName))
    }


    private fun stagingDir(path: Path): Path {
        return path.resolveSibling(path.fileName.toString() + stagingSuffix)
    }


    private fun deleteRecursively(path: Path) {
        MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE)
    }


    private fun swapIntoPlace(staging: Path, target: Path) {
        Files.createDirectories(target.parent)
        if (Files.exists(target)) {
            deleteRecursively(target)
        }
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE)
        }
        catch (e: AtomicMoveNotSupportedException) {
            // staging and target on different stores — a plain move copies then deletes.
            logger.info("atomic move unsupported ({}), copying across stores: {} -> {}", e.message, staging, target)
            Files.move(staging, target)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // https://www.baeldung.com/java-compress-and-uncompress
    private fun extractZip(zipFile: Path, outputDir: Path) {
        ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
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

                    Files.newOutputStream(newFile).use { output ->
                        zis.copyTo(output)
                    }
                }
                zipEntry = zis.nextEntry
            }
        }
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
