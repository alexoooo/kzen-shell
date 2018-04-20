package tech.kzen.shell.resource

import com.google.common.io.MoreFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

class ResourceWriter {
    companion object {
        private val executablePermissions = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,

                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.GROUP_READ,

                PosixFilePermission.OTHERS_EXECUTE,
                PosixFilePermission.OTHERS_READ)
    }

    fun write(root: Path, resourceTree: ResourceTree) {
        for (e in resourceTree.files) {
            val location = root.resolve("./${e.key}").normalize()
            Files.createDirectories(location.parent)
            Files.write(location, e.value)

            val extension = MoreFiles.getFileExtension(location)
            if (extension == "" || extension == "bat") {
                Files.setPosixFilePermissions(location, executablePermissions)
            }
        }
    }
}