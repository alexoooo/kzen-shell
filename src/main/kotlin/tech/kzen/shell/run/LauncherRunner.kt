package tech.kzen.shell.run

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import tech.kzen.shell.process.BootJarRunner
import tech.kzen.shell.properties.ShellProperties
import tech.kzen.shell.repo.ArtifactRepo
import tech.kzen.shell.ui.DesktopUi
import tech.kzen.shell.util.FreePortUtil
import java.net.URI
import java.nio.file.Paths


@Suppress("unused")
@Component
class LauncherRunner(
    private val properties: ShellProperties,
    private val artifactRepo: ArtifactRepo,
    private val bootJarRunner: BootJarRunner
): ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        val path = Paths.get(properties.path!!)
        val download = URI(properties.download!!)

        artifactRepo.downloadIfAbsent(path, download)

        val freePort = FreePortUtil.findAvailableTcpPort()

        val name = path.fileName.toString()
        bootJarRunner.start(name, path, freePort, "-XX:+UseShenandoahGC -mx64m")

        DesktopUi.onLoaded()
    }
}