package tech.kzen.shell

import tech.kzen.shell.model.ProjectModel
import tech.kzen.shell.process.GradleProcess
import tech.kzen.shell.process.GradleRunner
import tech.kzen.shell.resource.ResourceReader
import tech.kzen.shell.resource.ResourceTemplate
import tech.kzen.shell.resource.ResourceWriter
import java.nio.file.Paths


fun createAndRunProject() {
    val model = ProjectModel(
            "tech.kzen.shell",
            "shell-launcher",
            "launcher")

    val projectPath = ProjectCreator.createFromResource(model)

    println("projectPath: $projectPath")

    GradleRunner.run(projectPath, "clean")
    GradleRunner.run(projectPath, "build")
}