package tech.kzen.shell.run

import com.google.common.io.ByteStreams
import mu.KLogging
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path


@Component
class ArtifactRepo {
    companion object: KLogging()

    fun downloadIfAbsent(
            path: Path, download: URI
    ): Boolean {
        if (Files.exists(path)) {
            return false
        }
        logger.info {"downloading: $download"}

        val downloadBytes = download
                .toURL()
                .openStream()
                .use { ByteStreams.toByteArray(it) }

        logger.info {"download complete: ${downloadBytes.size}"}

        Files.createDirectories(path.parent)
        Files.write(path, downloadBytes)
        return true
    }
}