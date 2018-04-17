package tech.kzen.shell.resource

import java.nio.file.Path

data class ResourceTree(
        val files: Map<Path, ByteArray>)