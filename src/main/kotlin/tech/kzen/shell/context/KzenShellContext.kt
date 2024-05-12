package tech.kzen.shell.context

import tech.kzen.shell.process.MainJarRunner
import tech.kzen.shell.proxy.ProxyHandler
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.registry.ProjectRegistry
import tech.kzen.shell.repo.ArtifactRepo
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

    val artifactRepo = ArtifactRepo(downloadService)

    val processRegistry = ProcessRegistry()
    val mainJarRunner = MainJarRunner(processRegistry)

    val projectRegistry = ProjectRegistry(mainJarRunner)

    val proxyHandler = ProxyHandler(
        projectRegistry,
        processRegistry,
        properties)


    //-----------------------------------------------------------------------------------------------------------------
    init {
        downloadService.trustBadCertificate()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun start() {
        val path = Paths.get(properties.path!!)
        val download = URI(properties.download!!)
        artifactRepo.downloadIfAbsent(path, download)

        val jarPath = path.resolve("main.jar").toAbsolutePath().normalize()

        val freePort = FreePortUtil.findAvailableTcpPort()

        val name = path.fileName.toString()
        mainJarRunner.start(name, jarPath, freePort, "-mx64m")
    }


    fun close() {
        processRegistry.close()
    }
}