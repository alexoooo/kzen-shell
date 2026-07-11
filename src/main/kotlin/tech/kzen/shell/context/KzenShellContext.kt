package tech.kzen.shell.context

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import tech.kzen.shell.process.MainJarRunner
import tech.kzen.shell.proxy.ProxyHandler
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.registry.ProjectRegistry
import tech.kzen.shell.repo.ArtifactInstaller
import tech.kzen.shell.repo.DownloadService
import tech.kzen.shell.util.FreePortUtil
import java.net.URI
import java.nio.file.Paths


//---------------------------------------------------------------------------------------------------------------------
class KzenShellContext(
    val properties: KzenShellProperties
) {
    //-----------------------------------------------------------------------------------------------------------------
    val downloadService = DownloadService()

    val artifactInstaller = ArtifactInstaller(downloadService)

    val processRegistry = ProcessRegistry()
    val mainJarRunner = MainJarRunner(processRegistry)

    val projectRegistry = ProjectRegistry(mainJarRunner)

    val httpClient = HttpClient(CIO) {
        followRedirects = false
        expectSuccess = false
    }

    val proxyHandler = ProxyHandler(
        projectRegistry,
        processRegistry,
        properties,
        httpClient)


    //-----------------------------------------------------------------------------------------------------------------
    fun start() {
        val path = Paths.get(properties.path)
        val download = URI(properties.download)
        artifactInstaller.downloadIfAbsent(path, download)
        artifactInstaller.pruneStaleSnapshotSiblings(path)

        val jarPath = path.resolve("main.jar").toAbsolutePath().normalize()

        val freePort = FreePortUtil.findAvailableTcpPort()

        val name = path.fileName.toString()
        mainJarRunner.start(name, jarPath, freePort, "-Xmx64m")
    }


    fun close() {
        httpClient.close()
        projectRegistry.close()
        processRegistry.close()
    }
}