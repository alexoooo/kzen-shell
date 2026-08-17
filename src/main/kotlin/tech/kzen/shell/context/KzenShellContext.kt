package tech.kzen.shell.context

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import tech.kzen.shell.process.MainJarRunner
import tech.kzen.shell.proxy.ProxyHandler
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.registry.ProjectRegistry
import tech.kzen.shell.repo.ArtifactInstaller
import tech.kzen.shell.repo.DownloadService
import tech.kzen.shell.ui.DesktopUi
import tech.kzen.shell.util.FreePortUtil
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths


//---------------------------------------------------------------------------------------------------------------------
class KzenShellContext(
    val properties: KzenShellProperties,
    val desktopUi: DesktopUi
) {
    //-----------------------------------------------------------------------------------------------------------------
    // The launcher's entry-point jar: the jar start() spawns, and the identity the proxy's '/main/' alias
    //  resolves against in the process registry — one derivation, so the two cannot disagree.
    val launcherJarPath: Path = Paths
        .get(properties.path)
        .resolve("main.jar")
        .toAbsolutePath()
        .normalize()

    val downloadService = DownloadService()

    val artifactInstaller = ArtifactInstaller(downloadService)

    val processRegistry = ProcessRegistry()
    val mainJarRunner = MainJarRunner(processRegistry)

    val projectRegistry = ProjectRegistry(mainJarRunner)

    val httpClient = HttpClient(CIO) {
        followRedirects = false
        expectSuccess = false

        // The proxy relays arbitrarily long-lived responses: kzen-auto's /logic/events SSE stream (open for the
        // life of the page) and large file downloads. CIO's engine default requestTimeout is 15s and is a
        // wall-clock cap on the ENTIRE call context — when it fires it cancels the response body channel
        // mid-stream, which ProxyHandler can only log ("interrupted after response was committed") because the
        // status and headers are long since committed. The browser just sees a silently truncated 200.
        //
        // CIO exempts SSE / upgrade requests from that default, but every exemption is keyed off the request
        // BODY type (SSEClientContent / ClientUpgradeContent) or an explicit timeout capability — and this proxy
        // forwards via a plain prepareRequest with a pass-through body, so none of them ever apply here.
        //
        // So: drop the wall-clock bound (a stream has no legitimate total-duration limit) and rely on a finite
        // SOCKET timeout instead — an inter-byte deadline, which is the real liveness question for a stream and
        // still detects a wedged or dead child. kzen-auto's SSE route heartbeats every 15s, giving 4x margin
        // (it tolerates 3 consecutive lost heartbeats before this fires).
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }

        // DO NOT install(ContentEncoding) here. kzen-auto gzip/deflate-compresses its JSON responses
        // (Ktor Compression, TP1), and this proxy relays that compression END TO END: it forwards the
        // browser's Accept-Encoding upstream, and copies the raw gzipped body + Content-Encoding back
        // downstream verbatim (ProxyHandler: Content-Encoding is neither hop-by-hop nor in Ktor's unsafe
        // header set, and the body is a byte-for-byte copyTo). The browser does the decoding.
        //
        // The ContentEncoding client plugin would break this: it injects its own Accept-Encoding and
        // TRANSPARENTLY DECODES the upstream body (stripping Content-Encoding/Content-Length), so the proxy
        // would then relay decompressed bytes on the proxied leg — throwing away the whole transfer saving,
        // and re-introducing exactly the byte volume TP1/TP3 removed. There is no plugin to add here; the
        // correct behaviour is to install nothing and let the bytes pass through.
    }

    val proxyHandler = ProxyHandler(
        projectRegistry,
        processRegistry,
        launcherJarPath,
        httpClient)


    //-----------------------------------------------------------------------------------------------------------------
    fun start() {
        val path = Paths.get(properties.path)
        val download = URI(properties.download)
        artifactInstaller.downloadIfAbsent(path, download)
        artifactInstaller.pruneStaleSnapshotSiblings(path)

        val freePort = FreePortUtil.findAvailableTcpPort()

        // Absolute: the launcher's working directory is its own unpack dir, so a relative home would
        //  resolve inside the artifact cache rather than beside it.
        val projectHome = Paths.get(properties.projectHome).toAbsolutePath().normalize()

        val name = path.fileName.toString()
        mainJarRunner.start(name, launcherJarPath, freePort, "-Xmx64m", listOf("--project.home=$projectHome"))
    }


    fun close() {
        httpClient.close()
        projectRegistry.close()
        processRegistry.close()
    }
}