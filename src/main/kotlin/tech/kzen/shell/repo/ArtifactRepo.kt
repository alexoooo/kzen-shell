package tech.kzen.shell.repo

import com.google.common.io.ByteStreams
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

        val fileBytes =
            if (download.scheme == "file") {
                val sourcePath = Paths.get(download)
                logger.info("reading from disk: {}", sourcePath)

                Files.readAllBytes(sourcePath)
            }
            else {
                ownloadService.download(download)
            }

        logger.info("done (size: {})", fileBytes.size)

        Files.createDirectories(path.parent)
        Files.write(path, fileBytes)
        return true
    }
}