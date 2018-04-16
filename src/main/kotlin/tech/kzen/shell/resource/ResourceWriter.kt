package tech.kzen.shell.resource

import java.nio.file.Files
import java.nio.file.Path

class ResourceWriter {
    fun write(root: Path, resourceTree: ResourceTree) {
        for (e in resourceTree.files) {
            val location = root.resolve("./${e.key}").normalize()
            Files.createDirectories(location.parent)
            Files.write(location, e.value)
        }
    }
}