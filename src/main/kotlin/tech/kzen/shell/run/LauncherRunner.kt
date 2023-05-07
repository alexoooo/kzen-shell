package tech.kzen.shell.run

//import org.springframework.boot.ApplicationArguments
//import org.springframework.boot.ApplicationRunner
//import org.springframework.stereotype.Component


//@Suppress("unused")
//@Component
//class LauncherRunner(
//    private val properties: ShellProperties,
//    private val artifactRepo: ArtifactRepo,
//    private val bootJarRunner: BootJarRunner
//): ApplicationRunner {
//    override fun run(args: ApplicationArguments?) {
//        val path = Paths.get(properties.path!!)
//        val download = URI(properties.download!!)
//
//        artifactRepo.downloadIfAbsent(path, download)
//
//        val freePort = FreePortUtil.findAvailableTcpPort()
//
//        val name = path.fileName.toString()
//        bootJarRunner.start(name, path, freePort, "-XX:+UseShenandoahGC -mx64m")
//
//        DesktopUi.onLoaded()
//    }
//}