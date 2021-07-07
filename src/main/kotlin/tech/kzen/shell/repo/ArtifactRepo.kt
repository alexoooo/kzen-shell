package tech.kzen.shell.repo

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


@Component
class ArtifactRepo(
        private val ownloadService: DownloadService
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(DownloadService::class.java)!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun downloadIfAbsent(
            path: Path, download: URI
    ): Boolean {
        if (Files.exists(path)) {
            return false
        }

        if (download.scheme == "file") {
            val sourcePath = Paths.get(download)
            logger.info("reading from disk: {}", sourcePath)

            Files.createDirectories(path.parent)
            Files.copy(sourcePath, path)
        }
        else {
            ownloadService.download(download, path)
        }

        return true
    }
}