//package tech.kzen.shell.template
//
//import tech.kzen.shell.model.ProjectModel
//import tech.kzen.shell.process.GradleRunner
//
//
//object TemplateSample {
//    fun createAndRunProject() {
//        val model = ProjectModel(
//                "tech.kzen.shell",
//                "shell-launcher",
//                "launcher")
//
//        val projectPath = ProjectCreator.createFromResource(model)
//
//        println("projectPath: $projectPath")
//
//        GradleRunner.run(projectPath, "clean")
//        GradleRunner.run(projectPath, "build")
//    }
//}
