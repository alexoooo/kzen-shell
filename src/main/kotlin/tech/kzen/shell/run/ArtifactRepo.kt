package tech.kzen.shell.run

import com.google.common.io.ByteStreams
import mu.KLogging
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


@Component
class ArtifactRepo {
    companion object: KLogging()

    fun downloadIfAbsent(
            path: Path, download: URI
    ): Boolean {
        if (Files.exists(path)) {
            return false
        }

        val fileBytes =
            if (download.scheme == "file") {
                val sourcePath = Paths.get(download)
                logger.info {"reading from disk: $sourcePath"}

                Files.readAllBytes(sourcePath)
            }
            else {
                logger.info {"downloading: $download"}

                download.toURL()
                        .openStream()
                        .use { ByteStreams.toByteArray(it) }
            }

        logger.info {"done (size: ${fileBytes.size})"}

        Files.createDirectories(path.parent)
        Files.write(path, fileBytes)
        return true
    }
}