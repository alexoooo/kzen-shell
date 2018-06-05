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

        const val mainJarPrefix = "kzen-project-jvm-"
        const val mainJarSuffix = ".jar"
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


    private fun startImpl(name: String, location: Path): ProjectInfo {
        val libsFolder = location.resolve(libsPath)

        if (! Files.exists(libsFolder)) {
            gradleRunner.run(location, buildCommand)
        }

        val matchingJars =
                Files.list(libsFolder).use {
                    it.filter({
                        val fileName = it.fileName.toString()

                        fileName.startsWith(mainJarPrefix) &&
                                fileName.endsWith(mainJarSuffix)
                    }).collect(Collectors.toList())
                }

        val jarPath = Iterables.getOnlyElement(matchingJars)

        val freePort = SocketUtils.findAvailableTcpPort(minPort, maxPort)

        val process = bootJarRunner.start(name, jarPath, freePort, location)

        return ProjectInfo(
                name, location, process)
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