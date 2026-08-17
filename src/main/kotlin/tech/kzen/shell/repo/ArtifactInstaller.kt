package tech.kzen.shell.repo

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import org.slf4j.LoggerFactory
import tech.kzen.shell.util.AtomicMoveUtil
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


// Installs a single zip artifact as an extracted, runnable directory (the launcher). Not a
//  catalogue: only the configured target dir is ever offered for running — contrast the
//  launcher's ArchetypeRepo, which manages many selectable versions.
class ArtifactInstaller(
    private val downloadService: DownloadService
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactInstaller::class.java)!!

        private const val mainJarName = "main.jar"
        private const val archiveName = "archive.zip"

        // Extraction happens in a sibling staging dir and is atomically swapped into the target, so a
        //  crash mid-extract leaves only the staging dir (cleaned next boot), never a half-populated target.
        private const val stagingSuffix = ".staging"

        // The previous extraction is moved aside (not deleted in place) during the swap: a rename
        //  fails fast when another process holds files open (e.g. a still-running instance),
        //  leaving the existing extraction intact for the degrade path — an in-place recursive
        //  delete would destroy the unlocked part before failing.
        private const val retiredSuffix = ".old"

        // Trailing artifact version in a directory name, e.g. "kzen-launcher-0.30.0-SNAPSHOT".
        private val versionSuffix = Regex("-\\d+(\\.\\d+)*(-SNAPSHOT)?$")
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
                cleanResidue(path)
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

        try {
            acquire(path, download, isLocalSource)
            return true
        }
        catch (e: Exception) {
            // Degrade to the existing extraction (e.g. the dev source zip was cleaned, or the
            //  machine is offline) — the shell can still boot on what it has. Only fail hard
            //  when there is nothing runnable at all.
            if (isComplete(path)) {
                logger.error("re-acquisition failed, keeping existing extraction: {} <- {}", path, download, e)
                return false
            }
            throw e
        }
    }


    private fun acquire(path: Path, download: URI, isLocalSource: Boolean) {
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

        Files.newInputStream(zipPath).use { input ->
            unzip(input, staging)
        }
        Files.delete(zipPath)

        check(Files.exists(staging.resolve(mainJarName))) {
            "artifact missing $mainJarName after extract: $download"
        }

        swapIntoPlace(staging, path)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Old -SNAPSHOT extractions accumulate as the configured target dir moves on each version
    //  bump, and are never run again — reclaim the disk. Released versions are kept (a config
    //  rollback can point straight at one), mirroring the archetype-catalogue rule.
    fun pruneStaleSnapshotSiblings(current: Path) {
        val normalized = current.toAbsolutePath().normalize()
        val parent = normalized.parent
            ?: return

        val currentName = normalized.fileName.toString()
        val baseName = currentName.replace(versionSuffix, "")
        if (baseName == currentName || !Files.exists(parent)) {
            // Unversioned dir name — no way to recognize version siblings.
            return
        }

        val stale = Regex(
            Regex.escape(baseName) + "-\\d+(\\.\\d+)*-SNAPSHOT" +
                "(" + Regex.escape(stagingSuffix) + "|" + Regex.escape(retiredSuffix) + ")?")

        val candidates = Files.list(parent).use { stream ->
            stream
                .filter { Files.isDirectory(it) }
                .filter {
                    val name = it.fileName.toString()
                    name != currentName && stale.matches(name)
                }
                .filter {
                    val name = it.fileName.toString()
                    looksLikeExtraction(it) ||
                            name.endsWith(stagingSuffix) ||
                            name.endsWith(retiredSuffix)
                }
                .toList()
        }

        for (candidate in candidates) {
            logger.info("pruning stale artifact extraction: {}", candidate)
            try {
                deleteRecursively(candidate)
            }
            catch (e: Exception) {
                // Best-effort: a file can be locked (e.g. an old instance still running).
                logger.warn("unable to prune: {} - {}", candidate, e.toString())
            }
        }
    }


    // Residue a crash or failed acquisition can strand beside the target: the staging dir and
    //  the moved-aside previous extraction.
    private fun cleanResidue(path: Path) {
        val residue = listOf(
            stagingDir(path),
            path.resolveSibling(path.fileName.toString() + retiredSuffix))

        for (dir in residue.filter { Files.exists(it) }) {
            try {
                deleteRecursively(dir)
            }
            catch (e: Exception) {
                logger.warn("unable to clean residue: {} - {}", dir, e.toString())
            }
        }
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

        val retired = target.resolveSibling(target.fileName.toString() + retiredSuffix)
        if (Files.exists(retired)) {
            deleteRecursively(retired)
        }
        if (Files.exists(target)) {
            // Same-parent rename: fails fast (destroying nothing) if the extraction is in use.
            Files.move(target, retired)
        }

        AtomicMoveUtil.move(staging, target)

        if (Files.exists(retired)) {
            deleteRecursively(retired)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Intentionally duplicated in kzen-launcher's ProjectCreator (unzip/resolveEntry — the launcher
    //  and shell share no module; same rationale as SecurityGate) — keep the copies in sync.
    private fun unzip(zipInput: InputStream, destDirectory: Path) {
        ZipInputStream(zipInput).use { zipIn ->
            while (true) {
                val entry: ZipEntry =
                        zipIn.nextEntry
                        ?: break

                val filePath = resolveEntry(destDirectory, entry)

                if (entry.isDirectory) {
                    Files.createDirectories(filePath)
                }
                else {
                    Files.createDirectories(filePath.parent)
                    Files.newOutputStream(filePath).use {
                        zipIn.copyTo(it)
                    }
                }
                zipIn.closeEntry()
            }
        }
    }


    // Guards against zip-slip: a crafted entry name (e.g. ../) must not resolve outside the target dir.
    private fun resolveEntry(destDirectory: Path, entry: ZipEntry): Path {
        val filePath = destDirectory.resolve(entry.name)
        val destDirPath = destDirectory.toFile().canonicalPath
        val entryPath = filePath.toFile().canonicalPath
        if (entryPath != destDirPath && !entryPath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: ${entry.name}")
        }
        return filePath
    }
}
