package tech.kzen.shell.registry

import com.google.common.cache.CacheBuilder
//import org.springframework.stereotype.Component
import tech.kzen.shell.process.MainJarProcess
import tech.kzen.shell.process.MainJarRunner
import tech.kzen.shell.util.FreePortUtil
import java.nio.file.Files
import java.nio.file.Path


class ProjectRegistry(
//    private val gradleRunner: GradleRunner,
    private val mainJarRunner: MainJarRunner
) {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        const val buildCommand = "build"

        const val libsPath = "kzen-project-jvm/build/libs"

        const val mainJar = "main.jar"
        const val gradleMainJarPrefix = "kzen-project-jvm-"
        const val gradleMainJarSuffix = "-boot.jar"
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
    fun start(name: String, location: Path, jvmArgs: String): ProjectInfo {
        return projects.get(name) { startImpl(name, location, jvmArgs) }
    }


    private fun startImpl(name: String, projectHome: Path, jvmArgs: String): ProjectInfo {
        val jarPath = locateJar(projectHome)

        val freePort = FreePortUtil.findAvailableTcpPort()

        val process = mainJarRunner.start(name, jarPath, freePort, projectHome, jvmArgs)

        return ProjectInfo(
                name, projectHome, process)
    }


    private fun locateJar(projectHome: Path): Path {
        val mainJar = projectHome.resolve(mainJar)
        if (Files.exists(mainJar)) {
            return mainJar
        }

        throw IllegalArgumentException("Not found: $mainJar")

//        val libsFolder = projectHome.resolve(libsPath)
//        if (! Files.exists(libsFolder)) {
//            gradleRunner.run(projectHome, buildCommand)
//        }
//
//        val matchingJars =
//                Files.list(libsFolder).use { fileList ->
//                    fileList.filter {
//                        val fileName = it.fileName.toString()
//
//                        fileName.startsWith(gradleMainJarPrefix) &&
//                                fileName.endsWith(gradleMainJarSuffix)
//                    }.collect(Collectors.toList())
//                }
//
//        return Iterables.getOnlyElement(matchingJars)
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
            val process: MainJarProcess)
}