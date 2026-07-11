package tech.kzen.shell.repo

import com.google.common.io.ByteStreams
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path


// Downloads executable artifacts (jars), so TLS certificates are validated with the JVM's
//  default trust store — corporate-MITM environments can supply their own via
//  -Djavax.net.ssl.trustStore (see README).
class DownloadService {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(DownloadService::class.java)!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun download(location: URI, destination: Path) {
        Files.createDirectories(destination.parent)

        logger.info("downloading: {} to {}", location, destination)

        val bytes = BufferedOutputStream(
            Files.newOutputStream(destination)
        ).use { output ->
            location
                .toURL()
                .openStream()
                .use { input -> ByteStreams.copy(input, output) }
        }

        logger.info("download complete: {}", bytes)
    }
}

