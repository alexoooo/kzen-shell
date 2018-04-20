package tech.kzen.shell.model

data class ProjectModel(
        val groupId: String,
        val artifactId: String,
        val artifactPackage: String
) {
    val rootPackage: String =
            "$groupId.$artifactPackage"
}