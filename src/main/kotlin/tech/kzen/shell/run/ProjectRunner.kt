//package tech.kzen.shell.run
//
//import com.google.common.collect.Iterables
//import org.springframework.boot.ApplicationArguments
//import org.springframework.boot.ApplicationRunner
//import org.springframework.stereotype.Component
//import org.springframework.util.SocketUtils
//import tech.kzen.shell.process.BootJarRunner
//import tech.kzen.shell.process.GradleRunner
//import java.nio.file.Files
//import java.nio.file.Path
//import java.nio.file.Paths
//import java.nio.file.attribute.BasicFileAttributes
//import java.util.function.BiPredicate
//import java.util.stream.Collectors
//
//
//@Component
//class ProjectRunner(
////        private val artifactRepo: ArtifactRepo,
//        private val bootJarRunner: BootJarRunner
//): ApplicationRunner {
//    override fun run(args: ApplicationArguments?) {
//        val projectPath = Paths.get("/home/ao/proj/kzen-launcher/proj/foo")
//
//        val libsFolder = projectPath.resolve("kzen-project-jvm/build/libs")
//
//        if (! Files.exists(libsFolder)) {
//            GradleRunner.run(projectPath, "build")
//        }
//
//        val matchingJars =
//                Files.list(libsFolder).use {
//                    it.filter({
//                        val fileName = it.fileName.toString()
//
//                        fileName.startsWith("kzen-project-jvm-") &&
//                                fileName.endsWith(".jar")
//                    }).collect(Collectors.toList())
//                }
//
//        val jarPath = Iterables.getOnlyElement(matchingJars)
//
//        val freePort = SocketUtils.findAvailableTcpPort(49152, 65535)
//
//        bootJarRunner.start(jarPath, freePort, projectPath)
//    }
//}