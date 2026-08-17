package tech.kzen.shell.repo

import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path


// Downloads executable artifacts (the launcher zip), so TLS certificates are validated with the
//  JVM's default trust store — corporate-MITM environments can supply their own via
//  -Djavax.net.ssl.trustStore (see README). Intentionally duplicated in kzen-launcher's
//  DownloadService (no shared module — same rationale as SecurityGate) — keep the copies in sync.
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
                .use { input -> input.copyTo(output) }
        }

        logger.info("download complete: {}", bytes)
    }
}

