package tech.kzen.shell.repo

import com.google.common.io.ByteStreams
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.X509Certificate
import javax.net.ssl.*


class DownloadService {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(DownloadService::class.java)!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: implement proper certificate management
    @Suppress("unused")
    fun trustBadCertificate() {
        // https://stackoverflow.com/a/24501156

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })

        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

        val allHostsValid = HostnameVerifier { _, _ -> true }

        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun download(location: URI, destination: Path) {
        Files.createDirectories(destination.parent)

        logger.info("downloading: {}", location)

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

