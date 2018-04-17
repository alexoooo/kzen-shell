package tech.kzen.shell

import tech.kzen.shell.model.ProjectModel
import tech.kzen.shell.resource.ResourceReader
import tech.kzen.shell.resource.ResourceTemplate
import tech.kzen.shell.resource.ResourceWriter
import java.nio.file.Paths


fun main(args: Array<String>) {
    val defaultTemplatesPath =
            "${KzenShellPackage.pathName}/templates/default"

    val resourceReader = ResourceReader()
    val resourceTree = resourceReader.read(defaultTemplatesPath)

    val model = ProjectModel(
            "tech.kzen",
            "foo")

    val defaultTemplate = ResourceTemplate()

    val renderedDefaultTemplate =
            defaultTemplate.render(resourceTree, model)

    val resourceWriter = ResourceWriter()
    val destinationPath = Paths.get("work/${model.artifactId}")
    resourceWriter.write(destinationPath, renderedDefaultTemplate)

//    println(resourceTree.files.keys)


//    println("Hello, world! - ${getAnswer()}")
//    for (resourceInfo in KzenShellPackage.resources) {
//    for (resourceInfo in KzenShellPackage.resources) {
//        println(resourceInfo.resourceName)
//    }
}