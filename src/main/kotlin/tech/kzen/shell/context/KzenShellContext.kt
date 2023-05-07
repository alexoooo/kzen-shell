package tech.kzen.shell.context

import tech.kzen.shell.process.BootJarRunner
import tech.kzen.shell.registry.ProcessRegistry
import tech.kzen.shell.repo.ArtifactRepo
import tech.kzen.shell.repo.DownloadService
import tech.kzen.shell.ui.DesktopUi
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
    val bootJarRunner = BootJarRunner(processRegistry)


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
        bootJarRunner.start(name, jarPath, freePort, "-XX:+UseShenandoahGC -mx64m")
    }


    fun close() {
        processRegistry.close()
    }
}