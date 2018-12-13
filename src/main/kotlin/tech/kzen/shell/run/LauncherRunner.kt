package tech.kzen.shell.run

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.util.SocketUtils
import tech.kzen.shell.process.BootJarRunner
import tech.kzen.shell.properties.ShellProperties
import tech.kzen.shell.repo.ArtifactRepo
import tech.kzen.shell.ui.DesktopUi
import java.net.URI
import java.nio.file.Paths


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

        val freePort = SocketUtils.findAvailableTcpPort(49152, 65535)

        val name = path.fileName.toString()
        bootJarRunner.start(name, path, freePort)

        DesktopUi.onLoaded()
    }
}