package tech.kzen.shell.registry

import com.google.common.cache.CacheBuilder
import com.google.common.collect.Iterables
import org.springframework.stereotype.Component
import org.springframework.util.SocketUtils
import tech.kzen.shell.process.BootJarProcess
import tech.kzen.shell.process.BootJarRunner
import tech.kzen.shell.process.GradleRunner
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors


@Component
class ProjectRegistry(
        private val gradleRunner: GradleRunner,
        private val bootJarRunner: BootJarRunner
) {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        // Dynamic ports - https://en.wikipedia.org/wiki/Registered_port
        const val minPort = 49152
        const val maxPort = 65535


        const val buildCommand = "build"

        const val libsPath = "kzen-project-jvm/build/libs"

        const val mainJar = "main.jar"
        const val gradleMainJarPrefix = "kzen-project-jvm-"
        const val gradleMainJarSuffix = ".jar"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val projects = CacheBuilder
            .newBuilder()
            .concurrencyLevel(1)
            .build<String, ProjectInfo>()


    //-----------------------------------------------------------------------------------------------------------------
    fun contains(name: String): Boolean {
        return projects.getIfPresent(name) != null
    }


    fun list(): Set<String> {
        return projects.asMap().keys
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun start(name: String, location: Path): ProjectInfo {
        return projects.get(name, { startImpl(name, location) })
    }


    private fun startImpl(name: String, projectHome: Path): ProjectInfo {
        val jarPath = locateJar(projectHome)

        val freePort = SocketUtils.findAvailableTcpPort(minPort, maxPort)

        val process = bootJarRunner.start(name, jarPath, freePort, projectHome)

        return ProjectInfo(
                name, projectHome, process)
    }


    private fun locateJar(projectHome: Path): Path {
        val mainJar = projectHome.resolve("main.jar")
        if (Files.exists(mainJar)) {
            return mainJar
        }

        val libsFolder = projectHome.resolve(libsPath)
        if (! Files.exists(libsFolder)) {
            gradleRunner.run(projectHome, buildCommand)
        }

        val matchingJars =
                Files.list(libsFolder).use {
                    it.filter({
                        val fileName = it.fileName.toString()

                        fileName.startsWith(gradleMainJarPrefix) &&
                                fileName.endsWith(gradleMainJarSuffix)
                    }).collect(Collectors.toList())
                }

        return Iterables.getOnlyElement(matchingJars)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun stop(name: String): Boolean {
        val projectInfo = projects.getIfPresent(name)
                ?: return false

        projectInfo.process.kill()
        projects.invalidate(name)

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class ProjectInfo(
            val name: String,
            val location: Path,
            val process: BootJarProcess)
}