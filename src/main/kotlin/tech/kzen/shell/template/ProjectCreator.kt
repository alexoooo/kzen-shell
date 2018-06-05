//package tech.kzen.shell.template
//
//import tech.kzen.shell.KzenShellPackage
//import tech.kzen.shell.model.ProjectModel
//import tech.kzen.shell.resource.ResourceReader
//import tech.kzen.shell.resource.ResourceTemplate
//import tech.kzen.shell.resource.ResourceWriter
//import java.nio.file.Files
//import java.nio.file.Path
//import java.nio.file.Paths
//
//
//object ProjectCreator {
//    fun createFromResource(
//            projectModel: ProjectModel
//    ): Path {
//        val artifactId = projectModel.artifactId
//        val destinationPath = Paths.get("work/$artifactId")
//
//        if (Files.exists(destinationPath)) {
//            return destinationPath
//        }
//
//        val defaultTemplatesPath =
//                "${KzenShellPackage.pathName}/templates/${projectModel.artifactPackage}"
//
//        val resourceReader = ResourceReader()
//        val resourceTree = resourceReader.read(defaultTemplatesPath)
//
//        val defaultTemplate = ResourceTemplate()
//
//        val renderedDefaultTemplate =
//                defaultTemplate.render(resourceTree, projectModel)
//
//        val resourceWriter = ResourceWriter()
//        resourceWriter.write(destinationPath, renderedDefaultTemplate)
//
//        return destinationPath
//    }
//}